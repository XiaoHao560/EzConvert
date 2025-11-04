package com.tech.ezconvert;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.text.SimpleDateFormat;
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
        
        // 初始按钮状态
        setFunctionButtonsEnabled(false);
        updateStatus("正在检查权限...");
        
        // 检查权限状态
        PermissionManager.checkPermissionStatus(this);
        
        handleShareIntent(getIntent());
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
        
        // 按钮点击
        setupButtonListeners(settingsBtn);
    }
    
    private void setupSpinners() {
        // 视频格式
        ArrayAdapter<CharSequence> videoAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.video_formats,
            android.R.layout.simple_spinner_item
        );
        videoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        videoFormatSpinner.setAdapter(videoAdapter);
        
        // 音频格式
        ArrayAdapter<CharSequence> audioAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.audio_formats,
            android.R.layout.simple_spinner_item
        );
        audioAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        audioFormatSpinner.setAdapter(audioAdapter);
        
        // 质量
        ArrayAdapter<CharSequence> qualityAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.quality_options,
            android.R.layout.simple_spinner_item
        );
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        qualitySpinner.setAdapter(qualityAdapter);
        
        // 默认选择
        videoFormatSpinner.setSelection(0); // MP4
        audioFormatSpinner.setSelection(0); // MP3
        qualitySpinner.setSelection(1); // 中等
    }
    
    private void setupButtonListeners(ImageButton settingsBtn) {
        settingsBtn.setOnClickListener(v -> {
            Intent settingsIntent = new Intent(MainActivity.this, SettingsMainActivity.class);
            startActivity(settingsIntent);
        });
        
        // 文件选择按键
        selectFileBtn.setOnClickListener(v -> {
            if (permissionsGranted) {
                openFilePicker();
            } else {
                Toast.makeText(this, "需要媒体访问权限才能选择文件", Toast.LENGTH_SHORT).show();
                PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
            }
        });
        
        View.OnClickListener functionButtonListener = v -> {
            if (permissionsGranted && !currentInputPath.isEmpty()) {
                handleFunctionButtonClick(v.getId());
            } else if (!permissionsGranted) {
                Toast.makeText(this, "请先授予权限并选择文件", Toast.LENGTH_SHORT).show();
                PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
            } else {
                Toast.makeText(this, "请先选择文件", Toast.LENGTH_SHORT).show();
            }
        };
        
        convertBtn.setOnClickListener(functionButtonListener);
        compressBtn.setOnClickListener(functionButtonListener);
        extractAudioBtn.setOnClickListener(functionButtonListener);
        cutVideoBtn.setOnClickListener(functionButtonListener);
        screenshotBtn.setOnClickListener(functionButtonListener);
        convertAudioBtn.setOnClickListener(functionButtonListener);
        cutAudioBtn.setOnClickListener(functionButtonListener);
    }
    
    private void handleFunctionButtonClick(int viewId) {
        if (viewId == R.id.convert_btn) {
            startConversion();
        } else if (viewId == R.id.compress_btn) {
            startCompression();
        } else if (viewId == R.id.extract_audio_btn) {
            extractAudio();
        } else if (viewId == R.id.convert_audio_btn) {
            convertAudio();
        } else if (viewId == R.id.cut_video_btn) {
            showCutVideoDialog();
        } else if (viewId == R.id.screenshot_btn) {
            showScreenshotDialog();
        } else if (viewId == R.id.cut_audio_btn) {
            showCutAudioDialog();
        } else {
            Log.w("MainActivity", "未知的按钮ID: " + viewId);
        }
    }
    
    // 权限授予回调
    public void onPermissionsGranted() {
        permissionsGranted = true;
        setFunctionButtonsEnabled(true);
        // 只有在没有选择文件时才显示默认状态
        if (currentInputPath.isEmpty()) {
            updateStatus("权限已授予，请选择媒体文件");
        }
        Log.d("MainActivity", "权限检测通过");
        
        // 释放选择文件按键
        selectFileBtn.setEnabled(true);
        selectFileBtn.setAlpha(1.0f);
    }
    
    // 权限拒绝回调
    public void onPermissionsDenied() {
        permissionsGranted = false;
        setFunctionButtonsEnabled(false);
        updateStatus("需要媒体访问权限才能使用应用功能");
        Log.d("MainActivity", "权限被拒绝");
        
        // 禁用选择文件按键
        selectFileBtn.setEnabled(false);
        selectFileBtn.setAlpha(0.5f);
        
        Toast.makeText(this, 
            "需要媒体访问权限才能选择和处理文件", 
            Toast.LENGTH_LONG).show();
    }
    
    // 状态更新方法（PermissionManager调用）
    public void updateStatus(String message) {
        runOnUiThread(() -> {
            statusText.setText(message);
            Log.d("EzConvert", message);
        });
    }
    
    private void setFunctionButtonsEnabled(boolean enabled) {
        boolean hasFileSelected = !currentInputPath.isEmpty();
        
        convertBtn.setEnabled(enabled && hasFileSelected);
        compressBtn.setEnabled(enabled && hasFileSelected);
        extractAudioBtn.setEnabled(enabled && hasFileSelected);
        cutVideoBtn.setEnabled(enabled && hasFileSelected);
        screenshotBtn.setEnabled(enabled && hasFileSelected);
        convertAudioBtn.setEnabled(enabled && hasFileSelected);
        cutAudioBtn.setEnabled(enabled && hasFileSelected);
        
        float alpha = enabled && hasFileSelected ? 1.0f : 0.5f;
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
            PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
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
                    
                    // 更新按键状态
                    setFunctionButtonsEnabled(permissionsGranted);
                    
                    Toast.makeText(this, "已选择: " + fileName, Toast.LENGTH_SHORT).show();
                } else {
                    updateStatus("无法访问文件或文件不存在");
                    Toast.makeText(this, "无法访问文件或文件不存在", Toast.LENGTH_SHORT).show();
                    currentInputPath = "";
                    setFunctionButtonsEnabled(permissionsGranted);
                }
            }
        } else if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            // 处理所有文件访问权限的返回
            PermissionManager.checkPermissionStatus(this);
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
            PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
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
            PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
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
            PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
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
            PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
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
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("裁剪视频");
        
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
                
            String outputDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + File.separator + "简转";
            
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
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("视频截图");
        
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
                
            String outputDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + File.separator + "简转";
            
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
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("裁剪音频");
        
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
                
            String outputDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + File.separator + "简转";
            
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
        PermissionManager.handlePermissionResult(this, requestCode, permissions, grantResults);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 从设置页面返回时，重新检查权限
        PermissionManager.checkPermissionStatus(this);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        FFmpegUtil.cancelCurrentTask();
    }

    private void handleShareIntent(Intent intent) {
        String action = intent.getAction();
        String type   = intent.getType();
        
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if (type.startsWith("video/") || type.startsWith("audio/")) {
                Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri != null) {
                    String path = FileUtils.getPath(this, uri);
                    if (path != null && new File(path).exists()) {
                        currentInputPath = path;
                        currentOutputPath = generateShareOutputPath(path);
                        updateStatus("已接收分享：" + new File(path).getName());
                        setFunctionButtonsEnabled(permissionsGranted);
                    } else {
                        Toast.makeText(this, "无法解析分享文件", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    private String generateShareOutputPath(String inputPath) {
        File in = new File(inputPath);
        String base = in.getName().contains(".") ?
                      in.getName().substring(0, in.getName().lastIndexOf('.')) :
                      in.getName();
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                .getAbsolutePath() + File.separator + "简转";
        new File(dir).mkdir();
        return dir + File.separator + base + "_share_" + ts;
    }
}