package com.tech.ezconvert.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.ThemeManager;
import java.util.ArrayList;
import java.util.Arrays;

public abstract class BaseActivity extends AppCompatActivity {

    // 记录本次 onCreate 时的动态取色状态，用于 onResume 检测变更
    private boolean wasDynamicColorEnabled;

    // 子类可重写返回自定义背景色（默认白色）
    protected int getBackgroundColor() {
        return Color.WHITE;
    }

    protected int getTitleContainerId() {
        return View.NO_ID;
    }

    protected int getScrollContentId() {
        return View.NO_ID;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 动态取色
        ThemeManager.getInstance(this).applyDynamicColorToActivityIfNeeded(this);
        
        // 记录本次创建时的动态取色开关状态 (用于返回时检测变化)
        wasDynamicColorEnabled = ThemeManager.getInstance(this).isDynamicColorEnabled()
                && DynamicColors.isDynamicColorAvailable();
        
        // 启用沉浸式
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        // 根据主题设置自动切换状态栏/导航栏图标颜色
        setupSystemBarAppearance();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 当用户从设置页返回时，检测动态取色配置是否发生变化
        boolean isDynamicColorEnabledNow = ThemeManager.getInstance(this).isDynamicColorEnabled()
                && DynamicColors.isDynamicColorAvailable();
        
        if (isDynamicColorEnabledNow != wasDynamicColorEnabled) {
            // 配置已变更，重建当前 Activity 以应用新主题色调
            recreate();
        }
    }

    private void setupSystemBarAppearance() {
        View decorView = getWindow().getDecorView();
        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(decorView);
        
        if (controller != null) {
            boolean isLightMode = isLightTheme();
            controller.setAppearanceLightStatusBars(isLightMode);
            controller.setAppearanceLightNavigationBars(isLightMode);
        }
    }

    /**
     * 根据应用主题设置判断当前是否为浅色主题
     * 浅色主题 -> 状态栏图标用深色 (light bars = true)
     * 深色主题 -> 状态栏图标用浅色 (light bars = false)
     */
    private boolean isLightTheme() {
        int themeMode = ThemeManager.getInstance(this).getThemeMode();
        
        if (themeMode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO) {
            // 强制浅色主题
            return true;
        } else if (themeMode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) {
            // 强制深色主题
            return false;
        } else {
            // 跟随系统：根据系统当前实际主题判断
            int currentNightMode = getResources().getConfiguration().uiMode 
                & Configuration.UI_MODE_NIGHT_MASK;
            return currentNightMode != Configuration.UI_MODE_NIGHT_YES;
        }
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        applyWindowInsets();
    }

    // 动态获取Padding内边距
    private void applyWindowInsets() {
        View decorView = getWindow().getDecorView();
        View titleContainer = findViewById(getTitleContainerId());
        View scrollContent = findViewById(getScrollContentId());

        if (titleContainer == null && scrollContent == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(decorView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // 上方
            if (titleContainer != null) {
                titleContainer.setPadding(
                    titleContainer.getPaddingLeft(),
                    systemBars.top,
                    titleContainer.getPaddingRight(),
                    titleContainer.getPaddingBottom()
                );
            }

            // 下方
            if (scrollContent != null) {
                scrollContent.setPadding(
                    scrollContent.getPaddingLeft(),
                    scrollContent.getPaddingTop(),
                    scrollContent.getPaddingRight(),
                    12 + systemBars.bottom
                );
            }

            return WindowInsetsCompat.CONSUMED;
        });

        if (titleContainer != null) {
            ViewCompat.requestApplyInsets(titleContainer);
        }
    }

    /**
    * 配置 Spinner
    * 此类用于修复重建 Activity 后，Spinner 显示不全的问题
    */
    protected void setupSpinner(MaterialAutoCompleteTextView spinner, String[] items, String defaultValue) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.item_dropdown, items) {
            private final Filter NO_FILTER = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    results.values = new ArrayList<>(Arrays.asList(items));
                    results.count = items.length;
                    return results;
                }
    
                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    notifyDataSetChanged();
                }
    
                @Override
                public CharSequence convertResultToString(Object resultValue) {
                    return resultValue != null ? resultValue.toString() : "";
                }
            };
    
            @Override
            public Filter getFilter() {
                return NO_FILTER;
            }
        };
        adapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        spinner.setAdapter(adapter);
        spinner.setThreshold(1);
        spinner.setText(defaultValue, false);
    }
}
