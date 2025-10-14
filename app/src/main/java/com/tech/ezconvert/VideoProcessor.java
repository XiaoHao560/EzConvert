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
                    "-movflags", "+faststart",
                    "-strict", "-2",
                    "-y",
                    outputFile
                };
                break;
                
            case "avi":
                command = new String[]{
                    "-i", inputPath,
                    "-c:v", "mpeg4",
                    "-c:a", "mp3",
                    "-q:v", "5",
                    "-y",
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
                    "-strict", "-2",
                    "-y",
                    outputFile
                };
                break;
                
            case "mkv":
                command = new String[]{
                    "-i", inputPath,
                    "-c:v", "libx264",
                    "-c:a", "aac",
                    "-strict", "-2",
                    "-y",
                    outputFile
                };
                break;
                
            case "flv":
                command = new String[]{
                    "-i", inputPath,
                    "-c:v", "flv",
                    "-c:a", "mp3",
                    "-y",
                    outputFile
                };
                break;
                
            case "webm":
                command = new String[]{
                    "-i", inputPath,
                    "-c:v", "libvpx-vp9",
                    "-c:a", "libopus",
                    "-b:v", "1M",
                    "-y",
                    outputFile
                };
                break;
                
            case "gif":
                command = new String[]{
                    "-i", inputPath,
                    "-vf", "fps=10,scale=480:-1:flags=lanczos",
                    "-c:v", "gif",
                    "-y",
                    outputFile
                };
                break;
                
            default:
                command = new String[]{
                    "-i", inputPath,
                    "-c:v", "libx264",
                    "-c:a", "aac",
                    "-strict", "-2",
                    "-y",
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
            "-strict", "-2",
            "-y",
            outputFile
        };
        
        Log.d("VideoProcessor", "压缩命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 视频裁剪
    public static void cutVideo(String inputPath, String outputPath,
                               String startTime, String duration,
                               FFmpegUtil.FFmpegCallback callback) {
        String[] command = {
            "-i", inputPath,
            "-ss", startTime,
            "-t", duration,
            "-c:v", "libx264",
            "-c:a", "aac",
            "-avoid_negative_ts", "make_zero",
            "-strict", "-2",
            "-y",
            outputPath
        };
        
        Log.d("VideoProcessor", "裁剪命令: " + String.join(" ", command));
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
            case "center":
                overlay = "(main_w-overlay_w)/2:(main_h-overlay_h)/2";
                break;
            default:
                overlay = "10:10";
        }
        
        String[] command = {
            "-i", inputPath,
            "-i", watermarkPath,
            "-filter_complex", "[1]format=rgba,colorchannelmixer=aa=0.7[wm];[0][wm]overlay=" + overlay,
            "-codec:a", "aac",
            "-strict", "-2",
            "-y",
            outputPath
        };
        
        Log.d("VideoProcessor", "水印命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 调整视频分辨率
    public static void resizeVideo(String inputPath, String outputPath,
                                  int width, int height, FFmpegUtil.FFmpegCallback callback) {
        String scaleFilter = "scale=" + width + ":" + height + ":flags=lanczos";
        
        String[] command = {
            "-i", inputPath,
            "-vf", scaleFilter,
            "-c:a", "aac",
            "-strict", "-2",
            "-y",
            outputPath
        };
        
        Log.d("VideoProcessor", "调整分辨率命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 提取视频帧（截图）
    public static void extractFrame(String inputPath, String outputPath,
                                   String timestamp, FFmpegUtil.FFmpegCallback callback) {
        String[] command = {
            "-i", inputPath,
            "-ss", timestamp,
            "-vframes", "1",
            "-q:v", "2",
            "-y",
            outputPath
        };
        
        Log.d("VideoProcessor", "截图命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 合并视频和音频
    public static void mergeVideoAudio(String videoPath, String audioPath,
                                      String outputPath, FFmpegUtil.FFmpegCallback callback) {
        String[] command = {
            "-i", videoPath,
            "-i", audioPath,
            "-c:v", "copy",
            "-c:a", "aac",
            "-map", "0:v:0",
            "-map", "1:a:0",
            "-shortest",
            "-strict", "-2",
            "-y",
            outputPath
        };
        
        Log.d("VideoProcessor", "合并音视频命令: " + String.join(" ", command));
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
            case "gif": return "gif";
            case "mp3": return "mp3";
            case "wav": return "wav";
            case "aac": return "aac";
            case "flac": return "flac";
            default: return "mp4";
        }
    }
}