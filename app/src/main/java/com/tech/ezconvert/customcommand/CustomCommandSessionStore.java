package com.tech.ezconvert.customcommand;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tech.ezconvert.utils.Log;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/** 自定义命令任务会话的持久化存储 */
public final class CustomCommandSessionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CustomCommandSessionStore() {}

    public static File getSessionFile(Context context, String id) {
        File directory = new File(context.getFilesDir(), "custom_command_sessions");
        if (!directory.exists() && !directory.mkdirs()) {
            Log.w("CustomCommandStore", "无法创建自定义命令会话目录: " + directory);
        }
        return new File(directory, id + ".json");
    }

    /** 返回当前会话的独立执行日志文件，供后台 Worker 追加、界面实时读取 */
    public static File getLogFile(Context context, String id) {
        File directory = new File(context.getFilesDir(), "custom_command_logs");
        if (!directory.exists() && !directory.mkdirs()) {
            Log.w("CustomCommandStore", "无法创建自定义命令日志目录: " + directory);
        }
        return new File(directory, id + ".log");
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
