package com.example.imagecodec;

/**
 * 图片编解码日志回调接口
 */
public interface ImageCodecLogCallback {
    /**
     * 收到日志回调
     * @param level 日志级别：0=VERBOSE, 1=DEBUG, 2=INFO, 3=WARN, 4=ERROR
     * @param message 日志内容
     */
    void onLog(int level, String message);
}
