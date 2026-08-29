package com.github.tvcsantos.mermaidrenderer.settings

import com.github.tvcsantos.mermaidrenderer.MermaidBundle
import com.github.tvcsantos.mermaidrenderer.render.DiagramCache
import com.github.tvcsantos.mermaidrenderer.render.MermaidRenderService
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty

class MermaidConfigurable : BoundConfigurable(MermaidBundle.message("settings.title")) {

    override fun createPanel(): DialogPanel {
        val settings = MermaidSettings.getInstance()
        return panel {
            row {
                checkBox(MermaidBundle.message("settings.heuristic"))
                    .bindSelected(settings::heuristicDetection)
                    .comment(MermaidBundle.message("settings.heuristic.comment"))
            }
            row(MermaidBundle.message("settings.theme")) {
                comboBox(ThemeMode.entries.toList())
                    .bindItem(settings::themeMode.toNullableProperty())
            }
            row(MermaidBundle.message("settings.maxWidth")) {
                intTextField(200..4000).bindIntText(settings::maxDiagramWidth)
            }
            row(MermaidBundle.message("settings.timeout")) {
                intTextField(2..120).bindIntText(settings::renderTimeoutSeconds)
            }
            row(MermaidBundle.message("settings.cacheLimit")) {
                intTextField(4..4096).bindIntText(settings::diskCacheLimitMb)
            }
            row {
                button(MermaidBundle.message("settings.clearCache")) {
                    service<DiagramCache>().clear()
                    service<MermaidRenderService>().forgetFailures()
                }
            }
        }
    }

    override fun apply() {
        super.apply()
        // Theme, width and scale are part of the cache key, so previous failures deserve a retry.
        service<MermaidRenderService>().forgetFailures()
    }
}
