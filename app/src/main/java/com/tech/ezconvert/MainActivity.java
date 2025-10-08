package com.tech.ezconvert;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.widget.*;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements FFmpegUtil.FFmpegCallback {
    
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int FILE_PICKER_REQUEST = 101;
    
    private TextView statusText, progressText, versionText;
    private ProgressBar progressBar;
    private Button selectFileBtn, convertBtn, compressBtn, extractAudioBtn;
    private Spinner formatSpinner, qualitySpinner;
    private String currentInputPath = "";
    private String currentOutputPath = "";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initializeViews();
        setupSpinners();
        checkPermissions();
        
        // 显示版本信息
        String ffmpegVersion = FFmpegUtil.getVersion();
        versionText.setText("EzConvert v0.1.1 | FFmpeg: " + ffmpegVersion);
        
        updateStatus("应用已启动，请选择媒体文件");
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
        
        selectFileBtn.setOnClickListener(v -> openFilePicker());
        convertBtn.setOnClickListener(v -> startConversion());
        compressBtn.setOnClickListener(v -> startCompression());
        extractAudioBtn.setOnClickListener(v -> extractAudio());
        
        updateStatus("请选择要处理的媒体文件");
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
    
    private void checkPermissions() {
        String[] permissions = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        
        if (ContextCompat.checkSelfPermission(this, permissions[0]) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, permissions[1]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }
    
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        String[] mimeTypes = {"video/*", "audio/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        
        startActivityForResult(Intent.createChooser(intent, "选择媒体文件"), FILE_PICKER_REQUEST);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == FILE_PICKER_REQUEST && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            if (uri != null) {
                currentInputPath = FileUtils.getPath(this, uri);
                if (currentInputPath != null) {
                    String fileName = new File(currentInputPath).getName();
                    updateStatus("已选择文件: " + fileName);
                    
                    // 显示媒体信息
                    String mediaInfo = FFmpegUtil.getMediaInfo(currentInputPath);
                    Log.d("MediaInfo", mediaInfo);
                    
                    // 生成输出路径
                    generateOutputPath();
                    
                    // 在状态栏显示文件信息
                    Toast.makeText(this, "已选择: " + fileName, Toast.LENGTH_SHORT).show();
                } else {
                    updateStatus("无法获取文件路径");
                    Toast.makeText(this, "无法获取文件路径", Toast.LENGTH_SHORT).show();
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
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        currentOutputPath = outputDir + File.separator + baseName + "_" + timestamp + ".mp4";
    }
    
    private void startConversion() {
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
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updateStatus("权限已授予，请选择文件");
            } else {
                Toast.makeText(this, "需要存储权限才能处理文件", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        FFmpegUtil.cancelCurrentTask();
    }
}