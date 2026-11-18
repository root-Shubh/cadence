package com.cadence.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cadence.goals.PeriodSummary
import java.util.Locale

/** Number of pages: Daily, Pattern gallery, Weekly and Monthly. */
private const val PAGE_COUNT = 4

/**
 * Swipeable row of four cards under the Today ring: page 0 is the daily card passed in as
 * [dailyCard]; page 1 is the daily pattern gallery passed in as [gallery]; page 2 is the weekly
 * goal; page 3 is the monthly goal. Snap fling keeps one page on screen, and dots below show the
 * current page. A null [weekly] or [monthly] shows a placeholder card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GoalCardsPager(
    dailyCard: @Composable () -> Unit,
    gallery: @Composable () -> Unit,
    weekly: PeriodSummary?,
    monthly: PeriodSummary?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentPage by remember {
        derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, PAGE_COUNT - 1) }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val pageWidth = maxWidth
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            flingBehavior = rememberSnapFlingBehavior(listState),
        ) {
            item(key = "daily") { Page(pageWidth) { dailyCard() } }
            item(key = "patterns") { Page(pageWidth) { gallery() } }
            item(key = "weekly") {
                Page(pageWidth) {
                    GoalPeriodCard(
                        title = "Weekly goal",
                        periodLabel = "this week",
                        summary = weekly,
                        container = if (isDarkTheme()) Color(GoalCardTokens.WeeklyContainerDark)
                        else Color(GoalCardTokens.WeeklyContainerLight),
                        onContainer = if (isDarkTheme()) Color(GoalCardTokens.WeeklyOnDark)
                        else Color(GoalCardTokens.WeeklyOnLight),
                    )
                }
            }
            item(key = "monthly") {
                Page(pageWidth) {
                    GoalPeriodCard(
                        title = "Monthly goal",
                        periodLabel = "this month",
                        summary = monthly,
                        container = if (isDarkTheme()) Color(GoalCardTokens.MonthlyContainerDark)
                        else Color(GoalCardTokens.MonthlyContainerLight),
                        onContainer = if (isDarkTheme()) Color(GoalCardTokens.MonthlyOnDark)
                        else Color(GoalCardTokens.MonthlyOnLight),
                    )
                }
            }
        }
        PageDots(current = currentPage, count = PAGE_COUNT)
    }
    }
}

/** One full-width page, sized to the row's max width. */
@Composable
private fun Page(pageWidth: Dp, content: @Composable () -> Unit) {
    Box(modifier = Modifier.width(pageWidth)) { content() }
}

@Composable
private fun GoalPeriodCard(
    title: String,
    periodLabel: String,
    summary: PeriodSummary?,
    container: Color,
    onContainer: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .padding(16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = periodDescription(title, periodLabel, summary)
            },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = onContainer,
                )
                Text(
                    text = if (summary != null) "${formatCount(summary.steps.toLong())} steps" else "-",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                )
                Text(
                    text = periodLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
                )
            }
            if (summary != null && summary.goal != null) {
                LinearProgressIndicator(
                    progress = { summary.barFraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = onContainer,
                    trackColor = onContainer.copy(alpha = 0.2f),
                )
                Text(
                    text = "${summary.percent}% of ${formatCount(summary.goal.toLong())}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                )
            } else {
                Text(
                    text = "Set a ${periodWord(title)} goal in Profile",
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
                )
            }
            if (summary != null) {
                Text(
                    text = "Average ${formatCount(summary.averagePerDay.toLong())} per day",
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
                )
            }
        }
    }
}

/** Plain-language description of a card for TalkBack. */
internal fun periodDescription(title: String, periodLabel: String, summary: PeriodSummary?): String {
    if (summary == null) return "$title, no data"
    val goalPart = if (summary.goal != null && summary.percent != null) {
        "${summary.percent} percent of ${formatCount(summary.goal.toLong())} steps goal"
    } else {
        "no goal set"
    }
    return "$title: ${formatCount(summary.steps.toLong())} steps $periodLabel, $goalPart, " +
        "average ${formatCount(summary.averagePerDay.toLong())} steps per day"
}

private fun periodWord(title: String): String =
    title.removeSuffix(" goal").lowercase(Locale.US)

@Composable
private fun PageDots(current: Int, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Page ${current + 1} of $count" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(8.dp)
                    .background(
                        color = if (index == current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
