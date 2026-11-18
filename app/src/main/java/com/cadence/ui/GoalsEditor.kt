package com.cadence.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cadence.data.GoalInput
import com.cadence.data.parseGoalInput
import com.cadence.goals.Goals
import com.cadence.goals.effectiveGoals
import java.util.Locale

private const val GOAL_ERROR = "Enter a whole number above 0"

/**
 * Edits the daily, weekly and monthly step goals.
 *
 * Any goal you type fixes that field. Fields you have not typed in are filled in automatically
 * from the goals you did type (daily x 7 for weekly, daily x 30 for monthly, and so on). Only
 * the typed fields are saved; the rest keep following them.
 */
@Composable
fun GoalsEditor(
    current: Goals,
    onSave: (Goals) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dailyTyped by remember(current) { mutableStateOf(current.dailySteps != null) }
    var weeklyTyped by remember(current) { mutableStateOf(current.weeklySteps != null) }
    var monthlyTyped by remember(current) { mutableStateOf(current.monthlySteps != null) }
    var daily by remember(current) { mutableStateOf(current.dailySteps?.toString().orEmpty()) }
    var weekly by remember(current) { mutableStateOf(current.weeklySteps?.toString().orEmpty()) }
    var monthly by remember(current) { mutableStateOf(current.monthlySteps?.toString().orEmpty()) }

    val dailyInput = parseGoalInput(daily)
    val weeklyInput = parseGoalInput(weekly)
    val monthlyInput = parseGoalInput(monthly)
    val canSave = listOf(dailyInput, weeklyInput, monthlyInput).none { it == GoalInput.Invalid }

    // Goals derived from whatever has been typed so far.
    val typed = Goals(
        dailySteps = if (dailyTyped) (dailyInput as? GoalInput.Valid)?.value else null,
        weeklySteps = if (weeklyTyped) (weeklyInput as? GoalInput.Valid)?.value else null,
        monthlySteps = if (monthlyTyped) (monthlyInput as? GoalInput.Valid)?.value else null,
    )
    val derived = effectiveGoals(typed)

    // Untyped fields show the derived value.
    val shownDaily = if (dailyTyped) daily else derived.dailySteps?.let { format(it) }.orEmpty()
    val shownWeekly = if (weeklyTyped) weekly else derived.weeklySteps?.let { format(it) }.orEmpty()
    val shownMonthly = if (monthlyTyped) monthly else derived.monthlySteps?.let { format(it) }.orEmpty()

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Your goals",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Set any one goal and the others fill in automatically. Edit a field to set it yourself.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GoalField(
            label = "Daily steps",
            value = shownDaily,
            onValueChange = { daily = it; dailyTyped = true },
            isInvalid = dailyTyped && dailyInput == GoalInput.Invalid,
            auto = !dailyTyped && derived.dailySteps != null,
        )
        GoalField(
            label = "Weekly steps",
            value = shownWeekly,
            onValueChange = { weekly = it; weeklyTyped = true },
            isInvalid = weeklyTyped && weeklyInput == GoalInput.Invalid,
            auto = !weeklyTyped && derived.weeklySteps != null,
        )
        GoalField(
            label = "Monthly steps",
            value = shownMonthly,
            onValueChange = { monthly = it; monthlyTyped = true },
            isInvalid = monthlyTyped && monthlyInput == GoalInput.Invalid,
            auto = !monthlyTyped && derived.monthlySteps != null,
        )
        Button(
            onClick = {
                onSave(
                    Goals(
                        dailySteps = if (dailyTyped) (dailyInput as? GoalInput.Valid)?.value else null,
                        weeklySteps = if (weeklyTyped) (weeklyInput as? GoalInput.Valid)?.value else null,
                        monthlySteps = if (monthlyTyped) (monthlyInput as? GoalInput.Valid)?.value else null,
                    ),
                )
            },
            enabled = canSave,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Save goals")
        }
    }
}

private fun format(value: Int): String = String.format(Locale.ROOT, "%,d", value)

@Composable
private fun GoalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isInvalid: Boolean,
    auto: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(if (auto) "$label (automatic)" else label) },
        singleLine = true,
        isError = isInvalid,
        supportingText = {
            Text(if (isInvalid) GOAL_ERROR else if (auto) "Calculated from your other goals" else "steps")
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
