package com.github.tvcsantos.mermaidrenderer.render

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager

/**
 * Recomputes the open rendered comments when the IDE theme changes.
 *
 * Diagram colors follow the theme, and the theme is part of the cache key.
 * Without a refresh the comments keep showing the previous bitmaps.
 */
class MermaidThemeChangeListener : LafManagerListener, EditorColorsListener {

    override fun lookAndFeelChanged(source: LafManager) =
        refreshOpenFiles()

    override fun globalSchemeChange(scheme: EditorColorsScheme?) =
        refreshOpenFiles()

    private fun refreshOpenFiles() {
        // also bumps the rewrite generation
        service<MermaidRenderService>().forgetFailures()
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            val refresher = project.service<DocRenderRefresher>()
            FileEditorManager.getInstance(project)
                .openFiles
                .forEach(refresher::scheduleRefresh)
        }
    }
}
