package com.tech.ezconvert.ui;

import android.os.Bundle;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.tech.ezconvert.R;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;

/** 展示自定义命令功能的完整使用说明 */
public class CustomCommandHelpActivity extends BaseActivity {

    @Override
    protected int getTitleContainerId() {
        return R.id.custom_command_help_toolbar;
    }

    @Override
    protected int getScrollContentId() {
        return R.id.custom_command_help_scroll;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_command_help);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

        MaterialToolbar toolbar = findViewById(R.id.custom_command_help_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        TextView contentView = findViewById(R.id.custom_command_help_content);
        Markwon markwon = Markwon.builder(this)
                .usePlugin(TablePlugin.create(this))
                .build();
        markwon.setMarkdown(contentView, getString(R.string.custom_command_help_content));
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}