package com.github.tvcsantos.mermaidrender.inline

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.InlineDocumentation
import com.intellij.platform.backend.documentation.InlineDocumentationProvider
import com.intellij.psi.PsiFile
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Decorates the rendered doc comments produced by every other provider.
 *
 * The platform keeps one rendered item per text range and the last provider wins, so registering
 * this one with `order="last"` and re-emitting the other providers' items - wrapped - replaces
 * their HTML with ours. Nothing is contributed for files without a Mermaid diagram.
 *
 * This covers documentation built by the render pass; [MermaidInlineDocumentationFinder] covers
 * the gutter toggle, which asks a different question with the opposite ordering rule.
 */
class MermaidInlineDocumentationProvider : InlineDocumentationProvider, MermaidDocumentationDecorator {

    override fun inlineDocumentationItems(file: PsiFile?): Collection<InlineDocumentation> {
        val psiFile = file ?: return emptyList()
        if (!mayContainMermaid(psiFile)) return emptyList()

        warnIfNotLast()

        return delegateProviders()
            .flatMap { provider ->
                try {
                    provider.inlineDocumentationItems(psiFile)
                } catch (e: Exception) {
                    logger<MermaidInlineDocumentationProvider>().warn("Delegate provider failed: $provider", e)
                    emptyList()
                }
            }
            .map { DecoratedInlineDocumentation(it, psiFile) }
    }

    /** Handled by [MermaidInlineDocumentationFinder], which is registered first for this purpose. */
    override fun findInlineDocumentation(file: PsiFile, textRange: TextRange): InlineDocumentation? = null

    /**
     * The platform keeps one rendered item per range and the last provider wins, so anything
     * registered after this one silently replaces the diagrams with plain code blocks. Reported
     * once, because it explains an otherwise invisible failure.
     */
    private fun warnIfNotLast() {
        if (!orderReported.compareAndSet(false, true)) return
        val providers = InlineDocumentationProvider.EP_NAME.extensionList
        if (providers.lastOrNull() === this) return
        logger<MermaidInlineDocumentationProvider>().warn(
            "Mermaid Render is not the last inline documentation provider, so its diagrams can be " +
                "overwritten. Order: " + providers.joinToString { it.javaClass.name }
        )
    }

    private val orderReported = AtomicBoolean(false)
}
