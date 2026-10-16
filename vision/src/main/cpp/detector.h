// Document edge detection on a luminance frame. Pure C++: no JNI, no globals, so calls are re-entrant.
#pragma once

#include <cstdint>

namespace docscanner {

struct Detection {
    bool found = false;
    // x0,y0 ... x3,y3: normalized 0..1 in the input frame, clockwise on screen starting at the corner nearest the
    // top-left.
    float corners[8] = {};
    float confidence = 0.f;   // 0..1, product of shape, size and contrast checks
    float skewDegrees = 0.f;  // mean edge deviation from the nearest axis, -45..45, positive = clockwise
    float blurScore = 0.f;    // 0..1, saturating Laplacian variance; higher is sharper
};

// luma: 8-bit grayscale, `rowStride` bytes per row. Returns found=false when no page-like region exists.
Detection detectDocument(const uint8_t* luma, int width, int height, int rowStride);

}  // namespace docscanner
