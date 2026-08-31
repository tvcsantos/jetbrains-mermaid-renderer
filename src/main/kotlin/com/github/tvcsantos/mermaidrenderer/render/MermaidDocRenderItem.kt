// Decorating documentation this plugin does not own has no public API.
// DocRenderItem is @Internal. Wrapping it is how the comment's HTML is
// rewritten.
@file:Suppress("UnstableApiUsage")

package com.github.tvcsantos.mermaidrenderer.render

import com.github.tvcsantos.mermaidrenderer.html.MermaidFences
import com.github.tvcsantos.mermaidrenderer.html.MermaidHtmlRewriter
import com.github.tvcsantos.mermaidrenderer.html.RewriteResult
import com.github.tvcsantos.mermaidrenderer.settings.MermaidSettings
import com.intellij.codeInsight.documentation.render.DocRenderItem
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.InlineDocumentation
import java.util.concurrent.atomic.AtomicBoolean
import com.intellij.codeInsight.documentation.render.DocRenderer

/**
 * A rendered comment with its Mermaid blocks replaced by images.
 *
 * Everything is delegated except [textToRender], which is rewritten on the way
 * to the renderer. [DocRenderer] re-reads that property whenever it rebuilds
 * its pane, so the rewrite is re-derived rather than stored. That is what lets
 * a finished diagram replace its placeholder.
 *
 * The wrapper is only ever seen by [DocRenderer]. The manager keeps the
 * original item. The one behavior it changes is [DocRenderer.isDebugZombie],
 * an `instanceof` check behind the `cache.markup.debug` registry flag.
 */
class MermaidDocRenderItem(private val delegate: DocRenderItem) : DocRenderItem {

    private class Rewritten(val source: String, val generation: Long, val html: String)

    /** [textToRender] is read on every paint and height calculation, so the result is memoized. */
    @Volatile
    private var cached: Rewritten? = null

    override val textToRender: String?
        get() {
            val source = delegate.textToRender ?: return null
            val service = service<MermaidRenderService>()
            val generation = service.generation

            return rewritten(source, service, generation).html
        }

    private fun rewritten(source: String, service: MermaidRenderService, generation: Long): Rewritten {
        cached?.let { if (it.generation == generation && it.source == source) return it }
        val result = rewrite(source, service)
        return Rewritten(source, generation, result.html).also { cached = it }
    }

    private fun rewrite(source: String, service: MermaidRenderService): RewriteResult {
        // Cheap reject: documentation without a code block cannot hold a diagram.
        if (!source.contains("<pre", ignoreCase = true) && !source.contains("mermaid", ignoreCase = true)) {
            return RewriteResult(source, candidates = 0, matched = 0)
        }

        return try {
            val settings = MermaidSettings.getInstance()
            val target = refreshTarget()
            val tagged = collectFencedBodies()
            val result = MermaidHtmlRewriter.rewrite(
                html = source,
                heuristics = settings.heuristicDetection,
                requestFor = { DiagramRequest.of(it, settings) },
                resolve = { service.resolve(it, target) },
                isTagged = tagged::contains,
                showProgress = settings.showRenderingProgress,
            )
            reportOnce(result)
            target?.let {
                val offset = delegate.highlighter.takeIf { h -> h.isValid }?.startOffset ?: 0
                it.project.service<DocRenderRefresher>().syncMarkers(it.file, offset, result.failed)
            }
            result
        } catch (e: Exception) {
            logger.warn("Cannot rewrite rendered documentation", e)
            RewriteResult(source, candidates = 0, matched = 0)
        }
    }

    /**
     * The ```` ```mermaid ```` fences of the comment this item renders. Read from the document
     * because syntax highlighting strips the marker before it reaches the HTML.
     */
    private fun collectFencedBodies(): Set<String> = try {
        val highlighter = delegate.highlighter
        if (!highlighter.isValid) {
            emptySet()
        } else {
            val document = delegate.editor.document
            val end = highlighter.endOffset.coerceAtMost(document.textLength)
            val start = highlighter.startOffset.coerceAtMost(end)
            MermaidFences.collectFencedBodies(document.getText(TextRange(start, end)))
        }
    } catch (e: Exception) {
        logger.warn("Cannot read the comment source", e)
        emptySet()
    }

    private fun refreshTarget(): RefreshTarget? {
        val editor = delegate.editor
        val project = editor.project ?: return null
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
        return RefreshTarget(project, file)
    }

    /**
     * Reported once per session: when no diagram shows up there is otherwise nothing in the log to
     * distinguish "never asked" from "asked, found nothing".
     */
    private fun reportOnce(result: RewriteResult) {
        if (!reported.compareAndSet(false, true)) return
        logger.info(
            "Rewrote a rendered comment: ${result.candidates} code block(s), " +
                "${result.matched} recognised as mermaid"
        )
    }

    override val foldRegion: CustomFoldRegion? get() = delegate.foldRegion

    override val highlighter: RangeHighlighter get() = delegate.highlighter

    override val editor: Editor get() = delegate.editor

    /**
     * Plain delegation, and it has to stay that way: the platform casts the result back to its own
     * `DocRenderItemImpl.MyGutterIconRenderer`, so any other implementation throws a
     * ClassCastException on every gutter repaint - which takes the whole gutter down with it.
     */
    override fun calcFoldingGutterIconRenderer(): GutterIconRenderer? = delegate.calcFoldingGutterIconRenderer()

    override fun setIconVisible(visible: Boolean) = delegate.setIconVisible(visible)

    override fun toggle() = delegate.toggle()

    override fun getInlineDocumentation(): InlineDocumentation? = delegate.getInlineDocumentation()

    override fun getInlineDocumentationTarget(): DocumentationTarget? = delegate.getInlineDocumentationTarget()

    private companion object {
        /** Static: one of these items exists per rendered comment. */
        val logger = logger<MermaidDocRenderItem>()
        val reported = AtomicBoolean(false)
    }
}
