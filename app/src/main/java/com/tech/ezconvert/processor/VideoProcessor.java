package com.tech.ezconvert.processor;

import android.content.Context;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.ui.TranscodeSettingsActivity;
import com.tech.ezconvert.utils.FFmpegUtil;
import java.io.File;
import java.util.ArrayList;

public class VideoProcessor {
    
    // 视频转换
    public static void convertVideo(String inputPath, String outputPath, 
                                   String format, int volume, FFmpegUtil.FFmpegCallback callback, Context context) {
        
        boolean hardwareAcceleration = TranscodeSettingsActivity.isHardwareAccelerationEnabled(context);
        
        /**
         * 对 MKV 格式，启用硬件加速时采用两步转换方案
         * 一：硬件加速生成 MP4 中间文件
         * 二：转封装/转码为目标格式
         */
        if (hardwareAcceleration && (format.equalsIgnoreCase("mkv"))) {
            convertVideoTwoStep(inputPath, outputPath, format, volume, callback, context);
        } else {
            convertVideoDirect(inputPath, outputPath, format, volume, callback, context);
        }
    }
    
    // 两步转换：硬件加速 MP4 -> 转封装 MKV 
    // 用于 MKV 格式，解决 Android 平台缺少 MKV 硬件编码器的问题
    private static void convertVideoTwoStep(String inputPath, String outputPath, 
                                           String format, int volume, FFmpegUtil.FFmpegCallback callback, Context context) {
        
        // 生成临时 MP4 文件（硬件加速）
        String tempMp4Path = context.getCacheDir() + "/temp_hw_" + System.currentTimeMillis() + ".mp4";
        String finalOutputPath = outputPath + "." + format.toLowerCase();
        
        // 硬件加速转换为 h264_media/AAC 的 MP4
        ArrayList<String> step1Cmd = new ArrayList<>();
        step1Cmd.add("-i"); step1Cmd.add(inputPath);
        step1Cmd.add("-c:v"); step1Cmd.add("h264_mediacodec");
        int inputBitrate = getVideoBitrate(inputPath);
        step1Cmd.add("-b:v"); step1Cmd.add(inputBitrate + "k");
        step1Cmd.add("-c:a"); step1Cmd.add("aac");
        step1Cmd.add("-b:a"); step1Cmd.add("128k");
        step1Cmd.add("-movflags"); step1Cmd.add("+faststart");
        
        if (TranscodeSettingsActivity.isMultithreadingEnabled(context)) {
            step1Cmd.add("-threads"); step1Cmd.add("0");
        }
        if (volume != 100) {
            step1Cmd.add("-af"); step1Cmd.add("volume=" + (volume / 100.0));
        }
        step1Cmd.add("-y"); step1Cmd.add(tempMp4Path);
        
        FFmpegUtil.executeCommand(step1Cmd.toArray(new String[0]), new FFmpegUtil.FFmpegCallback() {
            @Override
            public void onComplete(boolean success, String message) {
                if (success) {
                    // 从 MP4 转换到目标格式
                    ArrayList<String> step2Cmd = new ArrayList<>();
                    step2Cmd.add("-i"); step2Cmd.add(tempMp4Path);
                    
                    if (format.equalsIgnoreCase("mkv")) {
                        step2Cmd.add("-c:v"); step2Cmd.add("copy");
                        step2Cmd.add("-c:a"); step2Cmd.add("copy");
                        step2Cmd.add("-f"); step2Cmd.add("matroska");
                    }
                    
                    if (TranscodeSettingsActivity.isMultithreadingEnabled(context)) {
                        step2Cmd.add("-threads"); step2Cmd.add("0");
                    }
                    step2Cmd.add("-y"); step2Cmd.add(finalOutputPath);
                    
                    FFmpegUtil.executeCommand(step2Cmd.toArray(new String[0]), new FFmpegUtil.FFmpegCallback() {
                        @Override
                        public void onComplete(boolean success, String message) {
                            // 清理临时 MP4 文件
                            new File(tempMp4Path).delete();
                            
                            if (success) {
                                callback.onComplete(true, "转换成功: " + message);
                            } else {
                                callback.onComplete(false, "第二步封装失败: " + message);
                            }
                        }
                        
                        @Override
                        public void onProgress(int progress, long time) {
                            // 将第二步进度作为总进度的 50-100%
                            callback.onProgress(50 + progress / 2, time);
                        }
                        
                        @Override
                        public void onError(String error) {
                            new File(tempMp4Path).delete();
                            callback.onError("第二步报错: " + error);
                        }
                    });
                } else {
                    callback.onComplete(false, "第一步硬件编码失败: " + message);
                }
            }
            
            @Override
            public void onProgress(int progress, long time) {
                // 将第一步进度作为总进度的 0-50%
                callback.onProgress(progress / 2, time);
            }
            
            @Override
            public void onError(String error) {
                new File(tempMp4Path).delete(); // 清理临时文件
                callback.onError("第一步硬件编码报错: " + error);
            }
        });
    }
    
