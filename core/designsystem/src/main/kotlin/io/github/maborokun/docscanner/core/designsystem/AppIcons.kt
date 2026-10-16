package io.github.maborokun.docscanner.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private const val ICON_SIZE = 24f
private const val STROKE = 2f

private fun outlineIcon(
    name: String,
    block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
): ImageVector =
    ImageVector
        .Builder(name, 24.dp, 24.dp, ICON_SIZE, ICON_SIZE)
        .path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        ).build()

/** App icons that Material's core set lacks, drawn as 2 dp outlines like the rest of the set. */
object AppIcons {
    /** Four page corners around a scan line: the scan action. */
    val Scan: ImageVector =
        outlineIcon("Scan") {
            moveTo(4f, 9f)
            verticalLineTo(6f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            horizontalLineTo(9f)
            moveTo(15f, 4f)
            horizontalLineTo(18f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineTo(9f)
            moveTo(20f, 15f)
            verticalLineTo(18f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(15f)
            moveTo(9f, 20f)
            horizontalLineTo(6f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(15f)
            moveTo(7.5f, 12f)
            horizontalLineTo(16.5f)
        }

    /** A picture frame with a hill and a sun: import an image. */
    val Image: ImageVector =
        outlineIcon("Image") {
            moveTo(6f, 4f)
            horizontalLineTo(18f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
            verticalLineTo(18f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(6f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(6f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            moveTo(4f, 17f)
            lineTo(9f, 12f)
            lineTo(13f, 16f)
            lineTo(15f, 14f)
            lineTo(20f, 19f)
            moveTo(15.5f, 8.5f)
            horizontalLineTo(15.6f)
        }

    /** A page with a folded corner and two text lines: a document. */
    val Document: ImageVector =
        outlineIcon("Document") {
            moveTo(14f, 3f)
            horizontalLineTo(7f)
            arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
            verticalLineTo(19f)
            arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
            horizontalLineTo(17f)
            arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
            verticalLineTo(8f)
            lineTo(14f, 3f)
            verticalLineTo(8f)
            horizontalLineTo(19f)
            moveTo(9f, 13f)
            horizontalLineTo(15f)
            moveTo(9f, 17f)
            horizontalLineTo(13f)
        }

    /** An arrow leaving a tray: export and share. */
    val Export: ImageVector =
        outlineIcon("Export") {
            moveTo(12f, 15f)
            verticalLineTo(4f)
            moveTo(8f, 8f)
            lineTo(12f, 4f)
            lineTo(16f, 8f)
            moveTo(5f, 13f)
            verticalLineTo(18f)
            arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
            horizontalLineTo(17f)
            arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
            verticalLineTo(13f)
        }

    /** An arrow curling back to the left: undo. */
    val Undo: ImageVector =
        outlineIcon("Undo") {
            moveTo(9f, 5f)
            lineTo(4f, 10f)
            lineTo(9f, 15f)
            moveTo(4f, 10f)
            horizontalLineTo(15f)
            arcToRelative(5f, 5f, 0f, false, true, 0f, 10f)
            horizontalLineTo(8f)
        }

    /** Two crossing crop marks: crop and straighten. */
    val Crop: ImageVector =
        outlineIcon("Crop") {
            moveTo(6f, 2f)
            verticalLineTo(18f)
            horizontalLineTo(22f)
            moveTo(2f, 6f)
            horizontalLineTo(18f)
            verticalLineTo(22f)
        }

    /** A sun: brightness. */
    val Brightness: ImageVector =
        outlineIcon("Brightness") {
            moveTo(8f, 12f)
            arcToRelative(4f, 4f, 0f, true, true, 8f, 0f)
            arcToRelative(4f, 4f, 0f, true, true, -8f, 0f)
            moveTo(12f, 2f)
            verticalLineTo(4f)
            moveTo(12f, 20f)
            verticalLineTo(22f)
            moveTo(2f, 12f)
            horizontalLineTo(4f)
            moveTo(20f, 12f)
            horizontalLineTo(22f)
        }

    /** Two overlapping discs: colour filters. */
    val Filters: ImageVector =
        outlineIcon("Filters") {
            moveTo(4f, 12f)
            arcToRelative(5f, 5f, 0f, true, true, 10f, 0f)
            arcToRelative(5f, 5f, 0f, true, true, -10f, 0f)
            moveTo(10f, 12f)
            arcToRelative(5f, 5f, 0f, true, true, 10f, 0f)
            arcToRelative(5f, 5f, 0f, true, true, -10f, 0f)
        }

    /** A pencil: draw on the page. */
    val Pen: ImageVector =
        outlineIcon("Pen") {
            moveTo(4f, 20f)
            lineTo(5f, 15f)
            lineTo(16f, 4f)
            lineTo(20f, 8f)
            lineTo(9f, 19f)
            close()
            moveTo(14f, 6f)
            lineTo(18f, 10f)
        }

    /** Two stacked sheets: copy. */
    val Copy: ImageVector =
        outlineIcon("Copy") {
            moveTo(9f, 9f)
            horizontalLineTo(19f)
            verticalLineTo(19f)
            horizontalLineTo(9f)
            close()
            moveTo(5f, 15f)
            verticalLineTo(5f)
            horizontalLineTo(15f)
        }

    /** An up and a down chevron: a value that opens a menu of choices. */
    val UnfoldMore: ImageVector =
        outlineIcon("UnfoldMore") {
            moveTo(8f, 9f)
            lineTo(12f, 5f)
            lineTo(16f, 9f)
            moveTo(8f, 15f)
            lineTo(12f, 19f)
            lineTo(16f, 15f)
        }
}
