package com.tech.ezconvert.processor;

import android.content.Context;
import com.tech.ezconvert.utils.CacheManager;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.ui.TranscodeSettingsActivity;
import com.tech.ezconvert.utils.FFmpegUtil;
import java.io.File;
import java.util.ArrayList;

public class AudioProcessor {
    
    // 包装回调，确保缓存文件被清理
    private static FFmpegUtil.FFmpegCallback wrapCallback(final FFmpegUtil.FFmpegCallback original, 
                                                         final String cachePath, 
                                                         final boolean isFromCache) {
        if (!isFromCache || original == null) return original;
        
        return new FFmpegUtil.FFmpegCallback() {
            @Override
            public void onProgress(int progress, long time) {
                original.onProgress(progress, time);
            }
            
            @Override
            public void onComplete(boolean success, String message) {
                CacheManager.releaseCacheFile(cachePath);
                original.onComplete(success, message);
            }
            
            @Override
            public void onError(String error) {
                CacheManager.releaseCacheFile(cachePath);
                original.onError(error);
            }
        };
    }
    
    /**
     * 向上取整到指定步长（用于音频码率）
     * @param value 原始值 (kbps)
     * @param step  取整步长 (kbps)
     * @return 向上取整后的值
     */
    private static int roundUpToNearest(int value, int step) {
        if (value <= 0) return step;
        return ((value + step - 1) / step) * step;
    }
    
    /**
     * 获取输入文件的音频流码率 (kbps)
     * @param inputPath 文件路径（视频或音频）
     * @return 音频码率 (kbps)，失败返回默认 192
     */
    private static int getAudioBitrate(String inputPath) {
        try {
            com.arthenica.ffmpegkit.FFprobeSession session = 
                com.arthenica.ffmpegkit.FFprobeKit.execute(
                    "-v quiet -select_streams a:0 -show_entries stream=bit_rate -of default=noprint_wrappers=1:nokey=1 \"" + inputPath + "\"");
            
            if (session == null) {
                Log.w("AudioProcessor", "无法创建 FFprobe 会话，使用默认音频码率");
                return 192;
            }
            
            String output = session.getOutput();
            if (output != null && !output.trim().isEmpty()) {
                String bitrateStr = output.trim();
                Log.d("AudioProcessor", "检测到输入音频码率: " + bitrateStr + " bps");
                int bitrateBps = Integer.parseInt(bitrateStr);
                return Math.max(32, bitrateBps / 1000); // 至少 32kbps
            } else {
                Log.w("AudioProcessor", "FFprobe 未返回音频码率信息，使用默认码率");
            }
        } catch (Exception e) {
            Log.e("AudioProcessor", "获取输入音频码率失败: " + e.getMessage(), e);
        }
        return 192;
    }
    
    /**
     * 根据质量选项获取目标音频码率 (kbps)
     * @param qualityOption 质量选项文本 ("自动" / "高质量" / "中等质量" / "低质量")
     * @param inputPath     输入文件路径(仅自动时需要)
     * @return 目标音频码率 (kbps)
     */
    private static int getTargetAudioBitrate(String qualityOption, String inputPath) {
        if ("自动".equals(qualityOption)) {
            int inputBitrate = getAudioBitrate(inputPath);
            // 音频码率向上取整到 32 的倍数，尽量匹配标准码率且不浪费
            return roundUpToNearest(inputBitrate, 32);
        }
        // 固定质量映射（复用视频质量选项文本）
        switch (qualityOption) {
            case "高质量":   return 320;
            case "中等质量": return 192;
            case "低质量":   return 128;
            default:        return 192;
        }
    }
    
    // 音频转换
    public static void convertAudio(String inputPath, String outputPath,
                                   String format, int volume, String qualityOption,
                                   FFmpegUtil.FFmpegCallback callback, Context context) {
        
        // 检查并准备文件路径
        CacheManager.AccessResult accessResult = CacheManager.prepareFileForProcessing(context, inputPath);
        if (accessResult == null) {
            callback.onError("无法访问输入文件");
            return;
        }
        
        final String usablePath = accessResult.usablePath;
        final boolean isFromCache = accessResult.isFromCache;
        FFmpegUtil.FFmpegCallback wrappedCallback = wrapCallback(callback, usablePath, isFromCache);
        
        String fileName = new File(inputPath).getName();
        String outputFile = outputPath + "." + getAudioExtension(format);
        
        // 根据质量选项获取目标码率
        int targetBitrate = getTargetAudioBitrate(qualityOption, usablePath);
        
        boolean multithreading = TranscodeSettingsActivity.isMultithreadingEnabled(context);
        
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(usablePath);
        
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
                commandList.add(targetBitrate + "k");
                commandList.add("-ac");
                commandList.add("2");
                break;
                
            case "wav":
                commandList.add("-c:a");
                commandList.add("pcm_s16le");
                commandList.add("-ac");
                commandList.add("2");
                // WAV 无损，忽略码率参数
                break;
                
            case "aac":
                commandList.add("-c:a");
                commandList.add("aac");
                commandList.add("-b:a");
                commandList.add(targetBitrate + "k");
                commandList.add("-ac");
                commandList.add("2");
                break;
                
            case "flac":
                commandList.add("-c:a");
                commandList.add("flac");
                commandList.add("-compression_level");
                commandList.add("8");
                // FLAC 无损压缩，忽略码率参数
                break;
                
            case "ogg":
                commandList.add("-c:a");
                commandList.add("libvorbis");
                commandList.add("-q:a");
                commandList.add("4");
                // Vorbis 用质量级别，忽略码率
                break;
                
            case "m4a":
                commandList.add("-c:a");
                commandList.add("aac");
                commandList.add("-b:a");
                commandList.add(targetBitrate + "k");
                commandList.add("-ac");
                commandList.add("2");
                break;
                
            default:
                commandList.add("-c:a");
                commandList.add("libmp3lame");
                commandList.add("-b:a");
                commandList.add(targetBitrate + "k");
                commandList.add("-ac");
                commandList.add("2");
        }
        
