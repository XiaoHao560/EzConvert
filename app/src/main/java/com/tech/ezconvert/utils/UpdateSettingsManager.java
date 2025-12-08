package com.tech.ezconvert.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class UpdateSettingsManager {
    
    private static final String PREF_NAME = "update_settings";
    private static final String KEY_AUTO_CHECK_ENABLED = "auto_check_enabled";
    private static final String KEY_CHECK_FREQUENCY = "check_frequency";
    
    // 检查频率常量
    public static final int FREQUENCY_DISABLED = 0;
    public static final int FREQUENCY_EVERY_24_HOURS = 1;
    public static final int FREQUENCY_EVERY_LAUNCH = 2;
    
    private final SharedPreferences preferences;
    
    public UpdateSettingsManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    // 获取自动检测更新是否启用
    public boolean isAutoCheckEnabled() {
        return preferences.getBoolean(KEY_AUTO_CHECK_ENABLED, true); // 默认启用
    }
    
    // 设置自动检测更新是否启用
    public void setAutoCheckEnabled(boolean enabled) {
        preferences.edit()
            .putBoolean(KEY_AUTO_CHECK_ENABLED, enabled)
            .apply();
    }
    
    // 获取检测频率
    public int getCheckFrequency() {
        return preferences.getInt(KEY_CHECK_FREQUENCY, FREQUENCY_EVERY_LAUNCH); // 默认每次启动应用检测更新
    }
    
    // 设置检测频率
    public void setCheckFrequency(int frequency) {
        preferences.edit()
            .putInt(KEY_CHECK_FREQUENCY, frequency)
            .apply();
    }
    
    // 获取频率对应的字符串
    public static String getFrequencyString(Context context, int frequency) {
        switch (frequency) {
            case FREQUENCY_DISABLED:
                return "关闭";
            case FREQUENCY_EVERY_24_HOURS:
                return "每24小时检测";
            case FREQUENCY_EVERY_LAUNCH:
                return "每次进入应用检测";
            default:
                return "每24小时检测";
        }
    }
}