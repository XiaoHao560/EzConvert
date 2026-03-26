package com.tech.ezconvert.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.*;
import androidx.recyclerview.widget.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tech.ezconvert.BuildConfig;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.LogManager;
import com.tech.ezconvert.utils.ToastUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class LogViewerActivity extends BaseActivity {

    @Override
    protected int getTitleContainerId() {
        return R.id.title_container;
    }
    
    @Override
    protected int getScrollContentId() {
        return R.id.scroll_content;
    }

    private LogManager logManager;
    
    // 设备信息相关
    private LinearLayout deviceInfoHeader;
    private LinearLayout deviceInfoContent;
    private ImageView deviceInfoExpandIcon;
    private TextView deviceInfoSummary;
    private TextView deviceNameText;
    private TextView deviceModelText;
    private TextView androidVersionText;
    private TextView systemVersionText;
    private TextView cpuArchText;
    private TextView appVersionNameText;
    private TextView appVersionCodeText;
    private TextView appCommitHashText;
    private boolean isDeviceInfoExpanded = false;
    
    // 应用日志相关
    private LogAdapter appLogAdapter;
    private RecyclerView appLogRecyclerView;
    private ImageView appLogExpandIcon;
    private LinearLayout appLogHeader;
    private TextView appLogCountText;
    private boolean isAppLogExpanded = false;  // 默认收起
    
    // FFmpeg日志相关
    private LogAdapter ffmpegLogAdapter;
    private RecyclerView ffmpegLogRecyclerView;
    private ImageView ffmpegLogExpandIcon;
    private LinearLayout ffmpegLogHeader;
    private TextView ffmpegLogCountText;
    private boolean isFfmpegLogExpanded = false;  // 默认收起

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_viewer);

        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("运行日志");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        logManager = LogManager.getInstance(this);
        
        initViews(); // 初始化视图
        setupListeners(); // 设置监听器
        loadDeviceInfo(); // 加载设备信息
        refreshLogDisplay(); // 初始化加载日志 （此时RecyclerView是隐藏的）
        
        logManager.addListener(new LogManager.LogListener() {
            @Override
            public void onLogAdded(LogManager.LogEntry entry) {
                runOnUiThread(() -> {
                    if ("FFmpegLog".equals(entry.tag)) {
                        List<String> ffmpegLogs = logManager.getFfmpegLogsFromMemory();
                        ffmpegLogAdapter.updateData(ffmpegLogs);
                        ffmpegLogCountText.setText("共 " + ffmpegLogs.size() + " 条");
                        if (isFfmpegLogExpanded) {
                            ffmpegLogRecyclerView.scrollToPosition(ffmpegLogs.size() - 1);
                        }
                    } else {
                        List<String> appLogs = logManager.getAppLogsFromMemory();
                        appLogAdapter.updateData(appLogs);
                        appLogCountText.setText("共 " + appLogs.size() + " 条");
                        if (isAppLogExpanded) {
                            appLogRecyclerView.scrollToPosition(appLogs.size() - 1);
                        }
                    }
                });
            }
            
            @Override
            public void onLogsCleared() {
                runOnUiThread(() -> refreshLogDisplay());
            }
        });
    }

    // 应用日志
    private void initViews() {
        // 设备信息视图
        deviceInfoHeader = findViewById(R.id.device_info_header);
        deviceInfoContent = findViewById(R.id.device_info_content);
        deviceInfoExpandIcon = findViewById(R.id.device_info_expand_icon);
        deviceInfoSummary = findViewById(R.id.device_info_summary);
        deviceNameText = findViewById(R.id.device_name);
        deviceModelText = findViewById(R.id.device_model);
        androidVersionText = findViewById(R.id.android_version);
        systemVersionText = findViewById(R.id.system_version);
        cpuArchText = findViewById(R.id.cpu_arch);
        appVersionNameText = findViewById(R.id.app_version_name);
        appVersionCodeText = findViewById(R.id.app_version_code);
        appCommitHashText = findViewById(R.id.app_commit_hash);
        
        // 默认收起状态
        deviceInfoContent.setVisibility(View.GONE);
        deviceInfoExpandIcon.setRotation(-90);
        
        // 应用日志视图
        appLogRecyclerView = findViewById(R.id.app_log_recycler_view);
        appLogExpandIcon = findViewById(R.id.app_log_expand_icon);
        appLogHeader = findViewById(R.id.app_log_header);
        appLogCountText = findViewById(R.id.app_log_count_text);
        
        // 默认收起状态
        appLogRecyclerView.setVisibility(View.GONE);
        appLogExpandIcon.setRotation(-90);  // 收起
        
        appLogAdapter = new LogAdapter(new ArrayList<>());
        appLogRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        appLogRecyclerView.setAdapter(appLogAdapter);
        
        // FFmpeg日志视图
        ffmpegLogRecyclerView = findViewById(R.id.ffmpeg_log_recycler_view);
        ffmpegLogExpandIcon = findViewById(R.id.ffmpeg_log_expand_icon);
        ffmpegLogHeader = findViewById(R.id.ffmpeg_log_header);
        ffmpegLogCountText = findViewById(R.id.ffmpeg_log_count_text);
        
        // 默认收起状态
        ffmpegLogRecyclerView.setVisibility(View.GONE);
        ffmpegLogExpandIcon.setRotation(-90);  // 收起
        
        ffmpegLogAdapter = new LogAdapter(new ArrayList<>());
        ffmpegLogRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        ffmpegLogRecyclerView.setAdapter(ffmpegLogAdapter);
    }

    // 加载设备信息
    private void loadDeviceInfo() {
        // 设备信息
        String deviceName = Build.BRAND + " " + Build.MODEL;
        String deviceModel = Build.MODEL;
        String androidVersion = Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        String systemVersion = Build.DISPLAY;
        String cpuArch = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : Build.CPU_ABI;

        deviceNameText.setText("设备名称: " + deviceName);
        deviceModelText.setText("设备型号: " + deviceModel);
        androidVersionText.setText("安卓版本: " + androidVersion);
        systemVersionText.setText("系统版本: " + systemVersion);
        cpuArchText.setText("CPU架构: " + cpuArch);

        // 应用信息
        String versionName = BuildConfig.VERSION_NAME;
        int versionCode = BuildConfig.VERSION_CODE;
        // 从 BuildConfig 获取 commit hash，如果没有则显示 "null"
        String commitHash = getCommitHash();

        appVersionNameText.setText("版本名称: " + versionName);
        appVersionCodeText.setText("版本号: " + versionCode);
        appCommitHashText.setText("Commit: " + commitHash);
        
        // 更新摘要显示
        deviceInfoSummary.setText(deviceName + " | " + cpuArch + " | v" + versionName);
    }
    
    // 获取 Commit Hash
    private String getCommitHash() {
        // 尝试从 BuildConfig 获取 GIT_COMMIT 字段
        try {
            return BuildConfig.GIT_COMMIT;
        } catch (Exception e) {
            // 如果没有定义 GIT_COMMIT，返回 null
            return "null";
        }
    }

    private void setupListeners() {
        // 设备信息卡片点击
        deviceInfoHeader.setOnClickListener(v -> {
            isDeviceInfoExpanded = !isDeviceInfoExpanded;
            toggleDeviceInfoCard(isDeviceInfoExpanded);
        });
        
        // 应用日志卡片点击
        appLogHeader.setOnClickListener(v -> {
            isAppLogExpanded = !isAppLogExpanded;
            toggleCard(appLogRecyclerView, appLogExpandIcon, isAppLogExpanded);
        });

        // FFmpeg日志卡片点击
        ffmpegLogHeader.setOnClickListener(v -> {
            isFfmpegLogExpanded = !isFfmpegLogExpanded;
            toggleCard(ffmpegLogRecyclerView, ffmpegLogExpandIcon, isFfmpegLogExpanded);
        });

        // 清空按钮
        findViewById(R.id.btn_clear_log).setOnClickListener(v -> {
            logManager.clearAllLogs();
            refreshLogDisplay();
            ToastUtils.show(this, "日志已清除");
        });

        // 复制按钮
        findViewById(R.id.btn_copy_log).setOnClickListener(v -> {
            copyAllLogs();
        });
        
        // 导出按钮
        findViewById(R.id.btn_export_log).setOnClickListener(v -> {
            exportLogsToZip();
        });
    }
    
    // 切换设备信息卡片展开/收起
    private void toggleDeviceInfoCard(boolean expand) {
        if (expand) {
            deviceInfoContent.setVisibility(View.VISIBLE);
            deviceInfoExpandIcon.animate().rotation(0).setDuration(200).start();  // 展开
        } else {
            deviceInfoContent.setVisibility(View.GONE);
            deviceInfoExpandIcon.animate().rotation(-90).setDuration(200).start();  // 收起
        }
    }

    private void toggleCard(RecyclerView recyclerView, ImageView icon, boolean expand) {
        if (expand) {
            recyclerView.setVisibility(View.VISIBLE);
            icon.animate().rotation(0).setDuration(200).start();  // 展开
        } else {
            recyclerView.setVisibility(View.GONE);
            icon.animate().rotation(-90).setDuration(200).start();  // 收起
        }
    }

    private void refreshLogDisplay() {
        //应用日志
        List<String> appLogs = logManager.getAppLogsFromMemory();
        appLogAdapter.updateData(appLogs);
        
        // 只在展开状态下滚动
        appLogCountText.setText("共 " + appLogs.size() + " 条");
        if (!appLogs.isEmpty() && isAppLogExpanded) {
            appLogRecyclerView.scrollToPosition(appLogs.size() - 1);
        }

        // FFmpeg日志
        List<String> ffmpegLogs = logManager.getFfmpegLogsFromMemory();
        ffmpegLogAdapter.updateData(ffmpegLogs);
        
        // 只在展开状态下滚动
        ffmpegLogCountText.setText("共 " + ffmpegLogs.size() + " 条");
        if (!ffmpegLogs.isEmpty() && isFfmpegLogExpanded) {
            ffmpegLogRecyclerView.scrollToPosition(ffmpegLogs.size() - 1);
        }
    }

    private void copyAllLogs() {
        StringBuilder sb = new StringBuilder();
        
        // 添加设备信息
        sb.append("=== 设备信息 ===\n");
        sb.append(deviceNameText.getText().toString()).append("\n");
        sb.append(deviceModelText.getText().toString()).append("\n");
        sb.append(androidVersionText.getText().toString()).append("\n");
        sb.append(systemVersionText.getText().toString()).append("\n");
        sb.append(cpuArchText.getText().toString()).append("\n");
        sb.append("\n=== 应用信息 ===\n");
        sb.append(appVersionNameText.getText().toString()).append("\n");
        sb.append(appVersionCodeText.getText().toString()).append("\n");
        sb.append(appCommitHashText.getText().toString()).append("\n");
        sb.append("\n=== 应用日志 ===\n");
        
        List<String> allLogs = logManager.getAllLogs();
        sb.append(String.join("\n", allLogs));
        
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("AllLogs", sb.toString());
        cm.setPrimaryClip(clip);
        ToastUtils.show(this, "日志已复制到剪贴板");
    }

    // 显示导出确认对话框
    private void exportLogsToZip() {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("导出日志")
            .setMessage("此操作将把应用的所有日志文件（包括崩溃日志、运行日志、FFmpeg 日志等）压缩成一个 ZIP 文件，保存到手机的 Download/简转/logs/ 目录下。\n\n是否继续？")
            .setPositiveButton("确认导出", (dialog, which) -> {
                // 用户确认后执行导出
                performExportLogs();
            })
            .setNegativeButton("取消", (dialog, which) -> {
                // 用户取消，什么都不做
                dialog.dismiss();
            })
            .setCancelable(true)
            .show();
    }

    // 实际执行日志导出 (后台线程)
    private void performExportLogs() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 源日志目录：Android/data/包名/files/logs/
                    File logDir = new File(getExternalFilesDir(null), "logs");
                    if (!logDir.exists() || !logDir.isDirectory()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ToastUtils.show(LogViewerActivity.this, "日志目录不存在");
                            }
                        });
                        return;
                    }

                    // 目标目录：Download/简转/logs/
                    File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File destDir = new File(downloadDir, "简转/logs");
                    if (!destDir.exists()) {
                        boolean created = destDir.mkdirs();
                        if (!created) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ToastUtils.show(LogViewerActivity.this, "无法创建目标目录");
                                }
                            });
                            return;
                        }
                    }

                    // 生成带时间戳的文件名
                    String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault()).format(new Date());
                    final File zipFile = new File(destDir, "logs_" + timeStamp + ".zip");

                    // 执行压缩（递归包含子目录）
                    zipDirectory(logDir, zipFile);

                    // 成功提示
                    final String successPath = zipFile.getAbsolutePath();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ToastUtils.show(LogViewerActivity.this, "日志已导出到:\n" + successPath);
                        }
                    });
                    
                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ToastUtils.show(LogViewerActivity.this, "导出失败: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    // 压缩整个目录
    private void zipDirectory(File sourceDir, File zipFile) throws IOException {
        FileOutputStream fos = null;
        ZipOutputStream zos = null;
        try {
            fos = new FileOutputStream(zipFile);
            zos = new ZipOutputStream(fos);
            zipFile(sourceDir, sourceDir.getName(), zos);
        } finally {
            if (zos != null) {
                try {
                    zos.close();
                } catch (IOException ignored) {}
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {}
            }
        }
    }

    // 递归压缩文件/目录
    private void zipFile(File fileToZip, String fileName, ZipOutputStream zos) throws IOException {
        // 跳过隐藏文件
        if (fileToZip.isHidden()) {
            return;
        }
        
        // 如果是目录，递归处理
        if (fileToZip.isDirectory()) {
            // 目录条目需要以 "/" 结尾
            String dirName = fileName.endsWith("/") ? fileName : fileName + "/";
            zos.putNextEntry(new ZipEntry(dirName));
            zos.closeEntry();
            
            File[] children = fileToZip.listFiles();
            if (children != null) {
                for (File childFile : children) {
                    zipFile(childFile, fileName + "/" + childFile.getName(), zos);
                }
            }
            return;
        }
        
        // 压缩文件
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(fileToZip);
            ZipEntry zipEntry = new ZipEntry(fileName);
            zos.putNextEntry(zipEntry);
            
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) >= 0) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignored) {}
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logManager.removeListener(null);
    }

    private static class LogAdapter extends RecyclerView.Adapter<LogAdapter.Holder> {
        private List<String> list;
        
        LogAdapter(List<String> list) { 
            this.list = new ArrayList<>(list); 
        }
        
        void updateData(List<String> newList) {
            this.list = new ArrayList<>(newList);
            notifyDataSetChanged();
        }
        
        @Override public Holder onCreateViewHolder(android.view.ViewGroup p, int vType) {
            TextView tv = new TextView(p.getContext());
            tv.setPadding(16, 12, 16, 12);
            tv.setTextSize(12);
            tv.setTextColor(p.getContext().getResources().getColor(R.color.text_primary));
            return new Holder(tv);
        }
        
        @Override public void onBindViewHolder(Holder h, int i) { 
            ((TextView) h.itemView).setText(list.get(i)); 
        }
        
        @Override public int getItemCount() { return list.size(); }
        
        static class Holder extends RecyclerView.ViewHolder { 
            Holder(android.view.View v) { super(v); } 
        }
    }
}
