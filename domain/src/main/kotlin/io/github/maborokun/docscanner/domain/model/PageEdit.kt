package io.github.maborokun.docscanner.domain.model

import io.github.maborokun.docscanner.domain.processing.EnhanceMode

/** A colour filter laid over the page; [matrix] is a 4x5 Android-style colour matrix (row-major, offsets in 0..255). */
@Suppress("MagicNumber") // the colour matrices are data tables
enum class PageTint(
    val label: String,
    val matrix: FloatArray,
) {
    None("None", floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)),
    Warm("Warm", floatArrayOf(1.1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0.85f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)),
    Cool("Cool", floatArrayOf(0.88f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1.12f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)),
    Sepia(
        "Sepia",
        floatArrayOf(
            0.393f,
            0.769f,
            0.189f,
            0f,
            0f,
            0.349f,
            0.686f,
            0.168f,
            0f,
            0f,
            0.272f,
            0.534f,
            0.131f,
            0f,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f,
        ),
    ),
    Mono(
        "Mono",
        floatArrayOf(
            0.299f,
            0.587f,
            0.114f,
            0f,
            0f,
            0.299f,
            0.587f,
            0.114f,
            0f,
            0f,
            0.299f,
            0.587f,
            0.114f,
            0f,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f,
        ),
    ),
    Vivid(
        "Vivid",
        floatArrayOf(
            1.35f,
            -0.24f,
            -0.08f,
            0f,
            0f,
            -0.12f,
            1.23f,
            -0.08f,
            0f,
            0f,
            -0.12f,
            -0.24f,
            1.39f,
            0f,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f,
        ),
    ),
}

/** One pen stroke: [width] is a fraction of the page width, [points] are normalized to the page. */
data class Stroke(
    val color: Int,
    val width: Float,
    val points: List<Point>,
)

/**
 * Everything the user changed on a page, applied to the original photo in this order: crop (the page's quad),
 * [mode], then [brightness] and [tint], then the [strokes]. Kept next to the page so it can be adjusted again later.
 */
data class PageEdit(
    val mode: EnhanceMode = EnhanceMode.None,
    /** -1 (darkest) to 1 (brightest); 0 leaves the image alone. */
    val brightness: Float = 0f,
    val tint: PageTint = PageTint.None,
    val strokes: List<Stroke> = emptyList(),
) {
    val hasColorChange: Boolean get() = brightness != 0f || tint != PageTint.None

    /** Tint and brightness as one 4x5 colour matrix; a fresh array every call. */
    fun colorMatrix(): FloatArray {
        val matrix = tint.matrix.copyOf()
        val offset = brightness.coerceIn(-1f, 1f) * MAX_CHANNEL
        for (row in 0 until RGB_ROWS) matrix[row * COLUMNS + OFFSET_COLUMN] += offset
        return matrix
    }

    private companion object {
        const val MAX_CHANNEL = 255f
        const val RGB_ROWS = 3
        const val COLUMNS = 5
        const val OFFSET_COLUMN = 4
    }
}
