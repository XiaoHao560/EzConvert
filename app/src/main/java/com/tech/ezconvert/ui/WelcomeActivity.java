package com.tech.ezconvert.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.tech.ezconvert.MainActivity;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.ConfigManager;
import com.tech.ezconvert.utils.LanguageManager;
import com.tech.ezconvert.utils.ThemeManager;
import com.tech.ezconvert.utils.ToastUtils;

import java.util.ArrayList;
import java.util.List;

/** 首次启动引导页，集中完成常用设置和权限说明 */
public class WelcomeActivity extends BaseActivity {

    private static final String PREFS_FIRST_RUN = "ezconvert_first_run";
    private static final String KEY_WELCOME_COMPLETED = "welcome_completed";

    private ConfigManager configManager;
    private ThemeManager themeManager;

    private RadioGroup outputGroup;
    private RadioGroup themeGroup;
    private RadioButton outputDownload;
    private RadioButton outputDcim;
    private RadioButton outputCustom;
    private RadioButton themeSystem;
    private RadioButton themeLight;
    private RadioButton themeDark;
    private TextView customPathText;
    private TextView mediaPermissionStatus;
    private TextView notificationPermissionStatus;
    private MaterialButton grantMediaButton;
    private MaterialButton grantNotificationsButton;
    private MaterialSwitch dynamicColorSwitch;
    private MaterialSwitch enableNotificationsSwitch;
    private android.view.View notificationConfigContainer;
    private MaterialAutoCompleteTextView languageSpinner;

    private ActivityResultLauncher<Intent> folderPickerLauncher;
    private ActivityResultLauncher<String[]> mediaPermissionLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    /** 判断首次启动引导是否已完成，供主界面兜底拦截首次直接打开的分享 Intent */
    public static boolean isWelcomeCompleted(Context context) {
        return context.getSharedPreferences(PREFS_FIRST_RUN, Context.MODE_PRIVATE)
                .getBoolean(KEY_WELCOME_COMPLETED, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isWelcomeCompleted(this)) {
            openMainActivity();
            return;
        }

        // 欢迎页保留系统栏安全区域，避免底部主按钮被导航栏遮挡
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        configManager = ConfigManager.getInstance(this);
        themeManager = ThemeManager.getInstance(this);

        registerLaunchers();
        setContentView(R.layout.activity_welcome);
        bindViews();
        loadCurrentSettings();
        setupSettingsListeners();
        refreshPermissionStatus();
    }

