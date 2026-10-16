package io.github.maborokun.docscanner.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import io.github.maborokun.docscanner.core.common.AppDispatchers
import io.github.maborokun.docscanner.domain.model.StoredImage
import io.github.maborokun.docscanner.domain.repository.PageImageStore
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Keeps page images under `filesDir/pages`; the database stores only their absolute paths. */
class AppPrivateImageStore(
    context: Context,
    private val dispatchers: AppDispatchers,
) : PageImageStore {
    private val resolver = context.applicationContext.contentResolver
    private val root = File(context.applicationContext.filesDir, "pages")

    override suspend fun import(source: String): StoredImage =
        withContext(dispatchers.io) {
            root.mkdirs()
            val target = File(root, "${UUID.randomUUID()}.img")
            var stored = false
            try {
                val input = requireNotNull(resolver.openInputStream(Uri.parse(source))) { "Cannot open $source" }
                input.use { src -> target.outputStream().use { dst -> src.copyTo(dst) } }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(target.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not a decodable image" }
                val (width, height) = normalizeOrientation(target, bounds.outWidth, bounds.outHeight)
                stored = true
                StoredImage(target.path, width, height)
            } finally {
                if (!stored) target.delete()
            }
        }

    override suspend fun allocatePath(): String =
        withContext(dispatchers.io) {
            root.mkdirs()
            File(root, "${UUID.randomUUID()}.img").path
        }

    override suspend fun delete(paths: List<String>) {
        withContext(dispatchers.io) {
            // Only ever touch files inside our own directory, whatever the database says.
            paths.map(::File).filter { it.canonicalFile.parentFile == root.canonicalFile }.forEach { it.delete() }
        }
    }

    /**
     * `BitmapFactory` ignores EXIF orientation, so a portrait photo would show sideways. Rewrite the file upright
     * (and drop the tag) so every later stage, including quad coordinates, works on the pixels as displayed.
     */
    private fun normalizeOrientation(
        file: File,
        width: Int,
        height: Int,
    ): Pair<Int, Int> {
        val exif = ExifInterface(file.path)
        val degrees = exif.rotationDegrees
        if (degrees == 0 && !exif.isFlipped) return width to height
        val source = requireNotNull(BitmapFactory.decodeFile(file.path)) { "Not a decodable image" }
        val matrix =
            Matrix().apply {
                postRotate(degrees.toFloat())
                if (exif.isFlipped) postScale(-1f, 1f)
            }
        val upright = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        file.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        return upright.width.also { if (upright !== source) source.recycle() } to upright.height
    }

    private companion object {
        const val JPEG_QUALITY = 95
    }
}
