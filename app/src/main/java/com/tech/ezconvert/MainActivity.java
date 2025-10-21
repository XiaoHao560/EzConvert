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
    private Button cutVideoBtn, screenshotBtn, convertAudioBtn, cutAudioBtn;
    private Spinner videoFormatSpinner, audioFormatSpinner, qualitySpinner;
    private String currentInputPath = "";
    private String currentOutputPath = "";
    private boolean permissionsGranted = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        FFmpegUtil.initLogging(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeViews();
        setupSpinners();
        
        // 显示版本信息
        String ffmpegVersion = FFmpegUtil.getVersion();
        versionText.setText("EzConvert v0.2.0 | FFmpeg: " + ffmpegVersion);
        
        // 立即检查权限状态
        checkPermissionStatus();
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
        cutVideoBtn = findViewById(R.id.cut_video_btn);
        screenshotBtn = findViewById(R.id.screenshot_btn);
        convertAudioBtn = findViewById(R.id.convert_audio_btn);
        cutAudioBtn = findViewById(R.id.cut_audio_btn);
        
        ImageButton settingsBtn = findViewById(R.id.settings_btn);
        
        videoFormatSpinner = findViewById(R.id.video_format_spinner);
        audioFormatSpinner = findViewById(R.id.audio_format_spinner);
        qualitySpinner = findViewById(R.id.quality_spinner);
        
        settingsBtn.setOnClickListener(v -> {
            Intent settingsIntent = new Intent(MainActivity.this, SettingsMainActivity.class);
            startActivity(settingsIntent);
        });
        
        selectFileBtn.setOnClickListener(v -> {
            if (permissionsGranted) {
                openFilePicker();
            } else {
                requestNecessaryPermissions();
            }
        });
        
        convertBtn.setOnClickListener(v -> {
            if (permissionsGranted && !currentInputPath.isEmpty()) {
                startConversion();
            } else {
                requestNecessaryPermissions();
            }
        });
        
        compressBtn.setOnClickListener(v -> {
            if (permissionsGranted && !currentInputPath.isEmpty()) {
                startCompression();
            } else {
                requestNecessaryPermissions();
            }
        });
        
        extractAudioBtn.setOnClickListener(v -> {
            if (permissionsGranted && !currentInputPath.isEmpty()) {
                extractAudio();
            } else {
                requestNecessaryPermissions();
            }
        });
        
        cutVideoBtn.setOnClickListener(v -> {
            if (permissionsGranted && !currentInputPath.isEmpty()) {
                showCutVideoDialog();
            } else {
                requestNecessaryPermissions();
            }
        });
        
        screenshotBtn.setOnClickListener(v -> {
            if (permissionsGranted && !currentInputPath.isEmpty()) {
                showScreenshotDialog();
            } else {
                requestNecessaryPermissions();
            }
        });
        
        convertAudioBtn.setOnClickListener(v -> {
            if (permissionsGranted && !currentInputPath.isEmpty()) {
                convertAudio();
            } else {
                requestNecessaryPermissions();
            }
        });
        
        cutAudioBtn.setOnClickListener(v -> {
            if (permissionsGranted && !currentInputPath.isEmpty()) {
                showCutAudioDialog();
            } else {
                requestNecessaryPermissions();
            }
        });
        
        setFunctionButtonsEnabled(false);
        updateStatus("正在检查权限状态...");
    }
    
    private void setupSpinners() {
        // 视频格式
        ArrayAdapter<CharSequence> videoFormatAdapter = ArrayAdapter.createFromResource(
            this, R.array.video_formats, android.R.layout.simple_spinner_item);
        videoFormatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        videoFormatSpinner.setAdapter(videoFormatAdapter);
        
        // 音频格式
        ArrayAdapter<CharSequence> audioFormatAdapter = ArrayAdapter.createFromResource(
            this, R.array.audio_formats, android.R.layout.simple_spinner_item);
        audioFormatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        audioFormatSpinner.setAdapter(audioFormatAdapter);
        
        // 质量选项
        ArrayAdapter<CharSequence> qualityAdapter = ArrayAdapter.createFromResource(
            this, R.array.quality_options, android.R.layout.simple_spinner_item);
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        qualitySpinner.setAdapter(qualityAdapter);
        qualitySpinner.setSelection(1); // 默认中等质量
    }
    
    // 检查当前权限状态（不申请权限）
    private void checkPermissionStatus() {
        boolean hasBasicPermissions = checkBasicPermissions();
        boolean hasMediaPermissions = checkMediaPermissions();
        boolean hasStorageAccess = checkStorageAccess();
        
        Log.d("Permission", "权限状态 - 基础: " + hasBasicPermissions + 
              ", 媒体: " + hasMediaPermissions + ", 存储访问: " + hasStorageAccess);
        
        if (hasStorageAccess) {
            onPermissionsGranted();
        } else if (hasBasicPermissions || hasMediaPermissions) {
            // 有部分权限但无法访问存储，需要申请更多权限
            updateStatus("部分权限已授予，但需要更多权限来访问文件");
            requestNecessaryPermissions();
        } else {
            // 没有任何权限
            updateStatus("需要存储权限来访问媒体文件");
            requestNecessaryPermissions();
        }
    }
    
    // 检查基础存储权限
    private boolean checkBasicPermissions() {
        boolean hasRead = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED;
        boolean hasWrite = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED;
        
        Log.d("Permission", "基础权限 - 读取: " + hasRead + ", 写入: " + hasWrite);
        return hasRead && hasWrite;
    }
    
    // 检查媒体权限（Android 13+）
    private boolean checkMediaPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean hasVideo = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) 
                    == PackageManager.PERMISSION_GRANTED;
            boolean hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) 
                    == PackageManager.PERMISSION_GRANTED;
            
            Log.d("Permission", "媒体权限 - 视频: " + hasVideo + ", 音频: " + hasAudio);
            return hasVideo && hasAudio;
        }
        return true; // Android 12及以下不需要媒体权限
    }
    
    // 检查实际存储访问能力
    private boolean checkStorageAccess() {
        try {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            boolean canRead = downloadsDir != null && downloadsDir.canRead();
            boolean canWrite = downloadsDir != null && downloadsDir.canWrite();
            
            Log.d("Permission", "存储访问 - 可读: " + canRead + ", 可写: " + canWrite);
            
            // 检查所有文件访问权限（Android 11+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                boolean hasAllFilesAccess = Environment.isExternalStorageManager();
                Log.d("Permission", "所有文件访问权限: " + hasAllFilesAccess);
                return hasAllFilesAccess || (canRead && canWrite);
            }
            
            return canRead && canWrite;
        } catch (Exception e) {
            Log.e("Permission", "检查存储访问失败", e);
            return false;
        }
    }
    
    // 请求必要的权限
    private void requestNecessaryPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();
        
        // 请求基础存储权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        
        // 媒体权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO);
            }
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
        }
        
        if (!permissionsToRequest.isEmpty()) {
            Log.d("Permission", "请求权限: " + permissionsToRequest);
            ActivityCompat.requestPermissions(this, 
                permissionsToRequest.toArray(new String[0]), 
                PERMISSION_REQUEST_CODE);
        } else {
            // 如果没有权限要请求，检查是否还需要所有文件访问权限
            if (!checkStorageAccess()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    requestAllFilesAccess();
                } else {
                    updateStatus("权限异常，无法访问存储");
                    Toast.makeText(this, 
                        "权限异常，请手动在设置中授予所有存储权限", 
                        Toast.LENGTH_LONG).show();
                    openAppSettings();
                }
            } else {
                onPermissionsGranted();
            }
        }
    }
    
    // 所有文件访问权限 (Android 11+)
    private void requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                updateStatus("需要所有文件访问权限");
                
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                } catch (Exception e) {
                    Log.e("Permission", "无法打开所有文件访问设置", e);
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
        Log.d("Permission", "权限检测通过");
    }
    
    private void setFunctionButtonsEnabled(boolean enabled) {
        boolean hasFileSelected = !currentInputPath.isEmpty();
        
        selectFileBtn.setEnabled(enabled);
        convertBtn.setEnabled(enabled && hasFileSelected);
        compressBtn.setEnabled(enabled && hasFileSelected);
        extractAudioBtn.setEnabled(enabled && hasFileSelected);
        cutVideoBtn.setEnabled(enabled && hasFileSelected);
        screenshotBtn.setEnabled(enabled && hasFileSelected);
        convertAudioBtn.setEnabled(enabled && hasFileSelected);
        cutAudioBtn.setEnabled(enabled && hasFileSelected);
        
        float alpha = enabled ? 1.0f : 0.5f;
        selectFileBtn.setAlpha(alpha);
        convertBtn.setAlpha(enabled && hasFileSelected ? 1.0f : 0.5f);
        compressBtn.setAlpha(enabled && hasFileSelected ? 1.0f : 0.5f);
        extractAudioBtn.setAlpha(enabled && hasFileSelected ? 1.0f : 0.5f);
        cutVideoBtn.setAlpha(enabled && hasFileSelected ? 1.0f : 0.5f);
        screenshotBtn.setAlpha(enabled && hasFileSelected ? 1.0f : 0.5f);
        convertAudioBtn.setAlpha(enabled && hasFileSelected ? 1.0f : 0.5f);
        cutAudioBtn.setAlpha(enabled && hasFileSelected ? 1.0f : 0.5f);
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
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
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
                    
                    // 生成输出路径
                    generateOutputPath();
                    
                    // 更新按钮状态
                    setFunctionButtonsEnabled(true);
                    
                    Toast.makeText(this, "已选择: " + fileName, Toast.LENGTH_SHORT).show();
                } else {
                    updateStatus("无法访问文件或文件不存在");
                    Toast.makeText(this, "无法访问文件或文件不存在", Toast.LENGTH_SHORT).show();
                    currentInputPath = "";
                    setFunctionButtonsEnabled(true);
                }
            }
        } else if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            // 处理所有文件访问权限的返回
            checkPermissionStatus(); // 重新检查权限状态
        }
    }
    
    private void generateOutputPath() {
        if (currentInputPath.isEmpty()) return;
        
        File inputFile = new File(currentInputPath);
        String fileName = inputFile.getName();
        String baseName = fileName.contains(".") ? 
            fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        
        // 创建简转文件夹
        String outputDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + File.separator + "简转";
        
        // 检查目录
        File outputDirFile = new File(outputDir);
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs();
        }
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        currentOutputPath = outputDir + File.separator + baseName + "_converted_" + timestamp;
        
        Log.d("GeneratePath", "输出路径基础: " + currentOutputPath);
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
        
        String format = videoFormatSpinner.getSelectedItem().toString();
        generateOutputPath(); // 生成基础路径
        
        updateStatus("开始转换视频到 " + format + " 格式...");
        VideoProcessor.convertVideo(currentInputPath, currentOutputPath, format, this, this);
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
        
        generateOutputPath(); // 生成基础路径
        
        updateStatus("开始压缩视频 (" + qualityStr + ")...");
        VideoProcessor.compressVideo(currentInputPath, currentOutputPath, quality, this, this);
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
            
        // 创建简转文件夹
        String outputDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + File.separator + "简转";
        
        // 检查目录
        File outputDirFile = new File(outputDir);
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs();
        }
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        currentOutputPath = outputDir + File.separator + baseName + "_audio_" + timestamp;
        
        updateStatus("开始提取音频...");
        AudioProcessor.extractAudioFromVideo(currentInputPath, currentOutputPath, "mp3", this, this);
    }
    
    private void convertAudio() {
        if (!permissionsGranted) {
            Toast.makeText(this, "请先授予存储权限", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (currentInputPath.isEmpty()) {
            Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String format = audioFormatSpinner.getSelectedItem().toString();
        
        File inputFile = new File(currentInputPath);
        String fileName = inputFile.getName();
        String baseName = fileName.contains(".") ? 
            fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            
        // 创建简转文件夹
        String outputDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + File.separator + "简转";
        
        // 检查目录
        File outputDirFile = new File(outputDir);
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs();
        }
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        currentOutputPath = outputDir + File.separator + baseName + "_converted_" + timestamp;
        
        updateStatus("开始转换音频到 " + format + " 格式...");
        AudioProcessor.convertAudio(currentInputPath, currentOutputPath, format, this, this);
    }
    
    private void showCutVideoDialog() {
        // 创建裁剪视频的对话框
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("裁剪视频");
        
        // 创建对话框布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        final EditText startTimeInput = new EditText(this);
        startTimeInput.setHint("开始时间 (格式: 00:00:00)");
        layout.addView(startTimeInput);
        
        final EditText durationInput = new EditText(this);
        durationInput.setHint("持续时间 (格式: 00:00:10)");
        layout.addView(durationInput);
        
        builder.setView(layout);
        
        builder.setPositiveButton("开始裁剪", (dialog, which) -> {
            String startTime = startTimeInput.getText().toString();
            String duration = durationInput.getText().toString();
            
            if (startTime.isEmpty() || duration.isEmpty()) {
                Toast.makeText(this, "请输入开始时间和持续时间", Toast.LENGTH_SHORT).show();
                return;
            }
            
            File inputFile = new File(currentInputPath);
            String fileName = inputFile.getName();
            String baseName = fileName.contains(".") ? 
                fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
                
            // 创建简转文件夹
            String outputDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + File.separator + "简转";
            
            // 检查目录
            File outputDirFile = new File(outputDir);
            if (!outputDirFile.exists()) {
                outputDirFile.mkdirs();
            }
            
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            currentOutputPath = outputDir + File.separator + baseName + "_cut_" + timestamp + ".mp4";
            
            updateStatus("开始裁剪视频...");
            VideoProcessor.cutVideo(currentInputPath, currentOutputPath, startTime, duration, this, this);
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    private void showScreenshotDialog() {
        // 创建截图的对话框
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("视频截图");
        
        // 创建对话框布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        final EditText timestampInput = new EditText(this);
        timestampInput.setHint("时间点 (格式: 00:00:05)");
        layout.addView(timestampInput);
        
        builder.setView(layout);
        
        builder.setPositiveButton("截图", (dialog, which) -> {
            String timestamp = timestampInput.getText().toString();
            
            if (timestamp.isEmpty()) {
                Toast.makeText(this, "请输入时间点", Toast.LENGTH_SHORT).show();
                return;
            }
            
            File inputFile = new File(currentInputPath);
            String fileName = inputFile.getName();
            String baseName = fileName.contains(".") ? 
                fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
                
            // 创建简转文件夹
            String outputDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + File.separator + "简转";
            
            // 检查目录
            File outputDirFile = new File(outputDir);
            if (!outputDirFile.exists()) {
                outputDirFile.mkdirs();
            }
            
            String fileTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            currentOutputPath = outputDir + File.separator + baseName + "_screenshot_" + fileTimestamp + ".jpg";
            
            updateStatus("开始截图...");
            VideoProcessor.extractFrame(currentInputPath, currentOutputPath, timestamp, this, this);
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    private void showCutAudioDialog() {
        // 创建裁剪音频的对话框
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("裁剪音频");
        
        // 创建对话框布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        final EditText startTimeInput = new EditText(this);
        startTimeInput.setHint("开始时间 (格式: 00:00:00)");
        layout.addView(startTimeInput);
        
        final EditText durationInput = new EditText(this);
        durationInput.setHint("持续时间 (格式: 00:00:10)");
        layout.addView(durationInput);
        
        builder.setView(layout);
        
        builder.setPositiveButton("开始裁剪", (dialog, which) -> {
            String startTime = startTimeInput.getText().toString();
            String duration = durationInput.getText().toString();
            
            if (startTime.isEmpty() || duration.isEmpty()) {
                Toast.makeText(this, "请输入开始时间和持续时间", Toast.LENGTH_SHORT).show();
                return;
            }
            
            File inputFile = new File(currentInputPath);
            String fileName = inputFile.getName();
            String baseName = fileName.contains(".") ? 
                fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
                
            // 创建简转文件夹
            String outputDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + File.separator + "简转";
            
            // 检查目录
            File outputDirFile = new File(outputDir);
            if (!outputDirFile.exists()) {
                outputDirFile.mkdirs();
            }
            
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            currentOutputPath = outputDir + File.separator + baseName + "_cut_" + timestamp + ".mp3";
            
            updateStatus("开始裁剪音频...");
            AudioProcessor.cutAudio(currentInputPath, currentOutputPath, startTime, duration, this, this);
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
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
    
    // FFmpegCallback 实现
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
                String outputFileName = new File(currentOutputPath).getName();
                Toast.makeText(MainActivity.this, 
                    "处理完成！输出文件: " + outputFileName + "\n保存在: Download/简转/", 
                    Toast.LENGTH_LONG).show();
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
        Log.d("Permission", "权限回调: requestCode=" + requestCode);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // 重新检查权限状态
            checkPermissionStatus();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 从设置页面返回时，重新检查权限状态
        if (!permissionsGranted) {
            checkPermissionStatus();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        FFmpegUtil.cancelCurrentTask();
    }
}