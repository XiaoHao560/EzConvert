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
import android.util.Log;
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
import java.util.Arrays;

public class AboutActivity extends AppCompatActivity {
    
    private static final String TAG = "AboutActivity";
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
            
            Log.d(TAG, "版本号: " + version + ", 是否为开发版本: " + isDevelopmentVersion);
            
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText("版本 0.0.0");
            isDevelopmentVersion = true; // 默认为开发版本
        }
    }
    
    private boolean isDevelopmentVersion(String version) {
        // 移除版本号前面的 'v' 或 'V'
        String cleanVersion = version.replaceFirst("^[vV]", "").toLowerCase();
        
        Log.d(TAG, "检查版本: " + cleanVersion + " 是否为开发版本");
        
       /*
        1. 首先检查是否为标准的预发布版本（无论是 .alpha 还是 -alpha 格式）
        标准的预发布版本：alpha, beta, rc, preview, pre-release 等
        支持两种格式：0.0.0.alpha（点分隔）和 0.0.0-alpha（横杠分隔）
       */
       
        // 检查是否为标准的预发布版本标识
        String[] versionParts = cleanVersion.split("\\.");
        boolean isStandardPrerelease = false;
        
        // 检查最后一部分是否是标准的预发布标识
        if (versionParts.length > 0) {
            String lastPart = versionParts[versionParts.length - 1].toLowerCase();
            // 检查标准预发布标识
            if (lastPart.matches("alpha|beta|rc[0-9]*|preview|pre-release")) {
                isStandardPrerelease = true;
            }
            
            // 检查是否有横杠分隔的预发布标识
            for (String part : versionParts) {
                if (part.contains("-")) {
                    String[] subParts = part.split("-");
                    if (subParts.length >= 2) {
                        String prereleasePart = subParts[1].toLowerCase();
                        if (prereleasePart.matches("alpha|beta|rc[0-9]*|preview|pre-release")) {
                            isStandardPrerelease = true;
                        }
                    }
                }
            }
        }
        
        // 如果是标准的预发布版本，先检查是否包含分支信息
        if (isStandardPrerelease) {
            Log.d(TAG, "检测到标准预发布版本标识");
            
            // 检查是否包含分支信息（feat/, fix/, hotfix/, chore/, docs/, style/, refactor/, test/等）
            if (cleanVersion.matches(".*(feat|fix|hotfix|chore|docs|style|refactor|test)[\\-/].*")) {
                Log.d(TAG, "包含分支信息，判定为开发版本");
                return true;
            }
            
            // 检查预发布版本是否包含额外的分支信息
            // 例如：1.0.0.alpha-feat-new-ui 或 1.0.0-alpha-feat-new-ui
            if (cleanVersion.contains("-")) {
                String[] dashParts = cleanVersion.split("-");
                // 如果有多个"-"，说明有额外的分支信息
                if (dashParts.length > 2) {
                    Log.d(TAG, "预发布版本包含分支信息，判定为开发版本");
                    return true;
                }
                
                // 如果只有一个"-"，检查是否包含分支关键词
                if (dashParts.length == 2) {
                    String secondPart = dashParts[1].toLowerCase();
                    // 检查第二部分是否包含分支关键词
                    if (secondPart.matches(".*(feat|fix|hotfix|chore|docs|style|refactor|test|dev|local|feature|branch).*")) {
                        Log.d(TAG, "预发布标识包含分支关键词，判定为开发版本");
                        return true;
                    }
                }
            }
            
            // 标准的预发布版本，不是开发版本
            Log.d(TAG, "标准预发布版本，不是开发版本");
            return false;
        }
        
        // 2. 检查是否包含分支信息（feat/, fix/, hotfix/, chore/, docs/, style/, refactor/, test/等）
        if (cleanVersion.matches(".*(feat|fix|hotfix|chore|docs|style|refactor|test)[\\-/].*")) {
            Log.d(TAG, "包含分支信息，判定为开发版本");
            return true;
        }
        
        // 3. 检查是否包含开发环境特定标识（这些是开发版本）
        if (cleanVersion.contains("dev") || 
            cleanVersion.contains("nightly") ||
            cleanVersion.contains("local") ||
            cleanVersion.contains("feature") ||
            cleanVersion.contains("branch")) {
            Log.d(TAG, "包含开发环境标识，判定为开发版本");
            return true;
        }
        
        // 4. 检查是否包含Git提交哈希（Git描述格式）
        if (cleanVersion.matches(".*\\d+-g[0-9a-f]+$")) { // Git描述格式，如 1.0.0-1-gabc123
            Log.d(TAG, "包含Git提交哈希，判定为开发版本");
            return true;
        }
        
        // 5. snapshot 标识（这是开发版本）
        if (cleanVersion.contains("snapshot")) {
            Log.d(TAG, "包含snapshot标识，判定为开发版本");
            return true;
        }
        
        // 6. 检查版本号部分数量
        // 先移除可能的预发布标识部分
        String baseVersion = cleanVersion.split("-")[0];
        String[] parts = baseVersion.split("\\.");
        
        // 如果主版本号部分超过3个，可能是开发版本（如 0.5.2.1）
        if (parts.length > 3) {
            Log.d(TAG, "版本号部分超过3个，判定为开发版本");
            return true;
        }
        
        // 默认不是开发版本
        Log.d(TAG, "未匹配到开发版本特征，判定为非开发版本");
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
                        
                        Log.d(TAG, "找到最新版本: " + latestVersion + ", 是否为预发布: " + isPrerelease);
                        
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
            
            int comparison = compareVersions(currentVersion, latestVersion);
            
            Log.d(TAG, "版本比较结果: 本地=" + currentVersion + ", GitHub=" + latestVersion + ", 比较结果=" + comparison);
            Log.d(TAG, "当前版本是否为开发版本: " + isDevelopmentVersion);
            Log.d(TAG, "GitHub版本是否为预发布: " + isPrerelease);
            
            if (comparison < 0) {
                // 有新版本可用（本地版本 < GitHub版本）
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
                } else {
                    testUpdateItem.setVisibility(View.GONE);
                }
            } else if (comparison == 0) {
                // 版本相同
                String statusText = "已是最新版本";
                if (isPrerelease) {
                    statusText += " (预发布)";
                }
                updateStatusText.setText(statusText);
                updateStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                updateStatusText.setOnClickListener(null); // 移除点击事件
                
                // 只有在确实是开发版本时才显示测试选项
                if (isDevelopmentVersion) {
                    testUpdateItem.setVisibility(View.VISIBLE);
                    Log.d(TAG, "显示测试更新按钮（开发版本）");
                } else {
                    testUpdateItem.setVisibility(View.GONE);
                }
            } else {
                // 当前版本比找到的版本更新（本地版本 > GitHub版本，可能是开发版本）
                updateStatusText.setText("当前为开发版本");
                updateStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
                updateStatusText.setOnClickListener(null); // 移除点击事件
                
                // 显示测试更新选项
                if (isDevelopmentVersion) {
                    testUpdateItem.setVisibility(View.VISIBLE);
                } else {
                    testUpdateItem.setVisibility(View.GONE);
                }
            }
        } catch (Exception e) {
            updateStatusText.setText("版本检查异常");
            updateStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            updateStatusText.setOnClickListener(null); // 移除点击事件
        }
    }
    
    private int compareVersions(String v1, String v2) {
        try {
            // 移除版本号前面的 'v' 或 'V'
            String cleanV1 = v1.replaceFirst("^[vV]", "");
            String cleanV2 = v2.replaceFirst("^[vV]", "");
            
            Log.d(TAG, "比较版本: " + cleanV1 + " vs " + cleanV2);
            
            // 特殊处理点分隔的预发布版本（如 0.0.0.alpha -> 0.0.0-alpha）
            // 这样可以让 0.0.0.alpha 和 0.0.0-alpha 正确比较
            cleanV1 = normalizeVersion(cleanV1);
            cleanV2 = normalizeVersion(cleanV2);
            
            // 分割版本号为数字部分和预发布标识部分
            String[] parts1 = cleanV1.split("-", 2);
            String[] parts2 = cleanV2.split("-", 2);
            
            // 获取主版本号部分
            String mainVersion1 = parts1[0];
            String mainVersion2 = parts2[0];
            
            // 比较主版本号
            int mainComparison = compareMainVersions(mainVersion1, mainVersion2);
            if (mainComparison != 0) {
                Log.d(TAG, "主版本比较结果: " + mainComparison);
                return mainComparison;
            }
            
            // 如果主版本号相同，比较预发布标识
            boolean hasPreRelease1 = parts1.length > 1;
            boolean hasPreRelease2 = parts2.length > 1;
            
            Log.d(TAG, "预发布标识检查: v1=" + hasPreRelease1 + ", v2=" + hasPreRelease2);
            
            if (!hasPreRelease1 && !hasPreRelease2) {
                Log.d(TAG, "都是正式版，返回0");
                return 0; // 都是正式版
            } else if (!hasPreRelease1 && hasPreRelease2) {
                Log.d(TAG, "v1是正式版，v2是预发布版，返回1");
                return 1; // v1是正式版，v2是预发布版，正式版 > 预发布版
            } else if (hasPreRelease1 && !hasPreRelease2) {
                Log.d(TAG, "v1是预发布版，v2是正式版，返回-1");
                return -1; // v1是预发布版，v2是正式版，预发布版 < 正式版
            } else {
                // 都是预发布版，比较预发布标识
                String preRelease1 = parts1[1];
                String preRelease2 = parts2[1];
                int prereleaseComparison = comparePreReleaseIdentifiers(preRelease1, preRelease2);
                Log.d(TAG, "预发布标识比较结果: " + prereleaseComparison);
                return prereleaseComparison;
            }
        } catch (Exception e) {
            Log.e(TAG, "版本比较异常: " + e.getMessage());
            // 如果版本号格式异常，使用字符串比较
            return v1.compareTo(v2);
        }
    }
    
    private String normalizeVersion(String version) {
        
        /*
         将点分隔的预发布版本转换为横杠分隔，便于比较
         例如: 0.0.0.alpha -> 0.0.0-alpha
         例如: 0.0.0.beta -> 0.0.0-beta
        */
        
        String[] parts = version.split("\\.");
        if (parts.length >= 4) {
            // 检查最后一部分是否为预发布标识
            String lastPart = parts[parts.length - 1].toLowerCase();
            if (lastPart.matches("alpha|beta|rc[0-9]*|preview|pre-release")) {
                // 重建版本号
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