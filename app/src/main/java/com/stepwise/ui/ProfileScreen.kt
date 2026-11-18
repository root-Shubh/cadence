package com.stepwise.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Transgender
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stepwise.data.ProfileState
import com.stepwise.data.ThemeChoice
import com.stepwise.data.validateProfile
import kotlinx.coroutines.delay

private data class SexChoice(val value: String, val label: String, val icon: ImageVector)

private val SEX_CHOICES = listOf(
    SexChoice("MALE", "Male", Icons.Filled.Male),
    SexChoice("FEMALE", "Female", Icons.Filled.Female),
    SexChoice("OTHER", "Other", Icons.Filled.Transgender),
)

private val THEME_CHOICES = listOf(
    ThemeChoice.SYSTEM to "System",
    ThemeChoice.LIGHT to "Light",
    ThemeChoice.DARK to "Dark",
)

private val DECIMAL_KEYBOARD = KeyboardOptions(keyboardType = KeyboardType.Decimal)
private val INTEGER_KEYBOARD = KeyboardOptions(keyboardType = KeyboardType.Number)

/** How long the "Saved" confirmation stays visible. */
private const val SAVED_VISIBLE_MS = 1500L

/**
 * Profile form, grouped into cards. Blank or unparseable numeric fields are null for validation.
 * [onSave] is only called with a valid [ProfileState]; otherwise the validation
 * messages are shown under the matching fields.
 *
 * A "Your numbers" card previews BMR and daily RMR as the user types. Also shows the
 * local-data notice, the theme choice, CSV export and the two delete actions. Each delete
 * is confirmed with a dialog before its callback runs.
 */
