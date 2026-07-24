package com.tech.ezconvert;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.Window;
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
import androidx.lifecycle.LiveData;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.gson.Gson;
import com.tech.ezconvert.ui.BaseActivity;
import com.tech.ezconvert.ui.EzPopupMenu;
import com.tech.ezconvert.ui.PreviewActivity;
import com.tech.ezconvert.ui.SettingsMainActivity;
import com.tech.ezconvert.utils.AnimationUtils;
import com.tech.ezconvert.utils.ConfigManager;
import com.tech.ezconvert.utils.CrashHandler;
import com.tech.ezconvert.utils.FFmpegUtil;
import com.tech.ezconvert.utils.FfmpegCommandBuilder;
import com.tech.ezconvert.utils.FileUtils;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.LogManager;
import com.tech.ezconvert.utils.NotificationHelper;
import com.tech.ezconvert.utils.ParameterData;
import com.tech.ezconvert.ui.ParameterDialogFragment;
import com.tech.ezconvert.utils.PermissionManager;
import com.tech.ezconvert.utils.ReleaseNotesManager;
import com.tech.ezconvert.utils.ToastUtils;
import com.tech.ezconvert.utils.UpdateChecker;
import com.tech.ezconvert.worker.FfmpegWorker;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends BaseActivity implements FFmpegUtil.FFmpegCallback, UpdateChecker.UpdateCheckListener {

    @Override
    protected int getTitleContainerId() {
        return R.id.title_container;
    }

    @Override
    protected int getScrollContentId() {
        return R.id.scroll_content;
    }

    private static final String TAG = "MainActivity";

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int MANAGE_STORAGE_REQUEST_CODE = 102;

    private static final String WORK_TAG_QUEUE = "ezconvert_queue";
    private static final String WORK_TAG_CURRENT = "ezconvert_current";

    private TextView statusText, progressText, versionText;
    private ProgressBar progressBar;
    private Button selectFileBtn, convertBtn, compressBtn, extractAudioBtn;
    private Button cutVideoBtn, screenshotBtn, convertAudioBtn, cutAudioBtn;
    private Button cancelBtn;
    private String currentInputPath = "";
    private String currentOutputPath = "";
    private String currentOutputFile = "";
    private boolean permissionsGranted = false;
    private long lastPermissionCheck = 0;
    private static final long PERMISSION_COOLDOWN = 1000; // 1 秒内不重复检查权限
    private int currentVolume = 100;
    private volatile boolean isTaskRunning = false;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private UpdateChecker updateChecker;
    private ActivityResultLauncher<Intent> filePickerLauncher;

    // 多文件队列处理相关字段
    private final List<String> selectedFilePaths = new ArrayList<>();
    private final CopyOnWriteArrayList<String> completedOutputFiles = new CopyOnWriteArrayList<>();
    private int currentQueueIndex = 0;
    private String currentTaskType = "";
    private final Map<String, Uri> pathToUriMap = new HashMap<>();

    // WorkManager 相关
    private WorkManager workManager;
    private Gson gson;
    private UUID currentWorkId = null;
    private LiveData<WorkInfo> currentWorkLiveData = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("TestLog", "Hello World!");
        LogManager.getInstance(this);
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        FFmpegUtil.initLogging(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化 WorkManager
        workManager = WorkManager.getInstance(this);
        gson = new Gson();

        // 初始化更新检查器
        updateChecker = new UpdateChecker(this);
        updateChecker.setUpdateCheckListener(this);

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

        // 卡片入场动画
        if (savedInstanceState == null) {
            setupCardAnimations();
        }

        // 初始按钮状态
        setFunctionButtonsEnabled(false);
        updateStatus(getString(R.string.status_checking_permission));

        // 检查权限状态
        checkPermissions();

        handleShareIntent(getIntent());

        // 检查并展示更新日志 (版本更新后首次打开时触发)
        ReleaseNotesManager.showIfNeeded(this);

        // 延迟2秒后自动检查更新（等待主界面加载完成）
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // 让 UpdateChecker 内部处理判断逻辑
            updateChecker.checkForAutoUpdate();
        }, 2000);

        // 恢复可能正在运行的 Worker 状态
        restoreRunningWorker();
    }

    /**
     * 恢复正在运行的 Worker 状态
     * 当 Activity 重建时（如系统杀进程后用户重新打开），检查是否有未完成的 Worker 任务
     */
    private void restoreRunningWorker() {
        workManager.getWorkInfosByTagLiveData(WORK_TAG_CURRENT).observe(this, workInfos -> {
            if (workInfos == null || workInfos.isEmpty()) {
                // 没有正在运行的任务
                if (isTaskRunning) {
                    // UI 显示运行中但实际没有 Worker，重置状态
                    resetTaskState();
                }
                return;
            }

            // 查找正在运行或已入队的任务
            for (WorkInfo info : workInfos) {
                WorkInfo.State state = info.getState();
                if (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED) {
                    // 有任务在运行，恢复监听
                    currentWorkId = info.getId();
                    observeWorkInfo(currentWorkId);
                    if (!isTaskRunning) {
                        isTaskRunning = true;
                        showCancelButton();
                        updateStatus(getString(R.string.status_restoring));
                    }
                    return;
                } else if (state == WorkInfo.State.SUCCEEDED) {
                    // 任务在后台完成了，显示完成通知
                    Data outputData = info.getOutputData();
                    String outputPath = outputData.getString(FfmpegWorker.KEY_OUTPUT_PATH);
                    if (outputPath != null) {
                        completedOutputFiles.add(outputPath);
                        String fileName = new File(outputPath).getName();
                        NotificationHelper.showCompleteNotification(this, fileName, true, "");
                    }
                    // 清理已完成的 Worker
                    workManager.pruneWork();
                } else if (state == WorkInfo.State.FAILED) {
                    Data outputData = info.getOutputData();
                    String error = outputData.getString(FfmpegWorker.KEY_ERROR_MESSAGE);
                    NotificationHelper.showCompleteNotification(this, "", false, error != null ? error : getString(R.string.error_unknown));
                    workManager.pruneWork();
                }
            }
        });
    }

    /**
     * 重置任务状态
     */
    private void resetTaskState() {
        isTaskRunning = false;
        hideCancelButton();
        progressBar.clearAnimation();
        progressBar.setProgress(0);
        progressText.setText(getString(R.string.progress_default));
        setFunctionButtonsEnabled(permissionsGranted && !selectedFilePaths.isEmpty());
    }

    @Override
    public void onUpdateCheckComplete(int comparisonResult, String latestVersion,
                                      String releaseName, String releaseNotes,
                                      boolean isPrerelease, boolean isDevelopmentVersion,
                                      String htmlUrl) {
        // 只在有新版本时弹出更新对话框
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

    private void setupActivityResultLaunchers() {
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            // 处理多选结果
                            if (data.getClipData() != null) {
                                ClipData clipData = data.getClipData();
                                selectedFilePaths.clear();
                                pathToUriMap.clear(); // 清空映射
                                for (int i = 0; i < clipData.getItemCount(); i++) {
                                    Uri uri = clipData.getItemAt(i).getUri();
                                    // 获取显示名称
                                    String displayName = FileUtils.getDisplayName(this, uri);
                                    if (displayName == null || displayName.isEmpty()) {
                                        displayName = "file_" + System.currentTimeMillis();
                                    }
                                    // 尝试解析路径（可能为null）
                                    String filePath = FileUtils.getPath(this, uri);
                                    // 用路径做key，没有则用显示名
                                    String key = (filePath != null && !filePath.isEmpty()) ? filePath : displayName;
                                    // 如果key已存在，添加后缀避免覆盖
                                    if (selectedFilePaths.contains(key)) {
                                        key = key + "_" + i;
                                    }
                                    selectedFilePaths.add(key);
                                    pathToUriMap.put(key, uri);
                                }
                                if (!selectedFilePaths.isEmpty()) {
                                    // 取第一个作为 currentInputPath（仅用于显示）
                                    currentInputPath = selectedFilePaths.get(0);
                                    String firstFileName = FileUtils.getDisplayName(this, pathToUriMap.get(currentInputPath));
                                    if (firstFileName == null) firstFileName = new File(currentInputPath).getName();
                                    updateStatus(getString(R.string.status_selected_files, selectedFilePaths.size(), firstFileName));

                                    // 生成输出路径
                                    generateOutputPath();

                                    // 更新按键状态
                                    setFunctionButtonsEnabled(permissionsGranted);

                                    // ToastUtils.show(this, "已选择: " + selectedFilePaths.size() + " 个文件");
                                    ToastUtils.showCustom(this, getString(R.string.toast_selected_files_count, selectedFilePaths.size()));
                                } else {
                                    updateStatus(getString(R.string.status_cannot_access_files));
                                    ToastUtils.showCustom(this, getString(R.string.status_cannot_access_files));
                                    currentInputPath = "";
                                    selectedFilePaths.clear();
                                    pathToUriMap.clear();
                                    setFunctionButtonsEnabled(permissionsGranted);
                                }
                            } else if (data.getData() != null) {
                                // 单选兼容
                                Uri uri = data.getData();
                                String displayName = FileUtils.getDisplayName(this, uri);
                                if (displayName == null) displayName = "file";
                                String filePath = FileUtils.getPath(this, uri);
                                String key = (filePath != null && !filePath.isEmpty()) ? filePath : displayName;
                                // 检查文件是否存在（通过Uri可读）
                                try {
                                    getContentResolver().openInputStream(uri).close();
                                    selectedFilePaths.clear();
                                    pathToUriMap.clear();
                                    selectedFilePaths.add(key);
                                    pathToUriMap.put(key, uri);
                                    currentInputPath = key;
                                    updateStatus(getString(R.string.status_selected_file, displayName));

                                    // 生成输出路径
                                    generateOutputPath();

                                    // 更新按键状态
                                    setFunctionButtonsEnabled(permissionsGranted);

                                    // ToastUtils.show(this, "已选择: " + fileName);
                                    ToastUtils.showCustom(this, getString(R.string.toast_selected_file_name, displayName));
                                } catch (Exception e) {
                                    // ToastUtils.show(this, "无法访问文件或文件不存在");
                                    updateStatus(getString(R.string.status_file_not_exist));
                                    ToastUtils.showCustom(this, getString(R.string.status_file_not_exist));
                                    currentInputPath = "";
                                    selectedFilePaths.clear();
                                    pathToUriMap.clear();
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

        cancelBtn = findViewById(R.id.cancel_btn);

        selectFileBtn = findViewById(R.id.select_file_btn);
        convertBtn = findViewById(R.id.convert_btn);
        compressBtn = findViewById(R.id.compress_btn);
        extractAudioBtn = findViewById(R.id.extract_audio_btn);
        cutVideoBtn = findViewById(R.id.cut_video_btn);
        screenshotBtn = findViewById(R.id.screenshot_btn);
        convertAudioBtn = findViewById(R.id.convert_audio_btn);
        cutAudioBtn = findViewById(R.id.cut_audio_btn);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.title_container);
        toolbar.setNavigationOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            v.animate().rotationBy(180).setDuration(300).start();

            showNavigationMenu(v);
        });

        // 获取版本号
        setVersionText();

        // 按钮点击
        setupButtonListeners();

        // 取消按钮点击监听
        setupCancelButtonListener();
    }

    // 显示导航菜单
    private void showNavigationMenu(View anchorView) {
        new EzPopupMenu(anchorView, new EzPopupMenu.OnMenuItemClickListener() {
            @Override
            public void onPreviewClick() {
                Intent previewIntent = new Intent(MainActivity.this, PreviewActivity.class);
                if (!currentInputPath.isEmpty()) {
                    previewIntent.putExtra("file_path", currentInputPath);
                    Uri uri = pathToUriMap.get(currentInputPath);
                    if (uri != null) {
                        previewIntent.putExtra("file_uri", uri.toString());
                    }
                }
                ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                        MainActivity.this,
                        R.anim.slide_in_right,
                        R.anim.slide_out_left
                );
                ActivityCompat.startActivity(MainActivity.this, previewIntent, options.toBundle());
            }

            @Override
            public void onSettingsClick() {
                // 延迟 100ms 后跳转
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
                }, 100, TimeUnit.MILLISECONDS);
            }
        },
        // 关闭回调 - 取消动画并复位旋转角度
        () -> {
            anchorView.animate().cancel();
            anchorView.setRotation(0);
        }).show(anchorView);
    }

    // 取消按钮监听器
    private void setupCancelButtonListener() {
        cancelBtn.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);

            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.round_warning)
                    .setTitle(getString(R.string.dialog_title_cancel))
                    .setMessage(getString(R.string.dialog_message_cancel))
                    .setPositiveButton(getString(R.string.btn_confirm_cancel), (dialog, which) -> {
                        dialog.dismiss();

                        progressBar.clearAnimation();
                        progressBar.setProgress(0);
                        progressText.setText(getString(R.string.progress_default));
                        performCancelAndCleanup();
                    })
                    .setNegativeButton(getString(R.string.btn_continue_process), null)
                    .setCancelable(true)
                    .show();
        });
    }

    // 执行取消和清理操作
    private void performCancelAndCleanup() {
        // 取消当前 Worker
        if (currentWorkId != null) {
            workManager.cancelWorkById(currentWorkId);
            currentWorkId = null;
        }

        // 先取消 FFmpeg 任务（兼容旧逻辑）
        FFmpegUtil.cancelCurrentTask();
        isTaskRunning = false;
        hideCancelButton();

        // 在后台线程删除已生成的文件
        new Thread(() -> {
            // 删除当前正在输出的文件（如果存在）
            if (currentOutputFile != null && !currentOutputFile.isEmpty()) {
                deleteFileIfExists(currentOutputFile);
            }

            // 删除本次多任务中已转换出来的文件
            for (String path : completedOutputFiles) {
                deleteFileIfExists(path);
            }
            completedOutputFiles.clear();

            // 同步清空队列状态，避免主线程的 processNextFileInQueue 竞争
            synchronized (selectedFilePaths) {
                selectedFilePaths.clear();
                pathToUriMap.clear(); // 同时清空映射
            }
            currentQueueIndex = 0;
            currentInputPath = "";

            runOnUiThread(() -> {
                updateStatus(getString(R.string.status_cancelled_cleaned));

                // 重置进度显示
                progressBar.clearAnimation();
                progressBar.setProgress(0);
                progressText.setText(getString(R.string.progress_default));

                // 恢复功能按钮可用状态
                setFunctionButtonsEnabled(permissionsGranted);

                // 清空队列状态
                selectedFilePaths.clear();
                pathToUriMap.clear();
                currentQueueIndex = 0;
                currentInputPath = "";
            });
        }).start();
    }

    // 删除指定文件
    private void deleteFileIfExists(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists() && file.isFile()) {
                boolean deleted = file.delete();
                Log.d("MainActivity", "删除文件 " + filePath + ": " + (deleted ? "成功" : "失败"));
            }
        } catch (Exception e) {
            Log.e("MainActivity", "删除文件失败: " + filePath, e);
            ToastUtils.show(this, getString(R.string.toast_delete_temp_failed));
        }
    }

    // 显示取消按钮
    private void showCancelButton() {
        cancelBtn.setVisibility(View.VISIBLE);
        // 禁用功能按钮，防止同时执行多个任务
        setFunctionButtonsEnabled(false);
        isTaskRunning = true;
    }

    // 隐藏取消按钮
    private void hideCancelButton() {
        cancelBtn.setVisibility(View.GONE);
        isTaskRunning = false;
        // 恢复功能按钮状态（如果有文件选中）
        setFunctionButtonsEnabled(permissionsGranted && !selectedFilePaths.isEmpty());
    }

    private void setVersionText() {
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            String ffmpegKitVersion = FFmpegUtil.getFFmpegKitVersion();
            String ffmpegVersion = FFmpegUtil.getFFmpegVersion();
            versionText.setText(getString(R.string.version_format, versionName, ffmpegVersion, ffmpegKitVersion));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "版本号获取失败", e);
            versionText.setText(getString(R.string.version_fallback));
        }
    }

    private void setupCardAnimations() {
        View[] cards = {
                findViewById(R.id.status_card),
                findViewById(R.id.file_selection_card),
                findViewById(R.id.video_processing_card),
                findViewById(R.id.audio_processing_card)
        };

        for (int i = 0; i < cards.length; i++) {
            if (cards[i] != null) {
                AnimationUtils.animateCardEntrance(cards[i], i * 100);
            }
        }
    }

    private void setupButtonListeners() {

        // 文件选择按钮
        selectFileBtn.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            if (permissionsGranted) {
                openFilePicker();
            } else {
                ToastUtils.show(this, getString(R.string.toast_need_permission_select));
                PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
            }
        });

        // 功能按钮统一添加动画
        View.OnClickListener functionButtonListener = v -> {
            AnimationUtils.animateButtonClick(v);
            if (permissionsGranted && !selectedFilePaths.isEmpty()) {
                handleFunctionButtonClick(v.getId());
            } else if (!permissionsGranted) {
                ToastUtils.show(this, getString(R.string.toast_grant_permission_first));
                PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
            } else {
                ToastUtils.show(this, getString(R.string.toast_select_file_first));
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
        if (selectedFilePaths.isEmpty()) {
            ToastUtils.show(this, getString(R.string.toast_select_file_first));
            return;
        }

        String taskType = getTaskTypeFromId(viewId);
        if (taskType == null) return;

        // 开始队列处理，使用弹窗
        showParameterDialogForQueue(taskType);
    }

    private String getTaskTypeFromId(int viewId) {
        if (viewId == R.id.convert_btn) return "convert";
        if (viewId == R.id.compress_btn) return "compress";
        if (viewId == R.id.extract_audio_btn) return "extract_audio";
        if (viewId == R.id.cut_video_btn) return "cut_video";
        if (viewId == R.id.screenshot_btn) return "screenshot";
        if (viewId == R.id.convert_audio_btn) return "convert_audio";
        if (viewId == R.id.cut_audio_btn) return "cut_audio";
        return null;
    }

    private void showParameterDialogForQueue(String taskType) {
        currentTaskType = taskType;
        currentQueueIndex = 0;
        completedOutputFiles.clear();
        showDialogForCurrentFile();
    }

    private void showDialogForCurrentFile() {
        if (currentQueueIndex >= selectedFilePaths.size()) {
            hideCancelButton();
            updateStatus(getString(R.string.status_all_complete, selectedFilePaths.size()));
            ToastUtils.showLong(this, getString(R.string.toast_all_complete_with_path));
            selectedFilePaths.clear();
            pathToUriMap.clear();
            currentInputPath = "";
            setFunctionButtonsEnabled(permissionsGranted);
            return;
        }

        currentInputPath = selectedFilePaths.get(currentQueueIndex);
        // 获取显示文件名
        Uri uri = pathToUriMap.get(currentInputPath);
        String fileName = (uri != null) ? FileUtils.getDisplayName(this, uri) : new File(currentInputPath).getName();
        if (fileName == null) fileName = "file";

        // 更新状态提示
        updateStatus(getString(R.string.status_set_params, currentQueueIndex + 1, selectedFilePaths.size()));

        ParameterDialogFragment dialog = ParameterDialogFragment.newInstance(
                currentTaskType, currentInputPath, currentQueueIndex + 1, selectedFilePaths.size()
        );
        dialog.setListener((params, syncAll) -> {
            // 点击"开始处理"后才锁定UI
            showCancelButton();
            if (syncAll) {
                processAllFilesWithParams(params);
            } else {
                processSingleFileWithParams(params);
            }
        });
        dialog.show(getSupportFragmentManager(), "param_dialog");
    }

    // WorkManager 任务提交
    private void processSingleFileWithParams(ParameterData params) {
        submitWorkerForCurrentFile(params);
    }

    private void processAllFilesWithParams(ParameterData params) {
        // 同步模式：所有文件使用同一套参数，逐个处理
        processNextFileWithSameParams(params);
    }

    private void processNextFileWithSameParams(ParameterData params) {
        if (currentQueueIndex >= selectedFilePaths.size()) {
            hideCancelButton();
            updateStatus(getString(R.string.toast_all_complete));
            ToastUtils.showLong(this, getString(R.string.toast_all_complete));
            selectedFilePaths.clear();
            pathToUriMap.clear();
            currentInputPath = "";
            setFunctionButtonsEnabled(permissionsGranted);
            return;
        }
        currentInputPath = selectedFilePaths.get(currentQueueIndex);
        submitWorkerForCurrentFile(params);
    }

    /**
     * 提交 Worker 处理当前文件
     */
    private void submitWorkerForCurrentFile(ParameterData params) {
        generateOutputPath();
        String outputPath = FfmpegCommandBuilder.buildOutputPath(currentOutputPath, params);
        currentOutputFile = outputPath;

        String inputKey = selectedFilePaths.get(currentQueueIndex);
        Uri fileUri = pathToUriMap.get(inputKey);
        String uriString = (fileUri != null) ? fileUri.toString() : "";
        String fileName = (fileUri != null) ? FileUtils.getDisplayName(this, fileUri) : new File(inputKey).getName();
        if (fileName == null) fileName = "file";

        // 构建 Worker 输入数据
        Data inputData = new Data.Builder()
                .putString(FfmpegWorker.KEY_INPUT_PATH, inputKey)
                .putString(FfmpegWorker.KEY_INPUT_URI, uriString)
                .putString(FfmpegWorker.KEY_OUTPUT_PATH_BASE, currentOutputPath)
                .putString(FfmpegWorker.KEY_PARAMS_JSON, gson.toJson(params))
                .putString(FfmpegWorker.KEY_FILE_NAME, fileName)
                .putInt(FfmpegWorker.KEY_TASK_INDEX, currentQueueIndex + 1)
                .putInt(FfmpegWorker.KEY_TOTAL_TASKS, selectedFilePaths.size())
                .build();

        // 创建 OneTimeWorkRequest
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(FfmpegWorker.class)
                .setInputData(inputData)
                .addTag(WORK_TAG_QUEUE)
                .addTag(WORK_TAG_CURRENT)
                .build();

        currentWorkId = workRequest.getId();

        // 观察 Worker 状态
        observeWorkInfo(currentWorkId);

        // 提交任务
        workManager.enqueue(workRequest);

        updateStatus(getString(R.string.status_processing, fileName, currentQueueIndex + 1, selectedFilePaths.size()));
    }

    /**
     * 观察 Worker 的状态和进度
     */
    private void observeWorkInfo(UUID workId) {
        // 先移除旧的观察
        if (currentWorkLiveData != null) {
            currentWorkLiveData.removeObservers(this);
        }

        currentWorkLiveData = workManager.getWorkInfoByIdLiveData(workId);
        currentWorkLiveData.observe(this, workInfo -> {
            if (workInfo == null) return;

            WorkInfo.State state = workInfo.getState();

            if (state == WorkInfo.State.RUNNING) {
                // 更新进度
                Data progress = workInfo.getProgress();
                int progressValue = progress.getInt(FfmpegWorker.KEY_PROGRESS, 0);
                long time = progress.getLong(FfmpegWorker.KEY_TIME, 0);
                updateProgressUI(progressValue, time);
            } else if (state == WorkInfo.State.SUCCEEDED) {
                // 任务成功
                Data outputData = workInfo.getOutputData();
                String outputPath = outputData.getString(FfmpegWorker.KEY_OUTPUT_PATH);
                if (outputPath != null) {
                    completedOutputFiles.add(outputPath);
                    String fileName = new File(outputPath).getName();
                    NotificationHelper.showCompleteNotification(this, fileName, true, "");
                }
                onWorkerComplete(true, getString(R.string.status_processing_complete));
            } else if (state == WorkInfo.State.FAILED) {
                // 任务失败
                Data outputData = workInfo.getOutputData();
                String errorMessage = outputData.getString(FfmpegWorker.KEY_ERROR_MESSAGE);
                if (errorMessage == null) errorMessage = getString(R.string.error_unknown);

                // 检查是否是取消操作
                if (getString(R.string.error_cancelled).equals(errorMessage)) {
                    onWorkerComplete(false, getString(R.string.error_cancelled));
                } else {
                    NotificationHelper.showCompleteNotification(this, "", false, errorMessage);
                    onWorkerComplete(false, errorMessage);
                }
            } else if (state == WorkInfo.State.CANCELLED) {
                onWorkerComplete(false, getString(R.string.error_cancelled));
            }
        });
    }

    /**
     * Worker 完成回调
     */
    private void onWorkerComplete(boolean success, String message) {
        if (success) {
            currentQueueIndex++;
            if (currentQueueIndex < selectedFilePaths.size()) {
                // 继续处理下一个文件
                progressBar.clearAnimation();
                progressBar.setProgress(0);
                progressText.setText(getString(R.string.progress_default));

                // 弹出下一个文件的参数对话框（逐个模式）
                showDialogForCurrentFile();
            } else {
                // 全部完成
                hideCancelButton();
                updateStatus(getString(R.string.status_all_complete, selectedFilePaths.size()));
                ToastUtils.showLong(this, getString(R.string.toast_all_complete));
                currentOutputFile = "";
                completedOutputFiles.clear();
                selectedFilePaths.clear();
                pathToUriMap.clear();
                currentInputPath = "";
                currentWorkId = null;
                setFunctionButtonsEnabled(permissionsGranted);
            }
        } else {
            // 失败或取消
            if (getString(R.string.error_cancelled).equals(message)) {
                updateStatus(getString(R.string.error_cancelled));
                ToastUtils.show(this, getString(R.string.toast_cancelled));
                progressBar.clearAnimation();
                progressBar.setProgress(0);
                progressText.setText(getString(R.string.progress_default));
                currentOutputFile = "";
                selectedFilePaths.clear();
                pathToUriMap.clear();
                completedOutputFiles.clear();
                currentQueueIndex = 0;
                currentWorkId = null;
                setFunctionButtonsEnabled(permissionsGranted);
            } else {
                hideCancelButton();
                updateStatus(getString(R.string.status_failed, message));
                ToastUtils.show(this, getString(R.string.status_failed, message));
                progressBar.clearAnimation();
                progressBar.setProgress(0);
                progressText.setText(getString(R.string.progress_default));
                currentOutputFile = "";
                currentWorkId = null;
            }
        }
    }

    /**
     * 更新进度 UI
     */
    private void updateProgressUI(int progress, long time) {
        AnimationUtils.animateProgressSmoothly(progressBar, progress);
        progressText.setText(getString(R.string.progress_text, progress));
        AnimationUtils.animateStatusUpdate(progressText);

        if (isTaskRunning && cancelBtn.getVisibility() != View.VISIBLE) {
            cancelBtn.setVisibility(View.VISIBLE);
        }
    }

    // 权限授予回调
    public void onPermissionsGranted() {
        permissionsGranted = true;
        setFunctionButtonsEnabled(true);

        // 成功动画反馈
        AnimationUtils.animateBounce(selectFileBtn);

        // 只有在没有选择文件时才显示默认状态
        if (selectedFilePaths.isEmpty()) {
            updateStatus(getString(R.string.status_permission_granted));
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
        updateStatus(getString(R.string.status_need_permission));
        Log.d("MainActivity", "权限被拒绝");

        // 禁用选择文件按键
        selectFileBtn.setEnabled(false);
        selectFileBtn.setAlpha(0.5f);

        ToastUtils.showLong(this, getString(R.string.toast_need_permission_full));
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
        // 如果有任务正在运行，强制禁用所有功能按钮
        if (isTaskRunning) {
            enabled = false;
        }

        boolean hasFileSelected = !selectedFilePaths.isEmpty();

        convertBtn.setEnabled(enabled && hasFileSelected);
        compressBtn.setEnabled(enabled && hasFileSelected);
        extractAudioBtn.setEnabled(enabled && hasFileSelected);
        cutVideoBtn.setEnabled(enabled && hasFileSelected);
        screenshotBtn.setEnabled(enabled && hasFileSelected);
        convertAudioBtn.setEnabled(enabled && hasFileSelected);
        cutAudioBtn.setEnabled(enabled && hasFileSelected);

        float alpha = (enabled && hasFileSelected) ? 1.0f : 0.5f;
        convertBtn.setAlpha(alpha);
        compressBtn.setAlpha(alpha);
        extractAudioBtn.setAlpha(alpha);
        cutVideoBtn.setAlpha(alpha);
        screenshotBtn.setAlpha(alpha);
        convertAudioBtn.setAlpha(alpha);
        cutAudioBtn.setAlpha(alpha);
    }

    private void openFilePicker() {
        if (!permissionsGranted) {
            ToastUtils.show(this, getString(R.string.toast_grant_storage_permission));
            PermissionManager.requestInitialPermissions(this, PERMISSION_REQUEST_CODE);
            return;
        }

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); // 支持多选

        String[] mimeTypes = {"video/*", "audio/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        try {
            filePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.picker_title)));
        } catch (Exception e) {
            ToastUtils.show(this, getString(R.string.toast_cannot_open_picker));
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

        // 尝试从映射获取Uri来获取更友好的文件名
        Uri uri = pathToUriMap.get(currentInputPath);
        String baseName;
        if (uri != null) {
            String name = FileUtils.getDisplayName(this, uri);
            if (name != null && !name.isEmpty()) {
                if (name.contains(".")) {
                    baseName = name.substring(0, name.lastIndexOf('.'));
                } else {
                    baseName = name;
                }
            } else {
                baseName = "file";
            }
        } else {
            File inputFile = new File(currentInputPath);
            String fileName = inputFile.getName();
            baseName = fileName.contains(".") ?
                    fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        }

        // 创建简转文件夹
        String outputDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + File.separator + getString(R.string.folder_output);

        // 检查目录
        File outputDirFile = new File(outputDir);
        if (!outputDirFile.exists()) {
            outputDirFile.mkdirs();
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        currentOutputPath = outputDir + File.separator + baseName + getString(R.string.filename_suffix_converted) + timestamp;

        Log.d("GeneratePath", "输出路径基础: " + currentOutputPath);
    }

    // FFmpegCallback 实现
    @Override
    public void onProgress(int progress, long time) {
        runOnUiThread(() -> {
            AnimationUtils.animateProgressSmoothly(progressBar, progress);
            progressText.setText(getString(R.string.progress_text, progress));

            // 为进度文本添加微动画
            AnimationUtils.animateStatusUpdate(progressText);

            // 确保取消按钮可见（处理从对话框启动的任务）
            if (isTaskRunning && cancelBtn.getVisibility() != View.VISIBLE) {
                cancelBtn.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onComplete(boolean success, String message) {
        runOnUiThread(() -> {
            // 检查是否是取消操作（通过特定消息标识）
            if (message != null && message.equals(getString(R.string.error_cancelled))) {
                updateStatus(getString(R.string.error_cancelled));
                ToastUtils.show(this, getString(R.string.toast_cancelled));
                progressBar.clearAnimation();
                progressBar.setProgress(0);
                progressText.setText(getString(R.string.progress_default));
                currentOutputFile = "";
                // 清空队列状态
                selectedFilePaths.clear();
                pathToUriMap.clear();
                completedOutputFiles.clear();
                currentQueueIndex = 0;
                return;
            }

            if (success) {
                completedOutputFiles.add(currentOutputFile);
                currentQueueIndex++;

                if (currentQueueIndex < selectedFilePaths.size()) {
                    // 还有下一个文件，重置进度并继续处理
                    progressBar.clearAnimation();
                    progressBar.setProgress(0);
                    progressText.setText(getString(R.string.progress_default));
                } else {
                    // 全部完成
                    hideCancelButton();
                    updateStatus(getString(R.string.status_all_complete, selectedFilePaths.size()));
                    ToastUtils.showLong(MainActivity.this, getString(R.string.toast_processing_complete));
                    currentOutputFile = "";
                    completedOutputFiles.clear();
                    selectedFilePaths.clear();
                    pathToUriMap.clear();
                    currentInputPath = "";
                    setFunctionButtonsEnabled(permissionsGranted);
                }
            } else {
                updateStatus(getString(R.string.status_failed, message));
                ToastUtils.show(MainActivity.this, getString(R.string.status_failed, message));
                // 失败时停止队列处理，保留已完成的文件
                hideCancelButton();
                progressBar.clearAnimation();
                progressBar.setProgress(0);
                progressText.setText(getString(R.string.progress_default));
                currentOutputFile = "";
            }
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            // 隐藏取消按钮并重置任务状态
            hideCancelButton();

            updateStatus(getString(R.string.status_error, error));
            ToastUtils.showLong(MainActivity.this, getString(R.string.status_error, error));

            // 清除动画并重置进度
            progressBar.clearAnimation();
            progressBar.setProgress(0);
            progressText.setText(getString(R.string.progress_default));

            // 错误时停止队列处理
            currentOutputFile = "";
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
        checkPermissions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        FFmpegUtil.cancelCurrentTask();

        // 注意：不取消 Worker，让后台任务继续运行
        // 只清理 Activity 级别的资源

        // 清理缓存文件
        cleanupCacheFiles();

        // 关闭 Executor
        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
        }

        // 清理更新检查器
        if (updateChecker != null) {
            updateChecker.cleanup();
        }

        // 移除 LiveData 观察
        if (currentWorkLiveData != null) {
            currentWorkLiveData.removeObservers(this);
        }
    }

    private void checkPermissions() {
        long now = System.currentTimeMillis();
        if (now - lastPermissionCheck < PERMISSION_COOLDOWN) {
            Log.d(TAG, "权限检查冷却中，跳过");
            return;
        }
        lastPermissionCheck = now;
        PermissionManager.checkPermissionStatus(this);
    }

    private void cleanupCacheFiles() {
        try {
            File cacheDir = new File(getCacheDir(), "shared_files");
            if (cacheDir.exists()) {
                File[] files = cacheDir.listFiles();
                if (files != null) {
                    long now = System.currentTimeMillis();
                    int deletedCount = 0;
                    long freedBytes = 0;

                    for (File file : files) {
                        // 删除超过24小时的缓存
                        if (now - file.lastModified() > 24 * 60 * 60 * 1000) {
                            long fileSize = file.length();
                            if (file.delete()) {
                                deletedCount++;
                                freedBytes += fileSize;
                            }
                        }
                    }

                    Log.d("MainActivity", "清理缓存成功: 删除 " + deletedCount + " 个文件，释放 " +
                            formatFileSize(freedBytes) + " (" + freedBytes + " bytes)");
                } else {
                    Log.d("MainActivity", "缓存目录为空，无需清理");
                }
            } else {
                Log.d("MainActivity", "缓存目录不存在，无需清理");
            }
        } catch (Exception e) {
            Log.e("MainActivity", "清理缓存失败", e);
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024));
    }

    private void handleShareIntent(Intent intent) {
        String action = intent.getAction();
        String type = intent.getType();

        // 类型校验: 只处理视频/音频
        if (type == null || (!type.startsWith("video/") && !type.startsWith("audio/"))) {
            return;
        }

        // 多文件分享
        if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> uriList;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                uriList = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                // 对于旧版本，使用已过时的方法
                uriList = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            }

            if (uriList == null || uriList.isEmpty()) {
                ToastUtils.showCustom(this, getString(R.string.toast_no_share_file));
                return;
            }

            selectedFilePaths.clear();
            pathToUriMap.clear();
            int successCount = 0;

            for (Uri uri : uriList) {
                if (uri == null) continue;

                // 获取显示名称（可能为 null）
                String displayName = FileUtils.getDisplayName(this, uri);
                if (displayName == null || displayName.isEmpty()) {
                    displayName = "share_" + System.currentTimeMillis();
                }

                // 生成唯一 key（避免重复）
                String key = displayName;
                if (selectedFilePaths.contains(key)) {
                    key = key + "_" + System.currentTimeMillis();
                }

                // 直接加入列表，不检查路径是否存在，由 CacheManager 处理实际访问
                selectedFilePaths.add(key);
                pathToUriMap.put(key, uri);
                successCount++;
            }

            if (selectedFilePaths.isEmpty()) {
                ToastUtils.showCustom(this, getString(R.string.toast_cannot_access_share));
                return;
            }

            // 使用第一个文件生成输出路径基础
            currentInputPath = selectedFilePaths.get(0);
            currentOutputFile = generateShareOutputPath(currentInputPath);

            String firstFileName = FileUtils.getDisplayName(this, pathToUriMap.get(currentInputPath));
            if (firstFileName == null) firstFileName = "file";
            updateStatus(getString(R.string.status_received_files, successCount, uriList.size(), firstFileName));
            setFunctionButtonsEnabled(permissionsGranted);

            if (successCount < uriList.size()) {
                ToastUtils.showCustom(this, getString(R.string.toast_partial_loaded, successCount, uriList.size() - successCount));
            } else {
                ToastUtils.showCustom(this, getString(R.string.toast_received_files_count, successCount));
            }
        }
        // 单文件分享
        else if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                // 对于旧版本，使用已过时的方法但添加抑制警告注解
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }

            if (uri != null) {
                String displayName = FileUtils.getDisplayName(this, uri);
                if (displayName == null) displayName = "share";

                // 直接使用 Uri，不检查路径
                String key = displayName;
                selectedFilePaths.clear();
                pathToUriMap.clear();
                selectedFilePaths.add(key);
                pathToUriMap.put(key, uri);
                currentInputPath = key;
                currentOutputPath = generateShareOutputPath(key);
                updateStatus(getString(R.string.status_received_share, displayName));
                setFunctionButtonsEnabled(permissionsGranted);
                ToastUtils.showCustom(this, getString(R.string.toast_received_share_file));
            } else {
                ToastUtils.showCustom(this, getString(R.string.toast_cannot_parse_share));
            }
        }
    }

    private String generateShareOutputPath(String inputKey) {
        // 从映射中获取Uri获取文件名
        Uri uri = pathToUriMap.get(inputKey);
        String base;
        if (uri != null) {
            String name = FileUtils.getDisplayName(this, uri);
            if (name != null && !name.isEmpty()) {
                if (name.contains(".")) {
                    base = name.substring(0, name.lastIndexOf('.'));
                } else {
                    base = name;
                }
            } else {
                base = "shared";
            }
        } else {
            // 直接使用 key
            base = inputKey;
            if (base.contains(".")) {
                base = base.substring(0, base.lastIndexOf('.'));
            }
        }
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .getAbsolutePath() + File.separator + getString(R.string.folder_output);
        new File(dir).mkdir();
        return dir + File.separator + base + getString(R.string.filename_suffix_share) + ts;
    }

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
                    String path = config.getConfigPath();
                    ToastUtils.showLong(this, getString(R.string.toast_config_path, path));
                })
                .show();
    }
}
