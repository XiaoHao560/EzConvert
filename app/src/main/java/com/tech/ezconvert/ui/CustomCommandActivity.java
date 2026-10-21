package com.tech.ezconvert.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.tech.ezconvert.R;
import com.tech.ezconvert.customcommand.CustomCommandParser;
import com.tech.ezconvert.customcommand.CustomCommandPresetManager;
import com.tech.ezconvert.customcommand.CustomCommandSession;
import com.tech.ezconvert.customcommand.CustomCommandSessionStore;
import com.tech.ezconvert.utils.ConfigManager;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.ToastUtils;
import com.tech.ezconvert.worker.CustomCommandWorker;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 提供独立的 FFmpeg 自定义参数编辑和任务提交界面 */
public class CustomCommandActivity extends BaseActivity {
    private static final String PREFS = "custom_command_ui";
    private static final String KEY_DRAFT = "draft_json";
    private static final String KEY_WORK_ID = "active_work_id";
    private static final String KEY_SESSION_ID = "active_session_id";
    private static final int MAX_LOG_DISPLAY_BYTES = 24 * 1024;

    private final Gson gson = new Gson();
    private final List<CustomCommandSession.InputSource> inputs = new ArrayList<>();
    private final List<EditText> outputNameEditors = new ArrayList<>();

    private ActivityResultLauncher<Intent> inputPicker;
    private ActivityResultLauncher<Intent> folderPicker;
    private ActivityResultLauncher<Intent> outputFilePicker;

    private Spinner modeSpinner;
    private TextView inputListText;
    private TextView outputLocationText;
    private TextView statusText;
    private TextView commandLogText;
    private ProgressBar progressBar;
    private ScrollView commandLogScroll;
    private EditText commandEditor;
    private LinearLayout outputRows;
    private View executeButton;
    private View cancelButton;
    private CustomCommandSessionPresetState currentOutputState = new CustomCommandSessionPresetState();
    private CustomCommandPresetManager presetManager;
    private UUID observedWorkId;
    private WorkInfo latestWorkInfo;
    private String activeSessionId = "";
    private String lastDisplayedLog = null;
    private boolean logFollowBottom = true;
    private boolean programmaticLogScroll = false;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    // 日志单独保存在会话文件中，轮询读取不会把其他并行 FFmpeg 任务的日志混在一起
    private final Runnable uiRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshTaskLog();
            if (latestWorkInfo != null && latestWorkInfo.getState() == WorkInfo.State.RUNNING) {
                renderRunningStatus(latestWorkInfo);
            }
            uiHandler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected int getTitleContainerId() {
        // BaseActivity 会给该容器增加状态栏内边距，避免固定高度的 Toolbar 被状态栏遮挡
        return R.id.custom_command_toolbar_container;
    }

    @Override
    protected int getScrollContentId() {
        return R.id.custom_command_scroll;
    }

