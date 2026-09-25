package com.tech.ezconvert.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.ConfigManager;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.ThemeManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 自定义背景图片编辑器：选择图片后立即进入，并允许之后从设置页反复重新调整。
 * 支持拖动、双指缩放；编辑区域比例跟随当前设备屏幕内容比例。
 */
public class BackgroundCropActivity extends AppCompatActivity {
    private static final String TAG = "BackgroundCropActivity";
    private static final String EXTRA_SOURCE_URI = "source_uri";
    private static final String EXTRA_COMMIT_SOURCE = "commit_source";

    private BackgroundCropImageView cropImageView;
    private Bitmap sourceBitmap;
    private String sourceUriString;
    private boolean commitSource;
    private boolean saving;

    public static void open(AppCompatActivity activity, Uri sourceUri, boolean commitSource) {
        Intent intent = new Intent(activity, BackgroundCropActivity.class);
        intent.putExtra(EXTRA_SOURCE_URI, sourceUri.toString());
        intent.putExtra(EXTRA_COMMIT_SOURCE, commitSource);
        activity.startActivityForResult(intent, MoreSettingsActivity.REQUEST_BACKGROUND_EDITOR);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_background_crop);

        sourceUriString = getIntent().getStringExtra(EXTRA_SOURCE_URI);
        commitSource = getIntent().getBooleanExtra(EXTRA_COMMIT_SOURCE, false);
        if (sourceUriString == null || sourceUriString.isEmpty()) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.background_crop_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        cropImageView = findViewById(R.id.background_crop_view);
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        cropImageView.setCropAspectRatio(metrics.widthPixels / (float) Math.max(1, metrics.heightPixels));
        MaterialButton cancelButton = findViewById(R.id.background_crop_cancel);
        MaterialButton confirmButton = findViewById(R.id.background_crop_confirm);
        cancelButton.setOnClickListener(v -> finish());
        confirmButton.setOnClickListener(v -> saveBackground());

        loadSourceBitmap();
        applyInsets();
    }

    private void loadSourceBitmap() {
        try {
            sourceBitmap = decodeBitmap(Uri.parse(sourceUriString), 4096);
            if (sourceBitmap == null) {
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
            // 每次进入编辑器都从保存的完整原图重新开始。
            // 不恢复上一次裁剪的缩放/偏移，这样用户可以重新调整整张原图。
            cropImageView.setBitmap(sourceBitmap);
        } catch (Throwable e) {
            Log.e(TAG, "加载背景图片失败", e);
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private void applyInsets() {
        View root = findViewById(R.id.background_crop_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            MaterialToolbar toolbar = findViewById(R.id.background_crop_toolbar);
            toolbar.setPadding(toolbar.getPaddingLeft(), bars.top, toolbar.getPaddingRight(), toolbar.getPaddingBottom());

            View bottom = findViewById(R.id.background_crop_bottom_bar);
            bottom.setPadding(bottom.getPaddingLeft(), bottom.getPaddingTop(), bottom.getPaddingRight(), bars.bottom + dp(12));
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void saveBackground() {
        if (saving || sourceBitmap == null || cropImageView.getWidth() <= 0 || cropImageView.getHeight() <= 0) {
            return;
        }
        saving = true;

        try {
            ConfigManager config = ConfigManager.getInstance(this);
            File directory = new File(getFilesDir(), "custom_background");
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IllegalStateException("无法创建背景目录");
            }

            File target = new File(directory, "background_image");
            File temp = new File(directory, "background_image.tmp");
            Bitmap result = cropImageView.renderToBitmap(2048);
            if (result == null) {
                throw new IllegalStateException("无法生成裁剪结果");
            }

            try (OutputStream output = new FileOutputStream(temp, false)) {
                if (!result.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw new IllegalStateException("无法保存背景图片");
                }
                output.flush();
            } finally {
                result.recycle();
            }

            if (target.exists() && !target.delete()) {
                throw new IllegalStateException("无法替换旧背景图片");
            }
            if (!temp.renameTo(target)) {
                throw new IllegalStateException("无法提交新的背景图片");
            }

            if (commitSource) {
                File pendingSource = new File(directory, "background_source_pending");
                File sourceFile = new File(directory, "background_source");
                if (pendingSource.exists()) {
                    if (sourceFile.exists() && !sourceFile.delete()) {
                        throw new IllegalStateException("无法更新背景原图");
                    }
                    if (!pendingSource.renameTo(sourceFile)) {
                        throw new IllegalStateException("无法提交背景原图");
                    }
                }
            }

            config.setCustomBackgroundUri(Uri.fromFile(target).toString());
            config.setCustomBackgroundEnabled(true);
            config.setDynamicColorSource(ConfigManager.DYNAMIC_COLOR_SOURCE_IMAGE);
            // 裁剪结果覆盖同一个 background_image 文件，URI 不变。递增内容版本，
            // 让已经存在的上一级 Activity 在返回时能够识别背景图片确实发生了变化。
            config.markCustomBackgroundUpdated();
            ThemeManager.getInstance(this).invalidateCustomBackgroundCache();
            setResult(RESULT_OK);
            finish();
        } catch (Throwable e) {
            Log.e(TAG, "保存背景图片失败", e);
            saving = false;
            android.widget.Toast.makeText(this, getString(R.string.background_crop_save_failed), android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (!saving && commitSource) {
            try {
                new File(getFilesDir(), "custom_background/background_source_pending").delete();
            } catch (Exception ignored) {
            }
        }
        if (isFinishing() && sourceBitmap != null && !sourceBitmap.isRecycled()) {
            sourceBitmap.recycle();
            sourceBitmap = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private Bitmap decodeBitmap(Uri uri, int maxDimension) throws Exception {
        try (InputStream boundsStream = openInputStream(uri)) {
            if (boundsStream == null) return null;
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(boundsStream, null, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            int largest = Math.max(bounds.outWidth, bounds.outHeight);
            while (largest / sample > maxDimension) sample *= 2;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream imageStream = openInputStream(uri)) {
                return BitmapFactory.decodeStream(imageStream, null, options);
            }
        }
    }

    private InputStream openInputStream(Uri uri) throws Exception {
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return new FileInputStream(new File(uri.getPath()));
        }
        return getContentResolver().openInputStream(uri);
    }

}
