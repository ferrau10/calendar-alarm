package com.calendaralarm.ui.leadtime

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/** Number field for "minutes before"; shows why the value can't be used. */
@Composable
fun CustomMinutesField(text: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val parsed = parseLeadMinutes(text)
    OutlinedTextField(
        value = text,
        onValueChange = { onTextChange(it.filter(Char::isDigit).take(3)) },
        label = { Text("Minutes before") },
        suffix = { Text("min") },
        singleLine = true,
        isError = text.isNotEmpty() && parsed is LeadMinutesInput.Invalid,
        supportingText = {
            Text(if (parsed is LeadMinutesInput.Invalid && text.isNotEmpty()) parsed.reason else "0–$MAX_CUSTOM_LEAD_MINUTES")
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** Small dialog asking for a custom number of minutes. */
@Composable
fun CustomMinutesDialog(title: String, initialMinutes: Int?, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialMinutes?.toString().orEmpty()) }
    val parsed = parseLeadMinutes(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { CustomMinutesField(text, { text = it }) },
        confirmButton = {
            TextButton(
                onClick = { (parsed as? LeadMinutesInput.Valid)?.let { onSave(it.minutes) } },
                enabled = parsed is LeadMinutesInput.Valid,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
