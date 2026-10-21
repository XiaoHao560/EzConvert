package com.tech.ezconvert.worker;

import android.app.Notification;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.FFprobeSession;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.FFmpegSessionCompleteCallback;
import com.arthenica.ffmpegkit.Level;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.Statistics;
import com.arthenica.ffmpegkit.StatisticsCallback;
import com.tech.ezconvert.customcommand.CustomCommandParser;
import com.tech.ezconvert.customcommand.CustomCommandSession;
import com.tech.ezconvert.customcommand.CustomCommandSessionStore;
import com.tech.ezconvert.utils.ConfigManager;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.LogManager;
import com.tech.ezconvert.utils.NotificationHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 在后台执行用户自行编写的 FFmpeg 参数，不应用普通转换参数 */
public class CustomCommandWorker extends Worker {
    public static final String KEY_SESSION_ID = "custom_command_session_id";
    public static final String KEY_ERROR_MESSAGE = "custom_command_error_message";
    public static final String KEY_SUCCESS_COUNT = "custom_command_success_count";
    public static final String KEY_FAILED_COUNT = "custom_command_failed_count";

    private volatile FFmpegSession currentSession;
    private volatile boolean stopped;

    public CustomCommandWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        String sessionId = getInputData().getString(KEY_SESSION_ID);
        if (TextUtils.isEmpty(sessionId)) return failure(context.getString(com.tech.ezconvert.R.string.custom_command_worker_missing_id));

        CustomCommandSession session;
        try {
            session = CustomCommandSessionStore.load(context, sessionId);
        } catch (Exception e) {
            return failure(context.getString(com.tech.ezconvert.R.string.custom_command_worker_read_session, safeMessage(e)));
        }
        if (session == null) return failure(context.getString(com.tech.ezconvert.R.string.custom_command_worker_session_missing));

        File workDirectory = new File(new File(context.getFilesDir(), "custom_command_work"), session.id);
        File outputTempDirectory = new File(new File(context.getCacheDir(), "custom_command_output"), session.id);
        if (!workDirectory.exists() && !workDirectory.mkdirs()) return failure(context.getString(com.tech.ezconvert.R.string.custom_command_worker_create_input_directory));
        if (!outputTempDirectory.exists() && !outputTempDirectory.mkdirs()) return failure(context.getString(com.tech.ezconvert.R.string.custom_command_worker_create_output_directory));

