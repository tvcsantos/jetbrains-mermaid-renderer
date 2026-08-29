package com.github.tvcsantos.mermaidrender.inline

import com.github.tvcsantos.mermaidrender.html.MermaidBlockDetector
import com.github.tvcsantos.mermaidrender.render.DiagramCache
import com.github.tvcsantos.mermaidrender.render.DiagramRequest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.platform.backend.documentation.InlineDocumentation
import com.intellij.platform.backend.documentation.InlineDocumentationProvider
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jsoup.Jsoup
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Guards the two assumptions the whole plugin rests on: this provider runs last (so its items win
 * in [com.intellij.codeInsight.documentation.render.DocRenderPassFactory]) and it re-emits what the
 * platform providers produced, rewritten.
 */
class MermaidInlineDocumentationProviderTest : BasePlatformTestCase() {

    fun testProviderIsRegisteredLast() {
        val providers = InlineDocumentationProvider.EP_NAME.extensionList
        val index = providers.indexOfFirst { it is MermaidInlineDocumentationProvider }

        assertTrue("The provider is not registered", index >= 0)
        assertEquals(
            "The provider must run last, otherwise its rendered items are overwritten: " +
                providers.joinToString { it.javaClass.simpleName },
            providers.lastIndex,
            index,
        )
    }

    fun testJavadocDiagramIsRewritten() {
        val file = myFixture.configureByText(
            "Sample.java",
            """
            /**
             * Flow of a sample.
             *
             * <pre class="mermaid">
             * graph TD;
             *   A --> B;
             * </pre>
             */
            public class Sample {}
            """.trimIndent(),
        )

        val html = renderFirstItem(MermaidInlineDocumentationProvider(), file)

        assertNotNull("No inline documentation was produced for the Javadoc comment", html)
        // JCEF is unavailable in tests, so the diagram resolves to a note either way; what matters
        // is that our rewriting ran over the platform's HTML.
        assertTrue(
            "Rewritten HTML did not mention the diagram: $html",
            html!!.contains("Rendering diagram") || html.contains("could not be rendered"),
        )
    }

    /**
     * With the bundled Mermaid plugin present the fence is syntax highlighted and its line breaks
     * become `<br>` elements. Losing them would hand Mermaid one unparseable line.
     */
    fun testHighlightedFenceKeepsItsLineBreaks() {
        val file = myFixture.configureByText(
            "Highlighted.kt",
            """
            /**
             * Sample.
             *
             * ```mermaid
             * stateDiagram-v2
             *     [*] --> Draft
             *     Draft --> [*]
             * ```
             */
            class Highlighted
            """.trimIndent(),
        )

        val platformHtml = renderPlatformHtml(file)
        assertNotNull(platformHtml)
        assertTrue("Expected highlighted code with <br>: $platformHtml", platformHtml!!.contains("<br>"))

        // Highlighting replaces the ```mermaid info string, so the language-mermaid class is gone
        // and a tagged fence is recognised by the keyword heuristic like an untagged one.
        val source = Jsoup.parse(platformHtml).select("pre")
            .firstNotNullOfOrNull { MermaidBlockDetector.mermaidSource(it, heuristics = true) }

        assertNotNull("The diagram was not detected in: $platformHtml", source)
        assertTrue("Line breaks were lost: $source", source!!.lines().size >= 3)
        assertTrue(source.startsWith("stateDiagram-v2"))
    }

    fun testFinderIsRegisteredFirst() {
        val providers = InlineDocumentationProvider.EP_NAME.extensionList
        val index = providers.indexOfFirst { it is MermaidInlineDocumentationFinder }

        assertTrue("The finder is not registered", index >= 0)
        assertEquals(
            "The finder must run first, because the gutter toggle takes the first non-null answer: " +
                providers.joinToString { it.javaClass.simpleName },
            0,
            index,
        )
    }

