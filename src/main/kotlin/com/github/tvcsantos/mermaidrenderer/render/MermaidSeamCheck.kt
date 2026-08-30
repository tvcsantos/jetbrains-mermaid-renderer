// Decorating documentation this plugin does not own has no public API.
// Reads DocRendererProvider to verify the plugin still owns that seam.
@file:Suppress("UnstableApiUsage")

package com.github.tvcsantos.mermaidrenderer.render

import com.github.tvcsantos.mermaidrenderer.MermaidBundle
import com.intellij.codeInsight.documentation.render.DocRendererProvider
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.util.concurrent.atomic.AtomicBoolean

private val checked = AtomicBoolean(false)

/**
 * Verifies that the plugin actually owns the rendering seam.
 *
 * A service has exactly one owner: if another plugin also replaces `DocRendererProvider`, or the
 * platform stops routing through it, this plugin would quietly do nothing at all - which is
 * precisely the failure mode that made earlier bugs so expensive to find. So it says so instead.
 */
class MermaidSeamCheck : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (!checked.compareAndSet(false, true)) return

        val provider = runCatching { DocRendererProvider.getInstance() }.getOrNull()
        if (provider is MermaidDocRendererProvider) return

        val owner = provider?.javaClass?.name ?: "none"
        logger.warn("Mermaid Renderer does not own DocRendererProvider (owner: $owner)")

        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                MermaidBundle.message("seam.lost.title"),
                MermaidBundle.message("seam.lost.content", owner),
                NotificationType.WARNING,
            )
            .notify(project)
    }

    private companion object {
        const val NOTIFICATION_GROUP = "Mermaid Renderer"
        val logger = logger<MermaidSeamCheck>()
    }
}