package com.tech.ezconvert.ui;

import android.app.Dialog;
import android.view.Window;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.ConfigManager;
import com.tech.ezconvert.utils.ParameterData;
import com.tech.ezconvert.utils.ParameterPresetManager;
import com.tech.ezconvert.utils.ToastUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParameterDialogFragment extends DialogFragment {
    private static final String ARG_TASK_TYPE = "task_type";
    private static final String ARG_FILE_PATH = "file_path";
    private static final String ARG_FILE_INDEX = "file_index";
    private static final String ARG_TOTAL_FILES = "total_files";
    private static final String ARG_IS_COMPRESS = "is_compress";

    private String taskType;
    private String currentFilePath;
    private int fileIndex;
    private int totalFiles;
    private boolean isCompressTask = false;

    private ParameterPresetManager presetManager;
    private ParameterData currentParams;
    private String selectedPresetName = "默认";
    private boolean isSyncAll = true;
    private int currentVideoBitrateKbps = 0; // 当前文件视频码率（用于压缩任务显示）

    private RecyclerView presetRecycler;
    private PresetAdapter presetAdapter;
    private MaterialButton btnSavePreset, btnDeletePreset, btnReset, btnStart;
    private MaterialSwitch switchSync;
    private TextView tvFileInfo, tvVolumePercent, tvCurrentBitrate;
    private Slider sliderVolume;
    private MaterialAutoCompleteTextView spinnerOutputFormat, spinnerVideoCodec, spinnerAudioCodec, spinnerBitrateUnit;
    private TextInputEditText etBitrateValue, etAudioBitrateValue, etCutStart, etCutDuration,
            etScreenshotTime, etScreenshotResolution, etScreenshotQuality;
    private ChipGroup chipVideoBitrateGroup, chipAudioBitrateGroup;
    private Chip chipVideoOriginal, chipVideoCustom, chipAudioOriginal, chipAudioCustom;
    private LinearLayout customBitrateLayout, customAudioBitrateLayout, screenshotSection, cutSection;
    private LinearLayout videoParamsContainer, volumeContainer, screenshotContainer, cutContainer;
    private MaterialAutoCompleteTextView spinnerScreenshotFormat;

    private OnParameterConfirmListener listener;

    public interface OnParameterConfirmListener {
        void onConfirm(ParameterData params, boolean syncAll);
    }

    public static ParameterDialogFragment newInstance(String taskType, String filePath, int index, int total) {
        ParameterDialogFragment f = new ParameterDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TASK_TYPE, taskType);
        args.putString(ARG_FILE_PATH, filePath);
        args.putInt(ARG_FILE_INDEX, index);
        args.putInt(ARG_TOTAL_FILES, total);
        f.setArguments(args);
        return f;
    }

    public void setListener(OnParameterConfirmListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.FullScreenDialogTheme);
        if (getArguments() != null) {
            taskType = getArguments().getString(ARG_TASK_TYPE);
            currentFilePath = getArguments().getString(ARG_FILE_PATH);
            fileIndex = getArguments().getInt(ARG_FILE_INDEX);
            totalFiles = getArguments().getInt(ARG_TOTAL_FILES);
            isCompressTask = "compress".equals(taskType);
        }
        presetManager = new ParameterPresetManager(requireContext());
        // 加载默认预设
        currentParams = presetManager.loadPreset("默认");
        if (currentParams == null) {
            currentParams = ParameterData.createDefault();
            presetManager.savePreset("默认", currentParams);
        }
        selectedPresetName = "默认";

        // 如果是压缩任务，获取当前视频码率
        if (isCompressTask && currentFilePath != null) {
            currentVideoBitrateKbps = getVideoBitrate(currentFilePath);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                            @Nullable Bundle savedInstanceState) {
        // 使用 Activity 的 LayoutInflater，确保继承动态颜色主题
        LayoutInflater activityInflater = LayoutInflater.from(getActivity());
        View view = activityInflater.inflate(R.layout.dialog_parameter, container, false);
        
        initViews(view);
        setupToolbar(view);
        setupPresetList();
        setupSpinners();
        setupChips();
        setupSliders();
        setupButtons();
        loadPreset(selectedPresetName);
        return view;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 延迟执行，确保布局完成，获取正确的 insets
        view.post(() -> applyEdgeToEdge(view));
        updateUIForTaskType();
    }

    // 应用 Edge-to-Edge，避免内容被状态栏/导航栏遮挡
    private void applyEdgeToEdge(View rootView) {
        Dialog dialog = getDialog();
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;

        // 取消窗口自动适配系统栏
        WindowCompat.setDecorFitsSystemWindows(window, false);

        View toolbar = rootView.findViewById(R.id.toolbar);
        View bottomActions = rootView.findViewById(R.id.bottom_actions);

        // 使用标记避免重复设置相同的 padding
        final int[] lastStatusBarHeight = { -1 };
        final int[] lastNavBarHeight = { -1 };

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;

            // 只在高度变化时更新，避免重复设置
            if (toolbar != null && statusBarHeight != lastStatusBarHeight[0]) {
                toolbar.setPadding(
                    toolbar.getPaddingLeft(),
                    statusBarHeight,
                    toolbar.getPaddingRight(),
                    toolbar.getPaddingBottom()
                );
                if (toolbar.getLayoutParams() != null) {
                    toolbar.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                }
                lastStatusBarHeight[0] = statusBarHeight;
            }

            if (bottomActions != null && navBarHeight != lastNavBarHeight[0]) {
                bottomActions.setPadding(
                    bottomActions.getPaddingLeft(),
                    bottomActions.getPaddingTop(),
                    bottomActions.getPaddingRight(),
                    navBarHeight
                );
                lastNavBarHeight[0] = navBarHeight;
            }

            return WindowInsetsCompat.CONSUMED;
        });

        // 请求应用 insets
        ViewCompat.requestApplyInsets(rootView);
    }

    private void initViews(View view) {
        presetRecycler = view.findViewById(R.id.preset_recycler);
        btnSavePreset = view.findViewById(R.id.btn_save_preset);
        btnDeletePreset = view.findViewById(R.id.btn_delete_preset);
        btnReset = view.findViewById(R.id.btn_reset);
        btnStart = view.findViewById(R.id.btn_start);
        switchSync = view.findViewById(R.id.switch_sync_all);
        tvFileInfo = view.findViewById(R.id.current_file_info);
        tvVolumePercent = view.findViewById(R.id.tv_volume_percent);
        tvCurrentBitrate = view.findViewById(R.id.tv_current_bitrate);
        sliderVolume = view.findViewById(R.id.slider_volume);

        spinnerOutputFormat = view.findViewById(R.id.spinner_output_format);
        spinnerVideoCodec = view.findViewById(R.id.spinner_video_codec);
        spinnerAudioCodec = view.findViewById(R.id.spinner_audio_codec);
        spinnerBitrateUnit = view.findViewById(R.id.spinner_bitrate_unit);

        etBitrateValue = view.findViewById(R.id.et_bitrate_value);
        etAudioBitrateValue = view.findViewById(R.id.et_audio_bitrate_value);
        etCutStart = view.findViewById(R.id.et_cut_start);
        etCutDuration = view.findViewById(R.id.et_cut_duration);
        etScreenshotTime = view.findViewById(R.id.et_screenshot_time);
        etScreenshotResolution = view.findViewById(R.id.et_screenshot_resolution);
        etScreenshotQuality = view.findViewById(R.id.et_screenshot_quality);

        chipVideoBitrateGroup = view.findViewById(R.id.chip_bitrate_group);
        chipAudioBitrateGroup = view.findViewById(R.id.chip_audio_bitrate_group);
        chipVideoOriginal = view.findViewById(R.id.chip_bitrate_original);
        chipVideoCustom = view.findViewById(R.id.chip_bitrate_custom);
        chipAudioOriginal = view.findViewById(R.id.chip_audio_original);
        chipAudioCustom = view.findViewById(R.id.chip_audio_custom);

        customBitrateLayout = view.findViewById(R.id.custom_bitrate_layout);
        customAudioBitrateLayout = view.findViewById(R.id.custom_audio_bitrate_layout);
        screenshotSection = view.findViewById(R.id.screenshot_container);
        cutSection = view.findViewById(R.id.cut_container);
        videoParamsContainer = view.findViewById(R.id.video_params_container);
        volumeContainer = view.findViewById(R.id.volume_container);
        screenshotContainer = view.findViewById(R.id.screenshot_container);
        cutContainer = view.findViewById(R.id.cut_container);
        spinnerScreenshotFormat = view.findViewById(R.id.spinner_screenshot_format);

        // 默认选中原质量
        chipVideoBitrateGroup.check(R.id.chip_bitrate_original);
        chipAudioBitrateGroup.check(R.id.chip_audio_original);
    }

    private void setupToolbar(View view) {
        MaterialToolbar toolbar = view.findViewById(R.id.toolbar);
        String title = getTaskTypeDisplayName(taskType);
        if (totalFiles > 1) {
            title += " (" + fileIndex + "/" + totalFiles + ")";
        }
        toolbar.setTitle(title);
        toolbar.setNavigationOnClickListener(v -> dismiss());
    }

    private String getTaskTypeDisplayName(String type) {
        switch (type) {
            case "convert": return "转换视频";
            case "compress": return "压缩视频";
            case "extract_audio": return "提取音频";
            case "cut_video": return "裁剪视频";
            case "screenshot": return "视频截图";
            case "convert_audio": return "转换音频";
            case "cut_audio": return "裁剪音频";
            default: return "处理文件";
        }
    }

    private void setupPresetList() {
        presetAdapter = new PresetAdapter();
        presetRecycler.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        presetRecycler.setAdapter(presetAdapter);
        refreshPresetList();

        presetAdapter.setListener((presetName, position) -> {
            selectedPresetName = presetName;
            loadPreset(presetName);
        });
    }

    private void refreshPresetList() {
        List<String> names = presetManager.listPresetNames();
        if (names.isEmpty()) {
            // 创建默认预设
            presetManager.savePreset("默认", ParameterData.createDefault());
            names = presetManager.listPresetNames();
        }
        int defaultPos = names.indexOf(selectedPresetName);
        if (defaultPos < 0) defaultPos = 0;
        presetAdapter.setData(names, defaultPos);
        selectedPresetName = names.get(defaultPos);
    }

    private void loadPreset(String presetName) {
        ParameterData data = presetManager.loadPreset(presetName);
        if (data == null) return;
        currentParams = data;

        // 输出格式
        setSpinnerValue(spinnerOutputFormat, data.outputFormat);
        // 编码器
        setSpinnerValue(spinnerVideoCodec, data.videoCodec);
        setSpinnerValue(spinnerAudioCodec, data.audioCodec);

        // 视频码率
        if ("original".equals(data.videoBitrateMode)) {
            chipVideoBitrateGroup.check(R.id.chip_bitrate_original);
            customBitrateLayout.setVisibility(View.GONE);
        } else {
            chipVideoBitrateGroup.check(R.id.chip_bitrate_custom);
            customBitrateLayout.setVisibility(View.VISIBLE);
            etBitrateValue.setText(String.valueOf(data.videoBitrateValue));
            setSpinnerValue(spinnerBitrateUnit, data.videoBitrateUnit);
        }

        // 音频码率
        if ("original".equals(data.audioBitrateMode)) {
            chipAudioBitrateGroup.check(R.id.chip_audio_original);
            customAudioBitrateLayout.setVisibility(View.GONE);
        } else {
            chipAudioBitrateGroup.check(R.id.chip_audio_custom);
            customAudioBitrateLayout.setVisibility(View.VISIBLE);
            etAudioBitrateValue.setText(String.valueOf(data.audioBitrateValue));
        }

        // 裁剪
        etCutStart.setText(data.cutStartTime);
        etCutDuration.setText(data.cutDuration);

        // 截图
        setSpinnerValue(spinnerScreenshotFormat, data.screenshotFormat);
        etScreenshotTime.setText(data.cutStartTime); // 复用开始时间作为截图时间
        etScreenshotResolution.setText(data.screenshotResolution);
        etScreenshotQuality.setText(String.valueOf(data.screenshotQuality));

        // 音量
        sliderVolume.setValue(data.volume);
        tvVolumePercent.setText(data.volume + "%");

        // 同步开关
        switchSync.setChecked(isSyncAll);

        updateCodecOptions(data.outputFormat);
    }

    private void setSpinnerValue(MaterialAutoCompleteTextView spinner, String value) {
        if (value == null) return;
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner.getAdapter();
        if (adapter == null) return;
        int pos = adapter.getPosition(value);
        if (pos >= 0) {
            spinner.setText(adapter.getItem(pos), false);
        }
    }

    private void setupSpinners() {
        // 输出格式
        String[] formats = getOutputFormatsForTask();
        setupSpinner(spinnerOutputFormat, formats, formats[0]);

        // 视频编码器（动态更新）
        setupSpinner(spinnerVideoCodec, new String[]{"H.264 (HW)"}, "H.264 (HW)");

        // 音频编码器
        setupSpinner(spinnerAudioCodec, new String[]{"AAC"}, "AAC");

        // 码率单位
        setupSpinner(spinnerBitrateUnit, new String[]{"Kbps", "Mbps"}, "Mbps");

        // 截图格式
        setupSpinner(spinnerScreenshotFormat, new String[]{"jpeg", "png"}, "jpeg");

        // 输出格式切换时更新编码器列表
        spinnerOutputFormat.setOnItemClickListener((parent, view, position, id) -> {
            String format = parent.getAdapter().getItem(position).toString();
            updateCodecOptions(format);
        });
    }

    private String[] getOutputFormatsForTask() {
        if ("extract_audio".equals(taskType) || "convert_audio".equals(taskType) || "cut_audio".equals(taskType)) {
            return new String[]{"mp3", "wav", "aac", "flac", "ogg", "m4a"};
        }
        if ("screenshot".equals(taskType)) {
            return new String[]{"jpeg", "png"};
        }
        return new String[]{"mp4", "mkv", "webm", "avi", "mov", "flv", "gif"};
    }

    private void setupSpinner(MaterialAutoCompleteTextView spinner, String[] items, String defaultVal) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), R.layout.item_dropdown, items);
        spinner.setAdapter(adapter);
        spinner.setText(defaultVal, false);
    }

    private void updateCodecOptions(String format) {
        // 根据输出格式更新编码器列表
        boolean hardwareAccel = ConfigManager.getInstance(requireContext()).isHardwareAccelerationEnabled();

        // 视频编码器
        String[] videoCodecs = getVideoCodecsForFormat(format, hardwareAccel);
        setupSpinner(spinnerVideoCodec, videoCodecs, videoCodecs[0]);

        // 音频编码器
        String[] audioCodecs = getAudioCodecsForFormat(format);
        setupSpinner(spinnerAudioCodec, audioCodecs, audioCodecs[0]);
    }

    private String[] getVideoCodecsForFormat(String format, boolean hwAccel) {
        switch (format) {
            case "mp4":
            case "mov":
                if (hwAccel) {
                    return new String[]{"h264_mediacodec (HW) (最佳)", "hevc_mediacodec (HW)", "libx264 (SW)", "libx265 (SW)"};
                } else {
                    return new String[]{"libx264 (SW) (最佳)", "libx265 (SW)", "h264_mediacodec (HW)", "hevc_mediacodec (HW)"};
                }
            case "mkv":
                return new String[]{"libx265 (SW) (最佳)", "libx264 (SW)"};
            case "webm":
                return new String[]{"libvpx-vp9 (最佳)", "libvpx"};
            case "avi":
                return new String[]{"mpeg4 (最佳)", "libx264 (SW)"};
            case "flv":
                if (hwAccel) {
                    return new String[]{"h264_mediacodec (HW) (最佳)", "libx264 (SW)"};
                } else {
                    return new String[]{"libx264 (SW) (最佳)", "h264_mediacodec (HW)"};
                }
            case "gif":
                return new String[]{"gif (最佳)"};
            default:
                return new String[]{"libx264 (SW) (最佳)"};
        }
    }

    private String[] getAudioCodecsForFormat(String format) {
        switch (format) {
            case "mp4":
            case "mov":
            case "flv":
                return new String[]{"aac (最佳)", "libmp3lame"};
            case "mkv":
                return new String[]{"libopus (最佳)", "aac", "libmp3lame"};
            case "webm":
                return new String[]{"libopus (最佳)"};
            case "avi":
                return new String[]{"libmp3lame (最佳)"};
            case "mp3":
                return new String[]{"libmp3lame (最佳)"};
            case "wav":
                return new String[]{"pcm_s16le (最佳)"};
            case "aac":
                return new String[]{"aac (最佳)"};
            case "flac":
                return new String[]{"flac (最佳)"};
            case "ogg":
                return new String[]{"libvorbis (最佳)"};
            case "m4a":
                return new String[]{"aac (最佳)"};
            default:
                return new String[]{"aac (最佳)"};
        }
    }

    private void setupChips() {
        // 视频码率 Chip 切换
        chipVideoBitrateGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chip_bitrate_original) {
                customBitrateLayout.setVisibility(View.GONE);
                currentParams.videoBitrateMode = "original";
            } else if (checkedId == R.id.chip_bitrate_custom) {
                customBitrateLayout.setVisibility(View.VISIBLE);
                currentParams.videoBitrateMode = "custom";
            }
        });

        // 音频码率 Chip 切换
        chipAudioBitrateGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chip_audio_original) {
                customAudioBitrateLayout.setVisibility(View.GONE);
                currentParams.audioBitrateMode = "original";
            } else if (checkedId == R.id.chip_audio_custom) {
                customAudioBitrateLayout.setVisibility(View.VISIBLE);
                currentParams.audioBitrateMode = "custom";
            }
        });
    }

    private void setupSliders() {
        sliderVolume.addOnChangeListener((slider, value, fromUser) -> {
            int vol = (int) value;
            tvVolumePercent.setText(vol + "%");
            currentParams.volume = vol;
        });
    }

    private void setupButtons() {
        btnSavePreset.setOnClickListener(v -> {
            String newName = presetManager.getNextPresetName();
            // 收集当前参数
            collectParamsFromUI();
            if (presetManager.savePreset(newName, currentParams)) {
                refreshPresetList();
                // 自动选中新预设
                int pos = presetManager.listPresetNames().indexOf(newName);
                if (pos >= 0) {
                    presetAdapter.selectPosition(pos);
                    selectedPresetName = newName;
                }
                ToastUtils.show(requireActivity(), "预设已保存: " + newName);
            } else {
                ToastUtils.show(requireActivity(), "保存预设失败");
            }
        });

        btnDeletePreset.setOnClickListener(v -> {
            if ("默认".equals(selectedPresetName)) {
                ToastUtils.show(requireActivity(), "不能删除默认预设");
                return;
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("删除预设")
                    .setMessage("确定要删除预设 \"" + selectedPresetName + "\" 吗？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        if (presetManager.deletePreset(selectedPresetName)) {
                            refreshPresetList();
                            // 切换到默认
                            int pos = presetManager.listPresetNames().indexOf("默认");
                            if (pos >= 0) {
                                presetAdapter.selectPosition(pos);
                                selectedPresetName = "默认";
                                loadPreset("默认");
                            }
                            ToastUtils.show(requireActivity(), "预设已删除");
                        } else {
                            ToastUtils.show(requireActivity(), "删除失败");
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        btnReset.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("重置为默认")
                    .setMessage("将重置所有参数为出厂默认值，确认继续？")
                    .setPositiveButton("重置", (dialog, which) -> {
                        currentParams = ParameterData.createDefault();
                        loadPreset("默认");
                        ToastUtils.show(requireActivity(), "已重置为默认参数");
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        switchSync.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isSyncAll = isChecked;
        });

        btnStart.setOnClickListener(v -> {
            collectParamsFromUI();
            if (validateParams()) {
                dismiss();
                if (listener != null) {
                    listener.onConfirm(currentParams, isSyncAll);
                }
            }
        });
    }

    private void collectParamsFromUI() {
        // 输出格式
        currentParams.outputFormat = spinnerOutputFormat.getText().toString();

        // 编码器（去除括号标注）
        String videoCodecFull = spinnerVideoCodec.getText().toString();
        currentParams.videoCodec = extractCodecName(videoCodecFull);

        String audioCodecFull = spinnerAudioCodec.getText().toString();
        currentParams.audioCodec = extractCodecName(audioCodecFull);

        // 视频码率
        if (chipVideoBitrateGroup.getCheckedChipId() == R.id.chip_bitrate_custom) {
            try {
                currentParams.videoBitrateValue = Integer.parseInt(etBitrateValue.getText().toString());
            } catch (NumberFormatException e) {
                currentParams.videoBitrateValue = 2000;
            }
            currentParams.videoBitrateUnit = spinnerBitrateUnit.getText().toString();
            currentParams.videoBitrateMode = "custom";
        } else {
            currentParams.videoBitrateMode = "original";
            currentParams.videoBitrateValue = 0;
        }

        // 音频码率
        if (chipAudioBitrateGroup.getCheckedChipId() == R.id.chip_audio_custom) {
            try {
                currentParams.audioBitrateValue = Integer.parseInt(etAudioBitrateValue.getText().toString());
            } catch (NumberFormatException e) {
                currentParams.audioBitrateValue = 192;
            }
            currentParams.audioBitrateMode = "custom";
        } else {
            currentParams.audioBitrateMode = "original";
            currentParams.audioBitrateValue = 0;
        }

        // 裁剪
        currentParams.cutStartTime = etCutStart.getText().toString();
        currentParams.cutDuration = etCutDuration.getText().toString();

        // 截图
        currentParams.screenshotFormat = spinnerScreenshotFormat.getText().toString();
        currentParams.screenshotResolution = etScreenshotResolution.getText().toString();
        try {
            currentParams.screenshotQuality = Integer.parseInt(etScreenshotQuality.getText().toString());
        } catch (NumberFormatException e) {
            currentParams.screenshotQuality = 90;
        }
    }

    private String extractCodecName(String fullName) {
        // "h264_mediacodec (HW) (最佳)" -> "h264_mediacodec"
        if (fullName.contains(" ")) {
            return fullName.split(" ")[0];
        }
        return fullName;
    }

    private boolean validateParams() {
        // 验证必要参数
        if (spinnerOutputFormat.getText().toString().isEmpty()) {
            ToastUtils.show(requireActivity(), "请选择输出格式");
            return false;
        }
        if ("screenshot".equals(taskType)) {
            if (etScreenshotTime.getText().toString().isEmpty()) {
                ToastUtils.show(requireActivity(), "请输入截图时间点");
                return false;
            }
        }
        if ("cut_video".equals(taskType) || "cut_audio".equals(taskType)) {
            if (etCutStart.getText().toString().isEmpty() || etCutDuration.getText().toString().isEmpty()) {
                ToastUtils.show(requireActivity(), "请输入裁剪开始时间和持续时间");
                return false;
            }
        }
        return true;
    }

    private void updateUIForTaskType() {
        // 显示当前文件信息
        if (currentFilePath != null) {
            String fileName = new File(currentFilePath).getName();
            tvFileInfo.setText("当前文件: " + fileName);
        } else {
            tvFileInfo.setText("当前文件: 无");
        }
        
        // 如果是压缩任务，显示当前码率
        if (isCompressTask && currentVideoBitrateKbps > 0) {
            tvCurrentBitrate.setVisibility(View.VISIBLE);
            if (currentVideoBitrateKbps >= 1000) {
                tvCurrentBitrate.setText("当前视频码率: " + (currentVideoBitrateKbps / 1000.0) + " Mbps");
            } else {
                tvCurrentBitrate.setText("当前视频码率: " + currentVideoBitrateKbps + " Kbps");
            }
        } else {
            tvCurrentBitrate.setVisibility(View.GONE);
        }
        
        // 先全部隐藏，再按需显示
        videoParamsContainer.setVisibility(View.GONE);
        volumeContainer.setVisibility(View.GONE);
        screenshotContainer.setVisibility(View.GONE);
        cutContainer.setVisibility(View.GONE);
        
        switch (taskType) {
            case "screenshot":
                // 截图任务：只显示截图参数和输出格式
                videoParamsContainer.setVisibility(View.GONE);
                volumeContainer.setVisibility(View.GONE);
                screenshotContainer.setVisibility(View.VISIBLE);
                cutContainer.setVisibility(View.GONE);
                // 设置输出格式为图片格式
                setupSpinner(spinnerOutputFormat, new String[]{"jpeg", "png"}, "jpeg");
                break;
            
            case "cut_video":
            case "cut_audio":
                // 裁剪任务：显示视频参数（或音频参数）和裁剪参数，隐藏截图参数
                videoParamsContainer.setVisibility(View.VISIBLE);
                volumeContainer.setVisibility(View.VISIBLE);
                screenshotContainer.setVisibility(View.GONE);
                cutContainer.setVisibility(View.VISIBLE);
                break;
            
            case "extract_audio":
            case "convert_audio":
                // 纯音频任务：显示音频相关参数（视频编码器隐藏），隐藏截图和裁剪
                videoParamsContainer.setVisibility(View.VISIBLE);
                // 隐藏视频编码器和视频码率（可在布局中单独控制）
                ((View) requireView().findViewById(R.id.spinner_video_codec).getParent()).setVisibility(View.GONE);
                requireView().findViewById(R.id.video_bitrate_layout).setVisibility(View.GONE); // 视频码率标题和输入
                volumeContainer.setVisibility(View.VISIBLE);
                screenshotContainer.setVisibility(View.GONE);
                cutContainer.setVisibility(View.GONE);
                break;
            
            default:
                // 视频转换/压缩：显示所有参数
                videoParamsContainer.setVisibility(View.VISIBLE);
                volumeContainer.setVisibility(View.VISIBLE);
                screenshotContainer.setVisibility(View.GONE);
                cutContainer.setVisibility(View.GONE);
                break;
        }
    }

    private int getVideoBitrate(String filePath) {
        try {
            com.arthenica.ffmpegkit.FFprobeSession session =
                    com.arthenica.ffmpegkit.FFprobeKit.execute(
                            "-v quiet -select_streams v:0 -show_entries stream=bit_rate -of default=noprint_wrappers=1:nokey=1 \"" + filePath + "\"");
            if (session != null) {
                String output = session.getOutput();
                if (output != null && !output.trim().isEmpty()) {
                    int bps = Integer.parseInt(output.trim());
                    return bps / 1000;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 2000; // 默认
    }
}