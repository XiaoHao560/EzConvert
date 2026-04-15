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
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.ToastUtils;
import com.tech.ezconvert.utils.UpdateChecker;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

public class AboutActivity extends BaseActivity implements UpdateChecker.UpdateCheckListener {

    @Override
    protected int getTitleContainerId() {
        return R.id.title_container;
    }
    
    @Override
    protected int getScrollContentId() {
        return R.id.scroll_content;
    }
    
    private static final String TAG = "AboutActivity";
    private TextView versionText;
    private TextView updateStatusText;
    private LinearLayout testUpdateItem;
    private LinearLayout testUpdateClickArea;
    private LinearLayout forceCheckUpdateItem;
    private LinearLayout forceCheckUpdateClickArea;
    private LinearLayout developerItem;
    private MaterialToolbar toolbar;
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
        setupToolbar();
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
            .usePlugin(io.noties.markwon.linkify.LinkifyPlugin.create())
            .usePlugin(io.noties.markwon.ext.strikethrough.StrikethroughPlugin.create())
            .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(this))
            .usePlugin(io.noties.markwon.html.HtmlPlugin.create())
            .build();
    }
    
    private void initializeViews() {
        toolbar = findViewById(R.id.title_container);
        versionText = findViewById(R.id.version_text);
        updateStatusText = findViewById(R.id.update_status_text);
        testUpdateItem = findViewById(R.id.test_update_item);
        testUpdateClickArea = findViewById(R.id.test_update_click_area);
        forceCheckUpdateItem = findViewById(R.id.force_check_update_item);
        forceCheckUpdateClickArea = findViewById(R.id.force_check_update_click_area);
        developerItem = findViewById(R.id.developer_item);
    }
    
    // 设置 Toolbar 返回按钮
    private void setupToolbar() {
        toolbar.setNavigationOnClickListener(v -> finish());
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
                ToastUtils.show(this, "无法打开链接");
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
                ToastUtils.show(this, "无法打开链接");
            }
        });
        
        // 检查更新
        LinearLayout checkUpdateItem = findViewById(R.id.check_update_item);
        checkUpdateItem.setOnClickListener(v -> {
            ToastUtils.show(this, "正在检查更新...");
            updateStatusText.setText("正在检查...");
            updateStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            checkForUpdates();
        });
        
        // 开源许可证
        LinearLayout licenseItem = findViewById(R.id.license_item);
        licenseItem.setOnClickListener(v -> {
            ToastUtils.showLong(this, "EzConvert 使用 GPLv3 开源许可证");
        });
        
        // 测试更新（强制拉取最新版）
        testUpdateClickArea.setOnClickListener(v -> {
            if (updateChecker.getLatestVersionFromGitHub() != null) {
                showTestUpdateDialog(
                    updateChecker.getReleaseNameFromGitHub(), 
                    updateChecker.getReleaseNotesFromGitHub(), 
                    updateChecker.isPrereleaseFromGitHub(), 
                    updateChecker.getHtmlUrlFromGitHub()
                );
            } else {
                ToastUtils.show(this, "请先检查更新以获取版本信息");
            }
        });
        
        // 强制检查更新 - 直接显示更新对话框用于测试
        forceCheckUpdateClickArea.setOnClickListener(v -> {
            if (updateChecker.getLatestVersionFromGitHub() != null) {
                // 直接使用 showUpdateDialog 显示弹窗，用于测试样式
                showUpdateDialog(
                    updateChecker.getReleaseNameFromGitHub(), 
                    updateChecker.getReleaseNotesFromGitHub(), 
                    updateChecker.isPrereleaseFromGitHub(), 
                    updateChecker.getHtmlUrlFromGitHub()
                );
            } else {
                ToastUtils.show(this, "请先检查更新以获取版本信息");
            }
        });
        
        // 开发者入口点击事件
        developerItem.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(this, DeveloperActivity.class);
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "启动开发者界面失败", e);
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
            
            // 根据是否是开发版本显示/隐藏测试按钮和开发者入口
            updateTestItemsVisibility();
            
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText("版本 0.0.0");
            isDevelopmentVersion = true;
            updateTestItemsVisibility();
        }
    }
    
    // 更新测试按钮和开发者入口的可见性
    private void updateTestItemsVisibility() {
        if (isDevelopmentVersion) {
            testUpdateItem.setVisibility(View.VISIBLE);
            forceCheckUpdateItem.setVisibility(View.VISIBLE);
            developerItem.setVisibility(View.VISIBLE);
            Log.d(TAG, "显示测试按钮和开发者入口（开发版本）");
        } else {
            testUpdateItem.setVisibility(View.GONE);
            forceCheckUpdateItem.setVisibility(View.GONE);
            developerItem.setVisibility(View.GONE);
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
                                     String releaseName, String ReleaseNotes,
                                     boolean isPrerelease,
                                     boolean isDevelopmentVersion, String htmlUrl) {
        runOnUiThread(() -> {
            this.isDevelopmentVersion = isDevelopmentVersion;
            
            // 首先处理开发者版本的状态显示
            if (isDevelopmentVersion) {
                // 开发者版本始终显示"当前为开发版本"
                String statusText = "当前是开发版本";
                
                updateStatusText.setText(statusText);
                updateStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
                updateStatusText.setOnClickListener(null);
                
                // 开发版本显示测试选项和开发者入口
                testUpdateItem.setVisibility(View.VISIBLE);
                forceCheckUpdateItem.setVisibility(View.VISIBLE);
                developerItem.setVisibility(View.VISIBLE);
                Log.d(TAG, "开发版本: 显示测试按钮和开发者入口");
                
                // 如果是开发版本，在有新版本时同样弹出更新对话框
                if (comparisonResult < 0) {
                    showUpdateDialog(releaseName, ReleaseNotes, isPrerelease, htmlUrl);
                }
                return;
            }
            
            // 非开发版本 (正式版)
            if (comparisonResult < 0) {
                // 有新版本可用 - 显示弹窗
                showUpdateDialog(releaseName, ReleaseNotes, isPrerelease, htmlUrl);
                
                // 更新状态文本
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
                
                // 正式版隐藏测试选项
                testUpdateItem.setVisibility(View.GONE);
                forceCheckUpdateItem.setVisibility(View.GONE);
                developerItem.setVisibility(View.GONE);
            } else if (comparisonResult == 0) {
                // 版本相同
                String statusText = "已是最新版本";
                if (isPrerelease) {
                    statusText += " (预发布)";
                }
                updateStatusText.setText(statusText);
                updateStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                updateStatusText.setOnClickListener(null);
                
                // 正式版隐藏测试选项
                testUpdateItem.setVisibility(View.GONE);
                forceCheckUpdateItem.setVisibility(View.GONE);
                developerItem.setVisibility(View.GONE);
            } else {
                // 当前版本比找到的版本更新
                updateStatusText.setText("当前版本比 github 上更新?");
                updateStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light));
                updateStatusText.setOnClickListener(null);
                
                // 正式版隐藏测试选项
                testUpdateItem.setVisibility(View.GONE);
                forceCheckUpdateItem.setVisibility(View.GONE);
                developerItem.setVisibility(View.GONE);
            }
            
            // 更新测试按钮和开发者入口的可见性
            updateTestItemsVisibility();
        });
    }
    
    @Override
    public void onUpdateCheckError(String errorMessage) {
        runOnUiThread(() -> {
            updateStatusText.setText("检查失败");
            updateStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            updateStatusText.setOnClickListener(null);
            testUpdateItem.setVisibility(View.GONE);
            forceCheckUpdateItem.setVisibility(View.GONE);
            developerItem.setVisibility(View.GONE);
        });
    }
    
    @Override
    public void onNoUpdateAvailable() {
        runOnUiThread(() -> {
            updateStatusText.setText("没有可用更新");
            updateStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
            updateStatusText.setOnClickListener(null);
            
            // 如果是开发版本，显示测试选项和开发者入口
            if (isDevelopmentVersion) {
                testUpdateItem.setVisibility(View.VISIBLE);
                forceCheckUpdateItem.setVisibility(View.VISIBLE);
                developerItem.setVisibility(View.VISIBLE);
            } else {
                testUpdateItem.setVisibility(View.GONE);
                forceCheckUpdateItem.setVisibility(View.GONE);
                developerItem.setVisibility(View.GONE);
            }
        });
    }
    
    private void showUpdateDialog(String releaseName, String releaseNotes, 
                                 boolean isPrerelease, String htmlUrl) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog);
        
        String title = isPrerelease ? "预发布版本: " + releaseName : "更新详情: " + releaseName;
        builder.setTitle(title);
        
        TextView messageView = new TextView(this);
        messageView.setPadding(50, 30, 50, 30);
        messageView.setTextSize(14);
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
                ToastUtils.show(this, "无法打开链接");
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
                forceCheckUpdateItem.setVisibility(View.GONE);
                developerItem.setVisibility(View.GONE);
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
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog);
        
        String title = "[测试] " + (isPrerelease ? "预发布版本: " + releaseName : "更新详情: " + releaseName);
        builder.setTitle(title);
        
        TextView messageView = new TextView(this);
        messageView.setPadding(50, 30, 50, 30);
        messageView.setTextSize(14);
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
                ToastUtils.show(this, "无法打开链接");
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
