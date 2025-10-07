package com.tech.ezconvert;

import android.util.Log;
import com.arthenica.mobileffmpeg.FFmpeg;
import com.arthenica.mobileffmpeg.FFprobe;
import com.arthenica.mobileffmpeg.FFtask;
import com.arthenica.mobileffmpeg.ExecuteCallback;
import com.arthenica.mobileffmpeg.Statistics;
import com.arthenica.mobileffmpeg.StatisticsCallback;

public class FFmpegUtil {
    private static final String TAG = "FFmpegUtil";
    private static FFtask currentTask;
    
    public interface FFmpegCallback {
        void onProgress(int progress, long time);
        void onComplete(boolean success, String message);
        void onError(String error);
    }
    
    // 执行 FFmpeg 命令
    public static FFtask executeCommand(String[] command, FFmpegCallback callback) {
        Log.d(TAG, "执行命令: " + String.join(" ", command));
        
        currentTask = FFmpeg.executeAsync(command, new ExecuteCallback() {
            @Override
            public void apply(long executionId, int returnCode) {
                Log.d(TAG, "命令执行完成，返回码: " + returnCode);
                if (callback != null) {
                    if (returnCode == 0) {
                        callback.onComplete(true, "处理完成");
                    } else {
                        callback.onComplete(false, "处理失败，返回码: " + returnCode);
                    }
                }
                currentTask = null;
            }
        });
        
        setupStatisticsCallback(callback);
        return currentTask;
    }
    
    // 获取媒体信息
    public static String getMediaInfo(String filePath) {
        try {
            String[] command = {
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                filePath
            };
            
            return FFprobe.execute(command);
        } catch (Exception e) {
            Log.e(TAG, "获取媒体信息失败", e);
            return "错误: " + e.getMessage();
        }
    }
    
    // 设置统计信息回调
    private static void setupStatisticsCallback(FFmpegCallback callback) {
        FFmpeg.enableStatisticsCallback(new StatisticsCallback() {
            @Override
            public void apply(Statistics statistics) {
                if (callback != null && statistics != null) {
                    int progress = 0;
                    if (statistics.getDuration() > 0) {
                        progress = (int) ((statistics.getTime() * 100) / statistics.getDuration());
                        progress = Math.min(progress, 100); // 确保不超过100%
                    }
                    callback.onProgress(progress, statistics.getTime());
                }
            }
        });
    }
    
    // 取消当前任务
    public static void cancelCurrentTask() {
        if (currentTask != null && !currentTask.isCompleted()) {
            FFmpeg.cancel(currentTask.getExecutionId());
            currentTask = null;
        }
    }
    
    // 获取 FFmpeg 版本
    public static String getVersion() {
        return FFmpeg.getVersion();
    }
}