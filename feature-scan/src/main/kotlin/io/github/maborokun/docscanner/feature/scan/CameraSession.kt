package io.github.maborokun.docscanner.feature.scan

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.github.maborokun.docscanner.core.common.catching
import io.github.maborokun.docscanner.domain.model.DetectionResult
import io.github.maborokun.docscanner.domain.vision.EdgeDetector
import io.github.maborokun.docscanner.domain.vision.Frame
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Preview, still capture and frame analysis, bound to a lifecycle for as long as [bind] is running.
 * Preview, capture and analysis all use 4:3 so they see the same field of view and the overlay lines up.
 */
class CameraSession(
    private val detector: EdgeDetector,
) {
    private val request = MutableStateFlow<SurfaceRequest?>(null)

    /** Feed this to the viewfinder; null until the camera asks for a surface. */
    val surfaceRequest: StateFlow<SurfaceRequest?> = request.asStateFlow()

    // Analysis results are conflated: a slow consumer sees the newest result, never a queue.
    private val results =
        MutableSharedFlow<DetectionResult>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Raw (unsmoothed) detections, newest only. */
    val detections: Flow<DetectionResult> = results.asSharedFlow()

    private val fourByThree =
        ResolutionSelector
            .Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    private val preview =
        Preview
            .Builder()
            .setResolutionSelector(fourByThree)
            .build()
            .apply { setSurfaceProvider { request.value = it } }
    private val imageCapture =
        ImageCapture
            .Builder()
            .setResolutionSelector(fourByThree)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

    // Frames that arrive while one is being analysed are dropped, never queued (CONTRIBUTING.md architecture rules).
    private val analysis =
        ImageAnalysis
            .Builder()
            .setResolutionSelector(fourByThree)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

    /**
     * Binds the back camera and suspends until cancelled, then unbinds. CameraX itself stops the camera and the
     * analysis while the lifecycle is stopped, so nothing runs in the background.
     */
    suspend fun bind(
        context: Context,
        owner: LifecycleOwner,
    ): Nothing {
        val provider = ProcessCameraProvider.awaitInstance(context.applicationContext)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        analysis.setAnalyzer(analysisExecutor, ::analyze)
        try {
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
            awaitCancellation()
        } finally {
            provider.unbind(preview, imageCapture, analysis)
            analysis.clearAnalyzer()
            analysisExecutor.shutdown()
            request.value = null
        }
    }

    // Runs on the analysis thread; blocking here is what makes CameraX drop the frames that arrive meanwhile.
    private fun analyze(image: ImageProxy) {
        image.use { proxy ->
            val plane = proxy.planes[0]
            val frame = Frame(proxy.width, proxy.height, plane.rowStride, plane.buffer, proxy.imageInfo.rotationDegrees)
            val start = System.nanoTime()
            catching { runBlocking { detector.detect(frame) } }.onSuccess { results.tryEmit(it) }
            logTiming(System.nanoTime() - start)
        }
    }

    private var analysedFrames = 0
    private var totalNanos = 0L

    // Tracked, not shown: average analysis time per frame, logged every 120 frames. Analysis thread only.
    private fun logTiming(nanos: Long) {
        totalNanos += nanos
        if (++analysedFrames % LOG_EVERY_FRAMES == 0) {
            Log.d(
                TAG,
                "frame analysis avg ${totalNanos / analysedFrames / NANOS_PER_MS} ms over $analysedFrames frames",
            )
        }
    }

    /** Takes a photo into [file]; [rotation] is a Surface.ROTATION_* value of the current display. */
    suspend fun capture(
        context: Context,
        file: File,
        rotation: Int,
    ) = suspendCancellableCoroutine { continuation ->
        file.parentFile?.mkdirs()
        imageCapture.targetRotation = rotation
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = continuation.resume(file)

                override fun onError(exception: ImageCaptureException) = continuation.resumeWithException(exception)
            },
        )
    }

    private companion object {
        const val TAG = "CameraSession"
        const val LOG_EVERY_FRAMES = 120
        const val NANOS_PER_MS = 1_000_000L
    }
}
