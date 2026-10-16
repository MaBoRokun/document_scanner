package io.github.maborokun.docscanner.data

import android.content.Context
import io.github.maborokun.docscanner.core.common.AppDispatchers
import io.github.maborokun.docscanner.domain.model.Page
import io.github.maborokun.docscanner.domain.processing.ExportProgress
import io.github.maborokun.docscanner.domain.processing.ImageExporter
import kotlinx.coroutines.withContext
import java.io.File

/** Copies a page image to `cacheDir/exports`, the folder the share FileProvider exposes, under a readable name. */
class AndroidImageExporter(
    context: Context,
    private val dispatchers: AppDispatchers,
) : ImageExporter {
    private val directory = File(context.applicationContext.cacheDir, "exports")

    override suspend fun export(
        page: Page,
        title: String,
    ): ExportProgress.Finished =
        withContext(dispatchers.io) {
            directory.mkdirs()
            directory.listFiles()?.forEach { it.delete() } // earlier exports have been shared or abandoned by now
            val fileName = exportFileName(title, "jpg")
            val target = File(directory, fileName)
            File(page.imagePath).copyTo(target, overwrite = true)
            ExportProgress.Finished(target.path, fileName)
        }
}
