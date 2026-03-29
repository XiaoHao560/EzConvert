package com.tech.ezconvert.utils;

import com.tech.ezconvert.utils.Log;
import android.app.Notification;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.Level;
import com.arthenica.ffmpegkit.LogCallback;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.Session;
import com.arthenica.ffmpegkit.SessionState;
import com.arthenica.ffmpegkit.Statistics;
import com.arthenica.ffmpegkit.StatisticsCallback;

import java.io.File;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ConcurrentHashMap;

public class FFmpegUtil {

    private static final String TAG = "FFmpegUtil";
    private static FFmpegSession currentSession = null;
    private static LogManager logManager;
    private static Context appContext;
    private static String currentFileName = "";
    private static volatile boolean isQueryCommand = false;
    
    // 资源清理机制
    private static final ReferenceQueue<FFmpegSession> refQueue = new ReferenceQueue<>(); 
    private static final CopyOnWriteArraySet<SessionPhantomRef> pendingRefs = new CopyOnWriteArraySet<>();
    private static int lastNotificationProgress = -1;
    private static long lastNotificationTime = 0;
    
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
    
    // 存储每个会话的总时长（毫秒）
    private static final ConcurrentHashMap<Long, Long> sessionDurations = new ConcurrentHashMap<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        
        // 启用统计回调获取实时进度
        FFmpegKitConfig.enableStatisticsCallback(new StatisticsCallback() {
            @Override
            public void apply(Statistics statistics) {
                long sessionId = statistics.getSessionId();
                final int timeInMs = (int) statistics.getTime(); // 已处理的毫秒数
                
                // 获取该会话的总时长
                Long totalDuration = sessionDurations.get(sessionId);
                if (totalDuration != null && totalDuration > 0) {
                    final int progress = (int) ((timeInMs * 100.0) / totalDuration);
                    final int clampedProgress = Math.min(100, Math.max(0, progress)); // 限制 0-100
                    
                    // 回调到 UI（主线程）
                    mainHandler.post(() -> {
                        FFmpegCallback callback = getCallbackForSession(sessionId);
                        if (callback != null) {
                            callback.onProgress(clampedProgress, timeInMs);
                        }
                    });
                    
                    // 更新通知 (限制频率: 每 5% 或者每 2 秒更新一次，避免卡顿)
                    long currentTime = System.currentTimeMillis();
                    if (appContext != null &&
                        (Math.abs(clampedProgress - lastNotificationProgress) >= 5 ||
                         currentTime - lastNotificationTime >= 2000 ||
                          clampedProgress == 100)) {
                              
                        lastNotificationProgress = clampedProgress;
                        lastNotificationTime = currentTime;
                        
                        NotificationHelper.showProgressNotification(appContext, currentFileName, clampedProgress);
                    }
                }
            }
        });
    }

    // 根据当前日志设置判断是否应该记录此日志
    private static boolean shouldLog(Level level, boolean verboseLogging) {
        // 如果是查询命令，则跳过所有 ERROR 级别的日志
        if (isQueryCommand && level == Level.AV_LOG_ERROR) {
            return false;
        }
        
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
        // 重置通知进度记录
        lastNotificationProgress = -1;
        lastNotificationTime = 0;
        currentFileName = fileName != null ? fileName : "未知文件";
        
        // 创建通知渠道（首次执行时）
        if (appContext != null) {
            NotificationHelper.createNotificationChannels(appContext);
        }
        
        Log.d(TAG, "执行命令: " + String.join(" ", command));
        
        // 先获取输入文件的总时长（用于计算进度百分比）
        getVideoDuration(tempInputPath, new DurationCallback() {
            @Override
            public void onDurationRetrieved(long durationMs) {
                // 执行 FFmpeg 命令
                runFfmpegCommand(command, callback, tempInputPath, durationMs);
            }
            
            @Override
            public void onError() {
                // 获取时长失败，仍执行命令但使用文件大小估算
                Log.w(TAG, "无法获取视频时长，将使用文件大小估算进度");
                runFfmpegCommandWithFileSize(command, callback, tempInputPath);
            }
        });
    }
    
    // 存储回调
    private static FFmpegCallback currentCallback;
    private static long currentSessionId = -1;
    
    private static FFmpegCallback getCallbackForSession(long sessionId) {
        if (sessionId == currentSessionId) {
            return currentCallback;
        }
        return null;
    }

    private interface DurationCallback {
        void onDurationRetrieved(long durationMs);
        void onError();
    }
    
    // 使用 FFprobe 获取视频总时长
    private static void getVideoDuration(String inputPath, DurationCallback callback) {
        if (inputPath == null || inputPath.isEmpty()) {
            callback.onError();
            return;
        }
        
        // 使用 FFprobe 获取时长（秒）
        String probeCmd = "-v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 \"" + inputPath + "\"";
        
        FFprobeKit.executeAsync(probeCmd, session -> {
            if (ReturnCode.isSuccess(session.getReturnCode())) {
                String output = session.getOutput();
                if (output != null && !output.trim().isEmpty()) {
                    try {
                        double seconds = Double.parseDouble(output.trim());
                        long durationMs = (long) (seconds * 1000);
                        Log.d(TAG, "视频总时长: " + durationMs + "ms");
                        callback.onDurationRetrieved(durationMs);
                        return;
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "解析时长失败: " + output);
                    }
                }
            }
            callback.onError();
        });
    }

    private static void runFfmpegCommand(String[] command, FFmpegCallback callback, 
                                        String tempInputPath, long durationMs) {
        currentCallback = callback;
        
        // 构建命令字符串
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

        // 执行命令
        currentSession = FFmpegKit.executeAsync(commandString, new FFmpegSessionCompleteCallback() {
            @Override
            public void apply(FFmpegSession session) {
                ReturnCode returnCode = session.getReturnCode();
                Log.d(TAG, "命令执行完成，返回码: " + (returnCode != null ? returnCode.getValue() : "null"));
                
                // 清理存储的时长
                sessionDurations.remove(session.getSessionId());
                
                // 清理临时文件
                cleanupTempFiles(tempInputPath);
                
                // 转换完成后删除临时文件
                if (tempInputPath != null && tempInputPath.contains("/shared_files/")) {
                    deleteTempFile(tempInputPath);
                }
                
                if (callback != null) {
                    mainHandler.post(() -> {
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
                    });
                }
                
                // 立即清理，不依赖 finalize
                currentSession = null;
                currentFileName = "";
                currentCallback = null;
                currentSessionId = -1;
                // 主动清理所有已完成会话
                FFmpegKitConfig.clearSessions();
            }
        }, 
        // Log callback (已经全局设置，这里传 null 即可)
        null,
        // Statistics callback (这里会触发 enableStatisticsCallback 设置的回调)
        statistics -> {
            // 这个 lambda 是必需的，但实际逻辑在 enableStatisticsCallback 中处理
        });
        
        // 注册 PhantomReference 防止内存泄漏
        if (currentSession != null) {
            new SessionPhantomRef(currentSession);
            currentSessionId = currentSession.getSessionId();
            // 存储该会话的总时长，供 statistics callback 使用
            if (durationMs > 0) {
                sessionDurations.put(currentSessionId, durationMs);
            }
            Log.d(TAG, "会话 " + currentSessionId + " 时长: " + durationMs + "ms");
        } else {
            // 启动失败也清理
            if (tempInputPath != null && tempInputPath.contains("/shared_files/")) {
                deleteTempFile(tempInputPath);
            }
            if (callback != null) {
                mainHandler.post(() -> callback.onError("命令执行失败，无法启动FFmpeg进程"));
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
    
    private static void cleanupTempFiles(String tempInputPath) {
        if (tempInputPath == null) return;
        
        // 清理 FileUtils 创建的 shared_files 缓存
        if (tempInputPath.contains("/shared_files/")) {
            deleteTempFile(tempInputPath);
        }
    }

    // 当无法获取时长时，使用文件大小估算进度条
    private static void runFfmpegCommandWithFileSize(String[] command, FFmpegCallback callback, 
                                                    String tempInputPath) {
        File inputFile = new File(tempInputPath);
        final long totalSize = inputFile.length();
        final int[] lastProgress = {0}; // 记录上次进度，避免重复回调
        
        // 先执行命令 (durationMs 传 -1 表示使用文件大小估算)
        runFfmpegCommand(command, callback, tempInputPath, -1);
        
        // 获取输出文件路径
        String outputPath = command[command.length - 1].replace("\"", "");
        final File outputFile = new File(outputPath);
        
        // 创建进度检查定时器
        final Handler progressHandler = new Handler(Looper.getMainLooper());
        final Runnable checkProgress = new Runnable() {
            @Override
            public void run() {
                // 检查当前会话是否还在运行
                if (currentSession == null) {
                    return; // 会话结束，停止检查
                }
                
                SessionState state = currentSession.getState();
                if (state != SessionState.RUNNING && state != SessionState.CREATED) {
                    return; // 会话已完成或者失败，停止检查
                }
                
                // 检查输出文件大小
                if (outputFile.exists()) {
                    long currentSize = outputFile.length();
                    
                    // 计算进度: 基于输出文件大小与输入文件大小的比例
                    // 对于压缩任务，输出可能小于输入; 对于转封装，输出接近输入
                    int progress;
                    if (currentSize >= totalSize) {
                        progress = 95; // 文件大小超过输入，则显示 95% 等待完成
                    } else {
                        progress = (int) ((currentSize * 95.0) / totalSize);
                    }
                    
                    // 确保进度条单调递增且不超过 95%
                    progress = Math.max(lastProgress[0], Math.min(95, progress));
                    
                    if (progress > lastProgress[0]) {
                        lastProgress[0] = progress;
                        FFmpegCallback cb = getCallbackForSession(currentSession.getSessionId());
                        if (cb != null) {
                            cb.onProgress(progress, 0); // time 传 0 表示未知时长
                            
                            // 更新通知进度
                            if (appContext != null && !currentFileName.isEmpty()) {
                                NotificationHelper.showProgressNotification(appContext, currentFileName, progress);
                            }
                        }
                    }
                }
                
                // 继续下一次检查 (每 500ms)
                progressHandler.postDelayed(this, 500);
            }
        };
        
        // 延迟启动检查 (给 FFmpeg 一点启动时间创建输出文件)
        progressHandler.postDelayed(checkProgress, 500);
        
        // 当 onComplete 被调用时， currentSession 会被设为 null，定时器自动停止
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
        if (currentSession != null) {
            // 检查状态再取消，避免异常
            SessionState state = currentSession.getState();
            if (state == SessionState.RUNNING || state == SessionState.CREATED) {
                Log.d(TAG, "Cancelling session: " + currentSession.getSessionId());
                if (logManager != null) {
                    logManager.appendFfmpegLog("取消当前FFmpeg任务", Level.AV_LOG_WARNING);
                }
                FFmpegKit.cancel(currentSession.getSessionId());
                sessionDurations.remove(currentSession.getSessionId());
            }
            currentSession = null;
            currentCallback = null;
            currentSessionId = -1;
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
    // 同步执行简单的 FFmpeg 查询命令 (用于获取版本，编解码器等信息) 
    // 要在后台线程调用，不要在主线程直接调用
    public static String executeSimpleCommand(String command) {
        isQueryCommand = true; // 标记为查询命令
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
        } finally {
            isQueryCommand = false; // 删除查询命令标志
        }
    }
}
