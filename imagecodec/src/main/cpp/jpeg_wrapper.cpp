#include "common.h"
#include "turbojpeg.h"

bool decodeJpeg(const uint8_t* data, size_t size, RawImage& out) {
    tjhandle handle = tjInitDecompress();
    if (!handle) return false;

    int width, height, subsamp, colorspace;
    if (tjDecompressHeader3(handle, data, size, &width, &height, &subsamp, &colorspace) < 0) {
        tjDestroy(handle);
        return false;
    }

    out.width = width;
    out.height = height;
    out.rgba.resize(width * height * 4);

    if (tjDecompress2(handle, data, size, out.rgba.data(), width, 0, height,
                      TJPF_RGBA, TJFLAG_FASTDCT) < 0) {
        tjDestroy(handle);
        return false;
    }

    tjDestroy(handle);
    return true;
}

bool encodeJpeg(const RawImage& in, std::vector<uint8_t>& out, int quality) {
    tjhandle handle = tjInitCompress();
    if (!handle) return false;

    unsigned char* buf = nullptr;
    unsigned long size = 0;
    if (tjCompress2(handle, in.rgba.data(), in.width, 0, in.height,
                    TJPF_RGBA, &buf, &size, TJSAMP_420, quality, TJFLAG_FASTDCT) < 0) {
        tjDestroy(handle);
        return false;
    }

    out.assign(buf, buf + size);
    tjFree(buf);
    tjDestroy(handle);
    return true;
}
