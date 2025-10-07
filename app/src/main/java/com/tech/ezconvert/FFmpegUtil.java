package com.tech.ezconvert;

import android.util.Log;
import com.arthenica.mobileffmpeg.FFmpeg;
import com.arthenica.mobileffmpeg.FFprobe;
import com.arthenica.mobileffmpeg.MediaInformation;
import com.arthenica.mobileffmpeg.ExecuteCallback;
import com.arthenica.mobileffmpeg.Statistics;
import com.arthenica.mobileffmpeg.StatisticsCallback;

public class FFmpegUtil {
    private static final String TAG = "FFmpegUtil";
    private static long currentExecutionId = 0;
    
    public interface FFmpegCallback {
        void onProgress(int progress, long time);
        void onComplete(boolean success, String message);
        void onError(String error);
    }
    
    // 执行 FFmpeg 命令
    public static void executeCommand(String[] command, FFmpegCallback callback) {
        Log.d(TAG, "执行命令: " + String.join(" ", command));
        
        // 设置统计回调（如果可用）
        setupStatisticsCallback(callback);
        
        // 执行命令
        currentExecutionId = FFmpeg.executeAsync(command, new ExecuteCallback() {
            @Override
            public void apply(long executionId, int returnCode) {
                Log.d(TAG, "命令执行完成，返回码: " + returnCode);
                currentExecutionId = 0;
                
                if (callback != null) {
                    if (returnCode == 0) {
                        callback.onComplete(true, "处理完成");
                    } else {
                        callback.onComplete(false, "处理失败，返回码: " + returnCode);
                    }
                }
            }
        });
        
        // 检查执行是否立即失败
        if (currentExecutionId <= 0 && callback != null) {
            callback.onError("命令执行失败，无法启动FFmpeg进程");
        }
    }
    
    // 获取媒体信息
    public static String getMediaInfo(String filePath) {
        try {
            MediaInformation mediaInfo = FFprobe.getMediaInformation(filePath);
            if (mediaInfo != null) {
                // 构建简化的媒体信息字符串
                StringBuilder info = new StringBuilder();
                info.append("文件路径: ").append(filePath).append("\n");
                
                if (mediaInfo.getFormat() != null) {
                    info.append("格式: ").append(mediaInfo.getFormat()).append("\n");
                }
                
                if (mediaInfo.getBitrate() > 0) {
                    info.append("比特率: ").append(mediaInfo.getBitrate() / 1000).append(" kb/s\n");
                }
                
                if (mediaInfo.getDuration() != null && mediaInfo.getDuration().length() > 0) {
                    info.append("时长: ").append(mediaInfo.getDuration()).append("\n");
                }
                
                if (mediaInfo.getStreams() != null) {
                    info.append("流数量: ").append(mediaInfo.getStreams().size()).append("\n");
                    
                    // 简单统计视频和音频流
                    int videoStreams = 0;
                    int audioStreams = 0;
                    for (int i = 0; i < mediaInfo.getStreams().size(); i++) {
                        String codecType = mediaInfo.getStreams().get(i).getCodecType();
                        if ("video".equals(codecType)) {
                            videoStreams++;
                        } else if ("audio".equals(codecType)) {
                            audioStreams++;
                        }
                    }
                    info.append("视频流: ").append(videoStreams).append("\n");
                    info.append("音频流: ").append(audioStreams).append("\n");
                }
                
                return info.toString();
            } else {
                return "无法获取媒体信息";
            }
        } catch (Exception e) {
            Log.e(TAG, "获取媒体信息失败", e);
            return "错误: " + e.getMessage() + "\n文件路径: " + filePath;
        }
    }
    
    // 设置统计信息回调
    private static void setupStatisticsCallback(FFmpegCallback callback) {
        try {
            FFmpeg.enableStatisticsCallback(new StatisticsCallback() {
                @Override
                public void apply(Statistics statistics) {
                    if (callback != null && statistics != null) {
                        int progress = 0;
                        long time = statistics.getTime();
                        
                        // 简单的进度模拟
                        if (time > 0) {
                            progress = (int) Math.min((time / 10000.0) * 100, 95);
                        }
                        
                        callback.onProgress(progress, time);
                    }
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "统计回调不可用，将使用模拟进度", e);
            startSimulatedProgress(callback);
        }
    }
    
    // 模拟进度（当统计回调不可用时使用）
    private static void startSimulatedProgress(final FFmpegCallback callback) {
        if (callback == null) return;
        
        new Thread(() -> {
            try {
                for (int i = 0; i <= 100; i += 2) {
                    Thread.sleep(200);
                    callback.onProgress(i, i * 1000L);
                    
                    // 如果任务完成，提前结束
                    if (currentExecutionId == 0 && i > 50) {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
    
    // 取消当前任务
    public static void cancelCurrentTask() {
        if (currentExecutionId != 0) {
            FFmpeg.cancel(currentExecutionId);
            currentExecutionId = 0;
        }
    }
    
    // 获取 FFmpeg 版本
    public static String getVersion() {
        try {
            return "mobile-ffmpeg-full-4.4.LTS";
        } catch (Exception e) {
            Log.w(TAG, "无法获取FFmpeg版本", e);
            return "mobile-ffmpeg-full-4.4.LTS";
        }
    }
}