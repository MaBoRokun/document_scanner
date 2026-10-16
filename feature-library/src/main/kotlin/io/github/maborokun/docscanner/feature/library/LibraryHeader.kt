package io.github.maborokun.docscanner.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.maborokun.docscanner.core.designsystem.CategoryFilterChip
import io.github.maborokun.docscanner.core.designsystem.ScreenMargin
import io.github.maborokun.docscanner.core.designsystem.displayName
import io.github.maborokun.docscanner.core.designsystem.docAssistChipColors
import io.github.maborokun.docscanner.core.designsystem.docFilterChipBorder
import io.github.maborokun.docscanner.core.designsystem.docFilterChipColors
import io.github.maborokun.docscanner.core.designsystem.docTextFieldColors
import io.github.maborokun.docscanner.domain.model.DocumentCategory

/** Title, search and category filter, pinned: they stay while the list scrolls underneath. */
@Composable
internal fun LibraryHeader(
    state: LibraryUiState,
    actions: LibraryScreenActions,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(start = ScreenMargin, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (state.selecting) {
                        pluralStringResource(R.plurals.selected_count, state.selected.size, state.selected.size)
                    } else {
                        stringResource(R.string.documents_title)
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (state.selecting) {
                    TextButton(onClick = actions.onCancelSelection) { Text(stringResource(R.string.cancel)) }
                } else {
                    IconButton(onClick = actions.onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            }
            SearchField(state.query, actions.onQueryChange, Modifier.padding(horizontal = ScreenMargin))
            CategoryChips(state, actions)
        }
    }
}

/**
 * Looks like a field but is only a tappable surface until the user taps it. A real text field in the layout takes
 * the window's initial focus, which opened the keyboard on launch and again when coming back from Settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val searchHint = stringResource(R.string.search_hint)
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val imeVisible = WindowInsets.isImeVisible
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) editing = false }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Closing the keyboard (back, swipe down) ends editing.
    LaunchedEffect(imeVisible) { if (editing && !imeVisible && hadFocus) focusManager.clearFocus() }
    if (editing) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(searchHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = { ClearButton(query, onQueryChange) },
            singleLine = true,
            shape = CircleShape,
            colors = docTextFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier =
                modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        if (it.isFocused) {
                            hadFocus = true
                        } else if (hadFocus) {
                            editing = false
                        }
                    },
        )
    } else {
        hadFocus = false
        Surface(
            onClick = { editing = true },
            modifier = modifier.fillMaxWidth().height(SEARCH_HEIGHT),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    query.ifEmpty { searchHint },
                    Modifier.weight(1f).padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (query.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ClearButton(query, onQueryChange)
            }
        }
    }
}

@Composable
internal fun ClearButton(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    if (query.isNotEmpty()) {
        IconButton(onClick = {
            onQueryChange("")
        }) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_search)) }
    }
}

@Composable
internal fun CategoryChips(
    state: LibraryUiState,
    actions: LibraryScreenActions,
) {
    val listState = rememberLazyListState()
    // A new suggestion is inserted at the start, which the row would otherwise leave scrolled out of sight.
    LaunchedEffect(state.suggestion) { if (state.suggestion != null) listState.animateScrollToItem(0) }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = ScreenMargin),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.suggestion?.let { suggestion ->
            item(key = "suggestion") {
                AssistChip(
                    onClick = { actions.onApplySuggestion(suggestion) },
                    colors = docAssistChipColors(),
                    border = null,
                    label = { Text(stringResource(R.string.only_category, suggestion.displayName())) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, Modifier.size(18.dp)) },
                )
            }
        }
        item(key = "all") {
            FilterChip(
                selected = state.category == null,
                onClick = { actions.onCategory(null) },
                colors = docFilterChipColors(),
                border = docFilterChipBorder(state.category == null),
                label = { Text(stringResource(R.string.filter_all)) },
            )
        }
        items(DocumentCategory.entries, key = { it.name }) { category ->
            CategoryFilterChip(
                category = category,
                selected = state.category == category,
                onClick = { actions.onCategory(if (state.category == category) null else category) },
            )
        }
    }
}
