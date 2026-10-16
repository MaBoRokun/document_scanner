package io.github.maborokun.docscanner.domain.processing

import io.github.maborokun.docscanner.domain.model.FrameSize
import io.github.maborokun.docscanner.domain.model.PageEdit
import io.github.maborokun.docscanner.domain.model.Quad

enum class EnhanceMode {
    /** Keep the colours as photographed. */
    None,

    /** Even out the lighting and whiten the paper, keeping colour. */
    Contrast,

    /** Pure black text on white, like a photocopy. */
    BlackWhite,
}

/** Image work on files in private storage; implemented natively, faked in tests. Output is a JPEG. */
interface PageProcessor {
    /** Flattens the page inside [quad] (normalized to the upright [input]) into [output]; returns its size. */
    suspend fun warp(
        input: String,
        quad: Quad,
        output: String,
    ): FrameSize

    suspend fun enhance(
        input: String,
        mode: EnhanceMode,
        output: String,
    )
}

/** Builds a page's final image from its original photo, crop and edits; implemented natively, faked in tests. */
interface PageRenderer {
    /** Writes the JPEG for [original] cropped to [quad] with [edit] applied to [output]; returns its size. */
    suspend fun render(
        original: String,
        quad: Quad,
        edit: PageEdit,
        output: String,
    ): FrameSize
}

/** On-device text recognition; returns the recognised text in reading order, empty when there is none. */
interface TextRecognizer {
    suspend fun recognize(imagePath: String): String
}
