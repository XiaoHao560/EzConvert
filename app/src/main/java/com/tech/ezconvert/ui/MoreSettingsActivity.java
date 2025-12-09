package com.tech.ezconvert.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.ConfigManager;

public class MoreSettingsActivity extends AppCompatActivity {
    
    private com.google.android.material.materialswitch.MaterialSwitch autoUpdateSwitch;
    private Spinner frequencySpinner;
    private LinearLayout frequencyLayout;
    private ConfigManager configManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_more_settings);

        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

        configManager = ConfigManager.getInstance(this);
        
        // 初始化视图
        initViews();
        setupClickListeners();
        loadCurrentSettings();
    }

    private void initViews() {
        // 运行日志
        LinearLayout logEntry = findViewById(R.id.item_run_log);
        logEntry.setOnClickListener(v -> {
            Intent intent = new Intent(this, LogSettingsActivity.class);
            ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            );
            ActivityCompat.startActivity(this, intent, options.toBundle());
        });
        
        // 自动更新开关
        autoUpdateSwitch = findViewById(R.id.auto_update_switch);
        frequencySpinner = findViewById(R.id.frequency_spinner);
        frequencyLayout = findViewById(R.id.frequency_layout);
        
        // 设置Spinner适配器
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
            this,
            R.array.update_frequency_options,
            android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        frequencySpinner.setAdapter(adapter);
    }

    private void setupClickListeners() {
        // 自动更新开关监听
        autoUpdateSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                configManager.setAutoCheckUpdateEnabled(isChecked);
                updateFrequencyLayoutVisibility(isChecked);
            }
        });
        
        // 频率选择监听
        frequencySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int frequency = mapPositionToFrequency(position);
                configManager.setUpdateCheckFrequency(frequency);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                
            }
        });
    }

    private void loadCurrentSettings() {
        // 加载当前设置
        boolean autoCheckEnabled = configManager.isAutoCheckUpdateEnabled();
        int currentFrequency = configManager.getUpdateCheckFrequency();
        
        // 更新开关状态
        autoUpdateSwitch.setChecked(autoCheckEnabled);
        
        // 更新Spinner选择
        int spinnerPosition = mapFrequencyToPosition(currentFrequency);
        frequencySpinner.setSelection(spinnerPosition);
        
        // 更新布局可见性
        updateFrequencyLayoutVisibility(autoCheckEnabled);
    }

    private void updateFrequencyLayoutVisibility(boolean enabled) {
        if (frequencyLayout != null) {
            frequencyLayout.setEnabled(enabled);
            frequencyLayout.setAlpha(enabled ? 1.0f : 0.5f);
            frequencySpinner.setEnabled(enabled);
        }
    }

    private int mapPositionToFrequency(int position) {
        switch (position) {
            case 0: // 每24小时检测
                return 1; // FREQUENCY_EVERY_24_HOURS
            case 1: // 每次进入应用检测
                return 2; // FREQUENCY_EVERY_LAUNCH
            default:
                return 1;
        }
    }

    private int mapFrequencyToPosition(int frequency) {
        switch (frequency) {
            case 1: // FREQUENCY_EVERY_24_HOURS
                return 0;
            case 2: // FREQUENCY_EVERY_LAUNCH
                return 1;
            default:
                return 0;
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}