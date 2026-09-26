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

import com.tech.ezconvert.utils.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 主题管理器
 * 负责应用主题模式、动态取色以及自定义背景的持久化与实时应用。
 */
public class ThemeManager {
    private static final String TAG = "ThemeManager";
    // 动态取色只需要很小的采样图；避免 Activity 启动时读取/缩放大图
    private static final int MAX_DYNAMIC_COLOR_BITMAP_SIZE = 128;
    // 实际显示背景无需使用裁剪结果的完整 2048px，降低首帧 GPU 上传和内存压力
    private static final int MAX_BACKGROUND_BITMAP_SIZE = 1280;
    private static final String DISPLAY_BACKGROUND_FILE = "background_display";
    private static final String DYNAMIC_COLOR_SOURCE_FILE = "background_dynamic_source";

    private static ThemeManager instance;
    private final ConfigManager configManager;

    // 自定义背景缓存。Bitmap 体积较大，所有耗时的解码/模糊操作都尽量放到后台线程。
    private volatile String cachedBackgroundKey;
    private volatile Bitmap cachedBackgroundBitmap;

    // 动态取色使用较小的 Bitmap，单独缓存，避免每次 Activity 重建都重新解码。
    private volatile String cachedDynamicColorKey;
    private volatile Bitmap cachedDynamicColorBitmap;

    // 模糊后的 Bitmap 单独缓存；这样切换 Activity 或 recreate 时无需重复 CPU 模糊。
    private volatile String cachedBlurKey;
    private volatile Bitmap cachedBlurredBitmap;

    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final AtomicInteger cacheGeneration = new AtomicInteger();

    // 防止多个 Activity 在上一张背景尚未准备完成时重复排队同一份解码/模糊任务。
    private final Object backgroundLoadLock = new Object();
    private final Set<String> pendingBackgroundWorkKeys = new HashSet<>();
    private final Map<String, List<PendingBackgroundTarget>> pendingBackgroundTargets = new HashMap<>();

    private static final class PendingBackgroundTarget {
        final WeakReference<Activity> activityRef;
        final int maskAlphaSetting;
        final boolean autoEffect;
        final boolean lightTheme;

        PendingBackgroundTarget(
                WeakReference<Activity> activityRef,
                int maskAlphaSetting,
                boolean autoEffect,
                boolean lightTheme) {
            this.activityRef = activityRef;
            this.maskAlphaSetting = maskAlphaSetting;
            this.autoEffect = autoEffect;
            this.lightTheme = lightTheme;
        }
    }

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
     * 使当前自定义背景 Bitmap 缓存失效。
     *
     * 背景图片使用应用私有文件保存，而该文件会在用户重新选择图片时
     * 被原地覆盖，因此 URI 本身可能保持不变。此时仅依赖 URI 判断缓存
     * 是否有效会错误复用旧 Bitmap。
     *
     * 不主动 recycle 旧 Bitmap，因为旧 Activity / Drawable 在 recreate
     * 完成前可能仍然持有它。由 GC 在无引用后回收。
     */
    public void invalidateCustomBackgroundCache() {
        cacheGeneration.incrementAndGet();
        cachedBackgroundKey = null;
        cachedBackgroundBitmap = null;
        cachedDynamicColorKey = null;
        cachedDynamicColorBitmap = null;
        cachedBlurKey = null;
        cachedBlurredBitmap = null;
        synchronized (backgroundLoadLock) {
            pendingBackgroundWorkKeys.clear();
            pendingBackgroundTargets.clear();
        }
    }