@Composable
fun ProfileScreen(
    initial: ProfileState?,
    onSave: (ProfileState) -> Unit,
    modifier: Modifier = Modifier,
    theme: ThemeChoice = ThemeChoice.SYSTEM,
    onThemeChange: (ThemeChoice) -> Unit = {},
    onDeleteProfile: () -> Unit = {},
    onDeleteAllData: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onImportCsv: (String) -> Unit = {},
    onPickCsv: (Uri) -> Unit = {},
    profiles: List<ProfileState> = emptyList(),
    activeId: String? = null,
    onSelectProfile: (String) -> Unit = {},
    onAddProfile: (String) -> Unit = {},
    onRenameProfile: (String, String) -> Unit = { _, _ -> },
    onDeleteActiveProfile: () -> Unit = {},
    goalsSection: @Composable () -> Unit = {},
    showReviewBanner: Boolean = false,
) {
    // The form reloads whenever the active profile changes. A new profile has zero values,
    // which show as blank fields.
    val formKey = activeId ?: initial?.id
    var height by remember(formKey) { mutableStateOf(formatMeasure(initial?.heightCm?.takeIf { it > 0.0 })) }
    var weight by remember(formKey) { mutableStateOf(formatMeasure(initial?.weightKg?.takeIf { it > 0.0 })) }
    var age by remember(formKey) { mutableStateOf(initial?.age?.takeIf { it > 0 }?.toString() ?: "") }
    var sex by remember(formKey) { mutableStateOf(initial?.sex ?: "") }
    var stride by remember(formKey) { mutableStateOf(formatMeasure(initial?.strideOverrideCm)) }
    var errors by remember(formKey) { mutableStateOf<List<String>>(emptyList()) }
    var confirmDeleteProfile by remember { mutableStateOf(false) }
    var confirmDeleteData by remember { mutableStateOf(false) }
    var saveCount by remember { mutableIntStateOf(0) }
    var savedVisible by remember { mutableStateOf(false) }
    // Dismissing the review banner lasts for this screen session only.
    var reviewBannerDismissed by rememberSaveable { mutableStateOf(false) }

    val saveSource = remember { MutableInteractionSource() }
    val exportSource = remember { MutableInteractionSource() }
    val importSource = remember { MutableInteractionSource() }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onPickCsv(uri)
            onImportCsv(uri.toString())
        }
    }
    val deleteProfileSource = remember { MutableInteractionSource() }
    val deleteDataSource = remember { MutableInteractionSource() }

    val estimate = profileEstimate(
        heightCm = height.trim().toDoubleOrNull(),
        weightKg = weight.trim().toDoubleOrNull(),
        age = age.trim().toIntOrNull(),
        sex = sex.ifEmpty { null },
    )

    // Shows "Saved" for 1.5 s each time a save succeeds.
    LaunchedEffect(saveCount) {
        if (saveCount > 0) {
            savedVisible = true
            delay(SAVED_VISIBLE_MS)
            savedVisible = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Reveal(index = 0, modifier = Modifier.fillMaxWidth()) {
                Text("Profile", style = MaterialTheme.typography.headlineSmall)
            }

            if (showReviewBanner && !reviewBannerDismissed) {
                Reveal(index = 1, modifier = Modifier.fillMaxWidth()) {
                    ReviewBanner(onDismiss = { reviewBannerDismissed = true })
                }
            }

            Reveal(index = 1, modifier = Modifier.fillMaxWidth()) {
                ProfilePicker(
                    profiles = profiles,
                    activeId = activeId,
                    onSelect = onSelectProfile,
                    onAdd = onAddProfile,
                    onRename = onRenameProfile,
                    onDeleteActive = onDeleteActiveProfile,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Reveal(index = 1, modifier = Modifier.fillMaxWidth()) {
                PrivacyNotice()
            }

            Reveal(index = 2, modifier = Modifier.fillMaxWidth()) {
                SectionCard(title = "About you", icon = Icons.Filled.Person) {
                    ProfileField(
                        value = height,
                        onValueChange = { height = it },
                        label = "Height (cm)",
                        helper = "Used for calories and stride",
                        suffix = "cm",
                        error = fieldError(errors, "Height"),
                        keyboard = DECIMAL_KEYBOARD,
                    )
                    ProfileField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = "Weight (kg)",
                        helper = "Used for calorie estimates",
                        suffix = "kg",
                        error = fieldError(errors, "Weight"),
                        keyboard = DECIMAL_KEYBOARD,
                    )
                    ProfileField(
                        value = age,
                        onValueChange = { age = it },
                        label = "Age (years)",
                        helper = "Used for calorie estimates",
                        suffix = "years",
                        error = fieldError(errors, "Age"),
                        keyboard = INTEGER_KEYBOARD,
                    )

                    Text(
                        "Sex",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SexSelector(
                        selected = sex,
                        onSelect = { sex = it },
                        isError = fieldError(errors, "Sex") != null,
                    )
                    val sexError = fieldError(errors, "Sex")
                    Text(
                        text = sexError ?: "Used for the BMR formula",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (sexError != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Reveal(index = 3, modifier = Modifier.fillMaxWidth()) {
                SectionCard(title = "Walking", icon = Icons.AutoMirrored.Filled.DirectionsWalk, tone = Tone.TERTIARY) {
                    ProfileField(
                        value = stride,
                        onValueChange = { stride = it },
                        label = "Stride override (cm, optional)",
                        helper = "Leave blank to estimate stride from your height",
                        suffix = "cm",
                        error = fieldError(errors, "Stride"),
                        keyboard = DECIMAL_KEYBOARD,
                    )
                }
            }

            Reveal(index = 4, modifier = Modifier.fillMaxWidth()) {
                SectionCard(title = "Your numbers", icon = Icons.Filled.Calculate, tone = Tone.SECONDARY) {
                    if (estimate == null) {
                        Text(
                            "Enter your details to see estimates",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                AnimatedCountText(
                                    value = estimate.bmrKcal,
                                    suffix = " kcal",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    "BMR: at complete rest",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                AnimatedCountText(
                                    value = estimate.dailyRestKcal,
                                    suffix = " kcal",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Text(
                                    "Daily RMR estimate",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            ungroupedErrors(errors).forEach { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Reveal(index = 5, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val heightValue = height.trim().toDoubleOrNull()
                            val weightValue = weight.trim().toDoubleOrNull()
                            val ageValue = age.trim().toIntOrNull()
                            val strideValue = stride.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                            val sexValue = sex.ifEmpty { null }

                            val found = validateProfile(
                                heightCm = heightValue,
                                weightKg = weightValue,
                                age = ageValue,
                                sex = sexValue,
                                strideOverrideCm = strideValue,
                            )
                            errors = found
                            if (found.isEmpty() && heightValue != null && weightValue != null &&
                                ageValue != null && sexValue != null
                            ) {
                                onSave(
                                    ProfileState(
                                        heightCm = heightValue,
                                        weightKg = weightValue,
                                        age = ageValue,
                                        sex = sexValue,
                                        strideOverrideCm = strideValue,
                                    )
                                )
                                saveCount++
                            }
                        },
                        interactionSource = saveSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .pressScale(saveSource),
                    ) {
                        Text("Save profile")
                    }
                    SavedIndicator(visible = savedVisible)
                }
            }

            Reveal(index = 6, modifier = Modifier.fillMaxWidth()) {
                SectionCard(title = "Appearance", icon = Icons.Filled.Palette, tone = Tone.TERTIARY) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        THEME_CHOICES.forEach { (choice, label) ->
                            FilterChip(
                                selected = theme == choice,
                                onClick = { onThemeChange(choice) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                }
            }

            Reveal(index = 7, modifier = Modifier.fillMaxWidth()) {
                SectionCard(title = "Your data", icon = Icons.Filled.Storage, tone = Tone.SECONDARY) {
                    Button(
                        onClick = onExportCsv,
                        interactionSource = exportSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .pressScale(exportSource),
                    ) {
                        Text("Export CSV")
                    }
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf("text/csv", "text/comma-separated-values", "text/plain"),
                            )
                        },
                        interactionSource = importSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .pressScale(importSource),
                    ) {
                        Text("Import CSV")
                    }
                    OutlinedButton(
                        onClick = { confirmDeleteProfile = true },
                        interactionSource = deleteProfileSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .pressScale(deleteProfileSource),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) {
                        Text("Delete profile")
                    }
                    OutlinedButton(
                        onClick = { confirmDeleteData = true },
                        interactionSource = deleteDataSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .pressScale(deleteDataSource),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) {
                        Text("Delete all recorded data")
                    }
                }
            }
        }
        goalsSection()
    }

    if (confirmDeleteProfile) {
        AlertDialog(
            onDismissRequest = { confirmDeleteProfile = false },
            title = { Text("Delete profile?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This removes your saved profile. Your step history is kept.")
                    DeleteAdviceBlock(onExportCsv = onExportCsv)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteProfile = false
                        onDeleteProfile()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteProfile = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (confirmDeleteData) {
        AlertDialog(
            onDismissRequest = { confirmDeleteData = false },
            title = { Text("Delete all recorded data?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This removes all recorded step history. Your profile is kept.")
                    DeleteAdviceBlock(onExportCsv = onExportCsv)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteData = false
                        onDeleteAllData()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteData = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/** Advice shown inside delete confirmations, with a button that starts the CSV export first. */
@Composable
private fun DeleteAdviceBlock(onExportCsv: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Export your data as CSV first. Stepwise has no cloud storage, so deleting here cannot be undone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onExportCsv,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text("Export CSV first")
        }
    }
}

/** Outlined text field of at least 56 dp with a helper line that turns into the error when set. */
@Composable
private fun ProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    helper: String,
    suffix: String,
    error: String?,
    keyboard: KeyboardOptions,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = { Text(suffix) },
        supportingText = {
            Text(
                text = error ?: helper,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        isError = error != null,
        keyboardOptions = keyboard,
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    )
}

/** Prompt to re-check the numbers. The dismiss button keeps 48 dp touch height. */
@Composable
private fun ReviewBanner(onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Review your details", style = MaterialTheme.typography.titleMedium)
            Text(
                "Check your height, weight and age so calorie estimates stay accurate.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
            ) {
                Text("Dismiss")
            }
        }
    }
}

/** "Saved" confirmation with a checkmark. Space is reserved so the layout does not jump. */
@Composable
private fun SavedIndicator(visible: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(MOTION_FAST_MS)) + scaleIn(tween(MOTION_FAST_MS), initialScale = 0.8f),
            exit = fadeOut(tween(MOTION_FAST_MS)),
        ) {
            Row(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Saved",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Three-segment selector with icons. Each segment is a radio option with a 56 dp target. */
@Composable
private fun SexSelector(
    selected: String,
    onSelect: (String) -> Unit,
    isError: Boolean,
) {
    val shape = MaterialTheme.shapes.extraLarge
    val borderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .selectableGroup(),
    ) {
        SEX_CHOICES.forEach { choice ->
            val isSelected = selected == choice.value
            val background by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                animationSpec = tween(MOTION_FAST_MS),
                label = "sexBackground",
            )
            val foreground by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(MOTION_FAST_MS),
                label = "sexForeground",
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .background(background)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelect(choice.value) },
                        role = Role.RadioButton,
                    )
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = choice.icon,
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = choice.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = foreground,
                )
            }
        }
    }
}
