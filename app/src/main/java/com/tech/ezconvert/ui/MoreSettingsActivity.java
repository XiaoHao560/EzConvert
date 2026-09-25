package com.tech.ezconvert.ui;

import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.net.Uri;
import android.graphics.BitmapFactory;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.slider.Slider;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.ConfigManager;
import com.tech.ezconvert.utils.LanguageManager;
import com.tech.ezconvert.utils.NotificationHelper;
import com.tech.ezconvert.utils.ThemeManager;
import com.tech.ezconvert.utils.ToastUtils;

public class MoreSettingsActivity extends BaseActivity {

    @Override
    protected int getTitleContainerId() {
        return R.id.title_container;
    }
    
    @Override
    protected int getScrollContentId() {
        return R.id.scroll_content;
    }
    
    private MaterialSwitch autoUpdateSwitch;
    private MaterialSwitch prereleaseSwitch;
    private MaterialSwitch notificationSwitch;
    private MaterialSwitch firebaseSwitch;
    private MaterialAutoCompleteTextView frequencySpinner;
    private LinearLayout frequencyLayout;
    private MaterialToolbar toolbar;
    private ConfigManager configManager;
    private ThemeManager themeManager;
    
    // 主题设置相关视图
    private LinearLayout itemThemeSystem;
    private LinearLayout itemThemeLight;
    private LinearLayout itemThemeDark;
    private RadioButton radioThemeSystem;
    private RadioButton radioThemeLight;
    private RadioButton radioThemeDark;
    
    // 语言设置相关视图
    private MaterialAutoCompleteTextView languageSpinner;
    
    // 动态取色相关视图
    private LinearLayout itemDynamicColor;
    private MaterialSwitch dynamicColorSwitch;

    // 自定义背景相关视图
    private LinearLayout itemCustomBackground;
    private LinearLayout itemSelectBackground;
    private MaterialSwitch customBackgroundSwitch;
    private android.widget.TextView selectedBackgroundText;
    private LinearLayout backgroundEffectCustomControls;
    private LinearLayout itemBackgroundEffectAuto;
    private LinearLayout itemBackgroundEffectCustom;
    private RadioButton radioBackgroundEffectAuto;
    private RadioButton radioBackgroundEffectCustom;
    private Slider backgroundMaskSlider;
    private Slider backgroundBlurSlider;
    private android.widget.TextView backgroundMaskValue;
    private android.widget.TextView backgroundBlurValue;

    // 动态取色来源相关视图
    private LinearLayout itemColorSourceImage;
    private LinearLayout itemColorSourceWallpaper;
    private RadioButton radioColorSourceImage;
    private RadioButton radioColorSourceWallpaper;
    
    // 输出目录设置
    private LinearLayout itemOutputDownload;
    private LinearLayout itemOutputDcim;
    private LinearLayout itemOutputCustom;
    private RadioButton radioOutputDownload;
    private RadioButton radioOutputDcim;
    private RadioButton radioOutputCustom;
    private android.widget.TextView customOutputPathText;
    private static final int REQUEST_OUTPUT_DIRECTORY = 1001;
    private static final int REQUEST_BACKGROUND_IMAGE = 1002;

