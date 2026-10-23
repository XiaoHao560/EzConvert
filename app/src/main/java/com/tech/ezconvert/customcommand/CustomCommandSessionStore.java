package com.tech.ezconvert.customcommand;

import android.content.Context;

import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tech.ezconvert.utils.Log;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 自定义命令任务会话的持久化存储 */
public final class CustomCommandSessionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String LOG_DIRECTORY = "custom_command_logs";
    private static final String WORK_TAG = "custom-command";
    private static final String SESSION_TAG_PREFIX = "custom-command-session-";
    private static final String PROTECT_ALL_LOGS_TAG = "__protect_all_custom_command_logs__";
    private static final String CLEANUP_PREFERENCES = "custom_command_log_cleanup";
    private static final String LAST_CLEANUP_KEY = "last_cleanup_at";
    private static final long LOG_RETENTION_MS = TimeUnit.HOURS.toMillis(24);
    private static final long CLEANUP_INTERVAL_MS = TimeUnit.HOURS.toMillis(6);
    private static final long WORK_QUERY_TIMEOUT_SECONDS = 10L;
    private static final ExecutorService CLEANUP_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean CLEANUP_SCHEDULED = new AtomicBoolean(false);

    private CustomCommandSessionStore() {}

    public static File getSessionFile(Context context, String id) {
        File directory = new File(context.getFilesDir(), "custom_command_sessions");
        if (!directory.exists() && !directory.mkdirs()) {
            Log.w("CustomCommandStore", "无法创建自定义命令会话目录: " + directory);
        }
        return new File(directory, id + ".json");
    }

    /** 返回当前会话的独立执行日志文件，并在后台按需检查过期日志 */
    public static File getLogFile(Context context, String id) {
        Context appContext = context.getApplicationContext();
        File directory = new File(appContext.getFilesDir(), LOG_DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            Log.w("CustomCommandStore", "无法创建自定义命令日志目录: " + directory);
        }
        scheduleLogCleanup(appContext, directory);
        return new File(directory, id + ".log");
    }

    /** 避免在界面线程或 FFmpeg 日志写入线程中执行磁盘扫描和 WorkManager 查询 */
    private static void scheduleLogCleanup(Context context, File directory) {
        long lastCleanupAt = context.getSharedPreferences(CLEANUP_PREFERENCES, Context.MODE_PRIVATE)
                .getLong(LAST_CLEANUP_KEY, 0L);
        if (System.currentTimeMillis() - lastCleanupAt < CLEANUP_INTERVAL_MS) return;
        if (!CLEANUP_SCHEDULED.compareAndSet(false, true)) return;

        CLEANUP_EXECUTOR.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                android.content.SharedPreferences preferences = context.getSharedPreferences(
                        CLEANUP_PREFERENCES, Context.MODE_PRIVATE);
                long lastRun = preferences.getLong(LAST_CLEANUP_KEY, 0L);
                if (now - lastRun < CLEANUP_INTERVAL_MS) return;

                Set<String> activeSessionIds = getActiveSessionIds(context);
                deleteExpiredLogs(directory, activeSessionIds, now);
                preferences.edit().putLong(LAST_CLEANUP_KEY, now).apply();
            } catch (Exception e) {
                // 查询任务状态失败时不清理任何日志，避免误删正在执行的任务日志
                Log.w("CustomCommandStore", "检查自定义命令过期日志失败，本次跳过清理", e);
            } finally {
                CLEANUP_SCHEDULED.set(false);
            }
        });
    }

    /** 通过 WorkManager 的真实状态保护所有排队中和执行中的自定义命令任务 */
    private static Set<String> getActiveSessionIds(Context context) throws Exception {
        List<WorkInfo> workInfos = WorkManager.getInstance(context)
                .getWorkInfosByTag(WORK_TAG)
                .get(WORK_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Set<String> activeIds = new HashSet<>();
        for (WorkInfo workInfo : workInfos) {
            if (workInfo == null || workInfo.getState().isFinished()) continue;

            boolean foundSessionTag = false;
            for (String tag : workInfo.getTags()) {
                if (tag != null && tag.startsWith(SESSION_TAG_PREFIX)) {
                    String sessionId = tag.substring(SESSION_TAG_PREFIX.length());
                    if (!sessionId.isEmpty()) activeIds.add(sessionId);
                    foundSessionTag = true;
                }
            }
            // 兼容升级前创建的任务：旧任务没有会话标签时保守跳过全部日志清理
            if (!foundSessionTag) activeIds.add(PROTECT_ALL_LOGS_TAG);
        }
        return activeIds;
    }

    /** 只删除超过 24 小时的日志，并保留所有仍关联未结束任务的日志 */
    private static void deleteExpiredLogs(File directory, Set<String> activeSessionIds, long now) {
        if (activeSessionIds.contains(PROTECT_ALL_LOGS_TAG)) {
            Log.i("CustomCommandStore", "发现缺少会话标签的运行中任务，本次跳过日志清理");
            return;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            Log.w("CustomCommandStore", "无法读取自定义命令日志目录: " + directory);
            return;
        }

        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".log")) continue;
            String fileName = file.getName();
            String sessionId = fileName.substring(0, fileName.length() - ".log".length());
            if (activeSessionIds.contains(sessionId)) continue;

            long lastModified = file.lastModified();
            // 文件时间异常或系统时间回拨时保守保留，避免误删新日志
            if (lastModified <= 0L || now < lastModified || now - lastModified <= LOG_RETENTION_MS) continue;
            if (file.delete()) {
                Log.d("CustomCommandStore", "已删除过期自定义命令日志: " + file);
            } else {
                Log.w("CustomCommandStore", "无法删除过期自定义命令日志: " + file);
            }
        }
    }

    public static void save(Context context, CustomCommandSession session) throws IOException {
        // 会话与 WorkManager 的 input Data 分开保存，避免大命令模板和文件列表触及 Data 的大小限制
        File file = getSessionFile(context, session.id);
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(session, writer);
        }
    }

    public static CustomCommandSession load(Context context, String id) throws IOException {
        File file = getSessionFile(context, id);
        if (!file.isFile()) return null;
        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, CustomCommandSession.class);
        }
    }

    public static void delete(Context context, String id) {
        File file = getSessionFile(context, id);
        if (file.exists() && !file.delete()) {
            Log.w("CustomCommandStore", "无法删除会话文件: " + file);
        }
    }
}
