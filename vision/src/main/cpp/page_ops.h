// Perspective warp and enhancement on RGBA_8888 buffers. Pure C++: no JNI, no globals, safe to call concurrently.
#pragma once

#include <cstdint>

namespace docscanner {

// Maps the destination rectangle onto `quad` (x0,y0 .. x3,y3: top-left, top-right, bottom-right, bottom-left, in
// source pixels) and samples the source bilinearly. Returns false for a degenerate quad or buffer.
bool warpPerspective(const uint8_t* src, int srcWidth, int srcHeight, int srcStride, const float quad[8], uint8_t* dst,
                     int dstWidth, int dstHeight, int dstStride);

// Evens out the lighting and whitens the paper, keeping colour.
void enhanceContrast(uint8_t* px, int width, int height, int stride);

// Pure black text on white, decided against the local paper brightness.
void enhanceBlackWhite(uint8_t* px, int width, int height, int stride);

}  // namespace docscanner
