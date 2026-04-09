package com.tech.ezconvert.ui;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.AnimationUtils;
import com.tech.ezconvert.utils.ConfigManager;
import com.tech.ezconvert.utils.ToastUtils;

public class TranscodeSettingsActivity extends BaseActivity {

    @Override
    protected int getTitleContainerId() {
        return R.id.title_container;
    }
    
    @Override
    protected int getScrollContentId() {
        return R.id.scroll_content;
    }
    
    private MaterialSwitch hardwareAccelerationSwitch;
    private MaterialSwitch multithreadingSwitch;
    private Button saveSettingsBtn;
    private MaterialToolbar toolbar;
    private ConfigManager configManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // 设置进入动画
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        
        configManager = ConfigManager.getInstance(this);
        initializeViews();
        loadSettings();
        setupToolbar();
        
        saveSettingsBtn.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            saveSettings();
        });
    }
    
    // 初始化视图组件
    private void initializeViews() {
        hardwareAccelerationSwitch = findViewById(R.id.hardware_acceleration_switch);
        multithreadingSwitch = findViewById(R.id.multithreading_switch);
        saveSettingsBtn = findViewById(R.id.save_settings_btn);
        toolbar = findViewById(R.id.title_container);
    }
    
    // 设置 Toolbar 返回按钮
    private void setupToolbar() {
        // 设置导航按钮点击事件 - 返回上一界面
        toolbar.setNavigationOnClickListener(v -> {
            finish();
        });
    }
    
    private void loadSettings() {
        boolean hardwareAcceleration = configManager.isHardwareAccelerationEnabled();
        boolean multithreading = configManager.isMultithreadingEnabled();
        
        hardwareAccelerationSwitch.setChecked(hardwareAcceleration);
        multithreadingSwitch.setChecked(multithreading);
    }
    
    private void saveSettings() {
        configManager.setHardwareAccelerationEnabled(hardwareAccelerationSwitch.isChecked());
        configManager.setMultithreadingEnabled(multithreadingSwitch.isChecked());
        
        ToastUtils.show(this, "设置已保存");
        finish();
    }
    
    @Override
    public void finish() {
        super.finish();
        // 设置退出动画
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
    
    public static boolean isHardwareAccelerationEnabled(android.content.Context context) {
        ConfigManager config = ConfigManager.getInstance(context);
        return config.isHardwareAccelerationEnabled();
    }
    
    public static boolean isMultithreadingEnabled(android.content.Context context) {
        ConfigManager config = ConfigManager.getInstance(context);
        return config.isMultithreadingEnabled();
    }
}
