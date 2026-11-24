package com.sarotech.lthisab.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sarotech.lthisab.data.AppSettings
import com.sarotech.lthisab.data.BalanceCalculator
import com.sarotech.lthisab.data.CashTransaction
import com.sarotech.lthisab.i18n.LocalStrings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AddEditTransactionDialog(
    existing: CashTransaction?,
    settings: AppSettings,
    recentNotes: List<String>,
    onDismiss: () -> Unit,
    onSave: (type: String, amountPaise: Long, note: String, timestamp: Long) -> Unit
) {
    val strings = LocalStrings.current
    val context = LocalContext.current

    var type by remember(existing) { mutableStateOf(existing?.type ?: "OUT") }
    var amountText by remember(existing) {
        mutableStateOf(
            if (existing != null) String.format(Locale.US, "%.2f", existing.amountPaise / 100.0) else ""
        )
    }
    var note by remember(existing) { mutableStateOf(existing?.note ?: "") }
    var timestamp by remember(existing) { mutableLongStateOf(existing?.timestamp ?: System.currentTimeMillis()) }
    var error by remember { mutableStateOf("") }

    val dateTimeFmt = remember(settings.dateFormat, settings.use24HourTime) {
        SimpleDateFormat(settings.dateTimePattern(), Locale.getDefault())
    }

    fun openDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                val updated = Calendar.getInstance().apply {
                    timeInMillis = timestamp
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                timestamp = updated.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun openTimePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val updated = Calendar.getInstance().apply {
                    timeInMillis = timestamp
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                }
                timestamp = updated.timeInMillis
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            settings.use24HourTime
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) strings.addTransaction else strings.editTransaction,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = type == "IN",
                        onClick = { type = "IN" },
                        modifier = Modifier.weight(1f),
                        label = { Text(strings.cashIn, maxLines = 1) },
                        leadingIcon = {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                    FilterChip(
                        selected = type == "OUT",
                        onClick = { type = "OUT" },
                        modifier = Modifier.weight(1f),
                        label = { Text(strings.cashOut, maxLines = 1) },
                        leadingIcon = {
                            Icon(Icons.Default.TrendingDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() || it == '.' || it == ',' }) {
                            amountText = input
                            error = ""
                        }
                    },
                    label = { Text("${strings.amountLabel} (${settings.currencySymbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it; error = "" },
                    label = { Text(strings.notesLabel) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                if (settings.notesSuggestionsEnabled && recentNotes.isNotEmpty()) {
                    val matching = remember(note, recentNotes) {
                        if (note.isBlank()) recentNotes.take(6)
                        else recentNotes.filter {
                            it.contains(note.trim(), ignoreCase = true) && !it.equals(note.trim(), ignoreCase = true)
                        }.take(6)
                    }
                    if (matching.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = strings.notesSuggestions,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                matching.forEach { suggestion ->
                                    AssistChip(
                                        onClick = { note = suggestion; error = "" },
                                        label = { Text(suggestion, maxLines = 1) }
                                    )
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${strings.dateAndTime}: ${dateTimeFmt.format(Date(timestamp))}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { openDatePicker() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.pickDate, fontSize = 12.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { openTimePicker() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.pickTime, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }

                if (error.isNotBlank()) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val paise = BalanceCalculator.parseAmountToPaise(amountText, allowNegative = false)
                when {
                    paise == null -> error = strings.amountRequired
                    note.isBlank() -> error = strings.notesRequired
                    else -> onSave(type, paise, note.trim(), timestamp)
                }
            }) {
                Text(if (existing == null) strings.save else strings.update)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}
