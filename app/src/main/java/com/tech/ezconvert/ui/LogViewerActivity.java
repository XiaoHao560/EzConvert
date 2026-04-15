package com.tech.ezconvert.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.tech.ezconvert.BuildConfig;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.LogManager;
import com.tech.ezconvert.utils.ToastUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class LogViewerActivity extends BaseActivity {

    private static final String PREFS_NAME = "LogExportPrefs";
    private static final String KEY_EXPORT_URI = "export_tree_uri";

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

    // SAF 相关
    private ActivityResultLauncher<Uri> openDocumentTreeLauncher;
    private TextInputEditText pathEditText;
    private Uri selectedTreeUri;
    private SharedPreferences prefs;

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
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // 初始化 SAF 启动器
        initOpenDocumentTreeLauncher();
        
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

    // 初始化目录选择启动器
    private void initOpenDocumentTreeLauncher() {
        openDocumentTreeLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri != null && pathEditText != null) {
                    selectedTreeUri = uri;
                    // 获取持久化权限
                    getContentResolver().takePersistableUriPermission(
                        uri, 
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | 
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );
                    // 转换为可读路径显示
                    String readablePath = getReadablePathFromUri(uri);
                    pathEditText.setText(readablePath);
                }
            }
        );
    }

    // 将 URI 转换为可读路径
    private String getReadablePathFromUri(Uri uri) {
        String path = getPathFromUri(uri);
        if (path != null && !path.isEmpty()) {
            return path;
        }
        
        // 备用方案: 使用 DocumentFile 名称
        DocumentFile docFile = DocumentFile.fromTreeUri(this, uri);
        if (docFile != null) {
            String displayName = docFile.getName();
            if (displayName != null) {
                // 尝试判断存储位置
                String uriString = uri.toString();
                if (uriString.contains("primary")) {
                    return "内部存储/" + displayName;
                } else {
                    // 尝试提取 SD 卡名称
                    String volumeName = extractVolumeName(uriString);
                    return volumeName + "/" + displayName;
                }
            }
        }
        
        return uri.toString();
    }

    // 通过 DocumentsContract 查询真实路径
    private String getPathFromUri(Uri uri) {
        if (DocumentsContract.isTreeUri(uri)) {
            String documentId = DocumentsContract.getTreeDocumentId(uri);
            // documentId 格式如 "primary:Download/简转" 或 "1234-5678:logs"
            
            if (documentId.contains(":")) {
                String[] parts = documentId.split(":", 2);
                String volumeId = parts[0];  // "primary" 或 "1234-5678"
                String relativePath = parts.length > 1 ? parts[1] : "";  // "Download/简转"
                
                // 转换为可读格式
                if ("primary".equals(volumeId)) {
                    return "storage/emulated/0/" + relativePath;
                } else {
                    // SD 卡或其他存储
                    return "storage/" + volumeId + "/" + relativePath;
                }
            }
        }
        return null;
    }

    // 从 URI 字符串提取卷标名称
    private String extractVolumeName(String uriString) {
        // 尝试从 URI 提取 volume ID
        if (uriString.contains("/tree/")) {
            String[] parts = uriString.split("/tree/");
            if (parts.length > 1) {
                String treePart = parts[1];
                if (treePart.contains("%3A")) {  // %3A 是 : 的 URL 编码
                    String volumeId = treePart.split("%3A")[0];
                    // 解码
                    try {
                        volumeId = URLDecoder.decode(volumeId, "UTF-8");
                    } catch (Exception e) {
                        // 忽略解码错误
                    }
                    
                    if ("primary".equals(volumeId)) {
                        return "内部存储";
                    } else {
                        return "SD卡(" + volumeId + ")";
                    }
                }
            }
        }
        return "外部存储";
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
        // 应用日志
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

    // 根据是否已保存路径决定显示哪个对话框
    private void exportLogsToZip() {
        String savedUriString = prefs.getString(KEY_EXPORT_URI, null);
        
        if (savedUriString == null) {
            // 第一次使用，显示路径选择对话框
            showPathSelectionDialog();
        } else {
            // 已有保存路径，显示确认对话框
            Uri savedUri = Uri.parse(savedUriString);
            // 验证权限是否仍然有效
            if (isUriPermissionValid(savedUri)) {
                showExportConfirmDialog(savedUri);
            } else {
                // 权限失效，重新选择
                prefs.edit().remove(KEY_EXPORT_URI).apply();
                showPathSelectionDialog();
            }
        }
    }

    // 检查 URI 权限是否有效
    private boolean isUriPermissionValid(Uri uri) {
        try {
            DocumentFile docFile = DocumentFile.fromTreeUri(this, uri);
            return docFile != null && docFile.canWrite();
        } catch (Exception e) {
            return false;
        }
    }

    // 显示路径选择对话框（首次使用或点击修改路径）
    private void showPathSelectionDialog() {
        selectedTreeUri = null;
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_path_input, null);
        TextInputLayout textInputLayout = dialogView.findViewById(R.id.text_input_layout);
        pathEditText = dialogView.findViewById(R.id.path_edit_text);
        
        textInputLayout.setHint("保存路径");
        pathEditText.setText("点击选择保存目录");
        pathEditText.setFocusable(false);
        pathEditText.setClickable(true);
        
        pathEditText.setOnClickListener(v -> {
            openDocumentTreeLauncher.launch(null);
        });
        
        textInputLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        textInputLayout.setEndIconOnClickListener(v -> {
            openDocumentTreeLauncher.launch(null);
        });

        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("导出日志")
            .setMessage("请选择保存日志文件的目录")
            .setView(dialogView)
            .setPositiveButton("保存", (dialog, which) -> {
                if (selectedTreeUri == null) {
                    ToastUtils.show(this, "请先选择保存目录");
                    return;
                }
                // 保存路径到 SharedPreferences
                prefs.edit().putString(KEY_EXPORT_URI, selectedTreeUri.toString()).apply();
                performExportToTreeUri(selectedTreeUri);
            })
            .setNegativeButton("取消", null);
            
        AlertDialog dialog = dialogBuilder.create();
        dialog.show();
        
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        
        pathEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean hasPath = s != null && !s.toString().equals("点击选择保存目录");
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(hasPath);
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    // 显示导出确认对话框（已有路径时）
    private void showExportConfirmDialog(Uri savedUri) {
        String readablePath = getReadablePathFromUri(savedUri);
        
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("导出日志")
            .setMessage("日志将导出到以下目录：\n\n" + readablePath + "\n\n文件格式：logs_时间戳.zip")
            .setPositiveButton("确认导出", (dialog, which) -> {
                performExportToTreeUri(savedUri);
            })
            .setNegativeButton("取消", null)
            .setNeutralButton("修改路径", (dialog, which) -> {
                // 清除保存的路径并显示选择对话框
                prefs.edit().remove(KEY_EXPORT_URI).apply();
                showPathSelectionDialog();
            })
            .show();
    }

    // 执行实际导出到指定树 URI
    private void performExportToTreeUri(Uri treeUri) {
        new Thread(() -> {
            try {
                // 源日志目录：Android/data/包名/files/logs/
                File logDir = new File(getExternalFilesDir(null), "logs");
                if (!logDir.exists() || !logDir.isDirectory()) {
                    runOnUiThread(() -> ToastUtils.show(this, "日志目录不存在"));
                    return;
                }

                DocumentFile pickedDir = DocumentFile.fromTreeUri(this, treeUri);
                if (pickedDir == null || !pickedDir.canWrite()) {
                    runOnUiThread(() -> ToastUtils.show(this, "无法访问所选目录"));
                    return;
                }

                // 生成带时间戳的文件名
                String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault()).format(new Date());
                String fileName = "logs_" + timeStamp + ".zip";

                DocumentFile newFile = pickedDir.createFile("application/zip", fileName);
                if (newFile == null) {
                    runOnUiThread(() -> ToastUtils.show(this, "无法创建文件"));
                    return;
                }

                // 打开输出流并写入
                try (OutputStream os = getContentResolver().openOutputStream(newFile.getUri())) {
                    if (os == null) {
                        runOnUiThread(() -> ToastUtils.show(this, "无法打开文件输出流"));
                        return;
                    }
                    
                    // 压缩日志目录到输出流
                    zipDirectoryToStream(logDir, os);
                }

                final String successPath = pickedDir.getName() + "/" + fileName;
                runOnUiThread(() -> ToastUtils.show(this, "日志已导出到:\n" + successPath));

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> ToastUtils.show(this, "导出失败: " + e.getMessage()));
            }
        }).start();
    }

    // 压缩目录到输出流（用于 SAF）
    private void zipDirectoryToStream(File sourceDir, OutputStream outputStream) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            zipFileToStream(sourceDir, sourceDir.getName(), zos);
        }
    }

    // 递归压缩文件到流
    private void zipFileToStream(File fileToZip, String fileName, ZipOutputStream zos) throws IOException {
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
                    zipFileToStream(childFile, fileName + "/" + childFile.getName(), zos);
                }
            }
            return;
        }
        
        // 压缩文件
        try (FileInputStream fis = new FileInputStream(fileToZip)) {
            ZipEntry zipEntry = new ZipEntry(fileName);
            zos.putNextEntry(zipEntry);
            
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) >= 0) {
                zos.write(buffer, 0, length);
            }
            zos.closeEntry();
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
        // 正则匹配日志级别标签
        private static final Pattern LEVEL_PATTERN = Pattern.compile("\\[(ERROR|WARN|INFO|DEBUG|VERBOSE)\\]");
        
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
            // 默认颜色，实际会在 onBindViewHolder 中动态设置
            tv.setTextColor(p.getContext().getResources().getColor(R.color.text_primary));
            return new Holder(tv);
        }
        
        @Override public void onBindViewHolder(Holder h, int i) {
            String logEntry = list.get(i);
            TextView textView = (TextView) h.itemView;
            
            // 应用带整行颜色+标签背景的富文本
            textView.setText(createStyledLog(logEntry, textView.getContext()));
        }
        
        @Override public int getItemCount() { return list.size(); }
        
        private SpannableStringBuilder createStyledLog(String logLine, Context context) {
            SpannableStringBuilder ssb = new SpannableStringBuilder(logLine);
            
            if (logLine == null || logLine.isEmpty()) {
                return ssb;
            }
            
            // 确定并应用整行文字颜色
            int lineColor = getLineColorByLevel(logLine, context);
            ssb.setSpan(
                new ForegroundColorSpan(lineColor), 
                0, logLine.length(), 
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            
            // 为日志级别标签添加圆角背景（会覆盖标签区域的整行颜色）
            Matcher matcher = LEVEL_PATTERN.matcher(logLine);
            while (matcher.find()) {
                String levelTag = matcher.group(1);
                int start = matcher.start();
                int end = matcher.end();
                
                LevelStyle style = getLevelTagStyle(levelTag, context);
                
                // RoundedBackgroundSpan 会自己绘制背景和文字，覆盖该区域之前的 ForegroundColorSpan
                ssb.setSpan(
                    new RoundedBackgroundSpan(
                        style.backgroundColor,
                        style.textColor,
                        context.getResources().getDisplayMetrics().density
                    ),
                    start, end, 
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
            
            return ssb;
        }
        
        // 获取整行文字颜色
        private int getLineColorByLevel(String logLine, Context context) {
            if (logLine.contains("[ERROR]")) {
                return getThemeColor(context, com.google.android.material.R.attr.colorError);
            } else if (logLine.contains("[WARN]")) {
                return android.graphics.Color.parseColor("#FF9800");
            } else if (logLine.contains("[INFO]")) {
                return getThemeColor(context, com.google.android.material.R.attr.colorOnSurface);
            } else if (logLine.contains("[DEBUG]") || logLine.contains("[VERBOSE]")) {
                return getThemeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant);
            }
            return getThemeColor(context, com.google.android.material.R.attr.colorOnSurface);
        }
        
        // 获取日志级别标签的样式 (背景色 + 标签内文字色)
        // 使用 MD3 Container/OnContainer 配色确保对比度
        private LevelStyle getLevelTagStyle(String level, Context context) {
            switch (level) {
                case "ERROR":
                    return new LevelStyle(
                        getThemeColor(context, com.google.android.material.R.attr.colorErrorContainer),
                        getThemeColor(context, com.google.android.material.R.attr.colorOnErrorContainer)
                    );
                case "WARN":
                    // 使用琥珀色配色，确保与 ERROR 的红色区分
                    return new LevelStyle(
                        android.graphics.Color.parseColor("#FFECB3"), // 浅琥珀背景
                        android.graphics.Color.parseColor("#BF360C")  // 深琥珀文字
                    );
                case "INFO":
                    return new LevelStyle(
                        getThemeColor(context, com.google.android.material.R.attr.colorPrimaryContainer),
                        getThemeColor(context, com.google.android.material.R.attr.colorOnPrimaryContainer)
                    );
                case "DEBUG":
                case "VERBOSE":
                    return new LevelStyle(
                        getThemeColor(context, com.google.android.material.R.attr.colorSurfaceVariant),
                        getThemeColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant)
                    );
                default:
                    return new LevelStyle(
                        getThemeColor(context, com.google.android.material.R.attr.colorSurfaceVariant),
                        getThemeColor(context, com.google.android.material.R.attr.colorOnSurface)
                    );
            }
        }
        
        // 安全获取主题属性颜色值
        private int getThemeColor(Context context, int attrResId) {
            android.util.TypedValue typedValue = new android.util.TypedValue();
            android.content.res.Resources.Theme theme = context.getTheme();
            if (theme.resolveAttribute(attrResId, typedValue, true)) {
                if (typedValue.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT 
                        && typedValue.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
                    return typedValue.data;
                }
            }
            return android.graphics.Color.GRAY;
        }
        
        // 样式数据类: 背景色 + 文字色
        private static class LevelStyle {
            final int backgroundColor;
            final int textColor;
            
            LevelStyle(int backgroundColor, int textColor) {
                this.backgroundColor = backgroundColor;
                this.textColor = textColor;
            }
        }
        
        // 自定义 Span: 绘制圆角背景 + 垂直水平居中文字
        private static class RoundedBackgroundSpan extends android.text.style.ReplacementSpan {
            private final int backgroundColor;
            private final int textColor;
            private final float cornerRadius;
            private final float paddingHorizontal;
            private final float paddingVertical;
            
            RoundedBackgroundSpan(int backgroundColor, int textColor, float density) {
                this.backgroundColor = backgroundColor;
                this.textColor = textColor;
                this.cornerRadius = 4 * density;      // 4dp 圆角
                this.paddingHorizontal = 8 * density; // 8dp 水平内边距
                this.paddingVertical = 3 * density;   // 3dp 垂直内边距
            }
            
            @Override
            public int getSize(Paint paint, CharSequence text, int start, int end, 
                             Paint.FontMetricsInt fm) {
                float textWidth = paint.measureText(text, start, end);
                return (int) (textWidth + paddingHorizontal * 2);
            }
            
            @Override
            public void draw(Canvas canvas, CharSequence text, int start, int end, 
                           float x, int top, int y, int bottom, Paint paint) {
                // 测量文字宽度
                float textWidth = paint.measureText(text, start, end);
                
                // 定义背景矩形（相对于整行的 top/bottom，加上垂直内边距）
                float bgLeft = x;
                float bgRight = x + textWidth + paddingHorizontal * 2;
                float bgTop = top + paddingVertical;
                float bgBottom = bottom - paddingVertical;
                
                RectF rect = new RectF(bgLeft, bgTop, bgRight, bgBottom);
                
                // 保存原始状态
                int previousColor = paint.getColor();
                Paint.Style previousStyle = paint.getStyle();
                boolean previousAntiAlias = paint.isAntiAlias();
                
                // 绘制圆角背景
                paint.setColor(backgroundColor);
                paint.setStyle(Paint.Style.FILL);
                paint.setAntiAlias(true);
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
                
                // 计算文字垂直居中位置
                // 获取字体度量信息
                Paint.FontMetrics fontMetrics = paint.getFontMetrics();
                // 计算文字实际高度
                float textHeight = fontMetrics.descent - fontMetrics.ascent;
                // 计算背景高度
                float bgHeight = bgBottom - bgTop;
                // 计算垂直偏移量，使文字在背景中居中
                // 公式：背景中心 + (文字高度的一半 - descent) 的微调
                float textCenterY = (bgTop + bgBottom) / 2;
                float textBaselineOffset = (textHeight / 2) - fontMetrics.descent;
                float finalY = textCenterY + textBaselineOffset;
                
                // 绘制文字（水平方向：x + 左内边距，垂直方向：finalY）
                paint.setColor(textColor);
                paint.setStyle(previousStyle);
                canvas.drawText(text, start, end, x + paddingHorizontal, finalY, paint);
                
                // 恢复画笔状态
                paint.setColor(previousColor);
                paint.setAntiAlias(previousAntiAlias);
            }
        }
        
        static class Holder extends RecyclerView.ViewHolder { 
            Holder(android.view.View v) { super(v); } 
        }
    }
}