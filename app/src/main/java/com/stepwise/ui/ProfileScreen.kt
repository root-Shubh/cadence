package com.stepwise.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stepwise.data.ProfileState
import com.stepwise.data.ThemeChoice
import com.stepwise.data.validateProfile

private val SEX_CHOICES = listOf(
    "MALE" to "Male",
    "FEMALE" to "Female",
    "OTHER" to "Other",
)

private val THEME_CHOICES = listOf(
    ThemeChoice.SYSTEM to "System",
    ThemeChoice.LIGHT to "Light",
    ThemeChoice.DARK to "Dark",
)

private val DECIMAL_KEYBOARD = KeyboardOptions(keyboardType = KeyboardType.Decimal)
private val INTEGER_KEYBOARD = KeyboardOptions(keyboardType = KeyboardType.Number)

/**
 * Profile form. Blank or unparseable numeric fields are null for validation.
 * [onSave] is only called with a valid [ProfileState]; otherwise the validation
 * messages are shown under the form.
 *
 * Also shows the local-data notice, the theme choice, CSV export and the two
 * delete actions. Each delete is confirmed with a dialog before its callback runs.
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
) {
    var height by remember { mutableStateOf(initial?.heightCm?.toString() ?: "") }
    var weight by remember { mutableStateOf(initial?.weightKg?.toString() ?: "") }
    var age by remember { mutableStateOf(initial?.age?.toString() ?: "") }
    var sex by remember { mutableStateOf(initial?.sex ?: "") }
    var stride by remember { mutableStateOf(initial?.strideOverrideCm?.toString() ?: "") }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirmDeleteProfile by remember { mutableStateOf(false) }
    var confirmDeleteData by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrivacyNotice()

            Text("Profile", style = MaterialTheme.typography.headlineSmall)

            OutlinedTextField(
                value = height,
                onValueChange = { height = it },
                label = { Text("Height (cm)") },
                keyboardOptions = DECIMAL_KEYBOARD,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                label = { Text("Weight (kg)") },
                keyboardOptions = DECIMAL_KEYBOARD,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = age,
                onValueChange = { age = it },
                label = { Text("Age (years)") },
                keyboardOptions = INTEGER_KEYBOARD,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Sex", style = MaterialTheme.typography.titleSmall)
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                SEX_CHOICES.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = sex == value,
                                onClick = { sex = value },
                                role = Role.RadioButton,
                            ),
                    ) {
                        RadioButton(selected = sex == value, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }

            OutlinedTextField(
                value = stride,
                onValueChange = { stride = it },
                label = { Text("Stride override (cm, optional)") },
                keyboardOptions = DECIMAL_KEYBOARD,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            errors.forEach { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

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
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save profile")
            }

            Text("Theme", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                THEME_CHOICES.forEach { (choice, label) ->
                    FilterChip(
                        selected = theme == choice,
                        onClick = { onThemeChange(choice) },
                        label = { Text(label) },
                    )
                }
            }

            Text("Your data", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                Text("Export CSV")
            }
            OutlinedButton(
                onClick = { confirmDeleteProfile = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete profile")
            }
            OutlinedButton(
                onClick = { confirmDeleteData = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete all recorded data")
            }
        }
    }

    if (confirmDeleteProfile) {
        AlertDialog(
            onDismissRequest = { confirmDeleteProfile = false },
            title = { Text("Delete profile?") },
            text = { Text("This removes your saved profile. Your step history is kept.") },
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
            text = { Text("This removes all recorded step history. Your profile is kept.") },
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
