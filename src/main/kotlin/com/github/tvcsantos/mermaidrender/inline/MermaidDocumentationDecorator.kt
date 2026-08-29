package com.github.tvcsantos.mermaidrender.inline

import com.github.tvcsantos.mermaidrender.html.MermaidBlockDetector
import com.github.tvcsantos.mermaidrender.settings.MermaidSettings
import com.intellij.platform.backend.documentation.InlineDocumentationProvider
import com.intellij.psi.PsiFile

/**
 * Marks this plugin's providers so they never delegate to each other - or to themselves, which
 * would recurse forever.
 */
internal interface MermaidDocumentationDecorator

/** The providers whose documentation is decorated: everything except this plugin's own. */
internal fun delegateProviders(): List<InlineDocumentationProvider> =
    InlineDocumentationProvider.EP_NAME.extensionList.filterNot { it is MermaidDocumentationDecorator }

/**
 * O(text) pre-filter, so files without a diagram cost close to nothing - this runs for every file
 * the editor renders documentation for.
 */
internal fun mayContainMermaid(file: PsiFile): Boolean =
    MermaidBlockDetector.mayContainMermaid(file.text, MermaidSettings.getInstance().heuristicDetection)
