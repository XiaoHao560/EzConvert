package com.tech.ezconvert.ui;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.tech.ezconvert.R;
import com.tech.ezconvert.processor.AudioProcessor;
import com.tech.ezconvert.processor.VideoProcessor;
import com.tech.ezconvert.utils.AnimationUtils;
import com.tech.ezconvert.utils.ConfigManager;
import com.tech.ezconvert.utils.FFmpegUtil;
import com.tech.ezconvert.utils.FileUtils;
import com.tech.ezconvert.utils.PermissionManager;
import com.tech.ezconvert.utils.UpdateChecker;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HomeFragment extends BaseFragment implements FFmpegUtil.FFmpegCallback {
    
    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private TextView statusText, progressText, versionText, volumePercentText;
    private ProgressBar progressBar;
    private Button selectFileBtn, convertBtn, compressBtn, extractAudioBtn;
    private Button cutVideoBtn, screenshotBtn, convertAudioBtn, cutAudioBtn;
    private Spinner videoFormatSpinner, audioFormatSpinner, qualitySpinner, volumeSpinner;
    private SeekBar volumeSeekBar;
    private LinearLayout customVolumeLayout;
    private ImageButton settingsBtn;
    private String currentInputPath = "";
    private String currentOutputPath = "";
    private boolean permissionsGranted = false;
    private int currentVolume = 100;
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private UpdateChecker updateChecker;
    private ActivityResultLauncher<android.content.Intent> filePickerLauncher;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 初始化更新检查器
        updateChecker = new UpdateChecker(requireContext());
        
        // 初始化配置管理器
        ConfigManager configManager = ConfigManager.getInstance(requireContext());
        
        // 检查是否需要迁移
        checkMigration();
        
        setupActivityResultLaunchers();
        initializeViews(view);
        setupSpinners();
        
        // 初始按钮状态
        setFunctionButtonsEnabled(false);
        updateStatus("正在检查权限...");
        
        // 检查权限状态
        PermissionManager.checkPermissionStatus(this, new PermissionManager.PermissionCallback() {
            @Override
            public void onPermissionsGranted() {
                requireActivity().runOnUiThread(() -> {
                    permissionsGranted = true;
                    setFunctionButtonsEnabled(true);
                    
                    // 成功动画反馈
                    AnimationUtils.animateBounce(selectFileBtn);
                    
                    // 只有在没有选择文件时才显示默认状态
                    if (TextUtils.isEmpty(currentInputPath)) {
                        updateStatus("权限已授予，请选择媒体文件");
                    }
                    
                    // 释放选择文件按键
                    selectFileBtn.setEnabled(true);
                    selectFileBtn.setAlpha(1.0f);
                });
            }
            
            @Override
            public void onPermissionsDenied() {
                requireActivity().runOnUiThread(() -> {
                    permissionsGranted = false;
                    setFunctionButtonsEnabled(false);
                    updateStatus("需要媒体访问权限才能使用应用功能");
                    
                    // 禁用选择文件按键
                    selectFileBtn.setEnabled(false);
                    selectFileBtn.setAlpha(0.5f);
                    
                    Toast.makeText(requireContext(), 
                        "需要媒体访问权限才能选择和处理文件", 
                        Toast.LENGTH_LONG).show();
                });
            }
        });
        
        // 处理可能的分享意图
        handleShareIntent();
        
        // 延迟2秒后自动检查更新
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (updateChecker.shouldAutoCheck()) {
                updateChecker.checkForAutoUpdate();
            }
        }, 2000);
    }
    
    private void setupActivityResultLaunchers() {
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == requireActivity().RESULT_OK) {
                    android.content.Intent data = result.getData();
                    if (data != null) {
                        Uri uri = data.getData();
                        if (uri != null) {
                            currentInputPath = FileUtils.getPath(requireContext(), uri);
                            if (currentInputPath != null && new File(currentInputPath).exists()) {
                                String fileName = new File(currentInputPath).getName();
                                updateStatus("已选择文件: " + fileName);
                                
                                // 生成输出路径
                                generateOutputPath();
                                
                                // 更新按键状态
                                setFunctionButtonsEnabled(permissionsGranted);
                                
                                Toast.makeText(requireContext(), "已选择: " + fileName, Toast.LENGTH_SHORT).show();
                            } else {
                                updateStatus("无法访问文件或文件不存在");
                                Toast.makeText(requireContext(), "无法访问文件或文件不存在", Toast.LENGTH_SHORT).show();
                                currentInputPath = "";
                                setFunctionButtonsEnabled(false);
                            }
                        }
                    }
                }
            }
        );
    }
    
    private void initializeViews(View view) {
        statusText = view.findViewById(R.id.status_text);
        progressText = view.findViewById(R.id.progress_text);
        progressBar = view.findViewById(R.id.progress_bar);
        versionText = view.findViewById(R.id.version_text);
        
        volumeSpinner = view.findViewById(R.id.volume_spinner);
        volumeSeekBar = view.findViewById(R.id.volume_seekbar);
        volumePercentText = view.findViewById(R.id.volume_percent_text);
        customVolumeLayout = view.findViewById(R.id.custom_volume_layout);
        
        selectFileBtn = view.findViewById(R.id.select_file_btn);
        convertBtn = view.findViewById(R.id.convert_btn);
        compressBtn = view.findViewById(R.id.compress_btn);
        extractAudioBtn = view.findViewById(R.id.extract_audio_btn);
        cutVideoBtn = view.findViewById(R.id.cut_video_btn);
        screenshotBtn = view.findViewById(R.id.screenshot_btn);
        convertAudioBtn = view.findViewById(R.id.convert_audio_btn);
        cutAudioBtn = view.findViewById(R.id.cut_audio_btn);
        settingsBtn = view.findViewById(R.id.settings_btn);
        
        videoFormatSpinner = view.findViewById(R.id.video_format_spinner);
        audioFormatSpinner = view.findViewById(R.id.audio_format_spinner);
        qualitySpinner = view.findViewById(R.id.quality_spinner);
        
        // 按钮点击监听
        setupButtonListeners();
        
        // 卡片入场动画
        setupCardAnimations(view);
    }
    
    private void setupCardAnimations(View view) {
        View[] cards = {
            view.findViewById(R.id.status_card),
            view.findViewById(R.id.file_selection_card), 
            view.findViewById(R.id.options_card),
            view.findViewById(R.id.video_processing_card),
            view.findViewById(R.id.audio_processing_card)
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
            requireContext(),
            R.array.video_formats,
            android.R.layout.simple_spinner_item
        );
        videoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        videoFormatSpinner.setAdapter(videoAdapter);
        
        // 音频格式
        ArrayAdapter<CharSequence> audioAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.audio_formats,
            android.R.layout.simple_spinner_item
        );
        audioAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        audioFormatSpinner.setAdapter(audioAdapter);
        
        // 质量
        ArrayAdapter<CharSequence> qualityAdapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.quality_options,
            android.R.layout.simple_spinner_item
        );
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        qualitySpinner.setAdapter(qualityAdapter);
        
        // 音量设置
        ArrayAdapter<CharSequence> volumeAdapter = ArrayAdapter.createFromResource(
            requireContext(),
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
    
    private void setupButtonListeners() {
        // 设置按钮添加旋转动画和导航
        settingsBtn.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            v.animate().rotationBy(180).setDuration(300).start();
            
            scheduler.schedule(() -> {
                requireActivity().runOnUiThread(() -> navigateTo(R.id.settingsFragment));
            }, 150, TimeUnit.MILLISECONDS);
        });
        
        // 文件选择按钮
        selectFileBtn.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            if (permissionsGranted) {
                openFilePicker();
            } else {
                Toast.makeText(requireContext(), "需要媒体访问权限才能选择文件", Toast.LENGTH_SHORT).show();
                PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE, 
                    new PermissionManager.PermissionCallback() {
                        @Override
                        public void onPermissionsGranted() {
                            requireActivity().runOnUiThread(() -> {
                                permissionsGranted = true;
                                openFilePicker();
                            });
                        }
                        
                        @Override
                        public void onPermissionsDenied() {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), "需要权限才能选择文件", Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
            }
        });
        
        // 功能按钮统一添加动画
        View.OnClickListener functionButtonListener = v -> {
            AnimationUtils.animateButtonClick(v);
            if (permissionsGranted && !TextUtils.isEmpty(currentInputPath)) {
                handleFunctionButtonClick(v.getId());
            } else if (!permissionsGranted) {
                Toast.makeText(requireContext(), "请先授予权限并选择文件", Toast.LENGTH_SHORT).show();
                PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE, 
                    new PermissionManager.PermissionCallback() {
                        @Override
                        public void onPermissionsGranted() {
                            requireActivity().runOnUiThread(() -> {
                                permissionsGranted = true;
                                if (!TextUtils.isEmpty(currentInputPath)) {
                                    handleFunctionButtonClick(v.getId());
                                } else {
                                    Toast.makeText(requireContext(), "请先选择文件", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                        
                        @Override
                        public void onPermissionsDenied() {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), "需要权限才能处理文件", Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
            } else {
                Toast.makeText(requireContext(), "请先选择文件", Toast.LENGTH_SHORT).show();
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
        }
    }
    
    public void updateStatus(String message) {
        requireActivity().runOnUiThread(() -> {
            // 状态文本更新动画
            AnimationUtils.animateStatusUpdate(statusText);
            statusText.setText(message);
        });
    }
    
    private void setFunctionButtonsEnabled(boolean enabled) {
        boolean hasFileSelected = !TextUtils.isEmpty(currentInputPath);
        
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
            Toast.makeText(requireContext(), "请先授予存储权限", Toast.LENGTH_SHORT).show();
            PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE, 
                new PermissionManager.PermissionCallback() {
                    @Override
                    public void onPermissionsGranted() {
                        requireActivity().runOnUiThread(() -> {
                            permissionsGranted = true;
                            openFilePicker();
                        });
                    }
                    
                    @Override
                    public void onPermissionsDenied() {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "需要权限才能选择文件", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            return;
        }
        
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        
        String[] mimeTypes = {"video/*", "audio/*"};
        intent.putExtra(android.content.Intent.EXTRA_MIME_TYPES, mimeTypes);
        
        try {
            filePickerLauncher.launch(android.content.Intent.createChooser(intent, "选择媒体文件"));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void generateOutputPath() {
        if (TextUtils.isEmpty(currentInputPath)) return;
        
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
    }
    
    private void startConversion() {
        if (!permissionsGranted) {
            Toast.makeText(requireContext(), "请先授予存储权限", Toast.LENGTH_SHORT).show();
            PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE, 
                new PermissionManager.PermissionCallback() {
                    @Override
                    public void onPermissionsGranted() {
                        requireActivity().runOnUiThread(() -> {
                            permissionsGranted = true;
                            if (!TextUtils.isEmpty(currentInputPath)) {
                                startConversion();
                            }
                        });
                    }
                    
                    @Override
                    public void onPermissionsDenied() {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "需要权限才能处理文件", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            return;
        }
        
        if (TextUtils.isEmpty(currentInputPath)) {
            Toast.makeText(requireContext(), "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String format = videoFormatSpinner.getSelectedItem().toString();
        generateOutputPath(); // 生成基础路径
        
        updateStatus("开始转换视频到 " + format + " 格式...");
        VideoProcessor.convertVideo(currentInputPath, currentOutputPath, format, currentVolume, this, requireActivity());
    }
    
    private void startCompression() {
        if (!permissionsGranted) {
            Toast.makeText(requireContext(), "请先授予存储权限", Toast.LENGTH_SHORT).show();
            PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE, 
                new PermissionManager.PermissionCallback() {
                    @Override
                    public void onPermissionsGranted() {
                        requireActivity().runOnUiThread(() -> {
                            permissionsGranted = true;
                            if (!TextUtils.isEmpty(currentInputPath)) {
                                startCompression();
                            }
                        });
                    }
                    
                    @Override
                    public void onPermissionsDenied() {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "需要权限才能处理文件", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            return;
        }
        
        if (TextUtils.isEmpty(currentInputPath)) {
            Toast.makeText(requireContext(), "请先选择文件", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String qualityStr = qualitySpinner.getSelectedItem().toString();
        int quality = getQualityValue(qualityStr);
        
        generateOutputPath(); // 生成基础路径
        
        updateStatus("开始压缩视频 (" + qualityStr + ")...");
        VideoProcessor.compressVideo(currentInputPath, currentOutputPath, quality, currentVolume, this, requireActivity());
    }
    
    private void extractAudio() {
        if (!permissionsGranted) {
            Toast.makeText(requireContext(), "请先授予存储权限", Toast.LENGTH_SHORT).show();
            PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE, 
                new PermissionManager.PermissionCallback() {
                    @Override
                    public void onPermissionsGranted() {
                        requireActivity().runOnUiThread(() -> {
                            permissionsGranted = true;
                            if (!TextUtils.isEmpty(currentInputPath)) {
                                extractAudio();
                            }
                        });
                    }
                    
                    @Override
                    public void onPermissionsDenied() {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "需要权限才能处理文件", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            return;
        }
        
        if (TextUtils.isEmpty(currentInputPath)) {
            Toast.makeText(requireContext(), "请先选择文件", Toast.LENGTH_SHORT).show();
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
        AudioProcessor.extractAudioFromVideo(currentInputPath, currentOutputPath, "mp3", currentVolume, this, requireActivity());
    }
    
    private void convertAudio() {
        if (!permissionsGranted) {
            Toast.makeText(requireContext(), "请先授予存储权限", Toast.LENGTH_SHORT).show();
            PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE, 
                new PermissionManager.PermissionCallback() {
                    @Override
                    public void onPermissionsGranted() {
                        requireActivity().runOnUiThread(() -> {
                            permissionsGranted = true;
                            if (!TextUtils.isEmpty(currentInputPath)) {
                                convertAudio();
                            }
                        });
                    }
                    
                    @Override
                    public void onPermissionsDenied() {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "需要权限才能处理文件", Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            return;
        }
        
        if (TextUtils.isEmpty(currentInputPath)) {
            Toast.makeText(requireContext(), "请先选择文件", Toast.LENGTH_SHORT).show();
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
        AudioProcessor.convertAudio(currentInputPath, currentOutputPath, format, currentVolume, this, requireActivity());
    }
    
    private void showCutVideoDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("裁剪视频");
        
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        final EditText startTimeInput = new EditText(requireContext());
        startTimeInput.setHint("开始时间 (格式: 00:00:00)");
        layout.addView(startTimeInput);
        
        final EditText durationInput = new EditText(requireContext());
        durationInput.setHint("持续时间 (格式: 00:00:10)");
        layout.addView(durationInput);
        
        builder.setView(layout);
        
        builder.setPositiveButton("开始裁剪", (dialog, which) -> {
            String startTime = startTimeInput.getText().toString();
            String duration = durationInput.getText().toString();
            
            if (TextUtils.isEmpty(startTime) || TextUtils.isEmpty(duration)) {
                Toast.makeText(requireContext(), "请输入开始时间和持续时间", Toast.LENGTH_SHORT).show();
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
            VideoProcessor.cutVideo(currentInputPath, currentOutputPath, startTime, duration, currentVolume, this, requireActivity());
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    private void showScreenshotDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("视频截图");
        
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        final EditText timestampInput = new EditText(requireContext());
        timestampInput.setHint("时间点 (格式: 00:00:05)");
        layout.addView(timestampInput);
        
        builder.setView(layout);
        
        builder.setPositiveButton("截图", (dialog, which) -> {
            String timestamp = timestampInput.getText().toString();
            
            if (TextUtils.isEmpty(timestamp)) {
                Toast.makeText(requireContext(), "请输入时间点", Toast.LENGTH_SHORT).show();
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
            VideoProcessor.extractFrame(currentInputPath, currentOutputPath, timestamp, this, requireActivity());
        });
        
        builder.setNegativeButton("取消", null);
        builder.show();
    }
    
    private void showCutAudioDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("裁剪音频");
        
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        
        final EditText startTimeInput = new EditText(requireContext());
        startTimeInput.setHint("开始时间 (格式: 00:00:00)");
        layout.addView(startTimeInput);
        
        final EditText durationInput = new EditText(requireContext());
        durationInput.setHint("持续时间 (格式: 00:00:10)");
        layout.addView(durationInput);
        
        builder.setView(layout);
        
        builder.setPositiveButton("开始裁剪", (dialog, which) -> {
            String startTime = startTimeInput.getText().toString();
            String duration = durationInput.getText().toString();
            
            if (TextUtils.isEmpty(startTime) || TextUtils.isEmpty(duration)) {
                Toast.makeText(requireContext(), "请输入开始时间和持续时间", Toast.LENGTH_SHORT).show();
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
            AudioProcessor.cutAudio(currentInputPath, currentOutputPath, startTime, duration, currentVolume, this, requireActivity());
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
        requireActivity().runOnUiThread(() -> {
            AnimationUtils.animateProgressSmoothly(progressBar, progress);
            progressText.setText("进度: " + progress + "%");
            
            // 为进度文本添加微动画
            AnimationUtils.animateStatusUpdate(progressText);
        });
    }
    
    @Override
    public void onComplete(boolean success, String message) {
        requireActivity().runOnUiThread(() -> {
            if (success) {
                String outputFileName = new File(currentInputPath).getName();
                updateStatus("处理完成: " + outputFileName);
                Toast.makeText(requireContext(), 
                    "处理完成！输出文件\n保存在: Download/简转/", 
                    Toast.LENGTH_LONG).show();
            } else {
                updateStatus("处理失败: " + message);
                Toast.makeText(requireContext(), "处理失败: " + message, Toast.LENGTH_LONG).show();
            }
            progressBar.setProgress(0);
            progressText.setText("进度: 0%");
        });
    }
    
    @Override
    public void onError(String error) {
        requireActivity().runOnUiThread(() -> {
            updateStatus("错误: " + error);
            Toast.makeText(requireContext(), "错误: " + error, Toast.LENGTH_LONG).show();
        });
    }
    
    private void checkMigration() {
        SharedPreferences prefs = requireContext().getSharedPreferences("EzConvertSettings", android.content.Context.MODE_PRIVATE);
        if (prefs.getAll().size() > 0) {
            // 有旧设置，询问用户是否迁移
            showMigrationDialog();
        }
    }
    
    private void showMigrationDialog() {
        new AlertDialog.Builder(requireContext())
            .setTitle("检测到旧版设置")
            .setMessage("发现旧版本的设置数据，是否迁移到新的JSON配置文件？\n\n迁移后设置将保存在 downloads/简转/config/ 目录下。")
            .setPositiveButton("迁移", (dialog, which) -> {
                ConfigManager.getInstance(requireContext()).migrateOldSettings();
                Toast.makeText(requireContext(), "设置迁移完成", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("跳过", null)
            .setNeutralButton("查看配置文件", (dialog, which) -> {
                // 打开文件管理器显示配置文件
                ConfigManager config = ConfigManager.getInstance(requireContext());
                File configDir = config.getConfigDirectory();
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(configDir), "*/*");
                startActivity(android.content.Intent.createChooser(intent, "打开配置文件夹"));
            })
            .show();
    }
    
    private void handleShareIntent() {
        Bundle args = getArguments();
        if (args != null && args.containsKey("sharedFileUri")) {
            Uri uri = args.getParcelable("sharedFileUri");
            if (uri != null) {
                String path = FileUtils.getPath(requireContext(), uri);
                if (path != null && new File(path).exists()) {
                    currentInputPath = path;
                    generateOutputPath();
                    updateStatus("已接收分享：" + new File(path).getName());
                    setFunctionButtonsEnabled(permissionsGranted);
                }
            }
        }
    }
    
    @Override
    public void onDestroy() {
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
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                         @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        PermissionManager.handlePermissionResult(this, requestCode, permissions, grantResults, 
            new PermissionManager.PermissionCallback() {
                @Override
                public void onPermissionsGranted() {
                    requireActivity().runOnUiThread(() -> {
                        permissionsGranted = true;
                        setFunctionButtonsEnabled(true);
                        AnimationUtils.animateBounce(selectFileBtn);
                        updateStatus("权限已授予，请选择媒体文件");
                        selectFileBtn.setEnabled(true);
                        selectFileBtn.setAlpha(1.0f);
                    });
                }
                
                @Override
                public void onPermissionsDenied() {
                    requireActivity().runOnUiThread(() -> {
                        permissionsGranted = false;
                        setFunctionButtonsEnabled(false);
                        updateStatus("需要媒体访问权限才能使用应用功能");
                        selectFileBtn.setEnabled(false);
                        selectFileBtn.setAlpha(0.5f);
                        Toast.makeText(requireContext(), 
                            "需要媒体访问权限才能选择和处理文件", 
                            Toast.LENGTH_LONG).show();
                    });
                }
            });
    }
}