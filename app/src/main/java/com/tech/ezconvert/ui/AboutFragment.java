package com.tech.ezconvert.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.UpdateChecker;

public class AboutFragment extends BaseFragment {
    
    private LinearLayout sourceCodeItem;
    private LinearLayout feedbackItem;
    private LinearLayout checkUpdateItem;
    private LinearLayout testUpdateItem;
    private LinearLayout licenseItem;
    private TextView updateStatusText;
    private TextView versionText;
    
    private UpdateChecker updateChecker;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_about, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        updateChecker = new UpdateChecker(requireContext());
        initializeViews(view);
        setupClickListeners();
        setupVersionInfo();
    }
    
    private void initializeViews(View view) {
        sourceCodeItem = view.findViewById(R.id.source_code_item);
        feedbackItem = view.findViewById(R.id.feedback_item);
        checkUpdateItem = view.findViewById(R.id.check_update_item);
        testUpdateItem = view.findViewById(R.id.test_update_item);
        licenseItem = view.findViewById(R.id.license_item);
        updateStatusText = view.findViewById(R.id.update_status_text);
        versionText = view.findViewById(R.id.version_text);
    }
    
    private void setupClickListeners() {
        sourceCodeItem.setOnClickListener(v -> openGitHubRepository());
        feedbackItem.setOnClickListener(v -> openGitHubIssues());
        checkUpdateItem.setOnClickListener(v -> checkForUpdates());
        licenseItem.setOnClickListener(v -> showLicenseInfo());
        
        // 测试更新（仅开发时使用）
        testUpdateItem.setOnClickListener(v -> {
            updateStatusText.setText("正在强制检查更新...");
            updateChecker.setUpdateCheckListener(new UpdateChecker.UpdateCheckListener() {
                @Override
                public void onUpdateCheckComplete(int comparisonResult, String latestVersion, 
                                                String releaseName, boolean isPrerelease, 
                                                boolean isDevelopmentVersion, String htmlUrl) {
                    requireActivity().runOnUiThread(() -> {
                        if (comparisonResult < 0) {
                            updateStatusText.setText("发现新版本: " + latestVersion);
                            Toast.makeText(requireContext(), "发现新版本: " + latestVersion, Toast.LENGTH_SHORT).show();
                        } else if (comparisonResult == 0) {
                            updateStatusText.setText("已是最新版本");
                            Toast.makeText(requireContext(), "已是最新版本", Toast.LENGTH_SHORT).show();
                        } else {
                            updateStatusText.setText("当前为开发版本");
                            Toast.makeText(requireContext(), "当前为开发版本", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                
                @Override
                public void onUpdateCheckError(String errorMessage) {
                    requireActivity().runOnUiThread(() -> {
                        updateStatusText.setText("检查更新失败");
                        Toast.makeText(requireContext(), "检查更新失败: " + errorMessage, Toast.LENGTH_SHORT).show();
                    });
                }
                
                @Override
                public void onNoUpdateAvailable() {
                    requireActivity().runOnUiThread(() -> {
                        updateStatusText.setText("没有可用更新");
                        Toast.makeText(requireContext(), "没有可用更新", Toast.LENGTH_SHORT).show();
                    });
                }
            });
            updateChecker.checkForManualUpdate();
        });
    }
    
    private void setupVersionInfo() {
        // 设置版本信息，这里可以动态获取
        versionText.setText("EzConvert v0.8.1");
    }
    
    private void openGitHubRepository() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://github.com/XiaoHao560/EzConvert"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "无法打开浏览器", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openGitHubIssues() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://github.com/XiaoHao560/EzConvert/issues"));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "无法打开浏览器", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void checkForUpdates() {
        updateStatusText.setText("正在检查更新...");
        updateChecker.setUpdateCheckListener(new UpdateChecker.UpdateCheckListener() {
            @Override
            public void onUpdateCheckComplete(int comparisonResult, String latestVersion, 
                                            String releaseName, boolean isPrerelease, 
                                            boolean isDevelopmentVersion, String htmlUrl) {
                requireActivity().runOnUiThread(() -> {
                    if (comparisonResult < 0) {
                        updateStatusText.setText("发现新版本: " + latestVersion);
                        Toast.makeText(requireContext(), "发现新版本: " + latestVersion, Toast.LENGTH_SHORT).show();
                    } else if (comparisonResult == 0) {
                        updateStatusText.setText("已是最新版本");
                        Toast.makeText(requireContext(), "已是最新版本", Toast.LENGTH_SHORT).show();
                    } else {
                        updateStatusText.setText("当前为开发版本");
                        Toast.makeText(requireContext(), "当前为开发版本", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            @Override
            public void onUpdateCheckError(String errorMessage) {
                requireActivity().runOnUiThread(() -> {
                    updateStatusText.setText("检查更新失败");
                    Toast.makeText(requireContext(), "检查更新失败: " + errorMessage, Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onNoUpdateAvailable() {
                requireActivity().runOnUiThread(() -> {
                    updateStatusText.setText("没有可用更新");
                    Toast.makeText(requireContext(), "没有可用更新", Toast.LENGTH_SHORT).show();
                });
            }
        });
        updateChecker.checkForManualUpdate();
    }
    
    private void showLicenseInfo() {
        Toast.makeText(requireContext(), 
            "EzConvert 使用 GPLv3 许可证开源\n© 2025 XiaoHao. 保留所有权利。", 
            Toast.LENGTH_LONG).show();
    }
}