    private void registerLaunchers() {
        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null
                            || result.getData().getData() == null) {
                        updateOutputUi(configManager.getOutputPathMode());
                        return;
                    }
                    Uri treeUri = result.getData().getData();
                    int grantFlags = result.getData().getFlags()
                            & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    if (grantFlags == 0) {
                        grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    }
                    try {
                        getContentResolver().takePersistableUriPermission(treeUri, grantFlags);
                    } catch (SecurityException e) {
                        ToastUtils.show(this, getString(R.string.toast_folder_permission_failed));
                        updateOutputUi(configManager.getOutputPathMode());
                        return;
                    }
                    if (!"content".equalsIgnoreCase(treeUri.getScheme())
                            || !DocumentsContract.isTreeUri(treeUri)) {
                        ToastUtils.show(this, getString(R.string.toast_folder_permission_failed));
                        updateOutputUi(configManager.getOutputPathMode());
                        return;
                    }
                    boolean canWrite = false;
                    for (android.content.UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
                        if (treeUri.equals(permission.getUri()) && permission.isWritePermission()) {
                            canWrite = true;
                            break;
                        }
                    }
                    if (!canWrite) {
                        ToastUtils.show(this, getString(R.string.toast_folder_permission_failed));
                        updateOutputUi(configManager.getOutputPathMode());
                        return;
                    }
                    configManager.setCustomOutputUri(treeUri.toString());
                    configManager.setOutputPathMode(ConfigManager.OUTPUT_PATH_CUSTOM);
                    updateOutputUi(ConfigManager.OUTPUT_PATH_CUSTOM);
                });

        mediaPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> refreshPermissionStatus());

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    configManager.setNotificationEnabled(Boolean.TRUE.equals(granted));
                    refreshPermissionStatus();
                });
    }

    private void bindViews() {
        outputGroup = findViewById(R.id.welcome_output_group);
        outputDownload = findViewById(R.id.welcome_output_download);
        outputDcim = findViewById(R.id.welcome_output_dcim);
        outputCustom = findViewById(R.id.welcome_output_custom);
        customPathText = findViewById(R.id.welcome_custom_path_text);

        themeGroup = findViewById(R.id.welcome_theme_group);
        themeSystem = findViewById(R.id.welcome_theme_system);
        themeLight = findViewById(R.id.welcome_theme_light);
        themeDark = findViewById(R.id.welcome_theme_dark);
        dynamicColorSwitch = findViewById(R.id.welcome_dynamic_color_switch);
        languageSpinner = findViewById(R.id.welcome_language_spinner);

        mediaPermissionStatus = findViewById(R.id.welcome_media_permission_status);
        notificationPermissionStatus = findViewById(R.id.welcome_notification_permission_status);
        grantMediaButton = findViewById(R.id.welcome_grant_media);
        grantNotificationsButton = findViewById(R.id.welcome_grant_notifications);
        enableNotificationsSwitch = findViewById(R.id.welcome_enable_notifications_switch);
        notificationConfigContainer = findViewById(R.id.welcome_notification_config_container);

        findViewById(R.id.welcome_start_button).setOnClickListener(v -> finishWelcome());
        enableNotificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            configManager.setNotificationEnabled(isChecked);
            refreshPermissionStatus();
        });
        grantMediaButton.setOnClickListener(v -> requestMediaPermissions());
        grantNotificationsButton.setOnClickListener(v -> requestNotificationPermission());
        findViewById(R.id.welcome_choose_folder).setOnClickListener(v -> openOutputDirectoryPicker());
    }

    private void loadCurrentSettings() {
        updateOutputUi(configManager.getOutputPathMode());

        int themeMode = themeManager.getThemeMode();
        if (themeMode == AppCompatDelegate.MODE_NIGHT_NO) {
            themeLight.setChecked(true);
        } else if (themeMode == AppCompatDelegate.MODE_NIGHT_YES) {
            themeDark.setChecked(true);
        } else {
            themeSystem.setChecked(true);
        }

        dynamicColorSwitch.setChecked(themeManager.isDynamicColorEnabled());
        boolean dynamicColorAvailable = com.google.android.material.color.DynamicColors.isDynamicColorAvailable();
        dynamicColorSwitch.setEnabled(dynamicColorAvailable);
        dynamicColorSwitch.setAlpha(dynamicColorAvailable ? 1f : 0.5f);

        String[] languageItems = getResources().getStringArray(R.array.language_options);
        setupSpinner(languageSpinner, languageItems,
                languageItems[mapLanguageToPosition(LanguageManager.getCurrentLanguage(this))]);
    }

    private void setupSettingsListeners() {
        outputGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.welcome_output_download) {
                configManager.setOutputPathMode(ConfigManager.OUTPUT_PATH_DOWNLOAD);
                updateOutputUi(ConfigManager.OUTPUT_PATH_DOWNLOAD);
            } else if (checkedId == R.id.welcome_output_dcim) {
                configManager.setOutputPathMode(ConfigManager.OUTPUT_PATH_DCIM);
                updateOutputUi(ConfigManager.OUTPUT_PATH_DCIM);
            } else if (checkedId == R.id.welcome_output_custom) {
                String currentUri = configManager.getCustomOutputUri();
                if (currentUri == null || currentUri.isEmpty()) {
                    openOutputDirectoryPicker();
                } else {
                    configManager.setOutputPathMode(ConfigManager.OUTPUT_PATH_CUSTOM);
                    updateOutputUi(ConfigManager.OUTPUT_PATH_CUSTOM);
                }
            }
        });

        themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.welcome_theme_system) {
                themeManager.setThemeMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            } else if (checkedId == R.id.welcome_theme_light) {
                themeManager.setThemeMode(AppCompatDelegate.MODE_NIGHT_NO);
            } else if (checkedId == R.id.welcome_theme_dark) {
                themeManager.setThemeMode(AppCompatDelegate.MODE_NIGHT_YES);
            }
        });

        dynamicColorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!com.google.android.material.color.DynamicColors.isDynamicColorAvailable()) return;
            configManager.setDynamicColorEnabled(isChecked);
            themeManager.applyDynamicColorAndRecreate(this);
        });

        languageSpinner.setOnItemClickListener((parent, view, position, id) -> {
            String[] languageItems = getResources().getStringArray(R.array.language_options);
            String languageCode = mapPositionToLanguage(position);
            if (!LanguageManager.getCurrentLanguage(this).equals(languageCode)) {
                languageSpinner.setText(languageItems[position], false);
                LanguageManager.setAppLanguage(this, languageCode);
            }
        });
    }

    private void updateOutputUi(String mode) {
        // 仅在自定义目录有效时保留 custom 模式，避免用户取消选择后进入无效输出目录
        String customUri = configManager.getCustomOutputUri();
        if (ConfigManager.OUTPUT_PATH_CUSTOM.equals(mode)
                && (customUri == null || customUri.isEmpty())) {
            mode = ConfigManager.OUTPUT_PATH_DOWNLOAD;
            configManager.setOutputPathMode(mode);
        }
        outputDownload.setChecked(ConfigManager.OUTPUT_PATH_DOWNLOAD.equals(mode));
        outputDcim.setChecked(ConfigManager.OUTPUT_PATH_DCIM.equals(mode));
        outputCustom.setChecked(ConfigManager.OUTPUT_PATH_CUSTOM.equals(mode));

        if (customUri == null || customUri.isEmpty()) {
            customPathText.setText(R.string.output_path_custom_hint);
        } else {
            String displayPath = getTreeUriDisplayPath(Uri.parse(customUri));
            customPathText.setText(displayPath == null ? customUri : displayPath);
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

    private void openOutputDirectoryPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            folderPickerLauncher.launch(intent);
        } catch (Exception e) {
            ToastUtils.show(this, getString(R.string.toast_cannot_open_folder_picker));
            updateOutputUi(configManager.getOutputPathMode());
        }
    }

    private int mapLanguageToPosition(String languageCode) {
        if (LanguageManager.LANG_ZH.equals(languageCode)) return 1;
        if (LanguageManager.LANG_EN.equals(languageCode)) return 2;
        return 0;
    }

    private String mapPositionToLanguage(int position) {
        if (position == 1) return LanguageManager.LANG_ZH;
        if (position == 2) return LanguageManager.LANG_EN;
        return LanguageManager.LANG_SYSTEM;
    }

    private void requestMediaPermissions() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addMissingPermission(missing, Manifest.permission.READ_MEDIA_AUDIO);
            addMissingPermission(missing, Manifest.permission.READ_MEDIA_VIDEO);
        } else {
            addMissingPermission(missing, Manifest.permission.READ_EXTERNAL_STORAGE);
            addMissingPermission(missing, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (missing.isEmpty()) {
            refreshPermissionStatus();
        } else {
            mediaPermissionLauncher.launch(missing.toArray(new String[0]));
        }
    }

    private void addMissingPermission(List<String> permissions, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(permission);
        }
    }

    private boolean hasMediaPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                configManager.setNotificationEnabled(true);
                refreshPermissionStatus();
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                configManager.setNotificationEnabled(true);
                refreshPermissionStatus();
            } else {
                Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(intent);
            }
        }
    }

    private void refreshPermissionStatus() {
        if (mediaPermissionStatus == null) return;
        boolean hasMedia = hasMediaPermissions();
        mediaPermissionStatus.setText(hasMedia
                ? R.string.welcome_permission_status_granted
                : R.string.welcome_permission_status_required);
        grantMediaButton.setText(hasMedia ? R.string.welcome_granted : R.string.welcome_grant);
        grantMediaButton.setEnabled(!hasMedia);

        boolean notificationPermissionAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionAllowed = notificationPermissionAllowed
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        boolean notificationsEnabled = notificationPermissionAllowed && configManager.isNotificationEnabled();
        notificationPermissionStatus.setText(notificationPermissionAllowed
                ? R.string.welcome_permission_status_granted
                : R.string.welcome_permission_status_optional);
        grantNotificationsButton.setVisibility(notificationPermissionAllowed ? android.view.View.GONE : android.view.View.VISIBLE);
        grantNotificationsButton.setEnabled(!notificationPermissionAllowed);
        notificationConfigContainer.setVisibility(notificationPermissionAllowed ? android.view.View.VISIBLE : android.view.View.GONE);
        // 系统通知权限可用后，改为显示应用内通知开关，避免将系统授权和应用通知配置混为一谈
        enableNotificationsSwitch.setOnCheckedChangeListener(null);
        enableNotificationsSwitch.setChecked(notificationsEnabled);
        enableNotificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            configManager.setNotificationEnabled(isChecked);
            refreshPermissionStatus();
        });
    }

    private void finishWelcome() {
        if (!hasMediaPermissions()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.welcome_media_permission_required_title)
                    .setMessage(R.string.welcome_media_permission_required_message)
                    .setPositiveButton(R.string.welcome_grant, (dialog, which) -> requestMediaPermissions())
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
            return;
        }
        getSharedPreferences(PREFS_FIRST_RUN, MODE_PRIVATE)
                .edit().putBoolean(KEY_WELCOME_COMPLETED, true).apply();
        openMainActivity();
    }

    private void openMainActivity() {
        Intent mainIntent;
        String action = getIntent().getAction();
        if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            // 首次从其他应用分享媒体时，原始 Intent 的数据和临时 URI 授权需要一起传回主界面
            mainIntent = new Intent(getIntent());
            mainIntent.setClass(this, MainActivity.class);
        } else {
            mainIntent = new Intent(this, MainActivity.class);
        }
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(mainIntent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionStatus();
    }
}
