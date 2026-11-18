package com.cadence.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.cadence.R

/** One feature shown on the welcome carousel, with a real screenshot of that feature. */
data class WelcomeFeature(
    val title: String,
    val body: String,
    val image: Int,
    val imageDark: Int,
    val imageDescription: String,
)

/** The features shown on the first onboarding step, in the order they are shown. */
val welcomeFeatures: List<WelcomeFeature> = listOf(
    WelcomeFeature(
        "Count every step",
        "Uses your phone's step sensor. Works offline, no account needed.",
        R.drawable.feature_today,
        R.drawable.feature_today_dark,
        "Today screen with the step count, distance and calories",
    ),
    WelcomeFeature(
        "Know your calories",
        "Resting and walking calories from your height, weight and age.",
        R.drawable.feature_profile,
        R.drawable.feature_profile_dark,
        "Profile screen where your height, weight and age set the calorie estimate",
    ),
    WelcomeFeature(
        "See your trends",
        "Week, month and quarter charts, with your best weekdays.",
        R.drawable.feature_trend,
        R.drawable.feature_trend_dark,
        "Trend screen with a 90-day chart, weekday averages and insights",
    ),
    WelcomeFeature(
        "Set goals",
        "Daily, weekly and monthly targets, with clear next steps.",
        R.drawable.feature_today,
        R.drawable.feature_today_dark,
        "Goals card on the Today screen with progress and suggestions",
    ),
    WelcomeFeature(
        "Calibrate your stride",
        "Walk a known route with GPS for a more accurate distance.",
        R.drawable.feature_calibrate,
        R.drawable.feature_calibrate_dark,
        "Calibrate screen for measuring stride with a GPS walk",
    ),
    WelcomeFeature(
        "Private by design",
        "Your data never leaves this phone. Delete it any time.",
        R.drawable.feature_profile,
        R.drawable.feature_profile_dark,
        "Profile screen with the on-device privacy notice and data controls",
    ),
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
            modifier = Modifier.fillMaxWidth().height(420.dp),
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
        modifier = modifier.fillMaxWidth().height(420.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // The screenshot sits at the top of the card, cropped to a fixed frame so every card lines up.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Image(
                    painter = painterResource(if (isSystemInDarkTheme()) feature.imageDark else feature.image),
                    contentDescription = feature.imageDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                )
            }
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text(feature.title, style = MaterialTheme.typography.titleLarge)
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
