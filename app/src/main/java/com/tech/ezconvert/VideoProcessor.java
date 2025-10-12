package com.tech.ezconvert;

import android.util.Log;

public class VideoProcessor {
    
    // 视频转换
    public static void convertVideo(String inputPath, String outputPath, 
                                   String format, FFmpegUtil.FFmpegCallback callback) {
        String[] command;
        String outputFile = outputPath + "." + getFileExtension(format);
        
        switch (format.toLowerCase()) {
            case "mp4":
                command = new String[]{
                    "-i", inputPath,
                    "-c:v", "libx264",
                    "-c:a", "aac",
                    "-preset", "medium",
                    "-crf", "23",
                    "-movflags", "+faststart", // 优化MP4播放
                    outputFile
                };
                break;
                
            case "avi":
                command = new String[]{
                    "-i", inputPath,
                    "-c:v", "mpeg4",
                    "-c:a", "mp3",
                    "-q:v", "5",
                    outputFile
                };
                break;
                
            case "mov":
                command = new String[]{
                    "-i", inputPath,
                    "-c:v", "libx264",
                    "-c:a", "aac",
                    "-preset", "medium",
                    "-crf", "23",
                    outputFile
                };
                break;
                
            case "mkv":
                command = new String[]{
                    "-i", inputPath,
                    "-c", "copy", // 直接复制流
                    outputFile
                };
                break;
                
            case "flv":
                command = new String[]{
                    "-i", inputPath,
                    "-c:v", "flv",
                    "-c:a", "mp3",
                    outputFile
                };
                break;
                
            default:
                command = new String[]{
                    "-i", inputPath,
                    outputFile
                };
        }
        
        Log.d("VideoProcessor", "转换命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 视频压缩
    public static void compressVideo(String inputPath, String outputPath, 
                                    int quality, FFmpegUtil.FFmpegCallback callback) {
        // quality: 0-100, 0最高质量
        int crf = 51 - (quality * 51 / 100);
        if (crf < 18) crf = 18;
        if (crf > 51) crf = 51;
        
        String outputFile = outputPath + "_compressed.mp4";
        
        String[] command = {
            "-i", inputPath,
            "-c:v", "libx264",
            "-crf", String.valueOf(crf),
            "-c:a", "aac",
            "-preset", "medium",
            "-movflags", "+faststart",
            outputFile
        };
        
        Log.d("VideoProcessor", "压缩命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 提取音频
    public static void extractAudio(String inputPath, String outputPath,
                                   FFmpegUtil.FFmpegCallback callback) {
        String[] command = {
            "-i", inputPath,
            "-vn", // 不处理视频
            "-acodec", "copy", // 直接复制音频流
            outputPath
        };
        
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 视频裁剪
    public static void cutVideo(String inputPath, String outputPath,
                               String startTime, String duration,
                               FFmpegUtil.FFmpegCallback callback) {
        String[] command = {
            "-i", inputPath,
            "-ss", startTime, // 开始时间
            "-t", duration,   // 持续时间
            "-c", "copy",     // 直接复制流
            outputPath
        };
        
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 添加水印
    public static void addWatermark(String inputPath, String outputPath,
                                   String watermarkPath, String position,
                                   FFmpegUtil.FFmpegCallback callback) {
        String overlay;
        switch (position) {
            case "top-left":
                overlay = "10:10";
                break;
            case "top-right":
                overlay = "main_w-overlay_w-10:10";
                break;
            case "bottom-left":
                overlay = "10:main_h-overlay_h-10";
                break;
            case "bottom-right":
                overlay = "main_w-overlay_w-10:main_h-overlay_h-10";
                break;
            default:
                overlay = "10:10";
        }
        
        String[] command = {
            "-i", inputPath,
            "-i", watermarkPath,
            "-filter_complex", "overlay=" + overlay,
            "-codec:a", "copy",
            outputPath
        };
        
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 获取文件扩展名
    private static String getFileExtension(String format) {
        switch (format.toLowerCase()) {
            case "mp4": return "mp4";
            case "avi": return "avi";
            case "mov": return "mov";
            case "mkv": return "mkv";
            case "flv": return "flv";
            case "webm": return "webm";
            default: return "mp4";
        }
    }
}