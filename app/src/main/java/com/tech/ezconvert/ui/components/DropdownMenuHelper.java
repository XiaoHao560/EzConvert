package com.tech.ezconvert.ui.components;

import android.content.Context;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.tech.ezconvert.R;

/** 统一初始化 Material 3 Exposed Dropdown Menu */
public final class DropdownMenuHelper {

    private DropdownMenuHelper() {
    }

    /**
     * 使用标准 ArrayAdapter 初始化下拉选项
     * 选项值由业务配置恢复，因此关闭 View 自身状态保存，避免重建时恢复文本触发 Adapter 过滤
     */
    public static void setItems(
            @NonNull Context context,
            @NonNull MaterialAutoCompleteTextView view,
            @NonNull String[] items,
            String selectedValue) {
        view.setSaveEnabled(false);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context,
                R.layout.item_dropdown,
                items);
        adapter.setDropDownViewResource(R.layout.item_dropdown_popup);
        view.setAdapter(adapter);
        view.setText(selectedValue == null ? "" : selectedValue, false);
    }
}
