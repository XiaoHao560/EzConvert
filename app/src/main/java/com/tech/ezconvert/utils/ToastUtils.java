package com.tech.ezconvert.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.tech.ezconvert.R;

/**
 * 自定义Toast样式
 */
public class ToastUtils {

    private static Toast currentToast;
    
    private ToastUtils() {
        throw new UnsupportedOperationException("工具类不实例化");
    }
    
    public static void show(Context context, String message) {
        if (currentToast != null) {
            currentToast.cancel();
        }
        
        // 加载布局
        View layout = LayoutInflater.from(context.getApplicationContext())
                .inflate(R.layout.layout_custom_toast, null);
                
        TextView textView = layout.findViewById(R.id.toast_text);
        textView.setText(message);
        
        // 创建Toast
        currentToast = new Toast(context.getApplicationContext());
        currentToast.setDuration(Toast.LENGTH_SHORT);
        currentToast.setView(layout);
        currentToast.show();
    }
    
    // 显示长时Toast
    public static void showLong(Context context, String message) {
        if (currentToast != null) {
            currentToast.cancel();
        }
        
        View layout = LayoutInflater.from(context.getApplicationContext())
                .inflate(R.layout.layout_custom_toast, null);
                
        TextView textView = layout.findViewById(R.id.toast_text);
        textView.setText(message);
        
        currentToast = new Toast(context.getApplicationContext());
        currentToast.setDuration(Toast.LENGTH_LONG);
        currentToast.setView(layout);
        currentToast.show();
    }
}
