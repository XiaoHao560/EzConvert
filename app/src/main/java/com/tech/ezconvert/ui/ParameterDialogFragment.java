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
    private String selectedPresetName;
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
        String defaultPresetName = getString(R.string.preset_default_name);
        selectedPresetName = defaultPresetName;
        currentParams = presetManager.loadPreset(defaultPresetName);
        if (currentParams == null) {
            currentParams = ParameterData.createDefault();
            presetManager.savePreset(defaultPresetName, currentParams);
        }

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
            case "convert": return getString(R.string.task_convert_video);
            case "compress": return getString(R.string.task_compress_video);
            case "extract_audio": return getString(R.string.task_extract_audio);
            case "cut_video": return getString(R.string.task_cut_video);
            case "screenshot": return getString(R.string.task_screenshot);
            case "convert_audio": return getString(R.string.task_convert_audio);
            case "cut_audio": return getString(R.string.task_cut_audio);
            default: return getString(R.string.task_process_file);
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
        String defaultPresetName = getString(R.string.preset_default_name);
        if (names.isEmpty()) {
            // 创建默认预设
            presetManager.savePreset(defaultPresetName, ParameterData.createDefault());
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
        tvVolumePercent.setText(data.volume + getString(R.string.unit_percent));

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
        setupSpinner(spinnerVideoCodec, new String[]{getString(R.string.codec_h264_hw_best)}, getString(R.string.codec_h264_hw_best));

        // 音频编码器
        setupSpinner(spinnerAudioCodec, new String[]{getString(R.string.codec_aac_best)}, getString(R.string.codec_aac_best));

        // 码率单位
        setupSpinner(spinnerBitrateUnit, new String[]{getString(R.string.unit_kbps), getString(R.string.unit_mbps)}, getString(R.string.unit_mbps));

        // 截图格式
        setupSpinner(spinnerScreenshotFormat, new String[]{getString(R.string.format_jpeg), getString(R.string.format_png)}, getString(R.string.format_jpeg));

        // 输出格式切换时更新编码器列表
        spinnerOutputFormat.setOnItemClickListener((parent, view, position, id) -> {
            String format = parent.getAdapter().getItem(position).toString();
            updateCodecOptions(format);
        });
    }

    private String[] getOutputFormatsForTask() {
        if ("extract_audio".equals(taskType) || "convert_audio".equals(taskType) || "cut_audio".equals(taskType)) {
            return new String[]{
                getString(R.string.format_mp3),
                getString(R.string.format_wav),
                getString(R.string.format_aac),
                getString(R.string.format_flac_audio),
                getString(R.string.format_ogg),
                getString(R.string.format_m4a)
            };
        }
        if ("screenshot".equals(taskType)) {
            return new String[]{
                getString(R.string.format_jpeg),
                getString(R.string.format_png)
            };
        }
        return new String[]{
            getString(R.string.format_mp4),
            getString(R.string.format_mkv),
            getString(R.string.format_webm),
            getString(R.string.format_avi),
            getString(R.string.format_mov),
            getString(R.string.format_flv),
            getString(R.string.format_gif)
        };
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
                    return new String[]{
                        getString(R.string.codec_h264_hw_best),
                        getString(R.string.codec_hevc_hw),
                        getString(R.string.codec_h264_sw),
                        getString(R.string.codec_h265_sw)
                    };
                } else {
                    return new String[]{
                        getString(R.string.codec_h264_sw_best),
                        getString(R.string.codec_h265_sw),
                        getString(R.string.codec_h264_hw_best),
                        getString(R.string.codec_hevc_hw)
                    };
                }
            case "mkv":
                return new String[]{
                    getString(R.string.codec_h265_sw_best),
                    getString(R.string.codec_h264_sw)
                };
            case "webm":
                return new String[]{
                    getString(R.string.codec_vp9_best),
                    getString(R.string.codec_vp8)
                };
            case "avi":
                return new String[]{
                    getString(R.string.codec_mpeg4_best),
                    getString(R.string.codec_h264_sw)
                };
            case "flv":
                if (hwAccel) {
                    return new String[]{
                        getString(R.string.codec_h264_hw_best),
                        getString(R.string.codec_h264_sw)
                    };
                } else {
                    return new String[]{
                        getString(R.string.codec_h264_sw_best),
                        getString(R.string.codec_h264_hw_best)
                    };
                }
            case "gif":
                return new String[]{
                    getString(R.string.codec_gif_best)
                };
            default:
                return new String[]{
                    getString(R.string.codec_default_best)
                };
        }
    }

    private String[] getAudioCodecsForFormat(String format) {
        switch (format) {
            case "mp4":
            case "mov":
            case "flv":
                return new String[]{
                    getString(R.string.codec_aac_best),
                    getString(R.string.codec_mp3)
                };
            case "mkv":
                return new String[]{
                    getString(R.string.codec_opus_best),
                    getString(R.string.codec_aac),
                    getString(R.string.codec_mp3)
                };
            case "webm":
                return new String[]{
                    getString(R.string.codec_opus_best)
                };
            case "avi":
                return new String[]{
                    getString(R.string.codec_mp3_best)
                };
            case "mp3":
                return new String[]{
                    getString(R.string.codec_mp3_best)
                };
            case "wav":
                return new String[]{
                    getString(R.string.codec_pcm_best)
                };
            case "aac":
                return new String[]{
                    getString(R.string.codec_aac_best)
                };
            case "flac":
                return new String[]{
                    getString(R.string.codec_flac_best)
                };
            case "ogg":
                return new String[]{
                    getString(R.string.codec_vorbis_best)
                };
            case "m4a":
                return new String[]{
                    getString(R.string.codec_aac_best)
                };
            default:
                return new String[]{
                    getString(R.string.codec_aac_best)
                };
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
            tvVolumePercent.setText(vol + getString(R.string.unit_percent));
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
                ToastUtils.show(requireActivity(), getString(R.string.toast_preset_saved, newName));
            } else {
                ToastUtils.show(requireActivity(), getString(R.string.toast_preset_save_failed));
            }
        });

        btnDeletePreset.setOnClickListener(v -> {
            String defaultPresetName = getString(R.string.preset_default_name);
            if (defaultPresetName.equals(selectedPresetName)) {
                ToastUtils.show(requireActivity(), getString(R.string.toast_preset_delete_default));
                return;
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dialog_delete_preset_title)
                    .setMessage(getString(R.string.dialog_delete_preset_message, selectedPresetName))
                    .setPositiveButton(R.string.btn_delete, (dialog, which) -> {
                        if (presetManager.deletePreset(selectedPresetName)) {
                            refreshPresetList();
                            // 切换到默认
                            int pos = presetManager.listPresetNames().indexOf(defaultPresetName);
                            if (pos >= 0) {
                                presetAdapter.selectPosition(pos);
                                selectedPresetName = defaultPresetName;
                                loadPreset(defaultPresetName);
                            }
                            ToastUtils.show(requireActivity(), getString(R.string.toast_preset_deleted));
                        } else {
                            ToastUtils.show(requireActivity(), getString(R.string.toast_preset_delete_failed));
                        }
                    })
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
        });

        btnReset.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.dialog_reset_title)
                    .setMessage(R.string.dialog_reset_message)
                    .setPositiveButton(R.string.btn_reset, (dialog, which) -> {
                        currentParams = ParameterData.createDefault();
                        loadPreset(getString(R.string.preset_default_name));
                        ToastUtils.show(requireActivity(), getString(R.string.toast_reset_done));
                    })
                    .setNegativeButton(R.string.btn_cancel, null)
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
            ToastUtils.show(requireActivity(), getString(R.string.toast_select_output_format));
            return false;
        }
        if ("screenshot".equals(taskType)) {
            if (etScreenshotTime.getText().toString().isEmpty()) {
                ToastUtils.show(requireActivity(), getString(R.string.toast_enter_screenshot_time));
                return false;
            }
        }
        if ("cut_video".equals(taskType) || "cut_audio".equals(taskType)) {
            if (etCutStart.getText().toString().isEmpty() || etCutDuration.getText().toString().isEmpty()) {
                ToastUtils.show(requireActivity(), getString(R.string.toast_enter_cut_times));
                return false;
            }
        }
        return true;
    }

    private void updateUIForTaskType() {
        // 显示当前文件信息
        if (currentFilePath != null) {
            String fileName = new File(currentFilePath).getName();
            tvFileInfo.setText(getString(R.string.current_file_format, fileName));
        } else {
            tvFileInfo.setText(R.string.current_file_none);
        }
        
        // 如果是压缩任务，显示当前码率
        if (isCompressTask && currentVideoBitrateKbps > 0) {
            tvCurrentBitrate.setVisibility(View.VISIBLE);
            if (currentVideoBitrateKbps >= 1000) {
                tvCurrentBitrate.setText(getString(R.string.current_bitrate_mbps, String.valueOf(currentVideoBitrateKbps / 1000.0)));
            } else {
                tvCurrentBitrate.setText(getString(R.string.current_bitrate_kbps, currentVideoBitrateKbps));
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
                setupSpinner(spinnerOutputFormat, new String[]{
                    getString(R.string.format_jpeg),
                    getString(R.string.format_png)
                }, getString(R.string.format_jpeg));
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
