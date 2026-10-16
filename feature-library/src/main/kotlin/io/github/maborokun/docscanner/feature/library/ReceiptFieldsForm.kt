package io.github.maborokun.docscanner.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.maborokun.docscanner.core.designsystem.docTextFieldColors
import io.github.maborokun.docscanner.domain.model.ReceiptFields
import io.github.maborokun.docscanner.domain.receipt.FieldError
import io.github.maborokun.docscanner.domain.receipt.FieldsInput
import io.github.maborokun.docscanner.domain.receipt.toInput
import kotlin.math.roundToInt

/** Detected receipt values, editable: they are guesses from OCR, so the user has the last word. */
@Composable
internal fun ReceiptFieldsForm(
    fields: ReceiptFields?,
    form: FieldsFormState,
    onSave: (FieldsInput) -> Unit,
    onEdited: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Starts from the stored values and restarts whenever they change (after a save).
    val stored = fields.toInput()
    var input by remember(fields) { mutableStateOf(stored) }
    val changed = input != stored

    fun edit(change: (FieldsInput) -> FieldsInput) {
        input = change(input)
        onEdited()
    }

    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.receipt_details), style = MaterialTheme.typography.titleMedium)
                Text(
                    detectionNote(fields),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = input.merchant,
                onValueChange = { edit { f -> f.copy(merchant = it) } },
                label = { Text(stringResource(R.string.merchant)) },
                singleLine = true,
                colors = docTextFieldColors(onCard = true),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = input.date,
                onValueChange = { edit { f -> f.copy(date = it) } },
                label = { Text(stringResource(R.string.date)) },
                placeholder = { Text("YYYY-MM-DD") },
                isError = FieldError.Date in form.errors,
                supportingText = { if (FieldError.Date in form.errors) Text(stringResource(R.string.date_error)) },
                singleLine = true,
                colors = docTextFieldColors(onCard = true),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = input.total,
                    onValueChange = { edit { f -> f.copy(total = it) } },
                    label = { Text(stringResource(R.string.total)) },
                    isError = FieldError.Total in form.errors,
                    supportingText = {
                        if (FieldError.Total in
                            form.errors
                        ) {
                            Text(stringResource(R.string.total_error))
                        }
                    },
                    singleLine = true,
                    colors = docTextFieldColors(onCard = true),
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = input.currency,
                    onValueChange = { edit { f -> f.copy(currency = it) } },
                    label = { Text(stringResource(R.string.currency)) },
                    isError = FieldError.Currency in form.errors,
                    supportingText = {
                        if (FieldError.Currency in
                            form.errors
                        ) {
                            Text(stringResource(R.string.currency_error))
                        }
                    },
                    singleLine = true,
                    colors = docTextFieldColors(onCard = true),
                    modifier = Modifier.weight(CURRENCY_WEIGHT),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onSave(input) }, enabled = changed) { Text(stringResource(R.string.save_details)) }
                if (form.saved && !changed) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.saved),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun detectionNote(fields: ReceiptFields?): String =
    when {
        fields == null -> stringResource(R.string.receipt_nothing)
        fields.confidence >= 1f -> stringResource(R.string.receipt_confirmed)
        else -> stringResource(R.string.receipt_detected, (fields.confidence * PERCENT).roundToInt())
    }

private const val PERCENT = 100

private const val CURRENCY_WEIGHT = 1.4f
