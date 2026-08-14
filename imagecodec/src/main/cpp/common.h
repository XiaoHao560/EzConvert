#ifndef IMAGE_CODEC_COMMON_H
#define IMAGE_CODEC_COMMON_H

#include <jni.h>
#include <android/log.h>
#include <vector>
#include <cstdint>
#include <string>

// 日志级别，与 Java 层对应
enum LogLevel {
    LOG_VERBOSE = 0,
    LOG_DEBUG = 1,
    LOG_INFO = 2,
    LOG_WARN = 3,
    LOG_ERROR = 4
};

// 日志函数声明
void codecLog(LogLevel level, const char* fmt, ...);
void codecLog(LogLevel level, const std::string& message);
void initCodecLogger(JavaVM* vm);
void cacheLogMethod(JNIEnv* env);

// 获取格式名称
const char* getFormatName(int format);

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
