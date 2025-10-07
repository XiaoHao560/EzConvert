#ifndef EZCONVERT_FFMPEG_WRAPPER_H
#define EZCONVERT_FFMPEG_WRAPPER_H

#include <jni.h>
#include <string>

// 只有在没有模拟模式下才包含FFmpeg头文件
#ifndef SIMULATE_FFMPEG
#ifdef __cplusplus
extern "C" {
#endif

#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libavutil/avutil.h>
#include <libswscale/swscale.h>

#ifdef __cplusplus
}
#endif
#endif

class FFmpegWrapper {
public:
    static bool initialize();
    static void cleanup();
    
    // 获取媒体文件信息
    static std::string getMediaInfo(const char* filePath);
    
    // 视频转换
    static bool convertVideo(const char* inputPath, const char* outputPath, 
                           const char* outputFormat, JNIEnv* env, jobject callback);
    
    // 音频转换  
    static bool convertAudio(const char* inputPath, const char* outputPath,
                           const char* outputFormat, JNIEnv* env, jobject callback);
    
    // 提取音频
    static bool extractAudio(const char* inputPath, const char* outputPath,
                           JNIEnv* env, jobject callback);
    
    // 提取视频（无音频）
    static bool extractVideo(const char* inputPath, const char* outputPath,
                           JNIEnv* env, jobject callback);

private:
    static void updateProgress(JNIEnv* env, jobject callback, int progress);
    static bool isCancelled(JNIEnv* env, jobject callback);
    
    // 模拟FFmpeg功能的辅助方法
    static std::string simulateMediaInfo(const char* filePath);
    static bool simulateConversion(const char* inputPath, const char* outputPath,
                                 const char* operation, JNIEnv* env, jobject callback);
};

#endif //EZCONVERT_FFMPEG_WRAPPER_H