package com.tech.ezconvert.utils;

public class Log {
    public static final int VERBOSE = android.util.Log.VERBOSE;
    public static final int DEBUG = android.util.Log.DEBUG;
    public static final int INFO = android.util.Log.INFO;
    public static final int WARN = android.util.Log.WARN;
    public static final int ERROR = android.util.Log.ERROR;
    public static final int ASSERT = android.util.Log.ASSERT;

    private static final String LOG_CLASS_NAME = Log.class.getName();
    private static final String LOG_MANAGER_CLASS_NAME = "com.tech.ezconvert.utils.LogManager";

    private static LogManager getSafeInstance() {
        try {
            return LogManager.getInstance();
        } catch (Exception e) {
            return null; // 未初始化时返回null
        }
    }

    // 获取调用者信息 (文件名和行号)，格式为 "(FileName.java:行号)"
    // 如果获取失败则返回空字符串
    private static String getCallerInfo() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        
        boolean foundLogClass = false;
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            
            // 跳过所有属于 Log 自身或 LogManager 的栈帧
            if (className.equals(LOG_CLASS_NAME) || className.equals(LOG_MANAGER_CLASS_NAME)) {
                foundLogClass = true;
                continue;
            }
            
            // 跳过 Android 内部堆栈获取相关的类 (这些类名通常以 dalvik. 或 java.lang. 开头，并且出现在调用链的最前面)
            // 在已经找到 Log 类之后，再遇到的系统类也要跳过，因为真正的调用者应当在 Log 类调用之后
            if (className.startsWith("dalvik.") || className.startsWith("java.lang.Thread") || className.startsWith("java.lang.VMStack")) {
                continue;
            }
            
            // 如果还没有遇到过 Log 类，说明堆栈还在更底层，继续寻找
            if (!foundLogClass) {
                continue;
            }
            
            // 找到第一个符合要求的栈帧
            String fileName = element.getFileName();
            int lineNumber = element.getLineNumber();
            if (fileName != null) {
                return " (" + fileName + ":" + lineNumber + ")";
            } else {
                // 如果没有文件名，则返回类名和方法名
                return " (" + className + "." + element.getMethodName() + ")";
            }
        }
        return "";
    }

    // 将调用者信息附加到消息尾部
    private static String appendCallerInfo(String msg) {
        String callerInfo = getCallerInfo();
        if (msg == null || msg.isEmpty()) {
            return callerInfo.isEmpty() ? "" : callerInfo.trim();
        }
        return msg + callerInfo;
    }

    public static int v(String tag, String msg) {
        LogManager instance = getSafeInstance();
        String fullMsg = appendCallerInfo(msg);
        if (instance != null) instance.addAppLog(VERBOSE, tag, fullMsg, null);
        return android.util.Log.v(tag, fullMsg);
    }

    public static int v(String tag, String msg, Throwable tr) {
        LogManager instance = getSafeInstance();
        String fullMsg = appendCallerInfo(msg);
        if (instance != null) instance.addAppLog(VERBOSE, tag, fullMsg, tr);
        return android.util.Log.v(tag, fullMsg, tr);
    }

    public static int d(String tag, String msg) {
        LogManager instance = getSafeInstance();
        String fullMsg = appendCallerInfo(msg);
        if (instance != null) instance.addAppLog(DEBUG, tag, fullMsg, null);
        return android.util.Log.d(tag, fullMsg);
    }

    public static int d(String tag, String msg, Throwable tr) {
        LogManager instance = getSafeInstance();
        String fullMsg = appendCallerInfo(msg);
        if (instance != null) instance.addAppLog(DEBUG, tag, fullMsg, tr);
        return android.util.Log.d(tag, fullMsg, tr);
    }

    public static int i(String tag, String msg) {
        LogManager instance = getSafeInstance();
        String fullMsg = appendCallerInfo(msg);
        if (instance != null) instance.addAppLog(INFO, tag, fullMsg, null);
        return android.util.Log.i(tag, fullMsg);
    }

    public static int i(String tag, String msg, Throwable tr) {
        LogManager instance = getSafeInstance();
        String fullMsg = appendCallerInfo(msg);
        if (instance != null) instance.addAppLog(INFO, tag, fullMsg, tr);
        return android.util.Log.i(tag, fullMsg, tr);
    }

    public static int w(String tag, String msg) {
        LogManager instance = getSafeInstance();
        String fullMsg = appendCallerInfo(msg);
        if (instance != null) instance.addAppLog(WARN, tag, fullMsg, null);
        return android.util.Log.w(tag, fullMsg);
    }

    public static int w(String tag, String msg, Throwable tr) {
        LogManager instance = getSafeInstance();
        String fullMsg = appendCallerInfo(msg);
        if (instance != null) instance.addAppLog(WARN, tag, fullMsg, tr);
        return android.util.Log.w(tag, fullMsg, tr);
    }

    public static int w(String tag, Throwable tr) {
        LogManager instance = getSafeInstance();
        String fullMsg = appendCallerInfo("");
        if (instance != null) instance.addAppLog(WARN, tag, fullMsg, tr);
        return android.util.Log.w(tag, fullMsg, tr);
    }

    public static int e(String tag, String msg) {
        LogManager instance = getSafeInstance();
        String fullMsg = appendCallerInfo(msg);
        if (instance != null) instance.addAppLog(ERROR, tag, fullMsg, null);
        return android.util.Log.e(tag, fullMsg);
    }

    public static int e(String tag, String msg, Throwable tr) {
        LogManager instance = getSafeInstance();
        String fullMsg = appendCallerInfo(msg);
        if (instance != null) instance.addAppLog(ERROR, tag, fullMsg, tr);
        return android.util.Log.e(tag, fullMsg, tr);
    }
}