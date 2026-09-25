package com.tech.ezconvert.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.view.Gravity;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * 主题管理器
 * 负责应用主题模式、动态取色以及自定义背景的持久化与实时应用。
 */
public class ThemeManager {
    private static final String TAG = "ThemeManager";
    private static final int MAX_DYNAMIC_COLOR_BITMAP_SIZE = 512;
    private static final int MAX_BACKGROUND_BITMAP_SIZE = 2048;

    private static ThemeManager instance;
    private final ConfigManager configManager;

    // 当前自定义背景缓存；仅缓存最后一次使用的图片，避免每个 Activity 重复解码。
    private String cachedBackgroundUri;
    private Bitmap cachedBackgroundBitmap;

    private ThemeManager(Context context) {
        // 使用 Application Context 避免内存泄漏
        this.configManager = ConfigManager.getInstance(context.getApplicationContext());
    }

    public static synchronized ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context);
        }
        return instance;
    }

    // 应用当前保存的主题模式
    public void applySavedTheme() {
        int savedMode = configManager.getThemeMode();
        AppCompatDelegate.setDefaultNightMode(savedMode);
    }

    // 设置并立即应用主题模式，同时修改配置文件
    public void setThemeMode(int mode) {
        configManager.setThemeMode(mode);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    // 获取当前保存的主题模式
    public int getThemeMode() {
        return configManager.getThemeMode();
    }

    // 获取当前动态取色设置状态
    public boolean isDynamicColorEnabled() {
        return configManager.isDynamicColorEnabled();
    }

    // 设置动态取色开关状态
    public void setDynamicColorEnabled(boolean enabled) {
        configManager.setDynamicColorEnabled(enabled);
    }

    public String getDynamicColorSource() {
        return configManager.getDynamicColorSource();
    }

    public boolean isCustomBackgroundEnabled() {
        return configManager.isCustomBackgroundEnabled();
    }

    public void setCustomBackgroundEnabled(boolean enabled) {
        configManager.setCustomBackgroundEnabled(enabled);
    }

    public String getCustomBackgroundUri() {
        return configManager.getCustomBackgroundUri();
    }

    /**
     * 返回会影响当前 Activity 外观的状态签名。
     * BaseActivity 用它判断用户从设置页返回后是否需要重建页面。
     */
    public String getAppearanceStateKey() {
        return configManager.getThemeMode() + "|"
                + configManager.isDynamicColorEnabled() + "|"
                + configManager.getDynamicColorSource() + "|"
                + configManager.isCustomBackgroundEnabled() + "|"
                + configManager.getCustomBackgroundUri();
    }

    /**
     * 为指定 Activity 应用动态取色。
     * 调用时必须位于 super.onCreate() 之后、setContentView() 之前。
     *
     * 图片来源模式使用 Material Components 的 content-based dynamic color；
     * 壁纸模式继续使用现有系统 Material You 动态取色。
     */
    public void applyDynamicColorToActivityIfNeeded(Activity activity) {
        if (!configManager.isDynamicColorEnabled() || !DynamicColors.isDynamicColorAvailable()) {
            return;
        }

        if (configManager.isCustomBackgroundEnabled()
                && ConfigManager.DYNAMIC_COLOR_SOURCE_IMAGE.equals(configManager.getDynamicColorSource())) {
            Bitmap bitmap = loadBitmapForDynamicColor(activity);
            if (bitmap != null) {
                try {
                    DynamicColorsOptions options = new DynamicColorsOptions.Builder()
                            .setContentBasedSource(bitmap)
                            .build();
                    DynamicColors.applyToActivityIfAvailable(activity, options);
                    return;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to apply image-based dynamic color", e);
                } finally {
                    // DynamicColors 会在本次调用中提取颜色，调用后不再需要这份临时 bitmap。
                    bitmap.recycle();
                }
            }
        }

        // 图片不存在、读取失败，或当前选择为壁纸时，安全回退到系统壁纸取色。
        DynamicColors.applyToActivityIfAvailable(activity);
    }

    /**
     * 将用户选择的图片应用到 Activity 根布局。
     * 自定义背景和动态取色彼此独立：关闭自定义背景时不会显示图片，
     * 但之前选择的图片和取色来源仍会保留，重新开启即可恢复。
     */
    public void applyCustomBackgroundIfNeeded(Activity activity) {
        if (!configManager.isCustomBackgroundEnabled()) {
            return;
        }

        String uriString = configManager.getCustomBackgroundUri();
        if (uriString == null || uriString.isEmpty()) {
            return;
        }

        Bitmap bitmap = getCachedBackgroundBitmap(activity, uriString);
        if (bitmap == null) {
            return;
        }

        android.view.ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) {
            return;
        }

        android.view.View rootView = content.getChildAt(0);
        BitmapDrawable drawable = new BitmapDrawable(activity.getResources(), bitmap);
        drawable.setGravity(Gravity.FILL);
        rootView.setBackground(drawable);
    }

    /**
     * 切换动态取色设置后即时刷新当前 Activity。
     */
    public void applyDynamicColorAndRecreate(Activity activity) {
        activity.recreate();
    }

    private Bitmap loadBitmapForDynamicColor(Context context) {
        String uriString = configManager.getCustomBackgroundUri();
        if (uriString == null || uriString.isEmpty()) {
            return null;
        }
        return decodeBitmap(context, Uri.parse(uriString), MAX_DYNAMIC_COLOR_BITMAP_SIZE);
    }

    private Bitmap getCachedBackgroundBitmap(Context context, String uriString) {
        if (uriString.equals(cachedBackgroundUri) && cachedBackgroundBitmap != null
                && !cachedBackgroundBitmap.isRecycled()) {
            return cachedBackgroundBitmap;
        }

        // 不主动 recycle 旧 Bitmap：旧 Activity 可能仍持有它的 Drawable，
        // 提前回收会导致旧页面出现解码/渲染异常。由 GC 根据 Activity 生命周期回收即可。
        cachedBackgroundBitmap = decodeBitmap(context, Uri.parse(uriString), MAX_BACKGROUND_BITMAP_SIZE);
        cachedBackgroundUri = uriString;
        return cachedBackgroundBitmap;
    }

    private InputStream openImageInputStream(Context context, Uri uri) throws Exception {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return new FileInputStream(new File(uri.getPath()));
        }
        return context.getContentResolver().openInputStream(uri);
    }

    private Bitmap decodeBitmap(Context context, Uri uri, int maxSize) {
        try (InputStream boundsStream = openImageInputStream(context, uri)) {
            if (boundsStream == null) {
                return null;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(boundsStream, null, bounds);

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            int sampleSize = 1;
            int maxDimension = Math.max(bounds.outWidth, bounds.outHeight);
            while (maxDimension / sampleSize > maxSize) {
                sampleSize *= 2;
            }

            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inSampleSize = sampleSize;
            decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;

            try (InputStream imageStream = openImageInputStream(context, uri)) {
                if (imageStream == null) {
                    return null;
                }
                return BitmapFactory.decodeStream(imageStream, null, decodeOptions);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to decode custom background image: " + uri, e);
            return null;
        }
    }
}
