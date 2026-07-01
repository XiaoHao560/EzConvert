package com.tech.ezconvert.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.R;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

public class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private static final long AUTO_CHECK_INTERVAL = TimeUnit.DAYS.toMillis(1);
    private static final String PREF_LAST_CHECK = "last_update_check";
    private static final String PREF_IGNORED_VERSION = "ignored_version";
    
    // 静态共享线程池，生命周期跟随应用进程，避免频繁重建 Activity 时线程池被关闭
    private static ExecutorService sharedExecutor;
    private static final Object EXECUTOR_LOCK = new Object();
    
    private final Context context;
    private final Handler mainHandler;
    private Markwon markwon;
    
    private boolean includePrerelease = true;
    private boolean isChecking = false;
    private UpdateCheckListener updateCheckListener;
    private UpdateSettingsManager settingsManager;
    
    // 存储从GitHub获取的最新版本信息
    private String latestVersionFromGitHub;
    private String releaseNameFromGitHub;
    private String releaseNotesFromGitHub;
    private boolean isPrereleaseFromGitHub;
    private String htmlUrlFromGitHub;
    
    public interface UpdateCheckListener {
        void onUpdateCheckComplete(int comparisonResult, String latestVersion, 
                                  String releaseName, String releaseNotes,
                                  boolean isPrerelease,
                                  boolean isDevelopmentVersion, String htmlUrl);
        void onUpdateCheckError(String errorMessage);
        void onNoUpdateAvailable();
    }
    
    public UpdateChecker(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.settingsManager = new UpdateSettingsManager(context);
        this.includePrerelease = settingsManager.isIncludePrerelease();
        initMarkwon();
        ensureExecutor();
    }
    
    public void setUpdateCheckListener(UpdateCheckListener listener) {
        this.updateCheckListener = listener;
    }
    
    private void initMarkwon() {
        markwon = Markwon.builder(context)
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .build();
    }
    
     // 检查是否需要自动检查更新
    public boolean shouldAutoCheck() {
        try {
            // 检查用户设置
            if (!settingsManager.isAutoCheckEnabled()) {
                Log.d(TAG, "用户已关闭自动检测更新");
                return false;
            }
            
            int frequency = settingsManager.getCheckFrequency();
            
            if (frequency == UpdateSettingsManager.FREQUENCY_EVERY_LAUNCH) {
                // 每次启动都检查
                Log.d(TAG, "设置为每次启动都检查");
                return true;
            }
            
            // 每24小时检查一次
            SharedPreferences prefs = context.getSharedPreferences("update_preferences", Context.MODE_PRIVATE);
            long lastCheck = prefs.getLong(PREF_LAST_CHECK, 0);
            long currentTime = System.currentTimeMillis();
            return currentTime - lastCheck > AUTO_CHECK_INTERVAL;
        } catch (Exception e) {
            Log.e(TAG, "检查自动更新条件失败", e);
            return true;
        }
    }
    
     // 自动检查更新（主界面启动时调用）
    public void checkForAutoUpdate() {
        if (isChecking) {
            Log.d(TAG, "更新检查已在进行中，跳过");
            return;
        }
        
        if (!shouldAutoCheck()) {
            Log.d(TAG, "距离上次检查不足24小时，跳过自动检查");
            return;
        }
        
        if (!isNetworkAvailable()) {
            Log.d(TAG, "网络不可用，跳过自动检查");
            return;
        }
        
        checkForUpdates(false, false);
    }
    
     // 手动检查更新（"关于"界面调用）
    public void checkForManualUpdate() {
        if (!isNetworkAvailable()) {
            mainHandler.post(() -> {
                ToastUtils.show(context, context.getString(R.string.update_network_unavailable));
                if (updateCheckListener != null) {
                    updateCheckListener.onUpdateCheckError(context.getString(R.string.update_network_unavailable));
                }
            });
            return;
        }
        
        checkForUpdates(true, true);
    }
    
     // 检查版本是否被忽略
    private boolean isVersionIgnored(String version) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("update_preferences", Context.MODE_PRIVATE);
            String ignoredVersion = prefs.getString(PREF_IGNORED_VERSION, "");
            return ignoredVersion.equals(version);
        } catch (Exception e) {
            return false;
        }
    }
    
     // 标记版本为已忽略
    private void ignoreVersion(String version) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("update_preferences", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(PREF_IGNORED_VERSION, version);
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "标记忽略版本失败", e);
        }
    }
    
     // 保存检查时间
    private void saveCheckTime() {
        try {
            SharedPreferences prefs = context.getSharedPreferences("update_preferences", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(PREF_LAST_CHECK, System.currentTimeMillis());
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "保存检查时间失败", e);
        }
    }
    
    /**
     * 确保共享线程池可用
     * 若线程池未创建或已被关闭，则重新创建，防止页面频繁切换导致崩溃
     */
    private void ensureExecutor() {
        synchronized (EXECUTOR_LOCK) {
            if (sharedExecutor == null || sharedExecutor.isShutdown()) {
                sharedExecutor = Executors.newSingleThreadExecutor();
                Log.d(TAG, "Shared executor recreated");
            }
        }
    }
    
    private void checkForUpdates(final boolean showToast, final boolean isManual) {
        isChecking = true;
        
        // 确保线程池可用后再提交任务
        ensureExecutor();
        
        sharedExecutor.execute(() -> {
            try {
                // 获取当前版本信息
                String currentVersion = getCurrentVersion();
                if (currentVersion == null) {
                    handleError(context.getString(R.string.update_cannot_get_version), showToast);
                    return;
                }
                
                // 检测是否为开发版本
                boolean isDevelopmentVersion = isDevelopmentVersion(currentVersion);
                
                // GitHub API获取所有发布版本
                URL url = new URL("https://api.github.com/repos/XiaoHao560/EzConvert/releases");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode != 200) {
                    handleError(context.getString(R.string.update_api_failed, responseCode), showToast);
                    return;
                }
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JSONArray releasesArray = new JSONArray(response.toString());
                
                if (releasesArray.length() == 0) {
                    handleNoUpdate(showToast, isManual, isDevelopmentVersion);
                    return;
                }
                
                // 查找最新版本
                JSONObject latestRelease = null;
                String latestVersion = null;
                String releaseName = null;
                String releaseNotes = null;
                boolean isPrerelease = false;
                String htmlUrl = null;
                
                for (int i = 0; i < releasesArray.length(); i++) {
                    JSONObject release = releasesArray.getJSONObject(i);
                    boolean currentIsPrerelease = release.getBoolean("prerelease");
                    boolean isDraft = release.getBoolean("draft");
                    
                    if (isDraft) continue;
                    if (!includePrerelease && currentIsPrerelease) continue;
                    
                    String tagName = release.getString("tag_name");
                    
                    if (latestRelease == null || compareVersions(tagName, latestVersion) > 0) {
                        latestRelease = release;
                        latestVersion = tagName;
                        releaseName = release.optString("name", tagName);
                        releaseNotes = release.optString("body", "");
                        isPrerelease = currentIsPrerelease;
                        htmlUrl = release.getString("html_url");
                    }
                }
                
                if (latestRelease == null) {
                    handleNoUpdate(showToast, isManual, isDevelopmentVersion);
                    return;
                }
                
                // 保存获取到的版本信息
                latestVersionFromGitHub = latestVersion;
                releaseNameFromGitHub = releaseName;
                releaseNotesFromGitHub = releaseNotes;
                isPrereleaseFromGitHub = isPrerelease;
                htmlUrlFromGitHub = htmlUrl;
                
                // 检查是否需要更新
                int comparison = compareVersions(currentVersion, latestVersion);
                
                Log.d(TAG, "版本比较: 当前=" + currentVersion + 
                      ", 最新=" + latestVersion + ", 结果=" + comparison + 
                      ", 预发布=" + isPrerelease + ", 开发版本=" + isDevelopmentVersion);
                
                // 创建final变量供lambda使用
                final String finalLatestVersion = latestVersion;
                final String finalReleaseName = releaseName;
                final String finalReleaseNotes = releaseNotes;
                final boolean finalIsPrerelease = isPrerelease;
                final String finalHtmlUrl = htmlUrl;
                final int finalComparison = comparison;
                final boolean finalIsDevelopmentVersion = isDevelopmentVersion;
                
                if (comparison < 0) {
                    // 有新版本可用
                    if (!isVersionIgnored(latestVersion)) {
                        // 不显示弹窗，而是通知回调
                        if (updateCheckListener != null) {
                            mainHandler.post(() ->
                                updateCheckListener.onUpdateCheckComplete(
                                    finalComparison, finalLatestVersion, finalReleaseName,
                                    finalReleaseNotes,
                                    finalIsPrerelease, finalIsDevelopmentVersion, finalHtmlUrl));
                        }
                    } else {
                        Log.d(TAG, "版本 " + latestVersion + " 已被用户忽略");
                        if (showToast && isManual) {
                            mainHandler.post(() -> 
                                ToastUtils.show(context, context.getString(R.string.update_ignored_version)));
                        }
                    }
                } else {
                    // 通知检查结果
                    if (updateCheckListener != null) {
                        mainHandler.post(() -> 
                            updateCheckListener.onUpdateCheckComplete(
                                finalComparison, finalLatestVersion, finalReleaseName,
                                finalReleaseNotes,
                                finalIsPrerelease, finalIsDevelopmentVersion, finalHtmlUrl));
                    }
                    
                    if (showToast && isManual) {
                        String toastMessage = null;
                        // 优先判断是否为开发版本
                        if (finalIsDevelopmentVersion) {
                            toastMessage = context.getString(R.string.update_dev_version);
                        } else if (finalComparison == 0) {
                            toastMessage = finalIsPrerelease ? 
                                context.getString(R.string.update_latest_prerelease) : context.getString(R.string.update_latest);
                        } else if (finalComparison > 0) {
                            toastMessage = context.getString(R.string.update_newer_than_github);
                        } else {
                            // finalComparison < 0
                            toastMessage = context.getString(R.string.update_new_version_found);
                        }
                        if (toastMessage != null) {
                            final String finalToastMessage = toastMessage;
                            mainHandler.post(() -> 
                                ToastUtils.show(context, finalToastMessage));
                        }
                    }
                }
                
                // 保存检查时间
                saveCheckTime();
                
            } catch (Exception e) {
                Log.e(TAG, "检查更新失败", e);
                handleError(context.getString(R.string.update_check_failed, e.getMessage()), showToast);
            } finally {
                isChecking = false;
            }
        });
    }
    
    private void handleError(String error, boolean showToast) {
        Log.e(TAG, error);
        isChecking = false;
        
        if (showToast) {
            mainHandler.post(() -> 
                ToastUtils.show(context, error));
        }
        
        if (updateCheckListener != null) {
            mainHandler.post(() -> 
                updateCheckListener.onUpdateCheckError(error));
        }
    }
    
    private void handleNoUpdate(boolean showToast, boolean isManual, boolean isDevelopmentVersion) {
        isChecking = false;
        
        if (updateCheckListener != null) {
            mainHandler.post(() -> 
                updateCheckListener.onNoUpdateAvailable());
        }
        
        if (showToast && isManual) {
            String message = context.getString(R.string.update_no_update_found);
            if (isDevelopmentVersion) {
                message = context.getString(R.string.update_dev_version_alt);
            }
            final String finalMessage = message;
            mainHandler.post(() -> 
                ToastUtils.show(context, finalMessage));
        }
    }
    
    private String getCurrentVersion() {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pInfo = pm.getPackageInfo(context.getPackageName(), 
                    PackageManager.PackageInfoFlags.of(0));
            } else {
                pInfo = pm.getPackageInfo(context.getPackageName(), 0);
            }
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "获取版本信息失败", e);
            return null;
        }
    }
    
    public boolean isDevelopmentVersion(String version) {
        try {
            PackageManager pm = context.getPackageManager();
            android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(
                context.getPackageName(), 0);
            boolean isDebuggable = (appInfo.flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            Log.d(TAG, "应用构建类型: " + (isDebuggable ? "Debug (开发版本)" : "Release (正式版本)"));
            return isDebuggable;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "获取应用信息失败，默认为开发版本", e);
            // 异常时默认视为开发版本
            return true;
        }
    }
    
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = 
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (connectivityManager == null) {
            return false;
        }
        
        // Android 6.0 及以上
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                return false;
            }
            
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } else {
            // 兼容 Android 6.0 以下旧版本(已过时)
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
    }
    
    public void showUpdateDialog(String releaseName, String releaseNotes, 
                                boolean isPrerelease, String htmlUrl) {
        // 使用final变量供内部类使用
        final String finalReleaseName = releaseName;
        final String finalReleaseNotes = releaseNotes;
        final boolean finalIsPrerelease = isPrerelease;
        final String finalHtmlUrl = htmlUrl;
        
        mainHandler.post(() -> {
            if (!(context instanceof Activity)) {
                Log.e(TAG, "上下文不是Activity，无法显示对话框");
                return;
            }
            
            Activity activity = (Activity) context;
            if (activity.isFinishing() || activity.isDestroyed()) {
                Log.e(TAG, "Activity已销毁，无法显示对话框");
                return;
            }
            
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
            
            String title = finalIsPrerelease ? 
                context.getString(R.string.update_dialog_title_prerelease, finalReleaseName) :
                context.getString(R.string.update_dialog_title_stable, finalReleaseName);
            builder.setTitle(title);
            
            TextView messageView = new TextView(activity);
            messageView.setPadding(50, 30, 50, 30);
            messageView.setTextSize(14);
            messageView.setTextColor(ContextCompat.getColor(activity, android.R.color.black));
            messageView.setMovementMethod(LinkMovementMethod.getInstance());
            
            String markdownContent = finalReleaseNotes;
            if (markdownContent == null || markdownContent.trim().isEmpty()) {
                markdownContent = context.getString(R.string.update_dialog_no_notes);
            }
            
            markwon.setMarkdown(messageView, markdownContent);
            
            final int maxHeight = activity.getResources().getDisplayMetrics().heightPixels * 2 / 3;
            messageView.setMaxHeight(maxHeight);
            
            builder.setView(messageView);
            
            builder.setPositiveButton(context.getString(R.string.update_dialog_btn_download), (dialog, which) -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(finalHtmlUrl));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                } catch (Exception e) {
                    ToastUtils.show(activity, context.getString(R.string.update_cannot_open_link));
                }
            });
            
            if (finalIsPrerelease) {
                builder.setNeutralButton(context.getString(R.string.update_dialog_btn_stable_only), (dialog, which) -> {
                    includePrerelease = false;
                    settingsManager.setIncludePrerelease(false);
                    checkForManualUpdate();
                });
            }
            
            builder.setNegativeButton(context.getString(R.string.update_dialog_btn_ignore), (dialog, which) -> {
                ignoreVersion(latestVersionFromGitHub);
                ToastUtils.show(activity, context.getString(R.string.update_ignored_version));
            });
            
            try {
                builder.show();
                
                if (messageView.getLineCount() > 20) {
                    messageView.setVerticalScrollBarEnabled(true);
                    messageView.setScrollbarFadingEnabled(true);
                }
            } catch (Exception e) {
                Log.e(TAG, "显示更新对话框失败", e);
            }
        });
    }
    
    // 版本比较方法
    private int compareVersions(String v1, String v2) {
        try {
            String cleanV1 = v1.replaceFirst("^[vV]", "");
            String cleanV2 = v2.replaceFirst("^[vV]", "");
            
            cleanV1 = normalizeVersion(cleanV1);
            cleanV2 = normalizeVersion(cleanV2);
            
            String[] parts1 = cleanV1.split("-", 2);
            String[] parts2 = cleanV2.split("-", 2);
            
            String mainVersion1 = parts1[0];
            String mainVersion2 = parts2[0];
            
            int mainComparison = compareMainVersions(mainVersion1, mainVersion2);
            if (mainComparison != 0) return mainComparison;
            
            boolean hasPreRelease1 = parts1.length > 1;
            boolean hasPreRelease2 = parts2.length > 1;
            
            if (!hasPreRelease1 && !hasPreRelease2) return 0;
            else if (!hasPreRelease1 && hasPreRelease2) return 1;
            else if (hasPreRelease1 && !hasPreRelease2) return -1;
            else {
                String preRelease1 = parts1[1];
                String preRelease2 = parts2[1];
                return comparePreReleaseIdentifiers(preRelease1, preRelease2);
            }
        } catch (Exception e) {
            Log.e(TAG, "版本比较异常", e);
            return v1.compareTo(v2);
        }
    }
    
    private String normalizeVersion(String version) {
        String[] parts = version.split("\\.");
        if (parts.length >= 4) {
            String lastPart = parts[parts.length - 1].toLowerCase();
            if (lastPart.matches("alpha|beta|rc[0-9]*|preview|pre-release")) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < parts.length - 2; i++) {
                    builder.append(parts[i]).append(".");
                }
                builder.append(parts[parts.length - 2]);
                builder.append("-").append(parts[parts.length - 1]);
                return builder.toString();
            }
        }
        return version;
    }
    
    private int compareMainVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int num1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int num2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            if (num1 != num2) return Integer.compare(num1, num2);
        }
        return 0;
    }
    
    private int parseVersionPart(String part) {
        try {
            String digits = part.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) return Integer.parseInt(digits);
            return 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private int comparePreReleaseIdentifiers(String pre1, String pre2) {
        String[] parts1 = pre1.split("\\.");
        String[] parts2 = pre2.split("\\.");
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            String part1 = i < parts1.length ? parts1[i] : "";
            String part2 = i < parts2.length ? parts2[i] : "";
            int comparison = comparePreReleasePart(part1, part2);
            if (comparison != 0) return comparison;
        }
        return 0;
    }
    
    private int comparePreReleasePart(String part1, String part2) {
        boolean isNum1 = part1.matches("\\d+");
        boolean isNum2 = part2.matches("\\d+");
        if (isNum1 && isNum2) {
            int num1 = Integer.parseInt(part1);
            int num2 = Integer.parseInt(part2);
            return Integer.compare(num1, num2);
        } else if (!isNum1 && !isNum2) {
            return part1.compareTo(part2);
        } else {
            return isNum1 ? -1 : 1;
        }
    }
    
    public void setIncludePrerelease(boolean include) {
        this.includePrerelease = include;
        settingsManager.setIncludePrerelease(include);
    }
    
    /**
     * 清理当前实例资源
     * 仅移除当前实例的 Handler 回调与监听器引用，防止 Activity 内存泄漏
     */
    public void cleanup() {
        mainHandler.removeCallbacksAndMessages(null);
        updateCheckListener = null;
    }
    
     // 用于测试的强制检查（不考虑时间间隔）
    public void forceCheckForUpdate() {
        checkForUpdates(true, true);
    }
    
     // 获取最新的GitHub版本信息
    public String getLatestVersionFromGitHub() {
        return latestVersionFromGitHub;
    }
    
    public String getReleaseNameFromGitHub() {
        return releaseNameFromGitHub;
    }
    
    public String getReleaseNotesFromGitHub() {
        return releaseNotesFromGitHub;
    }
    
    public boolean isPrereleaseFromGitHub() {
        return isPrereleaseFromGitHub;
    }
    
    public String getHtmlUrlFromGitHub() {
        return htmlUrlFromGitHub;
    }
}