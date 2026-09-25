package com.tech.ezconvert.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** 可拖动/缩放的背景编辑区域。作为独立 View 放置在 XML 中，避免嵌套类 XML inflate 在部分 Android 构建/加载环境下无法找到类。 */
public class BackgroundCropImageView extends View {
    private Bitmap bitmap;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF mappedRect = new RectF();
    private final RectF cropRect = new RectF();
    private final float[] matrixValues = new float[9];
    private ScaleGestureDetector scaleDetector;
    private float minScale = 1f;
    private float maxScale = 4f;
    private float lastX;
    private float lastY;
    private boolean dragging;
    private boolean initialized;
    private float cropAspectRatio = 1f;

    public BackgroundCropImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        overlayPaint.setColor(Color.argb(130, 0, 0, 0));
        guidePaint.setColor(Color.WHITE);
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(dp(context, 1));
        setBackgroundColor(Color.BLACK);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (bitmap == null) return false;
                float factor = detector.getScaleFactor();
                float current = getCurrentScale();
                float next = Math.max(minScale, Math.min(maxScale, current * factor));
                if (current > 0f) {
                    float actual = next / current;
                    matrix.postScale(actual, actual, detector.getFocusX(), detector.getFocusY());
                    clampTranslation();
                    invalidate();
                }
                return true;
            }
        });
    }

    public void setCropAspectRatio(float ratio) {
        if (ratio > 0f && Float.isFinite(ratio)) {
            cropAspectRatio = ratio;
            initialized = false;
            requestLayout();
            invalidate();
        }
    }

    public void setBitmap(Bitmap value) {
        bitmap = value;
        initialized = false;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0 && bitmap != null && !initialized) {
            float cropWidth = w;
            float cropHeight = cropWidth / Math.max(0.01f, cropAspectRatio);
            if (cropHeight > h) {
                cropHeight = h;
                cropWidth = cropHeight * Math.max(0.01f, cropAspectRatio);
            }
            float cropLeft = (w - cropWidth) / 2f;
            float cropTop = (h - cropHeight) / 2f;
            cropRect.set(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight);

            minScale = Math.max(cropWidth / (float) bitmap.getWidth(), cropHeight / (float) bitmap.getHeight());
            maxScale = Math.max(minScale * 4f, minScale + 1f);
            matrix.reset();
            float scale = minScale;
            float width = bitmap.getWidth() * scale;
            float height = bitmap.getHeight() * scale;
            float tx = cropRect.centerX() - width / 2f;
            float ty = cropRect.centerY() - height / 2f;
            matrix.setScale(scale, scale);
            matrix.postTranslate(tx, ty);
            clampTranslation();
            initialized = true;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) return;
        canvas.drawBitmap(bitmap, matrix, bitmapPaint);

        // 裁剪框之外变暗，中间区域就是最终应用背景的范围。
        overlayPaint.setAlpha(150);
        canvas.drawRect(0, 0, getWidth(), cropRect.top, overlayPaint);
        canvas.drawRect(0, cropRect.bottom, getWidth(), getHeight(), overlayPaint);
        canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint);
        canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, overlayPaint);

        guidePaint.setAlpha(90);
        float thirdW = cropRect.width() / 3f;
        float thirdH = cropRect.height() / 3f;
        canvas.drawLine(cropRect.left + thirdW, cropRect.top, cropRect.left + thirdW, cropRect.bottom, guidePaint);
        canvas.drawLine(cropRect.left + thirdW * 2f, cropRect.top, cropRect.left + thirdW * 2f, cropRect.bottom, guidePaint);
        canvas.drawLine(cropRect.left, cropRect.top + thirdH, cropRect.right, cropRect.top + thirdH, guidePaint);
        canvas.drawLine(cropRect.left, cropRect.top + thirdH * 2f, cropRect.right, cropRect.top + thirdH * 2f, guidePaint);
        guidePaint.setAlpha(190);
        canvas.drawRect(cropRect, guidePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                dragging = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1 && dragging && bitmap != null) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    matrix.postTranslate(dx, dy);
                    clampTranslation();
                    lastX = event.getX();
                    lastY = event.getY();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
            default:
                return true;
        }
    }

    private float getCurrentScale() {
        matrix.getValues(matrixValues);
        return Math.abs(matrixValues[Matrix.MSCALE_X]);
    }

    private void clampTranslation() {
        if (bitmap == null || getWidth() <= 0 || getHeight() <= 0) return;
        matrix.mapRect(mappedRect, new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()));
        float dx = 0f;
        float dy = 0f;
        if (mappedRect.width() < cropRect.width()) {
            dx = cropRect.centerX() - mappedRect.centerX();
        } else {
            if (mappedRect.left > cropRect.left) dx = cropRect.left - mappedRect.left;
            if (mappedRect.right < cropRect.right) dx = cropRect.right - mappedRect.right;
        }
        if (mappedRect.height() < cropRect.height()) {
            dy = cropRect.centerY() - mappedRect.centerY();
        } else {
            if (mappedRect.top > cropRect.top) dy = cropRect.top - mappedRect.top;
            if (mappedRect.bottom < cropRect.bottom) dy = cropRect.bottom - mappedRect.bottom;
        }
        matrix.postTranslate(dx, dy);
    }

    public Bitmap renderToBitmap(int maxDimension) {
        if (bitmap == null || getWidth() <= 0 || getHeight() <= 0) return null;
        float scale = Math.min(1f, maxDimension / (float) Math.max(cropRect.width(), cropRect.height()));
        int outW = Math.max(1, Math.round(cropRect.width() * scale));
        int outH = Math.max(1, Math.round(cropRect.height() * scale));
        Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.BLACK);
        canvas.save();
        canvas.scale(scale, scale);
        canvas.translate(-cropRect.left, -cropRect.top);
        canvas.drawBitmap(bitmap, matrix, bitmapPaint);
        canvas.restore();
        return out;
    }

    public float getZoomFactor() {
        return Math.max(1f, getCurrentScale() / Math.max(0.0001f, minScale));
    }

    public float getOffsetXRatio() {
        if (bitmap == null || getWidth() == 0) return 0f;
        matrix.mapRect(mappedRect, new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()));
        return (mappedRect.centerX() - cropRect.centerX()) / Math.max(1f, cropRect.width());
    }

    public float getOffsetYRatio() {
        if (bitmap == null || getHeight() == 0) return 0f;
        matrix.mapRect(mappedRect, new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()));
        return (mappedRect.centerY() - cropRect.centerY()) / Math.max(1f, cropRect.height());
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
