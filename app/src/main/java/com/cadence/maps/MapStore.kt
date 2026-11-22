package com.cadence.maps

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** One map file on the device. */
data class MapFile(val fileName: String, val file: File, val info: PmtilesInfo, val bundled: Boolean) {
    val label: String get() = if (bundled) "${mapLabel(fileName)} (built in)" else mapLabel(fileName)
}

/** Result of importing a map file the user picked. */
sealed interface MapImportResult {
    data class Imported(val map: MapFile) : MapImportResult
    data class Rejected(val reason: String) : MapImportResult
}

/**
 * Offline map files (STEPW-65), kept in filesDir/maps. The Chennai map ships in the app's assets
 * and is copied here on first use, because MapLibre reads PMTiles only from a real file. Users can
 * import their own .pmtiles file with the system file picker; nothing is downloaded by the app.
 */
class MapStore(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "maps")
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Copies the bundled map into place when it is missing or incomplete. Call off the main thread. */
    fun ensureBundled() {
        dir.mkdirs()
        val target = File(dir, BUNDLED_MAP_FILE)
        val expected = app.assets.openFd("maps/$BUNDLED_MAP_FILE").use { it.length }
        if (target.exists() && target.length() == expected) return
        val tmp = File(dir, "$BUNDLED_MAP_FILE.part")
        app.assets.open("maps/$BUNDLED_MAP_FILE").use { input -> tmp.outputStream().use { input.copyTo(it) } }
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    /** Every usable map on the device, built-in first. Call off the main thread. */
    fun list(): List<MapFile> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".pmtiles") }.orEmpty()
            .mapNotNull { f -> readInfo(f)?.let { MapFile(f.name, f, it, f.name == BUNDLED_MAP_FILE) } }
            .sortedWith(compareByDescending<MapFile> { it.bundled }.thenBy { it.fileName })

    /** The map the user chose, or the built-in one. */
    fun active(): MapFile? {
        val maps = list()
        val chosen = prefs.getString(KEY_ACTIVE, BUNDLED_MAP_FILE)
        return maps.firstOrNull { it.fileName == chosen } ?: maps.firstOrNull { it.bundled } ?: maps.firstOrNull()
    }

    fun setActive(fileName: String) {
        prefs.edit().putString(KEY_ACTIVE, fileName).apply()
    }

    /** Copies the picked file into the app after checking it is a vector PMTiles map. Off the main thread. */
    fun import(uri: Uri): MapImportResult {
        val resolver = app.contentResolver
        val header = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val buf = ByteArray(PMTILES_HEADER_BYTES)
                var read = 0
                while (read < buf.size) {
                    val n = input.read(buf, read, buf.size - read)
                    if (n < 0) break
                    read += n
                }
                buf
            }
        }.getOrNull() ?: return MapImportResult.Rejected("The file could not be read.")
        checkMapHeader(header)?.let { return MapImportResult.Rejected(it.message) }
        val name = safeMapFileName(displayName(uri))
        dir.mkdirs()
        val tmp = File(dir, "$name.part")
        val copied = runCatching {
            resolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } } != null
        }.getOrDefault(false)
        if (!copied) {
            tmp.delete()
            return MapImportResult.Rejected("The file could not be copied. Is there enough space?")
        }
        val target = File(dir, name)
        target.delete()
        tmp.renameTo(target)
        val info = readInfo(target) ?: return MapImportResult.Rejected(MapFileProblem.NOT_PMTILES.message)
        setActive(name)
        return MapImportResult.Imported(MapFile(name, target, info, bundled = false))
    }

    /** Removes an imported map. The built-in map cannot be removed. */
    fun delete(map: MapFile) {
        if (map.bundled) return
        map.file.delete()
        if (prefs.getString(KEY_ACTIVE, null) == map.fileName) setActive(BUNDLED_MAP_FILE)
    }

    private fun readInfo(file: File): PmtilesInfo? = runCatching {
        file.inputStream().use { input ->
            val buf = ByteArray(PMTILES_HEADER_BYTES)
            if (input.read(buf) < PMTILES_HEADER_BYTES) null else parsePmtilesHeader(buf)
        }
    }.getOrNull()?.takeIf { it.tileType == PMTILES_TYPE_MVT }

    private fun displayName(uri: Uri): String? = runCatching {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private companion object {
        const val PREFS = "cadence_maps"
        const val KEY_ACTIVE = "active_map"
    }
}
