// Decorating documentation this plugin does not own has no public API.
// DocRendererProvider is the platform's own hook for replacing the
// rendered-comment renderer.
@file:Suppress("UnstableApiUsage")

package com.github.tvcsantos.mermaidrenderer.render

import com.intellij.codeInsight.documentation.render.DocRenderItem
import com.intellij.codeInsight.documentation.render.DocRenderer
import com.intellij.codeInsight.documentation.render.DocRendererProvider
import com.intellij.codeInsight.documentation.render.DocRenderItemManagerImpl

/**
 * The single point every rendered doc comment passes through.
 *
 * [DocRenderItemManagerImpl] builds every item with
 * [DocRendererProvider.getInstance]. The platform marks that service
 * `open="true"` for exactly this purpose. Replacing it puts the plugin on the
 * path of the render pass, the gutter toggle and every refresh alike. No
 * competing with other plugins over who owns a comment's documentation.
 */
class MermaidDocRendererProvider : DocRendererProvider {

    override fun provideDocRenderer(item: DocRenderItem): DocRenderer =
        DocRenderer(MermaidDocRenderItem(item))
}
