package com.tech.ezconvert.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import androidx.core.content.ContextCompat;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.Log;

public class DeveloperActivity extends BaseActivity {

    private static final String TAG = "DeveloperActivity";
    
    @Override
    protected int getTitleContainerId() {
        return R.id.title_container;
    }
    
    @Override
    protected int getScrollContentId() {
        return R.id.scroll_content;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_developer);
        
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        
        initializeViews();
        setupClickListeners();
    }
    
    private void initializeViews() {
        
    }
    
    private void setupClickListeners() {
        // 触发空指针异常按钮
        MaterialCardView triggerNpeItem = findViewById(R.id.trigger_npe_item);
        triggerNpeItem.setOnClickListener(v -> {
            showNpeConfirmDialog();
        });
    }
    
    // 显示对话框防止误触
    private void showNpeConfirmDialog() {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setIcon(R.drawable.alert_circle)
            .setTitle("警告")
            .setMessage("即将触发空指针异常来测试崩溃处理机制。\n\n应用将会崩溃，未保存的数据可能会丢失。\n\n确定要继续吗？")
            .setPositiveButton("触发崩溃", (dialog, which) -> {
                triggerNullPointerException();
            })
            .setNegativeButton("取消", null)
            .setNeutralButton("查看说明", (dialog, which) -> {
                showNpeExplanationDialog();
            })
            .show();
    }
    
    // 显示说明对话框
    private void showNpeExplanationDialog() {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setIcon(R.drawable.information_outline)
            .setTitle("空指针异常测试说明")
            .setMessage("此功能用于测试应用的崩溃处理机制：\n\n" +
                       "• 验证 CrashHandler 是否能正确捕获异常\n" +
                       "• 测试崩溃日志的记录功能\n" +
                       "• 确认下次启动时是否能正常恢复\n\n" +
                       "触发后应用会立即崩溃，请重新启动应用查看效果。")
            .setPositiveButton("知道了", null)
            .show();
    }
    
    // 触发空指针异常
    private void triggerNullPointerException() {
        Log.w(TAG, "用户主动触发空指针异常测试");
        
        // 延迟100ms执行，让日志写入完成
        new android.os.Handler().postDelayed(() -> {
            String nullString = null;
            // 这行代码会抛出 NullPointerException
            int length = nullString.length();
            
            // 这行不会执行，因为上面已经崩溃了
            Log.d(TAG, "这行不会执行: " + length);
        }, 100);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
    
    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
