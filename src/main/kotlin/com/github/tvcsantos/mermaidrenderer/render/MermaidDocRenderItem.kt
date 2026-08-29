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

/**
 * A rendered comment with its Mermaid blocks replaced by images.
 *
 * Everything is delegated except [textToRender], which is rewritten on the way to the renderer.
 * `DocRenderer` re-reads that property whenever it rebuilds its pane, so the rewrite is re-derived
 * rather than stored - that is what lets a finished diagram replace its placeholder.
 *
 * The wrapper is only ever seen by `DocRenderer`; the manager keeps the original item. The one
 * behaviour it changes is `DocRenderer.isDebugZombie`, an `instanceof` check behind the
 * `cache.markup.debug` registry flag.
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

            cached?.let { if (it.generation == generation && it.source == source) return it.html }

            val html = rewrite(source, service)
            cached = Rewritten(source, generation, html)
            return html
        }

    private fun rewrite(source: String, service: MermaidRenderService): String {
        // Cheap reject: documentation without a code block cannot hold a diagram.
        if (!source.contains("<pre", ignoreCase = true) && !source.contains("mermaid", ignoreCase = true)) {
            return source
        }

        return try {
            val settings = MermaidSettings.getInstance()
            val target = refreshTarget()
            val tagged = taggedBodies()
            val result = MermaidHtmlRewriter.rewrite(
                html = source,
                heuristics = settings.heuristicDetection,
                requestFor = { DiagramRequest.of(it, settings) },
                resolve = { service.resolve(it, target) },
                isTagged = tagged::contains,
            )
            reportOnce(result)
            result.html
        } catch (e: Exception) {
            logger<MermaidDocRenderItem>().warn("Cannot rewrite rendered documentation", e)
            source
        }
    }

    /**
     * The ```` ```mermaid ```` fences of the comment this item renders. Read from the document
     * because syntax highlighting strips the marker before it reaches the HTML.
     */
    private fun taggedBodies(): Set<String> = try {
        val highlighter = delegate.highlighter
        if (!highlighter.isValid) {
            emptySet()
        } else {
            val document = delegate.editor.document
            val end = highlighter.endOffset.coerceAtMost(document.textLength)
            val start = highlighter.startOffset.coerceAtMost(end)
            MermaidFences.taggedBodies(document.getText(TextRange(start, end)))
        }
    } catch (e: Exception) {
        logger<MermaidDocRenderItem>().warn("Cannot read the comment source", e)
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
        logger<MermaidDocRenderItem>().info(
            "Rewrote a rendered comment: ${result.candidates} code block(s), " +
                "${result.matched} recognised as mermaid"
        )
    }

    override val foldRegion: CustomFoldRegion? get() = delegate.foldRegion

    override val highlighter: RangeHighlighter get() = delegate.highlighter

    override val editor: Editor get() = delegate.editor

    override fun calcFoldingGutterIconRenderer(): GutterIconRenderer? = delegate.calcFoldingGutterIconRenderer()

    override fun setIconVisible(visible: Boolean) = delegate.setIconVisible(visible)

    override fun toggle() = delegate.toggle()

    override fun getInlineDocumentation(): InlineDocumentation? = delegate.getInlineDocumentation()

    override fun getInlineDocumentationTarget(): DocumentationTarget? = delegate.getInlineDocumentationTarget()

    private companion object {
        val reported = AtomicBoolean(false)
    }
}