    /**
     * 在用户刚完成裁剪/替换背景后预热缓存。
     *
     * 预热在后台线程执行，下一次 Activity 创建时可以直接复用 Bitmap，
     * 避免把大图解码和模糊处理压到 UI 线程。
     */
    public void preloadCustomBackground(Context context) {
        if (!configManager.isCustomBackgroundEnabled()) {
            return;
        }

        final String uriString = configManager.getCustomBackgroundUri();
        if (uriString == null || uriString.isEmpty()) {
            return;
        }

        final String cacheKey = buildBackgroundCacheKey(uriString);
        final int generation = cacheGeneration.get();

        // 应用启动阶段只预热小型显示缓存，不抢占首屏 CPU 去计算模糊。
        scheduleBackgroundPreparation(
                context.getApplicationContext(),
                uriString,
                cacheKey,
                generation,
                0,
                null,
                0,
                false,
                false);
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
                + configManager.getCustomBackgroundVersion() + "|"
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

        final String cacheKey = buildBackgroundCacheKey(uriString);
        final int blurDpSetting = configManager.getBackgroundBlurDp();
        final int maskAlphaSetting = configManager.getBackgroundMaskAlpha();
        final boolean autoEffect = ConfigManager.BACKGROUND_EFFECT_MODE_AUTO.equals(
                configManager.getBackgroundEffectMode());
        final int initialBlurDp = autoEffect ? 8 : blurDpSetting;
        final boolean lightTheme = isLightTheme(activity);

        Bitmap cachedBitmap = cachedBackgroundBitmap;
        boolean cachedSourceReady = cacheKey.equals(cachedBackgroundKey)
                && cachedBitmap != null && !cachedBitmap.isRecycled();
        boolean cachedBlurReady = initialBlurDp <= 0
                || (cacheKey + "|" + initialBlurDp).equals(cachedBlurKey)
                && cachedBlurredBitmap != null
                && !cachedBlurredBitmap.isRecycled();

        if (cachedSourceReady) {
            int maskAlphaPercent = autoEffect
                    ? calculateAutoMaskPercent(cachedBitmap, lightTheme)
                    : maskAlphaSetting;

            // 首帧优先使用已经解码好的小型显示图；模糊版本随后在后台补上。
            if (cachedBlurReady || initialBlurDp <= 0) {
                Bitmap displayBitmap = initialBlurDp > 0 ? cachedBlurredBitmap : cachedBitmap;
                applyPreparedCustomBackground(
                        activity, cacheKey, displayBitmap, maskAlphaPercent, initialBlurDp);
                return;
            }

            applyPreparedCustomBackground(
                    activity, cacheKey, cachedBitmap, maskAlphaPercent, 0);

            // 不阻塞首帧，异步生成真正的模糊版本并替换当前背景。
            scheduleBackgroundPreparation(
                    activity.getApplicationContext(),
                    uriString,
                    cacheKey,
                    cacheGeneration.get(),
                    initialBlurDp,
                    new WeakReference<>(activity),
                    maskAlphaSetting,
                    autoEffect,
                    lightTheme);
            return;
        }

        scheduleBackgroundPreparation(
                activity.getApplicationContext(),
                uriString,
                cacheKey,
                cacheGeneration.get(),
                initialBlurDp,
                new WeakReference<>(activity),
                maskAlphaSetting,
                autoEffect,
                lightTheme);
    }

