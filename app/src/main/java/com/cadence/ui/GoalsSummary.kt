package com.cadence.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.cadence.goals.Goals
import com.cadence.goals.StepPoint
import com.cadence.goals.dailyProgress
import com.cadence.goals.effectiveGoals
import com.cadence.goals.monthlyProgress
import com.cadence.goals.weeklyProgress

/** Tracks where the goals card sits on screen, so a tap elsewhere can collapse it. */
class GoalsCardBounds {
    internal var container: LayoutCoordinates? = null
    internal var card: LayoutCoordinates? = null

    /** True when [position] (in the container's coordinates) is inside the card. */
    fun containsCard(position: Offset): Boolean {
        val outer = container?.takeIf { it.isAttached } ?: return false
        val inner = card?.takeIf { it.isAttached } ?: return false
        return outer.localBoundingBoxOf(inner).contains(position)
    }
}

/**
 * Calls [onCollapse] when a tap (press and release without dragging) lands outside the goals
 * card tracked by [bounds]. The pointer is observed in the initial pass and never consumed, so
 * the tap still reaches whatever was pressed. A drag, such as scrolling, does not collapse.
 */
@Composable
fun Modifier.collapseOnTapOutside(
    bounds: GoalsCardBounds,
    enabled: Boolean,
    onCollapse: () -> Unit,
): Modifier {
    val slop = LocalViewConfiguration.current.touchSlop
    return this
        .onGloballyPositioned { bounds.container = it }
        .pointerInput(enabled, slop) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    val press = awaitPointerEvent(PointerEventPass.Initial)
                    if (press.type != PointerEventType.Press) continue
                    val start = press.changes.first().position
                    val outside = !bounds.containsCard(start)
                    var moved = false
                    var event = press
                    while (event.changes.any { it.pressed }) {
                        event = awaitPointerEvent(PointerEventPass.Initial)
                        if ((event.changes.first().position - start).getDistance() > slop) moved = true
                    }
                    if (outside && !moved) onCollapse()
                }
            }
        }
}

/**
 * Goals card for the Profile screen. Collapsed it shows Daily, Weekly and Monthly progress bars
 * using the effective goals; tapping it expands to show [GoalsEditor] for the stored [goals].
 * The caller owns [expanded] so it can collapse the card on taps elsewhere.
 */
@Composable
fun ProfileGoalsCard(
    goals: Goals,
    points: List<StepPoint>,
    todayIso: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSave: (Goals) -> Unit,
    bounds: GoalsCardBounds,
    modifier: Modifier = Modifier,
) {
    val effective = effectiveGoals(goals)
    val rows = listOfNotNull(
        dailyProgress(points, effective, todayIso)?.let { GoalRow("Daily", it.steps, it.goal) },
        weeklyProgress(points, effective, todayIso)?.let { GoalRow("Weekly", it.steps, it.goal) },
        monthlyProgress(points, effective, todayIso)?.let { GoalRow("Monthly", it.steps, it.goal) },
    )
    val collapsedDescription = if (rows.isEmpty()) {
        "Goals, collapsed. Set a goal to see progress. Tap to expand"
    } else {
        "Goals, collapsed. " + rows.joinToString(". ") { it.description() } + ". Tap to expand"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .onGloballyPositioned { bounds.card = it }
            .then(
                if (expanded) {
                    Modifier
                } else {
                    Modifier
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Expand goals",
                            onClick = { onExpandedChange(true) },
                        )
                        .semantics(mergeDescendants = true) { contentDescription = collapsedDescription }
                },
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .then(
                        if (expanded) {
                            Modifier
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "Collapse goals",
                                    onClick = { onExpandedChange(false) },
                                )
                                .semantics {
                                    contentDescription = "Goals, expanded. Tap to collapse"
                                    stateDescription = "Expanded"
                                }
                        } else {
                            Modifier.semantics { stateDescription = "Collapsed" }
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Goals",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (rows.isEmpty()) {
                Text(
                    text = "Set a daily, weekly or monthly goal to see progress.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                rows.forEach { GoalSummaryRow(it) }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                GoalsEditor(
                    current = goals,
                    onSave = onSave,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private data class GoalRow(val label: String, val steps: Int, val goal: Int) {
    val percent: Int get() = (steps.toLong() * 100L / goal.toLong()).toInt()
    val value: String get() = "${formatCount(steps.toLong())} of ${formatCount(goal.toLong())} steps"
    fun description(): String = "$label: $value, $percent percent"
}

@Composable
private fun GoalSummaryRow(row: GoalRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = row.description() },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${row.percent}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { progressFraction(row.steps, row.goal) },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surface,
        )
        Text(
            text = row.value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
