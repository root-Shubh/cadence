package com.cadence.patterns

import java.io.File

/**
 * Saves walk drawings, one text file per profile and day under [filesDir]/tracks, one point per
 * line (see [TrackCodec]). Appends only, so a save never rewrites earlier steps. Pure JVM.
 */
class TrackStore(filesDir: File) {
    private val root = File(filesDir, "tracks")

    fun fileFor(profileId: String, date: String): File = File(File(root, safeName(profileId)), "$date.track")

    /** The saved points for [date], oldest first. Empty when nothing is saved or the file cannot be read. */
    fun read(profileId: String, date: String): List<TrackPoint> = synchronized(LOCK) {
        val file = fileFor(profileId, date)
        if (!file.exists()) return emptyList()
        runCatching { TrackCodec.decodeAll(file.readText()) }.getOrDefault(emptyList())
    }

    /** Days with a saved drawing, as yyyy-MM-dd. */
    fun listDates(profileId: String): List<String> {
        val dir = File(root, safeName(profileId))
        return dir.listFiles()?.map { it.name }?.filter { it.endsWith(".track") }?.map { it.removeSuffix(".track") }.orEmpty()
    }

    /** Appends [points] to the file for [date]. Throws on a write error, so the caller can retry. */
    fun append(profileId: String, date: String, points: List<TrackPoint>) = synchronized(LOCK) {
        if (points.isEmpty()) return@synchronized
        val file = fileFor(profileId, date)
        file.parentFile?.mkdirs()
        file.appendText(points.joinToString(separator = "\n", postfix = "\n") { TrackCodec.encode(it) })
    }

    companion object {
        private val LOCK = Any()

        /** A file-safe form of a profile id. */
        fun safeName(profileId: String): String = profileId.replace(Regex("[^A-Za-z0-9_-]"), "_")
    }
}
