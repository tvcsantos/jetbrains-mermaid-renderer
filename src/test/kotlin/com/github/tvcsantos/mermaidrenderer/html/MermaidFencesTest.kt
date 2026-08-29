package com.github.tvcsantos.mermaidrenderer.html

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MermaidFencesTest {

    @Test
    fun `reads a fence out of a kdoc comment`() {
        val bodies = MermaidFences.taggedBodies(
            """
            /**
             * Order lifecycle.
             *
             * ```mermaid
             * graph TD;
             *   A --> B;
             * ```
             */
            """.trimIndent()
        )

        assertEquals(setOf("graph TD;\nA --> B;"), bodies)
    }

    @Test
    fun `reads a fence out of a markdown javadoc comment`() {
        val bodies = MermaidFences.taggedBodies(
            """
            /// ```mermaid
            /// flowchart LR
            ///   A --> B
            /// ```
            """.trimIndent()
        )

        assertEquals(setOf("flowchart LR\nA --> B"), bodies)
    }

    @Test
    fun `ignores fences of other languages`() {
        val bodies = MermaidFences.taggedBodies(
            """
            /**
             * ```kotlin
             * val x = 1
             * ```
             */
            """.trimIndent()
        )

        assertTrue(bodies.isEmpty())
    }

    @Test
    fun `handles tildes, longer fences and an info suffix`() {
        val bodies = MermaidFences.taggedBodies(
            """
            /**
             * ~~~mermaid
             * pie title Votes
             * ~~~
             *
             * ````mermaid title="flow"
             * graph LR;
             * ````
             */
            """.trimIndent()
        )

        assertEquals(setOf("pie title Votes", "graph LR;"), bodies)
    }

    @Test
    fun `normalization ignores indentation and blank lines`() {
        assertEquals(
            MermaidFences.normalize("graph TD;\n    A --> B;\n"),
            MermaidFences.normalize("  graph TD;\n\n  A --> B;"),
        )
    }
}
