#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "EzConvertNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_tech_ezconvert_MainActivity_nativeGetVersion(JNIEnv* env, jobject thiz) {
    std::string version = "EzConvert Native v0.1.0";
    return env->NewStringUTF(version.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_tech_ezconvert_MainActivity_nativeTestFFmpeg(JNIEnv* env, jobject thiz) {
    std::string status = "FFmpeg 已通过 Java 层集成";
    return env->NewStringUTF(status.c_str());
}