    /**
     * 直接转换方法
     * 适用于 MP4/MOV/AVI/FLV/GIF 等格式，以及关闭硬件加速时的所有格式
     */
    private static void convertVideoDirect(String inputPath, String outputPath, 
                                          String format, int volume, FFmpegUtil.FFmpegCallback callback, Context context) {
        
        String outputFile = outputPath + "." + getFileExtension(format);
        
        boolean hardwareAcceleration = TranscodeSettingsActivity.isHardwareAccelerationEnabled(context);
        boolean multithreading = TranscodeSettingsActivity.isMultithreadingEnabled(context);
        
        Log.d("VideoProcessor", "硬件加速: " + hardwareAcceleration + ", 多线程: " + multithreading);
        
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
            // mp4,mov 格式如果启用了硬件编解码则使用 h264_media
            //            没有启用则使用 libx264
            case "mp4":
            case "mov":
                if (hardwareAcceleration) {
                    commandList.add("-c:v");
                    commandList.add("h264_mediacodec");
                    int inputBitrate = getVideoBitrate(inputPath);
                    commandList.add("-b:v");
                    commandList.add(inputBitrate + "k");
                } else {
                    commandList.add("-c:v");
                    commandList.add("libx264");
                    commandList.add("-preset");
                    commandList.add("medium");
                    commandList.add("-crf");
                    commandList.add("23");
                }
                commandList.add("-c:a");
                commandList.add("aac");
                commandList.add("-b:a");
                commandList.add("128k");
                commandList.add("-movflags");
                commandList.add("+faststart");
                break;
                
            case "mkv":
                // mkv 目前只有软件解码方案 libx265
                // 当硬件加速启用时，此方法不会被调用（由两步转换处理）
                commandList.add("-c:v");
                commandList.add("libx265");
                commandList.add("-preset");
                commandList.add("medium");
                commandList.add("-crf");
                commandList.add("28");
                commandList.add("-c:a");
                commandList.add("aac");
                commandList.add("-b:a");
                commandList.add("128k");
                break;
                
            case "webm":
                // webm 目前只有软件解码方案 libvpx-vp9
                commandList.add("-c:v");
                commandList.add("libvpx-vp9");
                commandList.add("-b:v");
                commandList.add("1M");
                commandList.add("-c:a");
                commandList.add("libopus");
                commandList.add("-b:a");
                commandList.add("128k");
                break;
                
            case "avi":
            // avi 如果启用了硬件编解码则使用 h264_media
            //     没有启用则使用 mpeg4
                if (hardwareAcceleration) {
                    commandList.add("-c:v");
                    commandList.add("h264_mediacodec");
                    int inputBitrate = getVideoBitrate(inputPath);
                    commandList.add("-b:v");
                    commandList.add(inputBitrate + "k");
                } else {
                    commandList.add("-c:v");
                    commandList.add("mpeg4");
                    commandList.add("-q:v");
                    commandList.add("5");
                }
                commandList.add("-c:a");
                commandList.add("libmp3lame");
                commandList.add("-b:a");
                commandList.add("192k");
                break;
                
            case "flv":
            // flv 如果启用了硬件编解码则使用 h264_media
            //     没有启用则使用 libx264
                if (hardwareAcceleration) {
                    commandList.add("-c:v");
                    commandList.add("h264_mediacodec");
                    int inputBitrate = getVideoBitrate(inputPath);
                    commandList.add("-b:v");
                    commandList.add(inputBitrate + "k");
                } else {
                    commandList.add("-c:v");
                    commandList.add("libx264");
                    commandList.add("-preset");
                    commandList.add("fast");
                }
                commandList.add("-c:a");
                commandList.add("aac");
                commandList.add("-b:a");
                commandList.add("128k");
                break;
                
            case "gif":
            // gif 没有硬件编解码
                commandList.add("-vf");
                commandList.add("fps=10,scale=480:-1:flags=lanczos");
                commandList.add("-pix_fmt");
                commandList.add("rgb8");
                commandList.add("-loop");
                commandList.add("0");
                break;
                
            default:
            // 默认方案如果启用了硬件编解码则使用 h264_media
            //        没有启用则使用 libx264
                if (hardwareAcceleration) {
                    commandList.add("-c:v");
                    commandList.add("h264_mediacodec");
                    int inputBitrate = getVideoBitrate(inputPath);
                    commandList.add("-b:v");
                    commandList.add(inputBitrate + "k");
                } else {
                    commandList.add("-c:v");
                    commandList.add("libx264");
                    commandList.add("-preset");
                    commandList.add("medium");
                    commandList.add("-crf");
                    commandList.add("23");
                }
                commandList.add("-c:a");
                commandList.add("aac");
                commandList.add("-b:a");
                commandList.add("128k");
        }
        
        commandList.add("-y");
        commandList.add(outputFile);
        
