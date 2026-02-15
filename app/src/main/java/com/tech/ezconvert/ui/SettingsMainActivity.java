package com.tech.ezconvert.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
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
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_main);
        
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        
        initializeViews();
        setupClickListeners();
    }
    
    private void initializeViews() {
        transcodeSettingsItem = findViewById(R.id.transcode_settings_item);
        generalSettingsItem = findViewById(R.id.general_settings_item);
        aboutItem = findViewById(R.id.about_item);
        versionText = findViewById(R.id.version_text);
        
        setVersionText();
    }
    
    private void setVersionText() {
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            versionText.setText("EzConvert v" + versionName);
        } catch (PackageManager.NameNotFoundException e) {
            versionText.setText("EzConvert v0.0.0");
        }
    }
    
    private void setupClickListeners() {
        transcodeSettingsItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 打开转码设置界面
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
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}