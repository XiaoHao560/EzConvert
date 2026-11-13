package com.tech.ezconvert.ui.components;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;
import com.tech.ezconvert.utils.ThemeManager;

/** 管理可选的滚动半透明 Toolbar 效果，不参与窗口 Insets 处理 */
public final class ToolbarAppearanceHelper {

    private ToolbarAppearanceHelper() {
    }

    /** 根据页面的 Toolbar 容器和滚动位置渐显 Surface 背景 */
    public static void apply(
            @NonNull Activity activity,
            @Nullable MaterialToolbar toolbar,
            @Nullable View titleContainer,
            @Nullable View scrollView,
            boolean enabled) {
        if (!enabled || toolbar == null
                || !ThemeManager.getInstance(activity).shouldUseTranslucentToolbar()) {
            return;
        }

        View backgroundHost = titleContainer == null ? toolbar : titleContainer;
        ViewParent parent = toolbar.getParent();
        AppBarLayout appBarLayout = parent instanceof AppBarLayout
                ? (AppBarLayout) parent
                : null;
        if (appBarLayout != null) {
            backgroundHost = appBarLayout;
            // 顶部栏不参与折叠，只根据内容滚动位置改变背景透明度
            appBarLayout.setLiftOnScroll(false);
            appBarLayout.setElevation(0f);
            appBarLayout.setStateListAnimator(null);
            appBarLayout.setStatusBarForeground(null);
        }

        int surfaceColor = MaterialColors.getColor(
                toolbar,
                com.google.android.material.R.attr.colorSurface);

        backgroundHost.setBackgroundColor(Color.TRANSPARENT);
        if (backgroundHost != toolbar) {
            backgroundHost.setElevation(0f);
            backgroundHost.setStateListAnimator(null);
        }
        toolbar.setBackgroundColor(Color.TRANSPARENT);
        toolbar.setElevation(0f);
        toolbar.setStateListAnimator(null);

        if (scrollView == null) {
            return;
        }

        View targetHost = backgroundHost;
        Runnable updateBackground = () -> {
            int scrollY = getScrollY(scrollView);
            int transitionDistance = Math.max(1, Math.round(
                    32 * activity.getResources().getDisplayMetrics().density));
            int alpha = Math.min(0xB8, scrollY * 0xB8 / transitionDistance);
            int backgroundColor = Color.argb(
                    alpha,
                    Color.red(surfaceColor),
                    Color.green(surfaceColor),
                    Color.blue(surfaceColor));
            targetHost.setBackgroundColor(backgroundColor);
        };

        if (scrollView instanceof RecyclerView) {
            ((RecyclerView) scrollView).addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    updateBackground.run();
                }
            });
        } else {
            ViewTreeObserver observer = scrollView.getViewTreeObserver();
            observer.addOnScrollChangedListener(updateBackground::run);
        }

        updateBackground.run();
    }

    private static int getScrollY(@NonNull View scrollView) {
        if (scrollView instanceof RecyclerView) {
            return ((RecyclerView) scrollView).computeVerticalScrollOffset();
        }
        if (scrollView instanceof NestedScrollView || scrollView instanceof android.widget.ScrollView) {
            return Math.max(0, scrollView.getScrollY());
        }
        return 0;
    }
}
