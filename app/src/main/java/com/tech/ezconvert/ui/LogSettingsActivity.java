package com.tech.ezconvert.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.AnimationUtils;
import com.tech.ezconvert.utils.ConfigManager;
import com.tech.ezconvert.utils.FFmpegUtil;

public class LogSettingsActivity extends AppCompatActivity {

    private RadioButton rbAll, rbError;
    private Button btnViewLog;
    private ConfigManager configManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_settings);

        configManager = ConfigManager.getInstance(this);

        rbAll   = findViewById(R.id.rb_log_all);
        rbError = findViewById(R.id.rb_log_error);
        btnViewLog = findViewById(R.id.btn_view_log);

        boolean isVerbose = configManager.isVerboseLoggingEnabled();
        rbAll.setChecked(isVerbose);
        rbError.setChecked(!isVerbose);

        RadioGroup rg = findViewById(R.id.rg_log_level);
        rg.setOnCheckedChangeListener((group, checkedId) -> {
            boolean verbose = checkedId == R.id.rb_log_all;
            configManager.setVerboseLoggingEnabled(verbose);
            
            // 重新初始化FFmpeg日志
            FFmpegUtil.initLogging(this);
            
            // 显示设置提示
            String message = verbose ? "已启用详细日志模式" : "已启用仅错误日志模式";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });

        btnViewLog.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            
            v.postDelayed(() -> {
                Intent intent = new Intent(this, LogViewerActivity.class);
                ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                    this,
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
                );
                ActivityCompat.startActivity(this, intent, options.toBundle());
            }, 200);
        });
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}