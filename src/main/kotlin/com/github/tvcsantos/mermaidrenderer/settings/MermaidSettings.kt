package com.github.tvcsantos.mermaidrenderer.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * Mermaid theme to render with. [AUTO] follows the editor background.
 */
enum class ThemeMode(val displayName: String, val mermaidTheme: String?) {
    AUTO("Match IDE theme", null),
    DEFAULT("Default", "default"),
    DARK("Dark", "dark"),
    NEUTRAL("Neutral", "neutral"),
    FOREST("Forest", "forest"),
    BASE("Base", "base");

    override fun toString(): String = displayName
}

@Service(Service.Level.APP)
@State(name = "MermaidRenderer", storages = [Storage("mermaid-renderer.xml")])
class MermaidSettings : PersistentStateComponent<MermaidSettings.State> {

    class State {
        @JvmField
        var heuristicDetection: Boolean = true

        @JvmField
        var themeMode: String = ThemeMode.AUTO.name

        @JvmField
        var maxDiagramWidth: Int = 760

        @JvmField
        var renderTimeoutSeconds: Int = 15

        @JvmField
        var diskCacheLimitMb: Int = 64
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var heuristicDetection: Boolean
        get() = state.heuristicDetection
        set(value) {
            state.heuristicDetection = value
        }

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(state.themeMode) }.getOrDefault(ThemeMode.AUTO)
        set(value) {
            state.themeMode = value.name
        }

    var maxDiagramWidth: Int
        get() = state.maxDiagramWidth.coerceIn(200, 4000)
        set(value) {
            state.maxDiagramWidth = value.coerceIn(200, 4000)
        }

    var renderTimeoutSeconds: Int
        get() = state.renderTimeoutSeconds.coerceIn(2, 120)
        set(value) {
            state.renderTimeoutSeconds = value.coerceIn(2, 120)
        }

    var diskCacheLimitMb: Int
        get() = state.diskCacheLimitMb.coerceIn(4, 4096)
        set(value) {
            state.diskCacheLimitMb = value.coerceIn(4, 4096)
        }

    companion object {
        fun getInstance(): MermaidSettings = service()
    }
}
