package com.cadence.ui

import com.cadence.domain.DailyRecord
import com.cadence.goals.Goals
import com.cadence.goals.StepPoint
import com.cadence.goals.dailyProgress
import com.cadence.goals.dailySuggestion
import com.cadence.goals.weeklyProgress
import com.cadence.goals.weeklySuggestion
import java.time.DateTimeException
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/** Kinds of insight card shown on the Trend screen. */
enum class InsightKind { WEEKDAY_PATTERN, BEST_DAY, STREAK, TREND, GOAL_PACE, CONSISTENCY, CALORIES, ADVICE }

/** Colour family of an insight card: POSITIVE = primary container, NUDGE = tertiary, NEUTRAL = surface variant. */
enum class InsightTone { POSITIVE, NEUTRAL, NUDGE }

data class Insight(
    val kind: InsightKind,
    val title: String,
    val body: String,
    val tone: InsightTone,
)

/**
 * Energy per step used for the calorie hint. 0.1 kcal per step is the approximation
 * "100 kcal is about 1,000 steps at an average stride". It ignores body weight on purpose,
 * because the insight has no weight input.
 */
const val KCAL_PER_STEP = 0.1

private const val WEEKDAY_WINDOW_DAYS = 56
private const val MIN_WEEKDAY_DAYS = 14
private const val BEST_WINDOW_DAYS = 30
private const val TREND_NEUTRAL_BAND_PERCENT = 3.0
private const val WEEKEND_LOW_PERCENT = -20.0
private const val CONSISTENT_DAYS = 20
private const val CALORIE_HINT_KCAL = 100.0
private const val MIN_AIM_STEPS = 1000L
private const val ADVICE_LIMIT = 2

private val WEEKDAY_NAMES = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
)

/** One day with a stored record. */
internal data class Day(val date: LocalDate, val steps: Int, val kcal: Double)

/** Goal streaks: [current] ends today or yesterday, [longest] is the best run in the last 30 days. */
data class StreakStats(val current: Int, val longest: Int)

/**
 * Builds the insight cards for the Trend screen, ending on [endDateIso].
 * Advice comes first so it is always visible in the capped list. Empty history and missing
 * (or non-positive) goals never throw; the rule for a missing goal simply yields no card.
 */
fun buildInsights(
    history: List<DailyRecord>,
    endDateIso: String,
    dailyGoal: Int?,
    weeklyGoal: Int?,
): List<Insight> {
    val end = parseIsoOrNull(endDateIso) ?: return emptyList()
    val byDate = indexByDate(history)
    val daily = dailyGoal?.takeIf { it > 0 }
    val weekly = weeklyGoal?.takeIf { it > 0 }
    val streak = daily?.let { goalStreaks(byDate, end, it) }

    val result = ArrayList<Insight>()
    result += adviceInsights(byDate, end, streak?.current)
    goalPaceInsight(byDate, end, daily, weekly)?.let { result += it }
    if (daily != null && streak != null) streakInsight(streak, daily)?.let { result += it }
    trendInsight(byDate, end)?.let { result += it }
    weekdayPatternInsight(byDate, end)?.let { result += it }
    bestDayInsight(byDate, end)?.let { result += it }
    consistencyInsight(byDate, end)?.let { result += it }
    caloriesInsight(byDate, end)?.let { result += it }
    return result
}

/** Extra steps per day that burn [extraKcal], using [kcalPerStep]. 100 kcal gives 1,000 steps. */
fun stepsForExtraKcal(extraKcal: Double, kcalPerStep: Double = KCAL_PER_STEP): Long {
    require(kcalPerStep > 0.0) { "kcalPerStep must be > 0, was $kcalPerStep" }
    require(extraKcal >= 0.0) { "extraKcal must not be negative, was $extraKcal" }
    return (extraKcal / kcalPerStep).roundToLong()
}

