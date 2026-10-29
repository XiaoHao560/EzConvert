package com.tech.ezconvert.worker;

import android.app.Notification;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
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
import com.tech.ezconvert.manager.ConversionQueueManager;
import com.tech.ezconvert.manager.OutputPathManager;
import com.tech.ezconvert.utils.CacheManager;
import com.tech.ezconvert.utils.ConfigManager;
import com.tech.ezconvert.utils.FileUtils;
import com.tech.ezconvert.utils.FfmpegCommandBuilder;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.LogManager;
import com.tech.ezconvert.utils.NotificationHelper;
import com.tech.ezconvert.utils.ParameterData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    public static final String KEY_OUTPUT_TREE_URI = "output_tree_uri";
    public static final String KEY_PARAMS_JSON = "params_json";
    public static final String KEY_FILE_NAME = "file_name";
    public static final String KEY_SESSION_ID = "session_id";
    public static final String KEY_TASK_INDEX = "task_index";
    public static final String KEY_TOTAL_TASKS = "total_tasks";
    public static final String KEY_SYNC_BATCH = "sync_batch";

    // Progress keys
    public static final String KEY_PROGRESS = "progress";
    public static final String KEY_TIME = "time";
    public static final String KEY_STATUS = "status";

    // Output keys
    public static final String KEY_OUTPUT_PATH = "output_path";
    public static final String KEY_ERROR_MESSAGE = "error_message";

    private static final int NOTIFICATION_ID = 1001;
    // 前台通知切换和文件准备均放到后台线程，避免阻塞 ListenableWorker 的启动线程
    private static final ExecutorService WORK_EXECUTOR = Executors.newCachedThreadPool();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // 同步取消请求与 FFmpeg 会话创建，避免 onStopped 在 session 赋值前错过正在启动的会话
    private final Object sessionLock = new Object();
    private volatile boolean isCancelled = false;
    private FFmpegSession currentSession = null;
    private volatile long totalDurationMs = -1;
    private volatile int activeTaskIndex = 1;
    private volatile int activeTaskTotal = 1;
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
            Uri inputUri = (inputUriString != null && !inputUriString.isEmpty())
                    ? Uri.parse(inputUriString) : null;
            String outputPathBase = inputData.getString(KEY_OUTPUT_PATH_BASE);
            String outputTreeUriString = inputData.getString(KEY_OUTPUT_TREE_URI);
            String paramsJson = inputData.getString(KEY_PARAMS_JSON);
            String suppliedFileName = inputData.getString(KEY_FILE_NAME);
            String fileName = suppliedFileName == null || suppliedFileName.trim().isEmpty()
                    ? context.getString(com.tech.ezconvert.R.string.notification_file_placeholder)
                    : suppliedFileName;
            int taskIndex = inputData.getInt(KEY_TASK_INDEX, 1);
            int totalTasks = inputData.getInt(KEY_TOTAL_TASKS, 1);
            boolean syncBatch = inputData.getBoolean(KEY_SYNC_BATCH, false);
            ConversionQueueManager batchQueue = syncBatch ? new ConversionQueueManager(context) : null;

            if ((inputPath == null && inputUri == null) || outputPathBase == null || paramsJson == null) {
                Log.e(TAG, "Worker 参数缺失");
                completer.set(Result.failure(new Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "Worker 参数缺失")
                        .putInt(KEY_TASK_INDEX, taskIndex)
                        .putBoolean(KEY_SYNC_BATCH, syncBatch)
                        .build()));
                return "ffmpeg-work";
            }

            String initialNotificationFileName = syncBatch
                    ? getBatchFileName(context, batchQueue) : fileName;
            Notification initialNotification = NotificationHelper.buildProgressNotification(
                    context, initialNotificationFileName, 0, true);

            ListenableFuture<Void> foregroundFuture;
            try {
                foregroundFuture = setForegroundAsync(createForegroundInfo(initialNotification));
            } catch (Exception e) {
                if (isForegroundStartNotAllowed(e)) {
                    Log.w(TAG, "系统限制了后台前台服务启动，将继续执行普通后台任务", e);
                    postProgressNotification(context, initialNotification);
                    WORK_EXECUTOR.execute(() -> beginTaskFlow(context, syncBatch, batchQueue,
                            inputPath, inputUri, outputPathBase, outputTreeUriString, paramsJson,
                            fileName, taskIndex, totalTasks, completer));
                } else {
                    Log.e(TAG, "无法请求建立前台进度通知", e);
                    completeForegroundStartFailure(context, taskIndex, fileName, syncBatch, completer);
                }
                return "ffmpeg-work";
            }

            final String taskFileName = fileName;
            final String taskOutputBase = outputPathBase;
            final String taskOutputTreeUri = outputTreeUriString;
            final String taskParamsJson = paramsJson;
            foregroundFuture.addListener(() -> {
                try {
                    // 监听器运行时前台切换 Future 已结束，不会在启动线程上等待服务启动
                    foregroundFuture.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.e(TAG, "等待前台通知建立时被中断", e);
                    completeForegroundStartFailure(context, taskIndex, taskFileName, syncBatch, completer);
                    return;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (isForegroundStartNotAllowed(cause)) {
                        // Android 12+ 不允许从后台重新启动前台服务时，不能直接判失败
                        // 同步队列会在同一个 Worker 内继续执行，避免每个文件重复启动前台服务
                        Log.w(TAG, "系统限制了后台前台服务启动，将继续执行普通后台任务", cause);
                        postProgressNotification(context, initialNotification);
                        beginTaskFlow(context, syncBatch, batchQueue, inputPath, inputUri,
                                taskOutputBase, taskOutputTreeUri, taskParamsJson, taskFileName,
                                taskIndex, totalTasks, completer);
                    } else {
                        Log.e(TAG, "无法建立前台进度通知", cause);
                        completeForegroundStartFailure(context, taskIndex, taskFileName, syncBatch, completer);
                    }
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "建立前台进度通知时发生异常", e);
                    completeForegroundStartFailure(context, taskIndex, taskFileName, syncBatch, completer);
                    return;
                }

                // WorkManager 负责建立前台服务，后续通知内容使用 NotificationManager 直接更新
                postProgressNotification(context, initialNotification);
                beginTaskFlow(context, syncBatch, batchQueue, inputPath, inputUri,
                        taskOutputBase, taskOutputTreeUri, taskParamsJson, taskFileName,
                        taskIndex, totalTasks, completer);
            }, WORK_EXECUTOR);

            return "ffmpeg-work";
        });
    }

    private void beginTaskFlow(Context context, boolean syncBatch, ConversionQueueManager batchQueue,
                               String inputPath, Uri inputUri, String outputPathBase,
                               String outputTreeUriString, String paramsJson, String fileName,
                               int taskIndex, int totalTasks,
                               CallbackToFutureAdapter.Completer<Result> completer) {
        if (syncBatch) {
            runNextSyncBatchTask(context, batchQueue, outputPathBase, outputTreeUriString,
                    taskIndex, completer);
            return;
        }

        try {
            prepareAndRun(context, inputPath, inputUri, outputPathBase, outputTreeUriString,
                    paramsJson, fileName, taskIndex, totalTasks, result -> completer.set(result));
        } catch (Exception e) {
            Log.e(TAG, "准备 FFmpeg 任务失败", e);
            String message = e.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = context.getString(com.tech.ezconvert.R.string.error_unknown);
            }
            completer.set(Result.failure(new Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, message)
                    .putString(KEY_FILE_NAME, fileName)
                    .putInt(KEY_TASK_INDEX, taskIndex)
                    .build()));
        }
    }

    private boolean isForegroundStartNotAllowed(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && "android.app.ForegroundServiceStartNotAllowedException".equals(
                    current.getClass().getName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String getBatchFileName(Context context, ConversionQueueManager queue) {
        if (queue == null || queue.getCurrentIndex() >= queue.size()) {
            return context.getString(com.tech.ezconvert.R.string.notification_file_placeholder);
        }
        String key = queue.getCurrentKey();
        Uri uri = queue.getCurrentUri();
        String name = uri != null ? FileUtils.getDisplayName(context, uri) : new File(key).getName();
        return name == null || name.trim().isEmpty()
                ? context.getString(com.tech.ezconvert.R.string.notification_file_placeholder) : name;
    }

    private String getOutputTreeUri(Context context, String outputModeOverride) {
        ConfigManager config = ConfigManager.getInstance(context);
        String mode = outputModeOverride != null
                ? outputModeOverride : config.getOutputPathMode();
        if (!ConfigManager.OUTPUT_PATH_CUSTOM.equals(mode)) return "";
        String uri = config.getCustomOutputUri();
        return uri == null ? "" : uri;
    }

    /** 在同一个前台 Worker 中依次执行同步参数队列，避免后台逐项启动前台服务被系统拒绝 */
    private void runNextSyncBatchTask(Context context, ConversionQueueManager queue,
                                      String initialOutputPathBase, String initialOutputTreeUri,
                                      int initialTaskIndex,
                                      CallbackToFutureAdapter.Completer<Result> completer) {
        if (queue == null || queue.getSyncParams() == null) {
            String message = context.getString(com.tech.ezconvert.R.string.error_unknown);
            completer.set(Result.failure(new Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, message)
                    .putInt(KEY_TASK_INDEX, queue == null ? 1 : queue.getCurrentPosition())
                    .putBoolean(KEY_SYNC_BATCH, true)
                    .build()));
            return;
        }
        if (isCancelled || isStopped()) {
            completer.set(Result.failure(new Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, context.getString(com.tech.ezconvert.R.string.error_cancelled))
                    .putInt(KEY_TASK_INDEX, queue.getCurrentPosition())
                    .putBoolean(KEY_SYNC_BATCH, true)
                    .build()));
            return;
        }

        int currentIndex = queue.getCurrentIndex();
        int taskIndex = currentIndex + 1;
        int totalTasks = queue.size();
        if (currentIndex >= totalTasks) {
            List<String> outputs = new ArrayList<>(queue.getCompletedOutputFiles());
            String lastOutput = outputs.isEmpty() ? null : outputs.get(outputs.size() - 1);
            Data.Builder output = new Data.Builder()
                    .putBoolean(KEY_SYNC_BATCH, true)
                    .putInt(KEY_TASK_INDEX, totalTasks)
                    .putInt(KEY_TOTAL_TASKS, totalTasks);
            if (lastOutput != null) output.putString(KEY_OUTPUT_PATH, lastOutput);
            completer.set(Result.success(output.build()));
            return;
        }

        String inputKey = queue.getCurrentKey();
        Uri inputUri = queue.getCurrentUri();
        ParameterData params = queue.getSyncParams();
        String fileName = getBatchFileName(context, queue);
        String outputModeOverride = queue.getEffectiveOutputModeOverride();

        String outputPathBase;
        String outputTreeUriString;
        if (taskIndex == initialTaskIndex && initialOutputPathBase != null) {
            outputPathBase = initialOutputPathBase;
            outputTreeUriString = initialOutputTreeUri == null ? "" : initialOutputTreeUri;
        } else {
            OutputPathManager outputPathManager = new OutputPathManager(context);
            outputPathBase = outputModeOverride != null && !outputModeOverride.isEmpty()
                    ? outputPathManager.generateBasePathForMode(inputKey, inputUri,
                    params.taskType, outputModeOverride)
                    : outputPathManager.generateBasePath(inputKey, inputUri, params.taskType);
            outputTreeUriString = getOutputTreeUri(context, outputModeOverride);
        }

        activeTaskIndex = taskIndex;
        activeTaskTotal = totalTasks;
        updateProgressNotification(context, fileName, 0, true);
        Data progress = new Data.Builder()
                .putInt(KEY_PROGRESS, 0)
                .putLong(KEY_TIME, 0L)
                .putInt(KEY_TASK_INDEX, taskIndex)
                .putInt(KEY_TOTAL_TASKS, totalTasks)
                .putString(KEY_STATUS, "RUNNING")
                .build();
        setProgressAsync(progress);

        final String taskOutputPathBase = outputPathBase;
        final String taskOutputTreeUri = outputTreeUriString;
        final String taskParamsJson = gson.toJson(params);
        try {
            prepareAndRun(context, inputKey, inputUri, taskOutputPathBase, taskOutputTreeUri,
                    taskParamsJson, fileName, taskIndex, totalTasks,
                    result -> handleSyncBatchTaskResult(context, queue, completer, fileName, result,
                            initialOutputPathBase, initialOutputTreeUri, initialTaskIndex));
        } catch (Exception e) {
            Log.e(TAG, "准备同步队列任务失败", e);
            String message = e.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = context.getString(com.tech.ezconvert.R.string.error_unknown);
            }
            Result failure = Result.failure(new Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, message)
                    .putString(KEY_FILE_NAME, fileName)
                    .putInt(KEY_TASK_INDEX, taskIndex)
                    .build());
            handleSyncBatchTaskResult(context, queue, completer, fileName, failure,
                    initialOutputPathBase, initialOutputTreeUri, initialTaskIndex);
        }
    }

    private void handleSyncBatchTaskResult(Context context, ConversionQueueManager queue,
                                           CallbackToFutureAdapter.Completer<Result> completer,
                                           String fileName, Result result,
                                           String initialOutputPathBase, String initialOutputTreeUri,
                                           int initialTaskIndex) {
        // FFmpeg 完成回调与 WorkManager 取消可能并发，取消后不得再登记输出或启动下一项
        if (isCancelled || isStopped()) {
            String outputPath = result.getOutputData().getString(KEY_OUTPUT_PATH);
            deleteOutputPath(context, outputPath);
            completer.set(Result.failure(new Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, context.getString(com.tech.ezconvert.R.string.error_cancelled))
                    .putString(KEY_FILE_NAME, fileName)
                    .putInt(KEY_TASK_INDEX, queue.getCurrentPosition())
                    .putInt(KEY_TOTAL_TASKS, queue.size())
                    .putBoolean(KEY_SYNC_BATCH, true)
                    .build()));
            return;
        }

        if (result instanceof Result.Success) {
            String outputPath = result.getOutputData().getString(KEY_OUTPUT_PATH);
            if (outputPath == null || outputPath.isEmpty()) {
                result = Result.failure(new Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, context.getString(com.tech.ezconvert.R.string.error_unknown))
                        .putString(KEY_FILE_NAME, fileName)
                        .putInt(KEY_TASK_INDEX, queue.getCurrentPosition())
                        .build());
            } else {
                queue.completeBatchItem(outputPath);
                String displayName = getPathDisplayName(context, outputPath, fileName);
                NotificationHelper.showCompleteNotificationPreservingProgress(context, displayName, true, "");
                WORK_EXECUTOR.execute(() -> runNextSyncBatchTask(context, queue,
                        initialOutputPathBase, initialOutputTreeUri, initialTaskIndex, completer));
                return;
            }
        }

        Data taskOutput = result.getOutputData();
        String errorMessage = taskOutput.getString(KEY_ERROR_MESSAGE);
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            errorMessage = context.getString(com.tech.ezconvert.R.string.error_unknown);
        }
        int failedTaskIndex = taskOutput.getInt(KEY_TASK_INDEX, queue.getCurrentPosition());
        String failedFileName = taskOutput.getString(KEY_FILE_NAME);
        if (failedFileName == null || failedFileName.isEmpty()) failedFileName = fileName;
        completer.set(Result.failure(new Data.Builder()
                .putString(KEY_ERROR_MESSAGE, errorMessage)
                .putString(KEY_FILE_NAME, failedFileName)
                .putInt(KEY_TASK_INDEX, failedTaskIndex)
                .putInt(KEY_TOTAL_TASKS, queue.size())
                .putBoolean(KEY_SYNC_BATCH, true)
                .build()));
    }

    private void deleteOutputPath(Context context, String path) {
        if (path == null || path.isEmpty()) return;
        if (path.startsWith("content://")) {
            try {
                DocumentsContract.deleteDocument(context.getContentResolver(), Uri.parse(path));
            } catch (Exception e) {
                Log.w(TAG, "取消任务时删除 SAF 输出失败: " + path, e);
            }
        } else {
            deleteTempFile(path);
        }
    }

    private String getPathDisplayName(Context context, String path, String fallback) {
        if (path != null && path.startsWith("content://")) {
            String displayName = FileUtils.getDisplayName(context, Uri.parse(path));
            if (displayName != null && !displayName.isEmpty()) return displayName;
        }
        String displayName = path == null ? null : new File(path).getName();
        return displayName == null || displayName.isEmpty() ? fallback : displayName;
    }

    private void completeForegroundStartFailure(Context context, int taskIndex, String fileName,
                                                boolean syncBatch,
                                                CallbackToFutureAdapter.Completer<Result> completer) {
        String message = context.getString(com.tech.ezconvert.R.string.notification_foreground_start_failed);
        completer.set(Result.failure(new Data.Builder()
                .putString(KEY_ERROR_MESSAGE, message)
                .putString(KEY_FILE_NAME, fileName)
                .putInt(KEY_TASK_INDEX, taskIndex)
                .putBoolean(KEY_SYNC_BATCH, syncBatch)
                .build()));
    }

    private interface TaskResultCallback {
        void onResult(Result result);
    }

    private ForegroundInfo createForegroundInfo(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return new ForegroundInfo(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            );
        }
        return new ForegroundInfo(NOTIFICATION_ID, notification);
    }

    private void prepareAndRun(Context context, String inputPath, Uri inputUri,
                               String outputPathBase, String outputTreeUriString, String paramsJson,
                               String fileName, int taskIndex, int totalTasks,
                               TaskResultCallback taskCallback) throws Exception {
        activeTaskIndex = taskIndex;
        activeTaskTotal = totalTasks;
        if (isCancelled || isStopped()) {
            taskCallback.onResult(Result.failure(new Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, context.getString(com.tech.ezconvert.R.string.error_cancelled))
                    .putString(KEY_FILE_NAME, fileName)
                    .putInt(KEY_TASK_INDEX, taskIndex)
                    .build()));
            return;
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
            taskCallback.onResult(Result.failure(new Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, "无法访问输入文件")
                    .putString(KEY_FILE_NAME, fileName)
                    .putInt(KEY_TASK_INDEX, taskIndex)
                    .build()));
            return;
        }

        final String usablePath = accessResult.usablePath;
        final boolean isFromCache = accessResult.isFromCache;

        // 生成命令
        String[] command = FfmpegCommandBuilder.buildCommand(usablePath, outputPath, params, context);
        String commandString = buildCommandString(command);
        Log.d(TAG, "Worker 执行命令: " + commandString);

        // 获取视频时长用于进度计算
        getVideoDuration(usablePath, durationMs -> {
            // 时长探测是异步操作；Worker 可能在探测期间已被取消，不能再启动下一项 FFmpeg
            if (isCancelled || isStopped()) {
                if (isFromCache) CacheManager.releaseCacheFile(usablePath);
                if (usablePath.contains("/shared_files/")) deleteTempFile(usablePath);
                Log.d(TAG, "任务已取消，跳过 FFmpeg 启动: " + fileName);
                taskCallback.onResult(Result.failure(new Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, context.getString(com.tech.ezconvert.R.string.error_cancelled))
                        .putString(KEY_FILE_NAME, fileName)
                        .putInt(KEY_TASK_INDEX, taskIndex)
                        .build()));
                return;
            }

            totalDurationMs = durationMs;
            updateProgressNotification(context, fileName, 0, durationMs <= 0);
            executeFfmpeg(commandString, fileName, outputPath, outputTreeUriString, usablePath,
                    isFromCache, workIdStr, taskIndex, taskCallback);
        });
    }

    private void updateProgressNotification(Context context, String fileName, int progress, boolean indeterminate) {
        Notification notification = NotificationHelper.buildProgressNotification(context, fileName, progress, indeterminate);
        // 前台通知建立后使用同一个通知 ID 更新，避免多次异步 setForegroundAsync 覆盖进度
        postProgressNotification(context, notification);
    }

    private void postProgressNotification(Context context, Notification notification) {
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification);
        } catch (Exception e) {
            // 通知权限或系统通知服务异常不应中断实际转码任务
            Log.w(TAG, "发布进度通知失败", e);
        }
    }

    private void executeFfmpeg(String commandString, String fileName, String outputPath, String outputTreeUriString,
                               String usablePath, boolean isFromCache, String workIdStr, int taskIndex,
                               TaskResultCallback taskCallback) {

        Context context = getApplicationContext();

        synchronized (sessionLock) {
            // 与 onStopped 使用同一把锁，保证取消请求不会错过即将创建的 FFmpeg 会话
            if (isCancelled || isStopped()) {
                if (isFromCache) CacheManager.releaseCacheFile(usablePath);
                if (usablePath.contains("/shared_files/")) deleteTempFile(usablePath);
                deleteTempFile(outputPath);
                Log.d(TAG, "任务已取消，跳过 FFmpeg 会话创建: " + fileName);
                taskCallback.onResult(Result.failure(new Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, context.getString(com.tech.ezconvert.R.string.error_cancelled))
                        .putString(KEY_FILE_NAME, fileName)
                        .putInt(KEY_TASK_INDEX, taskIndex)
                        .build()));
                return;
            }

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

                    if (isCancelled || isStopped()) {
                        // 取消时同时清理当前文件系统输出或 SAF 临时输出，避免留下不完整文件
                        deleteTempFile(outputPath);
                        LogManager.getInstance(context).appendFfmpegLog(
                                "=== Task [" + workIdStr + "] END (CANCELLED) ===",
                                Level.AV_LOG_WARNING
                        );
                        taskCallback.onResult(Result.failure(new Data.Builder()
                                .putString(KEY_ERROR_MESSAGE, "操作已取消")
                                .putString(KEY_FILE_NAME, fileName)
                                .putInt(KEY_TASK_INDEX, taskIndex)
                                .build()));
                        return;
                    }

                    if (ReturnCode.isSuccess(returnCode)) {
                        String finalOutputPath = outputPath;
                        if (outputTreeUriString != null && !outputTreeUriString.isEmpty()) {
                            finalOutputPath = publishToSafDirectory(outputPath, outputTreeUriString, fileName);
                            if (finalOutputPath == null) {
                                LogManager.getInstance(context).appendFfmpegLog(
                                        "=== Task [" + workIdStr + "] END (FAILED: 无法写入自定义输出目录) ===",
                                        Level.AV_LOG_ERROR
                                );
                                deleteTempFile(outputPath);
                                taskCallback.onResult(Result.failure(new Data.Builder()
                                        .putString(KEY_ERROR_MESSAGE, "无法写入自定义输出目录，请重新选择输出目录")
                                        .putString(KEY_FILE_NAME, fileName)
                                        .putInt(KEY_TASK_INDEX, taskIndex)
                                        .build()));
                                return;
                            }
                            deleteTempFile(outputPath);
                        }

                        LogManager.getInstance(context).appendFfmpegLog(
                                "=== Task [" + workIdStr + "] END (SUCCESS) ===",
                                Level.AV_LOG_INFO
                        );
                        taskCallback.onResult(Result.success(new Data.Builder()
                                .putString(KEY_OUTPUT_PATH, finalOutputPath)
                                .putString(KEY_FILE_NAME, fileName)
                                .putInt(KEY_TASK_INDEX, taskIndex)
                                .build()));
                    } else {
                        if (outputTreeUriString != null && !outputTreeUriString.isEmpty()) deleteTempFile(outputPath);
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
                        taskCallback.onResult(Result.failure(new Data.Builder()
                                .putString(KEY_ERROR_MESSAGE, errorMessage)
                                .putString(KEY_FILE_NAME, fileName)
                                .putInt(KEY_TASK_INDEX, taskIndex)
                                .build()));
                    }
                }
            }, null, new StatisticsCallback() {
                @Override
                public void apply(Statistics statistics) {
                    if (isCancelled) return;

                    long mediaTimeMs = Math.max(0L, Math.round(statistics.getTime()));
                    long durationMs = totalDurationMs;
                    boolean indeterminate = durationMs <= 0L;
                    int progress = 0;

                    if (!indeterminate) {
                        progress = (int) ((mediaTimeMs * 100.0) / durationMs);
                        progress = Math.min(100, Math.max(0, progress));
                    }

                    // 通过 WorkManager 进度机制上报，媒体时间使用 long 避免长文件发生整数溢出
                    Data progressData = new Data.Builder()
                            .putInt(KEY_PROGRESS, progress)
                            .putLong(KEY_TIME, mediaTimeMs)
                            .putInt(KEY_TASK_INDEX, activeTaskIndex)
                            .putInt(KEY_TOTAL_TASKS, activeTaskTotal)
                            .putString(KEY_STATUS, "RUNNING")
                            .build();
                    setProgressAsync(progressData);

                    // WorkManager 启动前台服务后，通过 NotificationManager 更新同一个通知 ID
                    updateProgressNotification(context, fileName, progress, indeterminate);
                }
            });
        }
    }

    /**
     * 将 FFmpeg 在应用缓存中生成的文件通过 SAF 发布到用户选择的目录
     * 不把 content:// Tree URI 转成 /storage/emulated/0 路径，因此不会触发 Scoped Storage 的权限问题
     */
    private String publishToSafDirectory(String tempPath, String treeUriString, String originalFileName) {
        Context context = getApplicationContext();
        File tempFile = new File(tempPath);
        if (!tempFile.isFile() || tempFile.length() <= 0) {
            Log.e(TAG, "自定义输出临时文件不存在: " + tempPath);
            return null;
        }
        try {
            Uri treeUri = Uri.parse(treeUriString);
            if (!"content".equalsIgnoreCase(treeUri.getScheme())
                    || !DocumentsContract.isTreeUri(treeUri)) {
                Log.e(TAG, "无效的自定义输出 Tree URI: " + treeUriString);
                return null;
            }

            // 确认持久化权限仍然存在。WorkManager 可能在应用进程被杀后重新启动 Worker
            int persistedFlags = 0;
            for (android.content.UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
                if (treeUri.equals(permission.getUri())) {
                    if (permission.isWritePermission()) persistedFlags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    if (permission.isReadPermission()) persistedFlags |= Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    break;
                }
            }
            if ((persistedFlags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0) {
                Log.e(TAG, "自定义输出目录没有持久化写权限: " + treeUriString);
                return null;
            }

            String displayName = tempFile.getName();
            if (displayName == null || displayName.isEmpty()) displayName = originalFileName;
            String mimeType = guessMimeType(displayName);
            ContentResolver resolver = context.getContentResolver();

            // ACTION_OPEN_DOCUMENT_TREE 返回的是 tree URI（content://.../tree/...）
            // DocumentsContract.createDocument() 的 parent 参数必须是对应的
            // document URI（content://.../document/...），不能直接传 tree URI
            String treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
            if (treeDocumentId == null || treeDocumentId.isEmpty()) {
                Log.e(TAG, "无法从 Tree URI 获取 documentId: " + treeUriString);
                return null;
            }
            Uri parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId);
            Log.d(TAG, "SAF 发布父目录 URI: " + parentDocumentUri);

            Uri targetUri = DocumentsContract.createDocument(resolver, parentDocumentUri, mimeType, displayName);
            if (targetUri == null) {
                Log.e(TAG, "DocumentsContract.createDocument 返回 null: " + displayName);
                return null;
            }

            try (InputStream in = new FileInputStream(tempFile);
                 OutputStream out = resolver.openOutputStream(targetUri, "w")) {
                if (out == null) {
                    Log.e(TAG, "无法打开自定义目录输出流: " + targetUri);
                    return null;
                }
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                out.flush();
            } catch (Exception copyError) {
                try { DocumentsContract.deleteDocument(resolver, targetUri); } catch (Exception ignored) {}
                throw copyError;
            }
            return targetUri.toString();
        } catch (Exception e) {
            Log.e(TAG, "发布文件到自定义输出目录失败: " + treeUriString, e);
            return null;
        }
    }

    private String guessMimeType(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.US);
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".aac")) return "audio/aac";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".avi")) return "video/x-msvideo";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
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

        FFmpegSession sessionToCancel;
        synchronized (sessionLock) {
            // 先标记取消，再读取会话；executeFfmpeg 若尚未启动将无法越过取消检查
            isCancelled = true;
            sessionToCancel = currentSession;
        }

        // FFmpegKitNext 的取消接口是异步的；在锁外调用，避免阻塞会话创建与回调
        if (sessionToCancel != null) {
            FFmpegKit.cancel(sessionToCancel.getSessionId());
            Log.d(TAG, "Worker 被取消，正在终止 FFmpeg 会话: " + sessionToCancel.getSessionId());
        }
    }
}
