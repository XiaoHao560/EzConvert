package com.tech.ezconvert;

import android.util.Log;

public class AudioProcessor {
    
    // 音频转换
    public static void convertAudio(String inputPath, String outputPath,
                                   String format, FFmpegUtil.FFmpegCallback callback) {
        String[] command;
        
        switch (format.toLowerCase()) {
            case "mp3":
                command = new String[]{
                    "-i", inputPath,
                    "-codec:a", "libmp3lame",
                    "-b:a", "192k",
                    "-ac", "2", // 立体声
                    outputPath
                };
                break;
                
            case "wav":
                command = new String[]{
                    "-i", inputPath,
                    "-codec:a", "pcm_s16le",
                    "-ac", "2",
                    outputPath
                };
                break;
                
            case "aac":
                command = new String[]{
                    "-i", inputPath,
                    "-codec:a", "aac",
                    "-b:a", "128k",
                    "-ac", "2",
                    outputPath
                };
                break;
                
            case "flac":
                command = new String[]{
                    "-i", inputPath,
                    "-codec:a", "flac",
                    "-compression_level", "8",
                    outputPath
                };
                break;
                
            default:
                command = new String[]{
                    "-i", inputPath,
                    outputPath
                };
        }
        
        Log.d("AudioProcessor", "音频转换命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 音频提取（从视频）
    public static void extractAudioFromVideo(String inputPath, String outputPath,
                                           String format, FFmpegUtil.FFmpegCallback callback) {
        String[] command;
        
        switch (format.toLowerCase()) {
            case "mp3":
                command = new String[]{
                    "-i", inputPath,
                    "-vn",
                    "-acodec", "libmp3lame",
                    "-b:a", "192k",
                    "-ac", "2",
                    outputPath
                };
                break;
                
            case "wav":
                command = new String[]{
                    "-i", inputPath,
                    "-vn",
                    "-acodec", "pcm_s16le",
                    "-ac", "2",
                    outputPath
                };
                break;
                
            case "aac":
                command = new String[]{
                    "-i", inputPath,
                    "-vn",
                    "-acodec", "aac",
                    "-b:a", "128k",
                    "-ac", "2",
                    outputPath
                };
                break;
                
            default:
                command = new String[]{
                    "-i", inputPath,
                    "-vn",
                    "-acodec", "copy",
                    outputPath
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
            outputPath
        };
        
        FFmpegUtil.executeCommand(command, callback);
    }
}