package com.tech.ezconvert.utils;

public class NativeLogWriter {
    static {
        System.loadLibrary("ezlog");
    }

    public static native void init(String logDir, int maxFileSize);
    public static native void write(char level, String tag, String msg);
    public static native void flush();
    public static native void close();
}
