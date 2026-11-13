package com.tech.ezconvert.ui.insets;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;

import com.google.android.material.appbar.MaterialToolbar;
import androidx.core.view.WindowInsetsCompat;

/**
 * 为传统 View 页面提供统一的 Edge-to-Edge 安全区域处理
 * 顶部 Insets 应用到工具栏外层容器，底部 Insets 应用到可滚动内容或固定操作栏
 */
public final class WindowInsetsHelper {

    private WindowInsetsHelper() {
    }

    /**
     * 为根布局及指定的顶部、底部区域应用系统 Insets
     * 不消费 Insets，确保子 View 仍能接收 IME 等系统窗口信息
     */
    public static void apply(
            @NonNull View root,
            @Nullable View topInsetTarget,
            @Nullable View bottomInsetTarget) {
        apply(root, topInsetTarget, bottomInsetTarget, true);
    }

    /** 为根布局及指定的顶部、底部区域应用系统 Insets */
    public static void apply(
            @NonNull View root,
            @Nullable View topInsetTarget,
            @Nullable View bottomInsetTarget,
            boolean applyHorizontalInsetsToRoot) {
        final View topTarget = topInsetTarget == null ? root : topInsetTarget;
        final View bottomTarget = bottomInsetTarget == null ? root : bottomInsetTarget;

        final Padding rootPadding = new Padding(root);
        final Padding topPadding = topTarget == root ? rootPadding : new Padding(topTarget);
        final Padding bottomPadding = bottomTarget == root
                ? rootPadding
                : topTarget == bottomTarget ? topPadding : new Padding(bottomTarget);

        final int originalTopHeight = topTarget.getLayoutParams() == null
                ? ViewGroup.LayoutParams.WRAP_CONTENT
                : topTarget.getLayoutParams().height;

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets safeInsets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            int bottomInset = Math.max(safeInsets.bottom, imeInsets.bottom);
            int rootLeftInset = applyHorizontalInsetsToRoot ? safeInsets.left : 0;
            int rootRightInset = applyHorizontalInsetsToRoot ? safeInsets.right : 0;
            int targetLeftInset = applyHorizontalInsetsToRoot ? 0 : safeInsets.left;
            int targetRightInset = applyHorizontalInsetsToRoot ? 0 : safeInsets.right;

            root.setPadding(
                    rootPadding.left + rootLeftInset,
                    rootPadding.top + (topTarget == root ? safeInsets.top : 0),
                    rootPadding.right + rootRightInset,
                    rootPadding.bottom + (bottomTarget == root ? bottomInset : 0));

            if (topTarget != root && topTarget == bottomTarget) {
                // 同一个容器同时承担顶部和底部安全区域时，一次性合并 Padding，避免后一次设置覆盖前一次
                topTarget.setPadding(
                        topPadding.left + targetLeftInset,
                        topPadding.top + safeInsets.top,
                        topPadding.right + targetRightInset,
                        topPadding.bottom + bottomInset);
            } else {
                if (topTarget != root) {
                    topTarget.setPadding(
                            topPadding.left + targetLeftInset,
                            topPadding.top + safeInsets.top,
                            topPadding.right + targetRightInset,
                            topPadding.bottom);
                }

                if (bottomTarget != root) {
                    bottomTarget.setPadding(
                            bottomPadding.left + targetLeftInset,
                            bottomPadding.top,
                            bottomPadding.right + targetRightInset,
                            bottomPadding.bottom + bottomInset);
                }
            }

            // 固定高度的独立 Toolbar 需要额外增加顶部 Insets，避免 Padding 挤压标题和导航按钮
            if (topTarget instanceof MaterialToolbar && originalTopHeight >= 0) {
                ViewGroup.LayoutParams layoutParams = topTarget.getLayoutParams();
                int targetHeight = originalTopHeight + safeInsets.top;
                if (layoutParams.height != targetHeight) {
                    layoutParams.height = targetHeight;
                    topTarget.setLayoutParams(layoutParams);
                }
            }

            return windowInsets;
        });

        ViewCompat.requestApplyInsets(root);
    }

    /** 保存布局声明的原始 Padding，防止 Insets 重复分发时不断累加 */
    private static final class Padding {
        final int left;
        final int top;
        final int right;
        final int bottom;

        Padding(@NonNull View view) {
            left = view.getPaddingLeft();
            top = view.getPaddingTop();
            right = view.getPaddingRight();
            bottom = view.getPaddingBottom();
        }
    }
}
