package com.tech.ezconvert.manager;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;

import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.FileUtils;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.ToastUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 负责文件选择、Share Intent 解析及 Uri/显示名称映射
 * UI 状态由回调交给 Activity 处理
 */
public class MediaSelectionManager {
    public interface Listener {
        void onFilesSelected(int count, String firstFileName, boolean fromShare);
        void onSelectionCleared(String message);
    }

    private final Context context;
    private final ConversionQueueManager queue;
    private final Listener listener;

    public MediaSelectionManager(Context context, ConversionQueueManager queue, Listener listener) {
        this.context = context;
        this.queue = queue;
        this.listener = listener;
    }

    /** 打开系统文件选择器，支持一次选择多个视频、音频或图片文件 */
    public void openFilePicker(ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "audio/*", "image/*"});
        try {
            launcher.launch(Intent.createChooser(intent, context.getString(R.string.picker_title)));
        } catch (Exception e) {
            ToastUtils.show(context, context.getString(R.string.toast_cannot_open_picker));
            Log.e("MediaSelectionManager", "打开文件选择器失败", e);
        }
    }

    /** 处理系统文件选择器返回结果，并把文件统一写入转换队列 */
    public void handlePickerResult(Intent data) {
        if (data == null) return;
        if (data.getClipData() != null) {
            int count = loadUris(data.getClipData());
            if (count > 0) {
                listener.onFilesSelected(count, getFirstDisplayName(), false);
            }
        } else if (data.getData() != null) {
            List<Uri> uris = new ArrayList<>();
            uris.add(data.getData());
            int count = loadUris(uris);
            if (count > 0) {
                listener.onFilesSelected(count, getFirstDisplayName(), false);
            }
        }
    }

    /** 处理 ACTION_SEND/ACTION_SEND_MULTIPLE 等外部分享入口 */
    public void handleShareIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();
        if (type == null || (!type.startsWith("video/") && !type.startsWith("audio/") && !type.startsWith("image/"))) return;

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
            int count = loadUris(uriList);
            if (count == 0) {
                ToastUtils.showCustom(context, context.getString(R.string.toast_cannot_access_share));
                return;
            }
            String first = getFirstDisplayName();
            ToastUtils.showCustom(context, count < uriList.size()
                    ? context.getString(R.string.toast_partial_loaded, count, uriList.size() - count)
                    : context.getString(R.string.toast_received_files_count, count));
            listener.onFilesSelected(count, first, true);
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
            if (loadUris(uris) > 0) {
                String name = getFirstDisplayName();
                ToastUtils.showCustom(context, context.getString(R.string.toast_received_share_file));
                listener.onFilesSelected(1, name, true);
            }
        }
    }

    private int loadUris(List<Uri> uris) {
        List<String> keys = new ArrayList<>();
        Map<String, Uri> mapping = new HashMap<>();
        if (uris != null) {
            for (Uri uri : uris) {
                if (uri == null) continue;
                String displayName = FileUtils.getDisplayName(context, uri);
                if (displayName == null || displayName.isEmpty()) displayName = "file_" + System.currentTimeMillis();
                String key = makeUniqueKey(displayName, keys);
                keys.add(key);
                mapping.put(key, uri);
            }
        }
        if (keys.isEmpty()) {
            queue.clearSelection();
            listener.onSelectionCleared(context.getString(R.string.status_cannot_access_files));
            return 0;
        }
        queue.replaceSelection(keys, mapping);
        return keys.size();
    }

    private int loadUris(ClipData clipData) {
        List<Uri> uris = new ArrayList<>();
        for (int i = 0; i < clipData.getItemCount(); i++) uris.add(clipData.getItemAt(i).getUri());
        return loadUris(uris);
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