    // 标记是否正在处理开关变化，防止循环触发
    private boolean isHandlingNotificationSwitch = false;
    // 标记是否刚从权限设置返回，需要检查权限状态
    private boolean needCheckPermissionOnResume = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 应用保存的主题模式，确保 Activity 创建前主题已生效
        themeManager = ThemeManager.getInstance(this);
        themeManager.applySavedTheme();
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_more_settings);

        // 设置进入动画
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

        configManager = ConfigManager.getInstance(this);
        
        // 初始化视图
        initViews();
        setupClickListeners();
        loadCurrentSettings();
    }

    // 初始化视图组件
    private void initViews() {
        // 初始化 Toolbar
        toolbar = findViewById(R.id.title_container);
        
        // 运行日志入口
        LinearLayout logEntry = findViewById(R.id.item_run_log);
        logEntry.setOnClickListener(v -> {
            Intent intent = new Intent(this, LogSettingsActivity.class);
            ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            );
            ActivityCompat.startActivity(this, intent, options.toBundle());
        });
        
        // 主题设置
        itemThemeSystem = findViewById(R.id.item_theme_system);
        itemThemeLight = findViewById(R.id.item_theme_light);
        itemThemeDark = findViewById(R.id.item_theme_dark);
        radioThemeSystem = findViewById(R.id.radio_theme_system);
        radioThemeLight = findViewById(R.id.radio_theme_light);
        radioThemeDark = findViewById(R.id.radio_theme_dark);
        
        // 语言设置
        languageSpinner = findViewById(R.id.language_spinner);
        String[] languageItems = getResources().getStringArray(R.array.language_options);
        setupSpinner(languageSpinner, languageItems, languageItems[0]);
        
        // 输出目录设置
        itemOutputDownload = findViewById(R.id.item_output_download);
        itemOutputDcim = findViewById(R.id.item_output_dcim);
        itemOutputCustom = findViewById(R.id.item_output_custom);
        radioOutputDownload = findViewById(R.id.radio_output_download);
        radioOutputDcim = findViewById(R.id.radio_output_dcim);
        radioOutputCustom = findViewById(R.id.radio_output_custom);
        customOutputPathText = findViewById(R.id.custom_output_path);

        // 动态取色开关
        itemDynamicColor = findViewById(R.id.item_dynamic_color);
        dynamicColorSwitch = findViewById(R.id.dynamic_color_switch);

        // 自定义背景
        itemCustomBackground = findViewById(R.id.item_custom_background);
        itemSelectBackground = findViewById(R.id.item_select_background);
        customBackgroundSwitch = findViewById(R.id.custom_background_switch);
        selectedBackgroundText = findViewById(R.id.selected_background_text);

        // 背景显示效果
        itemBackgroundEffectAuto = findViewById(R.id.item_background_effect_auto);
        itemBackgroundEffectCustom = findViewById(R.id.item_background_effect_custom);
        radioBackgroundEffectAuto = findViewById(R.id.radio_background_effect_auto);
        radioBackgroundEffectCustom = findViewById(R.id.radio_background_effect_custom);
        backgroundEffectCustomControls = findViewById(R.id.background_effect_custom_controls);
        backgroundMaskSlider = findViewById(R.id.background_mask_slider);
        backgroundBlurSlider = findViewById(R.id.background_blur_slider);
        backgroundMaskValue = findViewById(R.id.background_mask_value);
        backgroundBlurValue = findViewById(R.id.background_blur_value);

        // 动态取色来源
        itemColorSourceImage = findViewById(R.id.item_color_source_image);
        itemColorSourceWallpaper = findViewById(R.id.item_color_source_wallpaper);
        radioColorSourceImage = findViewById(R.id.radio_color_source_image);
        radioColorSourceWallpaper = findViewById(R.id.radio_color_source_wallpaper);
        
        // 检查设备是否支持动态取色 (Android 12+)
        if (!DynamicColors.isDynamicColorAvailable()) {
            itemDynamicColor.setEnabled(false);
            itemDynamicColor.setAlpha(0.38f);
            itemDynamicColor.setClickable(false);
            dynamicColorSwitch.setEnabled(false);
            // 点击整个行时提示用户设备不支持
            itemDynamicColor.setOnClickListener(v -> {
                ToastUtils.show(this, getString(R.string.toast_dynamic_color_not_supported));
            });
        }

        if (!DynamicColors.isDynamicColorAvailable()) {
            itemColorSourceImage.setEnabled(false);
            itemColorSourceImage.setAlpha(0.38f);
            itemColorSourceImage.setClickable(false);
            itemColorSourceWallpaper.setEnabled(false);
            itemColorSourceWallpaper.setAlpha(0.38f);
            itemColorSourceWallpaper.setClickable(false);
        }
        
        // 自动更新开关
        autoUpdateSwitch = findViewById(R.id.auto_update_switch);
        prereleaseSwitch = findViewById(R.id.prerelease_switch);
        frequencySpinner = findViewById(R.id.frequency_spinner);
        frequencyLayout = findViewById(R.id.frequency_layout);
        
        // 通知开关
        notificationSwitch = findViewById(R.id.notification_switch);
        
        // Firebase 数据收集开关
        firebaseSwitch = findViewById(R.id.firebase_switch);
        
        String[] frequencyItems = getResources().getStringArray(R.array.update_frequency_options);
        setupSpinner(frequencySpinner, frequencyItems, frequencyItems[0]);
        
        // 设置 Toolbar
        setupToolbar();
    }
    
    // 设置 Toolbar 返回按钮
    private void setupToolbar() {
        // 设置导航按钮点击事件 - 返回上一界面
        toolbar.setNavigationOnClickListener(v -> {
            finish();
        });
    }

    // 设置点击监听器
    private void setupClickListeners() {
        // 主题选项点击监听
        itemThemeSystem.setOnClickListener(v -> setThemeMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        itemThemeLight.setOnClickListener(v -> setThemeMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO));
        itemThemeDark.setOnClickListener(v -> setThemeMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES));

        // 自定义背景
        itemCustomBackground.setOnClickListener(v -> {
            boolean enabled = !customBackgroundSwitch.isChecked();
            customBackgroundSwitch.setChecked(enabled);
        });
        customBackgroundSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            configManager.setCustomBackgroundEnabled(isChecked);
            recreate();
        });
        itemSelectBackground.setOnClickListener(v -> openBackgroundImagePicker());

        itemBackgroundEffectAuto.setOnClickListener(v -> setBackgroundEffectMode(ConfigManager.BACKGROUND_EFFECT_MODE_AUTO, true));
        itemBackgroundEffectCustom.setOnClickListener(v -> setBackgroundEffectMode(ConfigManager.BACKGROUND_EFFECT_MODE_CUSTOM, true));

        backgroundMaskSlider.addOnChangeListener((slider, value, fromUser) -> {
            int rounded = Math.round(value);
            configManager.setBackgroundMaskAlpha(rounded);
            updateBackgroundEffectValues();
        });
        backgroundMaskSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(Slider slider) { }
            @Override public void onStopTrackingTouch(Slider slider) {
                if (ConfigManager.BACKGROUND_EFFECT_MODE_CUSTOM.equals(configManager.getBackgroundEffectMode())) {
                    recreate();
                }
            }
        });
        backgroundBlurSlider.addOnChangeListener((slider, value, fromUser) -> {
            int rounded = Math.round(value);
            configManager.setBackgroundBlurDp(rounded);
            updateBackgroundEffectValues();
        });
        backgroundBlurSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(Slider slider) { }
            @Override public void onStopTrackingTouch(Slider slider) {
                if (ConfigManager.BACKGROUND_EFFECT_MODE_CUSTOM.equals(configManager.getBackgroundEffectMode())) {
                    recreate();
                }
            }
        });

        // 动态取色来源
        itemColorSourceImage.setOnClickListener(v -> setDynamicColorSource(ConfigManager.DYNAMIC_COLOR_SOURCE_IMAGE));
        itemColorSourceWallpaper.setOnClickListener(v -> setDynamicColorSource(ConfigManager.DYNAMIC_COLOR_SOURCE_WALLPAPER));

        // 输出目录选择
        itemOutputDownload.setOnClickListener(v -> selectOutputPath(ConfigManager.OUTPUT_PATH_DOWNLOAD));
        itemOutputDcim.setOnClickListener(v -> selectOutputPath(ConfigManager.OUTPUT_PATH_DCIM));
        itemOutputCustom.setOnClickListener(v -> openOutputDirectoryPicker());
        
        // 语言选择监听
        languageSpinner.setOnItemClickListener((parent, view, position, id) -> {
            String languageCode = mapPositionToLanguage(position);
            String currentLanguage = LanguageManager.getCurrentLanguage(this);
            if (!currentLanguage.equals(languageCode)) {
                LanguageManager.setAppLanguage(this, languageCode);
            }
        });

        // 动态取色开关监听: 保存后即时刷新当前 Activity
        dynamicColorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            configManager.setDynamicColorEnabled(isChecked);
            themeManager.applyDynamicColorAndRecreate(MoreSettingsActivity.this);
        });
        
        // 自动更新开关监听
        autoUpdateSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                configManager.setAutoCheckUpdateEnabled(isChecked);
                updateFrequencyLayoutVisibility(isChecked);
            }
        });
        
        // 检测测试版更新开关监听
        prereleaseSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            configManager.setIncludePrerelease(isChecked);
        });
        
        // 通知开关监听
        notificationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // 防止循环触发
                if (isHandlingNotificationSwitch) {
                    return;
                }
                
                if (isChecked) {
                    // 开启通知，检查权限
                    handleNotificationEnable();
                } else {
                    // 关闭通知，直接保存
                    configManager.setNotificationEnabled(false);
                }
            }
        });
        
        // 频率选择监听
        frequencySpinner.setOnItemClickListener((parent, view, position, id) -> {
            int frequency = mapPositionToFrequency(position);
            configManager.setUpdateCheckFrequency(frequency);
        });
        
        // Firebase 数据收集开关监听
        firebaseSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            configManager.setFirebaseAnalyticsEnabled(isChecked);
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(isChecked);
        });
    }

    private void loadCurrentSettings() {
        // 先解绑所有监听器，防止触发保存
        autoUpdateSwitch.setOnCheckedChangeListener(null);
        prereleaseSwitch.setOnCheckedChangeListener(null);
        notificationSwitch.setOnCheckedChangeListener(null);
        dynamicColorSwitch.setOnCheckedChangeListener(null);
        customBackgroundSwitch.setOnCheckedChangeListener(null);
        firebaseSwitch.setOnCheckedChangeListener(null);
        
        // 加载当前设置
        boolean autoCheckEnabled = configManager.isAutoCheckUpdateEnabled();
        boolean includeprereleaseEnabled = configManager.isIncludePrerelease();
        int currentFrequency = configManager.getUpdateCheckFrequency();
        boolean notificationEnabled = configManager.isNotificationEnabled();
        boolean dynamicColorEnabled = configManager.isDynamicColorEnabled();
        boolean customBackgroundEnabled = configManager.isCustomBackgroundEnabled();
        String customBackgroundUri = configManager.getCustomBackgroundUri();
        String dynamicColorSource = configManager.getDynamicColorSource();
        String backgroundEffectMode = configManager.getBackgroundEffectMode();
        boolean firebaseEnabled = configManager.isFirebaseAnalyticsEnabled();

        updateOutputPathUi(configManager.getOutputPathMode(), configManager.getCustomOutputUri());
        
        // 更新开关状态
        autoUpdateSwitch.setChecked(autoCheckEnabled);
        prereleaseSwitch.setChecked(includeprereleaseEnabled);
        notificationSwitch.setChecked(notificationEnabled);
        firebaseSwitch.setChecked(firebaseEnabled);
        
        // 仅在设备支持时更新动态取色开关状态
        if (DynamicColors.isDynamicColorAvailable()) {
            dynamicColorSwitch.setChecked(dynamicColorEnabled);
        }

        customBackgroundSwitch.setChecked(customBackgroundEnabled);
        updateBackgroundImageUi(customBackgroundUri);
        updateDynamicColorSourceUi(dynamicColorSource);
        updateBackgroundEffectUi(backgroundEffectMode);
        updateBackgroundEffectValues();
        
        // 更新Spinner选择
        int spinnerPosition = mapFrequencyToPosition(currentFrequency);
        String[] frequencyItems = getResources().getStringArray(R.array.update_frequency_options);
        frequencySpinner.setText(frequencyItems[spinnerPosition], false);
        
        // 加载主题设置
        int currentThemeMode = themeManager.getThemeMode();
        updateThemeRadioButtons(currentThemeMode);

        // 加载语言设置
        String currentLanguage = LanguageManager.getCurrentLanguage(this);
        int langPosition = mapLanguageToPosition(currentLanguage);
        String[] languageItems = getResources().getStringArray(R.array.language_options);
        languageSpinner.setText(languageItems[langPosition], false);
        
        // 更新布局可见性
        updateFrequencyLayoutVisibility(autoCheckEnabled);
        
        // 重新绑定监听器
        autoUpdateSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                configManager.setAutoCheckUpdateEnabled(isChecked);
                updateFrequencyLayoutVisibility(isChecked);
            }
        });
        
        prereleaseSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            configManager.setIncludePrerelease(isChecked);
        });
        
        notificationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton ButtonView, boolean isChecked) {
                if (isHandlingNotificationSwitch) return;
                if (isChecked) {
                    handleNotificationEnable();
                } else {
                    configManager.setNotificationEnabled(false);
                }
            }
        });
        
        customBackgroundSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            configManager.setCustomBackgroundEnabled(isChecked);
            recreate();
        });

        dynamicColorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            configManager.setDynamicColorEnabled(isChecked);
            themeManager.applyDynamicColorAndRecreate(MoreSettingsActivity.this);
        });
        
        firebaseSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            configManager.setFirebaseAnalyticsEnabled(isChecked);
            FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(isChecked);
        });
    }
    
    private void setBackgroundEffectMode(String mode, boolean recreateAfterChange) {
        configManager.setBackgroundEffectMode(mode);
        updateBackgroundEffectUi(mode);
        if (recreateAfterChange) {
            recreate();
        }
    }

    private void updateBackgroundEffectUi(String mode) {
        boolean custom = ConfigManager.BACKGROUND_EFFECT_MODE_CUSTOM.equals(mode);
        radioBackgroundEffectAuto.setChecked(!custom);
        radioBackgroundEffectCustom.setChecked(custom);
        backgroundEffectCustomControls.setVisibility(custom ? View.VISIBLE : View.GONE);
    }

    private void updateBackgroundEffectValues() {
        int mask = configManager.getBackgroundMaskAlpha();
        int blur = configManager.getBackgroundBlurDp();
        backgroundMaskSlider.setValue(mask);
        backgroundBlurSlider.setValue(blur);
        backgroundMaskValue.setText(getString(R.string.background_mask_value, mask));
        backgroundBlurValue.setText(getString(R.string.background_blur_value, blur));
    }

    private void setDynamicColorSource(String source) {
        if (ConfigManager.DYNAMIC_COLOR_SOURCE_IMAGE.equals(source)
                && (configManager.getCustomBackgroundUri() == null
                || configManager.getCustomBackgroundUri().isEmpty())) {
            ToastUtils.show(this, getString(R.string.toast_select_background_first));
            return;
        }
        configManager.setDynamicColorSource(source);
        updateDynamicColorSourceUi(source);
        themeManager.applyDynamicColorAndRecreate(this);
    }

    private void updateDynamicColorSourceUi(String source) {
        radioColorSourceImage.setChecked(ConfigManager.DYNAMIC_COLOR_SOURCE_IMAGE.equals(source));
        radioColorSourceWallpaper.setChecked(ConfigManager.DYNAMIC_COLOR_SOURCE_WALLPAPER.equals(source));
    }

    private void updateBackgroundImageUi(String uriString) {
        if (uriString == null || uriString.isEmpty()) {
            selectedBackgroundText.setText(R.string.custom_background_no_image);
        } else {
            selectedBackgroundText.setText(R.string.custom_background_image_selected);
        }
    }

    private void openBackgroundImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_BACKGROUND_IMAGE);
        } catch (ActivityNotFoundException e) {
            ToastUtils.show(this, getString(R.string.toast_cannot_open_image_picker));
        }
    }

    /**
     * 将用户选择的图片复制到应用私有目录。这样即使原图片提供商不支持持久化 URI，
     * 应用重启后仍可稳定读取背景图片。
     */
    private Uri copyBackgroundImageToPrivateStorage(Uri sourceUri) {
        File directory = new File(getFilesDir(), "custom_background");
        if (!directory.exists() && !directory.mkdirs()) {
            return null;
        }

        File targetFile = new File(directory, "background_image");
        try (InputStream input = getContentResolver().openInputStream(sourceUri);
             OutputStream output = new FileOutputStream(targetFile, false)) {
            if (input == null) {
                targetFile.delete();
                return null;
            }

            byte[] buffer = new byte[16 * 1024];
            int read;
            long total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                // 防止异常提供商返回超大文件导致无意义的磁盘占用。
                if (total > 50L * 1024L * 1024L) {
                    output.flush();
                    targetFile.delete();
                    return null;
                }
                output.write(buffer, 0, read);
            }
            output.flush();
        } catch (Exception e) {
            targetFile.delete();
            return null;
        }

        // 先快速验证文件确实是一张可解码的图片。
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(targetFile.getAbsolutePath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            targetFile.delete();
            return null;
        }
        return Uri.fromFile(targetFile);
    }

    private void selectOutputPath(String mode) {
        configManager.setOutputPathMode(mode);
        updateOutputPathUi(mode, configManager.getCustomOutputUri());
    }

    private void openOutputDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_OUTPUT_DIRECTORY);
        } catch (ActivityNotFoundException e) {
            ToastUtils.show(this, getString(R.string.toast_cannot_open_folder_picker));
        }
    }

    private void updateOutputPathUi(String mode, String customUri) {
        radioOutputDownload.setChecked(ConfigManager.OUTPUT_PATH_DOWNLOAD.equals(mode));
        radioOutputDcim.setChecked(ConfigManager.OUTPUT_PATH_DCIM.equals(mode));
        radioOutputCustom.setChecked(ConfigManager.OUTPUT_PATH_CUSTOM.equals(mode));
        if (customUri == null || customUri.isEmpty()) {
            customOutputPathText.setText(R.string.output_path_custom_hint);
        } else {
            String displayPath = getTreeUriDisplayPath(Uri.parse(customUri));
            customOutputPathText.setText(displayPath != null ? displayPath : customUri);
        }
    }

    private String getTreeUriDisplayPath(Uri uri) {
        try {
            String documentId = DocumentsContract.getTreeDocumentId(uri);
            if (documentId == null) return null;
            String[] split = documentId.split(":", 2);
            if (split.length == 2 && "primary".equalsIgnoreCase(split[0])) {
                return split[1].isEmpty()
                        ? getString(R.string.output_path_internal_storage)
                        : getString(R.string.output_path_internal_storage) + "/" + split[1];
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_BACKGROUND_IMAGE) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

            Uri imageUri = data.getData();
            Uri localImageUri = copyBackgroundImageToPrivateStorage(imageUri);
            if (localImageUri == null) {
                ToastUtils.show(this, getString(R.string.toast_background_image_read_failed));
                return;
            }

            configManager.setCustomBackgroundUri(localImageUri.toString());

            // 背景图片实际保存在固定的应用私有文件中，因此连续选择不同图片时
            // URI 可能完全相同，ThemeManager 若继续复用旧 Bitmap，就会出现
            // "主题取色已经切换，但界面背景仍然显示上一张图片”的状态不一致
            // 保存新图片后先显式失效背景缓存，再重建 Activity，确保背景和取色都基于同一张最新图片
            themeManager.invalidateCustomBackgroundCache();

            // 用户明确选择了图片时直接启用自定义背景，并自动切换为图片取色。
            configManager.setCustomBackgroundEnabled(true);
            configManager.setDynamicColorSource(ConfigManager.DYNAMIC_COLOR_SOURCE_IMAGE);
            updateBackgroundImageUi(localImageUri.toString());
            updateDynamicColorSourceUi(ConfigManager.DYNAMIC_COLOR_SOURCE_IMAGE);
            recreate();
            return;
        }

        if (requestCode != REQUEST_OUTPUT_DIRECTORY || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;

        Uri treeUri = data.getData();
        try {
            int takeFlags = data.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
        } catch (SecurityException e) {
            ToastUtils.show(this, getString(R.string.toast_folder_permission_failed));
            return;
        }

        // 自定义目录使用 SAF Tree URI，不能限制为 primary:/内部存储
        // 这样用户也可以选择其他受 SAF 支持的存储位置；真正写文件时由 Worker
        // 通过 ContentResolver 输出，兼容 Android 7.0(API 24) 到 Android 15
        if (!"content".equalsIgnoreCase(treeUri.getScheme())
                || !DocumentsContract.isTreeUri(treeUri)) {
            ToastUtils.show(this, getString(R.string.toast_folder_permission_failed));
            return;
        }

        boolean hasWritePermission = false;
        for (android.content.UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
            if (treeUri.equals(permission.getUri()) && permission.isWritePermission()) {
                hasWritePermission = true;
                break;
            }
        }
        if (!hasWritePermission) {
            ToastUtils.show(this, getString(R.string.toast_folder_permission_failed));
            return;
        }

        configManager.setCustomOutputUri(treeUri.toString());
        configManager.setOutputPathMode(ConfigManager.OUTPUT_PATH_CUSTOM);
        updateOutputPathUi(ConfigManager.OUTPUT_PATH_CUSTOM, treeUri.toString());
    }

    // 将下拉菜单位置映射为语言代码
    private String mapPositionToLanguage(int position) {
        switch (position) {
            case 0: // 跟随系统
                return LanguageManager.LANG_SYSTEM;
            case 1: // 简体中文
                return LanguageManager.LANG_ZH;
            case 2: // English
                return LanguageManager.LANG_EN;
            default:
                return LanguageManager.LANG_SYSTEM;
        }
    }

    // 将语言代码映射为下拉菜单位置
    private int mapLanguageToPosition(String languageCode) {
        switch (languageCode) {
            case "system":
                return 0;
            case "zh":
                return 1;
            case "en":
                return 2;
            default:
                return 0;
        }
    }

    // 更新频率选择区域的可见性
    private void updateFrequencyLayoutVisibility(boolean enabled) {
        if (frequencyLayout != null) {
            frequencyLayout.setEnabled(enabled);
            frequencyLayout.setAlpha(enabled ? 1.0f : 0.5f);
            frequencySpinner.setEnabled(enabled);
        }
    }

    // 将下拉菜单位置映射为频率值
    private int mapPositionToFrequency(int position) {
        switch (position) {
            case 0: // 每24小时检测
                return 1; // FREQUENCY_EVERY_24_HOURS
            case 1: // 每次进入应用检测
                return 2; // FREQUENCY_EVERY_LAUNCH
            default:
                return 1;
        }
    }

    // 将频率值映射为下拉菜单位置
    private int mapFrequencyToPosition(int frequency) {
        switch (frequency) {
            case 1: // FREQUENCY_EVERY_24_HOURS
                return 0;
            case 2: // FREQUENCY_EVERY_LAUNCH
                return 1;
            default:
                return 0;
        }
    }
    
    // 设置主题模式并更新 UI 状态
    private void setThemeMode(int mode) {
        themeManager.setThemeMode(mode);
        updateThemeRadioButtons(mode);
    }
    
    // 更新主题单选按钮状态
    private void updateThemeRadioButtons(int mode) {
        radioThemeSystem.setChecked(mode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        radioThemeLight.setChecked(mode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        radioThemeDark.setChecked(mode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
    }
    
    // 处理用户开启通知
    private void handleNotificationEnable() {
        
        // 检查是否已有权限
        if (NotificationHelper.areNotificationsEnabled(this)) {
            // 已有权限，直接开启
            configManager.setNotificationEnabled(true);
        } else {
            // 无权限，显示申请对话框
            showNotificationPermissionDialog();
        }
    }
    
    // 显示通知权限申请对话框
    private void showNotificationPermissionDialog() {
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.notification_permission_title)
            .setMessage(R.string.notification_permission_message)
            .setPositiveButton(R.string.notification_permission_go_settings, (dialog, which) -> {
                // 点击去开启，保存设置并标记需要检查权限
                configManager.setNotificationEnabled(true);
                needCheckPermissionOnResume = true;
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(intent);
            })
            .setNegativeButton(R.string.notification_permission_later, (dialog, which) -> {
                // 点击拒绝，关闭开关
                isHandlingNotificationSwitch = true;
                notificationSwitch.setChecked(false);
                configManager.setNotificationEnabled(false);
                isHandlingNotificationSwitch = false;
                needCheckPermissionOnResume = false;
            })
            .setCancelable(false) // 禁止点击外部关闭对话框
            .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // 如果从权限设置返回，检查权限状态
        if (needCheckPermissionOnResume) {
            needCheckPermissionOnResume = false;
            
            // 检查是否实际开启了权限
            if (!NotificationHelper.areNotificationsEnabled(this)) {
                // 没有开启权限，关闭开关并提示
                isHandlingNotificationSwitch = true;
                notificationSwitch.setChecked(false);
                configManager.setNotificationEnabled(false);
                isHandlingNotificationSwitch = false;
                
                ToastUtils.show(this, getString(R.string.toast_notification_permission_denied));
            }
        }
    }

    @Override
    public void finish() {
        super.finish();
        // 设置退出动画
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
