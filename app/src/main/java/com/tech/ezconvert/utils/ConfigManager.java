package com.tech.ezconvert.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import com.tech.ezconvert.utils.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ConfigManager {
    private static final String TAG = "ConfigManager";
    private static final String CONFIG_DIR = "config";
    private static final String SETTINGS_FILE = "settings.json";
    private static final String README_FILE = "README.md";
    private static final String OLD_CONFIG_RELATIVE_PATH = "简转/config";
    
    private static ConfigManager instance;
    private final Context context;
    private final Gson gson;
    private File configDir;
    private File settingsFile;
    private Map<String, Object> settingsMap;
    
    // 时间格式常量
    private static final String TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    
    // 配置键常量
    public static final String KEY_TRANSCODE_HARDWARE_ACCEL = "transcode_hardware_acceleration";
    public static final String KEY_TRANSCODE_MULTITHREADING = "transcode_multithreading";
    public static final String KEY_LOG_VERBOSE = "log_verbose";
    public static final String KEY_UPDATE_AUTO_CHECK = "update_auto_check_enabled";
    public static final String KEY_UPDATE_CHECK_FREQUENCY = "update_check_frequency";
    public static final String KEY_NOTIFICATION_ENABLED = "notification_enabled";
    public static final String KEY_NOTIFICATION_FIRST_REQUESTED = "notification_first_requested";
    
    private ConfigManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = createGsonInstance();
        initConfigDirectory();
        loadSettings();
    }
    
    private Gson createGsonInstance() {
        return new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Double.class, new JsonSerializer<Double>() {
                @Override
                public JsonElement serialize(Double src, Type typeOfSrc, JsonSerializationContext context) {
                    // 如果是整数值，去掉小数部分
                    if (src == src.longValue()) {
                        return context.serialize(src.longValue());
                    }
                    return context.serialize(src);
                }
            })
            .create();
    }
    
    public static synchronized ConfigManager getInstance(Context context) {
        if (instance == null) {
            instance = new ConfigManager(context);
        }
        return instance;
    }
    
    private void initConfigDirectory() {
        try {
            // 使用应用的私有外部存储目录
            // 路径: Android/data/com.tech.ezconvert/files/config/
            File externalFilesDir = context.getExternalFilesDir(null);
            if (externalFilesDir != null) {
                configDir = new File(externalFilesDir, CONFIG_DIR);
            } else {
                // 如果外部存储不可用，则使用内部存储
                configDir = new File(context.getFilesDir(), CONFIG_DIR);
            }

            // 检查并迁移旧配置
            checkAndMigrateOldConfig();

            if (!configDir.exists()) {
                boolean created = configDir.mkdirs();
                if (created) {
                    Log.i(TAG, "Config directory created: " + configDir.getAbsolutePath());
                    createReadmeFile();
                }
            }
            
            settingsFile = new File(configDir, SETTINGS_FILE);
            if (!settingsFile.exists()) {
                Log.i(TAG, "Settings file not found, creating default");
                createDefaultConfig();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize config directory", e);
            // 备用方案：使用内部存储
            configDir = new File(context.getFilesDir(), CONFIG_DIR);
            configDir.mkdirs();
            settingsFile = new File(configDir, SETTINGS_FILE);
            createDefaultConfig();
        }
    }

    private void checkAndMigrateOldConfig() {
        try {
            // 检查旧配置是否存在 (Download/简转/config/settings.json)
            File oldDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File oldConfigDir = new File(oldDownloadsDir, OLD_CONFIG_RELATIVE_PATH);
            File oldSettingsFile = new File(oldConfigDir, SETTINGS_FILE);

            if (oldSettingsFile.exists() && oldSettingsFile.canRead()) {
                Log.i(TAG, "Found old config file, migrating to new location: " + configDir.getAbsolutePath());

                // 创建新目录
                if (!configDir.exists()) {
                    configDir.mkdirs();
                }

                // 复制旧配置文件到新位置
                File newSettingsFile = new File(configDir, SETTINGS_FILE);
                if (copyFile(oldSettingsFile, newSettingsFile)) {
                    Log.i(TAG, "Config file migrated successfully");

                    // 迁移成功后，重命名旧配置文件作为备份
                    File backupFile = new File(oldConfigDir, "settings.json.backup." + 
                        new SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA).format(new Date()));
                    if (oldSettingsFile.renameTo(backupFile)) {
                        Log.i(TAG, "Old config file renamed to: " + backupFile.getName());
                    }

                    // 加载迁移后的配置
                    loadSettings();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to migrate old config", e);
        }
    }

    private boolean copyFile(File src, File dest) {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "File copy failed: " + src.getAbsolutePath() + " -> " + dest.getAbsolutePath(), e);
            return false;
        }
    }
    
    private void createReadmeFile() {
        File readmeFile = new File(configDir, README_FILE);
        try (FileWriter writer = new FileWriter(readmeFile)) {
            String readmeContent = 
                    
                    "# 简转应用配置文件夹\n\n" +
                    "此文件夹用于存储简转应用的配置文件，请勿随意修改，否则可能导致应用功能异常。\n\n" +
                    "## 存储位置\n\n" +
                    "配置路径：`Android/data/com.tech.ezconvert/files/config/`\n\n" +
                    "## 文件说明\n\n" +
                    "1. **settings.json** - 应用的主要配置文件，包含所有用户设置\n" +
                    "2. **README.md** - 本说明文件\n" +
                    "3. **settings.json.backup.*** - 自动备份的配置文件（如果有）\n\n" +
                    "## 配置项说明\n\n" +
                    "### 配置文件元数据 (app_info)\n" +
                    "- `config_version`: 配置文件结构版本\n" +
                    "- `created`: 配置文件创建时间 (格式: 年-月-日 时:分:秒)\n" +
                    "- `app_version`: 创建配置文件时的应用版本\n" +
                    "- `app_version_code`: 创建配置文件时的应用版本号\n" +
                    "- `updated`: 配置文件最近更新时间\n" +
                    "- `last_updated_with_version`: 最近更新配置文件时的应用版本\n" +
                    "- `last_updated_with_version_code`: 最近更新配置文件时的应用版本号\n\n" +
                    "### 转码设置 (transcode_settings)\n" +
                    "- `hardware_acceleration`: 硬件加速 (true/false)\n" +
                    "- `multithreading`: 多线程处理 (true/false)\n\n" +
                    "### 日志设置 (log_settings)\n" +
                    "- `verbose`: 详细日志模式 (true/false)\n\n" +
                    "### 更新设置 (update_settings)\n" +
                    "- `auto_check_enabled`: 自动检查更新 (true/false)\n" +
                    "- `check_frequency`: 检查频率 (0=关闭, 1=每24小时, 2=每次启动)\n" +
                    "- `notification_enabled`: 启用转换通知 (true/false)，默认false\n" +
                    "- `notification_first_requested`: 是否首次申请过通知权限 (true/false)\n\n" +
                    "### 通知设置说明\n" +
                    "- `notification_enabled`: 控制是否显示转换进度和完成通知\n" +
                    "- `notification_first_requested`: 记录是否已在\"更多设置\"页面首次提示过通知权限\n\n" +
                    "## 注意事项\n\n" +
                    "- 请不要手动修改 `settings.json` 文件，除非你知道自己在做什么\n" +
                    "- 如需重置设置，可删除此文件，应用将重新创建默认配置\n" +
                    "- 配置文件会在应用设置更改时自动更新\n" +
                    "- 旧版本的配置文件（如果在 Download/简转/config/ 下）已自动迁移\n\n" +
                    "## 备份建议\n\n" +
                    "如需备份设置，可复制整个config文件夹到安全位置，或使用手机系统备份功能。\n\n" +
                    "---\n" +
                    "简转应用 - 配置文件夹\n" +
                    "最后更新: " + new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
                    
            writer.write(readmeContent);
            Log.i(TAG, "README file created at: " + readmeFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to create README file", e);
        }
    }
    
    private void createDefaultConfig() {
        settingsMap = new HashMap<>();
        
        // 创建配置文件元数据
        Map<String, Object> appInfo = new HashMap<>();
        appInfo.put("config_version", 1); // 配置文件结构版本
        appInfo.put("created", getCurrentTime());
        
        // 动态获取应用版本信息
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            appInfo.put("app_version", pInfo.versionName);
            
            // 获取版本号
            long versionCode = getPackageVersionCode(pInfo);
            appInfo.put("app_version_code", versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get app version info", e);
            appInfo.put("app_version", "unknown");
            appInfo.put("app_version_code", 0L);
        }
        
        settingsMap.put("app_info", appInfo);
        
        // 默认转码设置
        Map<String, Object> transcodeSettings = new HashMap<>();
        transcodeSettings.put("hardware_acceleration", true);
        transcodeSettings.put("multithreading", true);
        settingsMap.put("transcode_settings", transcodeSettings);
        
        // 默认日志设置
        Map<String, Object> logSettings = new HashMap<>();
        logSettings.put("verbose", true);
        settingsMap.put("log_settings", logSettings);
        
        // 默认更新设置
        Map<String, Object> updateSettings = new HashMap<>();
        updateSettings.put("auto_check_enabled", true);
        updateSettings.put("check_frequency", 2); // 2 = 每次启动应用检测
        updateSettings.put("notification_enabled", false); // 默认关闭通知
        updateSettings.put("notification_first_requested", false); // 默认未申请过
        settingsMap.put("update_settings", updateSettings);
        
        saveSettings();
    }
    
    // 获取当前时间
    private String getCurrentTime() {
        return new SimpleDateFormat(TIME_FORMAT, Locale.CHINA).format(new Date());
    }
    
    // 获取版本号
    private long getPackageVersionCode(PackageInfo packageInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9 (API 28) 及以上使用 getLongVersionCode()
            return packageInfo.getLongVersionCode();
        } else {
            // Android 8.1 (API 27) 及以下使用 versionCode
            return packageInfo.versionCode;
        }
    }
    
    // 获取应用版本名称
    private String getAppVersionName() {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get app version name", e);
            return "unknown";
        }
    }
    
    // 获取应用版本号
    private long getAppVersionCode() {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return getPackageVersionCode(pInfo);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get app version code", e);
            return 0L;
        }
    }
    
    private void loadSettings() {
        try {
            if (settingsFile.exists()) {
                Type type = new TypeToken<Map<String, Object>>(){}.getType();
                try (FileReader reader = new FileReader(settingsFile)) {
                    settingsMap = gson.fromJson(reader, type);
                    if (settingsMap == null) {
                        createDefaultConfig();
                    }
                }
            } else {
                createDefaultConfig();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load settings", e);
            createDefaultConfig();
        }
    }
    
    public void saveSettings() {
        // 更新配置文件元数据
        updateAppInfo();
        
        try (FileWriter writer = new FileWriter(settingsFile)) {
            gson.toJson(settingsMap, writer);
            Log.i(TAG, "Settings saved to: " + settingsFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to save settings", e);
        }
    }
    
    private void updateAppInfo() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> appInfo = (Map<String, Object>) settingsMap.get("app_info");
            if (appInfo == null) {
                appInfo = new HashMap<>();
                settingsMap.put("app_info", appInfo);
            }
            
            appInfo.put("updated", getCurrentTime());
            
            // 更新应用版本信息
            appInfo.put("last_updated_with_version", getAppVersionName());
            appInfo.put("last_updated_with_version_code", getAppVersionCode());
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to update app info", e);
        }
    }
    
    // 通用设置获取方法
    @SuppressWarnings("unchecked")
    private <T> T getSetting(String category, String key, T defaultValue) {
        try {
            if (settingsMap == null) {
                loadSettings();
            }
            
            Map<String, Object> categoryMap = (Map<String, Object>) settingsMap.get(category);
            if (categoryMap != null && categoryMap.containsKey(key)) {
                return (T) categoryMap.get(key);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting setting: " + category + "." + key, e);
        }
        return defaultValue;
    }
    
    private void setSetting(String category, String key, Object value) {
        try {
            if (settingsMap == null) {
                loadSettings();
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> categoryMap = (Map<String, Object>) settingsMap.get(category);
            if (categoryMap == null) {
                categoryMap = new HashMap<>();
                settingsMap.put(category, categoryMap);
            }
            
            categoryMap.put(key, value);
            saveSettings(); // 保存时会自动更新元数据
        } catch (Exception e) {
            Log.e(TAG, "Error setting value: " + category + "." + key, e);
        }
    }
    
    // 获取配置文件元数据
    public Map<String, Object> getAppInfo() {
        return getSetting("app_info", null, new HashMap<>());
    }
    
    // 获取配置文件创建时间
    public String getConfigCreatedTime() {
        Map<String, Object> appInfo = getAppInfo();
        return (String) appInfo.getOrDefault("created", "unknown");
    }
    
    // 获取配置文件版本
    public int getConfigVersion() {
        Map<String, Object> appInfo = getAppInfo();
        Object version = appInfo.get("config_version");
        if (version instanceof Double) {
            return ((Double) version).intValue();
        }
        return (int) appInfo.getOrDefault("config_version", 1);
    }
    
    // 转码设置
    public boolean isHardwareAccelerationEnabled() {
        return getSetting("transcode_settings", "hardware_acceleration", true);
    }
    
    public void setHardwareAccelerationEnabled(boolean enabled) {
        setSetting("transcode_settings", "hardware_acceleration", enabled);
    }
    
    public boolean isMultithreadingEnabled() {
        return getSetting("transcode_settings", "multithreading", true);
    }
    
    public void setMultithreadingEnabled(boolean enabled) {
        setSetting("transcode_settings", "multithreading", enabled);
    }
    
    // 日志设置
    public boolean isVerboseLoggingEnabled() {
        return getSetting("log_settings", "verbose", true);
    }
    
    public void setVerboseLoggingEnabled(boolean enabled) {
        setSetting("log_settings", "verbose", enabled);
    }
    
    // 更新设置
    public boolean isAutoCheckUpdateEnabled() {
        return getSetting("update_settings", "auto_check_enabled", true);
    }
    
    public void setAutoCheckUpdateEnabled(boolean enabled) {
        setSetting("update_settings", "auto_check_enabled", enabled);
    }
    
    public int getUpdateCheckFrequency() {
        // 默认返回2（每次启动应用检测）
        Object value = getSetting("update_settings", "check_frequency", 2);
        if (value instanceof Double) {
            return ((Double) value).intValue();
        }
        return (int) value;
    }
    
    public void setUpdateCheckFrequency(int frequency) {
        setSetting("update_settings", "check_frequency", frequency);
    }
    
    // 通知设置
    public boolean isNotificationEnabled() {
        return getSetting("update_settings", "notification_enabled", false);
    }
    
    public void setNotificationEnabled(boolean enabled) {
        setSetting("update_settings", "notification_enabled", enabled);
    }
    
    public boolean isNotificationFirstRequested() {
        return getSetting("update_settings", "notification_first_requested", false);
    }
    
    public void setNotificationFirstRequested(boolean requested) {
        setSetting("update_settings", "notification_first_requested", requested);
    }
    
    // 迁移SharedPreferences设置
    public void migrateOldSettings() {
        SharedPreferences transcodePrefs = context.getSharedPreferences("EzConvertSettings", Context.MODE_PRIVATE);
        SharedPreferences logPrefs = context.getSharedPreferences("debug_settings", Context.MODE_PRIVATE);
        SharedPreferences updatePrefs = context.getSharedPreferences("update_settings", Context.MODE_PRIVATE);
        
        boolean migrated = false;
        
        // 迁移转码设置
        if (transcodePrefs.contains("hardware_acceleration")) {
            setHardwareAccelerationEnabled(
                transcodePrefs.getBoolean("hardware_acceleration", false)
            );
            migrated = true;
        }
        
        if (transcodePrefs.contains("multithreading")) {
            setMultithreadingEnabled(
                transcodePrefs.getBoolean("multithreading", true)
            );
            migrated = true;
        }
        
        // 迁移日志设置
        if (logPrefs.contains("log_verbose")) {
            setVerboseLoggingEnabled(
                logPrefs.getBoolean("log_verbose", true)
            );
            migrated = true;
        }
        
        // 迁移更新设置
        if (updatePrefs.contains("auto_check_enabled")) {
            setAutoCheckUpdateEnabled(
                updatePrefs.getBoolean("auto_check_enabled", true)
            );
            migrated = true;
        }
        
        if (updatePrefs.contains("check_frequency")) {
            setUpdateCheckFrequency(
                updatePrefs.getInt("check_frequency", 2)
            );
            migrated = true;
        }
        
        if (migrated) {
            Log.i(TAG, "Settings migrated from SharedPreferences");
            saveSettings();
        }
    }
    
    public File getConfigDirectory() {
        return configDir;
    }
    
    public String getConfigPath() {
        return configDir.getAbsolutePath();
    }
    
    public String getSettingsJson() {
        return gson.toJson(settingsMap);
    }
    
    // 备份和恢复功能
    public boolean backupConfig(String backupName) {
        try {
            File backupDir = new File(configDir, "backup");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }
            
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
            String fileName = "settings_backup_" + (backupName != null ? backupName + "_" : "") + timestamp + ".json";
            File backupFile = new File(backupDir, fileName);
            
            try (FileWriter writer = new FileWriter(backupFile)) {
                gson.toJson(settingsMap, writer);
            }
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Backup failed", e);
            return false;
        }
    }
    
    public boolean restoreConfig(File backupFile) {
        try (FileReader reader = new FileReader(backupFile)) {
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            settingsMap = gson.fromJson(reader, type);
            saveSettings();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Restore failed", e);
            return false;
        }
    }
}