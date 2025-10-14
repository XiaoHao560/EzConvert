package com.tech.ezconvert;

import android.util.Log;
import android.content.Context;
import java.util.ArrayList;

public class VideoProcessor {
    
    // 视频转换
    public static void convertVideo(String inputPath, String outputPath, 
                                   String format, FFmpegUtil.FFmpegCallback callback, Context context) {
        String[] command;
        String outputFile = outputPath + "." + getFileExtension(format);
        
        boolean hardwareAcceleration = SettingsActivity.isHardwareAccelerationEnabled(context);
        boolean multithreading = SettingsActivity.isMultithreadingEnabled(context);
        
        Log.d("VideoProcessor", "硬件加速: " + hardwareAcceleration + ", 多线程: " + multithreading);
        
        // 基础命令
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(inputPath);
        
        if (multithreading) {
            commandList.add("-threads");
            commandList.add("0"); // 0则自动选择线程数
        }
        
        switch (format.toLowerCase()) {
            case "mp4":
                if (hardwareAcceleration) {
                    // 硬件加速版本
                    commandList.add("-c:v");
                    commandList.add("h264_mediacodec");
                    commandList.add("-c:a");
                    commandList.add("aac");
                } else {
                    // 软件编码版本
                    commandList.add("-c:v");
                    commandList.add("libx264");
                    commandList.add("-preset");
                    commandList.add("medium");
                    commandList.add("-crf");
                    commandList.add("23");
                    commandList.add("-c:a");
                    commandList.add("aac");
                }
                commandList.add("-movflags");
                commandList.add("+faststart");
                break;
                
            case "avi":
                commandList.add("-c:v");
                commandList.add("mpeg4");
                commandList.add("-c:a");
                commandList.add("mp3");
                commandList.add("-q:v");
                commandList.add("5");
                break;
                
            case "mov":
                if (hardwareAcceleration) {
                    commandList.add("-c:v");
                    commandList.add("h264_mediacodec");
                    commandList.add("-c:a");
                    commandList.add("aac");
                } else {
                    commandList.add("-c:v");
                    commandList.add("libx264");
                    commandList.add("-preset");
                    commandList.add("medium");
                    commandList.add("-crf");
                    commandList.add("23");
                    commandList.add("-c:a");
                    commandList.add("aac");
                }
                break;
                
            case "mkv":
                commandList.add("-c:v");
                commandList.add("libx264");
                commandList.add("-c:a");
                commandList.add("aac");
                break;
                
            case "flv":
                commandList.add("-c:v");
                commandList.add("flv");
                commandList.add("-c:a");
                commandList.add("mp3");
                break;
                
            case "webm":
                commandList.add("-c:v");
                commandList.add("libvpx-vp9");
                commandList.add("-c:a");
                commandList.add("libopus");
                commandList.add("-b:v");
                commandList.add("1M");
                break;
                
            case "gif":
                commandList.add("-vf");
                commandList.add("fps=10,scale=480:-1:flags=lanczos");
                commandList.add("-c:v");
                commandList.add("gif");
                break;
                
            default:
                commandList.add("-c:v");
                commandList.add("libx264");
                commandList.add("-c:a");
                commandList.add("aac");
        }
        
        // 通用参数
        commandList.add("-strict");
        commandList.add("-2");
        commandList.add("-y");
        commandList.add(outputFile);
        
        command = commandList.toArray(new String[0]);
        
        Log.d("VideoProcessor", "转换命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 视频压缩
    public static void compressVideo(String inputPath, String outputPath, 
                                    int quality, FFmpegUtil.FFmpegCallback callback, Context context) {
        // quality: 0-100, 0最高质量
        int crf = 51 - (quality * 51 / 100);
        if (crf < 18) crf = 18;
        if (crf > 51) crf = 51;
        
        String outputFile = outputPath + "_compressed.mp4";
        
        boolean hardwareAcceleration = SettingsActivity.isHardwareAccelerationEnabled(context);
        boolean multithreading = SettingsActivity.isMultithreadingEnabled(context);
        
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(inputPath);
        
        if (multithreading) {
            commandList.add("-threads");
            commandList.add("0");
        }
        
        if (hardwareAcceleration) {
            commandList.add("-c:v");
            commandList.add("h264_mediacodec");
        } else {
            commandList.add("-c:v");
            commandList.add("libx264");
            commandList.add("-crf");
            commandList.add(String.valueOf(crf));
            commandList.add("-preset");
            commandList.add("medium");
        }
        
        commandList.add("-c:a");
        commandList.add("aac");
        commandList.add("-movflags");
        commandList.add("+faststart");
        commandList.add("-strict");
        commandList.add("-2");
        commandList.add("-y");
        commandList.add(outputFile);
        
        String[] command = commandList.toArray(new String[0]);
        
        Log.d("VideoProcessor", "压缩命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 视频裁剪
    public static void cutVideo(String inputPath, String outputPath,
                               String startTime, String duration,
                               FFmpegUtil.FFmpegCallback callback, Context context) {
        boolean hardwareAcceleration = SettingsActivity.isHardwareAccelerationEnabled(context);
        boolean multithreading = SettingsActivity.isMultithreadingEnabled(context);
        
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(inputPath);
        
        if (multithreading) {
            commandList.add("-threads");
            commandList.add("0");
        }
        
        commandList.add("-ss");
        commandList.add(startTime);
        commandList.add("-t");
        commandList.add(duration);
        
        if (hardwareAcceleration) {
            commandList.add("-c:v");
            commandList.add("h264_mediacodec");
        } else {
            commandList.add("-c:v");
            commandList.add("libx264");
        }
        
        commandList.add("-c:a");
        commandList.add("aac");
        commandList.add("-avoid_negative_ts");
        commandList.add("make_zero");
        commandList.add("-strict");
        commandList.add("-2");
        commandList.add("-y");
        commandList.add(outputPath);
        
        String[] command = commandList.toArray(new String[0]);
        
        Log.d("VideoProcessor", "裁剪命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 添加水印
    public static void addWatermark(String inputPath, String outputPath,
                                   String watermarkPath, String position,
                                   FFmpegUtil.FFmpegCallback callback, Context context) {
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
        
        boolean hardwareAcceleration = SettingsActivity.isHardwareAccelerationEnabled(context);
        boolean multithreading = SettingsActivity.isMultithreadingEnabled(context);
        
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(inputPath);
        commandList.add("-i");
        commandList.add(watermarkPath);
        
        if (multithreading) {
            commandList.add("-threads");
            commandList.add("0");
        }
        
        commandList.add("-filter_complex");
        commandList.add("[1]format=rgba,colorchannelmixer=aa=0.7[wm];[0][wm]overlay=" + overlay);
        
        if (hardwareAcceleration) {
            commandList.add("-c:v");
            commandList.add("h264_mediacodec");
        } else {
            commandList.add("-c:v");
            commandList.add("libx264");
        }
        
        commandList.add("-c:a");
        commandList.add("aac");
        commandList.add("-strict");
        commandList.add("-2");
        commandList.add("-y");
        commandList.add(outputPath);
        
        String[] command = commandList.toArray(new String[0]);
        
        Log.d("VideoProcessor", "水印命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 调整视频分辨率
    public static void resizeVideo(String inputPath, String outputPath,
                                  int width, int height, FFmpegUtil.FFmpegCallback callback, Context context) {
        String scaleFilter = "scale=" + width + ":" + height + ":flags=lanczos";
        
        boolean hardwareAcceleration = SettingsActivity.isHardwareAccelerationEnabled(context);
        boolean multithreading = SettingsActivity.isMultithreadingEnabled(context);
        
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(inputPath);
        
        if (multithreading) {
            commandList.add("-threads");
            commandList.add("0");
        }
        
        commandList.add("-vf");
        commandList.add(scaleFilter);
        
        if (hardwareAcceleration) {
            commandList.add("-c:v");
            commandList.add("h264_mediacodec");
        } else {
            commandList.add("-c:v");
            commandList.add("libx264");
        }
        
        commandList.add("-c:a");
        commandList.add("aac");
        commandList.add("-strict");
        commandList.add("-2");
        commandList.add("-y");
        commandList.add(outputPath);
        
        String[] command = commandList.toArray(new String[0]);
        
        Log.d("VideoProcessor", "调整分辨率命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 提取视频帧（截图）
    public static void extractFrame(String inputPath, String outputPath,
                                   String timestamp, FFmpegUtil.FFmpegCallback callback, Context context) {
        boolean multithreading = SettingsActivity.isMultithreadingEnabled(context);
        
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(inputPath);
        
        if (multithreading) {
            commandList.add("-threads");
            commandList.add("0");
        }
        
        commandList.add("-ss");
        commandList.add(timestamp);
        commandList.add("-vframes");
        commandList.add("1");
        commandList.add("-q:v");
        commandList.add("2");
        commandList.add("-y");
        commandList.add(outputPath);
        
        String[] command = commandList.toArray(new String[0]);
        
        Log.d("VideoProcessor", "截图命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 合并视频和音频
    public static void mergeVideoAudio(String videoPath, String audioPath,
                                      String outputPath, FFmpegUtil.FFmpegCallback callback, Context context) {
        boolean hardwareAcceleration = SettingsActivity.isHardwareAccelerationEnabled(context);
        boolean multithreading = SettingsActivity.isMultithreadingEnabled(context);
        
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(videoPath);
        commandList.add("-i");
        commandList.add(audioPath);
        
        if (multithreading) {
            commandList.add("-threads");
            commandList.add("0");
        }
        
        if (hardwareAcceleration) {
            commandList.add("-c:v");
            commandList.add("h264_mediacodec");
        } else {
            commandList.add("-c:v");
            commandList.add("copy");
        }
        
        commandList.add("-c:a");
        commandList.add("aac");
        commandList.add("-map");
        commandList.add("0:v:0");
        commandList.add("-map");
        commandList.add("1:a:0");
        commandList.add("-shortest");
        commandList.add("-strict");
        commandList.add("-2");
        commandList.add("-y");
        commandList.add(outputPath);
        
        String[] command = commandList.toArray(new String[0]);
        
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