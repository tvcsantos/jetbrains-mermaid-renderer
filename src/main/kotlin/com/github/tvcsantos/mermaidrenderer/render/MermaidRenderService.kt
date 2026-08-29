package com.github.tvcsantos.mermaidrenderer.render

import com.github.tvcsantos.mermaidrenderer.html.MermaidFences
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** What the HTML rewriter knows about a diagram right now. */
sealed interface DiagramState {
    data class Ready(val diagram: CachedDiagram) : DiagramState
    data object Failed : DiagramState
    data object Pending : DiagramState
}

/** Where to push a refreshed rendered comment once a diagram is ready. */
data class RefreshTarget(val project: Project, val file: VirtualFile)

/**
 * Serves diagrams from the cache and renders missing ones in the background.
 *
 * [resolve] is called from `renderText()`, which runs under a read lock, so it never blocks: a
 * miss returns [DiagramState.Pending] and the rendered comment is recomputed once the image lands.
 */
@Service(Service.Level.APP)
class MermaidRenderService : Disposable {

    private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Mermaid Renderer", 1)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /** Remembered so a broken diagram is not re-rendered on every pass; cleared on settings change. */
    private val failures = ConcurrentHashMap<String, String>()

    /** The same failures keyed by normalized diagram text, for the gutter marker. */
    private val failuresBySource = ConcurrentHashMap<String, String>()

    private val generationCounter = AtomicLong()

    /**
     * Rewritten HTML is memoized per rendered comment, so anything that changes the outcome of a
     * rewrite - a diagram becoming available, a settings or theme change - has to bump this.
     */
    val generation: Long get() = generationCounter.get()

    private fun bumpGeneration() {
        generationCounter.incrementAndGet()
    }

    fun resolve(request: DiagramRequest, target: RefreshTarget?): DiagramState {
        service<DiagramCache>().get(request.cacheKey)?.let { return DiagramState.Ready(it) }
        // The message itself is read from failureFor() by the gutter marker, and logged when recorded.
        if (failures.containsKey(request.cacheKey)) return DiagramState.Failed

        // Nothing is displayed without a UI, and starting a browser would only slow tests down.
        if (ApplicationManager.getApplication().isHeadlessEnvironment) return DiagramState.Pending

        if (inFlight.add(request.cacheKey)) {
            executor.execute { renderNow(request, target) }
        }
        return DiagramState.Pending
    }

    private fun renderNow(request: DiagramRequest, target: RefreshTarget?) {
        try {
            when (val outcome = service<JcefMermaidRenderer>().render(request)) {
                is RenderOutcome.Success ->
                    service<DiagramCache>().put(request.cacheKey, outcome.png, outcome.width, outcome.height)

                is RenderOutcome.Failure -> recordFailure(request, outcome.message)
            }
        } finally {
            inFlight.remove(request.cacheKey)
        }

        bumpGeneration()

        if (target != null && !target.project.isDisposed) {
            target.project.service<DocRenderRefresher>().scheduleRefresh(target.file)
        }
    }

    internal fun recordFailure(request: DiagramRequest, message: String) {
        failures[request.cacheKey] = message
        failuresBySource[MermaidFences.normalize(request.source)] = message
        logger<MermaidRenderService>().warn("Mermaid could not render a diagram: $message")
    }

    /** The Mermaid error for a diagram whose text normalizes to [normalizedSource], if any. */
    fun failureFor(normalizedSource: String): String? = failuresBySource[normalizedSource]

    /** Lets diagrams that previously failed be retried, e.g. after settings changed. */
    fun forgetFailures() {
        failures.clear()
        failuresBySource.clear()
        bumpGeneration()
    }

    override fun dispose() {
        executor.shutdownNow()
    }
}
