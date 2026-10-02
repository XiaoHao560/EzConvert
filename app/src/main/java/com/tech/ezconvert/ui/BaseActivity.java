package com.tech.ezconvert.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ListView;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.LanguageManager;
import com.tech.ezconvert.utils.ThemeManager;

import java.util.ArrayList;
import java.util.Arrays;

public abstract class BaseActivity extends AppCompatActivity {

    // 记录本次 onCreate 时的主题/动态取色/自定义背景状态，用于 onResume 检测变更
    private String appearanceStateKey;

    // 子类可重写返回自定义背景色（默认白色）
    protected int getBackgroundColor() {
        return Color.WHITE;
    }

    /**
     * 是否允许当前 Activity 使用自定义背景、图片动态取色等增强主题功能。
     * 崩溃页面会关闭这些增强功能，避免异常页面再次触发同一个崩溃。
     */
    protected boolean shouldApplyThemeCustomizations() {
        return true;
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
        if (shouldApplyThemeCustomizations()) {
            try {
                ThemeManager.getInstance(this).applyDynamicColorToActivityIfNeeded(this);
            } catch (Throwable e) {
                // 主题增强不能阻止 Activity 启动。
                android.util.Log.e("BaseActivity", "应用动态取色失败，已回退到普通主题", e);
            }
        }
        
        // 崩溃页面等最小 Activity 不读取主题配置，避免配置损坏时形成二次崩溃循环。
        if (shouldApplyThemeCustomizations()) {
            // 记录本次创建时的外观状态，用于返回时检测主题设置是否发生变化
            appearanceStateKey = ThemeManager.getInstance(this).getAppearanceStateKey();
        } else {
            appearanceStateKey = null;
        }

        // 启用沉浸式
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // 根据主题设置自动切换状态栏/导航栏图标颜色
        if (shouldApplyThemeCustomizations()) {
            setupSystemBarAppearance();
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        // 应用保存的语言设置
        Context context = LanguageManager.applySavedLanguage(newBase);
        super.attachBaseContext(context);
    }

    /**
     * 根据滚动位置控制顶部栏的透明度
     */
    private void applyToolbarAppearance() {
        View titleContainer = findViewById(getTitleContainerId());
        if (!(titleContainer instanceof MaterialToolbar)) {
            return;
        }

        ThemeManager themeManager = ThemeManager.getInstance(this);
        boolean translucent = themeManager.shouldUseTranslucentToolbar();
        if (!translucent) {
            // 未开启半透明效果时完全保留布局和 MaterialToolbar 原本的外观
            return;
        }

        int surfaceColor = resolveThemeColor(
                com.google.android.material.R.attr.colorSurface, Color.WHITE);
        View backgroundHost = titleContainer;
        AppBarLayout appBarLayout = null;
        ViewParent parent = titleContainer.getParent();
        if (parent instanceof AppBarLayout) {
            appBarLayout = (AppBarLayout) parent;
            backgroundHost = appBarLayout;

            // 顶部栏不参与折叠，只根据内容滚动位置改变背景透明度
            appBarLayout.setLiftOnScroll(false);
            appBarLayout.setElevation(0f);
            appBarLayout.setStateListAnimator(null);
            appBarLayout.setStatusBarForeground(null);
        }

        // 半透明模式下 Toolbar 本身保持完全透明，避免与 AppBarLayout 形成两层背景
        titleContainer.setBackgroundColor(Color.TRANSPARENT);
        titleContainer.setElevation(0f);
        titleContainer.setStateListAnimator(null);

        if (appBarLayout != null) {
            setupToolbarScrollBackground(appBarLayout, surfaceColor);
        } else {
            backgroundHost.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    /**
     * Activity 位于顶部时保持顶部栏透明，开始滚动后逐渐显示半透明背景
     */
    private void setupToolbarScrollBackground(AppBarLayout appBarLayout, int surfaceColor) {
        View scrollView = findScrollContainer();
        if (scrollView == null) {
            appBarLayout.setBackgroundColor(Color.TRANSPARENT);
            return;
        }

        Runnable updateBackground = () -> {
            int scrollY = Math.max(0, getScrollY(scrollView));
            int transitionDistance = Math.max(1, dpToPx(32));
            int alpha = Math.min(0xB8, scrollY * 0xB8 / transitionDistance);
            int backgroundColor = Color.argb(
                    alpha,
                    Color.red(surfaceColor),
                    Color.green(surfaceColor),
                    Color.blue(surfaceColor));
            appBarLayout.setBackgroundColor(backgroundColor);
        };

        if (scrollView instanceof NestedScrollView) {
            ((NestedScrollView) scrollView).setOnScrollChangeListener(
                    (NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) -> updateBackground.run());
        } else if (scrollView instanceof ScrollView) {
            scrollView.getViewTreeObserver().addOnScrollChangedListener(updateBackground::run);
        } else if (scrollView instanceof RecyclerView) {
            ((RecyclerView) scrollView).addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    updateBackground.run();
                }
            });
        }

        // 初始状态必须根据实际滚动位置设置，避免页面打开时顶部栏先出现背景色
        updateBackground.run();
    }

