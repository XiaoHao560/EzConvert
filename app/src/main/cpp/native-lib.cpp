#include <jni.h>
#include <string>
#include "ffmpeg_wrapper.h"

#define JNI_FUNCTION(RETURN_TYPE, NAME) \
    extern "C" JNIEXPORT RETURN_TYPE JNICALL \
    Java_com_tech_ezconvert_FFmpegExecutor_##NAME

JNI_FUNCTION(void, nativeInitialize)(JNIEnv* env, jobject thiz) {
    FFmpegWrapper::initialize();
}

JNI_FUNCTION(void, nativeCleanup)(JNIEnv* env, jobject thiz) {
    FFmpegWrapper::cleanup();
}

JNI_FUNCTION(jstring, nativeGetMediaInfo)(JNIEnv* env, jobject thiz, jstring filePath) {
    const char* path = env->GetStringUTFChars(filePath, nullptr);
    std::string info = FFmpegWrapper::getMediaInfo(path);
    env->ReleaseStringUTFChars(filePath, path);
    return env->NewStringUTF(info.c_str());
}

JNI_FUNCTION(jboolean, nativeConvertVideo)(JNIEnv* env, jobject thiz, 
                                         jstring inputPath, jstring outputPath,
                                         jstring outputFormat, jobject callback) {
    const char* input = env->GetStringUTFChars(inputPath, nullptr);
    const char* output = env->GetStringUTFChars(outputPath, nullptr);
    const char* format = env->GetStringUTFChars(outputFormat, nullptr);
    
    bool result = FFmpegWrapper::convertVideo(input, output, format, env, callback);
    
    env->ReleaseStringUTFChars(inputPath, input);
    env->ReleaseStringUTFChars(outputPath, output);
    env->ReleaseStringUTFChars(outputFormat, format);
    
    return result;
}

JNI_FUNCTION(jboolean, nativeConvertAudio)(JNIEnv* env, jobject thiz,
                                         jstring inputPath, jstring outputPath,
                                         jstring outputFormat, jobject callback) {
    const char* input = env->GetStringUTFChars(inputPath, nullptr);
    const char* output = env->GetStringUTFChars(outputPath, nullptr);
    const char* format = env->GetStringUTFChars(outputFormat, nullptr);
    
    bool result = FFmpegWrapper::convertAudio(input, output, format, env, callback);
    
    env->ReleaseStringUTFChars(inputPath, input);
    env->ReleaseStringUTFChars(outputPath, output);
    env->ReleaseStringUTFChars(outputFormat, format);
    
    return result;
}

JNI_FUNCTION(jboolean, nativeExtractAudio)(JNIEnv* env, jobject thiz,
                                         jstring inputPath, jstring outputPath,
                                         jobject callback) {
    const char* input = env->GetStringUTFChars(inputPath, nullptr);
    const char* output = env->GetStringUTFChars(outputPath, nullptr);
    
    bool result = FFmpegWrapper::extractAudio(input, output, env, callback);
    
    env->ReleaseStringUTFChars(inputPath, input);
    env->ReleaseStringUTFChars(outputPath, output);
    
    return result;
}

JNI_FUNCTION(jboolean, nativeExtractVideo)(JNIEnv* env, jobject thiz,
                                         jstring inputPath, jstring outputPath,
                                         jobject callback) {
    const char* input = env->GetStringUTFChars(inputPath, nullptr);
    const char* output = env->GetStringUTFChars(outputPath, nullptr);
    
    bool result = FFmpegWrapper::extractVideo(input, output, env, callback);
    
    env->ReleaseStringUTFChars(inputPath, input);
    env->ReleaseStringUTFChars(outputPath, output);
    
    return result;
}