package com.cadence.data

import android.content.Context
import android.content.SharedPreferences
import com.cadence.goals.Goals

/** Result of parsing one goal text field. [Empty] means no goal is set for that period. */
sealed class GoalInput {
    object Empty : GoalInput()
    data class Valid(val value: Int) : GoalInput()
    object Invalid : GoalInput()
}

private const val MAX_GOAL = 1_000_000

private val PLAIN_DIGITS = Regex("""\d+""")
private val GROUPED_DIGITS = Regex("""\d{1,3}([, ]\d{3})+""")

/**
 * Parses a goal entered by the user. Blank text means no goal. Digits may contain thousands
 * separators (commas or spaces) between groups of three. Zero, negatives, decimals, non-numeric
 * text and values above 1,000,000 are [GoalInput.Invalid].
 */
fun parseGoalInput(text: String): GoalInput {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return GoalInput.Empty
    if (!PLAIN_DIGITS.matches(trimmed) && !GROUPED_DIGITS.matches(trimmed)) return GoalInput.Invalid
    val value = trimmed.replace(",", "").replace(" ", "").toLongOrNull() ?: return GoalInput.Invalid
    if (value <= 0L || value > MAX_GOAL) return GoalInput.Invalid
    return GoalInput.Valid(value.toInt())
}

/** Persists the user's step goals in SharedPreferences. Absent keys mean no goal. */
class GoalsStore(context: Context) {

    private val prefs: SharedPreferences? = try {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (e: RuntimeException) {
        null
    }

    /** Returns the saved goals. Never throws; unreadable or invalid values load as unset. */
    fun load(): Goals {
        val p = prefs ?: return Goals()
        return try {
            Goals(
                dailySteps = readPositive(p, KEY_DAILY),
                weeklySteps = readPositive(p, KEY_WEEKLY),
                monthlySteps = readPositive(p, KEY_MONTHLY),
            )
        } catch (e: RuntimeException) {
            Goals()
        }
    }

    /** Saves [goals]. Unset periods are removed. Storage failures are ignored. */
    fun save(goals: Goals) {
        val p = prefs ?: return
        try {
            p.edit().apply {
                writeOrRemove(this, KEY_DAILY, goals.dailySteps)
                writeOrRemove(this, KEY_WEEKLY, goals.weeklySteps)
                writeOrRemove(this, KEY_MONTHLY, goals.monthlySteps)
            }.apply()
        } catch (e: RuntimeException) {
            // Storage is unavailable; the goals simply are not persisted.
        }
    }

    private fun readPositive(p: SharedPreferences, key: String): Int? {
        if (!p.contains(key)) return null
        val value = p.getInt(key, 0)
        return if (value > 0) value else null
    }

    private fun writeOrRemove(
        editor: SharedPreferences.Editor,
        key: String,
        value: Int?,
    ) {
        if (value != null) editor.putInt(key, value) else editor.remove(key)
    }

    private companion object {
        const val PREFS_NAME = "stepwise_goals"
        const val KEY_DAILY = "daily"
        const val KEY_WEEKLY = "weekly"
        const val KEY_MONTHLY = "monthly"
    }
}
