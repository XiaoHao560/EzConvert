#include "common.h"
#include "webp/decode.h"
#include "webp/encode.h"

bool decodeWebp(const uint8_t* data, size_t size, RawImage& out) {
    codecLog(LOG_DEBUG, "[WebP Decode] 开始解码");

    int width, height;
    uint8_t* rgba = WebPDecodeRGBA(data, size, &width, &height);
    if (!rgba) {
        codecLog(LOG_ERROR, "[WebP Decode] WebPDecodeRGBA 失败");
        return false;
    }

    out.width = width;
    out.height = height;
    out.rgba.assign(rgba, rgba + width * height * 4);
    WebPFree(rgba);

    codecLog(LOG_DEBUG, "[WebP Decode] 解码完成: %dx%d", width, height);
    return true;
}

bool encodeWebp(const RawImage& in, std::vector<uint8_t>& out, int quality) {
    bool lossless = (quality >= 100);
    codecLog(LOG_DEBUG, "[WebP Encode] 开始编码: %dx%d, %s, quality=%d",
             in.width, in.height, lossless ? "无损" : "有损", quality);

    uint8_t* buf = nullptr;
    size_t size = 0;

    if (lossless) {
        size = WebPEncodeLosslessRGBA(in.rgba.data(), in.width, in.height,
                                      in.width * 4, &buf);
    } else {
        size = WebPEncodeRGBA(in.rgba.data(), in.width, in.height,
                              in.width * 4, static_cast<float>(quality), &buf);
    }

    if (size == 0 || !buf) {
        codecLog(LOG_ERROR, "[WebP Encode] 编码失败");
        return false;
    }

    out.assign(buf, buf + size);
    WebPFree(buf);

    codecLog(LOG_DEBUG, "[WebP Encode] 编码完成: %zu bytes", size);
    return true;
}
