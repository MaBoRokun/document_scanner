// Pipeline: downscale -> blur -> Otsu threshold -> largest connected region -> convex hull of its boundary ->
// reduce the hull to four sides -> fit a line per side -> intersect neighbouring lines -> score.
//
// shortcut: one global Otsu threshold instead of an adaptive one, so a strong lighting gradient across the page
// can split it; switch to a local threshold if real-world frames show that.
#include "detector.h"

#include <algorithm>
#include <cmath>
#include <utility>
#include <vector>

namespace docscanner {
namespace {

constexpr int kMaxSide = 256;           // analysis resolution: the long side is reduced to about this many pixels
constexpr int kMinSide = 16;            // smaller than this there is nothing to detect
constexpr float kBlurSaturation = 400.f;  // Laplacian variance at which blurScore reaches 1 (calibrated on fixtures)
constexpr float kMinConfidence = 0.25f;   // below this a candidate is treated as "no document"
constexpr float kPi = 3.14159265358979f;

struct Pt {
    float x;
    float y;
};

struct Gray {
    int w = 0;
    int h = 0;
    int factor = 1;  // original pixels per analysis pixel
    std::vector<uint8_t> px;
    uint8_t at(int x, int y) const { return px[static_cast<size_t>(y) * w + x]; }
};

Gray downscale(const uint8_t* src, int width, int height, int stride) {
    Gray out;
    out.factor = std::max(1, std::max(width, height) / kMaxSide);
    out.w = width / out.factor;
    out.h = height / out.factor;
    out.px.resize(static_cast<size_t>(out.w) * out.h);
    const int f = out.factor;
    for (int y = 0; y < out.h; ++y) {
        for (int x = 0; x < out.w; ++x) {
            int sum = 0;
            for (int dy = 0; dy < f; ++dy) {
                const uint8_t* row = src + static_cast<size_t>(y * f + dy) * stride + x * f;
                for (int dx = 0; dx < f; ++dx) sum += row[dx];
            }
            out.px[static_cast<size_t>(y) * out.w + x] = static_cast<uint8_t>(sum / (f * f));
        }
    }
    return out;
}

// [1 2 1] / 4 in both directions, edges clamped.
Gray blur(const Gray& in) {
    Gray tmp = in;
    Gray out = in;
    for (int y = 0; y < in.h; ++y) {
        for (int x = 0; x < in.w; ++x) {
            const int l = in.at(std::max(x - 1, 0), y);
            const int r = in.at(std::min(x + 1, in.w - 1), y);
            tmp.px[static_cast<size_t>(y) * in.w + x] = static_cast<uint8_t>((l + 2 * in.at(x, y) + r) / 4);
        }
    }
    for (int y = 0; y < in.h; ++y) {
        for (int x = 0; x < in.w; ++x) {
            const int u = tmp.at(x, std::max(y - 1, 0));
            const int d = tmp.at(x, std::min(y + 1, in.h - 1));
            out.px[static_cast<size_t>(y) * in.w + x] = static_cast<uint8_t>((u + 2 * tmp.at(x, y) + d) / 4);
        }
    }
    return out;
}

float blurScore(const Gray& g) {
    double sum = 0;
    double sumSq = 0;
    long n = 0;
    for (int y = 1; y < g.h - 1; ++y) {
        for (int x = 1; x < g.w - 1; ++x) {
            const double lap = 4.0 * g.at(x, y) - g.at(x - 1, y) - g.at(x + 1, y) - g.at(x, y - 1) - g.at(x, y + 1);
            sum += lap;
            sumSq += lap * lap;
            ++n;
        }
    }
    if (n == 0) return 0.f;
    const double mean = sum / n;
    const double variance = sumSq / n - mean * mean;
    return static_cast<float>(std::min(1.0, variance / kBlurSaturation));
}

int otsuThreshold(const Gray& g) {
    long hist[256] = {};
    for (uint8_t v : g.px) ++hist[v];
    const long total = static_cast<long>(g.px.size());
    double sumAll = 0;
    for (int i = 0; i < 256; ++i) sumAll += static_cast<double>(i) * hist[i];
    double sumB = 0;
    long weightB = 0;
    double best = -1;
    int threshold = 128;
    for (int t = 0; t < 256; ++t) {
        weightB += hist[t];
        if (weightB == 0) continue;
        const long weightF = total - weightB;
        if (weightF == 0) break;
        sumB += static_cast<double>(t) * hist[t];
        const double meanB = sumB / weightB;
        const double meanF = (sumAll - sumB) / weightF;
        const double between = static_cast<double>(weightB) * weightF * (meanB - meanF) * (meanB - meanF);
        if (between > best) {
            best = between;
            threshold = t;
        }
    }
    return threshold;
}

// Labels 4-connected regions of `mask` and returns the id (1-based) of the largest, or 0 when the mask is empty.
int largestRegion(const std::vector<uint8_t>& mask, int w, int h, std::vector<int>& labels, int& areaOut) {
    labels.assign(mask.size(), 0);
    std::vector<int> stack;
    int next = 0;
    int bestId = 0;
    int bestArea = 0;
    for (int start = 0; start < w * h; ++start) {
        if (!mask[start] || labels[start]) continue;
        ++next;
        int area = 0;
        stack.push_back(start);
        labels[start] = next;
        while (!stack.empty()) {
            const int p = stack.back();
            stack.pop_back();
            ++area;
            const int x = p % w;
            const int y = p / w;
            const int neighbours[4] = {x > 0 ? p - 1 : -1, x < w - 1 ? p + 1 : -1, y > 0 ? p - w : -1,
                                       y < h - 1 ? p + w : -1};
            for (int q : neighbours) {
                if (q >= 0 && mask[q] && !labels[q]) {
                    labels[q] = next;
                    stack.push_back(q);
                }
            }
        }
        if (area > bestArea) {
            bestArea = area;
            bestId = next;
        }
    }
    areaOut = bestArea;
    return bestId;
}

float cross(const Pt& o, const Pt& a, const Pt& b) { return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x); }

// Andrew's monotone chain; the result is in counter-clockwise order (y up), i.e. clockwise on screen.
std::vector<Pt> convexHull(std::vector<Pt> pts) {
    std::sort(pts.begin(), pts.end(), [](const Pt& a, const Pt& b) { return a.x < b.x || (a.x == b.x && a.y < b.y); });
    pts.erase(std::unique(pts.begin(), pts.end(),
                          [](const Pt& a, const Pt& b) { return a.x == b.x && a.y == b.y; }),
              pts.end());
    if (pts.size() < 3) return pts;
    std::vector<Pt> hull(2 * pts.size());
    size_t k = 0;
    for (size_t i = 0; i < pts.size(); ++i) {
        while (k >= 2 && cross(hull[k - 2], hull[k - 1], pts[i]) <= 0) --k;
        hull[k++] = pts[i];
    }
    for (size_t i = pts.size() - 1, t = k + 1; i > 0; --i) {
        while (k >= t && cross(hull[k - 2], hull[k - 1], pts[i - 1]) <= 0) --k;
        hull[k++] = pts[i - 1];
    }
    hull.resize(k - 1);
    return hull;
}

float polygonArea(const std::vector<Pt>& p) {
    double sum = 0;
    for (size_t i = 0; i < p.size(); ++i) {
        const Pt& a = p[i];
        const Pt& b = p[(i + 1) % p.size()];
        sum += static_cast<double>(a.x) * b.y - static_cast<double>(b.x) * a.y;
    }
    return static_cast<float>(std::fabs(sum) / 2.0);
}

// Repeatedly drops the hull vertex whose removal loses the least area until four remain.
std::vector<size_t> reduceToFour(const std::vector<Pt>& hull) {
    std::vector<size_t> keep(hull.size());
    for (size_t i = 0; i < keep.size(); ++i) keep[i] = i;
    while (keep.size() > 4) {
        size_t worst = 0;
        float least = 1e30f;
        for (size_t i = 0; i < keep.size(); ++i) {
            const Pt& prev = hull[keep[(i + keep.size() - 1) % keep.size()]];
            const Pt& next = hull[keep[(i + 1) % keep.size()]];
            const float tri = std::fabs(cross(prev, hull[keep[i]], next)) / 2.f;
            if (tri < least) {
                least = tri;
                worst = i;
            }
        }
        keep.erase(keep.begin() + static_cast<long>(worst));
    }
    return keep;
}

struct Line {
    Pt p;   // a point on the line
    Pt d;   // unit direction
    bool ok;
};

// Total least squares through `pts`.
Line fitLine(const std::vector<Pt>& pts) {
    Line line{{0, 0}, {1, 0}, false};
    if (pts.size() < 2) return line;
    double cx = 0;
    double cy = 0;
    for (const Pt& p : pts) {
        cx += p.x;
        cy += p.y;
    }
    cx /= pts.size();
    cy /= pts.size();
    double sxx = 0;
    double syy = 0;
    double sxy = 0;
    for (const Pt& p : pts) {
        sxx += (p.x - cx) * (p.x - cx);
        syy += (p.y - cy) * (p.y - cy);
        sxy += (p.x - cx) * (p.y - cy);
    }
    const double angle = 0.5 * std::atan2(2 * sxy, sxx - syy);
    line.p = {static_cast<float>(cx), static_cast<float>(cy)};
    line.d = {static_cast<float>(std::cos(angle)), static_cast<float>(std::sin(angle))};
    line.ok = true;
    return line;
}

bool intersect(const Line& a, const Line& b, Pt& out) {
    const float denom = a.d.x * b.d.y - a.d.y * b.d.x;
    if (!a.ok || !b.ok || std::fabs(denom) < 0.2f) return false;  // nearly parallel sides: not a corner
    const float t = ((b.p.x - a.p.x) * b.d.y - (b.p.y - a.p.y) * b.d.x) / denom;
    out = {a.p.x + t * a.d.x, a.p.y + t * a.d.y};
    return true;
}

float clamp01(float v) { return std::max(0.f, std::min(1.f, v)); }

struct Candidate {
    bool ok = false;
    Pt corners[4] = {};  // analysis-pixel coordinates, clockwise on screen from the top-left-most
    float confidence = 0.f;
};

float interiorAngleDegrees(const Pt& prev, const Pt& at, const Pt& next) {
    const float ax = prev.x - at.x;
    const float ay = prev.y - at.y;
    const float bx = next.x - at.x;
    const float by = next.y - at.y;
    const float denom = std::sqrt(ax * ax + ay * ay) * std::sqrt(bx * bx + by * by);
    if (denom <= 0.f) return 0.f;
    return std::acos(std::max(-1.f, std::min(1.f, (ax * bx + ay * by) / denom))) * 180.f / kPi;
}

Candidate analyse(const Gray& g, int threshold, bool brightForeground) {
    Candidate result;
    const int w = g.w;
    const int h = g.h;
    std::vector<uint8_t> mask(g.px.size());
    for (size_t i = 0; i < mask.size(); ++i) mask[i] = (g.px[i] > threshold) == brightForeground ? 1 : 0;

    std::vector<int> labels;
    int area = 0;
    const int id = largestRegion(mask, w, h, labels, area);
    if (id == 0 || area < w * h / 50) return result;  // under 2% of the frame is noise

    // Boundary pixels of the region.
    std::vector<Pt> boundary;
    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            if (labels[static_cast<size_t>(y) * w + x] != id) continue;
            const bool edge = x == 0 || y == 0 || x == w - 1 || y == h - 1 ||
                              labels[static_cast<size_t>(y) * w + x - 1] != id ||
                              labels[static_cast<size_t>(y) * w + x + 1] != id ||
                              labels[static_cast<size_t>(y - 1) * w + x] != id ||
                              labels[static_cast<size_t>(y + 1) * w + x] != id;
            if (edge) boundary.push_back({static_cast<float>(x) + 0.5f, static_cast<float>(y) + 0.5f});
        }
    }
    const std::vector<Pt> hull = convexHull(boundary);
    if (hull.size() < 4) return result;
    const float hullArea = polygonArea(hull);

    // One line per side, fitted to the hull points between two kept vertices. Points near the ends are skipped
    // because rounded corners pull the fit.
    const std::vector<size_t> keep = reduceToFour(hull);
    Line sides[4];
    for (int s = 0; s < 4; ++s) {
        const size_t from = keep[s];
        const size_t to = keep[(s + 1) % 4];
        std::vector<Pt> pts;
        for (size_t i = from;; i = (i + 1) % hull.size()) {
            pts.push_back(hull[i]);
            if (i == to) break;
        }
        if (pts.size() > 6) {
            const size_t trim = pts.size() / 7;
            pts = std::vector<Pt>(pts.begin() + static_cast<long>(trim), pts.end() - static_cast<long>(trim));
        }
        sides[s] = fitLine(pts);
    }
    Pt corners[4];
    for (int c = 0; c < 4; ++c) {
        Pt p;
        const Pt vertex = hull[keep[c]];
        if (!intersect(sides[(c + 3) % 4], sides[c], p) || std::fabs(p.x - vertex.x) > w / 8.f ||
            std::fabs(p.y - vertex.y) > h / 8.f) {
            p = vertex;
        }
        corners[c] = {std::max(0.f, std::min(static_cast<float>(w), p.x)),
                      std::max(0.f, std::min(static_cast<float>(h), p.y))};
    }

    // Order: clockwise on screen. The hull is counter-clockwise with y up, which is clockwise with y down, so the
    // kept order is already right; only the start vertex needs choosing.
    int first = 0;
    for (int i = 1; i < 4; ++i) {
        if (corners[i].x + corners[i].y < corners[first].x + corners[first].y) first = i;
    }
    for (int i = 0; i < 4; ++i) result.corners[i] = corners[(first + i) % 4];

    const std::vector<Pt> quad(result.corners, result.corners + 4);
    const float quadArea = polygonArea(quad);
    if (quadArea < 1.f) return result;

    // Scores, each 0..1.
    const float quadLike = std::min(hullArea, quadArea) / std::max(hullArea, quadArea);
    const float areaFraction = quadArea / static_cast<float>(w * h);
    float areaScore = clamp01((areaFraction - 0.05f) / 0.2f);
    if (areaFraction > 0.97f) areaScore *= 0.3f;  // the whole frame is not a page
    float maxDeviation = 0.f;
    for (int i = 0; i < 4; ++i) {
        const float angle = interiorAngleDegrees(quad[(i + 3) % 4], quad[i], quad[(i + 1) % 4]);
        maxDeviation = std::max(maxDeviation, std::fabs(angle - 90.f));
    }
    const float angleScore = clamp01(1.f - (maxDeviation - 15.f) / 45.f);
    double sumIn = 0;
    double sumOut = 0;
    long nIn = 0;
    long nOut = 0;
    for (size_t i = 0; i < labels.size(); ++i) {
        if (labels[i] == id) {
            sumIn += g.px[i];
            ++nIn;
        } else {
            sumOut += g.px[i];
            ++nOut;
        }
    }
    const float contrast = (nIn && nOut) ? static_cast<float>(std::fabs(sumIn / nIn - sumOut / nOut) / 255.0) : 0.f;
    const float contrastScore = clamp01(contrast / 0.15f);

    result.confidence = std::pow(quadLike * quadLike * areaScore * angleScore * contrastScore, 0.25f);
    result.ok = true;
    return result;
}

