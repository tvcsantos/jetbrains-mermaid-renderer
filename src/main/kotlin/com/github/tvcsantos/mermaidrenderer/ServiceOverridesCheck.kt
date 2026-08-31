// Decorating documentation this plugin does not own has no public API.
// Reads the two platform services the plugin replaces to verify its overrides
// are in effect.
@file:Suppress("UnstableApiUsage")

package com.github.tvcsantos.mermaidrenderer

import com.github.tvcsantos.mermaidrenderer.popup.MermaidIdeDocumentationTargetProvider
import com.github.tvcsantos.mermaidrenderer.render.MermaidDocRendererProvider
import com.intellij.codeInsight.documentation.render.DocRendererProvider
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.util.concurrent.atomic.AtomicBoolean

private val checked = AtomicBoolean(false)

/**
 * Verifies that the plugin's service overrides are in effect.
 *
 * A platform service has exactly one owner. Another plugin replacing
 * [DocRendererProvider] or [IdeDocumentationTargetProvider] leaves this plugin
 * silently doing nothing on that surface, which is the failure mode that made
 * earlier bugs expensive to find. So it is reported instead.
 */
class ServiceOverridesCheck : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (!checked.compareAndSet(false, true)) return

        val lost = buildList {
            val renderer = instanceOrNull {
                DocRendererProvider.getInstance()
            }
            if (renderer !is MermaidDocRendererProvider) {
                add(
                    MermaidBundle.message(
                        "override.lost.renderedComments",
                        nameOf(renderer)
                    )
                )
            }

            val popup = instanceOrNull {
                IdeDocumentationTargetProvider.getInstance(project)
            }
            if (popup !is MermaidIdeDocumentationTargetProvider) {
                add(
                    MermaidBundle.message(
                        "override.lost.popup",
                        nameOf(popup)
                    )
                )
            }
        }

        if (lost.isEmpty()) return

        logger.warn(
            "Mermaid Renderer lost a service override. ${
                lost.joinToString(" ")
            }"
        )

        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                MermaidBundle.message("override.lost.title"),
                MermaidBundle.message(
                    "override.lost.content",
                    lost.joinToString("<br>")
                ),
                NotificationType.WARNING,
            )
            .notify(project)
    }

    /** The service, or `null` when it cannot be obtained at all. */
    private fun instanceOrNull(service: () -> Any?): Any? =
        runCatching(service).getOrNull()

    private fun nameOf(service: Any?): String =
        service?.javaClass?.name ?: "none"

    private companion object {
        const val NOTIFICATION_GROUP = "Mermaid Renderer"
        val logger = logger<ServiceOverridesCheck>()
    }
}
