package com.github.tvcsantos.mermaidrender.inline

import com.github.tvcsantos.mermaidrender.html.MermaidHtmlRewriter
import com.github.tvcsantos.mermaidrender.html.RewriteResult
import com.github.tvcsantos.mermaidrender.render.DiagramRequest
import com.github.tvcsantos.mermaidrender.render.MermaidRenderService
import com.github.tvcsantos.mermaidrender.render.RefreshTarget
import com.github.tvcsantos.mermaidrender.settings.MermaidSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.InlineDocumentation
import com.intellij.psi.PsiFile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps another provider's inline documentation and rewrites its HTML. Everything else is
 * delegated, including the text range - which is what makes this item replace the original.
 */
class DecoratedInlineDocumentation(
    private val delegate: InlineDocumentation,
    private val file: PsiFile,
) : InlineDocumentation {

    override fun getDocumentationRange(): TextRange = delegate.documentationRange

    override fun getDocumentationOwnerRange(): TextRange? = delegate.documentationOwnerRange

    override fun getOwnerTarget(): DocumentationTarget? = delegate.ownerTarget

    override fun renderText(): String? {
        val html = delegate.renderText() ?: return null
        return try {
            val settings = MermaidSettings.getInstance()
            val target = file.virtualFile?.let { RefreshTarget(file.project, it) }
            val result = MermaidHtmlRewriter.rewrite(
                html = html,
                heuristics = settings.heuristicDetection,
                requestFor = { DiagramRequest.of(it, settings) },
                resolve = { service<MermaidRenderService>().resolve(it, target) },
            )
            reportOnce(result)
            result.html
        } catch (e: Exception) {
            logger<DecoratedInlineDocumentation>().warn("Cannot rewrite rendered documentation", e)
            html
        }
    }

    /**
     * Reported once per session: when no diagram shows up there is otherwise nothing at all in the
     * log to distinguish "never asked" from "asked, found nothing".
     */
    private fun reportOnce(result: RewriteResult) {
        if (!reported.compareAndSet(false, true)) return
        logger<DecoratedInlineDocumentation>().info(
            "Rewrote rendered documentation of ${file.name}: " +
                "${result.candidates} code block(s), ${result.matched} recognised as mermaid " +
                "(delegate=${delegate.javaClass.name})"
        )
    }

    private companion object {
        val reported = AtomicBoolean(false)
    }
}
