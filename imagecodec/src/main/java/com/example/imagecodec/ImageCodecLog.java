package com.example.imagecodec;

import android.util.Log;

/**
 * ImageCodec 日志管理类
 */
public class ImageCodecLog {

    public static final int LEVEL_VERBOSE = 0;
    public static final int LEVEL_DEBUG   = 1;
    public static final int LEVEL_INFO    = 2;
    public static final int LEVEL_WARN    = 3;
    public static final int LEVEL_ERROR   = 4;

    private static int sLevel = LEVEL_DEBUG;
    private static ImageCodecLogCallback sCallback;

    /**
     * 设置日志级别，低于该级别的日志不会输出
     */
    public static void setLevel(int level) {
        sLevel = level;
    }

    /**
     * 设置自定义日志回调
     * 设置后日志不再输出到系统 Logcat，而是交给你的回调处理
     */
    public static void setCallback(ImageCodecLogCallback callback) {
        sCallback = callback;
    }

    /**
     * 清除自定义回调，恢复默认 Logcat 输出
     */
    public static void clearCallback() {
        sCallback = null;
    }

    /**
     * Native 层调用的日志入口
     */
    static void log(int level, String message) {
        if (level < sLevel) return;

        if (sCallback != null) {
            sCallback.onLog(level, message);
        } else {
            switch (level) {
                case LEVEL_VERBOSE:
                    Log.v("ImageCodec", message);
                    break;
                case LEVEL_DEBUG:
                    Log.d("ImageCodec", message);
                    break;
                case LEVEL_INFO:
                    Log.i("ImageCodec", message);
                    break;
                case LEVEL_WARN:
                    Log.w("ImageCodec", message);
                    break;
                case LEVEL_ERROR:
                    Log.e("ImageCodec", message);
                    break;
            }
        }
    }
}
