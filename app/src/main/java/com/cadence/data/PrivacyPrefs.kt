package com.cadence.data

import android.content.Context
import android.content.SharedPreferences

/** Pure default: not acknowledged unless a stored value says otherwise. */
fun acknowledged(stored: Boolean?): Boolean = stored ?: false

/** True when the first-run privacy card should be shown on the Today screen. */
fun shouldShowPrivacyNotice(prefs: PrivacyPrefs): Boolean = !prefs.isAcknowledged()

/** Remembers whether the user dismissed the privacy notice with "Got it". */
class PrivacyPrefs(context: Context) {

    private val prefs: SharedPreferences? = try {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (e: RuntimeException) {
        null
    }

    fun isAcknowledged(): Boolean {
        return try {
            val stored = if (prefs?.contains(KEY_ACK) == true) prefs.getBoolean(KEY_ACK, false) else null
            acknowledged(stored)
        } catch (e: RuntimeException) {
            false
        }
    }

    fun acknowledge() {
        try {
            prefs?.edit()?.putBoolean(KEY_ACK, true)?.apply()
        } catch (e: RuntimeException) {
            // Not persisted; the card may reappear next launch.
        }
    }

    private companion object {
        const val PREFS_NAME = "cadence_privacy"
        const val KEY_ACK = "notice_acknowledged"
    }
}
