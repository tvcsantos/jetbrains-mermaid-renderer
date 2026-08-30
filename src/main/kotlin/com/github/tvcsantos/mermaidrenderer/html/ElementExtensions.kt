package com.github.tvcsantos.mermaidrenderer.html

import org.jsoup.nodes.Element

/**
 * Returns the text of this element, without surrounding blank lines.
 *
 * Reads [Element.wholeText] rather than `text()`: when a code block is syntax
 * highlighted its line breaks are `<br>` elements, and `text()` would collapse
 * the block into a single line.
 *
 * @return The element text, its own line breaks preserved.
 */
fun Element.blockText(): String = wholeText().trim('\n', '\r').trimEnd()
