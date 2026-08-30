// Decorating documentation this plugin does not own has no public API.
// IdeDocumentationTargetProvider is the platform's seam for the documentation popup.
@file:Suppress("UnstableApiUsage")

package com.github.tvcsantos.mermaidrenderer.popup

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.lang.documentation.ide.impl.IdeDocumentationTargetProviderImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.psi.PsiFile

/**
 * The single point the IDE asks for the documentation shown on hover, in the quick documentation
 * popup and in the documentation tool window - the popup's equivalent of `DocRendererProvider`.
 *
 * The platform marks this service `open="true"`, so replacing it and wrapping the targets decorates
 * every one of those surfaces without competing with the providers that own the documentation.
 */
class MermaidIdeDocumentationTargetProvider(project: Project) : IdeDocumentationTargetProvider {

    private val delegate = IdeDocumentationTargetProviderImpl(project)

    override fun documentationTargets(editor: Editor, file: PsiFile, offset: Int): List<DocumentationTarget> =
        delegate.documentationTargets(editor, file, offset).map(::MermaidDocumentationTarget)

    override fun documentationTargets(
        editor: Editor,
        file: PsiFile,
        lookupElement: LookupElement,
    ): List<DocumentationTarget> =
        delegate.documentationTargets(editor, file, lookupElement).map(::MermaidDocumentationTarget)
}
