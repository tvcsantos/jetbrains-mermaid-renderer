// Decorating documentation this plugin does not own has no public API.
// asserts the plugin owns DocRendererProvider.
@file:Suppress("UnstableApiUsage")

package com.github.tvcsantos.mermaidrenderer.render

import com.github.tvcsantos.mermaidrenderer.html.MermaidBlockDetector
import com.github.tvcsantos.mermaidrenderer.html.MermaidFences
import com.github.tvcsantos.mermaidrenderer.html.MermaidHtmlRewriter
import com.intellij.codeInsight.documentation.render.DocRendererProvider
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.platform.backend.documentation.InlineDocumentationProvider
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import org.jsoup.Jsoup
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * The rewriting is driven from [MermaidDocRenderItem], which the platform only reaches through a
 * painted inlay - so these tests check the two halves separately: that the plugin owns the seam,
 * and that the rewrite handles the HTML a real IDE produces.
 *
 * The documentation HTML is obtained through `InlineDocumentationProvider` **in the test only**:
 * production code no longer touches that extension point.
 */
class RenderedDocumentationTest : BasePlatformTestCase() {

    fun testPluginOwnsTheRenderingSeam() {
        assertTrue(
            "DocRendererProvider is ${DocRendererProvider.getInstance().javaClass.name}",
            DocRendererProvider.getInstance() is MermaidDocRendererProvider,
        )
    }

    fun testKdocDiagramIsRewritten() {
        val html = rewrite(
            platformHtml(
                "Sample.kt",
                """
                /**
                 * Order lifecycle.
                 *
                 * ```mermaid
                 * graph TD;
                 *   A --> B;
                 * ```
                 */
                class Sample
                """.trimIndent(),
            )
        )

        assertTrue(
            "The KDoc diagram was not rewritten: $html",
            html.contains("Rendering diagram") || html.contains("could not be rendered"),
        )
    }

    fun testDocumentationWithoutDiagramsIsUntouched() {
        val original = platformHtml(
            "Plain.java",
            """
            /** Ordinary documentation. */
            public class Plain {}
            """.trimIndent(),
        )

        assertEquals(original, rewrite(original))
    }

    /**
     * With the bundled Mermaid plugin present the fence is syntax highlighted and its line breaks
     * become `<br>` elements. Losing them would hand Mermaid one unparseable line.
     */
    fun testHighlightedFenceKeepsItsLineBreaks() {
        val html = platformHtml(
            "Highlighted.kt",
            """
            /**
             * ```mermaid
             * stateDiagram-v2
             *     [*] --> Draft
             *     Draft --> [*]
             * ```
             */
            class Highlighted
            """.trimIndent(),
        )
        assertTrue("Expected highlighted code with <br>: $html", html.contains("<br>"))

        // Highlighting replaces the ```mermaid info string, so the language-mermaid class is gone
        // and a tagged fence is recognized by the keyword heuristic like an untagged one.
        val source = Jsoup.parse(html).select("pre")
            .firstNotNullOfOrNull { MermaidBlockDetector.getMermaidSourceOrNull(it, heuristics = true) }

        assertNotNull("The diagram was not detected in: $html", source)
        assertTrue("Line breaks were lost: $source", source!!.lines().size >= 3)
        assertTrue(source.startsWith("stateDiagram-v2"))
    }

    /**
     * A tagged fence must render even with the keyword heuristic switched off - which needs the
     * marker read from the comment source, because highlighting consumed it before the HTML.
     */
    fun testTaggedFenceIsDetectedWithoutTheHeuristic() {
        val text = """
            /**
             * ```mermaid
             * stateDiagram-v2
             *     [*] --> Draft
             * ```
             */
            class Tagged
        """.trimIndent()
        val html = platformHtml("Tagged.kt", text)
        val blocks = Jsoup.parse(html).select("pre")

        assertNull(
            "Nothing should be detected from the HTML alone: $html",
            blocks.firstNotNullOfOrNull { MermaidBlockDetector.getMermaidSourceOrNull(it, heuristics = false) },
        )

        val tagged = MermaidFences.collectFencedBodies(text)
        val source = blocks.firstNotNullOfOrNull {
            MermaidBlockDetector.getMermaidSourceOrNull(it, heuristics = false, isTagged = tagged::contains)
        }

        assertNotNull("The fence from the comment source was not matched: $html", source)
        assertTrue(source!!.startsWith("stateDiagram-v2"))
    }

    fun testCachedDiagramIsEmbeddedAsAnImage() {
        val html = platformHtml(
            "Cached.java",
            """
            /**
             * <pre class="mermaid">
             * graph TD;
             *   A --> B;
             * </pre>
             */
            public class Cached {}
            """.trimIndent(),
        )
        val source = Jsoup.parse(html).select("pre")
            .firstNotNullOfOrNull { MermaidBlockDetector.getMermaidSourceOrNull(it, heuristics = true) }
        assertNotNull("The diagram was not detected in: $html", source)

        val request = DiagramRequest.of(source!!)
        assertNotNull(service<DiagramCache>().put(request.cacheKey, onePixelPng(), 120, 80))

        val rewritten = rewrite(html)

        assertTrue("Expected an image, got: $rewritten", rewritten.contains("<img"))
        assertTrue(rewritten.contains("width=\"120\""))
        assertTrue(rewritten.contains("height=\"80\""))
        assertFalse("The code block should have been replaced", rewritten.contains("Rendering diagram"))
    }

    override fun tearDown() {
        try {
            service<DiagramCache>().clear()
        } finally {
            super.tearDown()
        }
    }

    /**
     * Exactly what [MermaidDocRenderItem] does to the HTML it is handed, with the progress
     * placeholder turned on so a diagram that has been recognized is observable in the output.
     */
    private fun rewrite(html: String): String = MermaidHtmlRewriter.rewrite(
        html = html,
        heuristics = true,
        requestFor = { DiagramRequest.of(it) },
        resolve = { service<MermaidRenderService>().resolve(it, null) },
        showProgress = true,
    ).html

    /** Documentation HTML as the IDE itself produces it for [text]. */
    private fun platformHtml(fileName: String, text: String): String {
        val file: PsiFile = myFixture.configureByText(fileName, text)
        val documentation = InlineDocumentationProvider.EP_NAME.extensionList
            .flatMap { it.inlineDocumentationItems(file) }
            .firstOrNull()
        assertNotNull("No documentation was produced for $fileName", documentation)

        // renderText() requires a read lock and a background thread.
        val html = ReadAction.nonBlocking<String?> { documentation!!.renderText() }
            .submit(AppExecutorUtil.getAppExecutorService())
            .get()
        assertNotNull("Empty documentation for $fileName", html)
        return html!!
    }

    private fun onePixelPng(): ByteArray = ByteArrayOutputStream().use { out ->
        ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", out)
        out.toByteArray()
    }
}
