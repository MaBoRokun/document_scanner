package io.github.maborokun.docscanner.domain

import io.github.maborokun.docscanner.domain.model.DetectionResult
import io.github.maborokun.docscanner.domain.model.FrameSize
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.vision.EdgeDetector
import io.github.maborokun.docscanner.domain.vision.Frame

/** Returns a canned result for every frame; swap [result] mid-test to simulate a moving page. */
class FakeEdgeDetector(
    var result: DetectionResult =
        DetectionResult(Quad.FULL, confidence = 1f, skewDegrees = 0f, blurScore = 1f, frameSize = FrameSize(1, 1)),
) : EdgeDetector {
    var calls = 0
        private set

    override suspend fun detect(frame: Frame): DetectionResult {
        calls++
        return result
    }
}
