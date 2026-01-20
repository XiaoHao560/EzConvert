package com.tech.ezconvert;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
//import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
//import android.util.Log;
import com.tech.ezconvert.utils.Log;
import android.view.View;
import android.view.Window;
//import android.view.WindowInsets;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.tech.ezconvert.processor.AudioProcessor;
import com.tech.ezconvert.processor.VideoProcessor;
import com.tech.ezconvert.ui.SettingsMainActivity;
import com.tech.ezconvert.ui.BaseActivity;
import com.tech.ezconvert.utils.AnimationUtils;
import com.tech.ezconvert.utils.ConfigManager;
import com.tech.ezconvert.utils.FFmpegUtil;
import com.tech.ezconvert.utils.FileUtils;
import com.tech.ezconvert.utils.LogManager;
import com.tech.ezconvert.utils.PermissionManager;
import com.tech.ezconvert.utils.ToastUtils;
import com.tech.ezconvert.utils.UpdateChecker;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends BaseActivity implements FFmpegUtil.FFmpegCallback {
    
    @Override
    protected int getTitleContainerId() {
        return R.id.title_container;
    }
    
    @Override
    protected int getScrollContentId() {
        return R.id.scroll_content;
    }
    
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 102;
    
    private TextView statusText, progressText, versionText, volumePercentText;
    private ProgressBar progressBar;
    private Button selectFileBtn, convertBtn, compressBtn, extractAudioBtn;
    private Button cutVideoBtn, screenshotBtn, convertAudioBtn, cutAudioBtn;
    private Spinner videoFormatSpinner, audioFormatSpinner, qualitySpinner, volumeSpinner;
    private SeekBar volumeSeekBar;
    private LinearLayout customVolumeLayout;
    private String currentInputPath = "";
    private String currentOutputPath = "";
    private boolean permissionsGranted = false;
    private int currentVolume = 100;
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private UpdateChecker updateChecker;
    
    private ActivityResultLauncher<Intent> filePickerLauncher;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogManager.getInstance(this);
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        FFmpegUtil.initLogging(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 初始化更新检查器
        updateChecker = new UpdateChecker(this);
        
        // 初始化配置管理器
        ConfigManager configManager = ConfigManager.getInstance(this);
        
        // 检查是否需要迁移
        SharedPreferences prefs = getSharedPreferences("EzConvertSettings", MODE_PRIVATE);
        if (prefs.getAll().size() > 0) {
            // 有旧设置，询问用户是否迁移
            showMigrationDialog();
        }
        
        setupActivityResultLaunchers();
        initializeViews();
        setupSpinners();
        
        // 初始按钮状态
        setFunctionButtonsEnabled(false);
        updateStatus("正在检查权限...");
        
        // 检查权限状态
        PermissionManager.checkPermissionStatus(this);
        
        handleShareIntent(getIntent());
        
        // 延迟2秒后自动检查更新（等待主界面加载完成）
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (updateChecker.shouldAutoCheck()) {
                updateChecker.checkForAutoUpdate();
            } else {
                Log.d("MainActivity", "自动检测更新已关闭或者未到检测时间");
            }
        }, 2000);
    }
    
    private void setupActivityResultLaunchers() {
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null) {
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
                                
                                ToastUtils.show(this, "已选择: " + fileName);
                            } else {
                                updateStatus("无法访问文件或文件不存在");
                                ToastUtils.show(this, "无法访问文件或文件不存在");
                                currentInputPath = "";
                                setFunctionButtonsEnabled(permissionsGranted);
                            }
                        }
                    }
                }
            }
        );
    }
    
    private void initializeViews() {
        statusText = findViewById(R.id.status_text);
        progressText = findViewById(R.id.progress_text);
        progressBar = findViewById(R.id.progress_bar);
        versionText = findViewById(R.id.version_text);
        
        volumeSpinner = findViewById(R.id.volume_spinner);
        volumeSeekBar = findViewById(R.id.volume_seekbar);
        volumePercentText = findViewById(R.id.volume_percent_text);
        customVolumeLayout = findViewById(R.id.custom_volume_layout);
        
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
        
        // 卡片入场动画
        setupCardAnimations();
    }
    
    private void setupCardAnimations() {
        View[] cards = {
            findViewById(R.id.status_card),
            findViewById(R.id.file_selection_card), 
            findViewById(R.id.options_card),
            findViewById(R.id.video_processing_card),
            findViewById(R.id.audio_processing_card)
        };
        
        for (int i = 0; i < cards.length; i++) {
            if (cards[i] != null) {
                AnimationUtils.animateCardEntrance(cards[i], i * 100);
            }
        }
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
        
        // 音量设置
        ArrayAdapter<CharSequence> volumeAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.volume_options,
            android.R.layout.simple_spinner_item
        );
        volumeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        volumeSpinner.setAdapter(volumeAdapter);
        volumeSpinner.setSelection(0); //默认正常音量
        
        // 音量滑动条监听
        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser) {
              currentVolume = progress;
              volumePercentText.setText(progress + "%");
              
              // 更新spinner显示为"自定义"
              if (fromUser) {
                  volumeSpinner.setSelection(3); // 自定义
              }
          }
          
          @Override
          public void onStartTrackingTouch(SeekBar seekbar) {}
          
          @Override
          public void onStopTrackingTouch(SeekBar seekbar) {}
        });
        
        volumeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0: // 正常
                        currentVolume = 100;
                        volumeSeekBar.setProgress(100);
                        customVolumeLayout.setVisibility(View.GONE);
                        break;
                    case 1: // 较低
                        currentVolume = 50;
                        volumeSeekBar.setProgress(50);
                        customVolumeLayout.setVisibility(View.GONE);
                        break;
                    case 2: //较高
                        currentVolume = 150;
                        volumeSeekBar.setProgress(150);
                        customVolumeLayout.setVisibility(View.GONE);
                        break;
                    case 3: // 自定义
                    customVolumeLayout.setVisibility(View.VISIBLE);
                    break;
                }
                volumePercentText.setText(currentVolume + "%");
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        // 默认选择
        videoFormatSpinner.setSelection(0); // MP4
        audioFormatSpinner.setSelection(0); // MP3
        qualitySpinner.setSelection(1); // 中等
    }
    
    private void setupButtonListeners(ImageButton settingsBtn) {
        // 设置按钮添加旋转动画
        settingsBtn.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            v.animate().rotationBy(180).setDuration(300).start();
            
            scheduler.schedule(() -> {
                runOnUiThread(() -> {
                    Intent settingsIntent = new Intent(MainActivity.this, SettingsMainActivity.class);
                    ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                        MainActivity.this,
                        R.anim.slide_in_right,
                        R.anim.slide_out_left
                    );
                    ActivityCompat.startActivity(MainActivity.this, settingsIntent, options.toBundle());
                });
            }, 150, TimeUnit.MILLISECONDS);
        });
        
        // 文件选择按钮
        selectFileBtn.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            if (permissionsGranted) {
                openFilePicker();
            } else {
                ToastUtils.show(this, "需要媒体访问权限才能选择文件");
                PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
            }
        });
        
        // 功能按钮统一添加动画
        View.OnClickListener functionButtonListener = v -> {
            AnimationUtils.animateButtonClick(v);
            if (permissionsGranted && !currentInputPath.isEmpty()) {
                handleFunctionButtonClick(v.getId());
            } else if (!permissionsGranted) {
                ToastUtils.show(this, "请先授予权限并选择文件");
                PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
            } else {
                ToastUtils.show(this, "请先选择文件");
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
        
        // 成功动画反馈
        AnimationUtils.animateBounce(selectFileBtn);
        
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
        
        ToastUtils.showLong(this, 
            "需要媒体访问权限才能选择和处理文件");
    }
    
    // 状态更新方法（PermissionManager调用）
    public void updateStatus(String message) {
        runOnUiThread(() -> {
            // 状态文本更新动画
            AnimationUtils.animateStatusUpdate(statusText);
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
            ToastUtils.show(this, "请先授予存储权限");
            PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
            return;
        }
        
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        
        String[] mimeTypes = {"video/*", "audio/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        
        try {
            filePickerLauncher.launch(Intent.createChooser(intent, "选择媒体文件"));
        } catch (Exception e) {
            ToastUtils.show(this, "无法打开文件选择器");
            Log.e("MainActivity", "打开文件选择器失败", e);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // 保留 MANAGE_STORAGE_REQUEST_CODE 的处理，因为这是特殊的权限请求
        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
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
            ToastUtils.show(this, "请先授予存储权限");
            PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
            return;
        }
        
        if (currentInputPath.isEmpty()) {
            ToastUtils.show(this, "请先选择文件");
            return;
        }
        
        String format = videoFormatSpinner.getSelectedItem().toString();
        generateOutputPath(); // 生成基础路径
        
        updateStatus("开始转换视频到 " + format + " 格式...");
        VideoProcessor.convertVideo(currentInputPath, currentOutputPath, format, currentVolume, this, this);
    }
    
    private void startCompression() {
        if (!permissionsGranted) {
            ToastUtils.show(this, "请先授予存储权限");
            PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
            return;
        }
        
        if (currentInputPath.isEmpty()) {
            ToastUtils.show(this, "请先选择文件");
            return;
        }
        
        String qualityStr = qualitySpinner.getSelectedItem().toString();
        int quality = getQualityValue(qualityStr);
        
        generateOutputPath(); // 生成基础路径
        
        updateStatus("开始压缩视频 (" + qualityStr + ")...");
        VideoProcessor.compressVideo(currentInputPath, currentOutputPath, quality, currentVolume, this, this);
    }
    
    private void extractAudio() {
        if (!permissionsGranted) {
            ToastUtils.show(this, "请先授予存储权限");
            PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
            return;
        }
        
        if (currentInputPath.isEmpty()) {
            ToastUtils.show(this, "请先选择文件");
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
        AudioProcessor.extractAudioFromVideo(currentInputPath, currentOutputPath, "mp3", currentVolume, this, this);
    }
    
    private void convertAudio() {
        if (!permissionsGranted) {
            ToastUtils.show(this, "请先授予存储权限");
            PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
            return;
        }
        
        if (currentInputPath.isEmpty()) {
            ToastUtils.show(this, "请先选择文件");
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
        AudioProcessor.convertAudio(currentInputPath, currentOutputPath, format, currentVolume, this, this);
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
                ToastUtils.show(this, "请输入开始时间和持续时间");
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
            VideoProcessor.cutVideo(currentInputPath, currentOutputPath, startTime, duration, currentVolume, this, this);
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
                ToastUtils.show(this, "请输入时间点");
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
                ToastUtils.show(this, "请输入开始时间和持续时间");
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
            AudioProcessor.cutAudio(currentInputPath, currentOutputPath, startTime, duration, currentVolume, this, this);
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
            AnimationUtils.animateProgressSmoothly(progressBar, progress);
            progressText.setText("进度: " + progress + "%");
            
            // 为进度文本添加微动画
            AnimationUtils.animateStatusUpdate(progressText);
        });
    }
    
    @Override
    public void onComplete(boolean success, String message) {
        runOnUiThread(() -> {
            if (success) {
                String outputFileName = new File(currentInputPath).getName();
                updateStatus("处理完成: " + outputFileName);
                ToastUtils.showLong(MainActivity.this, 
                    "处理完成! 输出文件\n保存在: Download/简转/");
            } else {
                updateStatus("处理失败: " + message);
                ToastUtils.show(MainActivity.this, "处理失败: " + message);
            }
            progressBar.setProgress(0);
            progressText.setText("进度: 0%");
        });
    }
    
    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            updateStatus("错误: " + error);
            ToastUtils.showLong(MainActivity.this, "错误: " + error);
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
        
        // 关闭 Executor
        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        
        // 清理更新检查器
        if (updateChecker != null) {
            updateChecker.cleanup();
        }
    }

    private void handleShareIntent(Intent intent) {
        String action = intent.getAction();
        String type   = intent.getType();
        
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if (type.startsWith("video/") || type.startsWith("audio/")) {
                Uri uri = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
                } else {
                    // 对于旧版本，使用已过时的方法但添加抑制警告注解
                    uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                }
                
                if (uri != null) {
                    String path = FileUtils.getPath(this, uri);
                    if (path != null && new File(path).exists()) {
                        currentInputPath = path;
                        currentOutputPath = generateShareOutputPath(path);
                        updateStatus("已接收分享：" + new File(path).getName());
                        setFunctionButtonsEnabled(permissionsGranted);
                    } else {
                        ToastUtils.show(this, "无法解析分享文件");
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

    private void showMigrationDialog() {
        new AlertDialog.Builder(this)
            .setTitle("检测到旧版设置")
            .setMessage("发现旧版本的设置数据，是否迁移到新的JSON配置文件？\n\n" +
                       "新位置: Android/data/com.tech.ezconvert/files/config/\n\n" +
                       "迁移后旧配置文件将自动备份，无需手动操作。")
            .setPositiveButton("迁移", (dialog, which) -> {
                ConfigManager.getInstance(this).migrateOldSettings();
                ToastUtils.show(this, "设置迁移完成");
            })
            .setNegativeButton("跳过", null)
            .setNeutralButton("查看配置文件路径", (dialog, which) -> {
                ConfigManager config = ConfigManager.getInstance(this);
                String path = config.getConfigPath();
                ToastUtils.showLong(this, "配置文件路径: " + path);
            })
            .show();
    }
}
