package com.github.tvcsantos.mermaidrenderer.render

import com.intellij.codeInsight.documentation.render.DocRenderItem
import com.intellij.codeInsight.documentation.render.DocRenderer
import com.intellij.codeInsight.documentation.render.DocRendererProvider

/**
 * The single point every rendered doc comment passes through.
 *
 * `DocRenderItemManagerImpl` builds every item with `DocRendererProvider.getInstance()`, so
 * replacing this service (the platform marks it `open="true"` for exactly this purpose) puts the
 * plugin on the path of the render pass, the gutter toggle and every refresh alike - without
 * competing with other plugins over who owns a comment's documentation.
 */
class MermaidDocRendererProvider : DocRendererProvider {

    override fun provideDocRenderer(item: DocRenderItem): DocRenderer = DocRenderer(MermaidDocRenderItem(item))
}
