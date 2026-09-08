package com.tech.ezconvert;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.tech.ezconvert.manager.ConversionManager;
import com.tech.ezconvert.manager.ConversionQueueManager;
import com.tech.ezconvert.manager.MediaSelectionManager;
import com.tech.ezconvert.manager.OutputPathManager;
import com.tech.ezconvert.manager.SharedFileCacheCleaner;
import com.tech.ezconvert.ui.BaseActivity;
import com.tech.ezconvert.ui.EzPopupMenu;
import com.tech.ezconvert.ui.ParameterDialogFragment;
import com.tech.ezconvert.ui.PreviewActivity;
import com.tech.ezconvert.ui.SettingsMainActivity;
import com.tech.ezconvert.utils.AnimationUtils;
import com.tech.ezconvert.utils.ConfigManager;
import com.tech.ezconvert.utils.FFmpegUtil;
import com.tech.ezconvert.utils.FileUtils;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.LogManager;
import com.tech.ezconvert.utils.ParameterData;
import com.tech.ezconvert.utils.PermissionManager;
import com.tech.ezconvert.utils.ReleaseNotesManager;
import com.tech.ezconvert.utils.ToastUtils;
import com.tech.ezconvert.utils.UpdateChecker;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Activity 只负责 UI、生命周期和用户交互；文件选择、队列状态、输出路径以及
 * WorkManager 任务生命周期分别由 manager 包中的类负责
 */
