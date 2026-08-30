// Decorating documentation this plugin does not own has no public API.
// the public Documentation interface can only set content, so reading it needs DocumentationData.
@file:Suppress("UnstableApiUsage")

package com.github.tvcsantos.mermaidrenderer.popup

import com.github.tvcsantos.mermaidrenderer.html.MermaidHtmlRewriter
import com.github.tvcsantos.mermaidrenderer.html.RewriteResult
import com.github.tvcsantos.mermaidrenderer.render.DiagramRequest
import com.github.tvcsantos.mermaidrenderer.render.MermaidRenderService
import com.github.tvcsantos.mermaidrenderer.settings.MermaidSettings
import com.intellij.model.Pointer
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.backend.documentation.AsyncDocumentation
import com.intellij.platform.backend.documentation.DocumentationContent
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import kotlinx.coroutines.flow.map

/**
 * The quick documentation popup and tool window, decorated the same way as a
 * rendered comment.
 *
 * Everything is delegated except the documentation HTML, which has its Mermaid
 * blocks replaced by images. Wrapping happens both on
 * [MermaidIdeDocumentationTargetProvider] and on the
 * [DocumentationTarget.createPointer] returned by this class.
 *
 * The pointer wraps too, so a target that survives a read action comes back
 * decorated.
 */
class MermaidDocumentationTarget(
    private val delegate: DocumentationTarget
) : DocumentationTarget {

    private val logger = logger<MermaidDocumentationTarget>()

    override fun createPointer(): Pointer<out DocumentationTarget> {
        val delegatePointer = delegate.createPointer()
        return Pointer {
            delegatePointer.dereference()
                ?.let(::MermaidDocumentationTarget)
        }
    }

    override fun computePresentation(): TargetPresentation =
        delegate.computePresentation()

    override fun computeDocumentationHint(): String? =
        delegate.computeDocumentationHint()

    override fun computeDocumentation(): DocumentationResult? =
        delegate.computeDocumentation()?.let(::rewrite)

    private fun rewrite(result: DocumentationResult): DocumentationResult =
        when (result) {
            is DocumentationData -> rewrite(result)
            // Computed later, off the calling thread.
            // The decoration has to travel with the supplier.
            is AsyncDocumentation -> AsyncDocumentation {
                when (val documentation = result.supplier.invoke()) {
                    is DocumentationData -> rewrite(documentation)
                    else -> documentation
                }
            }
        }

    private fun rewrite(documentation: DocumentationData): DocumentationData =
        try {
            val original = documentation.html
            val rewritten = rewrite(original)
            val decorated = documentation.html(rewritten.html) as DocumentationData

            if (rewritten.pending == 0) {
                decorated
            } else {
                // Rendering finishes after the popup is on screen
                // so its content is replaced as the diagrams arrive.
                // The platform cancels the flow when the popup closes.
                val updates = DiagramUpdates.htmlUpdates(
                    initialHtml = rewritten.html
                ) {
                    rewrite(original)
                }.map {
                    DocumentationContent.content(it)
                }
                decorated.updates(updates) as DocumentationData
            }
        } catch (e: Exception) {
            logger.warn("Cannot rewrite documentation", e)
            documentation
        }

    private fun rewrite(html: String): RewriteResult {
        val settings = MermaidSettings.getInstance()
        val service = service<MermaidRenderService>()
        return MermaidHtmlRewriter.rewrite(
            html = html,
            heuristics = settings.heuristicDetection,
            requestFor = { DiagramRequest.of(it, settings) },
            // No editor to refresh from a popup; the updates flow above carries the diagram instead.
            resolve = { service.resolve(it, null) },
            showProgress = settings.showRenderingProgress,
        )
    }
}
