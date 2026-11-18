package com.cadence.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cadence.data.ProfileState
import com.cadence.data.defaultProfileName
import com.cadence.data.profileNameError

/**
 * Row of profile chips (the active one selected), a "+ New profile" chip, and Rename and
 * Delete actions for the active profile. Each action asks in a dialog first; Delete uses a
 * red confirm button. Every chip and action is at least 48 dp tall.
 */
@Composable
fun ProfilePicker(
    profiles: List<ProfileState>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDeleteActive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNew by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val active = profiles.firstOrNull { it.id == activeId }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            profiles.forEach { profile ->
                FilterChip(
                    selected = profile.id == activeId,
                    onClick = { onSelect(profile.id) },
                    label = { Text(profile.name, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
            FilterChip(
                selected = false,
                onClick = { showNew = true },
                label = { Text("+ New profile", maxLines = 1) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }

        if (active != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { showRename = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Rename")
                }
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showNew) {
        ProfileNameDialog(
            title = "New profile",
            confirmLabel = "Create",
            initial = defaultProfileName(profiles),
            onConfirm = { name ->
                showNew = false
                onAdd(name)
            },
            onDismiss = { showNew = false },
        )
    }

    if (showRename && active != null) {
        ProfileNameDialog(
            title = "Rename profile",
            confirmLabel = "Rename",
            initial = active.name,
            onConfirm = { name ->
                showRename = false
                onRename(active.id, name)
            },
            onDismiss = { showRename = false },
        )
    }

    if (confirmDelete && active != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete profile?") },
            text = {
                Text("This removes the profile \"${active.name}\". Its step history is kept.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDeleteActive()
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
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

/** Dialog with one name field. Confirm is enabled only for a valid (1 to 30 character) name. */
@Composable
private fun ProfileNameDialog(
    title: String,
    confirmLabel: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val error = profileNameError(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                isError = error != null,
                supportingText = { Text(error ?: "1 to 30 characters") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim()) },
                enabled = error == null,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        },
    )
}
