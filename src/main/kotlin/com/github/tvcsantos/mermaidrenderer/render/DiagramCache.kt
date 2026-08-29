package com.github.tvcsantos.mermaidrenderer.render

import com.github.tvcsantos.mermaidrenderer.settings.MermaidSettings
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.deleteIfExists
import kotlin.io.path.extension
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.name

/** A rasterized diagram on disk. [width]/[height] are logical (unscaled) pixels. */
data class CachedDiagram(val path: Path, val width: Int, val height: Int) {
    val url: String get() = path.toUri().toString()
}

/**
 * PNG cache keyed by [DiagramRequest.cacheKey]. Survives restarts so reopening a file shows its
 * diagrams immediately. The logical size is encoded in the file name, which keeps lookups free of
 * image decoding.
 */
@Service(Service.Level.APP)
class DiagramCache {

    private val log = logger<DiagramCache>()
    private val root: Path = Path.of(PathManager.getSystemPath(), "mermaid-renderer", "cache")
    private val index = ConcurrentHashMap<String, CachedDiagram>()
    private val scanned = AtomicBoolean(false)

    fun get(key: String): CachedDiagram? {
        ensureScanned()
        val cached = index[key] ?: return null
        if (!Files.exists(cached.path)) {
            index.remove(key)
            return null
        }
        return cached
    }

    fun put(key: String, png: ByteArray, width: Int, height: Int): CachedDiagram? {
        ensureScanned()
        return try {
            Files.createDirectories(root)
            val file = root.resolve("$key-${width}x$height.png")
            Files.write(file, png)
            CachedDiagram(file, width, height).also { index[key] = it }
        } catch (e: Exception) {
            log.warn("Cannot store a rendered diagram", e)
            null
        }
    }

    fun clear() {
        index.clear()
        runCatching {
            if (Files.isDirectory(root)) {
                Files.list(root).use { files -> files.forEach { it.deleteIfExists() } }
            }
        }.onFailure { log.warn("Cannot clear the diagram cache", it) }
    }

    private fun ensureScanned() {
        if (!scanned.compareAndSet(false, true)) return
        runCatching {
            if (!Files.isDirectory(root)) return
            Files.list(root).use { files ->
                files.forEach { path -> parse(path)?.let { (key, diagram) -> index[key] = diagram } }
            }
            prune()
        }.onFailure { log.warn("Cannot read the diagram cache", it) }
    }

    /** File names look like `<sha256>-<width>x<height>.png`. */
    private fun parse(path: Path): Pair<String, CachedDiagram>? {
        if (path.extension != "png") return null
        val name = path.name.removeSuffix(".png")
        val separator = name.lastIndexOf('-').takeIf { it > 0 } ?: return null
        val size = name.substring(separator + 1).split('x')
        if (size.size != 2) return null
        val width = size[0].toIntOrNull() ?: return null
        val height = size[1].toIntOrNull() ?: return null
        return name.substring(0, separator) to CachedDiagram(path, width, height)
    }

    private fun prune() {
        val limit = MermaidSettings.getInstance().diskCacheLimitMb.toLong() * 1024 * 1024
        val files = index.values.map { it.path }.filter { Files.exists(it) }
        var total = files.sumOf { runCatching { Files.size(it) }.getOrDefault(0L) }
        if (total <= limit) return

        val oldestFirst = files.sortedBy { runCatching { it.getLastModifiedTime().toMillis() }.getOrDefault(0L) }
        for (path in oldestFirst) {
            if (total <= limit) break
            total -= runCatching { Files.size(path) }.getOrDefault(0L)
            path.deleteIfExists()
            index.entries.removeIf { it.value.path == path }
        }
    }
}
