package com.tech.ezconvert.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.tech.ezconvert.R;
import java.util.ArrayList;
import java.util.List;

public class LogViewerActivity extends AppCompatActivity {

    private static final List<String> logBuffer = new ArrayList<>();
    private static LogAdapter adapter;

    private RecyclerView recyclerView;
    private ImageButton clearBtn, copyBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_viewer);

        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("运行日志");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.log_recycler_view);
        clearBtn     = findViewById(R.id.btn_clear_log);
        copyBtn      = findViewById(R.id.btn_copy_log);

        adapter = new LogAdapter(logBuffer);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        clearBtn.setOnClickListener(v -> {
            logBuffer.clear();
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "日志已清除", Toast.LENGTH_SHORT).show();
        });

        copyBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("FFmpegLog", String.join("\n", logBuffer));
            cm.setPrimaryClip(clip);
            Toast.makeText(this, "日志已复制", Toast.LENGTH_SHORT).show();
        });
    }

    public static void appendLog(String line) {
        logBuffer.add(line);
        if (adapter != null) adapter.notifyItemInserted(logBuffer.size() - 1);
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

    private static class LogAdapter extends RecyclerView.Adapter<LogAdapter.Holder> {
        private final List<String> list;
        LogAdapter(List<String> list) { this.list = list; }
        @Override public Holder onCreateViewHolder(android.view.ViewGroup p, int vType) {
            TextView tv = new TextView(p.getContext());
            tv.setPadding(12, 8, 12, 8);
            tv.setTextSize(12);
            return new Holder(tv);
        }
        @Override public void onBindViewHolder(Holder h, int i) { ((TextView) h.itemView).setText(list.get(i)); }
        @Override public int getItemCount() { return list.size(); }
        static class Holder extends RecyclerView.ViewHolder { Holder(android.view.View v) { super(v); } }
    }
}