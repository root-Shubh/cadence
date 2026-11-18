package com.stepwise.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.stepwise.goals.DailyProgress
import com.stepwise.goals.Goals
import com.stepwise.goals.GoalSuggestion
import com.stepwise.goals.MonthlyProgress
import com.stepwise.goals.StepPoint
import com.stepwise.goals.WeeklyProgress
import com.stepwise.goals.dailyProgress
import com.stepwise.goals.monthlyProgress
import com.stepwise.goals.monthlySuggestion
import com.stepwise.goals.weeklyProgress
import com.stepwise.goals.weeklySuggestion

/** Shows progress for each goal the user has set, for the day [todayIso]. */
@Composable
fun GoalsCard(
    points: List<StepPoint>,
    goals: Goals,
    todayIso: String,
    modifier: Modifier = Modifier,
    onSetGoals: () -> Unit = {},
) {
    if (goals.dailySteps == null && goals.weeklySteps == null && goals.monthlySteps == null) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Goals",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Set a daily, weekly or monthly goal to see progress.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = onSetGoals,
                    modifier = Modifier.height(48.dp),
                ) {
                    Text("Set goals")
                }
            }
        }
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        dailyProgress(points, goals, todayIso)?.let { DailyGoalCard(it) }
        weeklyProgress(points, goals, todayIso)?.let { WeeklyGoalCard(it) }
        monthlyProgress(points, goals, todayIso)?.let { MonthlyGoalCard(it) }
    }
}

@Composable
private fun DailyGoalCard(progress: DailyProgress) {
    GoalCardBody(
        label = "Today",
        steps = progress.steps,
        goal = progress.goal,
        status = statusText(progress.met, progress.deficit),
        suggestion = null,
    )
}

@Composable
private fun WeeklyGoalCard(progress: WeeklyProgress) {
    GoalCardBody(
        label = "Week (last 7 days)",
        steps = progress.steps,
        goal = progress.goal,
        status = statusText(progress.met, progress.deficit),
        suggestion = weeklySuggestion(progress.goal, progress.steps, progress.daysWithData),
    )
}

@Composable
private fun MonthlyGoalCard(progress: MonthlyProgress) {
    GoalCardBody(
        label = "Month",
        steps = progress.steps,
        goal = progress.goal,
        status = statusText(progress.met, progress.deficit),
        suggestion = monthlySuggestion(
            goal = progress.goal,
            monthTotal = progress.steps,
            remainingDays = progress.daysLeftInMonth,
        ),
    )
}

private fun statusText(met: Boolean, deficit: Int): String =
    if (met) "Goal reached" else "${formatCount(deficit.toLong())} steps to go"

@Composable
private fun GoalCardBody(
    label: String,
    steps: Int,
    goal: Int,
    status: String,
    suggestion: GoalSuggestion?,
) {
    val value = "${formatCount(steps.toLong())} of ${formatCount(goal.toLong())} steps"
    val suggestionText = suggestion?.text()
    val description = listOfNotNull(label, value, status, suggestionText).joinToString(". ")
    val fraction = (steps.toFloat() / goal.toFloat()).coerceIn(0f, 1f)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
            if (suggestionText != null) {
                Text(
                    text = suggestionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
