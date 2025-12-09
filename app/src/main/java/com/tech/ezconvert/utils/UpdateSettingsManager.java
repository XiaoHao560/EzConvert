package com.tech.ezconvert.utils;

import android.content.Context;

public class UpdateSettingsManager {
    
    private final ConfigManager configManager;
    
    // 检查频率常量
    public static final int FREQUENCY_DISABLED = 0;
    public static final int FREQUENCY_EVERY_24_HOURS = 1;
    public static final int FREQUENCY_EVERY_LAUNCH = 2;
    
    public UpdateSettingsManager(Context context) {
        configManager = ConfigManager.getInstance(context);
    }
    
    // 获取自动检测更新是否启用
    public boolean isAutoCheckEnabled() {
        return configManager.isAutoCheckUpdateEnabled();
    }
    
    // 设置自动检测更新是否启用
    public void setAutoCheckEnabled(boolean enabled) {
        configManager.setAutoCheckUpdateEnabled(enabled);
    }
    
    // 获取检测频率
    public int getCheckFrequency() {
        return configManager.getUpdateCheckFrequency();
    }
    
    // 设置检测频率
    public void setCheckFrequency(int frequency) {
        configManager.setUpdateCheckFrequency(frequency);
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