package com.tech.ezconvert.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.ConfigManager;
import com.tech.ezconvert.utils.ToastUtils;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
                e.printStackTrace();
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        // 创建自定义布局
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_migration_progress, null);
        
        ProgressBar progressBar = dialogView.findViewById(R.id.migration_progress_bar);
        TextView messageText = dialogView.findViewById(R.id.migration_message_text);
        messageText.setText("正在迁移设置数据...");
        
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
        new AlertDialog.Builder(this)
            .setTitle("迁移成功")
            .setMessage("设置迁移完成！\n\n配置文件已保存到:\n" + configPath)
            .setPositiveButton("确定", (dialog, which) -> {
                navigateToMainActivity();
            })
            .setNeutralButton("查看配置文件", (dialog, which) -> {
                ToastUtils.showLong(this, "配置文件位置: " + configPath);
                navigateToMainActivity();
            })
            .setCancelable(false)
            .show();
    }
    
    private void showErrorDialog() {
        new AlertDialog.Builder(this)
            .setTitle("迁移失败")
            .setMessage("设置迁移过程中出现错误，将使用默认设置")
            .setPositiveButton("确定", (dialog, which) -> {
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