    /**
     * Toggling one comment from the gutter bypasses the render pass: the platform resolves
     * `findInlineDocumentation` with first-non-null, so the decoration has to be offered there too.
     */
    fun testGutterTogglePathIsDecorated() {
        val file = myFixture.configureByText(
            "Toggle.kt",
            """
            /**
             * ```mermaid
             * graph TD;
             *   A --> B;
             * ```
             */
            class Toggle
            """.trimIndent(),
        )

        val providers = InlineDocumentationProvider.EP_NAME.extensionList
        val range = providers
            .filterNot { it is MermaidInlineDocumentationProvider || it is MermaidInlineDocumentationFinder }
            .flatMap { it.inlineDocumentationItems(file) }
            .first()
            .documentationRange

        // Exactly how com.intellij.codeInsight.documentation.render.findInlineDocumentation resolves.
        val documentation = providers.firstNotNullOfOrNull { it.findInlineDocumentation(file, range) }

        assertNotNull("No provider answered for $range", documentation)
        assertTrue(
            "The toggle path did not get the decorated documentation: ${documentation!!.javaClass.name}",
            documentation is DecoratedInlineDocumentation,
        )
        val html = render(documentation)
        assertTrue(
            "The toggled comment was not rewritten: $html",
            html!!.contains("Rendering diagram") || html.contains("could not be rendered"),
        )
    }

    fun testKdocDiagramIsRewritten() {
        val file = myFixture.configureByText(
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

        val html = renderFirstItem(MermaidInlineDocumentationProvider(), file)

        assertNotNull("No inline documentation was produced for the KDoc comment", html)
        assertTrue(
            "Rewritten HTML did not mention the diagram: $html",
            html!!.contains("Rendering diagram") || html.contains("could not be rendered"),
        )
    }

    /**
     * The full non-browser path: a cached bitmap must end up as an `<img>` in the rendered comment.
     * The Mermaid source is read back from the platform's own HTML so the cache key matches exactly
     * what the rewriter will ask for.
     */
    fun testCachedDiagramIsEmbeddedAsAnImage() {
        val file = myFixture.configureByText(
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

        val platformHtml = renderPlatformHtml(file)
        assertNotNull("No platform provider produced documentation", platformHtml)
        val source = Jsoup.parse(platformHtml!!).select("pre")
            .firstNotNullOfOrNull { MermaidBlockDetector.mermaidSource(it, heuristics = false) }
        assertNotNull("The diagram was not found in the platform HTML: $platformHtml", source)

        val request = DiagramRequest.of(source!!)
        assertNotNull(service<DiagramCache>().put(request.cacheKey, onePixelPng(), 120, 80))

        val html = renderFirstItem(MermaidInlineDocumentationProvider(), file)

        assertNotNull(html)
        assertTrue("Expected an image, got: $html", html!!.contains("<img"))
        assertTrue(html.contains("width=\"120\""))
        assertTrue(html.contains("height=\"80\""))
        assertFalse("The code block should have been replaced: $html", html.contains("Rendering diagram"))
    }

    fun testFileWithoutDiagramsIsIgnored() {
        val file = myFixture.configureByText(
            "Plain.java",
            """
            /** Ordinary documentation. */
            public class Plain {}
            """.trimIndent(),
        )

        assertTrue(MermaidInlineDocumentationProvider().inlineDocumentationItems(file).isEmpty())
    }

    override fun tearDown() {
        try {
            service<DiagramCache>().clear()
        } finally {
            super.tearDown()
        }
    }

    private fun renderFirstItem(provider: MermaidInlineDocumentationProvider, file: PsiFile): String? =
        render(provider.inlineDocumentationItems(file).firstOrNull())

    /** Documentation as the platform renders it, without this plugin's rewriting. */
    private fun renderPlatformHtml(file: PsiFile): String? = render(
        InlineDocumentationProvider.EP_NAME.extensionList
            .filterNot { it is MermaidInlineDocumentationProvider }
            .flatMap { it.inlineDocumentationItems(file) }
            .firstOrNull()
    )

    private fun render(documentation: InlineDocumentation?): String? {
        documentation ?: return null
        return ApplicationManager.getApplication()
            .executeOnPooledThread<String?> {
                ReadAction.compute<String?, RuntimeException> { documentation.renderText() }
            }
            .get()
    }

    private fun onePixelPng(): ByteArray = ByteArrayOutputStream().use { out ->
        ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", out)
        out.toByteArray()
    }
}
