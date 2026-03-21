package com.tech.ezconvert.utils;

import android.app.Application;

public class EzConvert extends Application {
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // 初始化崩溃捕获
        CrashHandler.getInstance().init(this);
        
        // 初始化logcat
//        LogcatRecorder.getInstance().init(this);
        
    }
}
