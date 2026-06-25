package com.tech.ezconvert.ui;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.documentfile.provider.DocumentFile;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.tech.ezconvert.BuildConfig;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.ToastUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 当应用发生未捕获异常时，由 CrashHandler 启动此 Activity
 * 用于展示崩溃摘要、完整崩溃报告，并提供重启应用和导出日志的功能
 */
public class CrashActivity extends BaseActivity {

    // 崩溃报告的临时文件名（CrashHandler 会覆盖写入此文件，仅保留最新一次崩溃）
    private static final String CRASH_REPORT_FILE = "crash_report_current.txt";
    private static final String LOG_DIR = "logs";

    private TextView crashTypeText;
    private TextView crashMessageText;
    private TextView crashLocationText;
    private TextView crashReportText;
    private MaterialButton btnRestart;
    private MaterialButton btnExport;

    // SAF 目录选择器
    private ActivityResultLauncher<Uri> openDocumentTreeLauncher;

    @Override
    protected int getTitleContainerId() {
        return R.id.title_container;
    }

    @Override
    protected int getScrollContentId() {
        return R.id.scroll_content;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash);

        // 设置标题栏
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.crash_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initViews();
        initLauncher();
        loadCrashInfo();
        setupListeners();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.title_container);
        // 返回按钮直接结束当前任务栈（崩溃后不应返回之前的 Activity）
        toolbar.setNavigationOnClickListener(v -> finishAffinity());

        crashTypeText = findViewById(R.id.crash_type_text);
        crashMessageText = findViewById(R.id.crash_message_text);
        crashLocationText = findViewById(R.id.crash_location_text);
        crashReportText = findViewById(R.id.crash_report_text);
        btnRestart = findViewById(R.id.btn_restart);
        btnExport = findViewById(R.id.btn_export);
    }

    /**
     * 初始化 SAF 目录选择器
     */
    private void initLauncher() {
        openDocumentTreeLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri != null) {
                    // 获取持久化读写权限，避免下次使用时权限失效
                    getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                    performExport(uri);
                }
            }
        );
    }

    /**
     * 加载崩溃信息
     * 从 Intent  extras 读取崩溃摘要（类型、消息、位置）
     * 从 crash_report_current.txt 读取完整崩溃报告
     */
    private void loadCrashInfo() {
        Intent intent = getIntent();
        String type = intent.getStringExtra("crash_type");
        String message = intent.getStringExtra("crash_message");
        String file = intent.getStringExtra("crash_file");
        String className = intent.getStringExtra("crash_class");
        String method = intent.getStringExtra("crash_method");
        int line = intent.getIntExtra("crash_line", 0);

        // 显示异常类型
        crashTypeText.setText(getString(R.string.crash_type_label) + ": " 
            + (type != null ? type : getString(R.string.unknown)));
        // 显示异常描述
        crashMessageText.setText(getString(R.string.crash_message_label) + ": " 
            + (message != null ? message : getString(R.string.none)));
        
        // 显示崩溃位置（文件名、行号、类名、方法名）
        if (file != null) {
            String location = file + ":" + line + "\n" + className + "." + method + "()";
            crashLocationText.setText(getString(R.string.crash_location_label) + ": " + location);
        } else {
            crashLocationText.setText(getString(R.string.crash_location_label) + ": " 
                + getString(R.string.unknown));
        }

        // 读取并显示完整崩溃报告
        String report = readCurrentCrashReport();
        crashReportText.setText(report.isEmpty() ? getString(R.string.crash_no_report) : report);
    }

    /**
     * 读取 CrashHandler 写入的当前崩溃报告文件
     * 返回报告内容字符串，若文件不存在或读取失败则返回空字符串或错误信息
     */
    private String readCurrentCrashReport() {
        File logDir = new File(getExternalFilesDir(null), LOG_DIR);
        File file = new File(logDir, CRASH_REPORT_FILE);
        if (!file.exists()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            return getString(R.string.crash_read_failed) + e.getMessage();
        }
        return sb.toString();
    }

    private void setupListeners() {
        btnRestart.setOnClickListener(v -> restartApp());
        btnExport.setOnClickListener(v -> openDocumentTreeLauncher.launch(null));
    }

    // 拦截返回键：崩溃后按返回不应回到之前的 Activity，直接结束任务栈
    @Override
    public void onBackPressed() {
        finishAffinity();
    }

    // 拦截 Toolbar 返回按钮，行为与物理返回键一致
    @Override
    public boolean onSupportNavigateUp() {
        finishAffinity();
        return true;
    }

    // 重启应用
    private void restartApp() {
        Intent intent = Intent.makeRestartActivityTask(
            new android.content.ComponentName(this, com.tech.ezconvert.MainActivity.class));
        startActivity(intent);
        
        // 结束当前崩溃进程，让系统在全新进程中启动 MainActivity
        Process.killProcess(Process.myPid());
        System.exit(1);
    }

    /**
     * 将日志导出到用户选择的目录
     * 在后台线程中将 logs 目录下所有文件打包为 ZIP，并额外写入 device_info.txt
     */
    private void performExport(Uri treeUri) {
        new Thread(() -> {
            try {
                // 检查日志目录是否存在
                File logDir = new File(getExternalFilesDir(null), LOG_DIR);
                if (!logDir.exists() || !logDir.isDirectory()) {
                    runOnUiThread(() -> ToastUtils.show(this, getString(R.string.crash_log_dir_missing)));
                    return;
                }

                // 验证所选目录可写
                DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);
                if (pickedDir == null || !pickedDir.canWrite()) {
                    runOnUiThread(() -> ToastUtils.show(this, getString(R.string.crash_dir_access_denied)));
                    return;
                }

                // 创建带时间戳的 ZIP 文件
                String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())
                    .format(new Date());
                String fileName = "crash_logs_" + timeStamp + ".zip";

                DocumentFile newFile = pickedDir.createFile("application/zip", fileName);
                if (newFile == null) {
                    runOnUiThread(() -> ToastUtils.show(this, getString(R.string.crash_create_file_failed)));
                    return;
                }

                // 写入 ZIP 内容
                try (OutputStream os = getContentResolver().openOutputStream(newFile.getUri())) {
                    if (os == null) {
                        runOnUiThread(() -> ToastUtils.show(this, getString(R.string.crash_open_stream_failed)));
                        return;
                    }
                    zipLogsToStream(logDir, os);
                }

                // 通知用户导出成功
                final String successPath = pickedDir.getName() + "/" + fileName;
                runOnUiThread(() -> ToastUtils.show(this, 
                    getString(R.string.crash_export_success, successPath)));

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> ToastUtils.show(this, 
                    getString(R.string.crash_export_failed, e.getMessage())));
            }
        }).start();
    }

    /**
     * 将日志目录压缩到输出流
     * ZIP 内包含：
     * - device_info.txt：设备与应用信息
     * - logs 目录下所有文件（Crash.log、EzConvert.log、FFmpeg.log 等）
     */
    private void zipLogsToStream(File sourceDir, OutputStream outputStream) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            // 先写入 device_info.txt
            ZipEntry deviceInfoEntry = new ZipEntry("device_info.txt");
            zos.putNextEntry(deviceInfoEntry);
            byte[] deviceInfoBytes = buildDeviceInfo().getBytes("UTF-8");
            zos.write(deviceInfoBytes);
            zos.closeEntry();

            // 再写入 logs 目录下所有文件
            File[] files = sourceDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        addFileToZip(file, file.getName(), zos);
                    }
                }
            }
        }
    }

    /**
     * 将单个文件添加到 ZIP 输出流
     */
    private void addFileToZip(File file, String entryName, ZipOutputStream zos) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            ZipEntry zipEntry = new ZipEntry(entryName);
            zos.putNextEntry(zipEntry);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) >= 0) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();
        }
    }

    /**
     * 构建设备与应用信息文本
     */
    private String buildDeviceInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 设备信息 ===\n");
        sb.append("设备名称: ").append(android.os.Build.BRAND).append(" ").append(android.os.Build.MODEL).append("\n");
        sb.append("设备型号: ").append(android.os.Build.MODEL).append("\n");
        sb.append("安卓版本: ").append(android.os.Build.VERSION.RELEASE)
          .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        sb.append("系统版本: ").append(android.os.Build.DISPLAY).append("\n");
        sb.append("CPU架构: ")
          .append(android.os.Build.SUPPORTED_ABIS.length > 0 ? android.os.Build.SUPPORTED_ABIS[0] : android.os.Build.CPU_ABI)
          .append("\n");
        sb.append("\n=== 应用信息 ===\n");
        sb.append("版本名称: ").append(BuildConfig.VERSION_NAME).append("\n");
        sb.append("版本号: ").append(BuildConfig.VERSION_CODE).append("\n");
        sb.append("Commit: ").append(getCommitHash()).append("\n");
        return sb.toString();
    }

    /**
     * 通过反射获取 BuildConfig 中定义的 GIT_COMMIT 字段
     * 若字段不存在则返回 "null"
     */
    private String getCommitHash() {
        try {
            Field field = BuildConfig.class.getField("GIT_COMMIT");
            return (String) field.get(null);
        } catch (Exception e) {
            return "null";
        }
    }
}
