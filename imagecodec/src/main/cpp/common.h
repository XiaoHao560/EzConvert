#ifndef IMAGE_CODEC_COMMON_H
#define IMAGE_CODEC_COMMON_H

#include <jni.h>
#include <android/log.h>
#include <vector>
#include <cstdint>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "ImageCodec", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "ImageCodec", __VA_ARGS__)

struct RawImage {
    int width = 0;
    int height = 0;
    std::vector<uint8_t> rgba;
};

bool decodeJpeg(const uint8_t* data, size_t size, RawImage& out);
bool encodeJpeg(const RawImage& in, std::vector<uint8_t>& out, int quality);

bool decodePng(const uint8_t* data, size_t size, RawImage& out);
bool encodePng(const RawImage& in, std::vector<uint8_t>& out);

bool decodeWebp(const uint8_t* data, size_t size, RawImage& out);
bool encodeWebp(const RawImage& in, std::vector<uint8_t>& out, int quality);

#endif
