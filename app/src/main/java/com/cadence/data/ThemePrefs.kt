package com.cadence.data

import android.content.Context
import android.content.SharedPreferences

/** User's theme choice. SYSTEM follows the device setting. */
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** Pure parser: null or unknown names fall back to [ThemeChoice.SYSTEM]. */
fun parseThemeChoice(name: String?): ThemeChoice {
    if (name == null) return ThemeChoice.SYSTEM
    return ThemeChoice.values().firstOrNull { it.name == name } ?: ThemeChoice.SYSTEM
}

/** Persists the theme choice in SharedPreferences. Failures fall back to safe defaults. */
class ThemePrefs(context: Context) {

    private val prefs: SharedPreferences? = try {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (e: RuntimeException) {
        null
    }

    fun load(): ThemeChoice {
        return try {
            parseThemeChoice(prefs?.getString(KEY_THEME, null))
        } catch (e: RuntimeException) {
            ThemeChoice.SYSTEM
        }
    }

    fun save(choice: ThemeChoice) {
        try {
            prefs?.edit()?.putString(KEY_THEME, choice.name)?.apply()
        } catch (e: RuntimeException) {
            // Preference could not be written; the in-memory choice still applies this session.
        }
    }

    private companion object {
        const val PREFS_NAME = "cadence_theme"
        const val KEY_THEME = "theme_choice"
    }
}
