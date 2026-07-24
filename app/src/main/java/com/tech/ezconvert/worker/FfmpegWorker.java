package com.tech.ezconvert.worker;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.Level;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.Statistics;
import com.arthenica.ffmpegkit.StatisticsCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.tech.ezconvert.utils.CacheManager;
import com.tech.ezconvert.utils.FfmpegCommandBuilder;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.LogManager;
import com.tech.ezconvert.utils.NotificationHelper;
import com.tech.ezconvert.utils.ParameterData;

import java.io.File;

/**
 * FFmpeg 后台 Worker
 * 通过 WorkManager 在后台执行 FFmpeg 转码任务，确保应用切后台或进程被杀后任务仍能完成
 */
public class FfmpegWorker extends ListenableWorker {
    public static final String TAG = "FfmpegWorker";

    // InputData keys
    public static final String KEY_INPUT_PATH = "input_path";
    public static final String KEY_INPUT_URI = "input_uri";
    public static final String KEY_OUTPUT_PATH_BASE = "output_path_base";
    public static final String KEY_PARAMS_JSON = "params_json";
    public static final String KEY_FILE_NAME = "file_name";
    public static final String KEY_TASK_INDEX = "task_index";
    public static final String KEY_TOTAL_TASKS = "total_tasks";

    // Progress keys
    public static final String KEY_PROGRESS = "progress";
    public static final String KEY_TIME = "time";
    public static final String KEY_STATUS = "status";

    // Output keys
    public static final String KEY_OUTPUT_PATH = "output_path";
    public static final String KEY_ERROR_MESSAGE = "error_message";

    private static final int NOTIFICATION_ID = 1001;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isCancelled = false;
    private FFmpegSession currentSession = null;
    private long totalDurationMs = -1;
    private final Gson gson = new Gson();

    public FfmpegWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        return CallbackToFutureAdapter.getFuture(completer -> {
            Context context = getApplicationContext();
            Data inputData = getInputData();

            String inputPath = inputData.getString(KEY_INPUT_PATH);
            String inputUriString = inputData.getString(KEY_INPUT_URI);
            Uri inputUri = (inputUriString != null && !inputUriString.isEmpty()) ? Uri.parse(inputUriString) : null;
            String outputPathBase = inputData.getString(KEY_OUTPUT_PATH_BASE);
            String paramsJson = inputData.getString(KEY_PARAMS_JSON);
            String fileName = inputData.getString(KEY_FILE_NAME);
            int taskIndex = inputData.getInt(KEY_TASK_INDEX, 1);
            int totalTasks = inputData.getInt(KEY_TOTAL_TASKS, 1);

            if ((inputPath == null && inputUri == null) || outputPathBase == null || paramsJson == null) {
                Log.e(TAG, "Worker 参数缺失");
                completer.set(Result.failure(new Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "Worker 参数缺失")
                        .build()));
                return "ffmpeg-work";
            }

            ParameterData params = gson.fromJson(paramsJson, ParameterData.class);
            String outputPath = FfmpegCommandBuilder.buildOutputPath(outputPathBase, params);

            // 记录任务开始标记
            String workIdStr = getId().toString();
            LogManager.getInstance(context).appendFfmpegLog(
                    "=== Task [" + workIdStr + "] START | " + fileName
                            + " (" + taskIndex + "/" + totalTasks + ") ===",
                    Level.AV_LOG_INFO
            );

            // 准备缓存文件
            CacheManager.AccessResult accessResult = CacheManager.prepareFileForProcessing(context, inputPath, inputUri);
            if (accessResult == null) {
                LogManager.getInstance(context).appendFfmpegLog(
                        "=== Task [" + workIdStr + "] END (FAILED: 无法访问输入文件) ===",
                        Level.AV_LOG_ERROR
                );
                completer.set(Result.failure(new Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "无法访问输入文件")
                        .build()));
                return "ffmpeg-work";
            }

            final String usablePath = accessResult.usablePath;
            final boolean isFromCache = accessResult.isFromCache;

            // 生成命令
            String[] command = FfmpegCommandBuilder.buildCommand(usablePath, outputPath, params, context);
            String commandString = buildCommandString(command);
            Log.d(TAG, "Worker 执行命令: " + commandString);

