package com.tech.ezconvert.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.tech.ezconvert.ui.components.DropdownMenuHelper;
import com.tech.ezconvert.ui.components.ToolbarAppearanceHelper;
import com.tech.ezconvert.ui.insets.WindowInsetsHelper;
import com.tech.ezconvert.utils.LanguageManager;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.ThemeManager;

/**
 * Activity 基类，统一处理应用语言、主题、系统栏和页面级通用 UI 行为
 * 页面只需声明顶部安全区域与底部安全区域对应的容器 ID
 */
public abstract class BaseActivity extends AppCompatActivity {

    private String appearanceStateKey;
    private boolean appearanceRecreationPending;

    /** 返回顶部栏或顶部栏外层容器的 ID */
    protected int getTitleContainerId() {
        return View.NO_ID;
    }

    /** 返回可滚动内容区域的 ID，默认同时作为底部安全区域容器 */
    protected int getScrollContentId() {
        return View.NO_ID;
    }

    /** 返回需要避开导航栏和键盘的底部操作区域 ID */
    protected int getBottomInsetContainerId() {
        return getScrollContentId();
    }

    /** 是否允许当前 Activity 使用动态取色和外观状态管理 */
    protected boolean shouldApplyThemeCustomizations() {
        return true;
    }

    /** 是否允许当前 Activity 应用用户选择的自定义背景 */
    protected boolean shouldApplyCustomBackground() {
        return true;
    }

    /** 是否允许当前 Activity 使用全局半透明 Toolbar 效果 */
    protected boolean shouldApplyTranslucentToolbar() {
        return true;
    }

    /** 是否将左右系统栏 Insets 应用到页面根布局 */
    protected boolean shouldApplyHorizontalInsetsToRoot() {
        return true;
    }

    /** 外观配置变化后重建当前页面，子类可按自身窗口特性调整重建方式 */
    protected void recreateForAppearanceChange() {
        recreate();
    }

