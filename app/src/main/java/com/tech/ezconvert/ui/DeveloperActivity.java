package com.tech.ezconvert.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.FFmpegUtil;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.ToastUtils;

public class DeveloperActivity extends BaseActivity {

    private static final String TAG = "DeveloperActivity";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
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
        setupClickListeners();
    }
    
    private void setupClickListeners() {
        // 触发空指针异常按钮
        MaterialCardView triggerNpeItem = findViewById(R.id.trigger_npe_item);
        if (triggerNpeItem != null) {
            triggerNpeItem.setOnClickListener(v -> showNpeConfirmDialog());
        }
        
        // 检查编解码器按钮
        MaterialCardView checkCodecsItem = findViewById(R.id.check_codecs_item);
        if (checkCodecsItem != null) {
            checkCodecsItem.setOnClickListener(v -> checkFFmpegCodecs());
        }
    }
    
    // 检查 FFmpeg 编解码器 - 主入口
    private void checkFFmpegCodecs() {
        // 显示加载对话框
        View loadingView = LayoutInflater.from(this).inflate(R.layout.dialog_loading, null);
        TextView loadingText = loadingView.findViewById(R.id.loading_text);
        if (loadingText != null) {
            loadingText.setText("正在查询 FFmpeg 信息...");
        }
        
        androidx.appcompat.app.AlertDialog loadingDialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setView(loadingView)
            .setCancelable(false)
            .show();
        
        // 后台线程执行查询
        new Thread(() -> {
            StringBuilder result = new StringBuilder();
            
            // 版本信息
            result.append("═══ FFmpeg 版本信息 ═══\n\n");
            String version = FFmpegUtil.executeSimpleCommand("-version");
            if (version != null) {
                // 只取前几行避免太长
                String[] lines = version.split("\n");
                for (int i = 0; i < Math.min(5, lines.length); i++) {
                    result.append(lines[i]).append("\n");
                }
            }
            result.append("\n");
            
            // 硬件加速支持
            result.append("═══ 硬件加速支持 ═══\n\n");
            String hwaccels = FFmpegUtil.executeSimpleCommand("-hwaccels");
            result.append(hwaccels != null ? hwaccels : "获取失败").append("\n\n");
            
            // 关键编码器检查
            result.append("═══ 核心编码器支持 ═══\n\n");
            String encoders = FFmpegUtil.executeSimpleCommand("-encoders");
            String[] keyCodecs = {
                "libx264", "libx265", "libvpx", "libvpx-vp9",
                "libaom-av1", "libsvtav1", "libmp3lame", 
                "libopus", "aac", "h264_mediacodec", "hevc_mediacodec"
            };
            
            for (String codec : keyCodecs) {
                if (encoders != null) {
                    boolean supported = encoders.contains(codec);
                    result.append(supported ? "✓ " : "✗ ").append(codec).append("\n");
                }
            }
            
            result.append("\n═══ 编解码器统计 ═══\n\n");
            if (encoders != null) {
                int encoderCount = encoders.split("\n").length;
                result.append("编码器数量: 约 ").append(encoderCount).append(" 个\n");
            }
            String decoders = FFmpegUtil.executeSimpleCommand("-decoders");
            if (decoders != null) {
                int decoderCount = decoders.split("\n").length;
                result.append("解码器数量: 约 ").append(decoderCount).append(" 个\n");
            }
            
            String fullOutput = result.toString();
            
            // 回到主线程显示结果
            mainHandler.post(() -> {
                loadingDialog.dismiss();
                showCodecsResultDialog(fullOutput, encoders, decoders);
            });
            
        }).start();
    }
    
    // 显示编解码器结果对话框
    private void showCodecsResultDialog(String summary, String fullEncoders, String fullDecoders) {
        // 创建滚动容器
        NestedScrollView scrollView = new NestedScrollView(this);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        scrollView.setPadding(padding, padding / 2, padding, padding / 2);
        
        // 文本视图
        TextView textView = new TextView(this);
        textView.setText(summary);
        textView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        textView.setTextIsSelectable(true);
        textView.setLineSpacing(0, 1.3f);
        textView.setTextColor(getColor(R.color.text_primary));
        
        scrollView.addView(textView);
        
        // 构建对话框
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setIcon(R.drawable.round_code)
            .setTitle("FFmpeg 编解码器信息")
            .setView(scrollView)
            .setPositiveButton("关闭", null)
            .setNeutralButton("复制结果", (dialog, which) -> {
                copyToClipboard("FFmpeg Codecs", summary);
                ToastUtils.show(this, "已复制到剪贴板");
            });
        
        // 如果有完整数据，添加查看详细按钮
        if (fullEncoders != null) {
            builder.setNegativeButton("查看完整编码器", (dialog, which) -> {
                showFullListDialog("完整编码器列表", fullEncoders);
            });
        }
        
        builder.show();
    }
    
    // 显示完整列表
    private void showFullListDialog(String title, String content) {
        NestedScrollView scrollView = new NestedScrollView(this);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        scrollView.setPadding(padding, padding / 2, padding, padding / 2);
        
        TextView textView = new TextView(this);
        textView.setText(content);
        textView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        textView.setTextIsSelectable(true);
        textView.setHorizontallyScrolling(true); // 横向滚动看长行
        textView.setTextColor(getColor(R.color.text_primary));
        
        // 设置等宽字体更整齐
        textView.setTypeface(android.graphics.Typeface.MONOSPACE);
        
        scrollView.addView(textView);
        
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton("关闭", null)
            .setNeutralButton("复制全部", (dialog, which) -> {
                copyToClipboard("FFmpeg " + title, content);
                ToastUtils.show(this, "已复制");
            })
            .show();
    }
    
    // 复制文本到剪贴板
    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
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
