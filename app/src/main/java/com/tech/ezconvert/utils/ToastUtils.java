package com.tech.ezconvert.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.color.MaterialColors;
import com.tech.ezconvert.R;

/**
 * 自定义Toast工具类
 */
public class ToastUtils {

    private static Toast currentToast;
    private static boolean isForeground = true; // 默认视为前台，避免安装后立即后台调用出现异常

    private ToastUtils() {
        throw new UnsupportedOperationException("工具类不可实例化");
    }

    /**
     * 设置应用前后台状态
     */
    public static void setForeground(boolean foreground) {
        isForeground = foreground;
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
        // 取消前一个 Toast
        if (currentToast != null) {
            currentToast.cancel();
        }
        
        Context appContext = context.getApplicationContext();
        View layout = LayoutInflater.from(appContext)
                .inflate(R.layout.custom_toast, null);
        TextView textView = layout.findViewById(R.id.toast_text);
        textView.setText(message);
        
        // 应用 Material You 动态取色
        applyMaterialYouColors(layout, context);
        
        currentToast = new Toast(appContext);
        currentToast.setDuration(duration);
        currentToast.setView(layout);
        currentToast.show();
    }

    /**
     * 使用自定义 Toast - 根据前后台状态选择使用自定义 Toast 或者使用系统 Toast
     */
    private static void showToast(Context context, String message, int duration) {
        // 取消上一个 Toast (任何类型)
        if (currentToast != null) {
            currentToast.cancel();
        }

        Context appContext = context.getApplicationContext();

        // Android 11 及以上且在后台时，使用系统原生Toast
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isForeground) {
            currentToast = Toast.makeText(appContext, message, duration);
            currentToast.show();
            return;
        }

        // 前台（或低版本）使用自定义布局
        View layout = LayoutInflater.from(appContext)
                .inflate(R.layout.custom_toast, null);
        TextView textView = layout.findViewById(R.id.toast_text);
        textView.setText(message);

        // 应用 Material You 动态取色
        applyMaterialYouColors(layout, context);

        currentToast = new Toast(appContext);
        currentToast.setDuration(duration);
        currentToast.setView(layout);
        currentToast.show();
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
