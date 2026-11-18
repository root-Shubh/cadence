package com.cadence.data

/** Id of the first profile. Pre-existing step history is stored under this id. */
const val DEFAULT_PROFILE_ID = "default"

/** Longest allowed profile name, in characters after trimming. */
const val MAX_PROFILE_NAME_LENGTH = 30

private const val HEADER = "stepwise-profiles-v1"
private const val FIELD_COUNT = 7
private const val SEP = '|'
private const val RECORD_SEP = '\n'
private const val ESC = '\\'

/**
 * Pure serialiser for the profile list. One header line, then one line per profile with
 * seven fields separated by '|': id, name, height, weight, age, sex, stride override
 * (empty when absent). Field text escapes backslash, '|', newline and carriage return,
 * so any name round-trips. Pure Kotlin: no Android or JSON dependency.
 */
object ProfileCodec {

    fun encode(profiles: List<ProfileState>): String {
        val sb = StringBuilder(HEADER)
        for (p in profiles) {
            sb.append(RECORD_SEP)
            val fields = listOf(
                p.id,
                p.name,
                p.heightCm.toString(),
                p.weightKg.toString(),
                p.age.toString(),
                p.sex,
                p.strideOverrideCm?.toString() ?: "",
            )
            sb.append(fields.joinToString(SEP.toString()) { escape(it) })
        }
        return sb.toString()
    }

    /** Returns the decoded list, or an empty list for null, empty or corrupt input. Never throws. */
    fun decode(raw: String?): List<ProfileState> {
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            parse(raw)
        } catch (e: RuntimeException) {
            emptyList()
        }
    }

    private fun parse(raw: String): List<ProfileState> {
        val lines = splitUnescaped(raw, RECORD_SEP)
        if (lines.first() != HEADER) return emptyList()
        val profiles = lines.drop(1).filter { it.isNotEmpty() }.map { line ->
            val fields = splitUnescaped(line, SEP).map { unescape(it) }
            check(fields.size == FIELD_COUNT) { "bad field count" }
            check(fields[0].isNotBlank()) { "blank id" }
            ProfileState(
                id = fields[0],
                name = fields[1],
                heightCm = fields[2].toDouble(),
                weightKg = fields[3].toDouble(),
                age = fields[4].toInt(),
                sex = fields[5],
                strideOverrideCm = fields[6].takeIf { it.isNotEmpty() }?.toDouble(),
            )
        }
        check(profiles.map { it.id }.toSet().size == profiles.size) { "duplicate id" }
        return profiles
    }

    private fun escape(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                ESC -> sb.append(ESC).append(ESC)
                SEP -> sb.append(ESC).append('p')
                RECORD_SEP -> sb.append(ESC).append('n')
                '\r' -> sb.append(ESC).append('r')
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != ESC) {
                sb.append(c)
                i++
                continue
            }
            require(i + 1 < s.length) { "dangling escape" }
            when (s[i + 1]) {
                ESC -> sb.append(ESC)
                'p' -> sb.append(SEP)
                'n' -> sb.append(RECORD_SEP)
                'r' -> sb.append('\r')
                else -> throw IllegalArgumentException("bad escape")
            }
            i += 2
        }
        return sb.toString()
    }

    /** Splits on [sep] characters that are not escaped. The pieces keep their escapes. */
    private fun splitUnescaped(s: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == ESC && i + 1 < s.length) {
                cur.append(c).append(s[i + 1])
                i += 2
                continue
            }
            if (c == sep) {
                out += cur.toString()
                cur.clear()
            } else {
                cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }
}

/**
 * Which profile should be active after [removedId] is deleted from [list] (the list before
 * deletion). Keeps [currentActive] when it still exists; otherwise the first remaining
 * profile; null when none remain.
 */
fun nextActiveId(list: List<ProfileState>, removedId: String, currentActive: String?): String? {
    val remaining = list.filter { it.id != removedId }
    if (currentActive != null && currentActive != removedId && remaining.any { it.id == currentActive }) {
        return currentActive
    }
    return remaining.firstOrNull()?.id
}

/** Error message for a profile name, or null when the trimmed name is 1 to 30 characters. */
fun profileNameError(raw: String): String? {
    val name = raw.trim()
    return when {
        name.isEmpty() -> "Name is required."
        name.length > MAX_PROFILE_NAME_LENGTH -> "Name must be $MAX_PROFILE_NAME_LENGTH characters or fewer."
        else -> null
    }
}

/** "Me" for the first profile, then the lowest "Profile N" (N >= 2) not already used. */
fun defaultProfileName(existing: List<ProfileState>): String {
    if (existing.isEmpty()) return "Me"
    val names = existing.map { it.name }.toSet()
    var n = existing.size + 1
    while ("Profile $n" in names) n++
    return "Profile $n"
}
