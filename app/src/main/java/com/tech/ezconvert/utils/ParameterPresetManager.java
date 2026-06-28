package com.tech.ezconvert.utils;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ParameterPresetManager {
    private static final String TAG = "ParamPresetManager";
    private static final String PRESET_DIR = "parameter";
    private final File presetDir;
    private final Gson gson;

    public ParameterPresetManager(Context context) {
        // 路径: Android/data/com.tech.ezconvert/files/parameter/
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir != null) {
            presetDir = new File(externalFilesDir, PRESET_DIR);
        } else {
            presetDir = new File(context.getFilesDir(), PRESET_DIR);
        }
        if (!presetDir.exists()) {
            presetDir.mkdirs();
        }
        gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public List<String> listPresetNames() {
        List<String> names = new ArrayList<>();
        File[] files = presetDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return names;
        // 按文件名数字排序
        Arrays.sort(files, (f1, f2) -> {
            int n1 = extractNumber(f1.getName());
            int n2 = extractNumber(f2.getName());
            return Integer.compare(n1, n2);
        });
        for (File f : files) {
            String name = f.getName().replace(".json", "");
            names.add(name);
        }
        return names;
    }

    private int extractNumber(String fileName) {
        try {
            String num = fileName.replace(".json", "");
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    public ParameterData loadPreset(String presetName) {
        File file = new File(presetDir, presetName + ".json");
        if (!file.exists()) return null;
        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, ParameterData.class);
        } catch (IOException e) {
            Log.e(TAG, "加载预设失败: " + presetName, e);
            return null;
        }
    }

    public boolean savePreset(String presetName, ParameterData data) {
        File file = new File(presetDir, presetName + ".json");
        data.presetName = presetName;
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(data, writer);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "保存预设失败: " + presetName, e);
            return false;
        }
    }

    public boolean deletePreset(String presetName) {
        File file = new File(presetDir, presetName + ".json");
        return file.delete();
    }

    public String getNextPresetName() {
        List<String> names = listPresetNames();
        int max = 0;
        for (String name : names) {
            try {
                int num = Integer.parseInt(name);
                if (num > max) max = num;
            } catch (NumberFormatException ignored) {}
        }
        return String.valueOf(max + 1);
    }

    public File getPresetDir() {
        return presetDir;
    }
}