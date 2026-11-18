package com.stepwise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** One feature shown on the welcome carousel. */
data class WelcomeFeature(val icon: ImageVector, val title: String, val body: String)

/** The features shown on the first onboarding step, in the order they are shown. */
val welcomeFeatures: List<WelcomeFeature> = listOf(
    WelcomeFeature(Icons.Filled.DirectionsWalk, "Count every step", "Uses your phone's step sensor. Works offline, no account needed."),
    WelcomeFeature(Icons.Filled.LocalFireDepartment, "Know your calories", "Resting and walking calories from your height, weight and age."),
    WelcomeFeature(Icons.AutoMirrored.Filled.TrendingUp, "See your trends", "Week, month and quarter charts, with your best weekdays."),
    WelcomeFeature(Icons.Filled.Flag, "Set goals", "Daily, weekly and monthly targets, with clear next steps."),
    WelcomeFeature(Icons.Filled.Straighten, "Calibrate your stride", "Walk a known route with GPS for a more accurate distance."),
    WelcomeFeature(Icons.Filled.Lock, "Private by design", "Your data never leaves this phone. Delete it any time."),
)

/** A swipeable row of feature cards with snapping and position dots. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeatureCarousel(modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val current by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - center) }?.index ?: 0
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(340.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            flingBehavior = rememberSnapFlingBehavior(listState),
        ) {
            itemsIndexed(welcomeFeatures) { _, feature ->
                FeatureCard(feature, Modifier.fillParentMaxWidth(0.84f))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            welcomeFeatures.indices.forEach { index ->
                val selected = index == current
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (selected) 10.dp else 8.dp)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(feature: WelcomeFeature, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().height(340.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = feature.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column {
                Text(feature.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    feature.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
