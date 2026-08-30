package com.github.tvcsantos.mermaidrenderer.html

import org.jsoup.parser.Parser

/**
 * Helpers for diagram text operations, including normalizing it for
 * comparison.
 */
object DiagramText {

    /**
     * Reduces [text] to the form used when comparing diagrams.
     *
     * Decodes HTML entities, trims every line and drops the empty ones. The
     * same diagram then matches whether it was read from rendered HTML, where
     * entities are already decoded and indentation may differ, or from comment
     * source, where it still reads `A --&gt;`.
     *
     * @param text The diagram text to normalize.
     * @return The normalized text, its lines joined by a newline.
     */
    fun normalize(text: String): String =
        Parser.unescapeEntities(text, false)
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
}
