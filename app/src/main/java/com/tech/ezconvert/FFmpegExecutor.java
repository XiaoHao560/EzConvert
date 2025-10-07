package com.tech.ezconvert;

public class FFmpegExecutor {
    
    public interface ConversionCallback {
        void onProgressUpdate(int progress);
        boolean isCancelled();
        void onConversionComplete(boolean success, String message);
    }
    
    static {
        System.loadLibrary("ezconvert");
    }
    
    private native void nativeInitialize();
    private native void nativeCleanup();
    private native String nativeGetMediaInfo(String filePath);
    private native boolean nativeConvertVideo(String inputPath, String outputPath, 
                                            String outputFormat, Object callback);
    private native boolean nativeConvertAudio(String inputPath, String outputPath,
                                            String outputFormat, Object callback);
    private native boolean nativeExtractAudio(String inputPath, String outputPath,
                                            Object callback);
    private native boolean nativeExtractVideo(String inputPath, String outputPath,
                                            Object callback);
    
    public FFmpegExecutor() {
        nativeInitialize();
    }
    
    public void release() {
        nativeCleanup();
    }
    
    public String getMediaInfo(String filePath) {
        return nativeGetMediaInfo(filePath);
    }
    
    public void convertVideo(String inputPath, String outputPath, 
                           String outputFormat, ConversionCallback callback) {
        new Thread(() -> {
            boolean success = nativeConvertVideo(inputPath, outputPath, outputFormat, callback);
            if (callback != null) {
                callback.onConversionComplete(success, 
                    success ? "转换完成" : "转换失败");
            }
        }).start();
    }
    
    public void convertAudio(String inputPath, String outputPath,
                           String outputFormat, ConversionCallback callback) {
        new Thread(() -> {
            boolean success = nativeConvertAudio(inputPath, outputPath, outputFormat, callback);
            if (callback != null) {
                callback.onConversionComplete(success,
                    success ? "转换完成" : "转换失败");
            }
        }).start();
    }
    
    public void extractAudio(String inputPath, String outputPath, ConversionCallback callback) {
        new Thread(() -> {
            boolean success = nativeExtractAudio(inputPath, outputPath, callback);
            if (callback != null) {
                callback.onConversionComplete(success,
                    success ? "提取完成" : "提取失败");
            }
        }).start();
    }
    
    public void extractVideo(String inputPath, String outputPath, ConversionCallback callback) {
        new Thread(() -> {
            boolean success = nativeExtractVideo(inputPath, outputPath, callback);
            if (callback != null) {
                callback.onConversionComplete(success,
                    success ? "提取完成" : "提取失败");
            }
        }).start();
    }
}