    /**
     * 后台准备显示背景。以 cacheKey + effect 作为 single-flight key，
     * 避免多个 Activity 同时进入时重复解码和模糊同一张图片。
     */
    private void scheduleBackgroundPreparation(
            Context context,
            String uriString,
            String cacheKey,
            int generation,
            int blurDp,
            WeakReference<Activity> activityRef,
            int maskAlphaSetting,
            boolean autoEffect,
            boolean lightTheme) {

        final String workKey = cacheKey + "|" + generation + "|" + blurDp + "|" + autoEffect + "|" + lightTheme;
        final PendingBackgroundTarget targetRequest = activityRef == null
                ? null
                : new PendingBackgroundTarget(
                        activityRef, maskAlphaSetting, autoEffect, lightTheme);

        synchronized (backgroundLoadLock) {
            if (generation != cacheGeneration.get()) {
                return;
            }

            if (targetRequest != null) {
                pendingBackgroundTargets
                        .computeIfAbsent(workKey, key -> new ArrayList<>())
                        .add(targetRequest);
            }

            if (!pendingBackgroundWorkKeys.add(workKey)) {
                // 相同任务已经在执行，当前 Activity 已被登记到回调列表。
                return;
            }
        }

        backgroundExecutor.execute(() -> {
            List<PendingBackgroundTarget> targetsToNotify = null;
            try {
                if (generation != cacheGeneration.get()) {
                    return;
                }

                Bitmap bitmap = getCachedBackgroundBitmap(context, uriString, cacheKey);
                if (bitmap == null || bitmap.isRecycled()
                        || generation != cacheGeneration.get()) {
                    return;
                }

                // 兼容旧版本没有动态取色缩略图的背景：只在后台生成一次。
                ensureDynamicColorSourceFile(context, bitmap);

                int maskAlphaPercent = maskAlphaSetting;
                int actualBlurDp = blurDp;

                if (autoEffect) {
                    maskAlphaPercent = calculateAutoMaskPercent(bitmap, lightTheme);
                }

                Bitmap displayBitmap = bitmap;
                if (actualBlurDp > 0) {
                    displayBitmap = getOrCreateBlurredBitmap(bitmap, cacheKey, actualBlurDp);
                    if (displayBitmap == null || displayBitmap.isRecycled()) {
                        displayBitmap = bitmap;
                    }
                }

                final Bitmap finalBitmap = displayBitmap;
                final int finalBlurDp = actualBlurDp;

                synchronized (backgroundLoadLock) {
                    targetsToNotify = pendingBackgroundTargets.remove(workKey);
                }

                if (targetsToNotify != null) {
                    for (PendingBackgroundTarget pendingTarget : targetsToNotify) {
                        Activity target = pendingTarget.activityRef.get();
                        if (target == null || target.isFinishing() || target.isDestroyed()) {
                            continue;
                        }

                        final int targetMaskAlpha = pendingTarget.autoEffect
                                ? calculateAutoMaskPercent(finalBitmap, pendingTarget.lightTheme)
                                : pendingTarget.maskAlphaSetting;

                        target.runOnUiThread(() -> applyPreparedCustomBackground(
                                target,
                                cacheKey,
                                finalBitmap,
                                targetMaskAlpha,
                                finalBlurDp));
                    }
                }
            } catch (Throwable e) {
                Log.e(TAG, "后台准备自定义背景失败", e);
            } finally {
                synchronized (backgroundLoadLock) {
                    pendingBackgroundTargets.remove(workKey);
                    pendingBackgroundWorkKeys.remove(workKey);
                }
            }
        });

    }