        String[] command = commandList.toArray(new String[0]);
        Log.d("VideoProcessor", "转换命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 视频压缩
    public static void compressVideo(String inputPath, String outputPath, 
                                    int quality, int volume, FFmpegUtil.FFmpegCallback callback, Context context) {
        // quality: 0-100, 0最高质量
        int crf = 51 - (quality * 51 / 100);
        if (crf < 18) crf = 18;
        if (crf > 51) crf = 51;
        
        String outputFile = outputPath + "_compressed.mp4";
        
        boolean hardwareAcceleration = TranscodeSettingsActivity.isHardwareAccelerationEnabled(context);
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
        
        if (hardwareAcceleration) {
            commandList.add("-c:v");
            commandList.add("h264_mediacodec");
            commandList.add("-b:v");
            commandList.add(getBitrateForQuality(quality) + "k");
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
        commandList.add("-y");
        commandList.add(outputFile);
        
        String[] command = commandList.toArray(new String[0]);
        Log.d("VideoProcessor", "压缩命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 视频裁剪
    public static void cutVideo(String inputPath, String outputPath,
                               String startTime, String duration, int volume,
                               FFmpegUtil.FFmpegCallback callback, Context context) {
        boolean hardwareAcceleration = TranscodeSettingsActivity.isHardwareAccelerationEnabled(context);
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
        
        if (hardwareAcceleration) {
            commandList.add("-c:v");
            commandList.add("h264_mediacodec");
            commandList.add("-b:v");
            commandList.add("2000k");
        } else {
            commandList.add("-c:v");
            commandList.add("libx264");
            commandList.add("-preset");
            commandList.add("fast");
        }
        
        commandList.add("-c:a");
        commandList.add("aac");
        commandList.add("-avoid_negative_ts");
        commandList.add("make_zero");
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
        
        boolean hardwareAcceleration = TranscodeSettingsActivity.isHardwareAccelerationEnabled(context);
        boolean multithreading = TranscodeSettingsActivity.isMultithreadingEnabled(context);
        
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
            commandList.add("-b:v");
            commandList.add("2000k");
        } else {
            commandList.add("-c:v");
            commandList.add("libx264");
            commandList.add("-preset");
            commandList.add("fast");
        }
        
        commandList.add("-c:a");
        commandList.add("aac");
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
        
        boolean hardwareAcceleration = TranscodeSettingsActivity.isHardwareAccelerationEnabled(context);
        boolean multithreading = TranscodeSettingsActivity.isMultithreadingEnabled(context);
        
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
            commandList.add("-b:v");
            commandList.add("1500k");
        } else {
            commandList.add("-c:v");
            commandList.add("libx264");
            commandList.add("-preset");
            commandList.add("fast");
        }
        
        commandList.add("-c:a");
        commandList.add("aac");
        commandList.add("-y");
        commandList.add(outputPath);
        
        String[] command = commandList.toArray(new String[0]);
        Log.d("VideoProcessor", "调整分辨率命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, callback);
    }
    
    // 提取视频帧（截图）
    public static void extractFrame(String inputPath, String outputPath,
                                   String timestamp, FFmpegUtil.FFmpegCallback callback, Context context) {
        boolean multithreading = TranscodeSettingsActivity.isMultithreadingEnabled(context);
        
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
        boolean hardwareAcceleration = TranscodeSettingsActivity.isHardwareAccelerationEnabled(context);
        boolean multithreading = TranscodeSettingsActivity.isMultithreadingEnabled(context);
        
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
            commandList.add("-b:v");
            commandList.add("2000k");
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
            case "mov": return "mov";
            case "mkv": return "mkv";
            case "webm": return "webm";
            case "avi": return "avi";
            case "flv": return "flv";
            case "gif": return "gif";
            default: return "mp4";
        }
    }
    
    // 根据质量获取比特率
    private static int getBitrateForQuality(int quality) {
        if (quality >= 90) return 4000; // 高质量
        if (quality >= 70) return 2000; // 中等质量
        return 1000; // 低质量
    }

    // 获取输入视频的码率 (kbps)
    private static int getVideoBitrate(String inputPath) {
        try {
            // 使用 FFprobeKit 获取媒体信息
            com.arthenica.ffmpegkit.FFprobeSession session = 
                com.arthenica.ffmpegkit.FFprobeKit.execute("-v quiet -select_streams v:0 -show_entries stream=bit_rate -of default=noprint_wrappers=1:nokey=1 \"" + inputPath + "\"");
            
            if (session == null) {
                Log.w("VideoProcessor", "无法创建 FFprobe 会话，使用默认码率");
                return 2000;
            }
            
            String output = session.getOutput();
            if (output != null && !output.trim().isEmpty()) {
                // 输出格式是纯数字
                String bitrateStr = output.trim();
                Log.d("VideoProcessor", "检测到输入文件码率: " + bitrateStr + " bps");
                
                // 转换为 kbps
                int bitrateBps = Integer.parseInt(bitrateStr);
                return Math.max(100, bitrateBps / 1000); // 确保至少 100k
            } else {
                Log.w("VideoProcessor", "FFprobe 未返回码率信息，使用默认码率");
            }
        } catch (Exception e) {
            Log.e("VideoProcessor", "获取输入文件码率失败: " + e.getMessage(), e);
        }
        
        // 如果获取失败，返回默认值
        return 2000;
    }
}