/** Current and longest goal streak. Pure; [goal] must be positive. */
internal fun goalStreaks(byDate: Map<LocalDate, Day>, end: LocalDate, goal: Int): StreakStats {
    fun meets(d: LocalDate) = stepsOn(byDate, d) >= goal

    var current = 0
    var d = if (meets(end)) end else end.minusDays(1)
    while (meets(d)) {
        current++
        d = d.minusDays(1)
    }

    var longest = 0
    var run = 0
    var day = end.minusDays(BEST_WINDOW_DAYS - 1L)
    while (!day.isAfter(end)) {
        if (meets(day)) {
            run++
            longest = max(longest, run)
        } else {
            run = 0
        }
        day = day.plusDays(1)
    }
    return StreakStats(current, longest)
}

internal fun streakInsight(streak: StreakStats, goal: Int): Insight? {
    if (streak.current == 0 && streak.longest == 0) return null
    val goalText = formatCount(goal.toLong())
    return if (streak.current > 0) {
        Insight(
            kind = InsightKind.STREAK,
            title = "Goal streak",
            body = "You are on a ${streak.current}-day streak at $goalText+ steps. " +
                "Longest in the last 30 days: ${streak.longest} ${dayWord(streak.longest)}.",
            tone = InsightTone.POSITIVE,
        )
    } else {
        Insight(
            kind = InsightKind.STREAK,
            title = "Goal streak",
            body = "No streak right now. Your best run in the last 30 days was " +
                "${streak.longest} ${dayWord(streak.longest)} at $goalText+ steps.",
            tone = InsightTone.NEUTRAL,
        )
    }
}

/** Last 7 days against the 7 days before. Under 3 percent counts as steady. */
internal fun trendInsight(byDate: Map<LocalDate, Day>, end: LocalDate): Insight? {
    val last = windowDays(byDate, end.minusDays(6), end)
    val previous = windowDays(byDate, end.minusDays(13), end.minusDays(7))
    if (last.isEmpty() || previous.isEmpty()) return null
    val prevAvg = averageSteps(previous)
    if (prevAvg == 0.0) return null
    val lastAvg = averageSteps(last)
    val pct = (lastAvg - prevAvg) / prevAvg * 100.0
    val avgText = formatCount(lastAvg.roundToLong())
    return when {
        abs(pct) < TREND_NEUTRAL_BAND_PERCENT -> Insight(
            InsightKind.TREND, "Steady week",
            "Your last 7 days average $avgText steps a day, about the same as the 7 days before.",
            InsightTone.NEUTRAL,
        )
        pct > 0 -> Insight(
            InsightKind.TREND, "Trending up",
            "Your last 7 days average $avgText steps a day, ${formatPercent(pct)} against the 7 days before.",
            InsightTone.POSITIVE,
        )
        else -> Insight(
            InsightKind.TREND, "Trending down",
            "Your last 7 days average $avgText steps a day, ${formatPercent(pct)} against the 7 days before.",
            InsightTone.NUDGE,
        )
    }
}

/** Average steps per weekday over 8 weeks. Needs at least 14 days of data. */
internal fun weekdayPatternInsight(byDate: Map<LocalDate, Day>, end: LocalDate): Insight? {
    val days = windowDays(byDate, end.minusDays(WEEKDAY_WINDOW_DAYS - 1L), end)
    if (days.size < MIN_WEEKDAY_DAYS) return null
    val averages = days.groupBy { it.date.dayOfWeek.value }
        .mapValues { (_, list) -> averageSteps(list) }
        .entries
        .sortedWith(compareByDescending<Map.Entry<Int, Double>> { it.value }.thenBy { it.key })
    val most = averages.first()
    val least = averages.last()
    val body = StringBuilder()
    body.append("Most active on ${WEEKDAY_NAMES[most.key - 1]} ")
        .append("(avg ${formatCount(most.value.roundToLong())} steps), least active on ")
        .append("${WEEKDAY_NAMES[least.key - 1]} (avg ${formatCount(least.value.roundToLong())} steps).")
    weekendChangePercent(byDate, end)?.let {
        body.append(" Weekends are ${formatPercent(it)} compared with weekdays.")
    }
    return Insight(InsightKind.WEEKDAY_PATTERN, "Weekday pattern", body.toString(), InsightTone.NEUTRAL)
}

