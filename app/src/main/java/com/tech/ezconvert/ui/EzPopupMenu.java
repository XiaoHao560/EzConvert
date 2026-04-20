package com.tech.ezconvert.ui;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import com.google.android.material.button.MaterialButton;
import com.tech.ezconvert.R;

/**
 * MD3 风格菜单，使用 MaterialCardView + TextButton 实现
 */
public class EzPopupMenu {

    private PopupWindow popupWindow;
    private View menuView;
    private OnMenuItemClickListener menuListener;
    private OnDismissListener dismissListener;

    // 菜单项点击回调
    public interface OnMenuItemClickListener {
        void onPreviewClick();
        void onSettingsClick();
    }

    // 菜单关闭回调
    public interface OnDismissListener {
        void onDismiss();
    }

    public EzPopupMenu(View anchorView, OnMenuItemClickListener menuListener, OnDismissListener dismissListener) {
        this.menuListener = menuListener;
        this.dismissListener = dismissListener;
        init(anchorView);
    }

    private void init(View anchorView) {
        menuView = LayoutInflater.from(anchorView.getContext())
                .inflate(R.layout.popup_menu_md3, null);

        popupWindow = new PopupWindow(
                menuView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setAnimationStyle(android.R.style.Animation_Dialog);

        // 关闭监听
        popupWindow.setOnDismissListener(() -> {
            if (dismissListener != null) {
                dismissListener.onDismiss();
            }
        });

        // 预览按钮
        MaterialButton previewBtn = menuView.findViewById(R.id.action_preview);
        previewBtn.setOnClickListener(v -> {
            if (menuListener != null) menuListener.onPreviewClick();
            dismiss();
        });

        // 设置按钮
        MaterialButton settingsBtn = menuView.findViewById(R.id.action_settings);
        settingsBtn.setOnClickListener(v -> {
            if (menuListener != null) menuListener.onSettingsClick();
            dismiss();
        });
    }

    public void show(View anchorView) {
        if (popupWindow != null && !popupWindow.isShowing()) {
            int[] location = new int[2];
            anchorView.getLocationOnScreen(location);
            int xOffset = location[0];
            int yOffset = location[1] + anchorView.getHeight();
            popupWindow.showAtLocation(anchorView, Gravity.TOP | Gravity.START, xOffset, yOffset);
        }
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }
}