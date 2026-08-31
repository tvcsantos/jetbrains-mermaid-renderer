package com.github.tvcsantos.mermaidrenderer.render

import com.intellij.openapi.util.text.StringUtil

/**
 * Helpers for laying out Diagram rendering errors, including wrapping it to a
 * readable width and rendering it as HTML.
 *
 * For example, Mermaid reports its "Expecting 'AMP', 'COLON', ..." list on a
 * single line, and Swing's HTML renderer will not break a long line on its
 * own, so a tooltip or balloon would otherwise grow as wide as the text.
 */
internal object DiagramErrorText {

    private const val MAX_LINE_LENGTH = 72
    private const val MAX_LINES = 12
    private const val BR_TAG = "<br>"
    private const val ELLIPSIS = "..."

    /**
     * Renders [messages] as HTML, wrapped and escaped.
     *
     * @param messages The messages to show.
     * @return The messages as HTML, a blank line between them.
     */
    fun toHtml(messages: List<String>): String =
            messages.joinToString(BR_TAG + BR_TAG) { message ->
            wrap(message).joinToString(BR_TAG) {
                StringUtil.escapeXmlEntities(it)
            }
        }

    /**
     * Breaks [message] into lines short enough to read.
     *
     * A line is broken at the last word boundary that still leaves it
     * reasonably full. A run without spaces is cut instead. The result is
     * capped at [MAX_LINES], with an ellipsis standing in for the rest.
     *
     * @param message The text to wrap.
     * @return The text as lines of at most [MAX_LINE_LENGTH] characters.
     */
    fun wrap(message: String): List<String> {
        val lines = mutableListOf<String>()

        for (rawLine in message.lines()) {
            var line = rawLine.trim()
            if (line.isEmpty()) continue

            while (line.length > MAX_LINE_LENGTH) {
                if (lines.size >= MAX_LINES) {
                    return lines + ELLIPSIS
                }
                // Break at the last space that still leaves a reasonably full
                // line; a run without spaces - Mermaid's `-------^` position
                // marker - is simply cut.
                val breakAt = line.lastIndexOf(
                    char = ' ',
                    startIndex = MAX_LINE_LENGTH
                ).takeIf { it > MAX_LINE_LENGTH / 2 }
                    ?: MAX_LINE_LENGTH
                lines += line.substring(0, breakAt).trimEnd()
                line = line.substring(breakAt).trimStart()
            }

            if (lines.size >= MAX_LINES) {
                return lines + ELLIPSIS
            }
            lines += line
        }

        return lines
    }
}
