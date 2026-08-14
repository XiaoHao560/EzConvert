#include "common.h"
#include "png.h"
#include <cstring>

static void pngReadCallback(png_structp png, png_bytep data, png_size_t length) {
    auto* src = static_cast<std::pair<const uint8_t*, size_t>*>(png_get_io_ptr(png));
    memcpy(data, src->first, length);
    src->first += length;
    src->second -= length;
}

static void pngWriteCallback(png_structp png, png_bytep data, png_size_t length) {
    auto* dst = static_cast<std::vector<uint8_t>*>(png_get_io_ptr(png));
    dst->insert(dst->end(), data, data + length);
}

static void pngFlushCallback(png_structp) {}

bool decodePng(const uint8_t* data, size_t size, RawImage& out) {
    codecLog(LOG_DEBUG, "[PNG Decode] 开始解码");

    png_structp png = png_create_read_struct(PNG_LIBPNG_VER_STRING, nullptr, nullptr, nullptr);
    if (!png) {
        codecLog(LOG_ERROR, "[PNG Decode] png_create_read_struct 失败");
        return false;
    }
    png_infop info = png_create_info_struct(png);
    if (!info) {
        codecLog(LOG_ERROR, "[PNG Decode] png_create_info_struct 失败");
        png_destroy_read_struct(&png, nullptr, nullptr);
        return false;
    }

    auto src = std::make_pair(data, size);
    png_set_read_fn(png, &src, pngReadCallback);
    png_read_info(png, info);

    int width = png_get_image_width(png, info);
    int height = png_get_image_height(png, info);
    png_byte colorType = png_get_color_type(png, info);
    png_byte bitDepth = png_get_bit_depth(png, info);

    codecLog(LOG_DEBUG, "[PNG Decode] 图像信息: %dx%d, colorType=%d, bitDepth=%d",
             width, height, colorType, bitDepth);

    if (bitDepth == 16) png_set_strip_16(png);
    if (colorType == PNG_COLOR_TYPE_PALETTE) png_set_palette_to_rgb(png);
    if (colorType == PNG_COLOR_TYPE_GRAY || colorType == PNG_COLOR_TYPE_GRAY_ALPHA)
        png_set_gray_to_rgb(png);
    if (png_get_valid(png, info, PNG_INFO_tRNS)) png_set_tRNS_to_alpha(png);
    if (colorType == PNG_COLOR_TYPE_RGB) png_set_filler(png, 0xFF, PNG_FILLER_AFTER);

    png_read_update_info(png, info);

    out.width = width;
    out.height = height;
    out.rgba.resize(width * height * 4);
    std::vector<png_bytep> rowPointers(height);
    for (int y = 0; y < height; ++y) {
        rowPointers[y] = &out.rgba[y * width * 4];
    }

    png_read_image(png, rowPointers.data());
    png_destroy_read_struct(&png, &info, nullptr);

    codecLog(LOG_DEBUG, "[PNG Decode] 解码完成");
    return true;
}

bool encodePng(const RawImage& in, std::vector<uint8_t>& out) {
    codecLog(LOG_DEBUG, "[PNG Encode] 开始编码: %dx%d", in.width, in.height);

    png_structp png = png_create_write_struct(PNG_LIBPNG_VER_STRING, nullptr, nullptr, nullptr);
    if (!png) {
        codecLog(LOG_ERROR, "[PNG Encode] png_create_write_struct 失败");
        return false;
    }
    png_infop info = png_create_info_struct(png);
    if (!info) {
        codecLog(LOG_ERROR, "[PNG Encode] png_create_info_struct 失败");
        png_destroy_write_struct(&png, nullptr);
        return false;
    }

    png_set_write_fn(png, &out, pngWriteCallback, pngFlushCallback);
    png_set_IHDR(png, info, in.width, in.height, 8, PNG_COLOR_TYPE_RGBA,
                 PNG_INTERLACE_NONE, PNG_COMPRESSION_TYPE_DEFAULT, PNG_FILTER_TYPE_DEFAULT);
    png_write_info(png, info);

    std::vector<png_bytep> rowPointers(in.height);
    for (int y = 0; y < in.height; ++y) {
        rowPointers[y] = const_cast<png_bytep>(&in.rgba[y * in.width * 4]);
    }

    png_write_image(png, rowPointers.data());
    png_write_end(png, nullptr);
    png_destroy_write_struct(&png, &info);

    codecLog(LOG_DEBUG, "[PNG Encode] 编码完成: %zu bytes", out.size());
    return true;
}
