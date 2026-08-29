package com.github.tvcsantos.mermaidrender.render

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.imageio.ImageIO

/**
 * Exercises the real browser pipeline: mermaid.js -> SVG -> canvas -> PNG.
 *
 * Opt-in, because it starts JCEF: run with `./gradlew test -Dmermaid.jcef.test=true`.
 */
class JcefMermaidRendererTest : BasePlatformTestCase() {

    /** Rendering blocks and needs EDT free to create the browser, so the test may not own EDT. */
    override fun runInDispatchThread(): Boolean = false

    private val enabled get() = System.getProperty("mermaid.jcef.test") == "true"

    fun testRendersAFlowchart() {
        if (!enabled) return

        val outcome = render(
            """
            graph TD;
              A[Start] --> B{Choice};
              B -->|yes| C[Done];
              B -->|no| A;
            """.trimIndent()
        )

        if (outcome !is RenderOutcome.Success) {
            fail("Expected a rendered diagram but got: $outcome")
            return
        }
        val success: RenderOutcome.Success = outcome
        val image = ImageIO.read(success.png.inputStream())
        assertNotNull("The produced bytes are not a readable PNG", image)
        assertTrue("Diagram is suspiciously small: ${success.width}x${success.height}", success.width > 50)
        // Rasterized at scale, so the bitmap is larger than the logical size it is displayed at.
        assertTrue(image.width >= success.width)
    }

    fun testReportsMermaidErrors() {
        if (!enabled) return

        val outcome = render("graph TD;\n  A -->")

        assertTrue("Expected a failure, got: $outcome", outcome is RenderOutcome.Failure)
    }

    private fun render(source: String): RenderOutcome = service<JcefMermaidRenderer>().render(
        DiagramRequest(
            source = source,
            theme = "default",
            background = "#ffffff",
            maxWidth = 760,
            scale = 2.0,
            mermaidVersion = MermaidResources.version,
        )
    )
}
