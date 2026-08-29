package com.github.tvcsantos.mermaidrenderer.render

import com.intellij.openapi.util.text.StringUtil

/**
 * Lays out a Mermaid error for a tooltip or balloon.
 *
 * Mermaid puts its whole "Expecting 'AMP', 'COLON', ..." list on one line, and Swing's HTML renderer
 * will not break a long line on its own - the balloon simply grows as wide as the text. So the text
 * is wrapped here, at word boundaries, and capped.
 */
internal object DiagramErrorText {

    private const val MAX_LINE_LENGTH = 72
    private const val MAX_LINES = 12

    fun toHtml(messages: List<String>): String = messages.joinToString("<br><br>") { message ->
        wrap(message).joinToString("<br>") { StringUtil.escapeXmlEntities(it) }
    }

    /** The message as display lines: word-wrapped to [MAX_LINE_LENGTH] and capped at [MAX_LINES]. */
    fun wrap(message: String): List<String> {
        val lines = mutableListOf<String>()

        for (rawLine in message.lines()) {
            var line = rawLine.trim()
            if (line.isEmpty()) continue

            while (line.length > MAX_LINE_LENGTH) {
                if (lines.size >= MAX_LINES) return lines + ELLIPSIS
                // Break at the last space that still leaves a reasonably full line; a run without
                // spaces - Mermaid's `-------^` position marker - is simply cut.
                val breakAt = line.lastIndexOf(' ', MAX_LINE_LENGTH)
                    .takeIf { it > MAX_LINE_LENGTH / 2 }
                    ?: MAX_LINE_LENGTH
                lines += line.substring(0, breakAt).trimEnd()
                line = line.substring(breakAt).trimStart()
            }

            if (lines.size >= MAX_LINES) return lines + ELLIPSIS
            lines += line
        }

        return lines
    }

    private const val ELLIPSIS = "..."
}