/** Weekend average against weekday average, in percent, over the 8-week weekday window. */
internal fun weekendChangePercent(byDate: Map<LocalDate, Day>, end: LocalDate): Double? {
    val days = windowDays(byDate, end.minusDays(WEEKDAY_WINDOW_DAYS - 1L), end)
    if (days.size < MIN_WEEKDAY_DAYS) return null
    val weekend = days.filter { it.date.dayOfWeek.value >= 6 }
    val weekdays = days.filter { it.date.dayOfWeek.value <= 5 }
    if (weekend.isEmpty() || weekdays.isEmpty()) return null
    val weekdayAvg = averageSteps(weekdays)
    if (weekdayAvg == 0.0) return null
    return (averageSteps(weekend) - weekdayAvg) / weekdayAvg * 100.0
}

/** Highest step day in the last 30 days. Ties go to the most recent date. */
internal fun bestDayInsight(byDate: Map<LocalDate, Day>, end: LocalDate): Insight? {
    val best = windowDays(byDate, end.minusDays(BEST_WINDOW_DAYS - 1L), end)
        .filter { it.steps > 0 }
        .maxWithOrNull(compareBy<Day> { it.steps }.thenBy { it.date })
        ?: return null
    return Insight(
        InsightKind.BEST_DAY,
        "Best day",
        "${dayLabelLong(best.date.toString())}: ${formatCount(best.steps.toLong())} steps, " +
            "your highest in the last 30 days.",
        InsightTone.POSITIVE,
    )
}

/** Days with at least one step in the last 30 days, and the longest run of such days. */
internal fun consistencyInsight(byDate: Map<LocalDate, Day>, end: LocalDate): Insight? {
    val start = end.minusDays(BEST_WINDOW_DAYS - 1L)
    val activeDates = windowDays(byDate, start, end).filter { it.steps >= 1 }.map { it.date }.toSet()
    if (activeDates.isEmpty()) return null

    var run = 0
    var longest = 0
    var d = start
    while (!d.isAfter(end)) {
        if (d in activeDates) {
            run++
            longest = max(longest, run)
        } else {
            run = 0
        }
        d = d.plusDays(1)
    }
    return Insight(
        InsightKind.CONSISTENCY,
        "Consistency",
        "Steps logged on ${activeDates.size} of the last 30 days. " +
            "Longest run of active days: $longest ${dayWord(longest)} in a row.",
        if (activeDates.size >= CONSISTENT_DAYS) InsightTone.POSITIVE else InsightTone.NEUTRAL,
    )
}

/** Average total kcal per day over the last 7 days, with the 100 kcal step hint. */
internal fun caloriesInsight(byDate: Map<LocalDate, Day>, end: LocalDate): Insight? {
    val days = windowDays(byDate, end.minusDays(6), end)
    if (days.isEmpty()) return null
    val avgKcal = days.map { it.kcal }.average()
    val extraSteps = stepsForExtraKcal(CALORIE_HINT_KCAL)
    return Insight(
        InsightKind.CALORIES,
        "Energy",
        "You use about ${formatCount(avgKcal.roundToLong())} kcal a day on average over the last 7 days. " +
            "About ${formatCount(extraSteps)} extra steps a day burns an extra 100 kcal " +
            "(estimate: 100 kcal per 1,000 steps at an average stride).",
        InsightTone.NEUTRAL,
    )
}

/**
 * Weekly and daily goal text, reusing the Goals.kt maths. The weekly text is always shown when
 * a weekly goal is set. The daily text is shown only while today is below the daily goal.
 */
internal fun goalPaceInsight(
    byDate: Map<LocalDate, Day>,
    end: LocalDate,
    dailyGoal: Int?,
    weeklyGoal: Int?,
): Insight? {
    if (dailyGoal == null && weeklyGoal == null) return null
    val points = byDate.values.map { StepPoint(it.date.toString(), it.steps) }
    val goals = Goals(dailySteps = dailyGoal, weeklySteps = weeklyGoal)
    val endIso = end.toString()
    val parts = ArrayList<String>()
    var metAny = false

    weeklyProgress(points, goals, endIso)?.let { wp ->
        parts += weeklySuggestion(wp.goal, wp.steps, wp.daysWithData).text()
        if (wp.met) metAny = true
    }
    dailyProgress(points, goals, endIso)?.let { dp ->
        if (dp.met) {
            parts += "Today's goal is met."
            metAny = true
        } else {
            parts += dailySuggestion(dp.goal, dp.steps).text()
        }
    }
    if (parts.isEmpty()) return null
    return Insight(
        InsightKind.GOAL_PACE,
        "Goal pace",
        parts.joinToString(" "),
        if (metAny) InsightTone.POSITIVE else InsightTone.NEUTRAL,
    )
}

