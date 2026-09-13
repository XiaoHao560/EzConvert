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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

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
            String outputTreeUriString = inputData.getString(KEY_OUTPUT_TREE_URI);
            String paramsJson = inputData.getString(KEY_PARAMS_JSON);
            String fileName = inputData.getString(KEY_FILE_NAME);
            int taskIndex = inputData.getInt(KEY_TASK_INDEX, 1);
            int totalTasks = inputData.getInt(KEY_TOTAL_TASKS, 1);

            if ((inputPath == null && inputUri == null) || outputPathBase == null || paramsJson == null) {
                Log.e(TAG, "Worker 参数缺失");
                completer.set(Result.failure(new Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, "Worker 参数缺失")
                        .putInt(KEY_TASK_INDEX, taskIndex)
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
                executeFfmpeg(commandString, fileName, outputPath, outputTreeUriString, usablePath, isFromCache, workIdStr, taskIndex, completer);
            });

            return "ffmpeg-work";
        });
    }

    private void executeFfmpeg(String commandString, String fileName, String outputPath, String outputTreeUriString,
                               String usablePath, boolean isFromCache, String workIdStr, int taskIndex,
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
                    if (outputTreeUriString != null && !outputTreeUriString.isEmpty()) deleteTempFile(outputPath);
                    LogManager.getInstance(context).appendFfmpegLog(
                            "=== Task [" + workIdStr + "] END (CANCELLED) ===",
                            Level.AV_LOG_WARNING
                    );
                    completer.set(Result.failure(new Data.Builder()
                            .putString(KEY_ERROR_MESSAGE, "操作已取消")
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
                            completer.set(Result.failure(new Data.Builder()
                                    .putString(KEY_ERROR_MESSAGE, "无法写入自定义输出目录，请重新选择输出目录")
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
                    completer.set(Result.success(new Data.Builder()
                            .putString(KEY_OUTPUT_PATH, finalOutputPath)
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
                    completer.set(Result.failure(new Data.Builder()
                            .putString(KEY_ERROR_MESSAGE, errorMessage)
                            .putInt(KEY_TASK_INDEX, taskIndex)
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
        isCancelled = true;
        if (currentSession != null) {
            FFmpegKit.cancel(currentSession.getSessionId());
            Log.d(TAG, "Worker 被取消，终止 FFmpeg 会话: " + currentSession.getSessionId());
        }
    }
}
