package com.cadence.debug

import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Ring buffer of step-counting events, read by the Debug panel and copied out as text.
 *
 * Once [attach] is called the buffer is also kept in a file, so the log survives the process
 * being killed (the case where counting stops on its own) and shows on the next launch. Each
 * line is "MM-dd HH:mm:ss.SSS [tag] message". The newest [capacity] lines are kept in memory and
 * in the file, so memory and disk use stay flat however long counting runs.
 */
object DebugLog {
    /**
     * Most lines kept. The oldest line is dropped first. A day of one-a-minute heartbeats is 1,440
     * lines, so this keeps at least a full day with room for service and vehicle events (STEPW-61).
     */
    const val DEFAULT_CAPACITY = 3000

    /** File name inside the directory passed to [attach]. */
    const val FILE_NAME = "debug-log.txt"

    private val timeFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    private val lock = Any()
    private var capacity = DEFAULT_CAPACITY
    private val lines = ArrayDeque<String>()
    private var file: File? = null
    private var appendsSinceRewrite = 0

    /**
     * Keeps the log in [dir]. Lines from earlier sessions are loaded first, followed by anything
     * logged in this process before the call. Later calls do nothing. File errors are ignored:
     * the in-memory log keeps working.
     */
    fun attach(dir: File) = synchronized(lock) {
        if (file != null) return@synchronized
        val target = File(dir, FILE_NAME)
        file = target
        val previous = runCatching { if (target.exists()) target.readLines() else emptyList() }
            .getOrDefault(emptyList())
            .filter { it.isNotBlank() }
        val merged = (previous + lines).takeLast(capacity)
        lines.clear()
        lines.addAll(merged)
        rewriteLocked()
    }

    /** Appends one event. [tag] names the source (sensor, service, storage, vehicle). */
    fun add(tag: String, message: String, nowMillis: Long = System.currentTimeMillis()) {
        val line = formatLine(nowMillis, tag, message)
        synchronized(lock) {
            lines.addLast(line)
            while (lines.size > capacity) lines.removeFirst()
            val target = file ?: return@synchronized
            runCatching { target.appendText(line + "\n") }
            appendsSinceRewrite++
            // The file grows by appends; trim it back to the buffer once it holds twice the limit.
            if (appendsSinceRewrite >= capacity) rewriteLocked()
        }
    }

    /** Snapshot of the lines, oldest first. */
    fun lines(): List<String> = synchronized(lock) { lines.toList() }

    /** The whole buffer as one string, one event per line, for copying out. */
    fun text(): String = lines().joinToString("\n")

    /** Empties the buffer and the file. */
    fun clear() = synchronized(lock) {
        lines.clear()
        rewriteLocked()
    }

    /** Changes the limit for tests. Lines beyond the new limit are dropped, oldest first. */
    internal fun setCapacityForTest(newCapacity: Int) = synchronized(lock) {
        capacity = newCapacity
        while (lines.size > capacity) lines.removeFirst()
    }

    /** Stops writing to the file, for tests. */
    internal fun detachForTest() = synchronized(lock) {
        file = null
        appendsSinceRewrite = 0
    }

    internal fun formatLine(nowMillis: Long, tag: String, message: String): String =
        "${timeFormat.format(Instant.ofEpochMilli(nowMillis))} [$tag] $message"

    private fun rewriteLocked() {
        appendsSinceRewrite = 0
        val target = file ?: return
        val body = if (lines.isEmpty()) "" else lines.joinToString("\n", postfix = "\n")
        runCatching { target.writeText(body) }
    }
}
