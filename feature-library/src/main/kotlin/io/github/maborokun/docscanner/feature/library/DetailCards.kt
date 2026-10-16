package io.github.maborokun.docscanner.feature.library

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.maborokun.docscanner.core.designsystem.AppIcons
import io.github.maborokun.docscanner.core.designsystem.CategoryFilterChip
import io.github.maborokun.docscanner.core.designsystem.docTextFieldColors
import io.github.maborokun.docscanner.domain.model.Document
import io.github.maborokun.docscanner.domain.model.DocumentCategory
import io.github.maborokun.docscanner.domain.model.Page
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Category and comment: the user's own notes about the document, saved as they type. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsCard(
    document: Document,
    actions: DetailActions,
) {
    var comment by rememberSaveable(document.id) { mutableStateOf(document.comment) }
    val stored by rememberUpdatedState(document.comment)
    // Saves a moment after the last keystroke instead of on every one.
    LaunchedEffect(comment) {
        if (comment != stored) {
            delay(COMMENT_SAVE_DELAY_MS)
            actions.onComment(comment)
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.details), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.type),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DocumentCategory.entries.forEach { category ->
                    CategoryFilterChip(
                        category = category,
                        selected = document.category == category,
                        onClick = { actions.onCategory(category) },
                    )
                }
            }
            // shortcut: the field cannot take focus before the first touch (no keyboard-only entry), fine for a
            // touch-first app; it stops Android handing this field the window's initial focus and opening the keyboard.
            var touched by remember(document.id) { mutableStateOf(false) }
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text(stringResource(R.string.comment)) },
                placeholder = { Text(stringResource(R.string.comment_hint)) },
                minLines = 2,
                colors = docTextFieldColors(onCard = true),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusProperties { canFocus = touched }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(pass = PointerEventPass.Initial)
                                touched = true
                            }
                        },
            )
        }
    }
}

/** What the text recognition read on the page being looked at, selectable and copyable like a camera lens would. */
@Composable
internal fun RecognisedText(
    page: Page,
    pageCount: Int,
    snackbar: SnackbarHostState,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipLabel = stringResource(R.string.recognised_text)
    val copiedMessage = stringResource(R.string.text_copied)

    fun copy(text: String) {
        context
            .getSystemService(Context.CLIPBOARD_SERVICE)
            .let { it as ClipboardManager }
            .setPrimaryClip(ClipData.newPlainText(clipLabel, text))
        scope.launch { snackbar.showSnackbar(copiedMessage) }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.text_on_page),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand),
                    modifier = Modifier.padding(12.dp),
                )
            }
            if (expanded) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (pageCount > 1) {
                                stringResource(R.string.page_n, page.index + 1)
                            } else {
                                stringResource(R.string.tap_hold_select)
                            },
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = { copy(page.ocrText) }) {
                            Icon(AppIcons.Copy, contentDescription = null, Modifier.size(18.dp))
                            Text(stringResource(R.string.copy), Modifier.padding(start = 6.dp))
                        }
                    }
                    SelectionContainer { Text(page.ocrText, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}