    /** 当前系统栏背景是否适合使用深色系统图标，默认跟随应用明暗主题 */
    protected boolean shouldUseDarkSystemBarIcons() {
        return isLightTheme();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 尽早设置 Edge-to-Edge，避免 Android 15 Activity 重建时 DecorView 初始化顺序导致 Insets 丢失
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        if (shouldApplyThemeCustomizations()) {
            ThemeManager themeManager = ThemeManager.getInstance(this);
            try {
                themeManager.applyDynamicColorToActivityIfNeeded(this);
            } catch (Throwable e) {
                Log.e("BaseActivity", "应用动态取色失败，已回退到普通主题", e);
            }
            try {
                appearanceStateKey = themeManager.getAppearanceStateKey();
            } catch (Throwable e) {
                Log.e("BaseActivity", "读取外观状态失败，当前页面跳过自动外观同步", e);
                appearanceStateKey = null;
            }
        }

        configureEdgeToEdgeWindow();
        setupSystemBarAppearance();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageManager.applySavedLanguage(newBase));
    }

    /** 所有 Activity 使用透明系统栏，具体安全区域由 WindowInsetsHelper 分发 */
    private void configureEdgeToEdgeWindow() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        // Android 10 及以下需要 adjustResize 才能稳定收到键盘 Insets
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);

        if (shouldApplyThemeCustomizations() && shouldApplyCustomBackground()) {
            try {
                ThemeManager.getInstance(this).applyCustomBackgroundIfNeeded(this);
            } catch (Throwable e) {
                Log.e("BaseActivity", "应用自定义背景失败，已跳过背景效果", e);
            }
        }

        View root = getContentRoot();
        if (root == null) {
            return;
        }

        applyWindowInsets(root);
        applyToolbarAppearance(root);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (!shouldApplyThemeCustomizations() || appearanceStateKey == null
                || appearanceRecreationPending) {
            return;
        }

        try {
            String currentStateKey = ThemeManager.getInstance(this).getAppearanceStateKey();
            if (!appearanceStateKey.equals(currentStateKey)) {
                // 不在 onResume 生命周期回调中直接 recreate，避免窗口尚未完成恢复时触发 Activity relaunch
                appearanceStateKey = currentStateKey;
                appearanceRecreationPending = true;
                getWindow().getDecorView().post(() -> {
                    if (isFinishing() || isDestroyed()) {
                        appearanceRecreationPending = false;
                        return;
                    }
                    recreateForAppearanceChange();
                });
            }
        } catch (Throwable e) {
            Log.e("BaseActivity", "检查外观状态失败，跳过页面重建", e);
        }
    }

    private View getContentRoot() {
        View content = findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) {
            return content;
        }
        ViewGroup contentGroup = (ViewGroup) content;
        return contentGroup.getChildCount() > 0 ? contentGroup.getChildAt(0) : content;
    }

    /** 按页面声明的容器统一分发顶部、底部和键盘 Insets */
    private void applyWindowInsets(@NonNull View root) {
        View titleView = getTitleContainerId() == View.NO_ID
                ? null
                : findViewById(getTitleContainerId());
        View topTarget = resolveTopInsetTarget(root, titleView);

        int bottomTargetId = getBottomInsetContainerId();
        View bottomTarget = bottomTargetId == View.NO_ID
                ? root
                : findViewById(bottomTargetId);
        if (bottomTarget == null) {
            bottomTarget = root;
        }

        WindowInsetsHelper.apply(
                root,
                topTarget,
                bottomTarget,
                shouldApplyHorizontalInsetsToRoot());
    }

    /** Toolbar 位于 AppBarLayout 中时，将顶部安全区域应用到 AppBar，而不是挤压 Toolbar 内容 */
    private View resolveTopInsetTarget(@NonNull View root, View titleView) {
        if (titleView == null) {
            return root;
        }
        if (titleView instanceof MaterialToolbar) {
            ViewParent parent = titleView.getParent();
            if (parent instanceof AppBarLayout) {
                return (View) parent;
            }
        }
        return titleView;
    }

    private void applyToolbarAppearance(@NonNull View root) {
        if (!shouldApplyThemeCustomizations()) {
            return;
        }

        View titleContainer = getTitleContainerId() == View.NO_ID
                ? null
                : findViewById(getTitleContainerId());
        MaterialToolbar toolbar = findToolbar(titleContainer);
        if (toolbar == null) {
            return;
        }

        // 使用当前 Activity 主题中的动态色调，避免 Toolbar 标题固定为白色或静态品牌色
        toolbar.setTitleTextColor(MaterialColors.getColor(
                toolbar,
                com.google.android.material.R.attr.colorOnSurfaceVariant));

        View scrollContainer = findScrollContainer(root);
        ToolbarAppearanceHelper.apply(
                this,
                toolbar,
                titleContainer,
                scrollContainer,
                shouldApplyTranslucentToolbar());
    }

    private MaterialToolbar findToolbar(View view) {
        if (view instanceof MaterialToolbar) {
            return (MaterialToolbar) view;
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }

        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            MaterialToolbar toolbar = findToolbar(group.getChildAt(i));
            if (toolbar != null) {
                return toolbar;
            }
        }
        return null;
    }

    /** 优先使用页面中声明的滚动区域，再回退到布局中的第一个常见滚动容器 */
    private View findScrollContainer(@NonNull View root) {
        int scrollContentId = getScrollContentId();
        if (scrollContentId != View.NO_ID) {
            View configuredView = findViewById(scrollContentId);
            if (isScrollContainer(configuredView)) {
                return configuredView;
            }
            ViewParent parent = configuredView == null ? null : configuredView.getParent();
            while (parent instanceof View) {
                if (isScrollContainer((View) parent)) {
                    return (View) parent;
                }
                parent = parent.getParent();
            }
        }
        return findScrollContainerRecursively(root);
    }

    private View findScrollContainerRecursively(View view) {
        if (isScrollContainer(view)) {
            return view;
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }

        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View result = findScrollContainerRecursively(group.getChildAt(i));
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private boolean isScrollContainer(View view) {
        return view instanceof NestedScrollView
                || view instanceof ScrollView
                || view instanceof RecyclerView;
    }

    private void setupSystemBarAppearance() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        if (controller == null) {
            return;
        }

        // 崩溃页面跳过 ThemeManager，避免配置异常形成二次崩溃循环
        boolean useDarkIcons;
        try {
            useDarkIcons = shouldApplyThemeCustomizations()
                    ? shouldUseDarkSystemBarIcons()
                    : isConfigurationLightTheme();
        } catch (Throwable e) {
            Log.e("BaseActivity", "读取系统栏图标颜色失败，回退到系统当前主题", e);
            useDarkIcons = isConfigurationLightTheme();
        }
        controller.setAppearanceLightStatusBars(useDarkIcons);
        controller.setAppearanceLightNavigationBars(useDarkIcons);
    }

    /** 根据应用主题模式确定系统栏图标颜色 */
    private boolean isLightTheme() {
        int themeMode = ThemeManager.getInstance(this).getThemeMode();
        if (themeMode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO) {
            return true;
        }
        if (themeMode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES) {
            return false;
        }
        return isConfigurationLightTheme();
    }

    /** 不读取应用配置，仅根据当前 Activity 已应用的系统主题判断明暗模式 */
    private boolean isConfigurationLightTheme() {
        int currentNightMode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode != Configuration.UI_MODE_NIGHT_YES;
    }

    /** 使用项目统一的 Material 3 Exposed Dropdown Menu 初始化方式 */
    protected void setupDropdown(
            @NonNull MaterialAutoCompleteTextView dropdown,
            @NonNull String[] items,
            String selectedValue) {
        DropdownMenuHelper.setItems(this, dropdown, items, selectedValue);
    }
}
