package com.tech.ezconvert.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.recyclerview.widget.*;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.LogManager;
import com.tech.ezconvert.utils.ToastUtils;
import java.util.ArrayList;
import java.util.List;

public class LogViewerActivity extends BaseActivity {

    @Override
    protected int getTitleContainerId() {
        return R.id.title_container;
    }
    
    @Override
    protected int getScrollContentId() {
        return R.id.scroll_content;
    }

    private LogManager logManager;
    
    // 应用日志相关
    private LogAdapter appLogAdapter;
    private RecyclerView appLogRecyclerView;
    private ImageView appLogExpandIcon;
    private LinearLayout appLogHeader;
    private TextView appLogCountText;
    private boolean isAppLogExpanded = false;  // 默认收起
    
    // FFmpeg日志相关
    private LogAdapter ffmpegLogAdapter;
    private RecyclerView ffmpegLogRecyclerView;
    private ImageView ffmpegLogExpandIcon;
    private LinearLayout ffmpegLogHeader;
    private TextView ffmpegLogCountText;
    private boolean isFfmpegLogExpanded = false;  // 默认收起

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_viewer);

        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("运行日志");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        logManager = LogManager.getInstance(this);
        
        initViews(); // 初始化视图
        setupListeners(); // 设置监听器
        refreshLogDisplay(); // 初始化加载日志 （此时RecyclerView是隐藏的）
        
        logManager.addListener(new LogManager.LogListener() {
            @Override
            public void onLogAdded(LogManager.LogEntry entry) {
                runOnUiThread(() -> {
                    if ("FFmpegLog".equals(entry.tag)) {
                        List<String> ffmpegLogs = logManager.getFfmpegLogsFromMemory();
                        ffmpegLogAdapter.updateData(ffmpegLogs);
                        ffmpegLogCountText.setText("共 " + ffmpegLogs.size() + " 条");
                        if (isFfmpegLogExpanded) {
                            ffmpegLogRecyclerView.scrollToPosition(ffmpegLogs.size() - 1);
                        }
                    } else {
                        List<String> appLogs = logManager.getAppLogsFromMemory();
                        appLogAdapter.updateData(appLogs);
                        appLogCountText.setText("共 " + appLogs.size() + " 条");
                        if (isAppLogExpanded) {
                            appLogRecyclerView.scrollToPosition(appLogs.size() - 1);
                        }
                    }
                });
            }
            
            @Override
            public void onLogsCleared() {
                runOnUiThread(() -> refreshLogDisplay());
            }
        });
    }

    // 应用日志
    private void initViews() {
        appLogRecyclerView = findViewById(R.id.app_log_recycler_view);
        appLogExpandIcon = findViewById(R.id.app_log_expand_icon);
        appLogHeader = findViewById(R.id.app_log_header);
        appLogCountText = findViewById(R.id.app_log_count_text);
        
        // 默认收起状态
        appLogRecyclerView.setVisibility(View.GONE);
        appLogExpandIcon.setRotation(-90);  // 收起
        
        appLogAdapter = new LogAdapter(new ArrayList<>());
        appLogRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        appLogRecyclerView.setAdapter(appLogAdapter);
        
        // FFmpeg日志
        ffmpegLogRecyclerView = findViewById(R.id.ffmpeg_log_recycler_view);
        ffmpegLogExpandIcon = findViewById(R.id.ffmpeg_log_expand_icon);
        ffmpegLogHeader = findViewById(R.id.ffmpeg_log_header);
        ffmpegLogCountText = findViewById(R.id.ffmpeg_log_count_text);
        
        // 默认收起状态
        ffmpegLogRecyclerView.setVisibility(View.GONE);
        ffmpegLogExpandIcon.setRotation(-90);  // 收起
        
        ffmpegLogAdapter = new LogAdapter(new ArrayList<>());
        ffmpegLogRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        ffmpegLogRecyclerView.setAdapter(ffmpegLogAdapter);
    }

    private void setupListeners() {
        // 应用日志卡片点击
        appLogHeader.setOnClickListener(v -> {
            isAppLogExpanded = !isAppLogExpanded;
            toggleCard(appLogRecyclerView, appLogExpandIcon, isAppLogExpanded);
        });

        // FFmpeg日志卡片点击
        ffmpegLogHeader.setOnClickListener(v -> {
            isFfmpegLogExpanded = !isFfmpegLogExpanded;
            toggleCard(ffmpegLogRecyclerView, ffmpegLogExpandIcon, isFfmpegLogExpanded);
        });

        // 清空按钮
        findViewById(R.id.btn_clear_log).setOnClickListener(v -> {
            logManager.clearAllLogs();
            refreshLogDisplay();
            ToastUtils.show(this, "日志已清除");
        });

        // 复制按钮
        findViewById(R.id.btn_copy_log).setOnClickListener(v -> {
            copyAllLogs();
        });
    }

    private void toggleCard(RecyclerView recyclerView, ImageView icon, boolean expand) {
        if (expand) {
            recyclerView.setVisibility(View.VISIBLE);
            icon.animate().rotation(0).setDuration(200).start();  // 展开
        } else {
            recyclerView.setVisibility(View.GONE);
            icon.animate().rotation(-90).setDuration(200).start();  // 收起
        }
    }

    private void refreshLogDisplay() {
        //应用日志
        List<String> appLogs = logManager.getAppLogsFromMemory();
        appLogAdapter.updateData(appLogs);
        
        // 只在展开状态下滚动
        appLogCountText.setText("共 " + appLogs.size() + " 条");
        if (!appLogs.isEmpty() && isAppLogExpanded) {
            appLogRecyclerView.scrollToPosition(appLogs.size() - 1);
        }

        // FFmpeg日志
        List<String> ffmpegLogs = logManager.getFfmpegLogsFromMemory();
        ffmpegLogAdapter.updateData(ffmpegLogs);
        
        // 只在展开状态下滚动
        ffmpegLogCountText.setText("共 " + ffmpegLogs.size() + " 条");
        if (!ffmpegLogs.isEmpty() && isFfmpegLogExpanded) {
            ffmpegLogRecyclerView.scrollToPosition(ffmpegLogs.size() - 1);
        }
    }

    private void copyAllLogs() {
        List<String> allLogs = logManager.getAllLogs();
        
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("AllLogs", String.join("\n", allLogs));
        cm.setPrimaryClip(clip);
        ToastUtils.show(this, "日志已复制到剪贴板");
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logManager.removeListener(null);
    }

    private static class LogAdapter extends RecyclerView.Adapter<LogAdapter.Holder> {
        private List<String> list;
        
        LogAdapter(List<String> list) { 
            this.list = new ArrayList<>(list); 
        }
        
        void updateData(List<String> newList) {
            this.list = new ArrayList<>(newList);
            notifyDataSetChanged();
        }
        
        @Override public Holder onCreateViewHolder(android.view.ViewGroup p, int vType) {
            TextView tv = new TextView(p.getContext());
            tv.setPadding(16, 12, 16, 12);
            tv.setTextSize(12);
            tv.setTextColor(p.getContext().getResources().getColor(R.color.text_primary));
            return new Holder(tv);
        }
        
        @Override public void onBindViewHolder(Holder h, int i) { 
            ((TextView) h.itemView).setText(list.get(i)); 
        }
        
        @Override public int getItemCount() { return list.size(); }
        
        static class Holder extends RecyclerView.ViewHolder { 
            Holder(android.view.View v) { super(v); } 
        }
    }
}
