#include "common.h"
#include "turbojpeg.h"

bool decodeJpeg(const uint8_t* data, size_t size, RawImage& out) {
    codecLog(LOG_DEBUG, "[JPEG Decode] 初始化 TurboJPEG 解码器");
    tjhandle handle = tjInitDecompress();
    if (!handle) {
        codecLog(LOG_ERROR, "[JPEG Decode] tjInitDecompress 失败");
        return false;
    }

    int width, height, subsamp, colorspace;
    if (tjDecompressHeader3(handle, data, size, &width, &height, &subsamp, &colorspace) < 0) {
        codecLog(LOG_ERROR, "[JPEG Decode] 解析文件头失败: %s", tjGetErrorStr2(handle));
        tjDestroy(handle);
        return false;
    }

    codecLog(LOG_DEBUG, "[JPEG Decode] 文件头解析成功: %dx%d, subsamp=%d, colorspace=%d",
             width, height, subsamp, colorspace);

    out.width = width;
    out.height = height;
    out.rgba.resize(width * height * 4);

    if (tjDecompress2(handle, data, size, out.rgba.data(), width, 0, height,
                      TJPF_RGBA, TJFLAG_FASTDCT) < 0) {
        codecLog(LOG_ERROR, "[JPEG Decode] 解压失败: %s", tjGetErrorStr2(handle));
        tjDestroy(handle);
        return false;
    }

    codecLog(LOG_DEBUG, "[JPEG Decode] 解压完成");
    tjDestroy(handle);
    return true;
}

bool encodeJpeg(const RawImage& in, std::vector<uint8_t>& out, int quality) {
    codecLog(LOG_DEBUG, "[JPEG Encode] 初始化 TurboJPEG 编码器, 质量=%d", quality);
    tjhandle handle = tjInitCompress();
    if (!handle) {
        codecLog(LOG_ERROR, "[JPEG Encode] tjInitCompress 失败");
        return false;
    }

    unsigned char* buf = nullptr;
    unsigned long size = 0;
    if (tjCompress2(handle, in.rgba.data(), in.width, 0, in.height,
                    TJPF_RGBA, &buf, &size, TJSAMP_420, quality, TJFLAG_FASTDCT) < 0) {
        codecLog(LOG_ERROR, "[JPEG Encode] 压缩失败: %s", tjGetErrorStr2(handle));
        tjDestroy(handle);
        return false;
    }

    out.assign(buf, buf + size);
    tjFree(buf);
    tjDestroy(handle);
    codecLog(LOG_DEBUG, "[JPEG Encode] 压缩完成: %lu bytes", size);
    return true;
}
