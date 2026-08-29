package com.github.tvcsantos.mermaidrenderer.popup

import com.github.tvcsantos.mermaidrenderer.render.DiagramCache
import com.github.tvcsantos.mermaidrenderer.render.DiagramRequest
import com.github.tvcsantos.mermaidrenderer.settings.MermaidSettings
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.model.Pointer
import com.intellij.openapi.components.service
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class MermaidDocumentationTargetTest : BasePlatformTestCase() {

    private val diagram = "graph TD; A-->B;"
    private val documentation = "<div class='content'><pre class=\"mermaid\">graph TD; A--&gt;B;</pre></div>"

    override fun tearDown() {
        try {
            MermaidSettings.getInstance().showRenderingProgress = false
            service<DiagramCache>().clear()
        } finally {
            super.tearDown()
        }
    }

    fun testThePluginOwnsTheDocumentationSeam() {
        assertTrue(
            "IdeDocumentationTargetProvider is ${IdeDocumentationTargetProvider.getInstance(project).javaClass.name}",
            IdeDocumentationTargetProvider.getInstance(project) is MermaidIdeDocumentationTargetProvider,
        )
    }

    fun testACachedDiagramBecomesAnImageInTheDocumentation() {
        val request = DiagramRequest.of(diagram)
        service<DiagramCache>().put(request.cacheKey, onePixelPng(), 120, 80)

        val html = (MermaidDocumentationTarget(FakeTarget(documentation)).computeDocumentation() as DocumentationData).html

        assertTrue("Expected an image, got: $html", html.contains("<img"))
        assertTrue(html.contains("width=\"120\""))
    }

    fun testDocumentationWithoutDiagramsIsUntouched() {
        val plain = "<div class='content'><p>Nothing to see</p></div>"

        val html = (MermaidDocumentationTarget(FakeTarget(plain)).computeDocumentation() as DocumentationData).html

        assertEquals(plain, html)
    }

    fun testAPendingDiagramFollowsTheProgressSetting() {
        MermaidSettings.getInstance().showRenderingProgress = true

        val html = (MermaidDocumentationTarget(FakeTarget(documentation)).computeDocumentation() as DocumentationData).html

        assertTrue("Expected the placeholder, got: $html", html.contains("Rendering diagram"))
    }

    /** Stands in for whatever provider owns the documentation of the element under the caret. */
    private class FakeTarget(private val html: String) : DocumentationTarget {
        override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)
        override fun computePresentation(): TargetPresentation = TargetPresentation.builder("test").presentation()
        override fun computeDocumentation(): DocumentationResult = DocumentationResult.documentation(html)
    }

    private fun onePixelPng(): ByteArray = ByteArrayOutputStream().use { out ->
        ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", out)
        out.toByteArray()
    }
}
