#include "common.h"
#include <stdarg.h>

static JavaVM* g_javaVM = nullptr;
static jclass g_logClass = nullptr;
static jmethodID g_logMethodId = nullptr;

void initCodecLogger(JavaVM* vm) {
    g_javaVM = vm;
}

void cacheLogMethod(JNIEnv* env) {
    if (g_logClass == nullptr) {
        jclass localClass = env->FindClass("com/example/imagecodec/ImageCodecLog");
        if (localClass != nullptr) {
            g_logClass = (jclass)env->NewGlobalRef(localClass);
            env->DeleteLocalRef(localClass);
        }
    }
    if (g_logClass != nullptr && g_logMethodId == nullptr) {
        g_logMethodId = env->GetStaticMethodID(g_logClass, "log", "(ILjava/lang/String;)V");
    }
}

void codecLog(LogLevel level, const char* fmt, ...) {
    if (g_javaVM == nullptr) return;

    JNIEnv* env;
    jint attachResult = g_javaVM->GetEnv((void**)&env, JNI_VERSION_1_6);
    bool needDetach = false;
    if (attachResult == JNI_EDETACHED) {
        g_javaVM->AttachCurrentThread(&env, nullptr);
        needDetach = true;
    } else if (attachResult != JNI_OK) {
        return;
    }

    cacheLogMethod(env);

    char buffer[2048];
    va_list args;
    va_start(args, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, args);
    va_end(args);

    if (g_logMethodId != nullptr && g_logClass != nullptr) {
        jstring msg = env->NewStringUTF(buffer);
        env->CallStaticVoidMethod(g_logClass, g_logMethodId, (int)level, msg);
        env->DeleteLocalRef(msg);
    } else {
        __android_log_print(ANDROID_LOG_INFO, "ImageCodec", "%s", buffer);
    }

    if (needDetach) {
        g_javaVM->DetachCurrentThread();
    }
}

void codecLog(LogLevel level, const std::string& message) {
    codecLog(level, "%s", message.c_str());
}

const char* getFormatName(int format) {
    switch (format) {
        case 0: return "JPEG";
        case 1: return "PNG";
        case 2: return "WebP";
        default: return "Unknown";
    }
}

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    initCodecLogger(vm);
    return JNI_VERSION_1_6;
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_imagecodec_ImageCodec_nativeDecode(
        JNIEnv* env, jclass clazz,
        jbyteArray input, jintArray outSize) {

    jsize len = env->GetArrayLength(input);
    codecLog(LOG_INFO, "[Decode] 开始解码，输入数据大小: %d bytes", len);

    std::vector<uint8_t> buffer(len);
    env->GetByteArrayRegion(input, 0, len, reinterpret_cast<jbyte*>(buffer.data()));

    RawImage img;
    bool ok = false;
    const char* formatName = "Unknown";

    if (len > 2 && buffer[0] == 0xFF && buffer[1] == 0xD8) {
        formatName = "JPEG";
        codecLog(LOG_DEBUG, "[Decode] 格式识别: JPEG (Magic: FF D8)");
        ok = decodeJpeg(buffer.data(), len, img);
    } else if (len > 8 && buffer[0] == 0x89 && buffer[1] == 0x50) {
        formatName = "PNG";
        codecLog(LOG_DEBUG, "[Decode] 格式识别: PNG (Magic: 89 50)");
        ok = decodePng(buffer.data(), len, img);
    } else if (len > 12 && buffer[0] == 'R' && buffer[1] == 'I') {
        formatName = "WebP";
        codecLog(LOG_DEBUG, "[Decode] 格式识别: WebP (Magic: RIFF)");
    } else {
        codecLog(LOG_ERROR, "[Decode] 格式识别失败: 不支持的图片格式 (Magic: %02X %02X)",
                 len > 0 ? buffer[0] : 0, len > 1 ? buffer[1] : 0);
        return nullptr;
    }

    if (!ok) {
        codecLog(LOG_ERROR, "[Decode] %s 解码失败", formatName);
        return nullptr;
    }

    if (img.rgba.empty()) {
        codecLog(LOG_ERROR, "[Decode] 解码结果为空");
        return nullptr;
    }

    codecLog(LOG_INFO, "[Decode] %s 解码成功: %dx%d, RGBA 数据大小: %zu bytes",
             formatName, img.width, img.height, img.rgba.size());

    jint sizes[2] = {img.width, img.height};
    env->SetIntArrayRegion(outSize, 0, 2, sizes);

    jbyteArray result = env->NewByteArray(img.rgba.size());
    env->SetByteArrayRegion(result, 0, img.rgba.size(),
                           reinterpret_cast<const jbyte*>(img.rgba.data()));
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_example_imagecodec_ImageCodec_nativeEncode(
        JNIEnv* env, jclass clazz,
        jbyteArray rgba, jint width, jint height,
        jint format, jint quality) {

    jsize pixelLen = env->GetArrayLength(rgba);
    const char* fmtName = getFormatName(format);

    codecLog(LOG_INFO, "[Encode] 开始编码: 格式=%s, 尺寸=%dx%d, 质量=%d, 输入像素数据=%d bytes",
             fmtName, width, height, quality, pixelLen);

    RawImage img;
    img.width = width;
    img.height = height;
    img.rgba.resize(pixelLen);
    env->GetByteArrayRegion(rgba, 0, pixelLen, reinterpret_cast<jbyte*>(img.rgba.data()));

    std::vector<uint8_t> out;
    bool ok = false;

    switch (format) {
        case 0:
            ok = encodeJpeg(img, out, quality);
            break;
        case 1:
            ok = encodePng(img, out);
            break;
        case 2:
            ok = encodeWebp(img, out, quality);
            break;
        default:
            codecLog(LOG_ERROR, "[Encode] 不支持的目标格式: %d", format);
            return nullptr;
    }

    if (!ok || out.empty()) {
        codecLog(LOG_ERROR, "[Encode] %s 编码失败", fmtName);
        return nullptr;
    }

    codecLog(LOG_INFO, "[Encode] %s 编码成功: 输出 %zu bytes", fmtName, out.size());

    jbyteArray result = env->NewByteArray(out.size());
    env->SetByteArrayRegion(result, 0, out.size(), reinterpret_cast<const jbyte*>(out.data()));
    return result;
}

}
