package com.tech.ezconvert.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tech.ezconvert.R;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 版本更新日志管理器
 * 负责在应用版本更新后首次启动时，从 GitHub Release 获取对应版本的更新日志
 * 并以 Material Dialog 形式展示
 */
public class ReleaseNotesManager {
    
    private static final String TAG = "ReleaseNotesManager";
    private static final String PREF_NAME = "release_notes_prefs";
    private static final String KEY_LAST_SEEN_VERSION = "last_seen_version";
    private static final String GITHUB_API_URL = "https://api.github.com/repos/XiaoHao560/EzConvert/releases";
    
    // 静态共享线程池，生命周期跟随应用进程
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private ReleaseNotesManager() {
        // 工具类，禁止实例化
    }
    
    /**
     * 检查并显示当前版本的更新日志
     * 若该版本日志已展示过，则静默跳过
     */
    public static void showIfNeeded(Context context) {
        String currentVersion = getCurrentVersion(context);
        if (currentVersion == null) {
            Log.w(TAG, "无法获取当前版本号，跳过更新日志展示");
            return;
        }
        
        String lastSeenVersion = getLastSeenVersion(context);
        if (currentVersion.equals(lastSeenVersion)) {
            Log.d(TAG, "版本 " + currentVersion + " 的更新日志已展示过，跳过");
            return;
        }
        
        fetchReleaseNotes(context, currentVersion);
    }
    
    /**
     * 异步从 GitHub API 获取 Release 列表，匹配当前版本号
     */
    private static void fetchReleaseNotes(Context context, String currentVersion) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            BufferedReader reader = null;
            try {
                URL url = new URL(GITHUB_API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                
                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    Log.e(TAG, "GitHub API 请求失败，响应码: " + responseCode);
                    return;
                }
                
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                JSONArray releasesArray = new JSONArray(response.toString());
                String releaseNotes = null;
                String releaseName = null;
                
                for (int i = 0; i < releasesArray.length(); i++) {
                    JSONObject release = releasesArray.getJSONObject(i);
                    String tagName = release.getString("tag_name");
                    
                    // 匹配版本号，自动处理 GitHub tag 与 APK versionName 的差异
                    if (isVersionMatch(tagName, currentVersion)) {
                        releaseNotes = release.optString("body", "");
                        releaseName = release.optString("name", tagName);
                        break;
                    }
                }
                
                if (releaseNotes == null || releaseNotes.trim().isEmpty()) {
                    Log.d(TAG, "未找到版本 " + currentVersion + " 的更新日志");
                    return;
                }
                
                final String finalReleaseNotes = releaseNotes;
                final String finalReleaseName = releaseName;
                
                mainHandler.post(() -> showDialog(context, finalReleaseName, finalReleaseNotes, currentVersion));
                
            } catch (Exception e) {
                Log.e(TAG, "获取更新日志失败", e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception ignored) {
                        // 静默处理
                    }
                }
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }
    
    /**
     * 判断 GitHub tag 是否与当前应用版本匹配
     * 标准化规则：去除 v/V 前缀，将连字符 '-' 统一替换为点号 '.'，忽略大小写
     * 例：
     *   GitHub tag: v0.14.0-alpha  → 0.14.0.alpha
     *   APK version: v0.14.0.alpha  → 0.14.0.alpha
     */
    private static boolean isVersionMatch(String tagName, String currentVersion) {
        String normalizedTag = tagName.replaceFirst("^[vV]", "")
                                    .replace('-', '.')
                                    .toLowerCase();
        String normalizedVersion = currentVersion.replaceFirst("^[vV]", "")
                                               .replace('-', '.')
                                               .toLowerCase();
        return normalizedTag.equals(normalizedVersion);
    }
    
    /**
     * 在主线程展示 Material 3 更新日志弹窗
     */
    private static void showDialog(Context context, String releaseName, String releaseNotes, String currentVersion) {
        if (!(context instanceof Activity)) {
            Log.e(TAG, "上下文不是 Activity，无法显示对话框");
            return;
        }
        
        Activity activity = (Activity) context;
        if (activity.isFinishing() || activity.isDestroyed()) {
            Log.e(TAG, "Activity 已销毁，无法显示对话框");
            return;
        }
        
        // 渲染 Markdown
        Markwon markwon = Markwon.builder(context)
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .build();
        
        TextView messageView = new TextView(context);
        messageView.setPadding(50, 30, 50, 30);
        messageView.setTextSize(14);
        messageView.setMovementMethod(LinkMovementMethod.getInstance());
        messageView.setVerticalScrollBarEnabled(true);
        messageView.setScrollbarFadingEnabled(true);
        
        markwon.setMarkdown(messageView, releaseNotes);
        
        int maxHeight = context.getResources().getDisplayMetrics().heightPixels * 2 / 3;
        messageView.setMaxHeight(maxHeight);
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle(context.getString(R.string.release_notes_dialog_title));
        builder.setView(messageView);
        builder.setPositiveButton(context.getString(R.string.release_notes_dialog_btn_ok), (dialog, which) -> {
            saveLastSeenVersion(context, currentVersion);
            dialog.dismiss();
        });
        builder.setCancelable(false);
        
        try {
            builder.show();
        } catch (Exception e) {
            Log.e(TAG, "显示更新日志弹窗失败", e);
        }
    }
    
    /**
     * 获取当前应用版本号
     */
    private static String getCurrentVersion(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pInfo = pm.getPackageInfo(context.getPackageName(), PackageManager.PackageInfoFlags.of(0));
            } else {
                pInfo = pm.getPackageInfo(context.getPackageName(), 0);
            }
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "获取版本信息失败", e);
            return null;
        }
    }
    
    /**
     * 读取本地已记录的"上次已看"版本号
     */
    private static String getLastSeenVersion(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_SEEN_VERSION, "");
    }
    
    /**
     * 保存当前版本号为"已看过更新日志"
     */
    private static void saveLastSeenVersion(Context context, String version) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_SEEN_VERSION, version).apply();
    }
}