float skewOf(const Pt (&c)[4]) {
    float sum = 0.f;
    for (int i = 0; i < 4; ++i) {
        const Pt& a = c[i];
        const Pt& b = c[(i + 1) % 4];
        float angle = std::atan2(b.y - a.y, b.x - a.x) * 180.f / kPi;
        angle -= 90.f * std::round(angle / 90.f);  // fold to -45..45
        sum += angle;
    }
    return sum / 4.f;
}

}  // namespace

Detection detectDocument(const uint8_t* luma, int width, int height, int rowStride) {
    Detection out;
    if (luma == nullptr || width < kMinSide || height < kMinSide || rowStride < width) return out;

    const Gray small = downscale(luma, width, height, rowStride);
    if (small.w < kMinSide || small.h < kMinSide) return out;
    out.blurScore = blurScore(small);
    const Gray smooth = blur(small);
    const int threshold = otsuThreshold(smooth);

    // Papers are usually brighter than the desk, but try the opposite polarity too and keep the better quad.
    const Candidate bright = analyse(smooth, threshold, true);
    const Candidate dark = analyse(smooth, threshold, false);
    const Candidate& best = (bright.ok && (!dark.ok || bright.confidence >= dark.confidence)) ? bright : dark;
    if (!best.ok || best.confidence < kMinConfidence) return out;

    out.found = true;
    out.confidence = best.confidence;
    out.skewDegrees = skewOf(best.corners);
    for (int i = 0; i < 4; ++i) {
        out.corners[2 * i] = best.corners[i].x * small.factor / static_cast<float>(width);
        out.corners[2 * i + 1] = best.corners[i].y * small.factor / static_cast<float>(height);
    }
    return out;
}

}  // namespace docscanner
