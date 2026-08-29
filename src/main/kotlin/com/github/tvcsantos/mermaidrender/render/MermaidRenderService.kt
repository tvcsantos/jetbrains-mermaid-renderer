package com.github.tvcsantos.mermaidrender.render

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ConcurrentHashMap

/** What the HTML rewriter knows about a diagram right now. */
sealed interface DiagramState {
    data class Ready(val diagram: CachedDiagram) : DiagramState
    data class Failed(val message: String) : DiagramState
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

    private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Mermaid Render", 1)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /** Remembered so a broken diagram is not re-rendered on every pass; cleared on settings change. */
    private val failures = ConcurrentHashMap<String, String>()

    fun resolve(request: DiagramRequest, target: RefreshTarget?): DiagramState {
        service<DiagramCache>().get(request.cacheKey)?.let { return DiagramState.Ready(it) }
        failures[request.cacheKey]?.let { return DiagramState.Failed(it) }

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

                is RenderOutcome.Failure ->
                    failures[request.cacheKey] = outcome.message
            }
        } finally {
            inFlight.remove(request.cacheKey)
        }

        if (target != null && !target.project.isDisposed) {
            target.project.service<DocRenderRefresher>().scheduleRefresh(target.file)
        }
    }

    /** Lets diagrams that previously failed be retried, e.g. after settings changed. */
    fun forgetFailures() = failures.clear()

    override fun dispose() {
        executor.shutdownNow()
    }
}