    private void applyPreparedCustomBackground(
            Activity activity,
            String cacheKey,
            Bitmap bitmap,
            int maskAlphaPercent,
            int blurDp) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || bitmap == null || bitmap.isRecycled()) {
            return;
        }

        // Activity 可能已经 recreate；只有状态仍然对应当前缓存时才应用结果。
        if (!cacheKey.equals(buildBackgroundCacheKey(configManager.getCustomBackgroundUri()))
                || !configManager.isCustomBackgroundEnabled()) {
            return;
        }

        try {
            android.view.ViewGroup content = activity.findViewById(android.R.id.content);
            if (content == null || content.getChildCount() == 0) {
                return;
            }

            android.view.View rootView = content.getChildAt(0);
            int maskColor = resolveColor(activity, com.google.android.material.R.attr.colorSurface,
                    activity.getResources().getColor(android.R.color.black));

            CustomBackgroundDrawable drawable = new CustomBackgroundDrawable(
                    bitmap, maskColor, maskAlphaPercent, blurDp);
            rootView.setBackground(drawable);
        } catch (Throwable e) {
            Log.e(TAG, "应用自定义背景失败", e);
        }
    }

    private int calculateAutoMaskPercent(Bitmap bitmap, boolean lightTheme) {
        float brightness = calculateAverageLuminance(bitmap);
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
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float maskAlpha;
        private final Matrix matrix = new Matrix();
        private final RectF destination = new RectF();

        CustomBackgroundDrawable(Bitmap bitmap, int maskColor, int maskAlphaPercent, int blurDp) {
            this.bitmap = bitmap;
            this.maskAlpha = Math.max(0f, Math.min(1f, maskAlphaPercent / 100f));

            overlayPaint.setColor(maskColor);
            overlayPaint.setAlpha(Math.round(this.maskAlpha * 255f));
        }

        @Override
        public void draw(Canvas canvas) {
            if (bitmap == null || bitmap.isRecycled()) {
                return;
            }

            Rect bounds = getBounds();
            if (bounds.width() <= 0 || bounds.height() <= 0) {
                return;
            }

            float scale = Math.max(
                    bounds.width() / (float) bitmap.getWidth(),
                    bounds.height() / (float) bitmap.getHeight());
            float width = bitmap.getWidth() * scale;
            float height = bitmap.getHeight() * scale;
            float left = bounds.left + (bounds.width() - width) / 2f;
            float top = bounds.top + (bounds.height() - height) / 2f;
            destination.set(left, top, left + width, top + height);

            matrix.reset();
            matrix.setRectToRect(
                    new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()),
                    destination, Matrix.ScaleToFit.FILL);

            canvas.drawBitmap(bitmap, matrix, bitmapPaint);
            if (maskAlpha > 0f) {
                canvas.drawRect(bounds, overlayPaint);
            }
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter colorFilter) {
            bitmapPaint.setColorFilter(colorFilter);
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /**
     * 切换动态取色设置后即时刷新当前 Activity。
     */
    public void applyDynamicColorAndRecreate(Activity activity) {
        activity.recreate();
    }

    private String buildBackgroundCacheKey(String uriString) {
        return uriString + "|" + configManager.getCustomBackgroundVersion();
    }

    private Bitmap loadBitmapForDynamicColor(Context context) {
        String uriString = configManager.getCustomBackgroundUri();
        if (uriString == null || uriString.isEmpty()) {
            return null;
        }

        String cacheKey = buildBackgroundCacheKey(uriString);
        Bitmap cached = cachedDynamicColorBitmap;
        if (cacheKey.equals(cachedDynamicColorKey) && cached != null && !cached.isRecycled()) {
            return cached;
        }

        // Activity 创建阶段必须保持轻量：优先读取裁剪时生成的 128px 小图。
        // 绝不在 UI 线程回读 2048px 背景原图。
        File sourceFile = new File(
                context.getFilesDir(), "custom_background/" + DYNAMIC_COLOR_SOURCE_FILE);
        if (sourceFile.exists() && sourceFile.length() > 0) {
            Bitmap sample = decodeFile(sourceFile, MAX_DYNAMIC_COLOR_BITMAP_SIZE);
            if (sample != null) {
                cachedDynamicColorKey = cacheKey;
                cachedDynamicColorBitmap = sample;
                return sample;
            }
        }

        // 当前进程已经预热完成时才复用内存中的背景，否则让调用方回退到系统动态取色。
        Bitmap source = cachedBackgroundBitmap;
        if (cacheKey.equals(cachedBackgroundKey) && source != null && !source.isRecycled()) {
            return getOrCreateDynamicColorBitmap(source, cacheKey);
        }

        return null;
    }


    private Bitmap getOrCreateDynamicColorBitmap(Bitmap source, String cacheKey) {
        Bitmap cached = cachedDynamicColorBitmap;
        if (cacheKey.equals(cachedDynamicColorKey) && cached != null && !cached.isRecycled()) {
            return cached;
        }

        Bitmap scaled = scaleBitmapForMaxDimension(source, MAX_DYNAMIC_COLOR_BITMAP_SIZE);
        if (scaled == null) {
            return null;
        }

        cachedDynamicColorKey = cacheKey;
        cachedDynamicColorBitmap = scaled;
        return scaled;
    }

    private Bitmap getCachedBackgroundBitmap(Context context, String uriString, String cacheKey) {
        Bitmap cached = cachedBackgroundBitmap;
        if (cacheKey.equals(cachedBackgroundKey) && cached != null && !cached.isRecycled()) {
            return cached;
        }

        // 优先使用裁剪时生成的 display cache，避免 Activity 每次从原始 PNG 解码大图。
        File displayFile = new File(
                context.getFilesDir(), "custom_background/" + DISPLAY_BACKGROUND_FILE);
        Bitmap decoded = null;
        if (displayFile.exists() && displayFile.length() > 0) {
            decoded = decodeFile(displayFile, MAX_BACKGROUND_BITMAP_SIZE);
        }

        // 兼容升级前没有 display cache 的旧背景。
        if (decoded == null) {
            decoded = decodeBitmap(context, Uri.parse(uriString), MAX_BACKGROUND_BITMAP_SIZE);
        }

        if (decoded != null) {
            cachedBackgroundKey = cacheKey;
            cachedBackgroundBitmap = decoded;
        }
        return decoded;
    }

    private Bitmap decodeFile(File file, int maxSize) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            int sampleSize = 1;
            int maxDimension = Math.max(bounds.outWidth, bounds.outHeight);
            while (maxDimension / sampleSize > maxSize) {
                sampleSize *= 2;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable e) {
            Log.e(TAG, "Failed to decode cached background file: " + file, e);
            return null;
        }
    }


    private void ensureDynamicColorSourceFile(Context context, Bitmap source) {
        try {
            File directory = new File(context.getFilesDir(), "custom_background");
            if (!directory.exists() && !directory.mkdirs()) {
                return;
            }

            File target = new File(directory, DYNAMIC_COLOR_SOURCE_FILE);
            if (target.exists() && target.length() > 0) {
                return;
            }

            int width = source.getWidth();
            int height = source.getHeight();
            int largest = Math.max(width, height);
            Bitmap sample = source;
            boolean ownsSample = false;

            if (largest > MAX_DYNAMIC_COLOR_BITMAP_SIZE) {
                float scale = MAX_DYNAMIC_COLOR_BITMAP_SIZE / (float) largest;
                int scaledWidth = Math.max(1, Math.round(width * scale));
                int scaledHeight = Math.max(1, Math.round(height * scale));
                sample = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true);
                ownsSample = sample != source;
            }

            File temp = new File(directory, DYNAMIC_COLOR_SOURCE_FILE + ".tmp");
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(temp, false)) {
                sample.compress(Bitmap.CompressFormat.JPEG, 85, output);
                output.flush();
            } finally {
                if (ownsSample && !sample.isRecycled()) {
                    sample.recycle();
                }
            }

            if (target.exists()) {
                target.delete();
            }
            temp.renameTo(target);
        } catch (Throwable e) {
            Log.e(TAG, "生成动态取色缩略图失败", e);
        }
    }

    private Bitmap getOrCreateBlurredBitmap(Bitmap source, String cacheKey, int blurDp) {
        if (blurDp <= 0) {
            return source;
        }

        String blurKey = cacheKey + "|" + blurDp;
        Bitmap cached = cachedBlurredBitmap;
        if (blurKey.equals(cachedBlurKey) && cached != null && !cached.isRecycled()) {
            return cached;
        }

        Bitmap blurred = createBlurredBitmap(source, blurDp);
        if (blurred != null) {
            cachedBlurKey = blurKey;
            cachedBlurredBitmap = blurred;
        }
        return blurred;
    }

    private Bitmap scaleBitmapForMaxDimension(Bitmap source, int maxDimension) {
        if (source == null || source.isRecycled()) {
            return null;
        }
        int width = source.getWidth();
        int height = source.getHeight();
        int largest = Math.max(width, height);
        if (largest <= maxDimension) {
            return source.copy(Bitmap.Config.ARGB_8888, true);
        }

        float scale = maxDimension / (float) largest;
        int scaledWidth = Math.max(1, Math.round(width * scale));
        int scaledHeight = Math.max(1, Math.round(height * scale));
        return Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true);
    }

    /**
     * Creates a small, CPU-friendly blurred copy.
     * The source is first reduced to at most 512px on its long edge.
     */
    private Bitmap createBlurredBitmap(Bitmap source, int blurDp) {
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

    private void boxBlur(Bitmap bitmap, int radius) {
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
