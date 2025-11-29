package com.tech.ezconvert;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;

public class LogSettingsActivity extends AppCompatActivity {

    private SharedPreferences sp;
    private RadioButton rbAll, rbError;
    private Button btnViewLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_settings);

        sp = getSharedPreferences("debug_settings", MODE_PRIVATE);

        rbAll   = findViewById(R.id.rb_log_all);
        rbError = findViewById(R.id.rb_log_error);
        btnViewLog = findViewById(R.id.btn_view_log);

        boolean isAll = sp.getBoolean("log_verbose", true); // 默认开启详细日志
        rbAll.setChecked(isAll);
        rbError.setChecked(!isAll);

        RadioGroup rg = findViewById(R.id.rg_log_level);
        rg.setOnCheckedChangeListener((group, checkedId) -> {
            boolean verbose = checkedId == R.id.rb_log_all;
            sp.edit().putBoolean("log_verbose", verbose).apply();
            
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