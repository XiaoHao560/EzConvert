package com.tech.ezconvert.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.tech.ezconvert.R;

public class LogSettingsFragment extends BaseFragment {
    
    private RadioGroup logLevelRadioGroup;
    private Button viewLogButton;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_log_settings, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initializeViews(view);
        setupClickListeners();
        loadCurrentSettings();
    }
    
    private void initializeViews(View view) {
        logLevelRadioGroup = view.findViewById(R.id.rg_log_level);
        viewLogButton = view.findViewById(R.id.btn_view_log);
    }
    
    private void setupClickListeners() {
        logLevelRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            saveLogLevel(checkedId);
        });
        
        viewLogButton.setOnClickListener(v -> {
            // 导航到日志查看器
            navigateTo(R.id.logViewerFragment);
        });
    }
    
    private void loadCurrentSettings() {
        // 这里可以从 ConfigManager 加载当前日志级别设置
        // 默认选择全部日志
        logLevelRadioGroup.check(R.id.rb_log_all);
    }
    
    private void saveLogLevel(int checkedId) {
        String level;
        if (checkedId == R.id.rb_log_all) {
            level = "verbose";
        } else {
            level = "error";
        }
        
        // 这里保存到 ConfigManager
        Toast.makeText(requireContext(), "日志级别设置为: " + level, Toast.LENGTH_SHORT).show();
    }
}