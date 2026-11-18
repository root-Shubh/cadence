package com.stepwise.ui

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
import com.stepwise.data.GoalInput
import com.stepwise.data.parseGoalInput
import com.stepwise.goals.Goals

private const val GOAL_ERROR = "Enter a whole number above 0"

/** Edits the daily, weekly and monthly step goals. Blank fields mean no goal. */
@Composable
fun GoalsEditor(
    current: Goals,
    onSave: (Goals) -> Unit,
    modifier: Modifier = Modifier,
) {
    var daily by remember(current) { mutableStateOf(current.dailySteps?.toString().orEmpty()) }
    var weekly by remember(current) { mutableStateOf(current.weeklySteps?.toString().orEmpty()) }
    var monthly by remember(current) {
        mutableStateOf(current.monthlySteps?.toString().orEmpty())
    }

    val dailyInput = parseGoalInput(daily)
    val weeklyInput = parseGoalInput(weekly)
    val monthlyInput = parseGoalInput(monthly)
    val canSave = listOf(dailyInput, weeklyInput, monthlyInput).none { it == GoalInput.Invalid }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Your goals",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        GoalField(
            label = "Daily steps",
            value = daily,
            onValueChange = { daily = it },
            isInvalid = dailyInput == GoalInput.Invalid,
        )
        GoalField(
            label = "Weekly steps",
            value = weekly,
            onValueChange = { weekly = it },
            isInvalid = weeklyInput == GoalInput.Invalid,
        )
        GoalField(
            label = "Monthly steps",
            value = monthly,
            onValueChange = { monthly = it },
            isInvalid = monthlyInput == GoalInput.Invalid,
        )
        Button(
            onClick = {
                onSave(
                    Goals(
                        dailySteps = (dailyInput as? GoalInput.Valid)?.value,
                        weeklySteps = (weeklyInput as? GoalInput.Valid)?.value,
                        monthlySteps = (monthlyInput as? GoalInput.Valid)?.value,
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

@Composable
private fun GoalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isInvalid: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isInvalid,
        supportingText = {
            Text(if (isInvalid) GOAL_ERROR else "steps")
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
