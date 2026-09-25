package com.tech.ezconvert.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.util.TypedValue;

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
                + configManager.getCustomBackgroundUri() + "|"
                + configManager.getBackgroundEffectMode() + "|"
                + configManager.getBackgroundMaskAlpha() + "|"
                + configManager.getBackgroundBlurDp();
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
            Bitmap bitmap = null;
            try {
                bitmap = loadBitmapForDynamicColor(activity);
                if (bitmap != null) {
                    DynamicColorsOptions options = new DynamicColorsOptions.Builder()
                            .setContentBasedSource(bitmap)
                            .build();
                    DynamicColors.applyToActivityIfAvailable(activity, options);
                    return;
                }
            } catch (Throwable e) {
                Log.e(TAG, "图片动态取色失败，自动回退到壁纸取色", e);
                try {
                    configManager.setDynamicColorSource(ConfigManager.DYNAMIC_COLOR_SOURCE_WALLPAPER);
                } catch (Throwable ignored) {
                }
            } finally {
                // 不主动 recycle：不同 Material 版本内部可能仍持有该 Bitmap 的引用。
                // 该图像最多约 512x512，仅占约 1MB，由 GC 在 Activity 生命周期结束后回收。
            }
        }

        try {
            DynamicColors.applyToActivityIfAvailable(activity);
        } catch (Throwable e) {
            Log.e(TAG, "系统动态取色失败，继续使用普通主题", e);
        }
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

        try {
            Bitmap bitmap = getCachedBackgroundBitmap(activity, uriString);
            if (bitmap == null || bitmap.isRecycled()) {
                return;
            }

            android.view.ViewGroup content = activity.findViewById(android.R.id.content);
            if (content == null || content.getChildCount() == 0) {
                return;
            }

            android.view.View rootView = content.getChildAt(0);
            int blurDp = configManager.getBackgroundBlurDp();
            int maskAlphaPercent = configManager.getBackgroundMaskAlpha();

            if (ConfigManager.BACKGROUND_EFFECT_MODE_AUTO.equals(configManager.getBackgroundEffectMode())) {
                maskAlphaPercent = calculateAutoMaskPercent(bitmap, activity);
                blurDp = 8;
            }

            int maskColor = resolveColor(activity, com.google.android.material.R.attr.colorSurface,
                    activity.getResources().getColor(android.R.color.black));

            CustomBackgroundDrawable drawable = new CustomBackgroundDrawable(
                    activity, bitmap, maskColor, maskAlphaPercent, blurDp);
            rootView.setBackground(drawable);
        } catch (Throwable e) {
            Log.e(TAG, "应用自定义背景失败，自动关闭背景功能并回退到普通界面", e);
            try { configManager.setCustomBackgroundEnabled(false); } catch (Throwable ignored) { }
        }
    }

    private int calculateAutoMaskPercent(Bitmap bitmap, Activity activity) {
        float brightness = calculateAverageLuminance(bitmap);
        boolean lightTheme = isLightTheme(activity);

        float alpha;
        if (lightTheme) {
            // 浅色主题使用 colorSurface（通常为浅色）作为遮罩，
            // 图片越暗，遮罩越强，避免深色照片与深色文字混在一起。
            alpha = 0.10f + Math.max(0f, 0.62f - brightness) * 0.95f;
        } else {
            // 深色主题使用 colorSurface（通常为深色）作为遮罩，
            // 图片越亮，遮罩越强，避免白色文字与高亮背景冲突。
            alpha = 0.10f + Math.max(0f, brightness - 0.32f) * 0.85f;
        }
        alpha = Math.max(0.10f, Math.min(0.60f, alpha));
        return Math.round(alpha * 100f);
    }

    private float calculateAverageLuminance(Bitmap source) {
        int sampleWidth = Math.min(32, source.getWidth());
        int sampleHeight = Math.min(32, source.getHeight());
        Bitmap sample = Bitmap.createScaledBitmap(source, sampleWidth, sampleHeight, true);
        long total = 0;
        int count = sample.getWidth() * sample.getHeight();
        int[] pixels = new int[count];
        sample.getPixels(pixels, 0, sample.getWidth(), 0, 0, sample.getWidth(), sample.getHeight());
        for (int pixel : pixels) {
            int r = Color.red(pixel);
            int g = Color.green(pixel);
            int b = Color.blue(pixel);
            total += Math.round((0.2126f * r + 0.7152f * g + 0.0722f * b));
        }
        sample.recycle();
        return count == 0 ? 0.5f : (total / (float) count) / 255f;
    }

    private boolean isLightTheme(Activity activity) {
        int mode = configManager.getThemeMode();
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) {
            return true;
        }
        if (mode == AppCompatDelegate.MODE_NIGHT_YES) {
            return false;
        }
        int nightMode = activity.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightMode != android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private int resolveColor(Context context, int attr, int fallback) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) {
                try {
                    return context.getResources().getColor(value.resourceId, context.getTheme());
                } catch (Exception ignored) {
                }
            }
            return value.data;
        }
        return fallback;
    }

    private static class CustomBackgroundDrawable extends android.graphics.drawable.Drawable {
        private final Bitmap bitmap;
        private final Bitmap blurredBitmap;
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int maskColor;
        private final float maskAlpha;
        private final Matrix matrix = new Matrix();
        private final RectF destination = new RectF();

        CustomBackgroundDrawable(Context context, Bitmap bitmap, int maskColor, int maskAlphaPercent, int blurDp) {
            this.bitmap = bitmap;
            this.maskColor = maskColor;
            this.maskAlpha = Math.max(0f, Math.min(1f, maskAlphaPercent / 100f));

            overlayPaint.setColor(maskColor);
            overlayPaint.setAlpha(Math.round(this.maskAlpha * 255f));
            this.blurredBitmap = blurDp > 0 ? createBlurredBitmap(bitmap, blurDp) : bitmap;
        }

        @Override
        public void draw(Canvas canvas) {
            Bitmap drawBitmap = blurredBitmap;
            if (drawBitmap == null || drawBitmap.isRecycled()) {
                return;
            }
            Rect bounds = getBounds();
            if (bounds.width() <= 0 || bounds.height() <= 0) {
                return;
            }

            float scale = Math.max(
                    bounds.width() / (float) drawBitmap.getWidth(),
                    bounds.height() / (float) drawBitmap.getHeight());
            float width = drawBitmap.getWidth() * scale;
            float height = drawBitmap.getHeight() * scale;
            float left = bounds.left + (bounds.width() - width) / 2f;
            float top = bounds.top + (bounds.height() - height) / 2f;
            destination.set(left, top, left + width, top + height);

            matrix.reset();
            matrix.setRectToRect(
                    new RectF(0, 0, drawBitmap.getWidth(), drawBitmap.getHeight()),
                    destination, Matrix.ScaleToFit.FILL);

            canvas.drawBitmap(drawBitmap, matrix, bitmapPaint);
            if (maskAlpha > 0f) {
                canvas.drawRect(bounds, overlayPaint);
            }
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter colorFilter) { bitmapPaint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

        /**
         * Creates a small, CPU-friendly blurred copy. The source is first reduced
         * to at most 512px on its long edge, then a separable box blur is applied.
         * This avoids RenderEffect/API 31 and works on older Android versions too.
         */
        private static Bitmap createBlurredBitmap(Bitmap source, int blurDp) {
            if (source == null || source.isRecycled() || blurDp <= 0) {
                return source;
            }

            final int maxDimension = 512;
            int width = source.getWidth();
            int height = source.getHeight();
            float scale = Math.min(1f, maxDimension / (float) Math.max(width, height));
            if (scale < 1f) {
                width = Math.max(1, Math.round(width * scale));
                height = Math.max(1, Math.round(height * scale));
            }

            Bitmap working = (width != source.getWidth() || height != source.getHeight())
                    ? Bitmap.createScaledBitmap(source, width, height, true)
                    : source.copy(Bitmap.Config.ARGB_8888, true);

            int radius = Math.max(1, Math.min(12, Math.round(blurDp * 0.5f)));
            boxBlur(working, radius);
            return working;
        }

        private static void boxBlur(Bitmap bitmap, int radius) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width <= 1 && height <= 1) {
                return;
            }

            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            int[] temp = new int[pixels.length];
            int window = radius * 2 + 1;

            // Horizontal pass.
            for (int y = 0; y < height; y++) {
                int row = y * width;
                int a = 0, r = 0, g = 0, b = 0;
                for (int x = -radius; x <= radius; x++) {
                    int ix = Math.max(0, Math.min(width - 1, x));
                    int c = pixels[row + ix];
                    a += Color.alpha(c);
                    r += Color.red(c);
                    g += Color.green(c);
                    b += Color.blue(c);
                }
                for (int x = 0; x < width; x++) {
                    temp[row + x] = Color.argb(a / window, r / window, g / window, b / window);
                    int removeX = Math.max(0, x - radius);
                    int addX = Math.min(width - 1, x + radius + 1);
                    int remove = pixels[row + removeX];
                    int add = pixels[row + addX];
                    a += Color.alpha(add) - Color.alpha(remove);
                    r += Color.red(add) - Color.red(remove);
                    g += Color.green(add) - Color.green(remove);
                    b += Color.blue(add) - Color.blue(remove);
                }
            }

            // Vertical pass.
            for (int x = 0; x < width; x++) {
                int a = 0, r = 0, g = 0, b = 0;
                for (int y = -radius; y <= radius; y++) {
                    int iy = Math.max(0, Math.min(height - 1, y));
                    int c = temp[iy * width + x];
                    a += Color.alpha(c);
                    r += Color.red(c);
                    g += Color.green(c);
                    b += Color.blue(c);
                }
                for (int y = 0; y < height; y++) {
                    pixels[y * width + x] = Color.argb(a / window, r / window, g / window, b / window);
                    int removeY = Math.max(0, y - radius);
                    int addY = Math.min(height - 1, y + radius + 1);
                    int remove = temp[removeY * width + x];
                    int add = temp[addY * width + x];
                    a += Color.alpha(add) - Color.alpha(remove);
                    r += Color.red(add) - Color.red(remove);
                    g += Color.green(add) - Color.green(remove);
                    b += Color.blue(add) - Color.blue(remove);
                }
            }

            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        }
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
