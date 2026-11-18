package com.stepwise.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.stepwise.data.ProfileState
import com.stepwise.data.profileNameError
import com.stepwise.data.validateProfile

private val ONBOARDING_DECIMAL = KeyboardOptions(keyboardType = KeyboardType.Decimal)
private val ONBOARDING_INTEGER = KeyboardOptions(keyboardType = KeyboardType.Number)

private val SEX_OPTIONS = listOf("MALE" to "Male", "FEMALE" to "Female", "OTHER" to "Other")

/**
 * First-launch flow: welcome, permissions, profile, done. Profile values are held here so they
 * survive moving between steps. [onSaveProfile] persists a valid profile and may throw
 * IllegalArgumentException; [onFinish] is called from the last step.
 */
@Composable
fun OnboardingFlow(
    onSaveProfile: (ProfileState) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME) }

    var name by rememberSaveable { mutableStateOf("Me") }
    var height by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf("") }
    var stride by rememberSaveable { mutableStateOf("") }
    var profileErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var saveError by remember { mutableStateOf<String?>(null) }

    // Permission status, re-read whenever the screen resumes (the user may return from Settings).
    var activityGranted by remember { mutableStateOf(isActivityGranted(context)) }
    var locationGranted by remember { mutableStateOf(isLocationGranted(context)) }
    var notificationsGranted by remember { mutableStateOf(isNotificationGranted(context)) }
    var locationSkipped by rememberSaveable { mutableStateOf(false) }
    var notificationsSkipped by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                activityGranted = isActivityGranted(context)
                locationGranted = isLocationGranted(context)
                notificationsGranted = isNotificationGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { activityGranted = isActivityGranted(context) }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { locationGranted = isLocationGranted(context) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { notificationsGranted = isNotificationGranted(context) }

    // Validates the profile form and saves it. Returns true when the profile was saved.
    fun trySaveProfile(): Boolean {
        val heightValue = height.trim().toDoubleOrNull()
        val weightValue = weight.trim().toDoubleOrNull()
        val ageValue = age.trim().toIntOrNull()
        val strideValue = stride.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        val sexValue = sex.ifEmpty { null }
        val nameError = profileNameError(name)

        val found = validateProfile(heightValue, weightValue, ageValue, sexValue, strideValue) +
            listOfNotNull(nameError)
        profileErrors = found
        saveError = null
        if (found.isNotEmpty()) return false

        val h = heightValue ?: return false
        val w = weightValue ?: return false
        val a = ageValue ?: return false
        val s = sexValue ?: return false
        return try {
            onSaveProfile(
                ProfileState(
                    heightCm = h,
                    weightKg = w,
                    age = a,
                    sex = s,
                    strideOverrideCm = strideValue,
                    name = name.trim(),
                ),
            )
            true
        } catch (e: IllegalArgumentException) {
            saveError = e.message ?: "Could not save the profile."
            false
        }
    }

    fun onNext() {
        when (step) {
            OnboardingStep.PROFILE -> if (trySaveProfile()) step = nextStep(step)
            else -> step = nextStep(step)
        }
    }

    Box(modifier = modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LinearProgressIndicator(
                progress = { onboardingProgress(step) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Step ${step.ordinal + 1} of ${ONBOARDING_STEPS.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val enter = slideInHorizontally(tween(MOTION_MEDIUM_MS)) { full ->
                        if (forward) full else -full
                    } + fadeIn(tween(MOTION_MEDIUM_MS))
                    val exit = slideOutHorizontally(tween(MOTION_MEDIUM_MS)) { full ->
                        if (forward) -full else full
                    } + fadeOut(tween(MOTION_MEDIUM_MS))
                    enter togetherWith exit
                },
                label = "onboardingStep",
            ) { current ->
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (current) {
                        OnboardingStep.WELCOME -> WelcomeStep()
                        OnboardingStep.PERMISSIONS -> PermissionsStep(
                            activityGranted = activityGranted,
                            locationGranted = locationGranted,
                            notificationsGranted = notificationsGranted,
                            locationSkipped = locationSkipped,
                            notificationsSkipped = notificationsSkipped,
                            onAllowActivity = { activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
                            onAllowLocation = {
                                locationLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            },
                            onSkipLocation = { locationSkipped = true },
                            onAllowNotifications = {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            onSkipNotifications = { notificationsSkipped = true },
                            onOpenSettings = { openAppSettings(context) },
                        )
                        OnboardingStep.PROFILE -> ProfileStep(
                            name = name,
                            onName = { name = it },
                            height = height,
                            onHeight = { height = it },
                            weight = weight,
                            onWeight = { weight = it },
                            age = age,
                            onAge = { age = it },
                            sex = sex,
                            onSex = { sex = it },
                            stride = stride,
                            onStride = { stride = it },
                            errors = profileErrors,
                            saveError = saveError,
                        )
                        OnboardingStep.DONE -> DoneStep(onFinish = onFinish)
                    }
                }
            }

            if (step != OnboardingStep.DONE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { step = previousStep(step) },
                        enabled = step != OnboardingStep.WELCOME,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text("Back")
                    }
                    Button(
                        onClick = { onNext() },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text(if (step == OnboardingStep.PROFILE) "Save" else "Next")
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { step = previousStep(step) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Back")
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Text("Welcome to Stepwise", style = MaterialTheme.typography.headlineSmall)
    Text("Everything stays on this phone. Nothing is uploaded.", style = MaterialTheme.typography.bodyLarge)
    FeatureCarousel(modifier = Modifier.padding(top = 8.dp))}

@Composable
private fun PermissionsStep(
    activityGranted: Boolean,
    locationGranted: Boolean,
    notificationsGranted: Boolean,
    locationSkipped: Boolean,
    notificationsSkipped: Boolean,
    onAllowActivity: () -> Unit,
    onAllowLocation: () -> Unit,
    onSkipLocation: () -> Unit,
    onAllowNotifications: () -> Unit,
    onSkipNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Text("Permissions", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Each permission is asked for in turn. Only Physical activity is needed. The other two are optional, and you can continue without them.",
        style = MaterialTheme.typography.bodyMedium,
    )

    PermissionCard(
        title = "Physical activity",
        buttonLabel = "Allow Physical activity",
        detail = "Needed to count your steps.",
        granted = activityGranted,
        onAllow = onAllowActivity,
    )
    PermissionCard(
        title = "Location (optional)",
        buttonLabel = "Allow Location",
        detail = "Only used to calibrate your stride with GPS. Location is not stored or uploaded.",
        granted = locationGranted,
        onAllow = onAllowLocation,
        skipped = locationSkipped,
        onSkip = onSkipLocation,
    )
    PermissionCard(
        title = "Notifications (optional)",
        buttonLabel = "Allow Notifications",
        detail = "Shows your step count in the notification.",
        granted = notificationsGranted,
        onAllow = onAllowNotifications,
        skipped = notificationsSkipped,
        onSkip = onSkipNotifications,
        available = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
    )

    val blocked = permissionBlockedMessage(activityGranted)
    if (blocked != null) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(blocked, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "To enable it, open app settings, choose Permissions and allow Physical activity.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Open app settings")
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    buttonLabel: String,
    detail: String,
    granted: Boolean,
    onAllow: () -> Unit,
    skipped: Boolean = false,
    onSkip: (() -> Unit)? = null,
    available: Boolean = true,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (granted) "Granted" else "Not granted",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!granted && available && !skipped) {
                Button(
                    onClick = onAllow,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(buttonLabel)
                }
                if (onSkip != null) {
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Skip for now")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStep(
    name: String,
    onName: (String) -> Unit,
    height: String,
    onHeight: (String) -> Unit,
    weight: String,
    onWeight: (String) -> Unit,
    age: String,
    onAge: (String) -> Unit,
    sex: String,
    onSex: (String) -> Unit,
    stride: String,
    onStride: (String) -> Unit,
    errors: List<String>,
    saveError: String?,
) {
    Text("Your profile", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Used for calorie estimates and stride. You can change it later on Profile.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OnboardingField(
        value = name,
        onValueChange = onName,
        label = "Name",
        helper = "Shown on your profile",
        suffix = null,
        error = fieldError(errors, "Name"),
        keyboard = KeyboardOptions.Default,
    )
    OnboardingField(
        value = height,
        onValueChange = onHeight,
        label = "Height (cm)",
        helper = "Used for calories and stride",
        suffix = "cm",
        error = fieldError(errors, "Height"),
        keyboard = ONBOARDING_DECIMAL,
    )
    OnboardingField(
        value = weight,
        onValueChange = onWeight,
        label = "Weight (kg)",
        helper = "Used for calorie estimates",
        suffix = "kg",
        error = fieldError(errors, "Weight"),
        keyboard = ONBOARDING_DECIMAL,
    )
    OnboardingField(
        value = age,
        onValueChange = onAge,
        label = "Age (years)",
        helper = "Used for calorie estimates",
        suffix = "years",
        error = fieldError(errors, "Age"),
        keyboard = ONBOARDING_INTEGER,
    )

    Text(
        "Sex",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val sexError = fieldError(errors, "Sex")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SEX_OPTIONS.forEach { (value, label) ->
            FilterChip(
                selected = sex == value,
                onClick = { onSex(value) },
                label = { Text(label) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
    if (sexError != null) {
        Text(sexError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }

    OnboardingField(
        value = stride,
        onValueChange = onStride,
        label = "Stride override (cm, optional)",
        helper = "Leave blank to estimate stride from your height",
        suffix = "cm",
        error = fieldError(errors, "Stride"),
        keyboard = ONBOARDING_DECIMAL,
    )

    ungroupedErrors(errors).filterNot { it.startsWith("Name") }.forEach { message ->
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
    saveError?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DoneStep(onFinish: () -> Unit) {
    Text("You're set.", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Start counting on Today whenever you're ready.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Button(
        onClick = onFinish,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text("Open Stepwise")
    }
}

/** Outlined field with the same look as the Profile form: at least 56 dp, helper or error line. */
@Composable
private fun OnboardingField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    helper: String,
    suffix: String?,
    error: String?,
    keyboard: KeyboardOptions,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
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
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    )
}

private fun isActivityGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED

private fun isLocationGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun isNotificationGranted(context: Context): Boolean {
    val runtimeOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    return runtimeOk && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

/** Opens this app's page in system Settings. */
private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
