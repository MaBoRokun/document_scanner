#include <android/bitmap.h>
#include <jni.h>

#include "detector.h"
#include "page_ops.h"

namespace {
constexpr jsize kResultSize = 11;  // 8 corner coordinates, confidence, skewDegrees, blurScore
}

extern "C" JNIEXPORT jboolean JNICALL Java_io_github_maborokun_docscanner_vision_NativeBridge_nativeDetect(
    JNIEnv* env, jobject /* this */, jobject luma, jint width, jint height, jint rowStride, jfloatArray out) {
    if (luma == nullptr || out == nullptr || env->GetArrayLength(out) < kResultSize) return JNI_FALSE;
    const auto* base = static_cast<const uint8_t*>(env->GetDirectBufferAddress(luma));
    const jlong capacity = env->GetDirectBufferCapacity(luma);
    if (base == nullptr || width <= 0 || height <= 0 || rowStride < width) return JNI_FALSE;
    if (capacity < static_cast<jlong>(rowStride) * (height - 1) + width) return JNI_FALSE;

    const docscanner::Detection d = docscanner::detectDocument(base, width, height, rowStride);
    float values[kResultSize];
    for (int i = 0; i < 8; ++i) values[i] = d.corners[i];
    values[8] = d.confidence;
    values[9] = d.skewDegrees;
    values[10] = d.blurScore;
    env->SetFloatArrayRegion(out, 0, kResultSize, values);
    return d.found ? JNI_TRUE : JNI_FALSE;
}

namespace {

// Locks an RGBA_8888 bitmap for the lifetime of the object.
class LockedBitmap {
public:
    LockedBitmap(JNIEnv* env, jobject bitmap) : env_(env), bitmap_(bitmap) {
        AndroidBitmapInfo info;
        if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return;
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return;
        void* pixels = nullptr;
        if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return;
        pixels_ = static_cast<uint8_t*>(pixels);
        width_ = static_cast<int>(info.width);
        height_ = static_cast<int>(info.height);
        stride_ = static_cast<int>(info.stride);
    }
    ~LockedBitmap() {
        if (pixels_ != nullptr) AndroidBitmap_unlockPixels(env_, bitmap_);
    }
    LockedBitmap(const LockedBitmap&) = delete;
    LockedBitmap& operator=(const LockedBitmap&) = delete;

    bool ok() const { return pixels_ != nullptr; }
    uint8_t* pixels() const { return pixels_; }
    int width() const { return width_; }
    int height() const { return height_; }
    int stride() const { return stride_; }

private:
    JNIEnv* env_;
    jobject bitmap_;
    uint8_t* pixels_ = nullptr;
    int width_ = 0;
    int height_ = 0;
    int stride_ = 0;
};

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL Java_io_github_maborokun_docscanner_vision_NativePageBridge_nativeWarp(
    JNIEnv* env, jobject /* this */, jobject src, jfloatArray quad, jobject dst) {
    if (src == nullptr || dst == nullptr || quad == nullptr || env->GetArrayLength(quad) < 8) return JNI_FALSE;
    float corners[8];
    env->GetFloatArrayRegion(quad, 0, 8, corners);
    LockedBitmap from(env, src);
    LockedBitmap to(env, dst);
    if (!from.ok() || !to.ok()) return JNI_FALSE;
    return docscanner::warpPerspective(from.pixels(), from.width(), from.height(), from.stride(), corners, to.pixels(),
                                       to.width(), to.height(), to.stride())
               ? JNI_TRUE
               : JNI_FALSE;
}

// mode: 1 = contrast, 2 = black and white.
extern "C" JNIEXPORT jboolean JNICALL Java_io_github_maborokun_docscanner_vision_NativePageBridge_nativeEnhance(
    JNIEnv* env, jobject /* this */, jobject bitmap, jint mode) {
    if (bitmap == nullptr) return JNI_FALSE;
    LockedBitmap image(env, bitmap);
    if (!image.ok()) return JNI_FALSE;
    if (mode == 1) {
        docscanner::enhanceContrast(image.pixels(), image.width(), image.height(), image.stride());
    } else if (mode == 2) {
        docscanner::enhanceBlackWhite(image.pixels(), image.width(), image.height(), image.stride());
    } else {
        return JNI_FALSE;
    }
    return JNI_TRUE;
}
