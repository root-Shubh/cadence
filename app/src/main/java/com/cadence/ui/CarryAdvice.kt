package com.cadence.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Why carrying matters: the phone's step sensor looks for the swing of a walking body. Held steady
 * in front of you, it often sees no walk and counts nothing (seen on a Galaxy S24 Ultra: 0 of about
 * 60 steps in the hand, 51 of 50 in a pocket).
 */
const val CARRY_ADVICE =
    "Holding the phone steady in front of you while walking can stop the step sensor from " +
        "recognising your steps. For the best count, carry it in a pocket or on your arm."

/**
 * A small info icon. Tapping it shows [text] in a dialog. Placed beside Record so the advice is
 * one tap away without taking space on the screen.
 */
@Composable
fun CarryInfoButton(
    modifier: Modifier = Modifier,
    text: String = CARRY_ADVICE,
    tint: Color = LocalContentColor.current,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = "How to carry the phone for an accurate count",
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            title = { Text("Carrying the phone") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("OK") } },
        )
    }
}
