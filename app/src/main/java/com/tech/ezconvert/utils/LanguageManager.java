package com.tech.ezconvert.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import android.util.DisplayMetrics;

import androidx.core.os.LocaleListCompat;

import java.util.Locale;

/**
 * 应用语言管理器
 */
public class LanguageManager {
    
    private static final String TAG = "LanguageManager";
    
    // 语言代码常量
    public static final String LANG_SYSTEM = "system";
    public static final String LANG_ZH = "zh";
    public static final String LANG_EN = "en";
    
    private LanguageManager() {
        // 工具类，禁止实例化
    }
    
    /**
     * 获取当前应用语言
     */
    public static String getCurrentLanguage(Context context) {
        return ConfigManager.getInstance(context).getAppLanguage();
    }
    
    /**
     * 设置应用语言并立即生效
     * 使用 AppCompatDelegate.setApplicationLocales()，系统自动处理 Activity 重建
     * 
     * @param activity 当前 Activity
     * @param languageCode "system"(跟随系统), "zh"(中文), "en"(英文)
     */
    public static void setAppLanguage(Activity activity, String languageCode) {
        // 保存到配置文件
        ConfigManager.getInstance(activity).setAppLanguage(languageCode);
        
        LocaleListCompat localeList;
        if (LANG_SYSTEM.equals(languageCode)) {
            localeList = LocaleListCompat.getEmptyLocaleList();
        } else if (LANG_ZH.equals(languageCode)) {
            localeList = LocaleListCompat.forLanguageTags("zh-CN");
        } else if (LANG_EN.equals(languageCode)) {
            localeList = LocaleListCompat.forLanguageTags("en");
        } else {
            localeList = LocaleListCompat.getEmptyLocaleList();
        }
        
        // AppCompat 1.6+ API，自动持久化，低版本回退到 SharedPreferences
        // 调用后自动触发 Activity 重建，无需手动 recreate()
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList);
        
        Log.i(TAG, "Language changed to: " + languageCode);
    }
    
    /**
     * 获取语言对应的 Locale
     */
    public static Locale getLocaleForLanguage(String languageCode) {
        if (LANG_ZH.equals(languageCode)) {
            return Locale.SIMPLIFIED_CHINESE;
        } else if (LANG_EN.equals(languageCode)) {
            return Locale.ENGLISH;
        }
        return Locale.getDefault();
    }
    
    /**
     * 在 Application/Activity 初始化时应用保存的语言设置
     * 需要在 attachBaseContext 中调用
     */
    public static Context applySavedLanguage(Context context) {
        String savedLanguage = ConfigManager.getInstance(context).getAppLanguage();
        
        if (LANG_SYSTEM.equals(savedLanguage)) {
            return context;
        }
        
        Locale locale = getLocaleForLanguage(savedLanguage);
        Locale.setDefault(locale);
        
        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        DisplayMetrics displayMetrics = resources.getDisplayMetrics();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale);
            configuration.setLocales(new LocaleList(locale));
            return context.createConfigurationContext(configuration);
        } else {
            configuration.locale = locale;
            resources.updateConfiguration(configuration, displayMetrics);
            return context;
        }
    }
}
