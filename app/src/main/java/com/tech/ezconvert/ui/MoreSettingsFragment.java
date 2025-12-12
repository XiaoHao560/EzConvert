package com.tech.ezconvert.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.tech.ezconvert.R;

public class MoreSettingsFragment extends BaseFragment {
    
    private MaterialSwitch autoUpdateSwitch;
    private Spinner frequencySpinner;
    private LinearLayout runLogItem;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_more_settings, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initializeViews(view);
        setupSpinner();
        setupClickListeners();
        loadCurrentSettings();
    }
    
    private void initializeViews(View view) {
        autoUpdateSwitch = view.findViewById(R.id.auto_update_switch);
        frequencySpinner = view.findViewById(R.id.frequency_spinner);
        runLogItem = view.findViewById(R.id.item_run_log);
    }
    
    private void setupSpinner() {
        // 创建检测频率选项
        String[] frequencyOptions = {"每次启动", "每天", "每周", "每月"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            frequencyOptions
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        frequencySpinner.setAdapter(adapter);
        frequencySpinner.setSelection(0);
        
        frequencySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = frequencyOptions[position];
                saveUpdateFrequency(selected);
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
    
    private void setupClickListeners() {
        autoUpdateSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 显示/隐藏频率选择布局
            View frequencyLayout = getView().findViewById(R.id.frequency_layout);
            frequencyLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            
            saveAutoUpdateSetting(isChecked);
        });
        
        runLogItem.setOnClickListener(v -> {
            // 导航到日志查看器
            navigateTo(R.id.logViewerFragment);
        });
    }
    
    private void loadCurrentSettings() {
        // 这里可以从 ConfigManager 加载当前设置
        autoUpdateSwitch.setChecked(true);
    }
    
    private void saveAutoUpdateSetting(boolean enabled) {
        // 保存到 ConfigManager
        Toast.makeText(requireContext(), 
            enabled ? "自动更新已开启" : "自动更新已关闭", 
            Toast.LENGTH_SHORT).show();
    }
    
    private void saveUpdateFrequency(String frequency) {
        // 保存到 ConfigManager
        Toast.makeText(requireContext(), 
            "更新频率设置为: " + frequency, 
            Toast.LENGTH_SHORT).show();
    }
}