    private int getScrollY(View scrollView) {
        if (scrollView instanceof RecyclerView) {
            return scrollView.canScrollVertically(-1) ? 1 : 0;
        }
        return scrollView.getScrollY();
    }

    /**
     * 优先使用 Activity 指定的滚动容器，否则从布局中查找第一个常见滚动容器
     */
    private View findScrollContainer() {
        int scrollContentId = getScrollContentId();
        if (scrollContentId != View.NO_ID) {
            View configuredView = findViewById(scrollContentId);
            if (configuredView instanceof NestedScrollView ||
                    configuredView instanceof ScrollView ||
                    configuredView instanceof RecyclerView) {
                return configuredView;
            }
        }

        return findScrollContainer(findViewById(android.R.id.content));
    }

    private View findScrollContainer(View view) {
        if (view instanceof NestedScrollView || view instanceof ScrollView || view instanceof RecyclerView) {
            return view;
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }

        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View result = findScrollContainer(group.getChildAt(i));
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private int resolveThemeColor(int attribute, int fallback) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (getTheme().resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) {
                try {
                    return getResources().getColor(value.resourceId, getTheme());
                } catch (Exception ignored) {
                }
            }
            return value.data;
        }
        return fallback;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!shouldApplyThemeCustomizations()) {
            return;
        }

        // 当用户从设置页返回时，检测主题、动态取色来源和自定义背景配置是否发生变化
        String appearanceStateKeyNow = ThemeManager.getInstance(this).getAppearanceStateKey();
        if (appearanceStateKey == null || !appearanceStateKey.equals(appearanceStateKeyNow)) {
            // 配置已变更，重建当前 Activity 以应用最新主题和背景
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

    // 自动分配稳定 ID
    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        // 应用用户选择的自定义背景（如果启用且已选择图片）
        if (shouldApplyThemeCustomizations()) {
            try {
                ThemeManager.getInstance(this).applyCustomBackgroundIfNeeded(this);
            } catch (Throwable e) {
                // 背景属于可选增强功能，任何异常都不能阻止正常 UI 显示。
                android.util.Log.e("BaseActivity", "应用自定义背景失败，已跳过背景效果", e);
            }
        }
        if (shouldApplyThemeCustomizations()) {
            applyToolbarAppearance();
        }
        // 为滚动容器分配基于路径的稳定 ID
        assignStableIdsToScrollables(findViewById(android.R.id.content), new StringBuilder());
        // 沉浸式内边距处理
        applyWindowInsets();
    }

    /**
     * 递归遍历 View 树，为所有未设置 ID 的滚动容器分配稳定 ID
     * ID 基于“当前 Activity 类名 + 视图树路径”生成，保证跨重建和进程重启不变
     *
     * @param view 当前遍历的 View
     * @param path 从根到当前节点的路径描述，用于区分同一布局中的多个滚动控件
     */
    private void assignStableIdsToScrollables(View view, StringBuilder path) {
        if (view == null) return;

        // 构建当前节点的路径片段：父布局中的索引 + 类名
        String currentSegment;
        if (view.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) view.getParent();
            int index = parent.indexOfChild(view);
            currentSegment = index + "_" + view.getClass().getSimpleName();
        } else {
            currentSegment = "root_" + view.getClass().getSimpleName();
        }

        int pathLen = path.length();
        if (pathLen > 0) path.append("_");
        path.append(currentSegment);

        // 如果当前 View 是滚动容器且没有 ID，则分配一个稳定的 ID
        if ((view instanceof ScrollView || view instanceof NestedScrollView ||
             view instanceof RecyclerView || view instanceof ListView) &&
             view.getId() == View.NO_ID) {

            String uniqueKey = getClass().getName() + "_" + path.toString();
            int stableId = uniqueKey.hashCode();
            // 确保 ID 不是 -1（View.NO_ID），且为正数
            if (stableId == View.NO_ID) stableId = 1;
            if (stableId < 0) stableId = -stableId;

            view.setId(stableId);
        }

        // 递归遍历子 View
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                assignStableIdsToScrollables(group.getChildAt(i), path);
            }
        }

        // 回溯，恢复路径到当前节点之前的状态
        path.setLength(pathLen);
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