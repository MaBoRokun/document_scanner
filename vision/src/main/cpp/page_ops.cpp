#include "page_ops.h"

#include <algorithm>
#include <cmath>
#include <vector>

namespace docscanner {
namespace {

constexpr int kGridLongSide = 160;     // background estimate resolution (long side, in cells)
constexpr int kPaperTarget = 250;      // brightness the paper is stretched to
constexpr int kMinBackground = 40;     // darker "backgrounds" are not paper; do not amplify them
constexpr float kInkRatio = 0.80f;     // a pixel darker than this fraction of the paper is ink

inline uint8_t clamp8(float v) { return static_cast<uint8_t>(std::max(0.f, std::min(255.f, v + 0.5f))); }

inline float lumaOf(const uint8_t* p) { return 0.299f * p[0] + 0.587f * p[1] + 0.114f * p[2]; }

// Solves the 8x8 system for a homography (h33 = 1) mapping dst[i] -> src[i]. Returns false when singular.
bool solveHomography(const double dst[8], const double src[8], double h[8]) {
    double m[8][9];
    for (int i = 0; i < 4; ++i) {
        const double x = dst[2 * i];
        const double y = dst[2 * i + 1];
        const double u = src[2 * i];
        const double v = src[2 * i + 1];
        const double r1[9] = {x, y, 1, 0, 0, 0, -u * x, -u * y, u};
        const double r2[9] = {0, 0, 0, x, y, 1, -v * x, -v * y, v};
        std::copy(r1, r1 + 9, m[2 * i]);
        std::copy(r2, r2 + 9, m[2 * i + 1]);
    }
    for (int col = 0; col < 8; ++col) {
        int pivot = col;
        for (int r = col + 1; r < 8; ++r) {
            if (std::fabs(m[r][col]) > std::fabs(m[pivot][col])) pivot = r;
        }
        if (std::fabs(m[pivot][col]) < 1e-12) return false;
        std::swap_ranges(m[col], m[col] + 9, m[pivot]);
        for (int r = 0; r < 8; ++r) {
            if (r == col) continue;
            const double f = m[r][col] / m[col][col];
            for (int c = col; c < 9; ++c) m[r][c] -= f * m[col][c];
        }
    }
    for (int i = 0; i < 8; ++i) h[i] = m[i][8] / m[i][i];
    return true;
}

inline void sampleBilinear(const uint8_t* src, int w, int h, int stride, double u, double v, uint8_t* out) {
    const double fx = std::max(0.0, std::min(static_cast<double>(w - 1), u - 0.5));
    const double fy = std::max(0.0, std::min(static_cast<double>(h - 1), v - 0.5));
    const int x0 = static_cast<int>(fx);
    const int y0 = static_cast<int>(fy);
    const int x1 = std::min(x0 + 1, w - 1);
    const int y1 = std::min(y0 + 1, h - 1);
    const float tx = static_cast<float>(fx - x0);
    const float ty = static_cast<float>(fy - y0);
    const uint8_t* p00 = src + static_cast<size_t>(y0) * stride + x0 * 4;
    const uint8_t* p10 = src + static_cast<size_t>(y0) * stride + x1 * 4;
    const uint8_t* p01 = src + static_cast<size_t>(y1) * stride + x0 * 4;
    const uint8_t* p11 = src + static_cast<size_t>(y1) * stride + x1 * 4;
    for (int c = 0; c < 4; ++c) {
        const float top = p00[c] + (p10[c] - p00[c]) * tx;
        const float bottom = p01[c] + (p11[c] - p01[c]) * tx;
        out[c] = clamp8(top + (bottom - top) * ty);
    }
}

// Low-resolution estimate of the paper brightness: cell means, text removed by a max filter, then smoothed.
struct Background {
    int gw = 0;
    int gh = 0;
    float cell = 1.f;  // image pixels per grid cell
    std::vector<float> grid;

    float at(float x, float y) const {
        const float gx = std::max(0.f, std::min(static_cast<float>(gw - 1), x / cell - 0.5f));
        const float gy = std::max(0.f, std::min(static_cast<float>(gh - 1), y / cell - 0.5f));
        const int x0 = static_cast<int>(gx);
        const int y0 = static_cast<int>(gy);
        const int x1 = std::min(x0 + 1, gw - 1);
        const int y1 = std::min(y0 + 1, gh - 1);
        const float tx = gx - x0;
        const float ty = gy - y0;
        const float top = grid[y0 * gw + x0] + (grid[y0 * gw + x1] - grid[y0 * gw + x0]) * tx;
        const float bottom = grid[y1 * gw + x0] + (grid[y1 * gw + x1] - grid[y1 * gw + x0]) * tx;
        return top + (bottom - top) * ty;
    }
};

std::vector<float> filterGrid(const std::vector<float>& in, int w, int h, int radius, bool useMax) {
    std::vector<float> tmp(in.size());
    std::vector<float> out(in.size());
    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            float acc = 0.f;
            int n = 0;
            for (int dx = -radius; dx <= radius; ++dx) {
                const int xx = std::max(0, std::min(w - 1, x + dx));
                const float v = in[y * w + xx];
                acc = useMax ? std::max(acc, v) : acc + v;
                ++n;
            }
            tmp[y * w + x] = useMax ? acc : acc / n;
        }
    }
    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            float acc = 0.f;
            int n = 0;
            for (int dy = -radius; dy <= radius; ++dy) {
                const int yy = std::max(0, std::min(h - 1, y + dy));
                const float v = tmp[yy * w + x];
                acc = useMax ? std::max(acc, v) : acc + v;
                ++n;
            }
            out[y * w + x] = useMax ? acc : acc / n;
        }
    }
    return out;
}

