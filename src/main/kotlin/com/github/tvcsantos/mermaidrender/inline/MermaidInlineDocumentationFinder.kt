package com.github.tvcsantos.mermaidrender.inline

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.InlineDocumentation
import com.intellij.platform.backend.documentation.InlineDocumentationProvider
import com.intellij.psi.PsiFile

/**
 * Covers the second way the platform builds a rendered comment.
 *
 * Toggling a single comment from the gutter does not go through the render pass: it asks
 * `InlineDocumentationFinder` for the documentation at a range, which resolves to the **first**
 * provider returning non-null - the opposite of the last-wins rule that governs
 * [MermaidInlineDocumentationProvider]. Hence a second extension, registered `order="first"`,
 * whose only job is to wrap whatever the real providers would have returned.
 */
class MermaidInlineDocumentationFinder : InlineDocumentationProvider, MermaidDocumentationDecorator {

    /** Left to [MermaidInlineDocumentationProvider], which must run last instead of first. */
    override fun inlineDocumentationItems(file: PsiFile?): Collection<InlineDocumentation> = emptyList()

    override fun findInlineDocumentation(file: PsiFile, textRange: TextRange): InlineDocumentation? {
        if (!mayContainMermaid(file)) return null

        val delegate = delegateProviders().firstNotNullOfOrNull { provider ->
            try {
                provider.findInlineDocumentation(file, textRange)
            } catch (e: Exception) {
                logger<MermaidInlineDocumentationFinder>().warn("Delegate provider failed: $provider", e)
                null
            }
        } ?: return null

        return DecoratedInlineDocumentation(delegate, file)
    }
}
