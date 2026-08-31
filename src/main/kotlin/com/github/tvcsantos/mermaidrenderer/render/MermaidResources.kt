package com.github.tvcsantos.mermaidrenderer.render

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Files
import java.nio.file.Path

/**
 * The bundled mermaid runtime. JCEF loads the host page from disk over a
 * `file:` URL, so the resources are extracted once per mermaid version into
 * the IDE system directory.
 */
object MermaidResources {

    private val log = logger<MermaidResources>()

    private const val RENDERER = "renderer.html"
    private const val LIBRARY = "mermaid.min.js"

    val version: String by lazy {
        readResource("/mermaid/version.txt")
            ?.toString(Charsets.UTF_8)
            ?.trim()
            .orEmpty()
            .ifEmpty { "unknown" }
    }

    /** `file:` URL of the extracted host page, or `null` when extraction failed. */
    fun rendererUrl(): String? =
        runCatching {
            runtimeDirectory()
                .resolve(RENDERER)
                .toUri()
                .toString()
        }
        .onFailure {
            log.warn("Cannot prepare the mermaid runtime", it)
        }
        .getOrNull()

    private fun runtimeDirectory(): Path {
        val directory = Path.of(
            PathManager.getSystemPath(),
            "mermaid-renderer",
            "runtime",
            version
        )
        Files.createDirectories(directory)

        val library = directory.resolve(LIBRARY)

        val libraryBytes = readResource("/mermaid/$LIBRARY")
            ?: error("$LIBRARY is missing from the plugin")

        if (!Files.exists(library) ||
            Files.size(library) != libraryBytes.size.toLong()) {
            Files.write(library, libraryBytes)
        }

        // Always refreshed: it is small and changes with the plugin, not with the mermaid version.
        val renderer = readResource("/mermaid/$RENDERER")
            ?: error("$RENDERER is missing from the plugin")

        Files.write(
            directory.resolve(RENDERER),
            renderer
        )

        return directory
    }

    private fun readResource(path: String): ByteArray? =
        MermaidResources::class.java.getResourceAsStream(path)?.use {
            it.readBytes()
        }
}
