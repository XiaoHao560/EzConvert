package com.tech.ezconvert.ui.components;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;
import com.tech.ezconvert.utils.ThemeManager;

/** 根据全局设置统一控制 Toolbar 的透明或默认背景，不参与窗口 Insets 处理 */
public final class ToolbarAppearanceHelper {

    private ToolbarAppearanceHelper() {
    }

    /** 开启透明 Toolbar 时移除背景；关闭时在自定义背景场景恢复 Material 3 Surface 背景 */
    public static void apply(
            @NonNull Activity activity,
            @Nullable MaterialToolbar toolbar,
            @Nullable View titleContainer,
            boolean applyGlobalSetting) {
        if (!applyGlobalSetting || toolbar == null) {
            return;
        }

        ThemeManager themeManager = ThemeManager.getInstance(activity);
        if (!themeManager.isCustomBackgroundEnabled()) {
            return;
        }

        View backgroundHost = titleContainer == null ? toolbar : titleContainer;
        ViewParent parent = toolbar.getParent();
        AppBarLayout appBarLayout = parent instanceof AppBarLayout
                ? (AppBarLayout) parent
                : null;
        if (appBarLayout != null) {
            backgroundHost = appBarLayout;
        }

        if (!themeManager.shouldUseTransparentToolbar()) {
            // 自定义背景下关闭透明效果时，使用不透背景的 Surface，避免背景图片穿透 Toolbar。
            int surfaceColor = MaterialColors.getColor(
                    toolbar,
                    com.google.android.material.R.attr.colorSurfaceContainer);
            backgroundHost.setBackgroundColor(surfaceColor);
            toolbar.setBackgroundColor(surfaceColor);
            return;
        }

        backgroundHost.setBackgroundColor(Color.TRANSPARENT);
        toolbar.setBackgroundColor(Color.TRANSPARENT);

        if (backgroundHost != toolbar) {
            backgroundHost.setElevation(0f);
            backgroundHost.setStateListAnimator(null);
        }
        toolbar.setElevation(0f);
        toolbar.setStateListAnimator(null);

        if (appBarLayout != null) {
            appBarLayout.setLiftOnScroll(false);
            appBarLayout.setStatusBarForeground(null);
        }
    }
}
