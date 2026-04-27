package com.tech.ezconvert.utils;

import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

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
                .inflate(R.layout.layout_custom_toast, null);
        TextView textView = layout.findViewById(R.id.toast_text);
        textView.setText(message);

        currentToast = new Toast(appContext);
        currentToast.setDuration(duration);
        currentToast.setView(layout);
        currentToast.show();
    }
}