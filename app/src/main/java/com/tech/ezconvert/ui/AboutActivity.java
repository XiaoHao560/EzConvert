package com.tech.ezconvert.ui;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.UpdateChecker;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

public class AboutActivity extends AppCompatActivity implements UpdateChecker.UpdateCheckListener {
    
    private static final String TAG = "AboutActivity";
    private TextView versionText;
    private TextView updateStatusText;
    private LinearLayout testUpdateItem;
    private LinearLayout testUpdateClickArea;
    private boolean includePrereleases = true;
    private boolean isDevelopmentVersion = false;
    
    private Markwon markwon;
    private UpdateChecker updateChecker;
    
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
        
        // 初始化更新检查器
        updateChecker = new UpdateChecker(this);
        updateChecker.setUpdateCheckListener(this);
        updateChecker.setIncludePrereleases(includePrereleases);
        
        // 初始化检查更新
        checkForUpdates();
    }
    
    private void initMarkwon() {
        markwon = Markwon.builder(this)
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(this))
            .usePlugin(HtmlPlugin.create())
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
            if (updateChecker.getLatestVersionFromGitHub() != null) {
                showTestUpdateDialog(
                    updateChecker.getReleaseNameFromGitHub(), 
                    updateChecker.getReleaseNotesFromGitHub(), 
                    updateChecker.isPrereleaseFromGitHub(), 
                    updateChecker.getHtmlUrlFromGitHub()
                );
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
                pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            }
            String version = pInfo.versionName;
            versionText.setText("版本 " + version);
            
            // 开发版本检测逻辑
            isDevelopmentVersion = updateChecker != null && updateChecker.isDevelopmentVersion(version);
            
            Log.d(TAG, "版本号: " + version + ", 是否为开发版本: " + isDevelopmentVersion);
            
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText("版本 0.0.0");
            isDevelopmentVersion = true;
        }
    }
    
    private void checkForUpdates() {
        if (updateChecker != null) {
            updateChecker.checkForManualUpdate();
        }
    }
    
    // UpdateCheckListener 实现
    @Override
    public void onUpdateCheckComplete(int comparisonResult, String latestVersion, 
                                     String releaseName, boolean isPrerelease, 
                                     boolean isDevelopmentVersion, String htmlUrl) {
        runOnUiThread(() -> {
            if (comparisonResult < 0) {
                // 有新版本可用
                String statusText = "有新版本 " + latestVersion;
                if (isPrerelease) {
                    statusText += " (预发布)";
                }
                updateStatusText.setText(statusText);
                updateStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                
                // 点击查看更新详情
                updateStatusText.setOnClickListener(v -> {
                    showUpdateDialog(releaseName, "", isPrerelease, htmlUrl);
                });
                
                // 如果是开发版本，显示测试选项
                if (isDevelopmentVersion) {
                    testUpdateItem.setVisibility(View.VISIBLE);
                } else {
                    testUpdateItem.setVisibility(View.GONE);
                }
            } else if (comparisonResult == 0) {
                // 版本相同
                String statusText = "已是最新版本";
                if (isPrerelease) {
                    statusText += " (预发布)";
                }
                updateStatusText.setText(statusText);
                updateStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                updateStatusText.setOnClickListener(null);
                
                // 是开发版本时则显示测试选项
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
                updateStatusText.setOnClickListener(null);
                
                // 显示测试更新选项
                if (isDevelopmentVersion) {
                    testUpdateItem.setVisibility(View.VISIBLE);
                } else {
                    testUpdateItem.setVisibility(View.GONE);
                }
            }
        });
    }
    
    @Override
    public void onUpdateCheckError(String errorMessage) {
        runOnUiThread(() -> {
            updateStatusText.setText("检查失败");
            updateStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            updateStatusText.setOnClickListener(null);
            testUpdateItem.setVisibility(View.GONE);
        });
    }
    
    @Override
    public void onNoUpdateAvailable() {
        runOnUiThread(() -> {
            updateStatusText.setText("没有可用更新");
            updateStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            updateStatusText.setOnClickListener(null);
            
            // 如果是开发版本，显示测试选项
            if (isDevelopmentVersion) {
                testUpdateItem.setVisibility(View.VISIBLE);
            } else {
                testUpdateItem.setVisibility(View.GONE);
            }
        });
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
        messageView.setMovementMethod(LinkMovementMethod.getInstance());
        
        String markdownContent = releaseNotes;
        if (markdownContent == null || markdownContent.trim().isEmpty()) {
            markdownContent = "暂无更新说明";
        }
        
        markwon.setMarkdown(messageView, markdownContent);
        
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
                if (updateChecker != null) {
                    updateChecker.setIncludePrereleases(false);
                }
                updateStatusText.setText("正在检查...");
                testUpdateItem.setVisibility(View.GONE);
                checkForUpdates();
            });
        }
        
        builder.setNegativeButton("关闭", null);
        
        try {
            androidx.appcompat.app.AlertDialog dialog = builder.show();
            
            if (messageView.getLineCount() > 20) {
                messageView.setVerticalScrollBarEnabled(true);
                messageView.setScrollbarFadingEnabled(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "显示更新对话框失败", e);
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
        messageView.setMovementMethod(LinkMovementMethod.getInstance());
        
        String markdownContent = releaseNotes;
        if (markdownContent == null || markdownContent.trim().isEmpty()) {
            markdownContent = "暂无更新说明";
        }
        
        markdownContent = "## ⚠️ 测试更新对话框\n\n*仅用于开发版本测试*\n\n" + markdownContent;
        
        markwon.setMarkdown(messageView, markdownContent);
        
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
            
            if (messageView.getLineCount() > 20) {
                messageView.setVerticalScrollBarEnabled(true);
                messageView.setScrollbarFadingEnabled(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "显示测试更新对话框失败", e);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 关闭线程池
        if (updateChecker != null) {
            updateChecker.cleanup();
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