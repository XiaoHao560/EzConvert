package com.tech.ezconvert.customcommand;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tech.ezconvert.utils.Log;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理用户保存的自定义命令预设 */
public class CustomCommandPresetManager {
    private static final String PREFS = "custom_command_presets";
    private static final String KEY_PRESETS = "presets_json";
    private final SharedPreferences preferences;
    private final Gson gson = new Gson();
    private final Type mapType = new TypeToken<LinkedHashMap<String, Preset>>() {}.getType();

    public CustomCommandPresetManager(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<String> getNames() {
        List<String> names = new ArrayList<>(read().keySet());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public synchronized void save(String name, CustomCommandSession session) {
        Map<String, Preset> presets = read();
        Preset preset = new Preset();
        // 预设只保存可复用模板，不保存本次选中的文件、临时会话 ID 或 SAF 授权
        preset.executionMode = session.executionMode;
        preset.commandText = makeReusableCommand(session);
        preset.outputFileNames = session.outputFileNames == null
                ? new ArrayList<>() : new ArrayList<>(session.outputFileNames);
        presets.put(name, preset);
        preferences.edit().putString(KEY_PRESETS, gson.toJson(presets)).apply();
    }

    /** 预设只保留可复用占位符，避免写入本次会话的私有临时路径 */
    private String makeReusableCommand(CustomCommandSession session) {
        String command = session.commandText == null ? "" : session.commandText;
        if (session.inputs != null) {
            for (int i = 0; i < session.inputs.size(); i++) {
                CustomCommandSession.InputSource input = session.inputs.get(i);
                if (input == null) continue;
                String placeholder = session.inputs.size() == 1 && i == 0
                        ? "%INPUT%" : "%INPUT_" + (i + 1) + "%";
                command = replacePath(command, input.commandPath, placeholder);
                command = replacePath(command, input.uri, placeholder);
            }
        }
        command = replacePath(command, session.outputCommandPath, "%OUTPUT_1%");
        return command;
    }

    private String replacePath(String command, String path, String placeholder) {
        if (command == null || path == null || path.isEmpty()) return command == null ? "" : command;
        String quoted = CustomCommandParser.quoteForDisplay(path);
        return command.replace(quoted, placeholder).replace(path, placeholder);
    }

    public synchronized CustomCommandSession load(String name) {
        Preset preset = read().get(name);
        if (preset == null) return null;
        CustomCommandSession session = new CustomCommandSession();
        session.executionMode = preset.executionMode == null ? "single" : preset.executionMode;
        session.commandText = preset.commandText == null ? "" : preset.commandText;
        session.outputFileNames = preset.outputFileNames == null
                ? new ArrayList<>() : new ArrayList<>(preset.outputFileNames);
        return session;
    }

    public synchronized void delete(String name) {
        Map<String, Preset> presets = read();
        presets.remove(name);
        preferences.edit().putString(KEY_PRESETS, gson.toJson(presets)).apply();
    }

    private Map<String, Preset> read() {
        String json = preferences.getString(KEY_PRESETS, "{}");
        try {
            Map<String, Preset> map = gson.fromJson(json, mapType);
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Exception e) {
            // 预设配置损坏时允许用户继续使用页面，同时记录错误便于定位数据异常
            Log.w("CustomCommandPresetManager", "读取自定义命令预设失败，已返回空列表", e);
            return new LinkedHashMap<>();
        }
    }

    private static class Preset {
        String executionMode;
        String commandText;
        List<String> outputFileNames;
    }
}
