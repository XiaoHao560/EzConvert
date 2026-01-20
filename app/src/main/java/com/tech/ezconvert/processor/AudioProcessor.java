package com.tech.ezconvert.processor;

import android.content.Context;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.ui.TranscodeSettingsActivity;
import com.tech.ezconvert.utils.FFmpegUtil;
import java.util.ArrayList;

public class AudioProcessor {
    
    // 音频转换
    public static void convertAudio(String inputPath, String outputPath,
                                   String format, int volume, FFmpegUtil.FFmpegCallback callback, Context context) {
        String outputFile = outputPath + "." + getAudioExtension(format);
        
        boolean multithreading = TranscodeSettingsActivity.isMultithreadingEnabled(context);
        
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(inputPath);
        
        if (multithreading) {
            commandList.add("-threads");
            commandList.add("0");
        }
        
        if (volume != 100) {
            double volumeFactor = volume / 100.0;
            commandList.add("-af");
            commandList.add("volume=" + volumeFactor);
        }
        
        switch (format.toLowerCase()) {
            case "mp3":
                commandList.add("-c:a");
                commandList.add("libmp3lame");
                commandList.add("-b:a");
                commandList.add("192k");
                commandList.add("-ac");
                commandList.add("2");
                break;
                
            case "wav":
                commandList.add("-c:a");
                commandList.add("pcm_s16le");
                commandList.add("-ac");
                commandList.add("2");
                break;
                
            case "aac":
                commandList.add("-c:a");
                commandList.add("aac");
                commandList.add("-b:a");
                commandList.add("128k");
                commandList.add("-ac");
                commandList.add("2");
                break;
                
            case "flac":
                commandList.add("-c:a");
                commandList.add("flac");
                commandList.add("-compression_level");
                commandList.add("8");
                break;
                
            case "ogg":
                commandList.add("-c:a");
                commandList.add("libvorbis");
                commandList.add("-q:a");
                commandList.add("4");
                break;
                
            case "m4a":
                commandList.add("-c:a");
                commandList.add("aac");
                commandList.add("-b:a");
                commandList.add("128k");
                commandList.add("-ac");
                commandList.add("2");
                break;
                
            default:
                commandList.add("-c:a");
                commandList.add("libmp3lame");
                commandList.add("-b:a");
                commandList.add("192k");
                commandList.add("-ac");
                commandList.add("2");
        }
        
        commandList.add("-y");
        commandList.add(outputFile);
        
        String[] command = commandList.toArray(new String[0]);
        Log.d("AudioProcessor", "音频转换命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 音频提取（从视频）
    public static void extractAudioFromVideo(String inputPath, String outputPath,
                                           String format, int volume, FFmpegUtil.FFmpegCallback callback, Context context) {
        String outputFile = outputPath + "." + getAudioExtension(format);
        
        boolean multithreading = TranscodeSettingsActivity.isMultithreadingEnabled(context);
        
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(inputPath);
        
        if (multithreading) {
            commandList.add("-threads");
            commandList.add("0");
        }
        
        if (volume != 100) {
            double volumeFactor = volume / 100.0;
            commandList.add("-af");
            commandList.add("volume=" + volumeFactor);
        }
        
        commandList.add("-vn");
        
        switch (format.toLowerCase()) {
            case "mp3":
                commandList.add("-c:a");
                commandList.add("libmp3lame");
                commandList.add("-b:a");
                commandList.add("192k");
                commandList.add("-ac");
                commandList.add("2");
                break;
                
            case "wav":
                commandList.add("-c:a");
                commandList.add("pcm_s16le");
                commandList.add("-ac");
                commandList.add("2");
                break;
                
            case "aac":
                commandList.add("-c:a");
                commandList.add("aac");
                commandList.add("-b:a");
                commandList.add("128k");
                commandList.add("-ac");
                commandList.add("2");
                break;
                
            case "flac":
                commandList.add("-c:a");
                commandList.add("flac");
                commandList.add("-compression_level");
                commandList.add("8");
                break;
                
            case "m4a":
                commandList.add("-c:a");
                commandList.add("aac");
                commandList.add("-b:a");
                commandList.add("128k");
                commandList.add("-ac");
                commandList.add("2");
                break;
                
            default:
                commandList.add("-c:a");
                commandList.add("libmp3lame");
                commandList.add("-b:a");
                commandList.add("192k");
                commandList.add("-ac");
                commandList.add("2");
        }
        
        commandList.add("-y");
        commandList.add(outputFile);
        
        String[] command = commandList.toArray(new String[0]);
        Log.d("AudioProcessor", "音频提取命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 调整音频质量
    public static void adjustAudioQuality(String inputPath, String outputPath,
                                        int bitrate, FFmpegUtil.FFmpegCallback callback, Context context) {
        boolean multithreading = TranscodeSettingsActivity.isMultithreadingEnabled(context);
        
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(inputPath);
        
        if (multithreading) {
            commandList.add("-threads");
            commandList.add("0");
        }
        
        commandList.add("-b:a");
        commandList.add(bitrate + "k");
        commandList.add("-vn");
        commandList.add("-ac");
        commandList.add("2");
        commandList.add("-y");
        commandList.add(outputPath);
        
        String[] command = commandList.toArray(new String[0]);
        Log.d("AudioProcessor", "调整音频质量命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 音频裁剪
    public static void cutAudio(String inputPath, String outputPath,
                               String startTime, String duration, int volume,
                               FFmpegUtil.FFmpegCallback callback, Context context) {
        boolean multithreading = TranscodeSettingsActivity.isMultithreadingEnabled(context);
        
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(inputPath);
        
        if (multithreading) {
            commandList.add("-threads");
            commandList.add("0");
        }
        
        if (volume != 100) {
            double volumeFactor = volume / 100.0;
            commandList.add("-af");
            commandList.add("volume=" + volumeFactor);
        }
        
        commandList.add("-ss");
        commandList.add(startTime);
        commandList.add("-t");
        commandList.add(duration);
        commandList.add("-c:a");
        commandList.add("libmp3lame");
        commandList.add("-b:a");
        commandList.add("192k");
        commandList.add("-y");
        commandList.add(outputPath);
        
        String[] command = commandList.toArray(new String[0]);
        Log.d("AudioProcessor", "音频裁剪命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 音频淡入淡出
    public static void fadeAudio(String inputPath, String outputPath,
                                String fadeIn, String fadeOut,
                                FFmpegUtil.FFmpegCallback callback, Context context) {
        boolean multithreading = TranscodeSettingsActivity.isMultithreadingEnabled(context);
        
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(inputPath);
        
        if (multithreading) {
            commandList.add("-threads");
            commandList.add("0");
        }
        
        commandList.add("-af");
        commandList.add("afade=t=in:st=0:d=" + fadeIn + ",afade=t=out:st=" + fadeOut + ":d=3");
        commandList.add("-c:a");
        commandList.add("libmp3lame");
        commandList.add("-b:a");
        commandList.add("192k");
        commandList.add("-y");
        commandList.add(outputPath);
        
        String[] command = commandList.toArray(new String[0]);
        Log.d("AudioProcessor", "音频淡入淡出命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 获取音频文件扩展名
    private static String getAudioExtension(String format) {
        switch (format.toLowerCase()) {
            case "mp3": return "mp3";
            case "wav": return "wav";
            case "aac": return "aac";
            case "flac": return "flac";
            case "ogg": return "ogg";
            case "m4a": return "m4a";
            default: return "mp3";
        }
    }
}