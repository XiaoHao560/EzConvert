package com.tech.ezconvert.ui;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.ConfigManager;
import com.tech.ezconvert.utils.NotificationHelper;
import com.tech.ezconvert.utils.ToastUtils;

public class MoreSettingsActivity extends BaseActivity {

    @Override
    protected int getTitleContainerId() {
        return R.id.title_container;
    }
    
    @Override
    protected int getScrollContentId() {
        return R.id.scroll_content;
    }
    
    private MaterialSwitch autoUpdateSwitch;
    private MaterialSwitch notificationSwitch;
    private AutoCompleteTextView frequencySpinner;
    private LinearLayout frequencyLayout;
    private MaterialToolbar toolbar;
    private ConfigManager configManager;
    
    // 标记是否正在处理开关变化，防止循环触发
    private boolean isHandlingNotificationSwitch = false;
    // 标记是否刚从权限设置返回，需要检查权限状态
    private boolean needCheckPermissionOnResume = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_more_settings);

        // 设置进入动画
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

        configManager = ConfigManager.getInstance(this);
        
        // 初始化视图
        initViews();
        setupClickListeners();
        loadCurrentSettings();
    }

    // 初始化视图组件
    private void initViews() {
        // 初始化 Toolbar
        toolbar = findViewById(R.id.title_container);
        
        // 运行日志入口
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
        
        // 通知开关
        notificationSwitch = findViewById(R.id.notification_switch);
        
        // 设置下拉菜单适配器
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
            this,
            R.array.update_frequency_options,
            android.R.layout.simple_dropdown_item_1line
        );
        frequencySpinner.setAdapter(adapter);
        
        // 设置下拉菜单背景为白色
        frequencySpinner.setDropDownBackgroundDrawable(new ColorDrawable(getResources().getColor(android.R.color.white)));
        
        // 设置 Toolbar
        setupToolbar();
    }
    
    // 设置 Toolbar 返回按钮
    private void setupToolbar() {
        // 设置导航按钮点击事件 - 返回上一界面
        toolbar.setNavigationOnClickListener(v -> {
            finish();
        });
    }

    // 设置点击监听器
    private void setupClickListeners() {
        // 自动更新开关监听
        autoUpdateSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                configManager.setAutoCheckUpdateEnabled(isChecked);
                updateFrequencyLayoutVisibility(isChecked);
            }
        });
        
        // 通知开关监听
        notificationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // 防止循环触发
                if (isHandlingNotificationSwitch) {
                    return;
                }
                
                if (isChecked) {
                    // 开启通知，检查权限
                    handleNotificationEnable();
                } else {
                    // 关闭通知，直接保存
                    configManager.setNotificationEnabled(false);
                }
            }
        });
        
        // 频率选择监听
        frequencySpinner.setOnItemClickListener((parent, view, position, id) -> {
            int frequency = mapPositionToFrequency(position);
            configManager.setUpdateCheckFrequency(frequency);
        });
    }

    private void loadCurrentSettings() {
        // 加载当前设置
        boolean autoCheckEnabled = configManager.isAutoCheckUpdateEnabled();
        int currentFrequency = configManager.getUpdateCheckFrequency();
        boolean notificationEnabled = configManager.isNotificationEnabled();
        
        // 更新开关状态
        autoUpdateSwitch.setChecked(autoCheckEnabled);
        notificationSwitch.setChecked(notificationEnabled);
        
        // 更新Spinner选择
        int spinnerPosition = mapFrequencyToPosition(currentFrequency);
        frequencySpinner.setText(frequencySpinner.getAdapter().getItem(spinnerPosition).toString(), false);
        
        // 更新布局可见性
        updateFrequencyLayoutVisibility(autoCheckEnabled);
    }

    // 更新频率选择区域的可见性
    private void updateFrequencyLayoutVisibility(boolean enabled) {
        if (frequencyLayout != null) {
            frequencyLayout.setEnabled(enabled);
            frequencyLayout.setAlpha(enabled ? 1.0f : 0.5f);
            frequencySpinner.setEnabled(enabled);
        }
    }

    // 将下拉菜单位置映射为频率值
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

    // 将频率值映射为下拉菜单位置
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
    
    // 处理用户开启通知
    private void handleNotificationEnable() {
        // 创建通知渠道
        NotificationHelper.createNotificationChannels(this);
        
        // 检查是否已有权限
        if (NotificationHelper.areNotificationsEnabled(this)) {
            // 已有权限，直接开启
            configManager.setNotificationEnabled(true);
        } else {
            // 无权限，显示申请对话框
            showNotificationPermissionDialog();
        }
    }
    
    // 显示通知权限申请对话框
    private void showNotificationPermissionDialog() {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(R.string.notification_permission_title)
            .setMessage(R.string.notification_permission_message)
            .setPositiveButton(R.string.notification_permission_go_settings, (dialog, which) -> {
                // 点击去开启，保存设置并标记需要检查权限
                configManager.setNotificationEnabled(true);
                needCheckPermissionOnResume = true;
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(intent);
            })
            .setNegativeButton(R.string.notification_permission_later, (dialog, which) -> {
                // 点击拒绝，关闭开关
                isHandlingNotificationSwitch = true;
                notificationSwitch.setChecked(false);
                configManager.setNotificationEnabled(false);
                isHandlingNotificationSwitch = false;
                needCheckPermissionOnResume = false;
            })
            .setCancelable(false) // 禁止点击外部关闭对话框
            .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // 如果从权限设置返回，检查权限状态
        if (needCheckPermissionOnResume) {
            needCheckPermissionOnResume = false;
            
            // 检查是否实际开启了权限
            if (!NotificationHelper.areNotificationsEnabled(this)) {
                // 没有开启权限，关闭开关并提示
                isHandlingNotificationSwitch = true;
                notificationSwitch.setChecked(false);
                configManager.setNotificationEnabled(false);
                isHandlingNotificationSwitch = false;
                
                ToastUtils.show(this, "未获得通知权限，已关闭通知功能");
            }
        }
    }

    @Override
    public void finish() {
        super.finish();
        // 设置退出动画
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
