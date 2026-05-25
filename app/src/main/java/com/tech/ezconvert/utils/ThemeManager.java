package com.tech.ezconvert.utils;

import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * 主题管理器
 * 负责应用主题模式的持久化存储与实时切换
 */
public class ThemeManager {
    private static final String TAG = "ThemeManager";
    
    private static ThemeManager instance;
    private final ConfigManager configManager;
    
    private ThemeManager(Context context) {
        // 使用 Application Context 避免内存泄漏
        this.configManager = ConfigManager.getInstance(context.getApplicationContext());
    }
    
    // 获取 ThemeManager 单例
    public static synchronized ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context);
        }
        return instance;
    }
    
    // 应用当前保存的主题模式
    public void applySavedTheme() {
        int savedMode = configManager.getThemeMode();
        AppCompatDelegate.setDefaultNightMode(savedMode);
    }
    
    // 设置并立即应用主题模式，同时修改配置文件
    public void setThemeMode(int mode) {
        configManager.setThemeMode(mode);
        AppCompatDelegate.setDefaultNightMode(mode);
    }
    
    // 获取当前保存的主题模式
    public int getThemeMode() {
        return configManager.getThemeMode();
    }
}
