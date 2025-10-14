package com.tech.ezconvert;

import android.util.Log;

public class AudioProcessor {
    
    // 音频转换
    public static void convertAudio(String inputPath, String outputPath,
                                   String format, FFmpegUtil.FFmpegCallback callback) {
        String[] command;
        String outputFile = outputPath + "." + getAudioExtension(format);
        
        switch (format.toLowerCase()) {
            case "mp3":
                command = new String[]{
                    "-i", inputPath,
                    "-codec:a", "libmp3lame",
                    "-b:a", "192k",
                    "-ac", "2",
                    "-y",
                    outputFile
                };
                break;
                
            case "wav":
                command = new String[]{
                    "-i", inputPath,
                    "-codec:a", "pcm_s16le",
                    "-ac", "2",
                    "-y",
                    outputFile
                };
                break;
                
            case "aac":
                command = new String[]{
                    "-i", inputPath,
                    "-codec:a", "aac",
                    "-b:a", "128k",
                    "-ac", "2",
                    "-strict", "-2",
                    "-y",
                    outputFile
                };
                break;
                
            case "flac":
                command = new String[]{
                    "-i", inputPath,
                    "-codec:a", "flac",
                    "-compression_level", "8",
                    "-y",
                    outputFile
                };
                break;
                
            case "ogg":
                command = new String[]{
                    "-i", inputPath,
                    "-codec:a", "libvorbis",
                    "-q:a", "4",
                    "-y",
                    outputFile
                };
                break;
                
            default:
                command = new String[]{
                    "-i", inputPath,
                    "-codec:a", "libmp3lame",
                    "-b:a", "192k",
                    "-ac", "2",
                    "-y",
                    outputFile
                };
        }
        
        Log.d("AudioProcessor", "音频转换命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 音频提取（从视频）
    public static void extractAudioFromVideo(String inputPath, String outputPath,
                                           String format, FFmpegUtil.FFmpegCallback callback) {
        String[] command;
        String outputFile = outputPath + "." + getAudioExtension(format);
        
        switch (format.toLowerCase()) {
            case "mp3":
                command = new String[]{
                    "-i", inputPath,
                    "-vn",
                    "-acodec", "libmp3lame",
                    "-b:a", "192k",
                    "-ac", "2",
                    "-y",
                    outputFile
                };
                break;
                
            case "wav":
                command = new String[]{
                    "-i", inputPath,
                    "-vn",
                    "-acodec", "pcm_s16le",
                    "-ac", "2",
                    "-y",
                    outputFile
                };
                break;
                
            case "aac":
                command = new String[]{
                    "-i", inputPath,
                    "-vn",
                    "-acodec", "aac",
                    "-b:a", "128k",
                    "-ac", "2",
                    "-strict", "-2",
                    "-y",
                    outputFile
                };
                break;
                
            case "flac":
                command = new String[]{
                    "-i", inputPath,
                    "-vn",
                    "-acodec", "flac",
                    "-compression_level", "8",
                    "-y",
                    outputFile
                };
                break;
                
            default:
                command = new String[]{
                    "-i", inputPath,
                    "-vn",
                    "-acodec", "libmp3lame",
                    "-b:a", "192k",
                    "-ac", "2",
                    "-y",
                    outputFile
                };
        }
        
        Log.d("AudioProcessor", "音频提取命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 调整音频质量
    public static void adjustAudioQuality(String inputPath, String outputPath,
                                        int bitrate, FFmpegUtil.FFmpegCallback callback) {
        String[] command = {
            "-i", inputPath,
            "-b:a", bitrate + "k",
            "-vn",
            "-ac", "2",
            "-y",
            outputPath
        };
        
        Log.d("AudioProcessor", "调整音频质量命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 音频裁剪
    public static void cutAudio(String inputPath, String outputPath,
                               String startTime, String duration,
                               FFmpegUtil.FFmpegCallback callback) {
        String[] command = {
            "-i", inputPath,
            "-ss", startTime,
            "-t", duration,
            "-c:a", "libmp3lame",
            "-b:a", "192k",
            "-y",
            outputPath
        };
        
        Log.d("AudioProcessor", "音频裁剪命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 音频淡入淡出
    public static void fadeAudio(String inputPath, String outputPath,
                                String fadeIn, String fadeOut,
                                FFmpegUtil.FFmpegCallback callback) {
        String[] command = {
            "-i", inputPath,
            "-af", "afade=t=in:st=0:d=" + fadeIn + ",afade=t=out:st=" + fadeOut + ":d=3",
            "-c:a", "libmp3lame",
            "-b:a", "192k",
            "-y",
            outputPath
        };
        
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