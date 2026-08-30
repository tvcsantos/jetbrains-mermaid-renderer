package com.github.tvcsantos.mermaidrenderer.html

import com.github.tvcsantos.mermaidrenderer.html.MermaidFences.normalize
import org.jsoup.parser.Parser

object Decorations {

    /**
     * Removes comment decoration from a single source line.
     *
     * For each known comment style, it removes an opening marker, an optional
     * closing marker, and then the style's per-line continuation marker, such
     * as the leading `*` in KDoc lines like `* graph TD`. The result is
     * trimmed text content.
     *
     * Currently supported markers are: `/** ... */`, `/* ... */`, `///`, and
     * `//`.
     *
     * @param line The line of text to undecorate.
     * @return The undecorated line of text.
     */
    fun undecorate(line: String): String {
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

object Fences {

    private val ANY_FENCE = Regex(pattern = "^(`{3,}|~{3,}).*$")

    private val HTML_BLOCK = Regex(
        pattern = "<pre[^>]*class=[\"']([^\"']*)[\"'][^>]*>(.*?)</pre>",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /**
     * Every block in [commentText] that could be a diagram: any fence, plus
     * HTML `<pre>` blocks.
     *
     * Used to ask which of a comment's diagrams failed, where the tag is not
     * the deciding factor.
     */
    fun candidateBodies(commentText: String): Set<String> =
        buildSet {
            collectFencedBodiesTo(
                commentText,
                opening = ANY_FENCE,
                destination = this
            )
            collectHtmlBodiesTo(
                commentText,
                destination = this
            )
        }

    /**
     * Collects normalized bodies of HTML `<pre>` blocks found in
     * [commentText].
     *
     * Each line is undecorated before scanning, so comment markers do not
     * interfere with the regex match. Only non-empty normalized bodies are
     * added to [destination].
     *
     * @param commentText The comment source to scan.
     * @param destination The set to which normalized bodies are added.
     * @return [destination] for convenience.
     */
    private fun collectHtmlBodiesTo(
        commentText: String,
        destination: MutableSet<String>
    ): MutableSet<String> {
        val undecorated = commentText.lineSequence()
            .joinToString("\n") {
                Decorations.undecorate(it)
            }
        return HTML_BLOCK.findAll(undecorated)
            .map { normalize(it.groupValues[2]) }
            .filter { it.isNotEmpty() }
            .toCollection(destination)
    }

    /**
     * Collects the body of every fence in [commentText] whose opening line
     * matches [opening].
     *
     * The marker that opened a fence is remembered, so a longer fence can hold
     * a shorter one without closing early. An unterminated fence contributes
     * nothing.
     *
     * @param commentText The comment source to scan.
     * @param opening A regex that matches the opening line of a fence, with
     * the marker in group 1.
     * @param destination The set to which normalized bodies are added.
     * @return [destination] for convenience.
     */
    fun collectFencedBodiesTo(
        commentText: String,
        opening: Regex,
        destination: MutableSet<String>
    ): MutableSet<String> {
        val body = StringBuilder()
        var fence: String? = null

        for (rawLine in commentText.lineSequence()) {
            val line = Decorations.undecorate(rawLine)
            val open = fence
            if (open == null) {
                val match = opening.find(line) ?: continue
                fence = match.groupValues[1]
                body.clear()
            } else if (line.startsWith(open)) {
                normalize(body.toString()).takeIf {
                    it.isNotEmpty()
                }?.let(destination::add)
                fence = null
            } else {
                body.appendLine(line)
            }
        }

        return destination
    }
}

/**
 * Mermaid comment utilities for scanning raw doc-comment source.
 *
 * The rendered HTML cannot be trusted for this: when a Mermaid language plugin
 * is installed the platform syntax-highlights fences and can consume the info
 * string, so explicit tagging may be lost. Reading the source preserves the
 * original comment content and keeps fence detection reliable.
 */
object MermaidFences {

    private val MERMAID_FENCE = Regex(
        pattern = "^(`{3,}|~{3,})\\s*mermaid\\b.*$",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * Returns the normalized bodies of fenced code blocks explicitly tagged as
     * `mermaid` in the raw doc comment source.
     *
     * @param commentText The comment source to scan.
     * @return The normalized bodies of every ```` ```mermaid ```` fence in
     * [commentText].
     */
    fun collectFencedBodies(commentText: String): Set<String> =
        Fences.collectFencedBodiesTo(
            commentText,
            opening = MERMAID_FENCE,
            destination = mutableSetOf()
        )

    /**
     * Comparable form of a diagram.
     *
     * The two sides differ in more than indentation: text taken from rendered HTML has had its
     * entities decoded, while the comment source still reads `A --&gt;`. Both are decoded here so a
     * diagram recorded from one side can be found from the other.
     */
    fun normalize(text: String): String =
        Parser.unescapeEntities(text, false)
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
}
