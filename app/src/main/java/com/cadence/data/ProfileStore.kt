package com.cadence.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * One saved profile. [id] is a stable UUID string (the first profile uses [DEFAULT_PROFILE_ID]).
 * The trailing defaults keep older positional constructor calls compiling.
 */
data class ProfileState(
    val heightCm: Double,
    val weightKg: Double,
    val age: Int,
    val sex: String,
    val strideOverrideCm: Double?,
    val id: String = DEFAULT_PROFILE_ID,
    val name: String = "Me",
)

/**
 * Persists a list of profiles plus the active profile id in SharedPreferences.
 * The single-profile methods (load, save, clear) act on the active profile, so callers that
 * know nothing about profiles keep working.
 */
class ProfileStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun listProfiles(): List<ProfileState> = readAll()

    /** The active profile id, or null when there are no profiles. */
    fun activeProfileId(): String? = activeIn(readAll())?.id

    /** Returns the active profile, or null if no profile exists. */
    fun load(): ProfileState? = activeIn(readAll())

    /**
     * Validates and saves [state] over the active profile, keeping that profile's id and name.
     * When no profile exists yet, creates the first one (id [DEFAULT_PROFILE_ID]).
     * Throws IllegalArgumentException with all messages if invalid.
     */
    fun save(state: ProfileState) {
        val errors = validateProfile(
            heightCm = state.heightCm,
            weightKg = state.weightKg,
            age = state.age,
            sex = state.sex,
            strideOverrideCm = state.strideOverrideCm,
        )
        require(errors.isEmpty()) { errors.joinToString(" ") }

        val all = readAll()
        val active = activeIn(all)
        if (active == null) {
            val name = state.name.trim().takeIf { profileNameError(it) == null } ?: "Me"
            val created = state.copy(id = DEFAULT_PROFILE_ID, name = name)
            write(listOf(created), created.id)
        } else {
            val updated = all.map {
                if (it.id == active.id) state.copy(id = active.id, name = active.name) else it
            }
            write(updated, active.id)
        }
    }

    /**
     * Adds a profile with no measurements yet and selects it. The first profile gets
     * [DEFAULT_PROFILE_ID]; later ones get a UUID.
     */
    fun addProfile(name: String): ProfileState {
        val error = profileNameError(name)
        require(error == null) { error.orEmpty() }
        val all = readAll()
        val profile = ProfileState(
            heightCm = 0.0,
            weightKg = 0.0,
            age = 0,
            sex = "",
            strideOverrideCm = null,
            id = if (all.isEmpty()) DEFAULT_PROFILE_ID else UUID.randomUUID().toString(),
            name = name.trim(),
        )
        write(all + profile, profile.id)
        return profile
    }

    fun rename(id: String, name: String) {
        val error = profileNameError(name)
        require(error == null) { error.orEmpty() }
        val all = readAll()
        require(all.any { it.id == id }) { "Unknown profile" }
        write(all.map { if (it.id == id) it.copy(name = name.trim()) else it }, activeProfileId())
    }

    /** Makes [id] the active profile. Unknown ids are rejected. */
    fun select(id: String) {
        val all = readAll()
        require(all.any { it.id == id }) { "Unknown profile" }
        write(all, id)
    }

    /**
     * Deletes one profile. Step history is not touched. If the deleted profile was active,
     * the first remaining profile becomes active (none if no profile is left).
     */
    fun deleteProfile(id: String) {
        val all = readAll()
        val next = nextActiveId(all, id, activeProfileId())
        write(all.filter { it.id != id }, next)
    }

    /** Deletes all profile data. Step history is not touched. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun activeIn(all: List<ProfileState>): ProfileState? {
        val stored = prefs.getString(KEY_ACTIVE, null)
        return all.firstOrNull { it.id == stored } ?: all.firstOrNull()
    }

    private fun readAll(): List<ProfileState> {
        val raw = prefs.getString(KEY_PROFILES, null)
        if (raw != null) return ProfileCodec.decode(raw)
        if (!prefs.contains(LEGACY_HEIGHT)) return emptyList()
        // Migrates the single profile saved by older versions into the list format.
        val legacy = ProfileState(
            heightCm = Double.fromBits(prefs.getLong(LEGACY_HEIGHT, 0L)),
            weightKg = Double.fromBits(prefs.getLong(LEGACY_WEIGHT, 0L)),
            age = prefs.getInt(LEGACY_AGE, 0),
            sex = prefs.getString(LEGACY_SEX, "") ?: "",
            strideOverrideCm = if (prefs.contains(LEGACY_STRIDE)) {
                Double.fromBits(prefs.getLong(LEGACY_STRIDE, 0L))
            } else {
                null
            },
        )
        write(listOf(legacy), legacy.id)
        return listOf(legacy)
    }

    /** Writes the whole list and the active id, and removes the legacy single-profile keys. */
    private fun write(profiles: List<ProfileState>, activeId: String?) {
        val editor = prefs.edit()
            .putString(KEY_PROFILES, ProfileCodec.encode(profiles))
            .remove(LEGACY_HEIGHT)
            .remove(LEGACY_WEIGHT)
            .remove(LEGACY_AGE)
            .remove(LEGACY_SEX)
            .remove(LEGACY_STRIDE)
        if (activeId == null) editor.remove(KEY_ACTIVE) else editor.putString(KEY_ACTIVE, activeId)
        editor.apply()
    }

    private companion object {
        const val PREFS_NAME = "stepwise_profile"
        const val KEY_PROFILES = "profiles_v2"
        const val KEY_ACTIVE = "active_profile_id"
        const val LEGACY_HEIGHT = "height_cm"
        const val LEGACY_WEIGHT = "weight_kg"
        const val LEGACY_AGE = "age"
        const val LEGACY_SEX = "sex"
        const val LEGACY_STRIDE = "stride_override_cm"
    }
}
