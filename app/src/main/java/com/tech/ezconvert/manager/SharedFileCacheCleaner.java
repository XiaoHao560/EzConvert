package com.tech.ezconvert.manager;

import android.content.Context;

import com.tech.ezconvert.utils.Log;

import java.io.File;
import java.util.Locale;

/** 清理 FileUtils 使用的 shared_files 临时缓存 */
public class SharedFileCacheCleaner {
    private static final String TAG = "SharedFileCacheCleaner";
    private static final long MAX_AGE_MS = 24L * 60L * 60L * 1000L;

    private SharedFileCacheCleaner() {
    }

    /**
     * 删除超过一天未使用的 shared_files 临时文件
     * 这里只清理过期文件，不影响仍可能被 Worker 使用的近期缓存
     */
    public static void cleanup(Context context) {
        try {
            File cacheDir = new File(context.getCacheDir(), "shared_files");
            if (!cacheDir.exists()) return;

            File[] files = cacheDir.listFiles();
            if (files == null) return;

            long now = System.currentTimeMillis();
            int deletedCount = 0;
            long freedBytes = 0;
            for (File file : files) {
                if (now - file.lastModified() > MAX_AGE_MS) {
                    long size = file.length();
                    if (file.delete()) {
                        deletedCount++;
                        freedBytes += size;
                    }
                }
            }
            Log.d(TAG, "清理缓存成功: 删除 " + deletedCount + " 个文件，释放 "
                    + formatFileSize(freedBytes) + " (" + freedBytes + " bytes)");
        } catch (Exception e) {
            Log.e(TAG, "清理缓存失败", e);
        }
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.2f KB", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.2f MB", bytes / (1024.0 * 1024));
    }
}
