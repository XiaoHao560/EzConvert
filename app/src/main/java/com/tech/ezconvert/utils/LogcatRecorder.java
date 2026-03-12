package com.tech.ezconvert.utils;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogcatRecorder implements Application.ActivityLifecycleCallbacks {
    
    private static final String TAG = "LogcatRecorder";
    private static final String LOG_DIR = "logs";
    private static final String LOG_FILE = "logcat.txt";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB 上限
    private static final int BUFFER_SIZE = 100; // 内存缓冲区行数
    
    private static LogcatRecorder instance;
    
    private Context context;
    private ExecutorService executor;
    private Handler mainHandler;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private volatile boolean isCrashing = false;
    
    private java.lang.Process logcatProcess;
    private BufferedReader reader;
    private FileOutputStream fileOutputStream;
    private OutputStreamWriter writer;
    
    // 内存缓冲，减少 IO 次数
    private final StringBuilder lineBuffer = new StringBuilder();
    private int bufferedLines = 0;
    private final Object bufferLock = new Object();
    
    // 时间格式化
    private final SimpleDateFormat timeFormat = 
        new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault());
    
    private LogcatRecorder() {}
    
    public static synchronized LogcatRecorder getInstance() {
        if (instance == null) {
            instance = new LogcatRecorder();
        }
        return instance;
    }
    
    // 初始化并开始记录
    public void init(Context context) {
        if (isRunning.get()) return;
        
        this.context = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // 注册生命周期监听，用于应用切换时优化
        if (context instanceof Application) {
            ((Application) context).registerActivityLifecycleCallbacks(this);
        }
        
        startRecording();
    }
    
    // 开始记录 Logcat
    private void startRecording() {
        if (!isRunning.compareAndSet(false, true)) return;
        
        executor.execute(() -> {
            try {
                // 准备日志文件
                File logFile = prepareLogFile();
                if (logFile == null) {
                    android.util.Log.e(TAG, "无法创建日志文件");
                    stopRecording();
                    return;
                }
                
                // 使用 FileOutputStream 包装，支持强制同步
                fileOutputStream = new FileOutputStream(logFile, true);
                writer = new OutputStreamWriter(fileOutputStream);
                
                // 写入启动标记
                writeLine("\n\n========== 日志记录启动 " + 
                    timeFormat.format(new Date()) + " ==========\n");
                
                // 启动 logcat 进程
                startLogcatProcess();
                
                // 读取循环
                String line;
                String myPid = String.valueOf(Process.myPid());
                
                while (isRunning.get() && !isCrashing && (line = reader.readLine()) != null) {
                    // API 16+ 过滤当前应用
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        if (!line.contains(myPid)) {
                            continue;
                        }
                    }
                    
                    // 缓冲写入
                    bufferAndWrite(line);
                }
                
            } catch (Exception e) {
                android.util.Log.e(TAG, "Logcat 读取错误", e);
            } finally {
                cleanup();
            }
        });
    }
    
    // 启动 Logcat 进程
    private void startLogcatProcess() throws IOException {
        // 清除旧日志，从当前开始记录
        Runtime.getRuntime().exec("logcat -c");
        
        String[] cmd = {
            "logcat",
            "-b", "main",
            "-v", "threadtime",
            "*:D"
        };
        
        logcatProcess = Runtime.getRuntime().exec(cmd);
        reader = new BufferedReader(
            new InputStreamReader(logcatProcess.getInputStream()),
            8192
        );
    }
    
    // 缓冲写入，减少 IO 频率
    private void bufferAndWrite(String line) throws Exception {
        synchronized (bufferLock) {
            lineBuffer.append(line).append('\n');
            bufferedLines++;
            
            // 达到缓冲区上限或包含重要日志时立即写入
            if (bufferedLines >= BUFFER_SIZE || 
                line.contains("AndroidRuntime") || 
                line.contains("FATAL") ||
                line.contains("crash")) {
                
                flushBuffer();
            }
        }
    }
    
    // 刷新缓冲区到文件
    private void flushBuffer() throws Exception {
        synchronized (bufferLock) {
            if (bufferedLines > 0 && writer != null) {
                writer.write(lineBuffer.toString());
                writer.flush();
                lineBuffer.setLength(0);
                bufferedLines = 0;
            }
        }
    }
    
    // 直接写入单行 (用于标记)
    private void writeLine(String line) throws Exception {
        synchronized (bufferLock) {
            if (writer != null) {
                writer.write(line);
                writer.flush();
            }
        }
    }
    
    // 准备日志文件，处理轮转
    private File prepareLogFile() {
        File dir = new File(context.getExternalFilesDir(null), LOG_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            dir = new File(context.getFilesDir(), LOG_DIR);
            if (!dir.exists()) dir.mkdirs();
        }
        
        File logFile = new File(dir, LOG_FILE);
        
        // 检查文件大小，超过则备份
        if (logFile.exists() && logFile.length() > MAX_FILE_SIZE) {
            rotateLogFile(logFile);
        }
        
        return logFile;
    }
    
    // 日志轮转
    private void rotateLogFile(File currentFile) {
        File dir = currentFile.getParentFile();
        File oldFile = new File(dir, "logcat_old.txt");
        
        if (oldFile.exists()) {
            oldFile.delete();
        }
        
        currentFile.renameTo(oldFile);
    }
    
    // 崩溃时调用: 同步转储所有数据并强制落盘 
    public void crashFlush() {
        isCrashing = true;
        
        try {
            // 写入崩溃标记
            writeLine("\n========== 检测到崩溃，强制刷新 " + 
                timeFormat.format(new Date()) + " ==========\n");
            
            // 刷新缓冲区
            flushBuffer();
            
            // 强制同步到磁盘
            if (fileOutputStream != null) {
                fileOutputStream.flush();
                fileOutputStream.getFD().sync(); // 确保数据写入物理存储
            }
            
            android.util.Log.d(TAG, "崩溃时日志已强制落盘");
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Crash flush failed", e);
        } finally {
            cleanup();
        }
    }
    
    // 普通停止
    public void stopRecording() {
        if (!isRunning.compareAndSet(true, false)) return;
        
        executor.execute(() -> {
            try {
                writeLine("\n========== 日志记录停止 " + 
                    timeFormat.format(new Date()) + " ==========\n");
                flushBuffer();
            } catch (Exception e) {
                android.util.Log.e(TAG, "停止时写入失败", e);
            } finally {
                cleanup();
            }
        });
        
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}
    }
    
    // 清理资源
    private void cleanup() {
        isRunning.set(false);
        
        try {
            flushBuffer();
        } catch (Exception ignored) {}
        
        if (writer != null) {
            try {
                writer.close();
            } catch (Exception ignored) {}
            writer = null;
        }
        
        if (fileOutputStream != null) {
            try {
                fileOutputStream.close();
            } catch (Exception ignored) {}
            fileOutputStream = null;
        }
        
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception ignored) {}
            reader = null;
        }
        
        if (logcatProcess != null) {
            logcatProcess.destroy();
            logcatProcess = null;
        }
    }
    
    // 普通刷新 (非崩溃)
    public void flush() {
        executor.execute(() -> {
            try {
                flushBuffer();
            } catch (Exception e) {
                android.util.Log.e(TAG, "Flush failed", e);
            }
        });
    }
    
    // 获取当前日志文件路径
    public String getLogFilePath() {
        File dir = new File(context.getExternalFilesDir(null), LOG_DIR);
        File file = new File(dir, LOG_FILE);
        return file.getAbsolutePath();
    }
    
    //           Activity 生命周期
    
    @Override
    public void onActivityCreated(Activity activity, android.os.Bundle savedInstanceState) {}
    
    @Override
    public void onActivityStarted(Activity activity) {}
    
    @Override
    public void onActivityResumed(Activity activity) {
        flush();
    }
    
    @Override
    public void onActivityPaused(Activity activity) {
        flush();
    }
    
    @Override
    public void onActivityStopped(Activity activity) {}
    
    @Override
    public void onActivitySaveInstanceState(Activity activity, android.os.Bundle outState) {}
    
    @Override
    public void onActivityDestroyed(Activity activity) {}
}
