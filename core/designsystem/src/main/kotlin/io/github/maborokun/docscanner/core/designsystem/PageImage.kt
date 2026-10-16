package io.github.maborokun.docscanner.core.designsystem

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.github.maborokun.docscanner.core.common.AppDispatchers
import io.github.maborokun.docscanner.core.common.DefaultAppDispatchers
import kotlinx.coroutines.withContext

val LocalAppDispatchers = compositionLocalOf<AppDispatchers> { DefaultAppDispatchers }

/** Decodes [path] downsampled so its longest side is at most about [maxPx]; null while loading or on failure. */
@Composable
fun rememberPageBitmap(
    path: String?,
    maxPx: Int,
): ImageBitmap? {
    val dispatchers = LocalAppDispatchers.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, maxPx) {
        value = path?.let { withContext(dispatchers.io) { decodeSampled(it, maxPx) } }
    }
    return bitmap
}

@Composable
fun PageImage(
    path: String?,
    maxPx: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    colorFilter: ColorFilter? = null,
) {
    val bitmap = rememberPageBitmap(path, maxPx)
    if (bitmap == null) {
        Box(modifier)
    } else {
        Image(
            bitmap,
            contentDescription,
            modifier,
            alignment = alignment,
            contentScale = contentScale,
            colorFilter = colorFilter,
        )
    }
}

private fun decodeSampled(
    path: String,
    maxPx: Int,
): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}