    @Override
    protected boolean shouldApplyTranslucentToolbar() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_command);
        presetManager = new CustomCommandPresetManager(this);

        MaterialToolbar toolbar = findViewById(R.id.custom_command_toolbar);
        toolbar.setNavigationIconTint(MaterialColors.getColor(toolbar,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.custom_command_toolbar_menu);
        android.view.MenuItem resetItem = toolbar.getMenu().findItem(R.id.custom_command_reset);
        if (resetItem != null && resetItem.getIcon() != null) {
            resetItem.getIcon().mutate().setTint(MaterialColors.getColor(toolbar,
                    com.google.android.material.R.attr.colorOnSurfaceVariant));
        }
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.custom_command_reset) {
                confirmResetScreen();
                return true;
            }
            return false;
        });
        modeSpinner = findViewById(R.id.custom_command_mode_spinner);
        inputListText = findViewById(R.id.custom_command_input_list);
        inputListText.setTextIsSelectable(false);
        inputListText.setMovementMethod(LinkMovementMethod.getInstance());
        inputListText.setHighlightColor(android.graphics.Color.TRANSPARENT);
        outputLocationText = findViewById(R.id.custom_command_output_location);
        outputLocationText.setTextIsSelectable(true);
        statusText = findViewById(R.id.custom_command_status);
        progressBar = findViewById(R.id.custom_command_progress_bar);
        commandLogText = findViewById(R.id.custom_command_log_text);
        commandLogScroll = findViewById(R.id.custom_command_log_scroll);
        commandEditor = findViewById(R.id.custom_command_editor);
        applyDynamicWidgetColors();
        configureLogScrolling();
        outputRows = findViewById(R.id.custom_command_output_rows);
        executeButton = findViewById(R.id.custom_command_execute);
        cancelButton = findViewById(R.id.custom_command_cancel);

        configurePickers();
        restoreDraft();
        bindButtons();
        updateInputList();
        updateOutputLocationText();
        activeSessionId = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_SESSION_ID, "");
        observePreviousWork();
        refreshTaskLog();
    }

    /** 使用系统文档选择器获取 URI，避免自定义命令的输入类型被 MIME 白名单限制 */
    private void configurePickers() {
        inputPicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onInputPickerResult);
        folderPicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onFolderPickerResult);
        outputFilePicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onOutputFilePickerResult);
    }

    private void bindButtons() {
        findViewById(R.id.custom_command_add_inputs).setOnClickListener(v -> openInputPicker());
        findViewById(R.id.custom_command_clear_inputs).setOnClickListener(v -> {
            replaceInputPathsWithPlaceholders();
            deleteManagedInputFiles(inputs);
            inputs.clear();
            updateInputList();
            saveDraft();
        });
        findViewById(R.id.custom_command_insert_placeholder).setOnClickListener(v -> showPathPicker());
        findViewById(R.id.custom_command_examples).setOnClickListener(v -> showExamples());
        findViewById(R.id.custom_command_choose_folder).setOnClickListener(v -> openFolderPicker());
        findViewById(R.id.custom_command_choose_file).setOnClickListener(v -> openOutputFilePicker());
        findViewById(R.id.custom_command_add_output).setOnClickListener(v -> addOutputEditor(getString(R.string.custom_command_output_default_template)));
        findViewById(R.id.custom_command_toggle_output_options).setOnClickListener(v -> {
            boolean visible = outputRows.getVisibility() != View.VISIBLE;
            setAdvancedOutputOptionsVisible(visible);
        });
        findViewById(R.id.custom_command_preview).setOnClickListener(v -> showCommandPreview());
        findViewById(R.id.custom_command_save_preset).setOnClickListener(v -> savePreset());
        findViewById(R.id.custom_command_load_preset).setOnClickListener(v -> loadPreset());
        findViewById(R.id.custom_command_delete_preset).setOnClickListener(v -> deletePreset());
        findViewById(R.id.custom_command_copy_log).setOnClickListener(v -> copyCurrentLog());
        findViewById(R.id.custom_command_clear_log).setOnClickListener(v -> clearCurrentLog());
        executeButton.setOnClickListener(v -> confirmAndExecute());
        cancelButton.setOnClickListener(v -> cancelCurrentWork());
        modeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                saveDraft();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        commandEditor.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                saveDraft();
                if (outputRows.getVisibility() != View.VISIBLE && hasAdvancedOutputPlaceholder(s)) {
                    setAdvancedOutputOptionsVisible(true);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private boolean hasAdvancedOutputPlaceholder(CharSequence command) {
        return command != null && java.util.regex.Pattern.compile("%OUTPUT_[2-9][0-9]*%")
                .matcher(command).find();
    }

    /** 页面重建或用户返回时恢复编辑草稿；任务运行状态和命令草稿分开保存 */
    private void restoreDraft() {
        String json = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_DRAFT, "");
        CustomCommandSession draft = null;
        try {
            if (!TextUtils.isEmpty(json)) draft = gson.fromJson(json, CustomCommandSession.class);
        } catch (Exception e) {
            Log.w("CustomCommandActivity", "恢复自定义命令草稿失败", e);
        }
        if (draft == null) {
            draft = new CustomCommandSession();
            draft.commandText = getString(R.string.custom_command_default_command);
            draft.outputFileNames.add(getString(R.string.custom_command_output_default_template));
        }
        if (draft.inputs != null) {
            for (CustomCommandSession.InputSource input : draft.inputs) {
                if (input != null) inputs.add(input);
            }
        }
        commandEditor.setText(draft.commandText);
        modeSpinner.setSelection("batch".equals(draft.executionMode) ? 1 : 0);
        currentOutputState.treeUri = draft.outputTreeUri == null ? "" : draft.outputTreeUri;
        currentOutputState.documentUri = draft.outputDocumentUri == null ? "" : draft.outputDocumentUri;
        currentOutputState.documentName = draft.outputDocumentName == null ? "" : draft.outputDocumentName;
        currentOutputState.commandPath = draft.outputCommandPath == null ? "" : draft.outputCommandPath;
        ensureInputCommandPaths();
        if (!TextUtils.isEmpty(currentOutputState.documentUri)
                && (TextUtils.isEmpty(currentOutputState.commandPath)
                || !isUnderManagedPath(new File(currentOutputState.commandPath), "output"))) {
            String previousPath = currentOutputState.commandPath;
            currentOutputState.commandPath = createOutputCommandPath(currentOutputState.documentName);
            replacePathTokenInEditor(previousPath, currentOutputState.commandPath);
        }
        outputRows.removeAllViews();
        outputNameEditors.clear();
        List<String> names = draft.outputFileNames == null ? new ArrayList<>() : draft.outputFileNames;
        if (names.isEmpty()) names = java.util.Collections.singletonList(getString(R.string.custom_command_output_default_template));
        for (String name : names) addOutputEditor(name);
        boolean hasAdvancedOutputs = outputNameEditors.size() > 1 || hasAdvancedOutputPlaceholder(draft.commandText);
        setAdvancedOutputOptionsVisible(hasAdvancedOutputs);
    }

    private void saveDraft() {
        if (commandEditor == null || modeSpinner == null || outputNameEditors == null) return;
        CustomCommandSession draft = new CustomCommandSession();
        draft.executionMode = modeSpinner.getSelectedItemPosition() == 1 ? "batch" : "single";
        draft.commandText = commandEditor.getText() == null ? "" : commandEditor.getText().toString();
        draft.inputs = new ArrayList<>(inputs);
        draft.outputFileNames = getOutputFileNames();
        draft.outputTreeUri = currentOutputState.treeUri;
        draft.outputDocumentUri = currentOutputState.documentUri;
        draft.outputDocumentName = currentOutputState.documentName;
        draft.outputCommandPath = currentOutputState.commandPath;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_DRAFT, gson.toJson(draft)).apply();
    }

    private void openInputPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            inputPicker.launch(intent);
        } catch (ActivityNotFoundException e) {
            Log.w("CustomCommandActivity", "无法打开输入文件选择器", e);
            showToast(getString(R.string.custom_command_picker_unavailable));
        }
    }

    private void onInputPickerResult(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
        Intent data = result.getData();
        List<Uri> selected = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                Uri uri = data.getClipData().getItemAt(i).getUri();
                if (uri != null) selected.add(uri);
            }
        } else if (data.getData() != null) {
            selected.add(data.getData());
        }
        for (Uri uri : selected) {
            boolean exists = false;
            for (CustomCommandSession.InputSource item : inputs) {
                if (uri.toString().equals(item.uri)) { exists = true; break; }
            }
            if (exists) continue;
            try {
                // 后台任务会在 Activity 退出后读取原文件，因此必须保留系统授予的读取授权
                int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                if (flags != 0) getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (SecurityException | IllegalArgumentException e) {
                Log.w("CustomCommandActivity", "无法持久化输入文件授权", e);
            }
            if (!hasReadableInputPermission(uri)) {
                showToast(getString(R.string.custom_command_input_permission_missing, queryDisplayName(uri)));
                continue;
            }
            String name = queryDisplayName(uri);
            if (TextUtils.isEmpty(name)) name = uri.getLastPathSegment();
            String commandPath = createInputCommandPath(name);
            CustomCommandSession.InputSource source = new CustomCommandSession.InputSource(uri.toString(), name, commandPath);
            inputs.add(source);
        }
        updateInputList();
        saveDraft();
    }

    private void openFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            folderPicker.launch(intent);
        } catch (ActivityNotFoundException e) {
            Log.w("CustomCommandActivity", "无法打开输出文件夹选择器", e);
            showToast(getString(R.string.toast_cannot_open_folder_picker));
        }
    }

    private void onFolderPickerResult(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        Uri uri = result.getData().getData();
        try {
            int flags = result.getData().getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception e) {
            Log.w("CustomCommandActivity", "无法保存输出文件夹授权", e);
            showToast(getString(R.string.toast_folder_permission_failed));
            return;
        }
        if (!DocumentsContract.isTreeUri(uri) || !hasPersistedWritePermission(uri)) {
            showToast(getString(R.string.toast_folder_permission_failed));
            return;
        }
        replaceOutputPathWithPlaceholder();
        deleteManagedOutputFile(currentOutputState.commandPath);
        currentOutputState.treeUri = uri.toString();
        currentOutputState.documentUri = "";
        currentOutputState.documentName = "";
        currentOutputState.commandPath = "";
        updateOutputLocationText();
        saveDraft();
    }

    private void openOutputFilePicker() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, getString(R.string.custom_command_output_file_default_name));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            outputFilePicker.launch(intent);
        } catch (ActivityNotFoundException e) {
            Log.w("CustomCommandActivity", "无法打开指定输出文件选择器", e);
            showToast(getString(R.string.custom_command_picker_unavailable));
        }
    }

    private void onOutputFilePickerResult(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        Uri uri = result.getData().getData();
        try {
            int flags = result.getData().getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (flags != 0) getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception e) {
            Log.w("CustomCommandActivity", "无法持久化指定输出文件授权", e);
        }
        if (!hasPersistedWritePermission(uri)) {
            showToast(getString(R.string.custom_command_output_file_permission_missing));
            return;
        }
        replaceOutputPathWithPlaceholder();
        deleteManagedOutputFile(currentOutputState.commandPath);
        currentOutputState.treeUri = "";
        currentOutputState.documentUri = uri.toString();
        currentOutputState.documentName = queryDisplayName(uri);
        if (TextUtils.isEmpty(currentOutputState.documentName)) currentOutputState.documentName = getString(R.string.custom_command_output_file_default_name);
        currentOutputState.commandPath = createOutputCommandPath(currentOutputState.documentName);
        updateOutputLocationText();
        saveDraft();
    }

    private void updateInputList() {
        if (inputs.isEmpty()) {
            inputListText.setText(R.string.custom_command_no_inputs);
            return;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (int i = 0; i < inputs.size(); i++) {
            if (i > 0) builder.append("\n\n");
            CustomCommandSession.InputSource input = inputs.get(i);
            builder.append(String.valueOf(i + 1)).append(". ");
            int nameStart = builder.length();
            builder.append(input.displayName);
            int nameEnd = builder.length();
            final int inputIndex = i;
            builder.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@androidx.annotation.NonNull View widget) {
                    openInputPreview(inputIndex);
                }

                @Override
                public void updateDrawState(TextPaint drawState) {
                    drawState.setColor(MaterialColors.getColor(inputListText,
                            com.google.android.material.R.attr.colorPrimary));
                    drawState.setUnderlineText(true);
                }
            }, nameStart, nameEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.append("\n").append(input.commandPath);
        }
        inputListText.setText(builder);
    }

    /** 从输入文件名直接打开预览，保留当前页面作为返回目标 */
    private void openInputPreview(int index) {
        if (index < 0 || index >= inputs.size()) return;
        CustomCommandSession.InputSource input = inputs.get(index);
        if (input == null || TextUtils.isEmpty(input.uri)) return;
        Intent intent = new Intent(this, PreviewActivity.class);
        intent.putExtra("file_uri", input.uri);
        intent.putExtra(PreviewActivity.EXTRA_RETURN_TARGET, PreviewActivity.RETURN_TARGET_CUSTOM_COMMAND);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    private void updateOutputLocationText() {
        if (!TextUtils.isEmpty(currentOutputState.documentUri)) {
            outputLocationText.setText(getString(R.string.custom_command_output_file_selected_path,
                    currentOutputState.documentName, currentOutputState.commandPath));
        } else if (!TextUtils.isEmpty(currentOutputState.treeUri)) {
            outputLocationText.setText(getString(R.string.custom_command_output_folder_selected, describeTreeUri(Uri.parse(currentOutputState.treeUri))));
        } else {
            ConfigManager config = ConfigManager.getInstance(this);
            String mode = config.getOutputPathMode();
            if (ConfigManager.OUTPUT_PATH_CUSTOM.equals(mode)) {
                outputLocationText.setText(getString(R.string.custom_command_output_follow_custom,
                        describeTreeUri(Uri.parse(config.getCustomOutputUri()))));
            } else if (ConfigManager.OUTPUT_PATH_DCIM.equals(mode)) {
                outputLocationText.setText(getString(R.string.custom_command_output_follow_dcim));
            } else {
                outputLocationText.setText(getString(R.string.custom_command_output_follow_download));
            }
        }
    }

    private void ensureInputCommandPaths() {
        for (CustomCommandSession.InputSource input : inputs) {
            if (input == null) continue;
            String previousPath = input.commandPath;
            if (TextUtils.isEmpty(previousPath) || !isUnderManagedPath(new File(previousPath), "input")) {
                input.commandPath = createInputCommandPath(input.displayName);
                replacePathTokenInEditor(previousPath, input.commandPath);
            }
        }
    }

    private String createInputCommandPath(String name) {
        File directory = new File(new File(getCacheDir(), "custom_command_paths"), "input");
        return new File(directory, UUID.randomUUID() + "_" + sanitizePathFileName(name)).getAbsolutePath();
    }

    private String createOutputCommandPath(String name) {
        File directory = new File(new File(getCacheDir(), "custom_command_paths"), "output");
        return new File(directory, UUID.randomUUID() + "_" + sanitizePathFileName(name)).getAbsolutePath();
    }

    private String sanitizePathFileName(String name) {
        String safe = TextUtils.isEmpty(name) ? "media.bin" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.length() > 100) safe = safe.substring(safe.length() - 100);
        return TextUtils.isEmpty(safe) ? "media.bin" : safe;
    }

    private boolean hasReadableInputPermission(Uri uri) {
        if (uri == null) return false;
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            File file = new File(uri.getPath() == null ? "" : uri.getPath());
            return file.isFile() && file.canRead();
        }
        return hasPersistedReadPermission(uri);
    }

    private boolean hasPersistedReadPermission(Uri uri) {
        for (android.content.UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
            if (uri.equals(permission.getUri()) && permission.isReadPermission()) return true;
        }
        return false;
    }

    private boolean isSelectedInputCommandPath(CustomCommandSession session, String path) {
        if (session == null || session.inputs == null || TextUtils.isEmpty(path)) return false;
        for (CustomCommandSession.InputSource input : session.inputs) {
            if (input != null && path.equals(input.commandPath)) return true;
        }
        return false;
    }

    private boolean isUnderManagedPath(File file, String childDirectory) {
        try {
            File base = new File(new File(getCacheDir(), "custom_command_paths"), childDirectory).getCanonicalFile();
            File candidate = file.getCanonicalFile();
            return candidate.getPath().startsWith(base.getPath() + File.separator);
        } catch (IOException e) {
            Log.w("CustomCommandActivity", "无法验证自定义命令临时路径", e);
            return false;
        }
    }

    private boolean isWorkActive() {
        return latestWorkInfo != null && (latestWorkInfo.getState() == WorkInfo.State.ENQUEUED
                || latestWorkInfo.getState() == WorkInfo.State.RUNNING);
    }

    private void deleteManagedInputFiles(List<CustomCommandSession.InputSource> sources) {
        if (isWorkActive() || sources == null) return;
        for (CustomCommandSession.InputSource input : sources) {
            if (input == null || TextUtils.isEmpty(input.commandPath)) continue;
            File file = new File(input.commandPath);
            if (isUnderManagedPath(file, "input") && file.isFile() && !file.delete()) {
                Log.w("CustomCommandActivity", "无法删除自定义命令输入缓存：" + file);
            }
        }
    }

    private void deleteManagedOutputFile(String path) {
        if (isWorkActive() || TextUtils.isEmpty(path)) return;
        File file = new File(path);
        if (isUnderManagedPath(file, "output") && file.isFile() && !file.delete()) {
            Log.w("CustomCommandActivity", "无法删除自定义命令输出缓存：" + file);
        }
    }

    private void validateDirectPathPermissions(CustomCommandSession session, List<String> args) {
        for (int i = 0; i + 1 < args.size(); i++) {
            if (!"-i".equals(args.get(i))) continue;
            String path = args.get(i + 1);
            if (CustomCommandParser.isInputPlaceholder(path) || isSelectedInputCommandPath(session, path)) continue;
            if (path.startsWith("content://")) {
                boolean selectedUri = false;
                for (CustomCommandSession.InputSource input : session.inputs) {
                    if (input != null && path.equals(input.uri)) { selectedUri = true; break; }
                }
                if (!selectedUri) {
                    throw new IllegalArgumentException(getString(R.string.custom_command_direct_content_uri_unselected));
                }
                continue;
            }
            File file = localFileFromArgument(path);
            if (file == null) continue;
            if (!file.isFile() || !file.canRead()) {
                requireDirectStoragePermission(file);
                throw new IllegalArgumentException(getString(R.string.custom_command_direct_input_unreadable, file.getAbsolutePath()));
            }
        }

        if (args.isEmpty()) return;
        String output = args.get(args.size() - 1);
        if (CustomCommandParser.isOutputPlaceholder(output)
                || (!TextUtils.isEmpty(session.outputCommandPath) && output.equals(session.outputCommandPath))
                || isSelectedInputCommandPath(session, output)) return;
        if (output.startsWith("content://")) {
            throw new IllegalArgumentException(getString(R.string.custom_command_direct_content_uri_unselected));
        }
        File outputFile = localFileFromArgument(output);
        if (outputFile == null) return;
        File parent = outputFile.getAbsoluteFile().getParentFile();
        if (parent == null || !parent.isDirectory()) {
            throw new IllegalArgumentException(getString(R.string.custom_command_direct_output_unwritable, outputFile.getAbsolutePath()));
        }
        if (!parent.canWrite()) {
            requireDirectStoragePermission(outputFile);
            throw new IllegalArgumentException(getString(R.string.custom_command_direct_output_unwritable, outputFile.getAbsolutePath()));
        }
    }

    private File localFileFromArgument(String argument) {
        if (TextUtils.isEmpty(argument)) return null;
        if (argument.startsWith("file://")) {
            Uri uri = Uri.parse(argument);
            return uri.getPath() == null ? null : new File(uri.getPath());
        }
        if (argument.startsWith("/")) return new File(argument);
        return null;
    }

    private void requireDirectStoragePermission(File file) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
                || isAppSpecificExternalPath(file)) return;
        String path = file.getAbsolutePath();
        if (path.startsWith("/storage/") || path.startsWith("/sdcard/")
                || path.startsWith("/mnt/media_rw/")) {
            throw new IllegalArgumentException(getString(R.string.custom_command_direct_storage_permission_required));
        }
    }

    private boolean isAppSpecificExternalPath(File file) {
        if (file == null) return false;
        File[] directories = getExternalFilesDirs(null);
        if (directories != null) {
            for (File directory : directories) {
                if (directory != null && isSameOrChildPath(file, directory)) return true;
            }
        }
        File[] cacheDirectories = getExternalCacheDirs();
        if (cacheDirectories != null) {
            for (File directory : cacheDirectories) {
                if (directory != null && isSameOrChildPath(file, directory)) return true;
            }
        }
        File[] mediaDirectories = getExternalMediaDirs();
        if (mediaDirectories != null) {
            for (File directory : mediaDirectories) {
                if (directory != null && isSameOrChildPath(file, directory)) return true;
            }
        }
        return false;
    }

    private boolean isSameOrChildPath(File file, File directory) {
        try {
            String candidate = file.getCanonicalPath();
            String base = directory.getCanonicalPath();
            return candidate.equals(base) || candidate.startsWith(base + File.separator);
        } catch (IOException e) {
            Log.w("CustomCommandActivity", "无法验证应用专属存储路径", e);
            return false;
        }
    }

    private boolean hasPersistedWritePermission(Uri uri) {
        for (android.content.UriPermission permission : getContentResolver().getPersistedUriPermissions()) {
            if (uri.equals(permission.getUri()) && permission.isWritePermission()) return true;
        }
        return false;
    }

    private String describeTreeUri(Uri uri) {
        try {
            String id = DocumentsContract.getTreeDocumentId(uri);
            if (id != null) {
                String[] split = id.split(":", 2);
                if (split.length == 2) return split[1].isEmpty() ? split[0] : split[1];
                return id;
            }
        } catch (Exception e) {
            Log.d("CustomCommandActivity", "无法解析输出目录显示路径，将显示原始 URI", e);
        }
        return uri.toString();
    }

    private void setAdvancedOutputOptionsVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        findViewById(R.id.custom_command_output_names_help).setVisibility(visibility);
        outputRows.setVisibility(visibility);
        findViewById(R.id.custom_command_add_output).setVisibility(visibility);
        ((com.google.android.material.button.MaterialButton) findViewById(R.id.custom_command_toggle_output_options))
                .setText(visible ? R.string.custom_command_hide_advanced_outputs : R.string.custom_command_show_advanced_outputs);
    }

    private void addOutputEditor(String value) {
        if (outputRows == null) return;
        if (outputNameEditors.size() >= 8) {
            showToast(getString(R.string.custom_command_output_limit));
            return;
        }
        int number = outputNameEditors.size() + 1;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = (int) (6 * getResources().getDisplayMetrics().density);
        row.setLayoutParams(rowParams);

        TextInputLayout wrapper = new TextInputLayout(this);
        wrapper.setHint(getString(R.string.custom_command_output_name_hint, number));
        wrapper.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wrapper.setLayoutParams(wrapperParams);
        TextInputEditText edit = new TextInputEditText(this);
        edit.setSingleLine(true);
        edit.setTextSize(14);
        edit.setText(value);
        wrapper.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(wrapper);
        outputNameEditors.add(edit);
        edit.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { saveDraft(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        if (number > 1) {
            com.google.android.material.button.MaterialButton remove = new com.google.android.material.button.MaterialButton(this);
            remove.setText(R.string.btn_delete);
            remove.setMinWidth(0);
            remove.setPadding(8, 0, 8, 0);
            row.addView(remove, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            remove.setOnClickListener(v -> {
                int index = outputNameEditors.indexOf(edit);
                if (index >= 0) {
                    outputNameEditors.remove(index);
                    outputRows.removeView(row);
                    relabelOutputRows();
                    saveDraft();
                }
            });
        }
        outputRows.addView(row);
    }

    private void relabelOutputRows() {
        for (int i = 0; i < outputRows.getChildCount(); i++) {
            View rowView = outputRows.getChildAt(i);
            if (rowView instanceof LinearLayout && ((LinearLayout) rowView).getChildAt(0) instanceof TextInputLayout) {
                ((TextInputLayout) ((LinearLayout) rowView).getChildAt(0)).setHint(getString(R.string.custom_command_output_name_hint, i + 1));
            }
        }
    }

    private List<String> getOutputFileNames() {
        List<String> names = new ArrayList<>();
        for (EditText edit : outputNameEditors) names.add(edit.getText() == null ? "" : edit.getText().toString().trim());
        return names;
    }

    /** 先选择已授权的路径，再选择命令中需要替换的占位符 */
    private void showPathPicker() {
        List<String> options = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        List<Boolean> inputPaths = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            CustomCommandSession.InputSource input = inputs.get(i);
            options.add(getString(R.string.custom_command_path_input_option,
                    i + 1, input.displayName, input.commandPath));
            paths.add(input.commandPath);
            inputPaths.add(true);
        }
        if (!TextUtils.isEmpty(currentOutputState.documentUri)
                && !TextUtils.isEmpty(currentOutputState.commandPath)) {
            options.add(getString(R.string.custom_command_path_output_option,
                    currentOutputState.documentName, currentOutputState.commandPath));
            paths.add(currentOutputState.commandPath);
            inputPaths.add(false);
        }
        if (options.isEmpty()) {
            showToast(getString(R.string.custom_command_path_picker_empty));
            return;
        }
        new MaterialAlertDialogBuilder(this).setTitle(R.string.custom_command_insert_placeholder)
                .setItems(options.toArray(new String[0]), (dialog, which) ->
                        showPlaceholderReplacementPicker(paths.get(which), inputPaths.get(which)))
                .setNegativeButton(R.string.btn_cancel, null).show();
    }

    private void showPlaceholderReplacementPicker(String path, boolean inputPath) {
        String command = commandEditor.getText() == null ? "" : commandEditor.getText().toString();
        String expression = inputPath
                ? "%INPUT(?:_[1-9][0-9]*)?%"
                : "%OUTPUT(?:_[1-9][0-9]*)?%";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(expression).matcher(command);
        LinkedHashSet<String> found = new LinkedHashSet<>();
        while (matcher.find()) {
            String token = matcher.group();
            // 当前指定输出文件只对应第一个输出目标，避免把它错误映射到其他输出目标
            if (!inputPath && CustomCommandParser.outputIndex(token) > 1) continue;
            found.add(token);
        }
        List<String> placeholders = new ArrayList<>(found);
        if (placeholders.isEmpty()) {
            // 命令中没有对应占位符时，将路径插入当前光标处，避免要求用户先手动编写占位符
            insertPathAtCursor(path);
            saveDraft();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.custom_command_choose_placeholder_title)
                .setItems(placeholders.toArray(new String[0]), (dialog, which) -> {
                    replaceWholePlaceholder(placeholders.get(which), path);
                    saveDraft();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    /** 将路径插入命令编辑框的当前光标位置，选中文本时则替换选中内容 */
    private void insertPathAtCursor(String path) {
        if (TextUtils.isEmpty(path) || commandEditor == null) return;
        String replacement = CustomCommandParser.quoteForDisplay(path);
        android.text.Editable editable = commandEditor.getText();
        if (editable == null) return;
        int start = commandEditor.getSelectionStart();
        int end = commandEditor.getSelectionEnd();
        if (start < 0 || end < 0) start = end = editable.length();
        int replaceStart = Math.min(start, end);
        int replaceEnd = Math.max(start, end);
        editable.replace(replaceStart, replaceEnd, replacement);
        commandEditor.setSelection(replaceStart + replacement.length());
    }

    /** 仅替换独立参数形式的占位符，避免误改滤镜表达式或其他参数中的相似文本 */
    private void replaceWholePlaceholder(String placeholder, String path) {
        if (TextUtils.isEmpty(placeholder) || TextUtils.isEmpty(path) || commandEditor == null) return;
        String original = commandEditor.getText() == null ? "" : commandEditor.getText().toString();
        String replacement = CustomCommandParser.quoteForDisplay(path);
        String token = java.util.regex.Pattern.quote(placeholder);
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?<!\\S)(?:\"" + token + "\"|'" + token + "'|" + token + ")(?=\\s|$)");
        String updated = pattern.matcher(original).replaceAll(java.util.regex.Matcher.quoteReplacement(replacement));
        if (!original.equals(updated)) {
            commandEditor.setText(updated);
            commandEditor.setSelection(commandEditor.length());
        }
    }

    private void replaceInputPathsWithPlaceholders() {
        String command = commandEditor.getText() == null ? "" : commandEditor.getText().toString();
        for (int i = 0; i < inputs.size(); i++) {
            CustomCommandSession.InputSource input = inputs.get(i);
            if (input == null) continue;
            String path = input.commandPath;
            if (!TextUtils.isEmpty(path)) {
                command = replacePathToken(command, path, i == 0 && inputs.size() == 1
                        ? "%INPUT%" : "%INPUT_" + (i + 1) + "%");
            }
        }
        commandEditor.setText(command);
    }

    private String replacePathToken(String command, String path, String placeholder) {
        String quoted = CustomCommandParser.quoteForDisplay(path);
        return command.replace(quoted, placeholder).replace(path, placeholder);
    }

    private void replacePathTokenInEditor(String path, String placeholder) {
        if (TextUtils.isEmpty(path)) return;
        String command = commandEditor.getText() == null ? "" : commandEditor.getText().toString();
        String updated = replacePathToken(command, path, placeholder);
        if (!command.equals(updated)) {
            commandEditor.setText(updated);
            commandEditor.setSelection(commandEditor.length());
        }
    }

    private void replaceOutputPathWithPlaceholder() {
        String path = currentOutputState.commandPath;
        if (TextUtils.isEmpty(path) || commandEditor == null) return;
        String command = commandEditor.getText() == null ? "" : commandEditor.getText().toString();
        commandEditor.setText(replacePathToken(command, path, "%OUTPUT_1%"));
    }

    private void showExamples() {
        String[] titles = getResources().getStringArray(R.array.custom_command_example_titles);
        String[] commands = getResources().getStringArray(R.array.custom_command_example_commands);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.custom_command_examples)
                .setItems(titles, (dialog, which) -> {
                    commandEditor.setText(commands[which]);
                    commandEditor.setSelection(commandEditor.length());
                }).setNegativeButton(R.string.btn_cancel, null).show();
    }

    private CustomCommandSession buildDraftSession() {
        CustomCommandSession session = new CustomCommandSession();
        session.executionMode = modeSpinner.getSelectedItemPosition() == 1 ? "batch" : "single";
        session.commandText = commandEditor.getText() == null ? "" : commandEditor.getText().toString();
        session.inputs = new ArrayList<>(inputs);
        session.outputFileNames = getOutputFileNames();
        session.outputTreeUri = currentOutputState.treeUri;
        session.outputDocumentUri = currentOutputState.documentUri;
        session.outputDocumentName = currentOutputState.documentName;
        session.outputCommandPath = currentOutputState.commandPath;
        ConfigManager config = ConfigManager.getInstance(this);
        session.configuredOutputMode = config.getOutputPathMode();
        session.configuredOutputTreeUri = config.getCustomOutputUri();
        return session;
    }

    /** 在启动后台任务前检查占位符、输出目标和持久化目录授权，避免明显错误进入队列后才失败 */
    private void validateDraft(CustomCommandSession session, List<String> args) {
        boolean hasInputPlaceholder = false;
        boolean hasSelectedInputPath = false;
        for (String arg : args) {
            if (CustomCommandParser.isInputPlaceholder(arg)) hasInputPlaceholder = true;
            if (isSelectedInputCommandPath(session, arg)) hasSelectedInputPath = true;
        }
        if (("batch".equals(session.executionMode) || hasInputPlaceholder || hasSelectedInputPath)
                && (session.inputs == null || session.inputs.isEmpty())) {
            throw new IllegalArgumentException(getString(R.string.custom_command_error_no_inputs));
        }
        if (session.outputFileNames == null || session.outputFileNames.isEmpty()) throw new IllegalArgumentException(getString(R.string.custom_command_error_no_outputs));
        for (int i = 0; i < session.outputFileNames.size(); i++) {
            String name = session.outputFileNames.get(i);
            if (TextUtils.isEmpty(name)) throw new IllegalArgumentException(getString(R.string.custom_command_error_empty_output_name, i + 1));
        }
        for (String arg : args) {
            if (CustomCommandParser.isInputPlaceholder(arg)) {
                int index = CustomCommandParser.inputIndex(arg);
                if ("batch".equals(session.executionMode)) {
                    if (index > 1) throw new IllegalArgumentException(getString(R.string.custom_command_error_batch_input_placeholder));
                } else {
                    if (index == 0 && session.inputs.size() != 1) throw new IllegalArgumentException(getString(R.string.custom_command_error_input_alias));
                    if (index > session.inputs.size()) throw new IllegalArgumentException(getString(R.string.custom_command_error_input_index, index));
                }
            }
            if (CustomCommandParser.isOutputPlaceholder(arg)) {
                int index = CustomCommandParser.outputIndex(arg);
                if (index > session.outputFileNames.size()) throw new IllegalArgumentException(getString(R.string.custom_command_error_output_index, index));
            }
        }
        if (!TextUtils.isEmpty(session.outputDocumentUri) && "batch".equals(session.executionMode) && session.inputs.size() > 1) {
            throw new IllegalArgumentException(getString(R.string.custom_command_error_single_document_batch));
        }
        boolean referencesOutputOne = false;
        boolean referencesSecondaryOutput = false;
        boolean referencesManagedOutput = false;
        for (String arg : args) {
            if (CustomCommandParser.isOutputPlaceholder(arg)) {
                referencesManagedOutput = true;
                int outputIndex = CustomCommandParser.outputIndex(arg);
                if (outputIndex == 1) referencesOutputOne = true;
                if (outputIndex > 1) referencesSecondaryOutput = true;
            } else if (!TextUtils.isEmpty(session.outputCommandPath) && arg.equals(session.outputCommandPath)) {
                referencesManagedOutput = true;
                referencesOutputOne = true;
            }
        }
        boolean needsOutputFolder = referencesManagedOutput
                && (TextUtils.isEmpty(session.outputDocumentUri) || referencesSecondaryOutput);
        if (needsOutputFolder && TextUtils.isEmpty(session.outputTreeUri)
                && ConfigManager.OUTPUT_PATH_CUSTOM.equals(session.configuredOutputMode)
                && TextUtils.isEmpty(session.configuredOutputTreeUri)) {
            throw new IllegalArgumentException(getString(R.string.custom_command_output_custom_path_missing));
        }
        if (needsOutputFolder) {
            String activeTree = !TextUtils.isEmpty(session.outputTreeUri)
                    ? session.outputTreeUri
                    : (ConfigManager.OUTPUT_PATH_CUSTOM.equals(session.configuredOutputMode) ? session.configuredOutputTreeUri : "");
            if (!TextUtils.isEmpty(activeTree) && !hasPersistedWritePermission(Uri.parse(activeTree))) {
                throw new IllegalArgumentException(getString(R.string.custom_command_output_folder_permission_missing));
            }
        }
        if (referencesOutputOne && !TextUtils.isEmpty(session.outputDocumentUri)
                && !hasPersistedWritePermission(Uri.parse(session.outputDocumentUri))) {
            throw new IllegalArgumentException(getString(R.string.custom_command_output_file_permission_missing));
        }
        for (String arg : args) {
            if (isSelectedInputCommandPath(session, arg)) {
                for (CustomCommandSession.InputSource input : session.inputs) {
                    if (input != null && arg.equals(input.commandPath)) {
                        File staged = new File(input.commandPath);
                        Uri uri = Uri.parse(input.uri);
                        if ((!staged.isFile() || staged.length() <= 0L) && !hasReadableInputPermission(uri)) {
                            throw new IllegalArgumentException(getString(R.string.custom_command_input_permission_missing, input.displayName));
                        }
                    }
                }
            }
        }
        validateDirectPathPermissions(session, args);
    }

    /** 预览只解释参数与占位符映射，不提前构造临时路径，也不宣称 FFmpeg 参数一定有效 */
    private void showCommandPreview() {
        try {
            CustomCommandSession session = buildDraftSession();
            List<String> args = CustomCommandParser.parse(this, session.commandText);
            validateDraft(session, args);
            StringBuilder preview = new StringBuilder();
            preview.append(getString(R.string.custom_command_preview_inputs)).append('\n');
            for (int i = 0; i < session.inputs.size(); i++) preview.append(i + 1).append(". ").append(session.inputs.get(i).displayName).append('\n');
            preview.append('\n').append(getString(R.string.custom_command_preview_outputs)).append('\n');
            for (int i = 0; i < session.outputFileNames.size(); i++) preview.append("%OUTPUT_").append(i + 1).append("% → ").append(session.outputFileNames.get(i)).append('\n');
            preview.append('\n').append(getString(R.string.custom_command_preview_arguments)).append('\n');
            for (String arg : args) {
                if (CustomCommandParser.isInputPlaceholder(arg)) {
                    int index = CustomCommandParser.inputIndex(arg);
                    if ("batch".equals(session.executionMode)) {
                        preview.append(CustomCommandParser.quoteForDisplay(session.inputs.get(0).displayName));
                    } else {
                        int actual = index == 0 ? 0 : index - 1;
                        preview.append(CustomCommandParser.quoteForDisplay(session.inputs.get(actual).displayName));
                    }
                } else if (CustomCommandParser.isOutputPlaceholder(arg)) {
                    int index = CustomCommandParser.outputIndex(arg);
                    preview.append(CustomCommandParser.quoteForDisplay(session.outputFileNames.get(index - 1)));
                } else {
                    preview.append(CustomCommandParser.quoteForDisplay(arg));
                }
                preview.append(' ');
            }
            preview.append("\n\n").append(getString(R.string.custom_command_preview_note));
            new MaterialAlertDialogBuilder(this).setTitle(R.string.custom_command_preview)
                    .setMessage(preview.toString()).setPositiveButton(R.string.dialog_close, null)
                    .setNegativeButton(R.string.btn_cancel, null).show();
        } catch (Exception e) {
            showError(e.getMessage());
        }
    }

    private void confirmAndExecute() {
        final CustomCommandSession session;
        final List<String> args;
        try {
            session = buildDraftSession();
            args = CustomCommandParser.parse(this, session.commandText);
            validateDraft(session, args);
        } catch (Exception e) {
            showError(e.getMessage());
            return;
        }
        String message = getString(R.string.custom_command_confirm_message,
                session.inputs.size(), session.outputFileNames.size());
        new MaterialAlertDialogBuilder(this).setTitle(R.string.custom_command_confirm_title)
                .setMessage(message + "\n\n" + session.commandText)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.custom_command_execute, (dialog, which) -> enqueueSession(session))
                .show();
    }

    private void enqueueSession(CustomCommandSession session) {
        session.id = UUID.randomUUID().toString();
        try {
            CustomCommandSessionStore.save(this, session);
        } catch (Exception e) {
            showError(getString(R.string.custom_command_error_save_session, e.getMessage()));
            return;
        }
        // WorkManager 只接收会话 ID，完整命令和 URI 列表由会话文件承载
        Data input = new Data.Builder().putString(CustomCommandWorker.KEY_SESSION_ID, session.id).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(CustomCommandWorker.class)
                .setInputData(input).addTag("custom-command").build();
        WorkManager.getInstance(this).enqueue(request);
        observedWorkId = request.getId();
        activeSessionId = session.id;
        lastDisplayedLog = null;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(KEY_WORK_ID, observedWorkId.toString())
                .putString(KEY_SESSION_ID, activeSessionId)
                .apply();
        commandLogText.setText(R.string.custom_command_log_waiting);
        progressBar.setIndeterminate(true);
        progressBar.setProgress(0);
        observeWork(observedWorkId);
        executeButton.setEnabled(false);
        cancelButton.setVisibility(View.VISIBLE);
        statusText.setText(R.string.custom_command_status_queued);
        saveDraft();
    }

    private void observePreviousWork() {
        String value = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_WORK_ID, "");
        activeSessionId = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_SESSION_ID, activeSessionId);
        if (TextUtils.isEmpty(value)) return;
        try {
            observedWorkId = UUID.fromString(value);
            observeWork(observedWorkId);
        } catch (IllegalArgumentException e) {
            Log.w("CustomCommandActivity", "已保存的自定义命令任务 ID 无效", e);
        }
    }

    private void observeWork(UUID workId) {
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(workId).observe(this, info -> {
            if (info == null) return;
            latestWorkInfo = info;
            if (info.getState() == WorkInfo.State.ENQUEUED) {
                executeButton.setEnabled(false);
                cancelButton.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);
                statusText.setText(R.string.custom_command_status_queued);
            } else if (info.getState() == WorkInfo.State.RUNNING) {
                executeButton.setEnabled(false);
                cancelButton.setVisibility(View.VISIBLE);
                renderRunningStatus(info);
            } else if (info.getState() == WorkInfo.State.SUCCEEDED) {
                progressBar.setIndeterminate(false);
                progressBar.setProgress(100);
                statusText.setText(getString(R.string.custom_command_status_succeeded,
                        info.getOutputData().getInt(CustomCommandWorker.KEY_SUCCESS_COUNT, 1)));
                finishObservedWork();
            } else if (info.getState() == WorkInfo.State.FAILED) {
                progressBar.setIndeterminate(false);
                String error = info.getOutputData().getString(CustomCommandWorker.KEY_ERROR_MESSAGE);
                statusText.setText(getString(R.string.custom_command_status_failed, TextUtils.isEmpty(error) ? getString(R.string.error_unknown) : error));
                finishObservedWork();
            } else if (info.getState() == WorkInfo.State.CANCELLED) {
                progressBar.setIndeterminate(false);
                statusText.setText(R.string.custom_command_status_cancelled);
                finishObservedWork();
            }
            refreshTaskLog();
        });
    }

    /** 根据 Worker 上报的媒体时间与队列信息刷新进度；未知总时长时显示不确定进度，避免固定停留在 0%% */
    private void renderRunningStatus(WorkInfo info) {
        Data progressData = info.getProgress();
        int percent = Math.max(0, Math.min(100, progressData.getInt("progress", 0)));
        boolean indeterminate = progressData.getBoolean("indeterminate", true);
        int itemIndex = Math.max(1, progressData.getInt("itemIndex", 1));
        int totalItems = Math.max(itemIndex, progressData.getInt("totalItems", 1));
        int currentPercent = Math.max(0, Math.min(100, progressData.getInt("currentProgress", 0)));
        long startedAt = progressData.getLong("startedAt", 0L);
        String elapsed = formatElapsed(startedAt > 0 ? System.currentTimeMillis() - startedAt : 0L);

        progressBar.setIndeterminate(indeterminate);
        if (!indeterminate) progressBar.setProgress(percent);
        if (indeterminate) {
            statusText.setText(getString(R.string.custom_command_status_running_unknown, itemIndex, totalItems, elapsed));
        } else {
            statusText.setText(getString(R.string.custom_command_status_running_known,
                    itemIndex, totalItems, percent, currentPercent, elapsed));
        }
    }

    private String formatElapsed(long elapsedMs) {
        long seconds = Math.max(0L, elapsedMs / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        if (hours > 0) return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, remainder);
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, remainder);
    }

    /** 读取当前会话的独立日志文件，避免其他转换任务的 FFmpeg 输出混入此处 */
    private void refreshTaskLog() {
        if (commandLogText == null) return;
        String content = "";
        if (!TextUtils.isEmpty(activeSessionId)) {
            File logFile = CustomCommandSessionStore.getLogFile(this, activeSessionId);
            if (logFile.isFile() && logFile.length() > 0) {
                try (FileInputStream input = new FileInputStream(logFile);
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    long length = logFile.length();
                    long skip = Math.max(0L, length - MAX_LOG_DISPLAY_BYTES);
                    long skipped = 0L;
                    while (skipped < skip) {
                        long count = input.skip(skip - skipped);
                        if (count <= 0L) break;
                        skipped += count;
                    }
                    byte[] buffer = new byte[4096];
                    int read;
                    int remaining = MAX_LOG_DISPLAY_BYTES;
                    while (remaining > 0 && (read = input.read(buffer, 0, Math.min(buffer.length, remaining))) != -1) {
                        output.write(buffer, 0, read);
                        remaining -= read;
                    }
                    content = new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                    if (skipped > 0) content = getString(R.string.custom_command_log_truncated) + "\n" + content;
                } catch (IOException e) {
                    Log.w("CustomCommandActivity", "读取自定义命令任务日志失败", e);
                    content = getString(R.string.custom_command_log_read_failed, e.getMessage() == null ? "" : e.getMessage());
                }
            }
        }
        if (TextUtils.isEmpty(content)) {
            boolean active = latestWorkInfo != null && (latestWorkInfo.getState() == WorkInfo.State.ENQUEUED
                    || latestWorkInfo.getState() == WorkInfo.State.RUNNING);
            content = getString(active ? R.string.custom_command_log_waiting : R.string.custom_command_log_empty);
        }
        if (content.equals(lastDisplayedLog)) return;

        // 只在用户原本就在日志末尾时跟随新内容，阅读旧日志时不主动改变其滚动位置
        boolean followBottom = logFollowBottom || isLogNearBottom();
        lastDisplayedLog = content;
        programmaticLogScroll = true;
        commandLogText.setText(content);
        if (followBottom) {
            commandLogScroll.post(() -> {
                if (isFinishing() || commandLogScroll.getChildCount() == 0) {
                    programmaticLogScroll = false;
                    return;
                }
                View logContent = commandLogScroll.getChildAt(0);
                int maxScroll = Math.max(0, logContent.getHeight() - commandLogScroll.getHeight());
                commandLogScroll.scrollTo(0, maxScroll);
                logFollowBottom = true;
                commandLogScroll.post(() -> programmaticLogScroll = false);
            });
        } else {
            // 非跟随状态下不调用 fullScroll，避免抢走外层页面的焦点并造成页面跳动
            commandLogScroll.post(() -> programmaticLogScroll = false);
        }
    }

    /** 为进度控件和返回按钮显式应用当前主题的动态颜色 */
    private void applyDynamicWidgetColors() {
        int primaryColor = MaterialColors.getColor(progressBar,
                com.google.android.material.R.attr.colorPrimary);
        int trackColor = MaterialColors.getColor(progressBar,
                com.google.android.material.R.attr.colorSurfaceVariant);
        progressBar.setProgressTintList(ColorStateList.valueOf(primaryColor));
        progressBar.setIndeterminateTintList(ColorStateList.valueOf(primaryColor));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(trackColor));
    }

    /** 保留日志区域自己的触摸事件，避免外层页面抢走竖向滑动手势 */
    private void configureLogScrolling() {
        commandLogScroll.setOnScrollChangeListener((View view, int scrollX, int scrollY,
                                                    int oldScrollX, int oldScrollY) -> {
            if (!programmaticLogScroll) logFollowBottom = isLogNearBottom();
        });
        commandLogScroll.setOnTouchListener((view, event) -> {
            ViewParent parent = commandLogScroll.getParent();
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                if (parent != null) parent.requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
    }

    private boolean isLogNearBottom() {
        if (commandLogScroll == null || commandLogScroll.getChildCount() == 0) return true;
        View content = commandLogScroll.getChildAt(0);
        return commandLogScroll.getScrollY() + commandLogScroll.getHeight()
                >= content.getHeight() - dpToPx(20);
    }

    /** 复制当前显示的日志，避免启用可选中文本后让日志更新抢占页面焦点 */
    private void copyCurrentLog() {
        String content = lastDisplayedLog;
        if (TextUtils.isEmpty(content)) content = getString(R.string.custom_command_log_empty);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.custom_command_log_title), content));
            showToast(getString(R.string.custom_command_log_copied));
        }
    }

    /** 清空当前自定义命令会话的日志文件和卡片显示，不影响应用的全局 FFmpeg 日志 */
    private void clearCurrentLog() {
        String error = clearCurrentLogContent();
        if (error == null) {
            showToast(getString(R.string.custom_command_log_cleared));
        } else {
            showToast(getString(R.string.custom_command_log_clear_failed, error));
        }
    }

    private String clearCurrentLogContent() {
        try {
            if (!TextUtils.isEmpty(activeSessionId)) {
                File logFile = CustomCommandSessionStore.getLogFile(this, activeSessionId);
                // 与后台 Worker 使用相同的锁，避免清空文件时和追加日志的写入操作冲突
                synchronized (CustomCommandWorker.class) {
                    if (logFile.isFile()) {
                        try (FileOutputStream ignored = new FileOutputStream(logFile, false)) {
                            // 打开并关闭文件即可将其截断为零字节
                        }
                    }
                }
            }
            lastDisplayedLog = null;
            refreshTaskLog();
            return null;
        } catch (IOException | SecurityException e) {
            Log.w("CustomCommandActivity", "清除自定义命令任务日志失败", e);
            return TextUtils.isEmpty(e.getMessage()) ? getString(R.string.error_unknown) : e.getMessage();
        }
    }

    /** 将当前编辑界面恢复为默认状态，已提交的后台任务和保存的预设保持不变 */
    private void confirmResetScreen() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.custom_command_reset_title)
                .setMessage(R.string.custom_command_reset_confirm)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.custom_command_reset, (dialog, which) -> resetScreenToDefaults())
                .show();
    }

    private void resetScreenToDefaults() {
        String logClearError = clearCurrentLogContent();

        deleteManagedInputFiles(inputs);
        deleteManagedOutputFile(currentOutputState.commandPath);
        inputs.clear();
        updateInputList();
        commandEditor.setText(R.string.custom_command_default_command);
        modeSpinner.setSelection(0);
        currentOutputState = new CustomCommandSessionPresetState();
        outputRows.removeAllViews();
        outputNameEditors.clear();
        addOutputEditor(getString(R.string.custom_command_output_default_template));
        setAdvancedOutputOptionsVisible(false);
        updateOutputLocationText();
        findViewById(R.id.custom_command_scroll).scrollTo(0, 0);

        // 后台任务的进度和取消入口必须反映真实状态，不能因重置编辑表单而伪装成空闲
        boolean workActive = latestWorkInfo != null
                && (latestWorkInfo.getState() == WorkInfo.State.ENQUEUED
                || latestWorkInfo.getState() == WorkInfo.State.RUNNING);
        if (!workActive) {
            progressBar.setIndeterminate(false);
            progressBar.setProgress(0);
            statusText.setText(R.string.custom_command_status_idle);
            executeButton.setEnabled(true);
            cancelButton.setVisibility(View.GONE);
        }
        saveDraft();

        if (logClearError == null) {
            showToast(getString(R.string.custom_command_reset_done));
        } else {
            showToast(getString(R.string.custom_command_reset_done_log_error, logClearError));
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void startUiRefresh() {
        uiHandler.removeCallbacks(uiRefreshRunnable);
        uiHandler.post(uiRefreshRunnable);
    }

    private void stopUiRefresh() {
        uiHandler.removeCallbacks(uiRefreshRunnable);
    }

    private void finishObservedWork() {
        executeButton.setEnabled(true);
        cancelButton.setVisibility(View.GONE);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_WORK_ID).apply();
    }

    private void cancelCurrentWork() {
        if (observedWorkId == null) return;
        new MaterialAlertDialogBuilder(this).setTitle(R.string.dialog_title_cancel)
                .setMessage(R.string.custom_command_cancel_confirm)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_confirm_cancel, (dialog, which) -> WorkManager.getInstance(this).cancelWorkById(observedWorkId))
                .show();
    }

    private void savePreset() {
        TextInputEditText input = new TextInputEditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.custom_command_preset_name_hint);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.custom_command_save_preset)
                .setView(input)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        showToast(getString(R.string.custom_command_preset_name_required));
                        return;
                    }
                    CustomCommandSession preset = buildDraftSession();
                    presetManager.save(name, preset);
                    showToast(getString(R.string.custom_command_preset_saved, name));
                }).show();
    }

    private void loadPreset() {
        List<String> names = presetManager.getNames();
        if (names.isEmpty()) {
            showToast(getString(R.string.custom_command_no_presets));
            return;
        }
        String[] list = names.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.custom_command_load_preset)
                .setItems(list, (dialog, which) -> {
                    CustomCommandSession preset = presetManager.load(list[which]);
                    if (preset == null) return;
                    commandEditor.setText(preset.commandText);
                    modeSpinner.setSelection("batch".equals(preset.executionMode) ? 1 : 0);
                    outputRows.removeAllViews();
                    outputNameEditors.clear();
                    if (preset.outputFileNames != null) {
                        for (String name : preset.outputFileNames) addOutputEditor(name);
                    }
                    if (outputNameEditors.isEmpty()) addOutputEditor(getString(R.string.custom_command_output_default_template));
                    updateOutputLocationText();
                    boolean hasAdvancedOutputs = outputNameEditors.size() > 1 || hasAdvancedOutputPlaceholder(preset.commandText);
                    setAdvancedOutputOptionsVisible(hasAdvancedOutputs);
                    saveDraft();
                    showToast(getString(R.string.custom_command_preset_loaded, list[which]));
                }).setNegativeButton(R.string.btn_cancel, null).show();
    }

    private void deletePreset() {
        List<String> names = presetManager.getNames();
        if (names.isEmpty()) {
            showToast(getString(R.string.custom_command_no_presets));
            return;
        }
        String[] list = names.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.custom_command_delete_preset)
                .setItems(list, (dialog, which) -> new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.custom_command_delete_preset)
                        .setMessage(getString(R.string.custom_command_delete_preset_confirm, list[which]))
                        .setNegativeButton(R.string.btn_cancel, null)
                        .setPositiveButton(R.string.btn_delete, (d, w) -> {
                            presetManager.delete(list[which]);
                            showToast(getString(R.string.custom_command_preset_deleted, list[which]));
                        }).show())
                .setNegativeButton(R.string.btn_cancel, null).show();
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception e) {
            Log.w("CustomCommandActivity", "读取文件名称失败", e);
        }
        return uri.getLastPathSegment();
    }

    private void showError(String message) {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.custom_command_error_title)
                .setMessage(TextUtils.isEmpty(message) ? getString(R.string.error_unknown) : message)
                .setPositiveButton(R.string.dialog_close, null).show();
    }

    private void showToast(String message) {
        ToastUtils.showCustomLong(this, message);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startUiRefresh();
    }

    @Override
    protected void onPause() {
        saveDraft();
        stopUiRefresh();
        super.onPause();
    }

    private static class CustomCommandSessionPresetState {
        String treeUri = "";
        String documentUri = "";
        String documentName = "";
        String commandPath = "";
    }
}
