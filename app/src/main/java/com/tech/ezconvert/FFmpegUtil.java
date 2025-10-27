package com.tech.ezconvert;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback;
import com.arthenica.ffmpegkit.LogCallback;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.FFmpegKitConfig;

public class FFmpegUtil {

    private static final String TAG = "FFmpegUtil";
    private static FFmpegSession currentSession = null;
    private static ProgressSimulator progressSimulator;

    public interface FFmpegCallback {
        void onProgress(int progress, long time);
        void onComplete(boolean success, String message);
        void onError(String error);
    }

    public static void initLogging(Context context) {
        SharedPreferences sp = context.getSharedPreferences("debug_settings", Context.MODE_PRIVATE);
        boolean verbose = sp.getBoolean("log_verbose", false);
        
        // FFmpegKit 的日志级别设置
        if (verbose) {
            FFmpegKitConfig.enableLogCallback(new LogCallback() {
                @Override
                public void apply(com.arthenica.ffmpegkit.Log log) {
                    String line = log.getMessage();
                    Log.d("FFmpegLog", line);
                    LogViewerActivity.appendLog(line);
                }
            });
        } else {
            // 禁用详细日志
            FFmpegKitConfig.enableLogCallback(null);
        }
    }

    public static void executeCommand(String[] command, FFmpegCallback callback) {
        Log.d(TAG, "执行命令: " + String.join(" ", command));
        stopProgressSimulation();

        String commandString = String.join(" ", command);
        
        currentSession = FFmpegKit.executeAsync(commandString, new FFmpegSessionCompleteCallback() {
            @Override
            public void apply(FFmpegSession session) {
                ReturnCode returnCode = session.getReturnCode();
                Log.d(TAG, "命令执行完成，返回码: " + (returnCode != null ? returnCode.getValue() : "null"));
                
                stopProgressSimulation();
                if (callback != null) {
                    if (ReturnCode.isSuccess(returnCode)) {
                        callback.onComplete(true, "处理完成");
                    } else {
                        String errorMessage = "处理失败";
                        if (session.getFailStackTrace() != null) {
                            errorMessage += ": " + session.getFailStackTrace();
                        } else if (returnCode != null) {
                            errorMessage += "，返回码: " + returnCode.getValue();
                        }
                        callback.onComplete(false, errorMessage);
                    }
                }
                currentSession = null;
            }
        });

        startProgressSimulation(callback);
        if (currentSession == null && callback != null) {
            callback.onError("命令执行失败，无法启动FFmpeg进程");
        }
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
            FFmpegKit.cancel(currentSession.getSessionId());
            currentSession = null;
        }
    }

    public static String getVersion() {
        try {
            // FFmpegKit 6.0 版本
            return "6.0-2";
        } catch (Exception e) {
            Log.w(TAG, "获取FFmpeg版本失败", e);
            return "未知版本";
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
        ProgressSimulator(FFmpegCallback callback) { this.callback = callback; }
        @Override public void run() {
            int progress = 0;
            long start = System.currentTimeMillis();
            try {
                while (running && progress < 95) {
                    Thread.sleep(500);
                    if (currentSession == null || currentSession.getState().equals(com.arthenica.ffmpegkit.SessionState.COMPLETED)) {
                        callback.onProgress(100, System.currentTimeMillis() - start);
                        break;
                    }
                    progress += (progress < 70 ? 5 : 2);
                    callback.onProgress(Math.min(progress, 95), System.currentTimeMillis() - start);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        void stop() { running = false; }
    }
}