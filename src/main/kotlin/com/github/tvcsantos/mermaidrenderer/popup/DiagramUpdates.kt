package com.github.tvcsantos.mermaidrenderer.popup

import com.github.tvcsantos.mermaidrenderer.html.RewriteResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Helpers for documentation updates, including a flow that emits the
 * documentation again as pending diagrams finish rendering.
 */
internal object DiagramUpdates {

    private val POLL_INTERVAL_MS = 250L.milliseconds
    private val BUDGET_MS = 30_000L.milliseconds

    /**
     * Emits [initialHtml] rewritten, each time a pending diagram changes it.
     *
     * A popup is computed once and shown, so unlike a rendered comment there
     * is no inlay to refresh when a diagram arrives a moment later. The
     * platform replaces the content from this flow instead, and cancels
     * collection when the popup closes. The rewrite is polled until nothing is
     * pending, either because every diagram rendered or because the rest
     * failed, and identical documentation is never emitted twice.
     *
     * @param initialHtml The documentation already on screen.
     * @param pollIntervalMs How long to wait between rewrites.
     * @param budgetMs How long to keep polling before giving up.
     * @param rewrite Produces the documentation as it stands now.
     * @return A flow of documentation to replace what is shown.
     */
    fun htmlUpdates(
        initialHtml: String,
        pollIntervalMs: Duration = POLL_INTERVAL_MS,
        budgetMs: Duration = BUDGET_MS,
        rewrite: () -> RewriteResult,
    ): Flow<String> = flow {
        var shown = initialHtml
        var waited = 0L.milliseconds

        while (waited < budgetMs) {
            delay(pollIntervalMs)
            waited += pollIntervalMs

            val result = rewrite()
            if (result.html != shown) {
                shown = result.html
                emit(shown)
            }
            // Nothing left to wait for: rendered,
            // or failed and reported in the gutter.
            if (result.pending == 0) return@flow
        }
    }
}
