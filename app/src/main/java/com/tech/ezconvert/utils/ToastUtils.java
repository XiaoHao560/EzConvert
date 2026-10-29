package com.tech.ezconvert.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.color.MaterialColors;
import com.tech.ezconvert.R;

import java.lang.ref.WeakReference;

/**
 * 自定义Toast工具类
 */
public class ToastUtils {

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static Toast currentToast;
    private static volatile boolean isForeground = true; // 默认视为前台，避免安装后立即后台调用出现异常
    private static WeakReference<Activity> resumedActivity = new WeakReference<>(null);

    private ToastUtils() {
        throw new UnsupportedOperationException("工具类不可实例化");
    }

    /**
     * 设置应用前后台状态
     */
    public static void setForeground(boolean foreground) {
        isForeground = foreground;
    }

    /** 记录当前处于 Resumed 状态的 Activity，为后台管理器触发的前台 Toast 提供主题上下文 */
    public static void setCurrentActivity(Activity activity) {
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            resumedActivity = new WeakReference<>(activity);
        }
    }

    /** 仅清理匹配的 Activity，避免旧页面暂停时清除新页面的主题上下文 */
    public static void clearCurrentActivity(Activity activity) {
        Activity current = resumedActivity.get();
        if (current == activity) {
            resumedActivity.clear();
            resumedActivity = new WeakReference<>(null);
        }
    }

    private static Context resolveThemeContext(Context fallback) {
        if (fallback instanceof Activity) {
            Activity activity = (Activity) fallback;
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                return activity;
            }
        }

        Activity activity = resumedActivity.get();
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            return activity;
        }
        return fallback;
    }

    public static void show(Context context, String message) {
        showToast(context, message, Toast.LENGTH_SHORT);
    }

    public static void showLong(Context context, String message) {
        showToast(context, message, Toast.LENGTH_LONG);
    }

    public static void showCustom(Context context, String message) {
    	showCustomToast(context, message, Toast.LENGTH_SHORT);
    }
    
    public static void showCustomLong(Context context, String message) {
    	showCustomToast(context, message, Toast.LENGTH_LONG);
    }

    /**
     * 使用自定义 Toast - 不做前后台判断
     */
    private static void showCustomToast(Context context, String message, int duration) {
        if (context == null || message == null || message.isEmpty()) return;
        MAIN_HANDLER.post(() -> showCustomToastOnMainThread(context, message, duration));
    }

    private static void showCustomToastOnMainThread(Context context, String message, int duration) {
        cancelCurrentToast();
        Context appContext = context.getApplicationContext();
        Context themeContext = resolveThemeContext(context);
        View layout = LayoutInflater.from(themeContext)
                .inflate(R.layout.custom_toast, null);
        TextView textView = layout.findViewById(R.id.toast_text);
        textView.setText(message);

        // 使用当前 Activity 的主题读取动态颜色，后台任务传入 Application Context 时也能保持前台配色
        applyMaterialYouColors(layout, themeContext);

        currentToast = new Toast(appContext);
        currentToast.setDuration(duration);
        currentToast.setView(layout);
        currentToast.show();
    }

    /**
     * 使用自定义 Toast - 根据前后台状态选择使用自定义 Toast 或者使用系统 Toast；统一在主线程执行
     */
    private static void showToast(Context context, String message, int duration) {
        if (context == null || message == null || message.isEmpty()) return;
        MAIN_HANDLER.post(() -> showToastOnMainThread(context, message, duration));
    }

    private static void showToastOnMainThread(Context context, String message, int duration) {
        cancelCurrentToast();
        Context appContext = context.getApplicationContext();

        // Android 11 及以上且在后台时，使用系统原生Toast
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isForeground) {
            currentToast = Toast.makeText(appContext, message, duration);
            currentToast.show();
            return;
        }

        // 前台（或低版本）使用自定义布局
        Context themeContext = resolveThemeContext(context);
        View layout = LayoutInflater.from(themeContext)
                .inflate(R.layout.custom_toast, null);
        TextView textView = layout.findViewById(R.id.toast_text);
        textView.setText(message);

        // 使用当前 Activity 的主题读取动态颜色，后台任务传入 Application Context 时也能保持前台配色
        applyMaterialYouColors(layout, themeContext);

        currentToast = new Toast(appContext);
        currentToast.setDuration(duration);
        currentToast.setView(layout);
        currentToast.show();
    }

    // 取消上一个 Toast
    private static void cancelCurrentToast() {
        if (currentToast != null) {
            currentToast.cancel();
            currentToast = null;
        }
    }

    /**
     * 应用 Material You 动态颜色到 Toast 布局
     */
    private static void applyMaterialYouColors(View layout, Context context) {
        TextView textView = layout.findViewById(R.id.toast_text);
        if (textView == null) return;

        try {
            // 获取 Material 3 容器背景色与对应文字色，fallback 为 0 表示获取失败时不覆盖
            int bgColor = MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorSecondaryContainer,
                    0);
            int textColor = MaterialColors.getColor(context,
                    com.google.android.material.R.attr.colorOnSecondaryContainer,
                    0);

            // 对背景 Drawable 进行 Tint 着色，实现动态取色适配
            if (bgColor != 0) {
                Drawable background = textView.getBackground();
                if (background != null) {
                    background = background.mutate();
                    DrawableCompat.setTint(background, bgColor);
                    textView.setBackground(background);
                }
            }
            
            // 应用动态文字颜色
            if (textColor != 0) {
                textView.setTextColor(textColor);
            }
        } catch (Exception e) {
            // 解析失败时保持 XML 默认颜色，确保不崩溃
            e.printStackTrace();
        }
    }
}