            // 获取视频时长用于进度计算
            getVideoDuration(usablePath, durationMs -> {
                totalDurationMs = durationMs;

                // 设置前台通知（Worker 必须持有前台通知，否则系统可能终止任务）
                // Android 14+ 必须指定 foregroundServiceType
                Notification notification = NotificationHelper.buildProgressNotification(context, fileName, 0);
                ForegroundInfo foregroundInfo;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    foregroundInfo = new ForegroundInfo(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    );
                } else {
                    foregroundInfo = new ForegroundInfo(NOTIFICATION_ID, notification);
                }
                setForegroundAsync(foregroundInfo);

                // 执行 FFmpeg
                executeFfmpeg(commandString, fileName, outputPath, usablePath, isFromCache, workIdStr, completer);
            });

            return "ffmpeg-work";
        });
    }

    private void executeFfmpeg(String commandString, String fileName, String outputPath,
                               String usablePath, boolean isFromCache, String workIdStr,
                               CallbackToFutureAdapter.Completer<Result> completer) {

        Context context = getApplicationContext();

        currentSession = FFmpegKit.executeAsync(commandString, new FFmpegSessionCompleteCallback() {
            @Override
            public void apply(FFmpegSession session) {
                ReturnCode returnCode = session.getReturnCode();

                // 清理缓存文件
                if (isFromCache) {
                    CacheManager.releaseCacheFile(usablePath);
                }
                // 清理临时共享文件
                if (usablePath.contains("/shared_files/")) {
                    deleteTempFile(usablePath);
                }

                if (isCancelled) {
                    LogManager.getInstance(context).appendFfmpegLog(
                            "=== Task [" + workIdStr + "] END (CANCELLED) ===",
                            Level.AV_LOG_WARNING
                    );
                    completer.set(Result.failure(new Data.Builder()
                            .putString(KEY_ERROR_MESSAGE, "操作已取消")
                            .build()));
                    return;
                }

                if (ReturnCode.isSuccess(returnCode)) {
                    LogManager.getInstance(context).appendFfmpegLog(
                            "=== Task [" + workIdStr + "] END (SUCCESS) ===",
                            Level.AV_LOG_INFO
                    );
                    completer.set(Result.success(new Data.Builder()
                            .putString(KEY_OUTPUT_PATH, outputPath)
                            .build()));
                } else {
                    String errorMessage = "处理失败";
                    if (session.getFailStackTrace() != null) {
                        errorMessage += ": " + session.getFailStackTrace();
                    } else if (returnCode != null) {
                        errorMessage += "，返回码: " + returnCode.getValue();
                    }
                    LogManager.getInstance(context).appendFfmpegLog(
                            "=== Task [" + workIdStr + "] END (FAILED: " + errorMessage + ") ===",
                            Level.AV_LOG_ERROR
                    );
                    completer.set(Result.failure(new Data.Builder()
                            .putString(KEY_ERROR_MESSAGE, errorMessage)
                            .build()));
                }
            }
        }, null, new StatisticsCallback() {
            @Override
            public void apply(Statistics statistics) {
                if (isCancelled) return;

                int timeInMs = (int) statistics.getTime();
                int progress = 0;

                if (totalDurationMs > 0) {
                    progress = (int) ((timeInMs * 100.0) / totalDurationMs);
                    progress = Math.min(100, Math.max(0, progress));
                }

                // 通过 WorkManager 进度机制上报
                Data progressData = new Data.Builder()
                        .putInt(KEY_PROGRESS, progress)
                        .putLong(KEY_TIME, timeInMs)
                        .putString(KEY_STATUS, "RUNNING")
                        .build();
                setProgressAsync(progressData);

                // 更新前台通知进度
                Notification notification = NotificationHelper.buildProgressNotification(
                        context, fileName, progress);
                ForegroundInfo foregroundInfo;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    foregroundInfo = new ForegroundInfo(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    );
                } else {
                    foregroundInfo = new ForegroundInfo(NOTIFICATION_ID, notification);
                }
                setForegroundAsync(foregroundInfo);
            }
        });
    }

    private void getVideoDuration(String inputPath, DurationCallback callback) {
        if (inputPath == null || inputPath.isEmpty()) {
            callback.onDuration(-1);
            return;
        }

        String probeCmd = "-v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 \"" + inputPath + "\"";
        FFprobeKit.executeAsync(probeCmd, session -> {
            if (ReturnCode.isSuccess(session.getReturnCode())) {
                String output = session.getOutput();
                if (output != null && !output.trim().isEmpty()) {
                    try {
                        double seconds = Double.parseDouble(output.trim());
                        long durationMs = (long) (seconds * 1000);
                        Log.d(TAG, "视频总时长: " + durationMs + "ms");
                        mainHandler.post(() -> callback.onDuration(durationMs));
                        return;
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "解析时长失败: " + output);
                    }
                }
            }
            mainHandler.post(() -> callback.onDuration(-1));
        });
    }

    private interface DurationCallback {
        void onDuration(long durationMs);
    }

    private String buildCommandString(String[] command) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < command.length; i++) {
            String arg = command[i];
            if (i > 0 && "-i".equals(command[i - 1])) {
                sb.append("\"").append(arg).append("\"");
            } else if (i == command.length - 1) {
                sb.append("\"").append(arg).append("\"");
            } else if (arg.contains("/") || arg.contains(".")) {
                sb.append("\"").append(arg).append("\"");
            } else {
                sb.append(arg);
            }
            if (i < command.length - 1) sb.append(" ");
        }
        return sb.toString();
    }

    private void deleteTempFile(String path) {
        try {
            File file = new File(path);
            if (file.exists() && file.delete()) {
                Log.d(TAG, "已删除临时文件: " + path);
            }
        } catch (Exception e) {
            Log.e(TAG, "删除临时文件失败: " + path, e);
        }
    }

    @Override
    public void onStopped() {
        super.onStopped();
        isCancelled = true;
        if (currentSession != null) {
            FFmpegKit.cancel(currentSession.getSessionId());
            Log.d(TAG, "Worker 被取消，终止 FFmpeg 会话: " + currentSession.getSessionId());
        }
    }
}
