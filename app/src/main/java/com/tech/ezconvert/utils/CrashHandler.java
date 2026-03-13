package com.tech.ezconvert.utils;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;

import com.tech.ezconvert.utils.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler, Application.ActivityLifecycleCallbacks {
    
    private static final String TAG = "CrashHandler";
    private static final String FILE_NAME = "crash.log";
    private static CrashHandler instance;
    
    // 系统默认的异常处理器（备份，用于兜底）
    private Thread.UncaughtExceptionHandler defaultHandler;
    private Context context;
    
    // 当前正在显示的 Activity 名称
    private String currentActivity = "Unknown";
    
    // 页面跳转路径历史记录（例如：→MainActivity→SettingsActivity）
    private String activityStack = "";
    private CrashHandler() {}
    
    public static CrashHandler getInstance() {
        if (instance == null) {
            instance = new CrashHandler();
        }
        return instance;
    }
    
    public void init(Context context) {
        
        // 保存应用上下文
        this.context = context.getApplicationContext();
        
        // 备份系统默认的异常处理器
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        
        // 设置自己为默认异常处理器，这样所有未捕获异常都会走到 uncaughtException()
        Thread.setDefaultUncaughtExceptionHandler(this);
        
        // 注册 Activity 生命周期监听，用于追踪页面路径
        if (context instanceof Application) {
            ((Application) context).registerActivityLifecycleCallbacks(this);
        }
        
        Log.d(TAG, "崩溃捕获已初始化");
    }
    
    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
    
        // 先让 logcat 崩溃刷新
        LogcatRecorder.getInstance().crashFlush();
        
        // 构建崩溃报告文本
        String crashReport = buildCrashReport(thread, ex);
        
        // 保存到本地文件
        saveToFile(crashReport);
        Log.e(TAG, "应用崩溃:\n" + crashReport);
        
        // 延迟 2 秒，确保日志写入完成
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 强制杀死进程，结束应用
        Process.killProcess(Process.myPid());
        System.exit(1);
    }
    
    private String buildCrashReport(Thread thread, Throwable ex) {
        StringBuilder sb = new StringBuilder();
        
        // 基本信息
        sb.append("【基本信息】\n");
        sb.append("崩溃时间: ").append(formatTime(new Date())).append("\n");
        sb.append("设备厂商: ").append(Build.MANUFACTURER).append("\n");
        sb.append("设备型号: ").append(Build.MODEL).append("\n");
        sb.append("安卓版本: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("应用版本: ").append(getVersionName()).append("\n");
        sb.append("进程ID: ").append(Process.myPid()).append("\n");
        sb.append("线程名: ").append(thread.getName()).append("\n\n");
        
        // 页面信息 (通过 ActivityLifecycleCallbacks 追踪)
        sb.append("【页面信息】\n");
        sb.append("当前页面: ").append(currentActivity).append("\n");
        sb.append("页面路径: ").append(activityStack.isEmpty() ? currentActivity : activityStack).append("\n\n");
        
        // 异常信息
        sb.append("【异常信息】\n");
        sb.append("异常类型: ").append(ex.getClass().getSimpleName()).append("\n");
        sb.append("异常描述: ").append(ex.getMessage() != null ? ex.getMessage() : "无").append("\n\n");
        
        // 精确定位出错位置
        sb.append("【出错位置】\n");
        StackTraceElement[] stackTrace = ex.getStackTrace();
        StackTraceElement crashPoint = null;
        
        // 遍历堆栈，找到应用包名内的第一行代码 (排除系统类)
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            if (className.startsWith(context.getPackageName())) {
                crashPoint = element;
                break;
            }
        }
        
        // 输出定位信息
        if (crashPoint != null) {
            sb.append("文件: ").append(crashPoint.getFileName()).append("\n");
            sb.append("类: ").append(crashPoint.getClassName()).append("\n");
            sb.append("方法: ").append(crashPoint.getMethodName()).append("\n");
            sb.append("行号: ").append(crashPoint.getLineNumber()).append("\n");
            sb.append("定位: ").append(crashPoint.getFileName()).append(":").append(crashPoint.getLineNumber()).append("\n");
        } else {
            sb.append("未定位到应用代码\n");
        }
        sb.append("\n");
        
        // 完整堆栈信息
        sb.append("【完整堆栈】\n");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);  // 将异常堆栈写入字符串
        sb.append(sw.toString());
        
        // 报告尾部
        sb.append("\n");
        sb.append("════════════════════════════════════════════\n");
        sb.append("                     报告结束 ").append(formatTime(new Date())).append("\n");
        sb.append("════════════════════════════════════════════\n\n\n");
        
        return sb.toString();
    }
    
    private void saveToFile(String content) {
        if (content == null || context == null) return;
        
        File dir = null;
        FileWriter writer = null;
        
        try {
            // 获取外部存储目录: Android/data/包名/files/
            File baseDir = context.getExternalFilesDir(null);
            
            if (baseDir != null) {
                dir = new File(baseDir, "logs");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
            }
            
            // 外部存储不可用，回退到内部存储
            if (dir == null || !dir.exists()) {
                dir = context.getFilesDir();
                // 内部存储也尝试创建 logs 子目录
                File logsDir = new File(dir, "logs");
                if (!logsDir.exists()) {
                    logsDir.mkdirs();
                }
                dir = logsDir;
            }
            
            // 创建日志文件
            File file = new File(dir, FILE_NAME);
            
            writer = new FileWriter(file, true); // 追加模式
            writer.write(content);
            writer.flush();
            
            android.util.Log.d(TAG, "崩溃日志已保存: " + file.getAbsolutePath());
            
        } catch (IOException e) {
            android.util.Log.e(TAG, "保存崩溃日志失败", e);
        } finally {
            // 手动关闭流
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {}
            }
        }
    }
    
    // Activity 生命周期回调
    // 用于追踪页面路径，记录用户是从哪个页面跳转到哪个页面时崩溃的
    
    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        currentActivity = activity.getClass().getSimpleName();
        updateActivityStack("→" + currentActivity);
    }
    
    @Override public void onActivityStarted(Activity activity) {}
    
    @Override 
    public void onActivityResumed(Activity activity) {
        currentActivity = activity.getClass().getSimpleName();
    }
    
    @Override public void onActivityPaused(Activity activity) {}
    
    @Override public void onActivityStopped(Activity activity) {}
    
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    
    @Override public void onActivityDestroyed(Activity activity) {}
    
    private void updateActivityStack(String activityName) {
        // 限制路径长度，防止内存无限增长
        if (activityStack.length() > 200) {
            // 截断前面的部分，保留最近的路径
            activityStack = activityStack.substring(activityStack.indexOf("→", 50));
        }
        activityStack += activityName;
    }
    
    // 格式化时间
    private String formatTime(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date);
    }
    
    private String getVersionName() {
        try {
            // 从 PackageManager 获取版本信息
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName + "(" + info.versionCode + ")";
        } catch (PackageManager.NameNotFoundException e) {
            return "Unknown";
        }
    }
}