/**
 * Up to two actionable tips, chosen in priority order: start tracking, short walk, weekend walk,
 * streak encouragement. A gentle default is used when none apply, so there is always one tip.
 */
internal fun adviceInsights(
    byDate: Map<LocalDate, Day>,
    end: LocalDate,
    streakCurrent: Int?,
): List<Insight> {
    val last3 = windowDays(byDate, end.minusDays(2), end)
    val week = windowDays(byDate, end.minusDays(6), end)
    val avg7 = if (week.isEmpty()) null else averageSteps(week)
    val candidates = ArrayList<Insight>()

    if (last3.isEmpty()) {
        candidates += Insight(
            InsightKind.ADVICE, "Start tracking",
            "No steps recorded in the last 3 days. Keep your phone with you during the day " +
                "and check back to see your trend.",
            InsightTone.NUDGE,
        )
    } else if (avg7 != null && last3.size == 3 && last3.all { it.steps < avg7 }) {
        val aim = max(MIN_AIM_STEPS, roundToHundred(avg7))
        candidates += Insight(
            InsightKind.ADVICE, "Short walk today",
            "Your last 3 days ran below your 7-day average of ${formatCount(avg7.roundToLong())} steps. " +
                "Aim for about ${formatCount(aim)} steps today, or a 15-minute walk, to get back on track.",
            InsightTone.NUDGE,
        )
    }
    if (weekendChangePercent(byDate, end)?.let { it <= WEEKEND_LOW_PERCENT } == true) {
        candidates += Insight(
            InsightKind.ADVICE, "Weekend walk",
            "Weekends run well below weekdays. Try a 20-minute walk on Saturday or Sunday.",
            InsightTone.NUDGE,
        )
    }
    if (streakCurrent != null && streakCurrent >= 1) {
        candidates += Insight(
            InsightKind.ADVICE, "Keep the streak",
            "You are on a $streakCurrent-day goal streak. A short evening walk will keep it going.",
            InsightTone.POSITIVE,
        )
    }
    if (candidates.isEmpty()) {
        candidates += Insight(
            InsightKind.ADVICE, "Steady rhythm",
            "A 10-minute walk after a meal is a simple habit, and small walks add up.",
            InsightTone.POSITIVE,
        )
    }
    return candidates.take(ADVICE_LIMIT)
}

private fun parseIsoOrNull(iso: String): LocalDate? =
    try {
        LocalDate.parse(iso)
    } catch (e: DateTimeException) {
        null
    }

/** Valid records keyed by date. Negative step counts are dropped; the last duplicate wins. */
internal fun indexByDate(history: List<DailyRecord>): Map<LocalDate, Day> =
    history.mapNotNull { rec ->
        val date = parseIsoOrNull(rec.dateIso) ?: return@mapNotNull null
        if (rec.steps < 0) return@mapNotNull null
        date to Day(date, rec.steps, rec.totalKcal)
    }.toMap()

private fun windowDays(byDate: Map<LocalDate, Day>, start: LocalDate, end: LocalDate): List<Day> {
    val out = ArrayList<Day>()
    var d = start
    while (!d.isAfter(end)) {
        byDate[d]?.let { out += it }
        d = d.plusDays(1)
    }
    return out
}

private fun stepsOn(byDate: Map<LocalDate, Day>, date: LocalDate): Int = byDate[date]?.steps ?: 0

private fun averageSteps(days: List<Day>): Double = days.map { it.steps.toDouble() }.average()

private fun roundToHundred(value: Double): Long = (value / 100.0).roundToLong() * 100L

private fun dayWord(count: Int): String = if (count == 1) "day" else "days"
