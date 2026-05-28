package com.tech.ezconvert.utils;

import android.app.Activity;
import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;

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
    
    // 获取当前动态取色设置状态
    public boolean isDynamicColorEnabled() {
        return configManager.isDynamicColorEnabled();
    }
    
    // 设置动态取色开关状态
    public void setDynamicColorEnabled(boolean enabled) {
        configManager.setDynamicColorEnabled(enabled);
    }
    
    /**
     * 为指定 Activity 应用动态取色 (如果配置开启且设备支持)
     * 必须要在 Activity 的 super.onCreate() 之后，setContentView() 之前调用
     */
    public void applyDynamicColorToActivityIfNeeded(Activity activity) {
        if (configManager.isDynamicColorEnabled() && DynamicColors.isDynamicColorAvailable()) {
            DynamicColors.applyToActivityIfAvailable(activity);
        }
    }
    
    /**
     * 切换动态取色后即时刷新当前 Activity
     * 通过 recreate() 重新创建 Activity，新实例会在 onCreate() 中自动应用最新配置
     */
    public void applyDynamicColorAndRecreate(Activity activity) {
        if (!DynamicColors.isDynamicColorAvailable()) {
            return;
        }
        // 无需在此处 apply，recreate 后新 Activity 的 onCreate 会调用 applyDynamicColorToActivityIfNeeded()
        activity.recreate();
    }
}
