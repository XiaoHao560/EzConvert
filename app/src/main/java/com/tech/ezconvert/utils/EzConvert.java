package com.tech.ezconvert.utils;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import com.tech.ezconvert.utils.Log;
import java.io.File;

public class EzConvert extends Application {
    
    private static final String PREFS_NAME = "ezconvert_prefs";
    private static final String KEY_LOGCAT_AVAILABLE = "logcat_available";
    private int activityResumedCount = 0;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // 在应用启动时应用保存的主题模式
        ThemeManager themeManager = ThemeManager.getInstance(this);
        themeManager.applySavedTheme();
        
        // 初始化崩溃捕获
        CrashHandler.getInstance().init(this);
        
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
            	activityResumedCount++;
                // 只要有一个 Activity 处于 Resumed 状态，默认为前台
                ToastUtils.setForeground(true);
            }
            
            @Override
            public void onActivityPaused(Activity activity) {
            	activityResumedCount--;
                // 当所有 Activity 都暂停，才认为进入后台
                if (activityResumedCount == 0) {
                    ToastUtils.setForeground(false);
                }
            }
            
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
        
        // 清理可能残留的处理缓存 (后台线程)
        new Thread(() -> {
            CacheManager.cleanupAllCache(this);
        }).start();
        
        // 初始化 mmap 日志（4MB 缓冲区，单文件 50MB 滚动）
        File logDir = new File(getExternalFilesDir(null), "logs");
        NativeLogWriter.init(logDir.getAbsolutePath(), 50);
        
        // 初始化 LogManager
        LogManager.getInstance(this);
        
        // 初始化 LogcatRecorder
        LogcatRecorder.getInstance().init(this);
        
        // 定期刷盘（30 秒）
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                NativeLogWriter.flush();
                
                // 只有 LogcatRecorder 可用时才刷新
                if (LogcatRecorder.getInstance().isAvailable()) {
                    LogcatRecorder.getInstance().flush();
                }
                
                new Handler(Looper.getMainLooper()).postDelayed(this, 30000);
            }
        }, 30000);
    }
    
    @Override
    public void onTerminate() {
        // 只有可用时才需要关闭
        if (LogcatRecorder.getInstance().isAvailable()) {
            LogcatRecorder.getInstance().stopRecording();
        }
        NativeLogWriter.close();
        super.onTerminate();
    }
}
