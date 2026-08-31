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
        var showErrorMarker: Boolean = false

        @JvmField
        var showRenderingProgress: Boolean = false

        @JvmField
        var themeMode: String = ThemeMode.AUTO.name

        @JvmField
        var maxDiagramWidth: Int = DEFAULT_MAX_DIAGRAM_WIDTH

        @JvmField
        var renderTimeoutSeconds: Int = DEFAULT_RENDER_TIMEOUT_SECONDS

        @JvmField
        var diskCacheLimitMb: Int = DEFAULT_DISK_CACHE_LIMIT_MB
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

    /**
     * When on, a diagram that has not been rendered yet is marked as such in
     * the comment. When off, the comment is left blank until the diagram is
     * ready.
     *
     * Default: off, because a diagram usually appears within a moment, and
     * the note is mostly useful when diagnosing why one does not.
     */
    var showRenderingProgress: Boolean
        get() = state.showRenderingProgress
        set(value) {
            state.showRenderingProgress = value
        }

    /**
     * When on, a diagram that fails to render is marked as such in the
     * comment. When off, the comment is left blank until the diagram is ready,
     * and if it fails, the comment is left blank.
     *
     * In both settings, the error is logged, and can be seen in the IDE log.
     *
     * Default: off, avoid cluttering the comment with error messages,
     * which are usually transient.
     */
    var showErrorMarker: Boolean
        get() = state.showErrorMarker
        set(value) {
            state.showErrorMarker = value
        }

    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(state.themeMode)
        }.getOrDefault(ThemeMode.AUTO)
        set(value) {
            state.themeMode = value.name
        }

    var maxDiagramWidth: Int
        get() = state.maxDiagramWidth.coerceIn(MIN_WIDTH, MAX_WIDTH)
        set(value) {
            state.maxDiagramWidth = value.coerceIn(MIN_WIDTH, MAX_WIDTH)
        }

    var renderTimeoutSeconds: Int
        get() = state.renderTimeoutSeconds.coerceIn(MIN_TIMEOUT, MAX_TIMEOUT)
        set(value) {
            state.renderTimeoutSeconds = value.coerceIn(MIN_TIMEOUT, MAX_TIMEOUT)
        }

    var diskCacheLimitMb: Int
        get() = state.diskCacheLimitMb.coerceIn(MIN_CACHE_MB, MAX_CACHE_MB)
        set(value) {
            state.diskCacheLimitMb = value.coerceIn(MIN_CACHE_MB, MAX_CACHE_MB)
        }

    companion object {
        fun getInstance(): MermaidSettings = service()

        private const val MIN_WIDTH = 200
        private const val MAX_WIDTH = 4000
        private const val MIN_TIMEOUT = 2
        private const val MAX_TIMEOUT = 120
        private const val MIN_CACHE_MB = 4
        private const val MAX_CACHE_MB = 4096

        private const val DEFAULT_MAX_DIAGRAM_WIDTH = 760
        private const val DEFAULT_RENDER_TIMEOUT_SECONDS = 15
        private const val DEFAULT_DISK_CACHE_LIMIT_MB = 64
    }
}
