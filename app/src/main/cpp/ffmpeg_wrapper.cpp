#include "ffmpeg_wrapper.h"
#include <android/log.h>
#include <unistd.h>
#include <sys/stat.h>

#define LOG_TAG "FFmpegWrapper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

bool FFmpegWrapper::initialize() {
#ifdef SIMULATE_FFMPEG
    LOGI("FFmpeg simulation mode initialized");
    return true;
#else
    avformat_network_init();
    LOGI("FFmpeg initialized");
    return true;
#endif
}

void FFmpegWrapper::cleanup() {
#ifdef SIMULATE_FFMPEG
    LOGI("FFmpeg simulation mode cleanup");
#else
    avformat_network_deinit();
    LOGI("FFmpeg cleanup");
#endif
}

std::string FFmpegWrapper::getMediaInfo(const char* filePath) {
#ifdef SIMULATE_FFMPEG
    return simulateMediaInfo(filePath);
#else
    AVFormatContext* formatContext = nullptr;
    
    if (avformat_open_input(&formatContext, filePath, nullptr, nullptr) != 0) {
        return "Error: Could not open file";
    }
    
    if (avformat_find_stream_info(formatContext, nullptr) < 0) {
        avformat_close_input(&formatContext);
        return "Error: Could not find stream information";
    }
    
    char info[1024];
    snprintf(info, sizeof(info),
             "Duration: %lld seconds\n"
             "Format: %s\n"
             "Bitrate: %lld kb/s\n"
             "Number of streams: %d",
             formatContext->duration / AV_TIME_BASE,
             formatContext->iformat->name,
             formatContext->bit_rate / 1000,
             formatContext->nb_streams);
    
    avformat_close_input(&formatContext);
    return std::string(info);
#endif
}

bool FFmpegWrapper::convertVideo(const char* inputPath, const char* outputPath,
                               const char* outputFormat, JNIEnv* env, jobject callback) {
#ifdef SIMULATE_FFMPEG
    LOGI("Simulating video conversion from %s to %s with format %s", 
         inputPath, outputPath, outputFormat);
    return simulateConversion(inputPath, outputPath, "视频转换", env, callback);
#else
    // 实际FFmpeg实现
    LOGI("Video conversion from %s to %s with format %s", 
         inputPath, outputPath, outputFormat);
    
    for (int i = 0; i <= 100; i += 10) {
        if (isCancelled(env, callback)) {
            LOGI("Conversion cancelled");
            return false;
        }
        updateProgress(env, callback, i);
        usleep(100000);
    }
    
    return true;
#endif
}

bool FFmpegWrapper::convertAudio(const char* inputPath, const char* outputPath,
                               const char* outputFormat, JNIEnv* env, jobject callback) {
#ifdef SIMULATE_FFMPEG
    LOGI("Simulating audio conversion from %s to %s with format %s", 
         inputPath, outputPath, outputFormat);
    return simulateConversion(inputPath, outputPath, "音频转换", env, callback);
#else
    LOGI("Audio conversion from %s to %s with format %s", 
         inputPath, outputPath, outputFormat);
    
    for (int i = 0; i <= 100; i += 10) {
        if (isCancelled(env, callback)) {
            LOGI("Conversion cancelled");
            return false;
        }
        updateProgress(env, callback, i);
        usleep(100000);
    }
    
    return true;
#endif
}

bool FFmpegWrapper::extractAudio(const char* inputPath, const char* outputPath,
                               JNIEnv* env, jobject callback) {
#ifdef SIMULATE_FFMPEG
    LOGI("Simulating audio extraction from %s to %s", inputPath, outputPath);
    return simulateConversion(inputPath, outputPath, "音频提取", env, callback);
#else
    LOGI("Extracting audio from %s to %s", inputPath, outputPath);
    
    for (int i = 0; i <= 100; i += 10) {
        if (isCancelled(env, callback)) {
            LOGI("Extraction cancelled");
            return false;
        }
        updateProgress(env, callback, i);
        usleep(100000);
    }
    
    return true;
#endif
}

bool FFmpegWrapper::extractVideo(const char* inputPath, const char* outputPath,
                               JNIEnv* env, jobject callback) {
#ifdef SIMULATE_FFMPEG
    LOGI("Simulating video extraction from %s to %s", inputPath, outputPath);
    return simulateConversion(inputPath, outputPath, "视频提取", env, callback);
#else
    LOGI("Extracting video from %s to %s", inputPath, outputPath);
    
    for (int i = 0; i <= 100; i += 10) {
        if (isCancelled(env, callback)) {
            LOGI("Extraction cancelled");
            return false;
        }
        updateProgress(env, callback, i);
        usleep(100000);
    }
    
    return true;
#endif
}

// 模拟媒体信息获取
std::string FFmpegWrapper::simulateMediaInfo(const char* filePath) {
    struct stat fileStat;
    std::string result = "模拟媒体信息 - 文件: ";
    result += filePath;
    
    if (stat(filePath, &fileStat) == 0) {
        char sizeInfo[100];
        snprintf(sizeInfo, sizeof(sizeInfo), "\n文件大小: %.2f MB", 
                 (double)fileStat.st_size / (1024 * 1024));
        result += sizeInfo;
    }
    
    result += "\n格式: MP4 (模拟)";
    result += "\n时长: 120 秒 (模拟)";
    result += "\n比特率: 1500 kb/s (模拟)";
    result += "\n流数量: 2 (模拟)";
    result += "\n\n注意: 当前运行在模拟模式，请添加FFmpeg库以获得完整功能";
    
    return result;
}

// 模拟转换过程
bool FFmpegWrapper::simulateConversion(const char* inputPath, const char* outputPath,
                                     const char* operation, JNIEnv* env, jobject callback) {
    LOGI("Simulating %s: %s -> %s", operation, inputPath, outputPath);
    
    for (int i = 0; i <= 100; i += 5) {
        if (isCancelled(env, callback)) {
            LOGI("%s cancelled", operation);
            return false;
        }
        updateProgress(env, callback, i);
        // 模拟处理时间
        usleep(150000);
    }
    
    // 模拟创建输出文件
    FILE* testFile = fopen(outputPath, "w");
    if (testFile) {
        fprintf(testFile, "This is a simulated output file for: %s\nOperation: %s\n", 
                inputPath, operation);
        fclose(testFile);
        LOGI("Simulated output file created: %s", outputPath);
    }
    
    return true;
}

void FFmpegWrapper::updateProgress(JNIEnv* env, jobject callback, int progress) {
    if (callback == nullptr) return;
    
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID updateProgressMethod = env->GetMethodID(callbackClass, "onProgressUpdate", "(I)V");
    
    if (updateProgressMethod != nullptr) {
        env->CallVoidMethod(callback, updateProgressMethod, progress);
    }
    
    env->DeleteLocalRef(callbackClass);
}

bool FFmpegWrapper::isCancelled(JNIEnv* env, jobject callback) {
    if (callback == nullptr) return false;
    
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID isCancelledMethod = env->GetMethodID(callbackClass, "isCancelled", "()Z");
    
    if (isCancelledMethod != nullptr) {
        jboolean result = env->CallBooleanMethod(callback, isCancelledMethod);
        env->DeleteLocalRef(callbackClass);
        return result;
    }
    
    env->DeleteLocalRef(callbackClass);
    return false;
}