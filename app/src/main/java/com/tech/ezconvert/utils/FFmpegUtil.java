package com.tech.ezconvert.utils;

import android.content.Context;
import com.tech.ezconvert.utils.Log;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback;
import com.arthenica.ffmpegkit.Level;
import com.arthenica.ffmpegkit.LogCallback;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.SessionState;

import java.io.File;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.CopyOnWriteArraySet;

public class FFmpegUtil {

    private static final String TAG = "FFmpegUtil";
    private static FFmpegSession currentSession = null;
    private static ProgressSimulator progressSimulator;
    private static LogManager logManager;
    private static Context appContext;
    private static String currentFileName = "";

    // 资源清理机制
    private static final ReferenceQueue<FFmpegSession> refQueue = new ReferenceQueue<>();
    private static final CopyOnWriteArraySet<SessionPhantomRef> pendingRefs = new CopyOnWriteArraySet<>();
    
    static {
        // 后台清理线程
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                try {
                    SessionPhantomRef ref = (SessionPhantomRef) refQueue.remove();
                    ref.cleanup();
                    pendingRefs.remove(ref);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "FFmpeg-Cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }
    
    private static class SessionPhantomRef extends PhantomReference<FFmpegSession> {
        private final long sessionId;
        
        SessionPhantomRef(FFmpegSession referent) {
            super(referent, refQueue);
            this.sessionId = referent.getSessionId();
            pendingRefs.add(this);
        }
        
        void cleanup() {
            Log.d(TAG, "Phantom cleanup for session: " + sessionId);
            FFmpegKit.cancel(sessionId);
        }
    }

    public interface FFmpegCallback {
        void onProgress(int progress, long time);
        void onComplete(boolean success, String message);
        void onError(String error);
    }

    public static void initLogging(Context context) {
        appContext = context.getApplicationContext();
        ConfigManager configManager = ConfigManager.getInstance(context);
        boolean verboseLogging = configManager.isVerboseLoggingEnabled();
        
        logManager = LogManager.getInstance(context);
        logManager.updateLogLevel(verboseLogging);
        
        Log.d(TAG, "初始化日志设置，详细日志模式: " + verboseLogging);
        
        // 日志级别设置
        FFmpegKitConfig.enableLogCallback(new LogCallback() {
                @Override
                public void apply(com.arthenica.ffmpegkit.Log log) {
                    String line = log.getMessage();
                    Level level = log.getLevel();
                    
                    // 根据日志级别设置过滤日志
                    if (!shouldLog(level, verboseLogging)) {
                        return; // 不记录此日志
                    }
                    
                    // 记录到Logcat
                    switch (level) {
                        case AV_LOG_ERROR:
                            Log.e("FFmpegLog", line);
                            break;
                        case AV_LOG_WARNING:
                            Log.w("FFmpegLog", line);
                            break;
                        case AV_LOG_INFO:
                            Log.i("FFmpegLog", line);
                            break;
                        case AV_LOG_DEBUG:
                            if (verboseLogging) {
                                Log.d("FFmpegLog", line);
                            }
                            break;
                        default:
                            if (verboseLogging) {
                                Log.v("FFmpegLog", line);
                            }
                            break;
                    }
                    
                    // 记录到日志管理器
                    logManager.appendFfmpegLog(line, level);
                }
        });
    }

    // 根据当前日志设置判断是否应该记录此日志
    private static boolean shouldLog(Level level, boolean verboseLogging) {
        if (verboseLogging) {
            return true;
        }
        // 不是详细模式下只记录错误和警告等
        return level == Level.AV_LOG_ERROR || 
               level == Level.AV_LOG_WARNING ||
               level == Level.AV_LOG_FATAL ||
               level == Level.AV_LOG_PANIC;
    }

    public static void executeCommand(String[] command, FFmpegCallback callback, String tempInputPath, String fileName) {
        currentFileName = fileName != null ? fileName : "未知文件";
        
        // 创建通知渠道（首次执行时）
        if (appContext != null) {
            NotificationHelper.createNotificationChannels(appContext);
        }
        
        Log.d(TAG, "执行命令: " + String.join(" ", command));
        stopProgressSimulation();

        StringBuilder commandBuilder = new StringBuilder();
        for (int i = 0; i < command.length; i++) {
            String arg = command[i];
            
            if (isFilePathArgument(command, i, arg)) {
                commandBuilder.append("\"").append(arg).append("\"");
            } else {
                commandBuilder.append(arg);
            }
            
            if (i < command.length - 1) {
                commandBuilder.append(" ");
            }
        }
        
        String commandString = commandBuilder.toString();
        Log.d(TAG, "转义后的命令: " + commandString);
        
        // 记录开始执行日志
        if (logManager != null) {
            logManager.appendFfmpegLog("执行FFmpeg命令: " + commandString, Level.AV_LOG_INFO);
        }
        
        // 显示初始进度通知
        if (appContext != null) {
            NotificationHelper.showProgressNotification(appContext, currentFileName, 0);
        }
        
        // 确保取消之前的会话
        cancelCurrentTask();

        currentSession = FFmpegKit.executeAsync(commandString, new FFmpegSessionCompleteCallback() {
            @Override
            public void apply(FFmpegSession session) {
                ReturnCode returnCode = session.getReturnCode();
                Log.d(TAG, "命令执行完成，返回码: " + (returnCode != null ? returnCode.getValue() : "null"));
                
                stopProgressSimulation();
                
                // 转换完成后删除临时文件
                if (tempInputPath != null && tempInputPath.contains("/shared_files/")) {
                    deleteTempFile(tempInputPath);
                }
                
                if (callback != null) {
                    if (ReturnCode.isSuccess(returnCode)) {
                        callback.onComplete(true, "处理完成");
                        if (appContext != null) {
                            NotificationHelper.showCompleteNotification(appContext, currentFileName, true, "");
                        }
                        if (logManager != null) {
                            logManager.appendFfmpegLog("FFmpeg命令执行成功", Level.AV_LOG_INFO);
                        }
                    } else {
                        String errorMessage = "处理失败";
                        if (session.getFailStackTrace() != null) {
                            errorMessage += ": " + session.getFailStackTrace();
                        } else if (returnCode != null) {
                            errorMessage += "，返回码: " + returnCode.getValue();
                        }
                        callback.onComplete(false, errorMessage);
                        if (appContext != null) {
                            NotificationHelper.showCompleteNotification(appContext, currentFileName, false, errorMessage);
                        }
                        if (logManager != null) {
                            logManager.appendFfmpegLog("FFmpeg命令执行失败: " + errorMessage, Level.AV_LOG_ERROR);
                        }
                    }
                }
                
                // 立即清理，不依赖 finalize
                currentSession = null;
                currentFileName = "";
                // 主动清理所有已完成会话
                FFmpegKitConfig.clearSessions();
            }
        });

        // 注册 PhantomReference 防止内存泄漏
        if (currentSession != null) {
            new SessionPhantomRef(currentSession);
            startProgressSimulation(callback);
        } else {
            // 启动失败也清理
            if (tempInputPath != null && tempInputPath.contains("/shared_files/")) {
                deleteTempFile(tempInputPath);
            }
            if (callback != null) {
                callback.onError("命令执行失败，无法启动FFmpeg进程");
            }
            if (appContext != null) {
                NotificationHelper.showCompleteNotification(appContext, currentFileName, false, "无法启动FFmpeg进程");
            }
            if (logManager != null) {
                logManager.appendFfmpegLog("无法启动FFmpeg进程", Level.AV_LOG_FATAL);
            }
            currentFileName = "";
        }
    }

    private static void deleteTempFile(String path) {
        try {
            File file = new File(path);
            if (file.exists() && file.delete()) {
                Log.d(TAG, "已删除临时文件: " + path);
            }
        } catch (Exception e) {
            Log.e(TAG, "删除临时文件失败: " + path, e);
        }
    }

    private static boolean isFilePathArgument(String[] command, int index, String arg) {
        if (index > 0 && "-i".equals(command[index - 1])) {
            return true;
        }
        
        if (index == command.length - 1) {
            return true;
        }
        
        if (arg.contains("/") || arg.contains(".")) {
            return true;
        }
        
        return false;
    }

    public static String getMediaInfo(String filePath) {
        try {
            java.io.File file = new java.io.File(filePath);
            StringBuilder info = new StringBuilder();
            info.append("文件信息:\n");
            info.append("路径: ").append(filePath).append("\n");
            info.append("文件名: ").append(file.getName()).append("\n");
            info.append("大小: ").append(formatFileSize(file.length())).append("\n");
            info.append("最后修改: ").append(new java.util.Date(file.lastModified())).append("\n");
            info.append("\n提示: 使用FFmpeg命令行工具获取详细媒体信息");
            return info.toString();
        } catch (Exception e) {
            Log.e(TAG, "获取媒体信息失败", e);
            return "错误: " + e.getMessage() + "\n文件路径: " + filePath;
        }
    }

    public static void cancelCurrentTask() {
        stopProgressSimulation();
        if (currentSession != null) {
            // 检查状态再取消，避免异常
            SessionState state = currentSession.getState();
            if (state == SessionState.RUNNING || state == SessionState.CREATED) {
                Log.d(TAG, "Cancelling session: " + currentSession.getSessionId());
                if (logManager != null) {
                    logManager.appendFfmpegLog("取消当前FFmpeg任务", Level.AV_LOG_WARNING);
                }
                FFmpegKit.cancel(currentSession.getSessionId());
            }
            currentSession = null;
        }
        if (appContext != null) {
            NotificationHelper.cancelProgressNotification(appContext);
        }
    }

    private static String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private static void startProgressSimulation(FFmpegCallback callback) {
        if (callback == null) return;
        stopProgressSimulation();
        progressSimulator = new ProgressSimulator(callback);
        new Thread(progressSimulator).start();
    }

    private static void stopProgressSimulation() {
        if (progressSimulator != null) {
            progressSimulator.stop();
            progressSimulator = null;
        }
    }

    private static class ProgressSimulator implements Runnable {
        private volatile boolean running = true;
        private final FFmpegCallback callback;
        
        ProgressSimulator(FFmpegCallback callback) { 
            this.callback = callback; 
        }
        
        @Override public void run() {
            int progress = 0;
            long start = System.currentTimeMillis();
            try {
                while (running && progress < 95) {
                    Thread.sleep(500);
                    
                    // 更新通知进度
                    if (appContext != null && !currentFileName.isEmpty()) {
                        NotificationHelper.showProgressNotification(appContext, currentFileName, progress);
                    }
                    
                    if (currentSession == null || currentSession.getState().equals(SessionState.COMPLETED)) {
                        callback.onProgress(100, System.currentTimeMillis() - start);
                        break;
                    }
                    progress += (progress < 70 ? 5 : 2);
                    callback.onProgress(Math.min(progress, 95), System.currentTimeMillis() - start);
                }
            } catch (InterruptedException e) { 
                Thread.currentThread().interrupt(); 
            }
        }
        
        void stop() { 
            running = false; 
        }
    }

    // 同步执行简单的 FFmpeg 查询命令 (用于获取版本，编解码器等信息)
    // 要在后台线程调用，不要在主线程直接调用
    public static String executeSimpleCommand(String command) {
        try {
            com.arthenica.ffmpegkit.Session session = FFmpegKit.execute(command);
            ReturnCode returnCode = session.getReturnCode();
            
            if (ReturnCode.isSuccess(returnCode)) {
                String output = session.getOutput();
                return output != null ? output : "";
            } else {
                Log.e(TAG, "命令执行失败: " + command + ", 返回码: " + returnCode);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "执行命令异常: " + command, e);
            return null;
        }
    }
}
