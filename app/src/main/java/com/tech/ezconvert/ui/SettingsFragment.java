package com.tech.ezconvert.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.tech.ezconvert.R;

public class SettingsFragment extends BaseFragment {
    
    private LinearLayout transcodeSettingsItem;
    private LinearLayout generalSettingsItem;
    private LinearLayout aboutItem;
    private LinearLayout logSettingsItem;
    private LinearLayout logViewerItem;
    private TextView versionText;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initializeViews(view);
        setupClickListeners();
        setupVersionInfo();
    }
    
    private void initializeViews(View view) {
        transcodeSettingsItem = view.findViewById(R.id.transcode_settings_item);
        generalSettingsItem = view.findViewById(R.id.general_settings_item);
        aboutItem = view.findViewById(R.id.about_item);
        logSettingsItem = view.findViewById(R.id.log_settings_item);
        logViewerItem = view.findViewById(R.id.log_viewer_item);
        versionText = view.findViewById(R.id.version_text);
    }
    
    private void setupClickListeners() {
        // 转码设置
        transcodeSettingsItem.setOnClickListener(v -> {
            navigateTo(R.id.transcodeSettingsFragment);
        });
        
        // 通用设置（更多设置）
        generalSettingsItem.setOnClickListener(v -> {
            navigateTo(R.id.moreSettingsFragment);
        });
        
        // 关于
        aboutItem.setOnClickListener(v -> {
            navigateTo(R.id.aboutFragment);
        });
        
        // 日志设置
        logSettingsItem.setOnClickListener(v -> {
            navigateTo(R.id.logSettingsFragment);
        });
        
        // 日志查看器
        logViewerItem.setOnClickListener(v -> {
            navigateTo(R.id.logViewerFragment);
        });
    }
    
    private void setupVersionInfo() {
        // 版本号显示
        // versionText.setText("EzConvert v0.8.1");
    }
}