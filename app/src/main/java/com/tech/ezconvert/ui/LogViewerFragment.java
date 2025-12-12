package com.tech.ezconvert.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.tech.ezconvert.R;
import java.util.ArrayList;
import java.util.List;

public class LogViewerFragment extends BaseFragment {
    
    private RecyclerView logRecyclerView;
    private ImageButton clearLogButton;
    private ImageButton copyLogButton;
    private LogAdapter logAdapter;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_log_viewer, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initializeViews(view);
        setupRecyclerView();
        loadLogs();
        setupClickListeners();
    }
    
    private void initializeViews(View view) {
        logRecyclerView = view.findViewById(R.id.log_recycler_view);
        clearLogButton = view.findViewById(R.id.btn_clear_log);
        copyLogButton = view.findViewById(R.id.btn_copy_log);
    }
    
    private void setupRecyclerView() {
        logRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        logAdapter = new LogAdapter();
        logRecyclerView.setAdapter(logAdapter);
    }
    
    private void loadLogs() {
        List<String> logEntries = new ArrayList<>();
        
        // 模拟日志数据 - "占位符"
        logEntries.add("[2025-01-15 10:30:25] INFO: 应用启动成功");
        logEntries.add("[2025-01-15 10:30:30] INFO: FFmpegKit 初始化完成");
        logEntries.add("[2025-01-15 10:31:45] INFO: 开始转换视频: video.mp4");
        logEntries.add("[2025-01-15 10:32:10] INFO: 视频转换完成");
        logEntries.add("[2025-01-15 10:35:22] ERROR: 文件访问权限不足");
        logEntries.add("[2025-01-15 10:40:00] INFO: 用户选择了设置菜单");
        
        logAdapter.setLogs(logEntries);
        if (logEntries.size() > 0) {
            logRecyclerView.scrollToPosition(logEntries.size() - 1);
        }
    }
    
    private void setupClickListeners() {
        clearLogButton.setOnClickListener(v -> {
            // 清空日志逻辑
            logAdapter.clearLogs();
            Toast.makeText(requireContext(), "日志已清空", Toast.LENGTH_SHORT).show();
        });
        
        copyLogButton.setOnClickListener(v -> {
            // 复制日志到剪贴板
            String allLogs = logAdapter.getAllLogs();
            ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("EzConvert Logs", allLogs);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(requireContext(), "日志已复制到剪贴板", Toast.LENGTH_SHORT).show();
        });
    }
    
    private static class LogAdapter extends RecyclerView.Adapter<LogViewHolder> {
        private List<String> logs = new ArrayList<>();
        
        public void setLogs(List<String> logs) {
            this.logs = logs;
            notifyDataSetChanged();
        }
        
        public void clearLogs() {
            logs.clear();
            notifyDataSetChanged();
        }
        
        public String getAllLogs() {
            StringBuilder sb = new StringBuilder();
            for (String log : logs) {
                sb.append(log).append("\n");
            }
            return sb.toString();
        }
        
        @NonNull
        @Override
        public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new LogViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
            holder.textView.setText(logs.get(position));
        }
        
        @Override
        public int getItemCount() {
            return logs.size();
        }
    }
    
    private static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        
        LogViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }
}