package com.github.tvcsantos.mermaidrenderer.html

/**
 * Helpers for comment markup operations, including stripping the markers from
 * a source line.
 */
object CommentMarkup {

    /**
     * Removes comment decoration from a single source line.
     *
     * For each known comment style, it removes an opening marker, an optional
     * closing marker, and then the style's per-line continuation marker, such
     * as the leading `*` in KDoc lines like `* graph TD`. The result is
     * trimmed text content.
     *
     * Supported styles: KDoc and block comments, plus the `///` and `//` line forms.
     *
     * @param line The line of text to strip.
     * @return The line without its comment markers.
     */
    fun strip(line: String): String {
        var text = line.trim()
        COMMENT_MARKERS.forEach { (open, middle, close) ->
            if (text.startsWith(open)) {
                text = text.removePrefix(open)
            }
            if (close != null && text.endsWith(close)) {
                text = text.removeSuffix(close)
            }
            text = text.trim()
            if (middle != null && text.startsWith(middle)) {
                text = text.removePrefix(middle).trimStart()
            }
        }
        return text
    }

    private val COMMENT_MARKERS = listOf(
        Triple("/**", "*", "*/"),
        Triple("/*", null, "*/"),
        Triple("///", "///", null),
        Triple("//", "//", null),
    )
}