        commandList.add("-y");
        commandList.add(outputFile);
        
        String[] command = commandList.toArray(new String[0]);
        Log.d("AudioProcessor", "音频转换命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, wrappedCallback, usablePath, fileName);
    }
    
    // 音频提取（从视频）
    public static void extractAudioFromVideo(String inputPath, String outputPath,
                                           String format, int volume, String qualityOption,
                                           FFmpegUtil.FFmpegCallback callback, Context context) {
        
        // 检查并准备文件路径
        CacheManager.AccessResult accessResult = CacheManager.prepareFileForProcessing(context, inputPath);
        if (accessResult == null) {
            callback.onError("无法访问输入文件");
            return;
        }
        
        final String usablePath = accessResult.usablePath;
        final boolean isFromCache = accessResult.isFromCache;
        FFmpegUtil.FFmpegCallback wrappedCallback = wrapCallback(callback, usablePath, isFromCache);
        
        String fileName = new File(inputPath).getName();
        String outputFile = outputPath + "." + getAudioExtension(format);
        
        // 根据质量选项获取目标码率
        int targetBitrate = getTargetAudioBitrate(qualityOption, usablePath);
        
        boolean multithreading = TranscodeSettingsActivity.isMultithreadingEnabled(context);
        
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(usablePath);
        
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
                commandList.add(targetBitrate + "k");
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
                commandList.add(targetBitrate + "k");
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
                commandList.add(targetBitrate + "k");
                commandList.add("-ac");
                commandList.add("2");
                break;
                
            default:
                commandList.add("-c:a");
                commandList.add("libmp3lame");
                commandList.add("-b:a");
                commandList.add(targetBitrate + "k");
                commandList.add("-ac");
                commandList.add("2");
        }
        
        commandList.add("-y");
        commandList.add(outputFile);
        
        String[] command = commandList.toArray(new String[0]);
        Log.d("AudioProcessor", "音频提取命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, wrappedCallback, usablePath, fileName);
    }
    
    // 调整音频质量
    public static void adjustAudioQuality(String inputPath, String outputPath,
                                        int bitrate, FFmpegUtil.FFmpegCallback callback, Context context) {
        
        // 检查并准备文件路径
        CacheManager.AccessResult accessResult = CacheManager.prepareFileForProcessing(context, inputPath);
        if (accessResult == null) {
            callback.onError("无法访问输入文件");
            return;
        }
        
        final String usablePath = accessResult.usablePath;
        final boolean isFromCache = accessResult.isFromCache;
        FFmpegUtil.FFmpegCallback wrappedCallback = wrapCallback(callback, usablePath, isFromCache);
        
        String fileName = new File(inputPath).getName();
        
        boolean multithreading = TranscodeSettingsActivity.isMultithreadingEnabled(context);
        
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(usablePath);
        
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
        FFmpegUtil.executeCommand(command, wrappedCallback, usablePath, fileName);
    }
    
    // 音频裁剪
    public static void cutAudio(String inputPath, String outputPath,
                               String startTime, String duration, int volume,
                               FFmpegUtil.FFmpegCallback callback, Context context) {
        
        // 检查并准备文件路径
        CacheManager.AccessResult accessResult = CacheManager.prepareFileForProcessing(context, inputPath);
        if (accessResult == null) {
            callback.onError("无法访问输入文件");
            return;
        }
        
        final String usablePath = accessResult.usablePath;
        final boolean isFromCache = accessResult.isFromCache;
        FFmpegUtil.FFmpegCallback wrappedCallback = wrapCallback(callback, usablePath, isFromCache);
        
        String fileName = new File(inputPath).getName();
        
        boolean multithreading = TranscodeSettingsActivity.isMultithreadingEnabled(context);
        
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(usablePath);
        
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
        FFmpegUtil.executeCommand(command, wrappedCallback, usablePath, fileName);
    }
    
    // 音频淡入淡出
    public static void fadeAudio(String inputPath, String outputPath,
                                String fadeIn, String fadeOut,
                                FFmpegUtil.FFmpegCallback callback, Context context) {
        
        // 检查并准备文件路径
        CacheManager.AccessResult accessResult = CacheManager.prepareFileForProcessing(context, inputPath);
        if (accessResult == null) {
            callback.onError("无法访问输入文件");
            return;
        }
        
        final String usablePath = accessResult.usablePath;
        final boolean isFromCache = accessResult.isFromCache;
        FFmpegUtil.FFmpegCallback wrappedCallback = wrapCallback(callback, usablePath, isFromCache);
        
        String fileName = new File(inputPath).getName();
        
        boolean multithreading = TranscodeSettingsActivity.isMultithreadingEnabled(context);
        
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(usablePath);
        
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
        FFmpegUtil.executeCommand(command, wrappedCallback, usablePath, fileName);
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
