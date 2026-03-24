package com.tech.ezconvert.utils;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import java.io.File;

public class EzConvert extends Application {
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // 初始化崩溃捕获
        CrashHandler.getInstance().init(this);
        
        // 初始化 mmap 日志（4MB 缓冲区，单文件 50MB 滚动）
        File logDir = new File(getExternalFilesDir(null), "logs");
        NativeLogWriter.init(logDir.getAbsolutePath(), 50);
        
        // 初始化 LogManager
        LogManager.getInstance(this);
        
        // 定期刷盘（30 秒）
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                NativeLogWriter.flush();
                new Handler(Looper.getMainLooper()).postDelayed(this, 30000);
            }
        }, 30000);
    }
    
    @Override
    public void onTerminate() {
        NativeLogWriter.close();
        super.onTerminate();
    }
}
