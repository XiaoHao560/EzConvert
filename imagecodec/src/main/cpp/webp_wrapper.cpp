#include "common.h"
#include "webp/decode.h"
#include "webp/encode.h"

bool decodeWebp(const uint8_t* data, size_t size, RawImage& out) {
    int width, height;
    uint8_t* rgba = WebPDecodeRGBA(data, size, &width, &height);
    if (!rgba) return false;

    out.width = width;
    out.height = height;
    out.rgba.assign(rgba, rgba + width * height * 4);
    WebPFree(rgba);
    return true;
}

bool encodeWebp(const RawImage& in, std::vector<uint8_t>& out, int quality) {
    uint8_t* buf = nullptr;
    size_t size = 0;
    if (quality >= 100) {
        size = WebPEncodeLosslessRGBA(in.rgba.data(), in.width, in.height,
                                      in.width * 4, &buf);
    } else {
        size = WebPEncodeRGBA(in.rgba.data(), in.width, in.height,
                              in.width * 4, static_cast<float>(quality), &buf);
    }
    if (size == 0 || !buf) return false;

    out.assign(buf, buf + size);
    WebPFree(buf);
    return true;
}
