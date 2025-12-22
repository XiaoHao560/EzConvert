package com.tech.ezconvert.ui;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public abstract class BaseActivity extends AppCompatActivity {

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
        
        // 启用沉浸式
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        // 自动设置状态栏/导航栏图标颜色（反色）
        setupSystemBarAppearance();
    }

    private void setupSystemBarAppearance() {
        View decorView = getWindow().getDecorView();
        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(decorView);
        
        if (controller != null) {
            // 检测背景是否为浅色，自动切换图标颜色为深色
            boolean isLightBackground = isLightColor(getBackgroundColor());
            controller.setAppearanceLightStatusBars(isLightBackground);
            controller.setAppearanceLightNavigationBars(isLightBackground);
        }
    }

    // 判断颜色是否为浅色
    private boolean isLightColor(int color) {
        double darkness = (0.299 * Color.red(color) + 
                          0.587 * Color.green(color) + 
                          0.114 * Color.blue(color)) / 255;
        return darkness > 0.5; // > 0.5 认为是浅色
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
}
