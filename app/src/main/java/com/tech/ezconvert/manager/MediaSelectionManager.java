package com.tech.ezconvert.manager;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.result.ActivityResultLauncher;

import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.FFmpegUtil;
import com.tech.ezconvert.utils.FileUtils;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.ToastUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 负责文件选择、Share Intent 解析及 Uri/显示名称映射
 * UI 状态由回调交给 Activity 处理
 */
public class MediaSelectionManager {
    public interface Listener {
        void onSelectionValidating(int count);
        void onFilesSelected(int count, String firstFileName, boolean fromShare);
        void onSelectionCleared(String message);
    }

    private final Context context;
    private final ConversionQueueManager queue;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService validationExecutor = Executors.newSingleThreadExecutor();
    private boolean validationInProgress = false;

    public MediaSelectionManager(Context context, ConversionQueueManager queue, Listener listener) {
        this.context = context;
        this.queue = queue;
        this.listener = listener;
    }

    /** 打开系统文件选择器，允许选择任意可读文件；实际类型由 FFprobe 验证 */
    public void openFilePicker(ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            launcher.launch(Intent.createChooser(intent, context.getString(R.string.picker_title)));
        } catch (Exception e) {
            ToastUtils.show(context, context.getString(R.string.toast_cannot_open_picker));
            Log.e("MediaSelectionManager", "打开文件选择器失败", e);
        }
    }

    /** 处理系统文件选择器返回结果；先用 FFprobe 验证，再写入转换队列 */
    public void handlePickerResult(Intent data) {
        if (data == null) return;

        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            ClipData clipData = data.getClipData();
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null) uris.add(uri);
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }

        if (!uris.isEmpty()) {
            validateAndLoadUris(uris, false);
        }
    }

    /** 处理 ACTION_SEND/ACTION_SEND_MULTIPLE 等外部分享入口 */
    public void handleShareIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();
        if (type == null && !Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) return;

        if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> uriList;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                uriList = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                uriList = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            }
            if (uriList == null || uriList.isEmpty()) {
                ToastUtils.showCustom(context, context.getString(R.string.toast_no_share_file));
                return;
            }
            validateAndLoadUris(uriList, true);
        } else if (Intent.ACTION_SEND.equals(action)) {
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
            if (uri == null) {
                ToastUtils.showCustom(context, context.getString(R.string.toast_cannot_parse_share));
                return;
            }
            List<Uri> uris = new ArrayList<>();
            uris.add(uri);
            validateAndLoadUris(uris, true);
        }
    }

    /**
     * 将选择结果交给后台线程验证。只有 FFprobe 能识别出音频/视频流的文件
     * 才会真正写入转换队列，避免不支持的任意文件进入任务恢复状态
     */
    private void validateAndLoadUris(List<Uri> uris, boolean fromShare) {
        if (uris == null || uris.isEmpty()) return;

        synchronized (this) {
            if (validationInProgress) {
                mainHandler.post(() -> ToastUtils.showCustom(
                        context, context.getString(R.string.toast_validating_files)));
                return;
            }
            validationInProgress = true;
        }

        // 新一轮选择会替换旧队列；验证期间禁止开始旧任务
        queue.clearSelection();
        mainHandler.post(() -> listener.onSelectionValidating(uris.size()));

        validationExecutor.execute(() -> {
            List<String> validKeys = new ArrayList<>();
            Map<String, Uri> validMapping = new HashMap<>();
            List<String> invalidNames = new ArrayList<>();

            for (Uri uri : uris) {
                if (uri == null) continue;
                persistReadPermission(uri);

                String displayName = FileUtils.getDisplayName(context, uri);
                if (displayName == null || displayName.isEmpty()) {
                    displayName = "file_" + System.currentTimeMillis();
                }

                boolean valid = false;
                try {
                    // 直接让 FFmpegKit/FFprobe 通过 SAF 协议读取 Uri
                    // 不需要为了格式验证而复制整个文件到缓存
                    valid = FFmpegUtil.isAudioOrVideoFile(context, uri);
                } catch (Exception e) {
                    Log.e("MediaSelectionManager", "验证文件失败: " + displayName, e);
                }

                if (valid) {
                    String key = makeUniqueKey(displayName, validKeys);
                    validKeys.add(key);
                    validMapping.put(key, uri);
                } else {
                    invalidNames.add(displayName);
                }
            }

            final int validCount = validKeys.size();
            final int invalidCount = invalidNames.size();
            final String firstInvalidName = invalidNames.isEmpty() ? "" : invalidNames.get(0);

            mainHandler.post(() -> {
                synchronized (MediaSelectionManager.this) {
                    validationInProgress = false;
                }

                if (validCount > 0) {
                    queue.replaceSelection(validKeys, validMapping);
                    listener.onFilesSelected(validCount, getFirstDisplayName(), fromShare);

                    if (invalidCount > 0) {
                        ToastUtils.showCustom(context, context.getString(
                                R.string.toast_invalid_media_skipped, invalidCount, firstInvalidName));
                    } else if (fromShare) {
                        if (validCount == 1) {
                            ToastUtils.showCustom(context, context.getString(R.string.toast_received_share_file));
                        } else {
                            ToastUtils.showCustom(context, context.getString(
                                    R.string.toast_received_files_count, validCount));
                        }
                    }
                } else {
                    queue.clearSelection();
                    listener.onSelectionCleared(
                            context.getString(R.string.status_no_valid_media_files));
                    ToastUtils.showCustom(context, context.getString(
                            R.string.toast_invalid_media_skipped,
                            invalidCount,
                            firstInvalidName));
                }
            });
        });
    }

    private void persistReadPermission(Uri uri) {
        try {
            context.getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Share Intent 或不支持持久化权限的 Provider 无法持久化，这类文件会在 Worker 中尽快复制到缓存
        } catch (UnsupportedOperationException ignored) {
            // 某些 Provider 不支持持久化权限
        }
    }

    private String makeUniqueKey(String name, List<String> existing) {
        String key = name;
        if (existing.contains(key)) key = key + "_" + System.currentTimeMillis();
        return key;
    }

    private String getFirstDisplayName() {
        Uri uri = queue.getCurrentUri();
        String name = uri != null ? FileUtils.getDisplayName(context, uri) : queue.getCurrentKey();
        return name == null || name.isEmpty() ? "file" : name;
    }
}
