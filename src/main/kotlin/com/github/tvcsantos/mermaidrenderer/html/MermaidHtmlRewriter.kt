package com.github.tvcsantos.mermaidrenderer.html

import com.github.tvcsantos.mermaidrenderer.MermaidBundle
import com.github.tvcsantos.mermaidrenderer.render.CachedDiagram
import com.github.tvcsantos.mermaidrenderer.render.DiagramRequest
import com.github.tvcsantos.mermaidrenderer.render.DiagramState
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Outcome of a rewrite; the counts exist so a silent no-op can be diagnosed. */
data class RewriteResult(val html: String, val candidates: Int, val matched: Int)

/**
 * Replaces Mermaid code blocks in rendered documentation HTML with the rendered image.
 *
 * Pure and dependency-free: callers supply how a request is built and how it resolves, so tests
 * run without services or a browser.
 */
object MermaidHtmlRewriter {

    fun rewrite(
        html: String,
        heuristics: Boolean,
        requestFor: (String) -> DiagramRequest,
        resolve: (DiagramRequest) -> DiagramState,
    ): RewriteResult {
        val document = Jsoup.parse(html)
        document.outputSettings().prettyPrint(false)

        val candidates = LinkedHashSet<Element>()
        candidates.addAll(document.select("pre"))
        candidates.addAll(document.select(".mermaid"))

        var changed = false
        var matched = 0
        for (element in candidates) {
            if (!element.hasParent()) continue
            val source = MermaidBlockDetector.mermaidSource(element, heuristics) ?: continue
            matched++
            when (val state = resolve(requestFor(source))) {
                is DiagramState.Ready -> element.replaceWith(image(state.diagram))
                is DiagramState.Failed -> element.after(
                    note(MermaidBundle.message("diagram.failed") + ": " + state.message.take(MAX_ERROR_LENGTH))
                )

                DiagramState.Pending -> element.after(note(MermaidBundle.message("diagram.pending")))
            }
            changed = true
        }

        return RewriteResult(
            html = if (changed) document.html() else html,
            candidates = candidates.size,
            matched = matched,
        )
    }

    private fun image(diagram: CachedDiagram): Element =
        Element("p").appendChild(
            Element("img")
                .attr("src", diagram.url)
                .attr("width", diagram.width.toString())
                .attr("height", diagram.height.toString())
                .attr("alt", MermaidBundle.message("diagram.alt"))
        )

    private fun note(text: String): Element = Element("p").appendChild(Element("i").text(text))

    private const val MAX_ERROR_LENGTH = 300
}