public class MainActivity extends BaseActivity implements
        UpdateChecker.UpdateCheckListener,
        MediaSelectionManager.Listener,
        ConversionManager.Listener {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 102;
    private static final long PERMISSION_COOLDOWN = 1000;

    private TextView statusText;
    private TextView progressText;
    private TextView versionText;
    private ProgressBar progressBar;
    private Button selectFileBtn;
    private Button convertBtn;
    private Button compressBtn;
    private Button extractAudioBtn;
    private Button cutVideoBtn;
    private Button screenshotBtn;
    private Button convertAudioBtn;
    private Button cutAudioBtn;
    private Button convertImageBtn;
    private Button cancelBtn;

    private boolean permissionsGranted = false;
    private boolean isTaskRunning = false;
    private long lastPermissionCheck = 0;

    private String currentInputPath = "";
    private String currentOutputPath = "";
    private String currentOutputFile = "";

    private ActivityResultLauncher<Intent> filePickerLauncher;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private UpdateChecker updateChecker;
    private ConversionQueueManager queueManager;
    private MediaSelectionManager mediaSelectionManager;
    private OutputPathManager outputPathManager;
    private ConversionManager conversionManager;

    @Override
    protected int getTitleContainerId() {
        return R.id.title_container;
    }

    @Override
    protected int getScrollContentId() {
        return R.id.scroll_content;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("TestLog", "Hello World!");
        LogManager.getInstance(this);
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        FFmpegUtil.initLogging(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        queueManager = new ConversionQueueManager(this);
        outputPathManager = new OutputPathManager(this);
        conversionManager = new ConversionManager(this, queueManager, this);
        mediaSelectionManager = new MediaSelectionManager(this, queueManager, this);

        updateChecker = new UpdateChecker(this);
        updateChecker.setUpdateCheckListener(this);

        ConfigManager.getInstance(this);
        checkLegacySettings();

        setupActivityResultLaunchers();
        initializeViews();
        if (savedInstanceState == null) setupCardAnimations();

        setFunctionButtonsEnabled(false);
        updateStatus(getString(R.string.status_checking_permission));
        checkPermissions();
        mediaSelectionManager.handleShareIntent(getIntent());

        ReleaseNotesManager.showIfNeeded(this);
        new Handler(Looper.getMainLooper()).postDelayed(() -> updateChecker.checkForAutoUpdate(), 2000);
        conversionManager.restoreRunningWorker(this);
    }

    // 检查旧版本配置，并在需要时提示用户迁移设置
    private void checkLegacySettings() {
        SharedPreferences prefs = getSharedPreferences("EzConvertSettings", MODE_PRIVATE);
        if (prefs.getAll().size() > 0) showMigrationDialog();
    }

    // ActivityResult 只负责接收系统返回结果，具体文件解析交给 MediaSelectionManager
    private void setupActivityResultLaunchers() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        mediaSelectionManager.handlePickerResult(result.getData());
                    }
                });
    }

    // 初始化页面控件以及所有与 UI 直接相关的监听器
    private void initializeViews() {
        statusText = findViewById(R.id.status_text);
        progressText = findViewById(R.id.progress_text);
        progressBar = findViewById(R.id.progress_bar);
        versionText = findViewById(R.id.version_text);
        cancelBtn = findViewById(R.id.cancel_btn);
        selectFileBtn = findViewById(R.id.select_file_btn);
        convertBtn = findViewById(R.id.convert_btn);
        compressBtn = findViewById(R.id.compress_btn);
        extractAudioBtn = findViewById(R.id.extract_audio_btn);
        cutVideoBtn = findViewById(R.id.cut_video_btn);
        screenshotBtn = findViewById(R.id.screenshot_btn);
        convertAudioBtn = findViewById(R.id.convert_audio_btn);
        cutAudioBtn = findViewById(R.id.cut_audio_btn);
        convertImageBtn = findViewById(R.id.convert_image_btn);

        MaterialToolbar toolbar = findViewById(R.id.title_container);
        toolbar.setNavigationOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            v.animate().rotationBy(180).setDuration(300).start();
            showNavigationMenu(v);
        });

        setVersionText();
        setupButtonListeners();
        setupCancelButtonListener();
    }

    // 构建左上角导航菜单
    private void showNavigationMenu(View anchorView) {
        new EzPopupMenu(anchorView, new EzPopupMenu.OnMenuItemClickListener() {
            @Override
            public void onPreviewClick() {
                Intent intent = new Intent(MainActivity.this, PreviewActivity.class);
                if (!currentInputPath.isEmpty()) {
                    intent.putExtra("file_path", currentInputPath);
                    Uri uri = queueManager.getCurrentUri();
                    if (uri != null) intent.putExtra("file_uri", uri.toString());
                }
                ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                        MainActivity.this, R.anim.slide_in_right, R.anim.slide_out_left);
                ActivityCompat.startActivity(MainActivity.this, intent, options.toBundle());
            }

            @Override
            public void onSettingsClick() {
                scheduler.schedule(() -> runOnUiThread(() -> {
                    Intent intent = new Intent(MainActivity.this, SettingsMainActivity.class);
                    ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                            MainActivity.this, R.anim.slide_in_right, R.anim.slide_out_left);
                    ActivityCompat.startActivity(MainActivity.this, intent, options.toBundle());
                }), 100, TimeUnit.MILLISECONDS);
            }
        }, () -> {
            anchorView.animate().cancel();
            anchorView.setRotation(0);
        }).show(anchorView);
    }

    // 转换功能按钮统一使用一个监听器，根据按钮 ID 判断任务类型
    private void setupButtonListeners() {
        selectFileBtn.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            if (permissionsGranted) {
                mediaSelectionManager.openFilePicker(filePickerLauncher);
            } else {
                ToastUtils.show(this, getString(R.string.toast_need_permission_select));
                PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
            }
        });

        View.OnClickListener functionListener = v -> {
            AnimationUtils.animateButtonClick(v);
            if (permissionsGranted && !queueManager.isEmpty()) {
                startQueue(getTaskTypeFromId(v.getId()));
            } else if (!permissionsGranted) {
                ToastUtils.show(this, getString(R.string.toast_grant_permission_first));
                PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
            } else {
                ToastUtils.show(this, getString(R.string.toast_select_file_first));
            }
        };

        convertBtn.setOnClickListener(functionListener);
        compressBtn.setOnClickListener(functionListener);
        extractAudioBtn.setOnClickListener(functionListener);
        cutVideoBtn.setOnClickListener(functionListener);
        screenshotBtn.setOnClickListener(functionListener);
        convertAudioBtn.setOnClickListener(functionListener);
        cutAudioBtn.setOnClickListener(functionListener);
        convertImageBtn.setOnClickListener(functionListener);
    }

    // 开始处理当前队列，具体 Worker 的创建与监听由 ConversionManager 负责
    private void startQueue(String taskType) {
        if (taskType == null || queueManager.isEmpty()) return;
        queueManager.setTaskType(taskType);
        queueManager.setCurrentIndex(0);
        queueManager.clearCompletedOutputs();
        showParameterDialogForCurrentFile();
    }

    private String getTaskTypeFromId(int viewId) {
        if (viewId == R.id.convert_btn) return "convert";
        if (viewId == R.id.compress_btn) return "compress";
        if (viewId == R.id.extract_audio_btn) return "extract_audio";
        if (viewId == R.id.cut_video_btn) return "cut_video";
        if (viewId == R.id.screenshot_btn) return "screenshot";
        if (viewId == R.id.convert_audio_btn) return "convert_audio";
        if (viewId == R.id.cut_audio_btn) return "cut_audio";
        if (viewId == R.id.convert_image_btn) return "convert_image";
        return null;
    }

    // 为当前队列文件显示参数设置对话框
    private void showParameterDialogForCurrentFile() {
        if (queueManager.getCurrentIndex() >= queueManager.size()) {
            finishQueue();
            return;
        }

        currentInputPath = queueManager.getCurrentKey();
        Uri uri = queueManager.getCurrentUri();
        String fileName = uri != null ? FileUtils.getDisplayName(this, uri) : new File(currentInputPath).getName();
        if (fileName == null) fileName = "file";

        updateStatus(getString(R.string.status_set_params,
                queueManager.getCurrentPosition(), queueManager.size()));

        ParameterDialogFragment dialog = ParameterDialogFragment.newInstance(
                queueManager.getTaskType(), currentInputPath, uri,
                queueManager.getCurrentPosition(), queueManager.size());
        dialog.setListener((params, syncAll) -> {
            showCancelButton();
            queueManager.setSyncMode(syncAll, params);
            submitCurrentWorker(params);
        });
        dialog.show(getSupportFragmentManager(), "param_dialog");
    }

    // 参数确认后提交当前文件，输出路径的生成和 Worker 的生命周期分别由独立 Manager 处理
    private void submitCurrentWorker(ParameterData params) {
        Uri uri = queueManager.getCurrentUri();
        currentOutputPath = outputPathManager.generateBasePath(
                queueManager.getCurrentKey(), uri, params.taskType);
        currentOutputFile = outputPathManager.buildOutputPath(currentOutputPath, params);
        conversionManager.submitCurrent(params, currentOutputPath, this);

        String fileName = uri != null ? FileUtils.getDisplayName(this, uri) : new File(queueManager.getCurrentKey()).getName();
        if (fileName == null) fileName = "file";
        updateStatus(getString(R.string.status_processing, fileName,
                queueManager.getCurrentPosition(), queueManager.size()));
    }

    // 取消按钮只负责通知 ConversionManager，并立即更新当前页面状态
    private void setupCancelButtonListener() {
        cancelBtn.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.round_warning)
                    .setTitle(getString(R.string.dialog_title_cancel))
                    .setMessage(getString(R.string.dialog_message_cancel))
                    .setPositiveButton(getString(R.string.btn_confirm_cancel), (dialog, which) -> {
                        dialog.dismiss();
                        resetProgressUI();
                        cancelAndCleanup();
                    })
                    .setNegativeButton(getString(R.string.btn_continue_process), null)
                    .setCancelable(true)
                    .show();
        });
    }

    private void cancelAndCleanup() {
        conversionManager.cancelCurrent();
        isTaskRunning = false;
        hideCancelButton();

        new Thread(() -> {
            deleteFileIfExists(currentOutputFile);
            for (String path : queueManager.getCompletedOutputFiles()) deleteFileIfExists(path);
            queueManager.clearCompletedOutputs();
            queueManager.clearSelection();
            currentInputPath = "";
            currentOutputFile = "";

            runOnUiThread(() -> {
                queueManager.resetProcessingState();
                updateStatus(getString(R.string.status_cancelled_cleaned));
                resetProgressUI();
                setFunctionButtonsEnabled(permissionsGranted);
            });
        }).start();
    }

    private void deleteFileIfExists(String path) {
        if (path == null || path.isEmpty()) return;
        try {
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                boolean deleted = file.delete();
                Log.d(TAG, "删除文件 " + path + ": " + (deleted ? "成功" : "失败"));
            }
        } catch (Exception e) {
            Log.e(TAG, "删除文件失败: " + path, e);
        }
    }

    private void showCancelButton() {
        isTaskRunning = true;
        cancelBtn.setVisibility(View.VISIBLE);
        setFunctionButtonsEnabled(false);
    }

    private void hideCancelButton() {
        isTaskRunning = false;
        cancelBtn.setVisibility(View.GONE);
        setFunctionButtonsEnabled(permissionsGranted);
    }

    private void setVersionText() {
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            versionText.setText(getString(R.string.version_format, versionName,
                    FFmpegUtil.getFFmpegVersion(), FFmpegUtil.getFFmpegKitVersion()));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "版本号获取失败", e);
            versionText.setText(getString(R.string.version_fallback));
        }
    }

    private void setupCardAnimations() {
        View[] cards = {
                findViewById(R.id.status_card), findViewById(R.id.file_selection_card),
                findViewById(R.id.video_processing_card), findViewById(R.id.audio_processing_card)
        };
        for (int i = 0; i < cards.length; i++) {
            if (cards[i] != null) AnimationUtils.animateCardEntrance(cards[i], i * 100);
        }
    }

    private void resetProgressUI() {
        progressBar.clearAnimation();
        progressBar.setProgress(0);
        progressText.setText(getString(R.string.progress_default));
    }

    // 所有队列任务完成后的统一收尾，恢复按钮、清空队列并重置进度 UI
    private void finishQueue() {
        int total = queueManager.size();
        hideCancelButton();
        updateStatus(getString(R.string.status_all_complete, total));
        ToastUtils.showLong(this, getString(R.string.toast_all_complete));
        queueManager.resetProcessingState();
        queueManager.clearSelection();
        currentInputPath = "";
        currentOutputPath = "";
        currentOutputFile = "";
        resetProgressUI();
        setFunctionButtonsEnabled(permissionsGranted);
    }

    // Worker 失败或取消后的 UI 收尾，真正的 WorkManager 状态判断由 ConversionManager 完成
    private void handleWorkerFailure(String message) {
        boolean cancelled = getString(R.string.error_cancelled).equals(message);
        if (cancelled) {
            updateStatus(getString(R.string.error_cancelled));
            ToastUtils.show(this, getString(R.string.toast_cancelled));
            queueManager.resetProcessingState();
            queueManager.clearSelection();
        } else {
            updateStatus(getString(R.string.status_failed, message));
            ToastUtils.show(this, getString(R.string.status_failed, message));
        }
        hideCancelButton();
        resetProgressUI();
        currentOutputFile = "";
        currentOutputPath = "";
        currentInputPath = cancelled ? "" : currentInputPath;
        setFunctionButtonsEnabled(permissionsGranted);
    }

    // 将 Worker 的进度数据转换为页面上的进度条、文字和取消按钮状态
    private void updateProgressUI(int progress, long time) {
        AnimationUtils.animateProgressSmoothly(progressBar, progress);
        progressText.setText(getString(R.string.progress_text, progress));
        AnimationUtils.animateStatusUpdate(progressText);
        if (isTaskRunning && cancelBtn.getVisibility() != View.VISIBLE) cancelBtn.setVisibility(View.VISIBLE);
    }

    @Override
    public void onProgress(int progress, long time) {
        runOnUiThread(() -> updateProgressUI(progress, time));
    }

    // ConversionManager 通知当前 Worker 结束后，决定继续队列、弹参数框或结束整个任务
    @Override
    public void onCompleted(boolean success, String message, String outputPath) {
        runOnUiThread(() -> {
            if (!success) {
                handleWorkerFailure(message);
                return;
            }

            queueManager.moveToNext();
            resetProgressUI();
            currentOutputFile = "";

            if (queueManager.isEmpty() || queueManager.getCurrentIndex() >= queueManager.size()) {
                finishQueue();
            } else if (queueManager.isSyncMode() && queueManager.getSyncParams() != null) {
                showCancelButton();
                submitCurrentWorker(queueManager.getSyncParams());
            } else {
                showParameterDialogForCurrentFile();
            }
        });
    }

    @Override
    public void onRestored() {
        runOnUiThread(() -> {
            isTaskRunning = true;
            showCancelButton();
            updateStatus(getString(R.string.status_restoring));
            // WorkManager 已经保存了真实进度，随后 onProgress 会刷新进度条
        });
    }

    @Override
    public void onIdleAfterRestore() {
        // Activity 初始状态已经由权限/选择逻辑负责，这里不覆盖现有 UI
    }

    // 文件选择完成后的 UI 更新，文件列表本身已经由 MediaSelectionManager 写入队列
    @Override
    public void onFilesSelected(int count, String firstFileName, boolean fromShare) {
        currentInputPath = queueManager.getCurrentKey();
        Uri uri = queueManager.getCurrentUri();
        currentOutputPath = outputPathManager.generateBasePath(currentInputPath, uri, null);

        if (fromShare) {
            if (count == 1) {
                updateStatus(getString(R.string.status_received_share, firstFileName));
            } else {
                updateStatus(getString(R.string.status_received_files, count, firstFileName));
            }
        } else if (count == 1) {
            updateStatus(getString(R.string.status_selected_file, firstFileName));
            ToastUtils.showCustom(this, getString(R.string.toast_selected_file_name, firstFileName));
        } else {
            updateStatus(getString(R.string.status_selected_files, count, firstFileName));
            ToastUtils.showCustom(this, getString(R.string.toast_selected_files_count, count));
        }
        setFunctionButtonsEnabled(permissionsGranted);
    }

    @Override
    public void onSelectionCleared(String message) {
        currentInputPath = "";
        currentOutputPath = "";
        updateStatus(message);
        ToastUtils.showCustom(this, message);
        setFunctionButtonsEnabled(permissionsGranted);
    }

    @Override
    public void onUpdateCheckComplete(int comparisonResult, String latestVersion,
                                      String releaseName, String releaseNotes,
                                      boolean isPrerelease, boolean isDevelopmentVersion,
                                      String htmlUrl) {
        if (comparisonResult < 0) {
            updateChecker.showUpdateDialog(releaseName, releaseNotes, isPrerelease, htmlUrl);
            Log.d(TAG, "自动更新: 发现新版本 " + latestVersion + (isPrerelease ? " (预发布)" : ""));
        }
    }

    @Override
    public void onUpdateCheckError(String errorMessage) {
        Log.e(TAG, "自动更新检查出错: " + errorMessage);
    }

    @Override
    public void onNoUpdateAvailable() {
        Log.d(TAG, "自动更新: 没有可用更新");
    }

    public void onPermissionsGranted() {
        permissionsGranted = true;
        setFunctionButtonsEnabled(true);
        AnimationUtils.animateBounce(selectFileBtn);
        if (queueManager.isEmpty()) updateStatus(getString(R.string.status_permission_granted));
        selectFileBtn.setEnabled(true);
        selectFileBtn.setAlpha(1.0f);
        Log.d(TAG, "权限检测通过");
    }

    public void onPermissionsDenied() {
        permissionsGranted = false;
        setFunctionButtonsEnabled(false);
        updateStatus(getString(R.string.status_need_permission));
        selectFileBtn.setEnabled(false);
        selectFileBtn.setAlpha(0.5f);
        ToastUtils.showLong(this, getString(R.string.toast_need_permission_full));
        Log.d(TAG, "权限被拒绝");
    }

    // 统一更新状态文字，避免各处重复处理动画和主线程切换
    public void updateStatus(String message) {
        runOnUiThread(() -> {
            AnimationUtils.animateStatusUpdate(statusText);
            statusText.setText(message);
            Log.d("EzConvert", message);
        });
    }

    // 根据权限、是否有文件以及是否正在运行任务，统一控制功能按钮
    private void setFunctionButtonsEnabled(boolean enabled) {
        if (isTaskRunning) enabled = false;
        boolean hasFile = !queueManager.isEmpty();
        Button[] buttons = {convertBtn, compressBtn, extractAudioBtn, cutVideoBtn,
                screenshotBtn, convertAudioBtn, cutAudioBtn, convertImageBtn};
        float alpha = enabled && hasFile ? 1.0f : 0.5f;
        for (Button button : buttons) {
            button.setEnabled(enabled && hasFile);
            button.setAlpha(alpha);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) PermissionManager.checkPermissionStatus(this);
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
        checkPermissions();
    }

    @Override
    protected void onDestroy() {
        conversionManager.clearObservation(this);
        if (updateChecker != null) updateChecker.cleanup();
        if (!scheduler.isShutdown()) scheduler.shutdown();
        SharedFileCacheCleaner.cleanup(this);
        super.onDestroy();
    }

    // 限制权限检查频率，避免 onResume 等生命周期回调导致重复检查
    private void checkPermissions() {
        long now = System.currentTimeMillis();
        if (now - lastPermissionCheck < PERMISSION_COOLDOWN) return;
        lastPermissionCheck = now;
        PermissionManager.checkPermissionStatus(this);
    }

    // 首次打开新版时提示迁移旧版配置
    private void showMigrationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_migration))
                .setMessage(getString(R.string.dialog_message_migration))
                .setPositiveButton(getString(R.string.btn_migrate), (dialog, which) -> {
                    ConfigManager.getInstance(this).migrateOldSettings();
                    ToastUtils.show(this, getString(R.string.toast_migration_complete));
                })
                .setNegativeButton(getString(R.string.btn_skip), null)
                .setNeutralButton(getString(R.string.btn_view_config_path), (dialog, which) -> {
                    ConfigManager config = ConfigManager.getInstance(this);
                    ToastUtils.showLong(this, getString(R.string.toast_config_path, config.getConfigPath()));
                })
                .show();
    }
}
