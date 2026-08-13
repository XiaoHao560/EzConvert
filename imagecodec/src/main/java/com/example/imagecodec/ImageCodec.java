package com.example.imagecodec;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class ImageCodec {

    public static final int FORMAT_JPEG = 0;
    public static final int FORMAT_PNG = 1;
    public static final int FORMAT_WEBP = 2;

    static {
        System.loadLibrary("imagecodec");
    }

    /**
     * 解码任意支持的格式为 Bitmap
     */
    public static Bitmap decode(byte[] data) {
        int[] sizeArray = new int[2];
        byte[] rgba = nativeDecode(data, sizeArray);
        if (rgba == null) return null;

        int w = sizeArray[0];
        int h = sizeArray[1];

        int[] pixels = new int[w * h];
        for (int i = 0; i < w * h; i++) {
            int r = rgba[i * 4 + 0] & 0xFF;
            int g = rgba[i * 4 + 1] & 0xFF;
            int b = rgba[i * 4 + 2] & 0xFF;
            int a = rgba[i * 4 + 3] & 0xFF;
            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        return bitmap;
    }

    /**
     * 将 Bitmap 编码为目标格式
     * @param quality JPEG/WebP 质量 (0-100)，PNG 忽略此参数
     */
    public static byte[] encode(Bitmap bitmap, int format, int quality) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);

        // ARGB int[] → RGBA byte[]
        byte[] rgba = new byte[w * h * 4];
        for (int i = 0; i < w * h; i++) {
            int pixel = pixels[i];
            rgba[i * 4 + 0] = (byte) ((pixel >> 16) & 0xFF); // R
            rgba[i * 4 + 1] = (byte) ((pixel >> 8) & 0xFF);  // G
            rgba[i * 4 + 2] = (byte) (pixel & 0xFF);          // B
            rgba[i * 4 + 3] = (byte) ((pixel >> 24) & 0xFF); // A
        }

        return nativeEncode(rgba, w, h, format, quality);
    }

    /**
     * 格式转换：byte[] → byte[]
     */
    public static byte[] convert(byte[] input, int outputFormat, int quality) {
        Bitmap bmp = decode(input);
        if (bmp == null) return null;
        byte[] result = encode(bmp, outputFormat, quality);
        bmp.recycle();
        return result;
    }

    private static native byte[] nativeDecode(byte[] input, int[] outSize);
    private static native byte[] nativeEncode(byte[] rgba, int width, int height, int format, int quality);
}
