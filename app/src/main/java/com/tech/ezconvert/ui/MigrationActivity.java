package com.tech.ezconvert.ui;

import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import com.tech.ezconvert.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tech.ezconvert.utils.ConfigManager;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.ToastUtils;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 负责将旧版配置迁移到当前配置文件 */
public class MigrationActivity extends BaseActivity {
    
    private AlertDialog progressDialog;
    private ExecutorService executorService;
    private Handler mainHandler;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_migration);
        
        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        
        startMigration();
    }
    
    private void startMigration() {
        showProgressDialog();
        
        executorService.execute(() -> {
            boolean success = false;
            
            try {
                ConfigManager configManager = ConfigManager.getInstance(MigrationActivity.this);
                configManager.migrateOldSettings();
                success = true;
            } catch (Exception e) {
                Log.e("MigrationActivity", "迁移设置失败", e);
                success = false;
            }
            
            boolean finalSuccess = success;
            mainHandler.post(() -> {
                dismissProgressDialog();
                showMigrationResult(finalSuccess);
            });
        });
    }
    
    private void showProgressDialog() {
        // 构建自定义进度对话框
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        
        // 创建自定义布局
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_migration_progress, null);
        
        TextView messageText = dialogView.findViewById(R.id.migration_message_text);
        messageText.setText(R.string.migration_progress_message);
        
        builder.setView(dialogView);
        builder.setCancelable(false);
        
        progressDialog = builder.create();
        progressDialog.show();
    }
    
    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
    
    private void showMigrationResult(boolean success) {
        if (success) {
            String configPath = ConfigManager.getInstance(this).getConfigPath();
            showSuccessDialog(configPath);
        } else {
            showErrorDialog();
        }
    }
    
    private void showSuccessDialog(String configPath) {
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.migration_success_title)
            .setMessage(getString(R.string.migration_success_message, configPath))
            .setPositiveButton(R.string.btn_confirm, (dialog, which) -> {
                navigateToMainActivity();
            })
            .setNeutralButton(R.string.migration_view_config, (dialog, which) -> {
                ToastUtils.showLong(this, getString(R.string.toast_config_path, configPath));
                navigateToMainActivity();
            })
            .setCancelable(false)
            .show();
    }
    
    private void showErrorDialog() {
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.migration_error_title)
            .setMessage(R.string.migration_error_message)
            .setPositiveButton(R.string.btn_confirm, (dialog, which) -> {
                navigateToMainActivity();
            })
            .setCancelable(false)
            .show();
    }
    
    private void navigateToMainActivity() {
        Intent intent = new Intent(MigrationActivity.this, com.tech.ezconvert.MainActivity.class);
        startActivity(intent);
        finish();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissProgressDialog();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}