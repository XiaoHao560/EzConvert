package com.tech.ezconvert.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import com.arthenica.ffmpegkit.Level;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class LogManager {
    private static final String TAG = "LogManager";
    private static LogManager instance;
    
    private Context context;
    private File appLogFile;
    private File ffmpegLogFile;
    private boolean verboseLogging = true;
    
    // 内存缓存
    private final CopyOnWriteArrayList<LogEntry> appLogMemoryCache = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<String> ffmpegLogMemoryCache = new CopyOnWriteArrayList<>();
    private final List<LogListener> listeners = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final int MAX_MEMORY_CACHE = 5000;

    public static class LogEntry {
        public long timestamp;
        public int level;
        public String tag;
        public String message;
        public String throwable;

        public LogEntry(int level, String tag, String message, Throwable tr) {
            this.timestamp = System.currentTimeMillis();
            this.level = level;
            this.tag = tag != null ? tag : "";
            this.message = message != null ? message : "";
            this.throwable = tr != null ? android.util.Log.getStackTraceString(tr) : null;
        }

        public String getFormattedMessage() {
            String timeStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", 
                java.util.Locale.getDefault()).format(new java.util.Date(timestamp));
            String levelStr = getLevelString(level);
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(timeStr).append("] ")
              .append("[").append(levelStr).append("] ")
              .append("[").append(tag).append("] ")
              .append(message);
            if (!TextUtils.isEmpty(throwable)) {
                sb.append("\n").append(throwable);
            }
            return sb.toString();
        }

        private String getLevelString(int level) {
            switch (level) {
                case android.util.Log.VERBOSE: return "VERBOSE";
                case android.util.Log.DEBUG: return "DEBUG";
                case android.util.Log.INFO: return "INFO";
                case android.util.Log.WARN: return "WARN";
                case android.util.Log.ERROR: return "ERROR";
                case android.util.Log.ASSERT: return "ASSERT";
                default: return "?";
            }
        }
    }

    public interface LogListener {
        void onLogAdded(LogEntry entry);
        void onLogsCleared();
    }

    public static synchronized LogManager getInstance(Context context) {
        if (instance == null) {
            instance = new LogManager(context);
        }
        return instance;
    }

    public static synchronized LogManager getInstance() {
        if (instance == null) {
            android.app.Application app = getApplicationUsingReflection();
            if (app != null) {
                instance = new LogManager(app);
            } else {
                throw new IllegalStateException("LogManager需要先调用 getInstance(Context) 初始化");
            }
        }
        return instance;
    }
    
    private static android.app.Application getApplicationUsingReflection() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getMethod("currentActivityThread").invoke(null);
            Object app = activityThread.getMethod("getApplication").invoke(thread);
            return (android.app.Application) app;
        } catch (Exception e) {
            return null;
        }
    }

    private LogManager(Context context) {
        this.context = context.getApplicationContext();
        init();
    }

    private void init() {
        try {
            File logDir = new File(context.getExternalFilesDir(null), "logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            appLogFile = new File(logDir, "EzConvert.log");
            ffmpegLogFile = new File(logDir, "FFmpeg.log");
            
            android.util.Log.d(TAG, "日志管理器初始化完成");
        } catch (Throwable e) {
            android.util.Log.e(TAG, "LogManager 初始化失败", e);
        }
    }

    // 用于自定义Log类调用
    public void addAppLog(int level, String tag, String message, Throwable tr) {
        
        if ("FFmpegLog".equals(tag)) {
            return;
        }
        
        try {
            
            // 检查是否应该记录此日志
            if (!shouldLog(level)) {
                return;
            }
            
            LogEntry entry = new LogEntry(level, tag, message, tr);
            
            appLogMemoryCache.add(entry);
            if (appLogMemoryCache.size() > MAX_MEMORY_CACHE) {
                appLogMemoryCache.remove(0);
            }
            
            Level ffmpegLevel = convertToFfmpegLevel(level);
            String formatted = entry.getFormattedMessage();
            writeToFile(appLogFile, formatted);
            
            mainHandler.post(() -> {
                for (LogListener listener : listeners) {
                    listener.onLogAdded(entry);
                }
            });
        } catch (Throwable e) {
            android.util.Log.e(TAG, "addAppLog异常", e);
        }
    }

    // 记录FFmpeg日志
    public void appendFfmpegLog(String message, Level level) {
        if (!shouldLog(level)) return;
        
        String logLine = formatLogLine(message, level);
        ffmpegLogMemoryCache.add(logLine);
        if (ffmpegLogMemoryCache.size() > MAX_MEMORY_CACHE) {
            ffmpegLogMemoryCache.remove(0);
        }
        writeToFile(ffmpegLogFile, logLine);
    }

    public void addListener(LogListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(LogListener listener) {
        listeners.remove(listener);
    }

    // 从内存获取应用日志
    public List<String> getAppLogsFromMemory() {
        List<String> logs = new ArrayList<>();
        for (LogEntry entry : appLogMemoryCache) {
            logs.add(entry.getFormattedMessage());
        }
        return logs;
    }

    // 从内存获取FFmpeg日志
    public List<String> getFfmpegLogsFromMemory() {
        return new ArrayList<>(ffmpegLogMemoryCache);
    }

    // 更新日志等级
    public void updateLogLevel(boolean verbose) {
        this.verboseLogging = verbose;
    }

    private Level convertToFfmpegLevel(int androidLevel) {
        switch (androidLevel) {
            case android.util.Log.VERBOSE:
            case android.util.Log.DEBUG: return Level.AV_LOG_DEBUG;
            case android.util.Log.INFO: return Level.AV_LOG_INFO;
            case android.util.Log.WARN: return Level.AV_LOG_WARNING;
            case android.util.Log.ERROR:
            case android.util.Log.ASSERT: return Level.AV_LOG_ERROR;
            default: return Level.AV_LOG_INFO;
        }
    }

    // 获取所有日志（用于复制功能）
    public List<String> getAllLogs() {
        List<String> allLogs = new ArrayList<>();
        allLogs.add("========== 应用日志 ==========");
        allLogs.addAll(getAppLogsFromMemory());
        allLogs.add("\n========== FFmpeg日志 ==========");
        allLogs.addAll(getFfmpegLogsFromMemory());
        return allLogs;
    }

    // 清空所有日志
    public void clearAllLogs() {
        appLogMemoryCache.clear();
        ffmpegLogMemoryCache.clear();
        
        clearFile(appLogFile);
        clearFile(ffmpegLogFile);
        
        mainHandler.post(() -> {
            for (LogListener listener : listeners) {
                listener.onLogsCleared();
            }
        });
    }

    private void writeToFile(File file, String content) {
        try {
            FileWriter writer = new FileWriter(file, true);
            writer.write(content + "\n");
            writer.flush();
            writer.close();
        } catch (IOException e) {
            android.util.Log.e(TAG, "写入失败: " + e.getMessage());
        }
    }

    private void clearFile(File file) {
        try {
            FileWriter writer = new FileWriter(file, false);
            writer.write("");
            writer.flush();
            writer.close();
        } catch (IOException e) {
            android.util.Log.e(TAG, "清空失败: " + e.getMessage());
        }
    }

    private String formatLogLine(String message, Level level) {
        String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", 
            java.util.Locale.getDefault()).format(new java.util.Date());
        return String.format("[%s] [%s] %s", timestamp, levelToString(level), message);
    }

    private String levelToString(Level level) {
        if (level == null) return "INFO";
        switch (level) {
            case AV_LOG_FATAL: return "FATAL";
            case AV_LOG_PANIC: return "PANIC";
            case AV_LOG_ERROR: return "ERROR";
            case AV_LOG_WARNING: return "WARN";
            case AV_LOG_INFO: return "INFO";
            case AV_LOG_DEBUG: return "DEBUG";
            default: return "VERBOSE";
        }
    }
    
    // 用于android日志级别过滤
    private boolean shouldLog(int androidLevel) {
        if (verboseLogging) return true;
        
        return androidLevel == android.util.Log.ERROR ||
               androidLevel == android.util.Log.WARN;
    }

    // 用于FFmpeg日志级别过滤
    private boolean shouldLog(Level level) {
        if (verboseLogging) return true;
        
        return level == Level.AV_LOG_ERROR || 
               level == Level.AV_LOG_WARNING || 
               level == Level.AV_LOG_FATAL || 
               level == Level.AV_LOG_PANIC;
    }
}
