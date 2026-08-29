package com.github.tvcsantos.mermaidrenderer.popup

import com.github.tvcsantos.mermaidrenderer.html.RewriteResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Documentation that keeps up with the diagrams still being rendered.
 *
 * A popup is computed once and shown; unlike a rendered comment there is no inlay to refresh when a
 * diagram arrives a moment later. The platform's answer is an updates flow whose emissions replace
 * the browser content, so this polls the rewrite until nothing is pending and emits whenever the
 * documentation actually changed. Collection is cancelled by the platform when the popup closes.
 */
internal object DiagramUpdates {

    private const val POLL_INTERVAL_MS = 250L
    private const val BUDGET_MS = 30_000L

    fun htmlUpdates(
        initialHtml: String,
        pollIntervalMs: Long = POLL_INTERVAL_MS,
        budgetMs: Long = BUDGET_MS,
        rewrite: () -> RewriteResult,
    ): Flow<String> = flow {
        var shown = initialHtml
        var waited = 0L

        while (waited < budgetMs) {
            delay(pollIntervalMs)
            waited += pollIntervalMs

            val result = rewrite()
            if (result.html != shown) {
                shown = result.html
                emit(shown)
            }
            // Nothing left to wait for: rendered, or failed and reported in the gutter.
            if (result.pending == 0) return@flow
        }
    }
}
