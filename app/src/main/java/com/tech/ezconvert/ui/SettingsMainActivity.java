package com.tech.ezconvert.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import android.content.pm.PackageManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.tech.ezconvert.R;

public class SettingsMainActivity extends BaseActivity {

    @Override
    protected int getTitleContainerId() {
        return R.id.title_container;
    }
    
    @Override
    protected int getScrollContentId() {
        return R.id.scroll_content;
    }
    
    private TextView versionText;
    private LinearLayout transcodeSettingsItem;
    private LinearLayout generalSettingsItem;
    private LinearLayout aboutItem;
    private MaterialToolbar toolbar;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_main);
        
        // 设置进入动画
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        
        initializeViews();
        setupClickListeners();
        setupToolbar();
    }
    
    // 初始化视图组件
    private void initializeViews() {
        transcodeSettingsItem = findViewById(R.id.transcode_settings_item);
        generalSettingsItem = findViewById(R.id.general_settings_item);
        aboutItem = findViewById(R.id.about_item);
        versionText = findViewById(R.id.version_text);
        toolbar = findViewById(R.id.title_container);
        
        setVersionText();
    }
    
    // 设置版本号文本
    private void setVersionText() {
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            versionText.setText("EzConvert v" + versionName);
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText("EzConvert v0.0.0");
        }
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
        // 打开转码设置界面
        transcodeSettingsItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SettingsMainActivity.this, TranscodeSettingsActivity.class);
                ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                    SettingsMainActivity.this,
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
                );
                ActivityCompat.startActivity(SettingsMainActivity.this, intent, options.toBundle());
            }
        });
        
        // 打开通用设置
        generalSettingsItem.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsMainActivity.this, MoreSettingsActivity.class);
            ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                SettingsMainActivity.this,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            );
            ActivityCompat.startActivity(SettingsMainActivity.this, intent, options.toBundle());
        });
        
        // 打开关于界面
        aboutItem.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsMainActivity.this, AboutActivity.class);
            ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                SettingsMainActivity.this,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            );
            ActivityCompat.startActivity(SettingsMainActivity.this, intent, options.toBundle());
        });
    }
    
    @Override
    public void finish() {
        super.finish();
        // 设置退出动画
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
