package com.tech.ezconvert.utils;

public class Log {
    public static final int VERBOSE = android.util.Log.VERBOSE;
    public static final int DEBUG = android.util.Log.DEBUG;
    public static final int INFO = android.util.Log.INFO;
    public static final int WARN = android.util.Log.WARN;
    public static final int ERROR = android.util.Log.ERROR;
    public static final int ASSERT = android.util.Log.ASSERT;

    private static LogManager getSafeInstance() {
        try {
            return LogManager.getInstance();
        } catch (Exception e) {
            return null; // 未初始化时返回null
        }
    }

    public static int v(String tag, String msg) {
        LogManager instance = getSafeInstance();
        if (instance != null) instance.addAppLog(VERBOSE, tag, msg, null);
        return android.util.Log.v(tag, msg);
    }

    public static int v(String tag, String msg, Throwable tr) {
        LogManager instance = getSafeInstance();
        if (instance != null) instance.addAppLog(VERBOSE, tag, msg, tr);
        return android.util.Log.v(tag, msg, tr);
    }

    public static int d(String tag, String msg) {
        LogManager instance = getSafeInstance();
        if (instance != null) instance.addAppLog(DEBUG, tag, msg, null);
        return android.util.Log.d(tag, msg);
    }

    public static int d(String tag, String msg, Throwable tr) {
        LogManager instance = getSafeInstance();
        if (instance != null) instance.addAppLog(DEBUG, tag, msg, tr);
        return android.util.Log.d(tag, msg, tr);
    }

    public static int i(String tag, String msg) {
        LogManager instance = getSafeInstance();
        if (instance != null) instance.addAppLog(INFO, tag, msg, null);
        return android.util.Log.i(tag, msg);
    }

    public static int i(String tag, String msg, Throwable tr) {
        LogManager instance = getSafeInstance();
        if (instance != null) instance.addAppLog(INFO, tag, msg, tr);
        return android.util.Log.i(tag, msg, tr);
    }

    public static int w(String tag, String msg) {
        LogManager instance = getSafeInstance();
        if (instance != null) instance.addAppLog(WARN, tag, msg, null);
        return android.util.Log.w(tag, msg);
    }

    public static int w(String tag, String msg, Throwable tr) {
        LogManager instance = getSafeInstance();
        if (instance != null) instance.addAppLog(WARN, tag, msg, tr);
        return android.util.Log.w(tag, msg, tr);
    }

    public static int w(String tag, Throwable tr) {
        LogManager instance = getSafeInstance();
        if (instance != null) instance.addAppLog(WARN, tag, "", tr);
        return android.util.Log.w(tag, tr);
    }

    public static int e(String tag, String msg) {
        LogManager instance = getSafeInstance();
        if (instance != null) instance.addAppLog(ERROR, tag, msg, null);
        return android.util.Log.e(tag, msg);
    }

    public static int e(String tag, String msg, Throwable tr) {
        LogManager instance = getSafeInstance();
        if (instance != null) instance.addAppLog(ERROR, tag, msg, tr);
        return android.util.Log.e(tag, msg, tr);
    }
}
