package com.tech.ezconvert;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.widget.*;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.provider.Settings;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements FFmpegUtil.FFmpegCallback {
    
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int FILE_PICKER_REQUEST = 101;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 102;
    
    private TextView statusText, progressText, versionText;
    private ProgressBar progressBar;
    private Button selectFileBtn, convertBtn, compressBtn, extractAudioBtn;
    private Spinner formatSpinner, qualitySpinner;
    private String currentInputPath = "";
    private String currentOutputPath = "";
    private boolean permissionsGranted = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeViews();
        setupSpinners();
        
        // 显示版本信息
        String ffmpegVersion = FFmpegUtil.getVersion();
        versionText.setText("EzConvert v0.1.5 | FFmpeg: " + ffmpegVersion);
        
        // 立即检查并申请权限
        checkAndRequestPermissions();
    }
    
    private void initializeViews() {
        statusText = findViewById(R.id.status_text);
        progressText = findViewById(R.id.progress_text);
        progressBar = findViewById(R.id.progress_bar);
        versionText = findViewById(R.id.version_text);
        
        selectFileBtn = findViewById(R.id.select_file_btn);
        convertBtn = findViewById(R.id.convert_btn);
        compressBtn = findViewById(R.id.compress_btn);
        extractAudioBtn = findViewById(R.id.extract_audio_btn);
        
        formatSpinner = findViewById(R.id.format_spinner);
        qualitySpinner = findViewById(R.id.quality_spinner);
        
        selectFileBtn.setOnClickListener(v -> {
            if (permissionsGranted) {
                openFilePicker();
            } else {
                checkAndRequestPermissions();
            }
        });
        
        convertBtn.setOnClickListener(v -> {
            if (permissionsGranted && !currentInputPath.isEmpty()) {
                startConversion();
            } else {
                checkAndRequestPermissions();
            }
        });
        
        compressBtn.setOnClickListener(v -> {
            if (permissionsGranted && !currentInputPath.isEmpty()) {
                startCompression();
            } else {
                checkAndRequestPermissions();
            }
        });
        
        extractAudioBtn.setOnClickListener(v -> {
            if (permissionsGranted && !currentInputPath.isEmpty()) {
                extractAudio();
            } else {
                checkAndRequestPermissions();
            }
        });
        
        // 禁用功能按钮，等待权限授予
        setFunctionButtonsEnabled(false);
        updateStatus("正在申请存储权限...");
    }
    
    private void setupSpinners() {
        // 视频格式
        ArrayAdapter<CharSequence> formatAdapter = ArrayAdapter.createFromResource(
            this, R.array.video_formats, android.R.layout.simple_spinner_item);
        formatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        formatSpinner.setAdapter(formatAdapter);
        
        // 质量选项
        ArrayAdapter<CharSequence> qualityAdapter = ArrayAdapter.createFromResource(
            this, R.array.quality_options, android.R.layout.simple_spinner_item);
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        qualitySpinner.setAdapter(qualityAdapter);
        qualitySpinner.setSelection(1); // 默认中等质量
    }
    
    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        
        // 检查基础存储权限（所有Android版本都需要）
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        
        // 媒体权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
        }
        
        if (!permissionsNeeded.isEmpty()) {
            Log.d("Permission", "申请权限: " + permissionsNeeded);
            // 请求权限
            ActivityCompat.requestPermissions(this, 
                permissionsNeeded.toArray(new String[0]), 
                PERMISSION_REQUEST_CODE);
        } else {
            // 检查是否真的有权限访问存储
            verifyStoragePermissions();
        }
    }
    
    private void verifyStoragePermissions() {
        // 测试是否真的可以访问存储
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        boolean canWrite = downloadsDir != null && downloadsDir.canWrite();
        boolean canRead = downloadsDir != null && downloadsDir.canRead();
        
        Log.d("Permission", "存储访问测试 - 可写: " + canWrite + ", 可读: " + canRead);
        
        if (canWrite && canRead) {
            onPermissionsGranted();
        } else {
            // 如果权限已授予，实际无法访问存储，尝试申请所有文件访问权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requestAllFilesAccess();
            } else {
                updateStatus("存储权限异常，无法访问文件");
                Toast.makeText(this, 
                    "存储权限异常，请尝试重新授权", 
                    Toast.LENGTH_LONG).show();
                
                // 打开设置页面
                openAppSettings();
            }
        }
    }
    
    // 请求所有文件访问权限 (Android 11+)
    private void requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                updateStatus("需要所有文件访问权限");
                Toast.makeText(this, 
                    "请授予\"所有文件访问权限\"以正常使用应用", 
                    Toast.LENGTH_LONG).show();
                
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                } catch (Exception e) {
                    Log.e("Permission", "无法打开所有文件访问设置", e);
                    // 打开应用设置
                    openAppSettings();
                }
            } else {
                onPermissionsGranted();
            }
        }
    }
    
    private void openAppSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("Permission", "无法打开设置页面", e);
        }
    }
    
    private void onPermissionsGranted() {
        permissionsGranted = true;
        setFunctionButtonsEnabled(true);
        
        updateStatus("权限已授予，请选择媒体文件");
        Toast.makeText(this, "存储权限已获得，现在可以选择文件了", Toast.LENGTH_SHORT).show();
        Log.d("Permission", "所有权限已正确授予");
    }
    
    private void setFunctionButtonsEnabled(boolean enabled) {
        selectFileBtn.setEnabled(enabled);
        convertBtn.setEnabled(enabled && !currentInputPath.isEmpty());
        compressBtn.setEnabled(enabled && !currentInputPath.isEmpty());
        extractAudioBtn.setEnabled(enabled && !currentInputPath.isEmpty());
        
        if (!enabled) {
            selectFileBtn.setAlpha(0.5f);
            convertBtn.setAlpha(0.5f);
            compressBtn.setAlpha(0.5f);
            extractAudioBtn.setAlpha(0.5f);
        } else {
            selectFileBtn.setAlpha(1.0f);
            convertBtn.setAlpha(currentInputPath.isEmpty() ? 0.5f : 1.0f);
            compressBtn.setAlpha(currentInputPath.isEmpty() ? 0.5f : 1.0f);
            extractAudioBtn.setAlpha(currentInputPath.isEmpty() ? 0.5f : 1.0f);
        }
    }
    
    private void openFilePicker() {
        if (!permissionsGranted) {
            Toast.makeText(this, "请先授予存储权限", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        String[] mimeTypes = {"video/*", "audio/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        
        try {
            startActivityForResult(Intent.createChooser(intent, "选择媒体文件"), FILE_PICKER_REQUEST);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开文件选择器: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("MainActivity", "打开文件选择器失败", e);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == FILE_PICKER_REQUEST && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            if (uri != null) {
                currentInputPath = FileUtils.getPath(this, uri);
                if (currentInputPath != null && new File(currentInputPath).exists()) {
                    String fileName = new File(currentInputPath).getName();
                    updateStatus("已选择文件: " + fileName);
                    
                    // 显示媒体信息
                    String mediaInfo = FFmpegUtil.getMediaInfo(currentInputPath);
                    Log.d("MediaInfo", mediaInfo);
                    
                    // 生成输出路径
                    generateOutputPath();
                    
                    // 更新按钮状态
                    setFunctionButtonsEnabled(true);
                    
                    // 在状态栏显示文件信息
                    Toast.makeText(this, "已选择: " + fileName, Toast.LENGTH_SHORT).show();
                } else {
                    updateStatus("无法访问文件或文件不存在");
                    Toast.makeText(this, "无法访问文件或文件不存在", Toast.LENGTH_SHORT).show();
                    currentInputPath = "";
                }
            }
        } else if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            // 处理所有文件访问权限的返回
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    onPermissionsGranted();
                } else {
                    updateStatus("所有文件访问权限被拒绝");
                    Toast.makeText(this, 
                        "所有文件访问权限被拒绝，部分功能可能受限", 
                        Toast.LENGTH_LONG).show();
                }
            }
        }
    }
    
    private void generateOutputPath() {
        if (currentInputPath.isEmpty()) return;
        
        File inputFile = new File(currentInputPath);
        String fileName = inputFile.getName();
        String baseName = fileName.contains(".") ? 
            fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        
        String outputDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        
        // 确保下载目录存在
        File outputDirFile = new File(outputDir);
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs();
        }
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        currentOutputPath = outputDir + File.separator + baseName + "_converted_" + timestamp + ".mp4";
        
        Log.d("GeneratePath", "输出路径: " + currentOutputPath);
    }
    
    private void startConversion() {
        if (!permissionsGranted) {
            Toast.makeText(this, "请先授予存储权限", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (currentInputPath.isEmpty()) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String format = formatSpinner.getSelectedItem().toString();
        generateOutputPath();
        
        updateStatus("开始转换视频到 " + format + " 格式...");
        VideoProcessor.convertVideo(currentInputPath, currentOutputPath, format, this);
    }
    
    private void startCompression() {
        if (!permissionsGranted) {
            Toast.makeText(this, "请先授予存储权限", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (currentInputPath.isEmpty()) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String qualityStr = qualitySpinner.getSelectedItem().toString();
        int quality = getQualityValue(qualityStr);
        
        generateOutputPath();
        
        updateStatus("开始压缩视频 (" + qualityStr + ")...");
        VideoProcessor.compressVideo(currentInputPath, currentOutputPath, quality, this);
    }
    
    private void extractAudio() {
        if (!permissionsGranted) {
            Toast.makeText(this, "请先授予存储权限", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (currentInputPath.isEmpty()) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }
        
        File inputFile = new File(currentInputPath);
        String fileName = inputFile.getName();
        String baseName = fileName.contains(".") ? 
            fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            
        String outputDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        currentOutputPath = outputDir + File.separator + baseName + "_audio_" + timestamp + ".mp3";
        
        updateStatus("开始提取音频...");
        AudioProcessor.extractAudioFromVideo(currentInputPath, currentOutputPath, "mp3", this);
    }
    
    private int getQualityValue(String qualityStr) {
        switch (qualityStr) {
            case "高质量": return 90;
            case "中等质量": return 70;
            case "低质量": return 50;
            default: return 70;
        }
    }
    
    private void updateStatus(String message) {
        runOnUiThread(() -> {
            statusText.setText(message);
            Log.d("EzConvert", message);
        });
    }
    
    // FFmpegCallback
    @Override
    public void onProgress(int progress, long time) {
        runOnUiThread(() -> {
            progressBar.setProgress(progress);
            progressText.setText("进度: " + progress + "%");
        });
    }
    
    @Override
    public void onComplete(boolean success, String message) {
        runOnUiThread(() -> {
            if (success) {
                updateStatus("处理完成: " + message);
                Toast.makeText(MainActivity.this, "处理完成！输出文件: " + 
                    new File(currentOutputPath).getName(), Toast.LENGTH_LONG).show();
            } else {
                updateStatus("处理失败: " + message);
                Toast.makeText(MainActivity.this, "处理失败: " + message, Toast.LENGTH_LONG).show();
            }
            progressBar.setProgress(0);
            progressText.setText("进度: 0%");
        });
    }
    
    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            updateStatus("错误: " + error);
            Toast.makeText(MainActivity.this, "错误: " + error, Toast.LENGTH_LONG).show();
        });
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d("Permission", "权限回调: requestCode=" + requestCode + ", 权限数量=" + permissions.length);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                int result = grantResults[i];
                Log.d("Permission", "权限: " + permission + " = " + 
                    (result == PackageManager.PERMISSION_GRANTED ? "授予" : "拒绝"));
                
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                }
            }
            
            if (allGranted) {
                // 验证实际存储访问能力
                verifyStoragePermissions();
            } else {
                permissionsGranted = false;
                setFunctionButtonsEnabled(false);
                updateStatus("部分权限被拒绝，无法访问媒体文件");
                
                // 显示缺少的权限
                StringBuilder message = new StringBuilder();
                message.append("以下权限被拒绝:\n");
                for (int i = 0; i < permissions.length; i++) {
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                        message.append(getPermissionName(permissions[i])).append("\n");
                    }
                }
                message.append("\n请到应用设置中手动授予权限");
                
                Toast.makeText(this, message.toString(), Toast.LENGTH_LONG).show();
                
                // 打开设置页面
                openAppSettings();
            }
        }
    }
    
    private String getPermissionName(String permission) {
        switch (permission) {
            case Manifest.permission.READ_EXTERNAL_STORAGE:
                return "读取存储";
            case Manifest.permission.WRITE_EXTERNAL_STORAGE:
                return "写入存储";
            case Manifest.permission.READ_MEDIA_VIDEO:
                return "读取视频";
            case Manifest.permission.READ_MEDIA_AUDIO:
                return "读取音频";
            default:
                return permission;
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 当从设置页面返回时，重新检查权限
        if (!permissionsGranted) {
            checkAndRequestPermissions();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        FFmpegUtil.cancelCurrentTask();
    }
}