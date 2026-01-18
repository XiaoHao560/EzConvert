package com.tech.ezconvert.utils;

import android.content.Context;
import android.util.Log;
import com.arthenica.ffmpegkit.Level;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class LogManager {
    private static final String TAG = "LogManager";
    private static LogManager instance;
    
    private Context context;
    private File appLogFile;
    private File ffmpegLogFile;
    private boolean verboseLogging = true;
    
    private final List<String> appLogBuffer = new ArrayList<>();
    private final List<String> ffmpegLogBuffer = new ArrayList<>();

    public static synchronized LogManager getInstance(Context context) {
        if (instance == null) {
            instance = new LogManager(context);
        }
        return instance;
    }

    private LogManager(Context context) {
        this.context = context.getApplicationContext();
        init();
    }

    private void init() {
        File logDir = new File(context.getExternalFilesDir(null), "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        
        appLogFile = new File(logDir, "EzConvert.log");
        ffmpegLogFile = new File(logDir, "FFmpeg.log");
        
        ConfigManager config = ConfigManager.getInstance(context);
        verboseLogging = config.isVerboseLoggingEnabled();
        
        Log.d(TAG, "日志管理器初始化完成，详细模式: " + verboseLogging);
    }

    // 记录应用日志
    public void appendAppLog(String message, Level level) {
        if (!shouldLog(level)) return;
        
        String logLine = formatLogLine(message, level);
        appLogBuffer.add(logLine);
        writeToFile(appLogFile, logLine);
    }

    // 记录FFmpeg日志
    public void appendFfmpegLog(String message, Level level) {
        if (!shouldLog(level)) return;
        
        String logLine = formatLogLine(message, level);
        ffmpegLogBuffer.add(logLine);
        writeToFile(ffmpegLogFile, logLine);
    }

    // 获取应用日志
    public List<String> getAppLogs() {
        return new ArrayList<>(appLogBuffer);
    }

    // 获取FFmpeg日志
    public List<String> getFfmpegLogs() {
        return new ArrayList<>(ffmpegLogBuffer);
    }

    // 获取所有日志（用于复制功能）
    public List<String> getAllLogs() {
        List<String> allLogs = new ArrayList<>();
        allLogs.add("========== 应用日志 ==========");
        allLogs.addAll(appLogBuffer);
        allLogs.add("\n========== FFmpeg日志 ==========");
        allLogs.addAll(ffmpegLogBuffer);
        return allLogs;
    }

    // 清空所有日志（用于清空按钮）
    public void clearAllLogs() {
        appLogBuffer.clear();
        ffmpegLogBuffer.clear();
        clearFile(appLogFile);
        clearFile(ffmpegLogFile);
    }

    // 更新日志等级设置
    public void updateLogLevel(boolean verbose) {
        this.verboseLogging = verbose;
    }

    private void writeToFile(File file, String content) {
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(content + "\n");
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "写入日志文件失败: " + file.getAbsolutePath(), e);
        }
    }

    private void clearFile(File file) {
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write("");
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "清空日志文件失败: " + file.getAbsolutePath(), e);
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

    private boolean shouldLog(Level level) {
        if (verboseLogging) return true;
        
        return level == Level.AV_LOG_ERROR || 
               level == Level.AV_LOG_WARNING || 
               level == Level.AV_LOG_FATAL || 
               level == Level.AV_LOG_PANIC;
    }
}
