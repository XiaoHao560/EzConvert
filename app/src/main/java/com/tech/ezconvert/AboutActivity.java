package com.tech.ezconvert;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.noties.markwon.html.HtmlPlugin;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AboutActivity extends AppCompatActivity {
    
    private TextView versionText;
    private TextView updateStatusText;
    private LinearLayout testUpdateItem;
    private LinearLayout testUpdateClickArea;
    private boolean includePrereleases = true; // 是否包含预发布版本
    private boolean isDevelopmentVersion = false; // 是否为开发版本
    
    private Markwon markwon;
    
    // 存储从GitHub获取的最新版本信息，用于测试
    private String latestVersionFromGitHub;
    private String releaseNameFromGitHub;
    private String releaseNotesFromGitHub;
    private boolean isPrereleaseFromGitHub;
    private String htmlUrlFromGitHub;
    
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        
        initMarkwon();
        
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        
        initializeViews();
        setupClickListeners();
        loadVersionInfo();
        checkForUpdates();
    }
    
    private void initMarkwon() {
        markwon = Markwon.builder(this)
            .usePlugin(LinkifyPlugin.create()) // 自动链接
            .usePlugin(StrikethroughPlugin.create()) // 删除线
            .usePlugin(TablePlugin.create(this)) // 表格支持
            .usePlugin(HtmlPlugin.create()) // HTML支持
            .build();
    }
    
    private void initializeViews() {
        versionText = findViewById(R.id.version_text);
        updateStatusText = findViewById(R.id.update_status_text);
        testUpdateItem = findViewById(R.id.test_update_item);
        testUpdateClickArea = findViewById(R.id.test_update_click_area);
    }
    
    private void setupClickListeners() {
        // 源代码
        LinearLayout sourceCodeItem = findViewById(R.id.source_code_item);
        sourceCodeItem.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, 
                    Uri.parse("https://github.com/XiaoHao560/EzConvert"));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show();
            }
        });
        
        // 问题反馈
        LinearLayout feedbackItem = findViewById(R.id.feedback_item);
        feedbackItem.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/XiaoHao560/EzConvert/issues"));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show();
            }
        });
        
        // 检查更新
        LinearLayout checkUpdateItem = findViewById(R.id.check_update_item);
        checkUpdateItem.setOnClickListener(v -> {
            Toast.makeText(this, "正在检查更新...", Toast.LENGTH_SHORT).show();
            updateStatusText.setText("正在检查...");
            updateStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            checkForUpdates();
        });
        
        // 开源许可证
        LinearLayout licenseItem = findViewById(R.id.license_item);
        licenseItem.setOnClickListener(v -> {
            Toast.makeText(this, "EzConvert 使用 GPLv3 开源许可证", Toast.LENGTH_LONG).show();
        });
        
        // 测试更新
        testUpdateClickArea.setOnClickListener(v -> {
            if (latestVersionFromGitHub != null) {
                showTestUpdateDialog(releaseNameFromGitHub, releaseNotesFromGitHub, 
                    isPrereleaseFromGitHub, htmlUrlFromGitHub);
            } else {
                Toast.makeText(this, "请先检查更新以获取版本信息", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void loadVersionInfo() {
        try {
            PackageInfo pInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.PackageInfoFlags.of(0));
            } else {
                // 兼容旧版本
                pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            }
            String version = pInfo.versionName;
            versionText.setText("版本 " + version);
            
            // 开发版本检测逻辑
            isDevelopmentVersion = isDevelopmentVersion(version);
            
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText("版本 0.0.0");
            isDevelopmentVersion = true; // 默认为开发版本
        }
    }
    
    private boolean isDevelopmentVersion(String version) {
        // 移除版本号前面的 'v' 或 'V'
        String cleanVersion = version.replaceFirst("^[vV]", "").toLowerCase();
        
        // 判断是否为开发版本的逻辑：
        // 1. 包含分支名（feat/, fix/, hotfix/, chore/, docs/, style/, refactor/, test/等）
        if (cleanVersion.matches(".*(feat|fix|hotfix|chore|docs|style|refactor|test)[\\-/].*")) {
            return true;
        }
        
        // 2. 包含开发环境标识
        if (cleanVersion.contains("dev") || 
            cleanVersion.contains("snapshot") ||
            cleanVersion.contains("nightly") ||
            cleanVersion.contains("local")) {
            return true;
        }
        
        // 3. 特殊的开发分支模式
        if (cleanVersion.matches(".*\\d+-g[0-9a-f]+$")) { // Git描述格式，如 1.0.0-1-gabc123
            return true;
        }
        
        // 4. 检查是否包含构建号等非标准部分
        String[] parts = cleanVersion.split("\\.");
        if (parts.length > 3) {
            // 如果有超过3个部分，可能是开发版本（如 0.5.2.1）
            return true;
        }
        
        // 默认不是开发版本
        return false;
    }
    
    private void checkForUpdates() {
        executorService.execute(() -> {
            try {
                // GitHub API获取所有发布版本（包括预发布）
                URL url = new URL("https://api.github.com/repos/XiaoHao560/EzConvert/releases");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                
                int responseCode = conn.getResponseCode();
                
                if (responseCode == 404) {
                    // 仓库不存在或没有访问权限
                    mainHandler.post(() -> {
                        updateStatusText.setText("仓库未找到");
                        updateStatusText.setTextColor(ContextCompat.getColor(AboutActivity.this, android.R.color.holo_red_dark));
                    });
                    return;
                }
                
                if (responseCode == 200 || responseCode == 201) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    JSONArray releasesArray = new JSONArray(response.toString());
                    
                    if (releasesArray.length() == 0) {
                        mainHandler.post(() -> {
                            updateStatusText.setText("尚未发布任何版本");
                            updateStatusText.setTextColor(ContextCompat.getColor(AboutActivity.this, android.R.color.darker_gray));
                        });
                        return;
                    }
                    
                    // 查找最新版本（包括预发布）
                    JSONObject latestRelease = null;
                    String latestVersion = null;
                    String releaseName = null;
                    String releaseNotes = null;
                    boolean isPrerelease = false;
                    String htmlUrl = null;
                    String publishedAt = null;
                    
                    for (int i = 0; i < releasesArray.length(); i++) {
                        JSONObject release = releasesArray.getJSONObject(i);
                        boolean currentIsPrerelease = release.getBoolean("prerelease");
                        boolean isDraft = release.getBoolean("draft");
                        
                        // 跳过草稿版本
                        if (isDraft) {
                            continue;
                        }
                        
                        // 如果不包含预发布版本且当前版本是预发布，则跳过
                        if (!includePrereleases && currentIsPrerelease) {
                            continue;
                        }
                        
                        // 获取版本号（tag_name）
                        String tagName = release.getString("tag_name");
                        
                        // 如果这是找到的第一个有效版本，或者找到更合适的版本
                        if (latestRelease == null) {
                            latestRelease = release;
                            latestVersion = tagName;
                            releaseName = release.optString("name", tagName);
                            releaseNotes = release.optString("body", "");
                            isPrerelease = currentIsPrerelease;
                            htmlUrl = release.getString("html_url");
                            publishedAt = release.getString("published_at");
                        } else {
                            // 比较当前版本和已找到的最新版本
                            String currentTagName = tagName;
                            if (compareVersions(currentTagName, latestVersion) > 0) {
                                latestRelease = release;
                                latestVersion = currentTagName;
                                releaseName = release.optString("name", currentTagName);
                                releaseNotes = release.optString("body", "");
                                isPrerelease = currentIsPrerelease;
                                htmlUrl = release.getString("html_url");
                                publishedAt = release.getString("published_at");
                            }
                        }
                    }
                    
                    if (latestRelease != null) {
                        // 保存获取到的版本信息，用于测试功能
                        latestVersionFromGitHub = latestVersion;
                        releaseNameFromGitHub = releaseName;
                        releaseNotesFromGitHub = releaseNotes;
                        isPrereleaseFromGitHub = isPrerelease;
                        htmlUrlFromGitHub = htmlUrl;
                        
                        final String finalLatestVersion = latestVersion;
                        final String finalReleaseName = releaseName;
                        final String finalReleaseNotes = releaseNotes;
                        final boolean finalIsPrerelease = isPrerelease;
                        final String finalHtmlUrl = htmlUrl;
                        final String finalPublishedAt = publishedAt;
                        
                        // 回到主线程处理结果
                        mainHandler.post(() -> handleUpdateResult(finalLatestVersion, finalReleaseName, 
                            finalReleaseNotes, finalIsPrerelease, finalHtmlUrl, finalPublishedAt));
                    } else {
                        mainHandler.post(() -> {
                            updateStatusText.setText("没有找到符合条件的版本");
                            updateStatusText.setTextColor(ContextCompat.getColor(AboutActivity.this, android.R.color.darker_gray));
                        });
                    }
                } else {
                    mainHandler.post(() -> {
                        updateStatusText.setText("API错误: " + responseCode);
                        updateStatusText.setTextColor(ContextCompat.getColor(AboutActivity.this, android.R.color.holo_red_dark));
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    updateStatusText.setText("网络错误");
                    updateStatusText.setTextColor(ContextCompat.getColor(AboutActivity.this, android.R.color.holo_red_dark));
                });
            }
        });
    }
    
    private void handleUpdateResult(String latestVersion, String releaseName, 
                                   String releaseNotes, boolean isPrerelease,
                                   String htmlUrl, String publishedAt) {
        try {
            PackageInfo pInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.PackageInfoFlags.of(0));
            } else {
                // 兼容旧版本
                pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            }
            String currentVersion = pInfo.versionName;
            
            int comparison = compareVersions(latestVersion, currentVersion);
            
            if (comparison > 0) {
                // 有新版本可用
                String statusText = "有新版本 " + latestVersion;
                if (isPrerelease) {
                    statusText += " (预发布)";
                }
                updateStatusText.setText(statusText);
                updateStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                
                // 点击查看更新详情
                updateStatusText.setOnClickListener(v -> {
                    showUpdateDialog(releaseName, releaseNotes, isPrerelease, htmlUrl);
                });
                
                // 如果是开发版本，显示测试选项
                if (isDevelopmentVersion) {
                    testUpdateItem.setVisibility(View.VISIBLE);
                }
            } else if (comparison == 0) {
                // 版本相同
                String statusText = "已是最新版本";
                if (isPrerelease) {
                    statusText += " (预发布)";
                }
                updateStatusText.setText(statusText);
                updateStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                
                // 如果是开发版本，显示测试选项
                if (isDevelopmentVersion) {
                    testUpdateItem.setVisibility(View.VISIBLE);
                }
            } else {
                // 当前版本比找到的版本更新（可能是开发版本）
                updateStatusText.setText("当前为开发版本");
                updateStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
                
                // 显示测试更新选项
                if (isDevelopmentVersion) {
                    testUpdateItem.setVisibility(View.VISIBLE);
                }
            }
        } catch (Exception e) {
            updateStatusText.setText("版本检查异常");
            updateStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        }
    }
    
    private int compareVersions(String v1, String v2) {
        try {
            // 移除版本号前面的 'v' 或 'V'
            String cleanV1 = v1.replaceFirst("^[vV]", "");
            String cleanV2 = v2.replaceFirst("^[vV]", "");
            
            // 分割版本号为数字部分和预发布标识部分
            String[] parts1 = cleanV1.split("-", 2);
            String[] parts2 = cleanV2.split("-", 2);
            
            // 获取主版本号部分
            String mainVersion1 = parts1[0];
            String mainVersion2 = parts2[0];
            
            // 比较主版本号
            int mainComparison = compareMainVersions(mainVersion1, mainVersion2);
            if (mainComparison != 0) {
                return mainComparison;
            }
            
            // 如果主版本号相同，比较预发布标识
            boolean hasPreRelease1 = parts1.length > 1;
            boolean hasPreRelease2 = parts2.length > 1;
            
            if (!hasPreRelease1 && !hasPreRelease2) {
                return 0; // 都是正式版
            } else if (!hasPreRelease1 && hasPreRelease2) {
                return 1; // v1是正式版，v2是预发布版，正式版 > 预发布版
            } else if (hasPreRelease1 && !hasPreRelease2) {
                return -1; // v1是预发布版，v2是正式版，预发布版 < 正式版
            } else {
                // 都是预发布版，比较预发布标识
                String preRelease1 = parts1[1];
                String preRelease2 = parts2[1];
                return comparePreReleaseIdentifiers(preRelease1, preRelease2);
            }
        } catch (Exception e) {
            // 如果版本号格式异常，使用字符串比较
            return v1.compareTo(v2);
        }
    }
    
    private int compareMainVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int num1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int num2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }
        return 0;
    }
    
    private int parseVersionPart(String part) {
        try {
            // 移除非数字字符，只解析数字部分
            String digits = part.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                return Integer.parseInt(digits);
            }
            return 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private int comparePreReleaseIdentifiers(String pre1, String pre2) {
        // 分割预发布标识为多个部分（按点号分割）
        String[] parts1 = pre1.split("\\.");
        String[] parts2 = pre2.split("\\.");
        
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            String part1 = i < parts1.length ? parts1[i] : "";
            String part2 = i < parts2.length ? parts2[i] : "";
            
            int comparison = comparePreReleasePart(part1, part2);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }
    
    private int comparePreReleasePart(String part1, String part2) {
        boolean isNum1 = part1.matches("\\d+");
        boolean isNum2 = part2.matches("\\d+");
        
        if (isNum1 && isNum2) {
            // 都是数字，按数字比较
            int num1 = Integer.parseInt(part1);
            int num2 = Integer.parseInt(part2);
            return Integer.compare(num1, num2);
        } else if (!isNum1 && !isNum2) {
            // 都不是数字，按字符串比较
            return part1.compareTo(part2);
        } else {
            // 一个是数字，一个是字符串，数字 < 字符串
            return isNum1 ? -1 : 1;
        }
    }
    
    private void showUpdateDialog(String releaseName, String releaseNotes, 
                                 boolean isPrerelease, String htmlUrl) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        
        String title = isPrerelease ? "预发布版本: " + releaseName : "更新详情: " + releaseName;
        builder.setTitle(title);
        
        TextView messageView = new TextView(this);
        messageView.setPadding(50, 30, 50, 30);
        messageView.setTextSize(14);
        messageView.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        messageView.setMovementMethod(LinkMovementMethod.getInstance()); // 链接点击
        
        // 处理空发布说明的情况
        String markdownContent = releaseNotes;
        if (markdownContent == null || markdownContent.trim().isEmpty()) {
            markdownContent = "暂无更新说明";
        }
        
        // 渲染Markdown
        markwon.setMarkdown(messageView, markdownContent);
        
        // 限制最大高度，避免对话框过长
        final int maxHeight = getResources().getDisplayMetrics().heightPixels * 2 / 3;
        messageView.setMaxHeight(maxHeight);
        
        builder.setView(messageView);
        
        builder.setPositiveButton("前往GitHub", (dialog, which) -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show();
            }
        });
        
        if (isPrerelease) {
            builder.setNeutralButton("仅检查正式版", (dialog, which) -> {
                includePrereleases = false;
                updateStatusText.setText("正在检查...");
                testUpdateItem.setVisibility(View.GONE); // 隐藏测试选项
                checkForUpdates();
            });
        }
        
        builder.setNegativeButton("关闭", null);
        
        try {
            androidx.appcompat.app.AlertDialog dialog = builder.show();
            
            // 对话框滑动功能
            if (messageView.getLineCount() > 20) {
                messageView.setVerticalScrollBarEnabled(true);
                messageView.setScrollbarFadingEnabled(true);
            }
        } catch (Exception e) {
            
        }
    }
    
    private void showTestUpdateDialog(String releaseName, String releaseNotes, 
                                     boolean isPrerelease, String htmlUrl) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        
        String title = "[测试] " + (isPrerelease ? "预发布版本: " + releaseName : "更新详情: " + releaseName);
        builder.setTitle(title);
        
        TextView messageView = new TextView(this);
        messageView.setPadding(50, 30, 50, 30);
        messageView.setTextSize(14);
        messageView.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        messageView.setMovementMethod(LinkMovementMethod.getInstance()); // 链接点击
        
        // 处理空发布说明的情况
        String markdownContent = releaseNotes;
        if (markdownContent == null || markdownContent.trim().isEmpty()) {
            markdownContent = "暂无更新说明";
        }
        
        // 测试提示
        markdownContent = "## ⚠️ 测试更新对话框\n\n*仅用于开发版本测试*\n\n" + markdownContent;
        
        // 渲染Markdown
        markwon.setMarkdown(messageView, markdownContent);
        
        // 限制最大高度，避免对话框过长
        final int maxHeight = getResources().getDisplayMetrics().heightPixels * 2 / 3;
        messageView.setMaxHeight(maxHeight);
        
        builder.setView(messageView);
        
        builder.setPositiveButton("测试前往GitHub", (dialog, which) -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNeutralButton("重新检查", (dialog, which) -> {
            updateStatusText.setText("正在检查...");
            checkForUpdates();
        });
        
        builder.setNegativeButton("关闭", null);
        
        try {
            androidx.appcompat.app.AlertDialog dialog = builder.show();
            
            // 对话框滑动功能
            if (messageView.getLineCount() > 20) {
                messageView.setVerticalScrollBarEnabled(true);
                messageView.setScrollbarFadingEnabled(true);
            }
        } catch (Exception e) {
            
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 关闭线程池
        if (!executorService.isShutdown()) {
            executorService.shutdown();
        }
        // 移除Handler中的回调
        mainHandler.removeCallbacksAndMessages(null);
    }
    
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}