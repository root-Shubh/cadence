package com.cadence.patterns

import java.io.File

/**
 * File store for daily patterns: one JSON Lines file per local day at
 * filesDir/patterns/<profileId>/<yyyy-MM-dd>.json. Every write goes to a .tmp file which is then
 * renamed over the target, so a reader never sees a half-written day. No database is used.
 */
class PatternStore(filesDir: File) {

    private val root = File(filesDir, "patterns")

    fun dirFor(profileId: String): File = File(root, safeName(profileId))

    fun fileFor(profileId: String, date: String): File = File(dirFor(profileId), "$date.json")

    /** Day strings that have a file, newest first. */
    fun listDates(profileId: String): List<String> {
        val files = dirFor(profileId).listFiles() ?: return emptyList()
        return files.map { it.name }
            .filter { DATE_FILE.matches(it) }
            .map { it.removeSuffix(".json") }
            .sortedDescending()
    }

    fun read(profileId: String, date: String): DayPatterns? = synchronized(LOCK) { readLocked(profileId, date) }

    /** Appends step times (epoch millis) to [date]. Times are kept in the order given. */
    fun appendSteps(profileId: String, date: String, stepMillis: List<Long>) {
        if (stepMillis.isEmpty()) return
        synchronized(LOCK) {
            val current = readLocked(profileId, date) ?: DayPatterns(date)
            writeLocked(profileId, current.withSteps(stepMillis))
        }
    }

    fun appendPoint(profileId: String, date: String, point: RoutePointRecord) {
        synchronized(LOCK) {
            val current = readLocked(profileId, date) ?: DayPatterns(date)
            writeLocked(profileId, current.withPoint(point))
        }
    }

    private fun readLocked(profileId: String, date: String): DayPatterns? {
        val file = fileFor(profileId, date)
        if (!file.isFile) return null
        return PatternCodec.decode(file.readText())
    }

    private fun writeLocked(profileId: String, day: DayPatterns) {
        val target = fileFor(profileId, day.date)
        val dir = target.parentFile ?: return
        if (!dir.isDirectory) dir.mkdirs()
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(PatternCodec.encode(day))
        if (!tmp.renameTo(target)) {
            // Some file systems refuse to rename over an existing file.
            target.delete()
            tmp.renameTo(target)
        }
    }

    companion object {
        private val LOCK = Any()
        private val DATE_FILE = Regex("\\d{4}-\\d{2}-\\d{2}\\.json")

        /** Profile ids are used as folder names, so anything outside a safe set becomes '_'. */
        fun safeName(profileId: String): String =
            profileId.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")
    }
}