        try {
            List<String> parsed = CustomCommandParser.parse(context, session.commandText);
            validateCommandForSession(context, session, parsed);
            establishForeground(context);
            int total = "batch".equals(session.executionMode) ? session.inputs.size() : 1;
            long preparationStartedAt = System.currentTimeMillis();
            updateProgress(context, 0, "正在准备自定义命令", true, 1, Math.max(1, total), preparationStartedAt);
            recordTaskLog(context, session.id,
                    "=== 自定义命令任务 [" + session.id + "] START | mode=" + session.executionMode
                            + " | inputs=" + session.inputs.size() + " ===", Level.AV_LOG_INFO);

            // 单次执行会预先准备全部输入；逐个执行则只在每次循环中准备当前文件，以限制临时存储占用
            List<String> inputPaths = new ArrayList<>();
            if ("batch".equals(session.executionMode)) {
                inputPaths.add("");
            } else {
                for (int i = 0; i < session.inputs.size(); i++) {
                    inputPaths.add(prepareInput(context, session, i, workDirectory));
                }
            }

            int successCount = 0;
            List<String> errors = new ArrayList<>();
            for (int item = 0; item < total; item++) {
                if (stopped || isStopped()) return cancelled(sessionId, workDirectory, outputTempDirectory);
                List<String> activeInputPaths;
                CustomCommandSession.InputSource sourceForNames;
                if ("batch".equals(session.executionMode)) {
                    activeInputPaths = new ArrayList<>();
                    activeInputPaths.add(prepareInput(context, session, item, workDirectory));
                    sourceForNames = session.inputs.get(item);
                } else {
                    activeInputPaths = inputPaths;
                    sourceForNames = session.inputs.isEmpty()
                            ? new CustomCommandSession.InputSource("", "output")
                            : session.inputs.get(0);
                }

                // 命令中可以直接使用界面显示的文件路径；被引用的输入路径会按需准备为可访问的本地文件
                for (String arg : parsed) {
                    int selectedIndex = findSelectedInputPathIndex(session, arg);
                    // 逐个文件模式下，第一项输入路径代表当前轮次的文件，不必提前复制第一项输入
                    if (selectedIndex >= 0 && !("batch".equals(session.executionMode) && selectedIndex == 0)) {
                        prepareInput(context, session, selectedIndex, workDirectory);
                    }
                }
                Map<Integer, File> outputFiles = prepareOutputFiles(context, session, sourceForNames, item, outputTempDirectory, parsed);
                List<String> commandArgs = resolveArguments(context, session, parsed, activeInputPaths, outputFiles, workDirectory);
                Log.i("CustomCommandWorker", "执行自定义 FFmpeg 命令，任务 " + (item + 1) + "/" + total
                        + "，参数数量=" + commandArgs.size());
                int percentBefore = Math.min(99, (int) (item * 100f / Math.max(1, total)));
                long expectedDurationMs = estimateExpectedDurationMs(activeInputPaths);
                long startedAt = System.currentTimeMillis();
                // 开始执行时先使用不确定进度，直到收到有效媒体时间，避免统计回调未启动时一直显示 0%
                updateProgress(context, percentBefore, "正在执行自定义命令 " + (item + 1) + "/" + total,
                        true, item + 1, total, startedAt);
                recordTaskLog(context, session.id, "开始执行第 " + (item + 1) + "/" + total
                        + " 项，预计媒体时长=" + (expectedDurationMs > 0 ? expectedDurationMs + "ms" : "未知；进度将以不确定状态显示"), Level.AV_LOG_INFO);

                FFmpegSession ffmpegSession = runFfmpeg(commandArgs, context, item, total,
                        expectedDurationMs, session.id, startedAt);
                if (stopped || isStopped()) return cancelled(sessionId, workDirectory, outputTempDirectory);
                ReturnCode returnCode = ffmpegSession == null ? null : ffmpegSession.getReturnCode();
                if (!ReturnCode.isSuccess(returnCode)) {
                    // 保留 FFmpeg 返回码和失败栈信息；批量模式下记录错误后继续处理剩余输入
                    String detail = ffmpegSession == null ? context.getString(com.tech.ezconvert.R.string.custom_command_worker_no_ffmpeg_session) : ffmpegSession.getFailStackTrace();
                    if (TextUtils.isEmpty(detail) && ffmpegSession != null) detail = ffmpegSession.getAllLogsAsString();
                    detail = redactSensitiveText(truncate(detail, 3500));
                    String message = context.getString(com.tech.ezconvert.R.string.custom_command_worker_item_failed,
                            item + 1, returnCode == null ? context.getString(com.tech.ezconvert.R.string.unknown) : String.valueOf(returnCode.getValue()),
                            TextUtils.isEmpty(detail) ? "" : detail);
                    errors.add(message);
                    recordTaskLog(context, session.id,
                            "=== 自定义命令第 " + (item + 1) + " 项执行失败 ===\n" + message,
                            Level.AV_LOG_ERROR);
                    Log.e("CustomCommandWorker", message);
                    // 失败项的临时输出可能已经创建，马上清理后再继续下一项，避免批量任务临时占满缓存
                    for (File file : outputFiles.values()) {
                        if (file.exists() && !file.delete()) Log.w("CustomCommandWorker", "无法清理失败项的临时输出: " + file);
                    }
                    updateProgress(context, Math.min(100, (int) ((item + 1) * 100f / Math.max(1, total))),
                            "已完成 " + (item + 1) + "/" + total, true,
                            Math.min(item + 2, total), total, System.currentTimeMillis());
                    continue;
                }

                try {
                    publishOutputs(context, session, outputFiles);
                    successCount++;
                } catch (Exception publishError) {
                    String publishMessage = context.getString(com.tech.ezconvert.R.string.custom_command_worker_publish_failed, item + 1, safeMessage(publishError));
                    errors.add(publishMessage);
                    recordTaskLog(context, session.id, publishMessage, Level.AV_LOG_ERROR);
                    Log.e("CustomCommandWorker", "发布自定义命令输出失败", publishError);
                } finally {
                    for (File file : outputFiles.values()) {
                        if (file.exists() && !file.delete()) Log.w("CustomCommandWorker", "无法删除临时输出文件: " + file);
                    }
                }
                updateProgress(context, Math.min(100, (int) ((item + 1) * 100f / Math.max(1, total))),
                        "已完成 " + (item + 1) + "/" + total, item + 1 < total,
                        Math.min(item + 2, total), total, System.currentTimeMillis());
            }

            CustomCommandSessionStore.delete(context, sessionId);
            deleteRecursively(workDirectory);
            deleteRecursively(outputTempDirectory);
            if (successCount == total && errors.isEmpty()) {
                recordTaskLog(context, session.id,
                        "=== 自定义命令任务 [" + session.id + "] END (SUCCESS) ===", Level.AV_LOG_INFO);
                setProgressAsync(new Data.Builder().putInt("progress", 100).putBoolean("indeterminate", false)
                        .putInt("currentProgress", 100).putInt("itemIndex", total).putInt("totalItems", total)
                        .putString("status", "SUCCEEDED").build());
                NotificationHelper.showCompleteNotification(context, context.getString(com.tech.ezconvert.R.string.custom_command_title), true, "");
                return Result.success(new Data.Builder()
                        .putInt(KEY_SUCCESS_COUNT, successCount)
                        .putInt(KEY_FAILED_COUNT, 0)
                        .build());
            }
            int failedCount = total - successCount;
            recordTaskLog(context, session.id,
                    "=== 自定义命令任务 [" + session.id + "] END (FAILED) ===", Level.AV_LOG_ERROR);
            String message = context.getString(com.tech.ezconvert.R.string.custom_command_worker_complete_count, successCount, total);
            if (!errors.isEmpty()) message += "\n\n" + TextUtils.join("\n\n", errors);
            message = truncate(message, 7000);
            NotificationHelper.showCompleteNotification(context, context.getString(com.tech.ezconvert.R.string.custom_command_title), false, truncate(message, 240));
            return Result.failure(new Data.Builder()
                    .putString(KEY_ERROR_MESSAGE, message)
                    .putInt(KEY_SUCCESS_COUNT, successCount)
                    .putInt(KEY_FAILED_COUNT, failedCount)
                    .build());
        } catch (Exception e) {
            Log.e("CustomCommandWorker", "自定义命令任务异常", e);
            if (stopped || isStopped() || e instanceof InterruptedException) {
                return cancelled(sessionId, workDirectory, outputTempDirectory);
            }
            CustomCommandSessionStore.delete(context, sessionId);
            deleteRecursively(workDirectory);
            deleteRecursively(outputTempDirectory);
            recordTaskLog(context, session.id, "自定义命令执行异常：" + safeMessage(e), Level.AV_LOG_ERROR);
            recordTaskLog(context, session.id,
                    "=== 自定义命令任务 [" + session.id + "] END (FAILED: unexpected error) ===", Level.AV_LOG_ERROR);
            String message = TextUtils.isEmpty(e.getMessage())
                    ? context.getString(com.tech.ezconvert.R.string.custom_command_worker_failed_generic)
                    : truncate(e.getMessage(), 7000);
            NotificationHelper.showCompleteNotification(context, context.getString(com.tech.ezconvert.R.string.custom_command_title), false, truncate(message, 240));
            return failure(message);
        }
    }

    private void validateCommandForSession(Context context, CustomCommandSession session, List<String> args) {
        if (session.inputs == null) session.inputs = new ArrayList<>();
        boolean hasInputReference = false;
        for (String arg : args) {
            if (CustomCommandParser.isInputPlaceholder(arg) || findSelectedInputPathIndex(session, arg) >= 0) {
                hasInputReference = true;
            }
        }
        if (("batch".equals(session.executionMode) || hasInputReference) && session.inputs.isEmpty()) {
            throw new IllegalArgumentException(context.getString(com.tech.ezconvert.R.string.custom_command_error_no_inputs));
        }
        if (session.outputFileNames == null) session.outputFileNames = new ArrayList<>();
        for (String arg : args) {
            if (CustomCommandParser.isInputPlaceholder(arg)) {
                int inputIndex = CustomCommandParser.inputIndex(arg);
                if ("batch".equals(session.executionMode)) {
                    if (inputIndex > 1) throw new IllegalArgumentException(context.getString(com.tech.ezconvert.R.string.custom_command_error_batch_input_placeholder));
                } else if (inputIndex > session.inputs.size()) {
                    throw new IllegalArgumentException(context.getString(com.tech.ezconvert.R.string.custom_command_error_input_index, inputIndex));
                } else if (inputIndex == 0 && session.inputs.size() != 1) {
                    throw new IllegalArgumentException(context.getString(com.tech.ezconvert.R.string.custom_command_error_input_alias));
                }
            }
            if (CustomCommandParser.isOutputPlaceholder(arg)) {
                int index = CustomCommandParser.outputIndex(arg);
                if (index > session.outputFileNames.size()) {
                    throw new IllegalArgumentException(context.getString(com.tech.ezconvert.R.string.custom_command_error_output_index, index));
                }
            }
        }
        if ("batch".equals(session.executionMode) && !TextUtils.isEmpty(session.outputDocumentUri)
                && session.inputs.size() > 1) {
            throw new IllegalArgumentException(context.getString(com.tech.ezconvert.R.string.custom_command_error_single_document_batch));
        }
        boolean directOutputReferenced = false;
        for (String arg : args) {
            if (!TextUtils.isEmpty(session.outputCommandPath) && arg.equals(session.outputCommandPath)) {
                directOutputReferenced = true;
            }
            if (arg.startsWith("content://") && findSelectedInputUriIndex(session, arg) < 0) {
                throw new IllegalArgumentException(context.getString(com.tech.ezconvert.R.string.custom_command_direct_content_uri_unselected));
            }
        }
        if (directOutputReferenced && !TextUtils.isEmpty(session.outputDocumentUri)
                && !hasPersistedWritePermission(context, Uri.parse(session.outputDocumentUri))) {
            throw new IllegalArgumentException(context.getString(com.tech.ezconvert.R.string.custom_command_output_file_permission_missing));
        }
        validateDirectPathPermissions(context, session, args);
    }

    private int findSelectedInputPathIndex(CustomCommandSession session, String path) {
        if (session == null || session.inputs == null || TextUtils.isEmpty(path)) return -1;
        for (int i = 0; i < session.inputs.size(); i++) {
            CustomCommandSession.InputSource input = session.inputs.get(i);
            if (input != null && path.equals(input.commandPath) && !TextUtils.isEmpty(input.commandPath)) return i;
        }
        return -1;
    }

    private int findSelectedInputUriIndex(CustomCommandSession session, String uri) {
        if (session == null || session.inputs == null || TextUtils.isEmpty(uri)) return -1;
        for (int i = 0; i < session.inputs.size(); i++) {
            CustomCommandSession.InputSource input = session.inputs.get(i);
            if (input != null && uri.equals(input.uri)) return i;
        }
        return -1;
    }

    private boolean containsArgument(List<String> args, String value) {
        for (String arg : args) if (value.equals(arg)) return true;
        return false;
    }

    private boolean isPathWithin(File child, File parent) {
        try {
            String parentPath = parent.getCanonicalPath();
            String childPath = child.getCanonicalPath();
            return childPath.startsWith(parentPath + File.separator);
        } catch (IOException e) {
            Log.w("CustomCommandWorker", "无法验证自定义命令临时路径", e);
            return false;
        }
    }

    private boolean hasPersistedWritePermission(Context context, Uri uri) {
        for (android.content.UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
            if (uri.equals(permission.getUri()) && permission.isWritePermission()) return true;
        }
        return false;
    }

    private void validateDirectPathPermissions(Context context, CustomCommandSession session, List<String> args) {
        for (int i = 0; i + 1 < args.size(); i++) {
            if (!"-i".equals(args.get(i))) continue;
            String path = args.get(i + 1);
            if (CustomCommandParser.isInputPlaceholder(path) || findSelectedInputPathIndex(session, path) >= 0) continue;
            File file = localFileFromArgument(path);
            if (file == null) continue;
            if (!file.isFile() || !file.canRead()) {
                requireDirectStoragePermission(context, file);
                throw new IllegalArgumentException(context.getString(com.tech.ezconvert.R.string.custom_command_direct_input_unreadable, file.getAbsolutePath()));
            }
        }
        if (args.isEmpty()) return;
        String output = args.get(args.size() - 1);
        if (CustomCommandParser.isOutputPlaceholder(output)
                || (!TextUtils.isEmpty(session.outputCommandPath) && output.equals(session.outputCommandPath))
                || findSelectedInputPathIndex(session, output) >= 0) return;
        File file = localFileFromArgument(output);
        if (file == null) return;
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent == null || !parent.isDirectory()) {
            throw new IllegalArgumentException(context.getString(com.tech.ezconvert.R.string.custom_command_direct_output_unwritable, file.getAbsolutePath()));
        }
        if (!parent.canWrite()) {
            requireDirectStoragePermission(context, file);
            throw new IllegalArgumentException(context.getString(com.tech.ezconvert.R.string.custom_command_direct_output_unwritable, file.getAbsolutePath()));
        }
    }

    private File localFileFromArgument(String argument) {
        if (TextUtils.isEmpty(argument)) return null;
        if (argument.startsWith("file://")) {
            Uri uri = Uri.parse(argument);
            return uri.getPath() == null ? null : new File(uri.getPath());
        }
        return argument.startsWith("/") ? new File(argument) : null;
    }

    private void requireDirectStoragePermission(Context context, File file) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
                || isAppSpecificExternalPath(context, file)) return;
        String path = file.getAbsolutePath();
        if (path.startsWith("/storage/") || path.startsWith("/sdcard/")
                || path.startsWith("/mnt/media_rw/")) {
            throw new IllegalArgumentException(context.getString(com.tech.ezconvert.R.string.custom_command_direct_storage_permission_required));
        }
    }

    private boolean isAppSpecificExternalPath(Context context, File file) {
        if (file == null) return false;
        File[] directories = context.getExternalFilesDirs(null);
        if (directories != null) {
            for (File directory : directories) {
                if (directory != null && isSameOrChildPath(file, directory)) return true;
            }
        }
        File[] cacheDirectories = context.getExternalCacheDirs();
        if (cacheDirectories != null) {
            for (File directory : cacheDirectories) {
                if (directory != null && isSameOrChildPath(file, directory)) return true;
            }
        }
        File[] mediaDirectories = context.getExternalMediaDirs();
        if (mediaDirectories != null) {
            for (File directory : mediaDirectories) {
                if (directory != null && isSameOrChildPath(file, directory)) return true;
            }
        }
        return false;
    }

    private boolean isSameOrChildPath(File file, File directory) {
        try {
            String candidate = file.getCanonicalPath();
            String base = directory.getCanonicalPath();
            return candidate.equals(base) || candidate.startsWith(base + File.separator);
        } catch (IOException e) {
            Log.w("CustomCommandWorker", "无法验证应用专属存储路径", e);
            return false;
        }
    }

    private void establishForeground(Context context) throws Exception {
        Notification notification = NotificationHelper.buildProgressNotification(context, context.getString(com.tech.ezconvert.R.string.custom_command_title), 0);
        ForegroundInfo info;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info = new ForegroundInfo(9107, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            info = new ForegroundInfo(9107, notification);
        }
        setForegroundAsync(info).get(15, TimeUnit.SECONDS);
    }

    private String prepareInput(Context context, CustomCommandSession session, int index, File workDirectory) throws Exception {
        if (index < 0 || index >= session.inputs.size()) throw new IllegalArgumentException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_input_index));
        CustomCommandSession.InputSource source = session.inputs.get(index);
        Uri uri = Uri.parse(source.uri);
        String name = sanitizeFileName(source.displayName);
        if (TextUtils.isEmpty(name)) name = "input.bin";
        File cached = null;
        if (!TextUtils.isEmpty(source.commandPath)) {
            File requested = new File(source.commandPath);
            File managedRoot = new File(new File(context.getCacheDir(), "custom_command_paths"), "input");
            if (isPathWithin(requested, managedRoot)) cached = requested;
        }
        if (cached == null) {
            File inputDirectory = new File(workDirectory, "input");
            cached = new File(inputDirectory, String.format(Locale.US, "%03d_%s", index + 1, name));
        }
        File parent = cached.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_create_input_cache));
        }
        if (cached.isFile() && cached.length() > 0) return cached.getAbsolutePath();

        try (InputStream in = openInput(context, uri); FileOutputStream out = new FileOutputStream(cached)) {
            if (in == null) throw new IllegalStateException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_read_input, source.displayName));
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (stopped || isStopped()) throw new InterruptedException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_cancelled));
                out.write(buffer, 0, read);
            }
            out.flush();
        } catch (Exception e) {
            if (cached.exists()) cached.delete();
            throw new IllegalStateException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_copy_input, source.displayName, safeMessage(e)), e);
        }
        if (cached.length() <= 0) throw new IllegalStateException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_empty_input, source.displayName));
        return cached.getAbsolutePath();
    }

    private InputStream openInput(Context context, Uri uri) throws Exception {
        if ("file".equalsIgnoreCase(uri.getScheme())) return new FileInputStream(new File(uri.getPath()));
        return context.getContentResolver().openInputStream(uri);
    }

    private Map<Integer, File> prepareOutputFiles(Context context, CustomCommandSession session,
                                                   CustomCommandSession.InputSource source,
                                                   int itemIndex, File tempDirectory,
                                                   List<String> args) throws Exception {
        Set<Integer> outputIndexes = new HashSet<>();
        for (String arg : args) {
            if (CustomCommandParser.isOutputPlaceholder(arg)) {
                outputIndexes.add(CustomCommandParser.outputIndex(arg));
            } else if (!TextUtils.isEmpty(session.outputCommandPath) && arg.equals(session.outputCommandPath)) {
                outputIndexes.add(1);
            }
        }
        Map<Integer, File> files = new HashMap<>();
        String baseName = stripExtension(source.displayName);
        for (Integer outputIndex : outputIndexes) {
            String template = session.outputFileNames.get(outputIndex - 1);
            String fileName = expandFileName(context, template, baseName, itemIndex + 1);
            if (outputIndex == 1 && !TextUtils.isEmpty(session.outputDocumentName)) {
                fileName = sanitizeFileName(session.outputDocumentName);
            }
            File outputFile;
            boolean directOutputPathUsed = outputIndex == 1 && !TextUtils.isEmpty(session.outputCommandPath)
                    && containsArgument(args, session.outputCommandPath);
            if (directOutputPathUsed) {
                File requested = new File(session.outputCommandPath);
                File managedRoot = new File(new File(context.getCacheDir(), "custom_command_paths"), "output");
                if (!isPathWithin(requested, managedRoot)) {
                    throw new IllegalArgumentException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_output_not_created, session.outputCommandPath));
                }
                File parent = requested.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IllegalStateException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_create_output_directory));
                }
                outputFile = requested;
            } else {
                // 传统输出占位符仍使用按任务隔离的临时子目录
                File outputIndexDirectory = new File(tempDirectory, String.format(Locale.US, "output_%03d", outputIndex));
                if (!outputIndexDirectory.exists() && !outputIndexDirectory.mkdirs()) {
                    throw new IllegalStateException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_create_output_directory));
                }
                outputFile = new File(outputIndexDirectory, fileName);
            }
            if (outputFile.exists() && !outputFile.delete()) throw new IllegalStateException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_delete_stale_output, outputFile.getName()));
            files.put(outputIndex, outputFile);
        }
        return files;
    }

    /** 占位符按参数整体替换，因此真实路径中的空格不会被再次拆分成多个 FFmpeg 参数 */
    private List<String> resolveArguments(Context context, CustomCommandSession session, List<String> parsed,
                                          List<String> inputPaths, Map<Integer, File> outputFiles,
                                          File workDirectory) throws Exception {
        List<String> args = new ArrayList<>(parsed.size());
        for (String arg : parsed) {
            if (CustomCommandParser.isInputPlaceholder(arg)) {
                int index = CustomCommandParser.inputIndex(arg);
                if ("batch".equals(session.executionMode)) {
                    args.add(inputPaths.get(0));
                } else if (index == 0) {
                    args.add(inputPaths.get(0));
                } else {
                    args.add(inputPaths.get(index - 1));
                }
            } else if (CustomCommandParser.isOutputPlaceholder(arg)) {
                int index = CustomCommandParser.outputIndex(arg);
                File output = outputFiles.get(index);
                if (output == null) throw new IllegalArgumentException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_output_not_created, arg));
                args.add(output.getAbsolutePath());
            } else {
                int selectedPathIndex = findSelectedInputPathIndex(session, arg);
                if (selectedPathIndex >= 0) {
                    if ("batch".equals(session.executionMode) && selectedPathIndex == 0) {
                        // 在逐个文件模式中保留命令里显示的具体路径，但实际传入当前轮次输入
                        args.add(inputPaths.get(0));
                    } else {
                        args.add(prepareInput(context, session, selectedPathIndex, workDirectory));
                    }
                    continue;
                }
                int selectedUriIndex = findSelectedInputUriIndex(session, arg);
                if (selectedUriIndex >= 0) {
                    args.add(prepareInput(context, session, selectedUriIndex, workDirectory));
                } else {
                    args.add(arg);
                }
            }
        }
        return args;
    }

    private FFmpegSession runFfmpeg(List<String> args, Context context, int itemIndex, int total,
                                    long expectedDurationMs, String sessionId, long startedAt) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final FFmpegSession[] result = new FFmpegSession[1];
        String[] command = args.toArray(new String[0]);
        currentSession = FFmpegKit.executeWithArgumentsAsync(command, new FFmpegSessionCompleteCallback() {
            @Override
            public void apply(FFmpegSession session) {
                result[0] = session;
                latch.countDown();
            }
        }, log -> {
            // FFmpeg 原始日志同时写入应用的 FFmpeg 日志和当前会话日志，方便用户独立排查该任务
            recordTaskLog(context, sessionId, log.getMessage(), log.getLevel());
        }, new StatisticsCallback() {
            @Override
            public void apply(Statistics statistics) {
                if (stopped || isStopped()) return;
                // FFmpegKit 的统计时间类型为 double，四舍五入为毫秒整数后再参与进度计算
                long mediaTimeMs = Math.max(0L, Math.round(statistics.getTime()));
                boolean indeterminate = expectedDurationMs <= 0L || mediaTimeMs <= 0L;
                int currentPercent = indeterminate ? 0
                        : (int) Math.max(0, Math.min(99, mediaTimeMs * 100.0 / expectedDurationMs));
                double fraction = indeterminate ? 0.0
                        : Math.min(0.999, Math.max(0.0, mediaTimeMs / (double) expectedDurationMs));
                // 总百分比由已完成项目和当前输入媒体时间估算，复杂滤镜或改变输出时长的命令只能视为近似值
                int overallPercent = (int) (((itemIndex + fraction) * 100.0) / Math.max(1, total));
                overallPercent = Math.max((int) (itemIndex * 100f / Math.max(1, total)), Math.min(99, overallPercent));
                Data progressData = new Data.Builder()
                        .putInt("progress", overallPercent)
                        .putBoolean("indeterminate", indeterminate)
                        .putInt("currentProgress", currentPercent)
                        .putInt("itemIndex", itemIndex + 1)
                        .putInt("totalItems", total)
                        .putLong("mediaTimeMs", mediaTimeMs)
                        .putLong("startedAt", startedAt)
                        .putString("status", "RUNNING")
                        .build();
                setProgressAsync(progressData);
                Notification notification = NotificationHelper.buildProgressNotification(
                        context, context.getString(com.tech.ezconvert.R.string.custom_command_title), overallPercent);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    setForegroundAsync(new ForegroundInfo(9107, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC));
                } else {
                    setForegroundAsync(new ForegroundInfo(9107, notification));
                }
            }
        });
        while (!latch.await(500, TimeUnit.MILLISECONDS)) {
            if (stopped || isStopped()) {
                if (currentSession != null) FFmpegKit.cancel(currentSession.getSessionId());
                throw new InterruptedException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_cancelled));
            }
        }
        return result[0];
    }

    /** 用 FFprobe 估算输入媒体时长；遇到图片、直播流或未知时长时返回 -1，让界面显示不确定进度 */
    private long estimateExpectedDurationMs(List<String> inputPaths) {
        long longestDuration = -1L;
        if (inputPaths == null) return longestDuration;
        for (String inputPath : inputPaths) {
            if (TextUtils.isEmpty(inputPath)) continue;
            try {
                // 输入路径来自应用私有缓存，但仍对引号和反斜杠做转义，避免构造 FFprobe 命令时截断路径
                String escapedPath = inputPath.replace("\\", "\\\\").replace("\"", "\\\"");
                String probeCommand = "-v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 \""
                        + escapedPath + "\"";
                FFprobeSession probe = FFprobeKit.execute(probeCommand);
                if (probe == null || !ReturnCode.isSuccess(probe.getReturnCode())) continue;
                String output = probe.getOutput();
                if (TextUtils.isEmpty(output)) continue;
                double seconds = Double.parseDouble(output.trim().split("\\R")[0]);
                long durationMs = (long) (seconds * 1000.0);
                if (durationMs > longestDuration) longestDuration = durationMs;
            } catch (Exception e) {
                Log.d("CustomCommandWorker", "无法估算输入媒体时长，改用不确定进度显示: " + safeMessage(e));
            }
        }
        return longestDuration > 0L ? longestDuration : -1L;
    }

    /** 将日志追加到本次会话，并保留 FFmpeg 总日志供现有日志查看器使用 */
    private void recordTaskLog(Context context, String sessionId, String message, Level level) {
        String safeMessage = redactSensitiveText(message == null ? "" : message);
        LogManager.getInstance(context).appendFfmpegLog(safeMessage, level);
        File logFile = CustomCommandSessionStore.getLogFile(context, sessionId);
        synchronized (CustomCommandWorker.class) {
            try {
                File parent = logFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    Log.w("CustomCommandWorker", "无法创建任务日志目录: " + parent);
                    return;
                }
                String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
                byte[] bytes = ("[" + timestamp + "] " + safeMessage + "\n").getBytes(StandardCharsets.UTF_8);
                try (FileOutputStream output = new FileOutputStream(logFile, true)) {
                    output.write(bytes);
                    output.flush();
                }
                // 限制会话日志大小；长任务只保留末尾内容，避免应用私有存储被日志无限占用
                if (logFile.length() > 512L * 1024L) {
                    byte[] allBytes;
                    try (FileInputStream input = new FileInputStream(logFile)) {
                        allBytes = new byte[(int) Math.min(Integer.MAX_VALUE, logFile.length())];
                        int offset = 0;
                        while (offset < allBytes.length) {
                            int read = input.read(allBytes, offset, allBytes.length - offset);
                            if (read < 0) break;
                            offset += read;
                        }
                        if (offset < allBytes.length) allBytes = java.util.Arrays.copyOf(allBytes, offset);
                    }
                    int keepFrom = Math.max(0, allBytes.length - 256 * 1024);
                    int newline = keepFrom;
                    while (newline < allBytes.length && allBytes[newline] != (byte) '\n') newline++;
                    try (FileOutputStream output = new FileOutputStream(logFile, false)) {
                        output.write(allBytes, Math.min(allBytes.length, newline + 1),
                                Math.max(0, allBytes.length - Math.min(allBytes.length, newline + 1)));
                    }
                }
            } catch (IOException e) {
                Log.w("CustomCommandWorker", "写入自定义命令会话日志失败", e);
            }
        }
    }

    /** FFmpeg 先写入临时路径，只有输出文件真实存在且非空后才发布到用户指定位置 */
    private void publishOutputs(Context context, CustomCommandSession session, Map<Integer, File> outputs) throws Exception {
        for (Map.Entry<Integer, File> entry : outputs.entrySet()) {
            int index = entry.getKey();
            File tempFile = entry.getValue();
            if (!tempFile.isFile() || tempFile.length() <= 0) {
                throw new IllegalStateException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_output_missing, tempFile.getName()));
            }
            if (index == 1 && !TextUtils.isEmpty(session.outputDocumentUri)) {
                writeToDocument(context, tempFile, Uri.parse(session.outputDocumentUri));
            } else {
                String treeUri = !TextUtils.isEmpty(session.outputTreeUri)
                        ? session.outputTreeUri
                        : ("custom".equals(session.configuredOutputMode) ? session.configuredOutputTreeUri : "");
                if (!TextUtils.isEmpty(treeUri)) {
                    publishToSafDirectory(context, tempFile, Uri.parse(treeUri));
                } else {
                    publishToPublicDirectory(context, tempFile, session.configuredOutputMode);
                }
            }
        }
    }

    private void publishToPublicDirectory(Context context, File source, String outputMode) throws Exception {
        File base = "dcim".equals(outputMode)
                ? Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File directory = new File(base, "EzConvert");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_public_output_dir, directory));
        File target = uniqueFile(directory, source.getName());
        copyFile(source, target);
        Log.i("CustomCommandWorker", "输出文件已保存：" + target.getAbsolutePath());
    }

    private void publishToSafDirectory(Context context, File source, Uri treeUri) throws Exception {
        if (!DocumentsContract.isTreeUri(treeUri)) throw new IllegalArgumentException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_invalid_output_tree));
        boolean hasWrite = false;
        for (android.content.UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
            if (treeUri.equals(permission.getUri()) && permission.isWritePermission()) {
                hasWrite = true;
                break;
            }
        }
        if (!hasWrite) throw new IllegalStateException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_output_permission));

        String treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId);
        String targetName = uniqueSafName(context.getContentResolver(), treeUri, treeDocumentId, source.getName());
        Uri target = DocumentsContract.createDocument(context.getContentResolver(), parent, guessMimeType(targetName), targetName);
        if (target == null) throw new IllegalStateException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_create_saf_file, targetName));
        try {
            writeToDocument(context, source, target);
        } catch (Exception e) {
            try {
                DocumentsContract.deleteDocument(context.getContentResolver(), target);
            } catch (Exception cleanupError) {
                Log.w("CustomCommandWorker", "发布输出失败后无法删除未完成的 SAF 文件: " + target, cleanupError);
            }
            throw e;
        }
    }

    private String uniqueSafName(ContentResolver resolver, Uri treeUri, String treeDocumentId, String desired) {
        Set<String> existing = new HashSet<>();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId);
        try (Cursor cursor = resolver.query(children, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) existing.add(cursor.getString(0));
            }
        } catch (Exception e) {
            Log.w("CustomCommandWorker", "无法检查输出目录中的文件名", e);
        }
        return uniqueName(existing, desired);
    }

    private void writeToDocument(Context context, File source, Uri documentUri) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        try (InputStream in = new FileInputStream(source)) {
            OutputStream out = resolver.openOutputStream(documentUri, "w");
            if (out == null) throw new IllegalStateException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_open_document));
            try (OutputStream actualOut = out) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) actualOut.write(buffer, 0, read);
                actualOut.flush();
            }
        }
    }

    private void copyFile(File source, File target) throws Exception {
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.flush();
        }
    }

    private File uniqueFile(File directory, String desiredName) {
        Set<String> existing = new HashSet<>();
        String[] names = directory.list();
        if (names != null) java.util.Collections.addAll(existing, names);
        String candidate = uniqueName(existing, desiredName);
        return new File(directory, candidate);
    }

    private String uniqueName(Set<String> existing, String desiredName) {
        if (!existing.contains(desiredName)) return desiredName;
        String base = desiredName;
        String extension = "";
        int dot = desiredName.lastIndexOf('.');
        if (dot > 0) {
            base = desiredName.substring(0, dot);
            extension = desiredName.substring(dot);
        }
        int index = 1;
        String candidate;
        do {
            candidate = base + " (" + index++ + ")" + extension;
        } while (existing.contains(candidate));
        return candidate;
    }

    private String expandFileName(Context context, String template, String baseName, int index) {
        String date = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        String name = template == null ? "{{name}}_custom.mp4" : template;
        name = name.replace("{{name}}", baseName)
                .replace("{{index}}", String.valueOf(index))
                .replace("{{date}}", date);
        name = sanitizeFileName(name);
        if (TextUtils.isEmpty(name) || ".".equals(name) || "..".equals(name)) {
            throw new IllegalArgumentException(context.getString(com.tech.ezconvert.R.string.custom_command_worker_invalid_template));
        }
        return name;
    }

    private String stripExtension(String name) {
        if (name == null) return "output";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String sanitizeFileName(String name) {
        if (name == null) return "";
        String value = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        while (value.startsWith(".")) value = value.substring(1);
        return value.length() > 180 ? value.substring(0, 180) : value;
    }

    private String guessMimeType(String name) {
        String extension = "";
        int dot = name == null ? -1 : name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) extension = name.substring(dot + 1).toLowerCase(Locale.US);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mime == null ? "application/octet-stream" : mime;
    }

    /** 除界面进度外同步更新前台通知；没有已知媒体时长时保留不确定进度状态 */
    private void updateProgress(Context context, int percent, String status, boolean indeterminate,
                                int itemIndex, int totalItems, long startedAt) {
        Data progressData = new Data.Builder()
                .putInt("progress", Math.max(0, Math.min(100, percent)))
                .putBoolean("indeterminate", indeterminate)
                .putInt("currentProgress", (!indeterminate && percent >= 100) || (status != null && status.startsWith("已完成")) ? 100 : 0)
                .putInt("itemIndex", Math.max(1, itemIndex))
                .putInt("totalItems", Math.max(1, totalItems))
                .putLong("startedAt", startedAt)
                .putString("status", status)
                .build();
        setProgressAsync(progressData);
        Notification notification = NotificationHelper.buildProgressNotification(
                context, context.getString(com.tech.ezconvert.R.string.custom_command_title), percent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setForegroundAsync(new ForegroundInfo(9107, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC));
        } else {
            setForegroundAsync(new ForegroundInfo(9107, notification));
        }
    }

    private Result cancelled(String id, File workDirectory, File outputDirectory) {
        recordTaskLog(getApplicationContext(), id, "自定义命令任务已取消", Level.AV_LOG_WARNING);
        CustomCommandSessionStore.delete(getApplicationContext(), id);
        deleteRecursively(workDirectory);
        deleteRecursively(outputDirectory);
        return Result.failure(new Data.Builder().putString(KEY_ERROR_MESSAGE, getApplicationContext().getString(com.tech.ezconvert.R.string.custom_command_worker_cancelled)).build());
    }

    private Result failure(String message) {
        return Result.failure(new Data.Builder().putString(KEY_ERROR_MESSAGE,
                TextUtils.isEmpty(message) ? getApplicationContext().getString(com.tech.ezconvert.R.string.custom_command_worker_failed_generic) : message).build());
    }

    @Override
    public void onStopped() {
        stopped = true;
        FFmpegSession session = currentSession;
        if (session != null) {
            try { FFmpegKit.cancel(session.getSessionId()); } catch (Exception e) {
                Log.w("CustomCommandWorker", "取消 FFmpeg 会话失败", e);
            }
        }
        super.onStopped();
    }

    private String safeMessage(Throwable throwable) {
        return throwable == null || TextUtils.isEmpty(throwable.getMessage())
                ? getApplicationContext().getString(com.tech.ezconvert.R.string.custom_command_worker_failed_generic)
                : throwable.getMessage();
    }

    private String redactSensitiveText(String value) {
        if (value == null) return "";
        return value.replaceAll("(?i)(https?://)[^/@\\s]+:[^/@\\s]+@", "$1***:***@");
    }

    private String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) return value == null ? "" : value;
        return value.substring(0, limit) + "…";
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        if (!file.delete()) Log.w("CustomCommandWorker", "无法删除临时文件：" + file);
    }
}
