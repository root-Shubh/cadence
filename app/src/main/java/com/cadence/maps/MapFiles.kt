package com.cadence.maps

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/** Size of a PMTiles v3 header, the only part read to check a file. */
const val PMTILES_HEADER_BYTES: Int = 127

/** PMTiles tile type for Mapbox Vector Tiles, the only kind the offline style can draw. */
const val PMTILES_TYPE_MVT: Int = 1

/** File name of the map shipped with the app (STEPW-65). */
const val BUNDLED_MAP_FILE: String = "chennai.pmtiles"

/** What the header of a PMTiles v3 file says about the map. Coordinates in degrees. */
data class PmtilesInfo(
    val minZoom: Int,
    val maxZoom: Int,
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double,
    val centerZoom: Int,
    val centerLon: Double,
    val centerLat: Double,
    val tileType: Int,
)

/** Why a picked file cannot be used as a map, or null when it can. */
enum class MapFileProblem(val message: String) {
    NOT_PMTILES("This is not a PMTiles map file (.pmtiles, version 3)."),
    NOT_VECTOR("This map file holds image tiles. Cadence needs a vector map (Protomaps basemap)."),
}

/**
 * Reads a PMTiles v3 header from the first [PMTILES_HEADER_BYTES] bytes of a file. Returns null
 * when the bytes are not a PMTiles v3 header. Pure Kotlin.
 */
fun parsePmtilesHeader(bytes: ByteArray): PmtilesInfo? {
    if (bytes.size < PMTILES_HEADER_BYTES) return null
    if (String(bytes, 0, 7, Charsets.US_ASCII) != "PMTiles" || bytes[7].toInt() != 3) return null
    val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    fun e7(pos: Int): Double = b.getInt(pos) / 1e7
    fun u8(pos: Int): Int = bytes[pos].toInt() and 0xFF
    return PmtilesInfo(
        minZoom = u8(100),
        maxZoom = u8(101),
        minLon = e7(102),
        minLat = e7(106),
        maxLon = e7(110),
        maxLat = e7(114),
        centerZoom = u8(118),
        centerLon = e7(119),
        centerLat = e7(123),
        tileType = u8(99),
    )
}

/** Checks a header read from a picked file. */
fun checkMapHeader(bytes: ByteArray): MapFileProblem? {
    val info = parsePmtilesHeader(bytes) ?: return MapFileProblem.NOT_PMTILES
    return if (info.tileType != PMTILES_TYPE_MVT) MapFileProblem.NOT_VECTOR else null
}

/**
 * A safe file name for an imported map, from the name the user's file had: lower case letters,
 * digits, dashes and underscores, ending in .pmtiles. Never the bundled map's name, so an import
 * cannot replace it.
 */
fun safeMapFileName(displayName: String?): String {
    val leaf = (displayName ?: "map").substringAfterLast('/').substringAfterLast('\\')
    val base = leaf.substringBeforeLast('.').lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9_-]+"), "-").trim('-').take(40).ifEmpty { "map" }
    val name = "$base.pmtiles"
    return if (name == BUNDLED_MAP_FILE) "$base-imported.pmtiles" else name
}

/** "Chennai" from "chennai.pmtiles", for lists. */
fun mapLabel(fileName: String): String =
    fileName.removeSuffix(".pmtiles").replace('-', ' ').replace('_', ' ')
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
