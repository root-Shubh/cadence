package com.stepwise.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val PRIVACY_TEXT =
    "Your data stays on this phone. Your profile and step history are stored only on " +
        "this device. Nothing is uploaded. Uninstalling the app removes all of it."

/**
 * Local-data notice card. Pass [onGotIt] on the first-run card on Today so the
 * caller can record the acknowledgement and hide it. Leave it null for the
 * always-visible notice on Profile (no button shown).
 */
@Composable
fun PrivacyNotice(
    modifier: Modifier = Modifier,
    onGotIt: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(PRIVACY_TEXT, style = MaterialTheme.typography.bodyMedium)
            if (onGotIt != null) {
                Button(onClick = onGotIt) {
                    Text("Got it")
                }
            }
        }
    }
}
