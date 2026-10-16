package io.github.maborokun.docscanner.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.maborokun.docscanner.domain.model.DocumentCategory

// One tint per document type, the same on every screen. The fills are light in both themes, so the label is a fixed
// dark ink; the accent is the dot and the outline of a selected chip.

private val Ink = Color(0xFF1F2933)

// White on a white card would vanish, so the A4 page gets a thin grey outline.
private val A4Outline = Color(0xFFB9BFC7)

private val DocumentCategory.restingBorder: Color
    get() = if (this == DocumentCategory.A4Page) A4Outline else Color.Transparent

/** Background of the category's chips and tags. */
val DocumentCategory.containerColor: Color
    get() =
        when (this) {
            DocumentCategory.Passport -> Color(0xFFE3EDFB)
            DocumentCategory.BookPage -> Color(0xFFE2F1E8)
            DocumentCategory.Receipt -> Color(0xFFF6ECD2)
            DocumentCategory.Document -> Color(0xFFE4E8ED)
            DocumentCategory.A4Page -> Color(0xFFFFFFFF)
            DocumentCategory.Other -> Color(0xFFB9BFC7)
        }

/** The stronger colour of the category: its dot and the outline of a selected chip. */
val DocumentCategory.accentColor: Color
    get() =
        when (this) {
            DocumentCategory.Passport -> Color(0xFF3F6FB5)
            DocumentCategory.BookPage -> Color(0xFF4A8A63)
            DocumentCategory.Receipt -> Color(0xFFA87B2E)
            DocumentCategory.Document -> Color(0xFF5F6B7A)
            DocumentCategory.A4Page -> Color(0xFF4A5361)
            DocumentCategory.Other -> Color(0xFF6E757E)
        }

@Composable
private fun CategoryDot(category: DocumentCategory) {
    Box(Modifier.size(10.dp).background(category.accentColor, CircleShape))
}

/** A filter chip in the category's colour; selecting it adds an outline in the accent colour. */
@Composable
fun CategoryFilterChip(
    category: DocumentCategory,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = category.containerColor,
                labelColor = Ink,
                selectedContainerColor = category.containerColor,
                selectedLabelColor = Ink,
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = category.restingBorder,
                selectedBorderColor = category.accentColor,
                selectedBorderWidth = 2.dp,
            ),
        label = { Text(category.displayName()) },
        leadingIcon = { CategoryDot(category) },
    )
}

/** The category as a small read-only pill, e.g. on a list row. */
@Composable
fun CategoryTag(
    category: DocumentCategory,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier,
        shape = CircleShape,
        color = category.containerColor,
        contentColor = Ink,
        border = BorderStroke(1.dp, category.restingBorder),
    ) {
        Text(
            category.displayName(),
            Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

/** The category's name in the language of the app. */
@get:StringRes
val DocumentCategory.labelRes: Int
    get() =
        when (this) {
            DocumentCategory.Receipt -> R.string.category_receipt
            DocumentCategory.Passport -> R.string.category_passport
            DocumentCategory.BookPage -> R.string.category_book_page
            DocumentCategory.A4Page -> R.string.category_a4_page
            DocumentCategory.Document -> R.string.category_document
            DocumentCategory.Other -> R.string.category_other
        }

@Composable
fun DocumentCategory.displayName(): String = stringResource(labelRes)