Background estimateBackground(const uint8_t* px, int width, int height, int stride) {
    Background bg;
    bg.cell = std::max(1.f, static_cast<float>(std::max(width, height)) / kGridLongSide);
    bg.gw = std::max(1, static_cast<int>(std::ceil(width / bg.cell)));
    bg.gh = std::max(1, static_cast<int>(std::ceil(height / bg.cell)));
    std::vector<float> sum(static_cast<size_t>(bg.gw) * bg.gh, 0.f);
    std::vector<int> count(sum.size(), 0);
    for (int y = 0; y < height; ++y) {
        const uint8_t* row = px + static_cast<size_t>(y) * stride;
        const int gy = std::min(bg.gh - 1, static_cast<int>(y / bg.cell));
        for (int x = 0; x < width; ++x) {
            const int gx = std::min(bg.gw - 1, static_cast<int>(x / bg.cell));
            sum[gy * bg.gw + gx] += lumaOf(row + x * 4);
            ++count[gy * bg.gw + gx];
        }
    }
    for (size_t i = 0; i < sum.size(); ++i) sum[i] = count[i] ? sum[i] / count[i] : 0.f;
    bg.grid = filterGrid(filterGrid(sum, bg.gw, bg.gh, 2, true), bg.gw, bg.gh, 3, false);
    return bg;
}

}  // namespace

bool warpPerspective(const uint8_t* src, int srcWidth, int srcHeight, int srcStride, const float quad[8], uint8_t* dst,
                     int dstWidth, int dstHeight, int dstStride) {
    if (!src || !dst || srcWidth < 2 || srcHeight < 2 || dstWidth < 1 || dstHeight < 1) return false;
    const double target[8] = {0, 0, static_cast<double>(dstWidth), 0, static_cast<double>(dstWidth),
                              static_cast<double>(dstHeight), 0, static_cast<double>(dstHeight)};
    double source[8];
    for (int i = 0; i < 8; ++i) source[i] = quad[i];
    double h[8];
    if (!solveHomography(target, source, h)) return false;
    for (int y = 0; y < dstHeight; ++y) {
        uint8_t* row = dst + static_cast<size_t>(y) * dstStride;
        for (int x = 0; x < dstWidth; ++x) {
            const double px = x + 0.5;
            const double py = y + 0.5;
            const double w = h[6] * px + h[7] * py + 1.0;
            if (std::fabs(w) < 1e-9) return false;
            sampleBilinear(src, srcWidth, srcHeight, srcStride, (h[0] * px + h[1] * py + h[2]) / w,
                           (h[3] * px + h[4] * py + h[5]) / w, row + x * 4);
        }
    }
    return true;
}

void enhanceContrast(uint8_t* px, int width, int height, int stride) {
    if (!px || width < 1 || height < 1) return;
    const Background bg = estimateBackground(px, width, height, stride);
    for (int y = 0; y < height; ++y) {
        uint8_t* row = px + static_cast<size_t>(y) * stride;
        for (int x = 0; x < width; ++x) {
            const float paper = std::max(static_cast<float>(kMinBackground), bg.at(x + 0.5f, y + 0.5f));
            const float gain = kPaperTarget / paper;
            for (int c = 0; c < 3; ++c) row[x * 4 + c] = clamp8(row[x * 4 + c] * gain);
        }
    }
}

void enhanceBlackWhite(uint8_t* px, int width, int height, int stride) {
    if (!px || width < 1 || height < 1) return;
    const Background bg = estimateBackground(px, width, height, stride);
    for (int y = 0; y < height; ++y) {
        uint8_t* row = px + static_cast<size_t>(y) * stride;
        for (int x = 0; x < width; ++x) {
            const float paper = std::max(static_cast<float>(kMinBackground), bg.at(x + 0.5f, y + 0.5f));
            const uint8_t v = lumaOf(row + x * 4) < paper * kInkRatio ? 0 : 255;
            row[x * 4] = row[x * 4 + 1] = row[x * 4 + 2] = v;
        }
    }
}

}  // namespace docscanner
