package com.github.tvcsantos.mermaidrenderer.render

import com.github.tvcsantos.mermaidrenderer.settings.MermaidSettings
import com.intellij.openapi.components.service
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class MermaidErrorLineMarkerProviderTest : BasePlatformTestCase() {

    private val provider = MermaidErrorLineMarkerProvider()

    override fun setUp() {
        super.setUp()
        // The marker is opt-in; these tests are about what it does once enabled.
        MermaidSettings.getInstance().showErrorMarker = true
    }

    override fun tearDown() {
        try {
            MermaidSettings.getInstance().showErrorMarker = false
            service<MermaidRenderService>().forgetFailures()
        } finally {
            super.tearDown()
        }
    }

    fun testTheMarkerIsOffByDefault() {
        MermaidSettings.getInstance().showErrorMarker = false
        recordFailure("graph TD;\n  A -->", "Parse error on line 2")

        assertEmpty(markers(JAVADOC))
    }

    fun testNoMarkerWhileNothingHasFailed() {
        assertEmpty(markers(JAVADOC))
    }

    fun testMarkerAppearsForTheCommentHoldingTheBrokenDiagram() {
        recordFailure("graph TD;\n  A -->", "Parse error on line 2: graph TD; A -->")

        val found = markers(JAVADOC)

        assertSize(1, found)
        assertTrue(
            "The message is missing: ${found[0].lineMarkerTooltip}",
            found[0].lineMarkerTooltip!!.contains("Parse error"),
        )
    }

    fun testMarkerAppearsForAKdocFence() {
        recordFailure("stateDiagram-v2\n  [*] -->", "Parse error on line 2")

        val found = markers(
            """
            /**
             * ```mermaid
             * stateDiagram-v2
             *   [*] -->
             * ```
             */
            class Broken
            """.trimIndent(),
            "Broken.kt",
        )

        assertSize(1, found)
    }

    fun testCommentsOfOtherDiagramsAreNotMarked() {
        recordFailure("graph TD;\n  A -->", "Parse error on line 2")

        val found = markers(
            """
            /**
             * <pre class="mermaid">
             * flowchart LR
             *   X --> Y
             * </pre>
             */
            public class Other {}
            """.trimIndent(),
            "Other.java",
        )

        assertEmpty("A healthy diagram must not be marked", found)
    }

    /**
     * Javadoc is written with entities - `A --&gt;` - while the diagram text is recorded after the
     * renderer decoded them. Both sides have to normalize to the same thing.
     */
    fun testMarkerAppearsWhenTheCommentUsesHtmlEntities() {
        recordFailure("graph TD;\n  A -->", "Parse error on line 2")

        val found = markers(
            """
            /**
             * <pre class="mermaid">
             * graph TD;
             *   A --&gt;
             * </pre>
             */
            public class Escaped {}
            """.trimIndent(),
            "Escaped.java",
        )

        assertSize(1, found)
    }

    fun testTheMarkerFollowsTheDiagramBeingFixed() {
        recordFailure("graph TD;\n  A -->", "Parse error on line 2")

        assertSize(
            1,
            markers(
                """
                /**
                 * <pre class="mermaid">
                 * graph TD;
                 *   A --&gt;
                 * </pre>
                 */
                public class Still {}
                """.trimIndent(),
                "Still.java",
            ),
        )

        assertEmpty(
            "The fixed diagram must not keep the marker",
            markers(
                """
                /**
                 * <pre class="mermaid">
                 * graph TD;
                 *   A --&gt; B;
                 * </pre>
                 */
                public class Fixed {}
                """.trimIndent(),
                "Fixed.java",
            ),
        )
    }

    private fun recordFailure(source: String, message: String) =
        service<MermaidRenderService>().recordFailure(DiagramRequest.of(source), message)

    /** Every marker the daemon would collect for [text], by asking about each leaf in turn. */
    private fun markers(text: String, fileName: String = "Broken.java"): List<LineMarkerInfo<*>> {
        val file = myFixture.configureByText(fileName, text)
        val found = mutableListOf<LineMarkerInfo<*>>()
        var leaf: PsiElement? = PsiTreeUtil.getDeepestFirst(file)
        while (leaf != null) {
            provider.getLineMarkerInfo(leaf)?.let(found::add)
            leaf = PsiTreeUtil.nextLeaf(leaf)
        }
        return found
    }

    private companion object {
        val JAVADOC = """
            /**
             * <pre class="mermaid">
             * graph TD;
             *   A -->
             * </pre>
             */
            public class Broken {}
        """.trimIndent()
    }
}
