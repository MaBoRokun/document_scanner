package io.github.maborokun.docscanner.feature.library

import io.github.maborokun.docscanner.domain.model.DocumentCategory
import io.github.maborokun.docscanner.domain.receipt.FieldsInput

/** Everything the detail screen can ask for; the route wires these to the view model and to navigation. */
class DetailActions(
    val onBack: () -> Unit,
    val onDelete: () -> Unit,
    val onSaveFields: (FieldsInput) -> Unit,
    val onFieldsEdited: () -> Unit,
    val onAddPage: () -> Unit,
    val onExport: () -> Unit,
    val onRemovePage: (Long) -> Unit,
    val onCategory: (DocumentCategory) -> Unit,
    val onComment: (String) -> Unit,
    val onEditPage: (Long) -> Unit,
    /** Shares one page: as an image when the flag is true, otherwise as a one-page PDF. [onExport] shares it all. */
    val onSharePage: (pageId: Long, asImage: Boolean) -> Unit,
)
