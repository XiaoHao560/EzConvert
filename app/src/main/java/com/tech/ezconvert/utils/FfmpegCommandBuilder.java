package com.tech.ezconvert.utils;

import android.content.Context;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.FFprobeSession;
import com.arthenica.ffmpegkit.ReturnCode;
import java.util.ArrayList;

/**
 * FFmpeg 命令生成器
 * 从 VideoProcessor / AudioProcessor 提取命令生成逻辑，供 Worker 和 Processor 共用
 */
public class FfmpegCommandBuilder {
    private static final String TAG = "FfmpegCommandBuilder";

    /**
     * 根据参数构建输出文件完整路径（含扩展名）
     */
    public static String buildOutputPath(String basePath, ParameterData params) {
        String ext;
        switch (params.taskType) {
            case "convert":
                ext = getVideoFileExtension(params.outputFormat);
                break;
            case "compress":
                ext = getVideoFileExtension(params.outputFormat);
                break;
            case "cut_video":
                ext = getVideoFileExtension(params.outputFormat);
                break;
            case "screenshot":
                ext = params.screenshotFormat != null ? params.screenshotFormat : "jpeg";
                break;
            case "extract_audio":
                ext = getAudioFileExtension(params.outputFormat);
                break;
            case "convert_audio":
            case "cut_audio":
                String format = params.outputFormat != null ? params.outputFormat : "mp3";
                ext = getAudioFileExtension(format);
                break;
            default:
                ext = "mp4";
        }
        return basePath + "." + ext;
    }

    /**
     * 根据任务类型生成 FFmpeg 命令数组
     */
    public static String[] buildCommand(String inputPath, String outputPath, ParameterData params, Context context) {
        if (isVideoTask(params.taskType)) {
            return buildVideoCommand(inputPath, outputPath, params, context);
        } else {
            return buildAudioCommand(inputPath, outputPath, params, context);
        }
    }

    private static boolean isVideoTask(String taskType) {
        return "convert".equals(taskType) || "compress".equals(taskType) ||
               "cut_video".equals(taskType) || "screenshot".equals(taskType);
    }

    private static String[] buildVideoCommand(String inputPath, String outputPath, ParameterData params, Context context) {
        boolean hw = ConfigManager.getInstance(context).isHardwareAccelerationEnabled();
        boolean mt = ConfigManager.getInstance(context).isMultithreadingEnabled();

        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("-i");
        cmd.add(inputPath);

        if (mt) {
            cmd.add("-threads");
            cmd.add("0");
        }

        if (params.volume != 100) {
            cmd.add("-af");
            cmd.add("volume=" + (params.volume / 100.0));
        }

        switch (params.taskType) {
            case "convert":
                buildConvertArgs(cmd, params, inputPath, hw);
                break;
            case "compress":
                buildCompressArgs(cmd, params, inputPath, hw);
                break;
            case "cut_video":
                buildCutVideoArgs(cmd, params, inputPath, hw);
                break;
            case "screenshot":
                buildScreenshotArgs(cmd, params);
                break;
        }

        cmd.add("-y");
        cmd.add(outputPath);

        return cmd.toArray(new String[0]);
    }

    private static String[] buildAudioCommand(String inputPath, String outputPath, ParameterData params, Context context) {
        boolean mt = ConfigManager.getInstance(context).isMultithreadingEnabled();

        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("-i");
        cmd.add(inputPath);

        if (mt) {
            cmd.add("-threads");
            cmd.add("0");
        }
        
        // 音频任务禁用视频流
        cmd.add("-vn");

        if (params.volume != 100) {
            cmd.add("-af");
            cmd.add("volume=" + (params.volume / 100.0));
        }

        switch (params.taskType) {
            case "convert_audio":
                buildConvertAudioArgs(cmd, params, inputPath);
                break;
            case "cut_audio":
                buildCutAudioArgs(cmd, params, inputPath);
                break;
            case "extract_audio":
                buildExtractAudioArgs(cmd, params, inputPath);
                break;
        }

        cmd.add("-y");
        cmd.add(outputPath);

        return cmd.toArray(new String[0]);
    }

    // 视频任务参数构建
    private static void buildConvertArgs(ArrayList<String> cmd, ParameterData params, String inputPath, boolean hw) {
        // 视频编码器
        String vCodec = (params.videoCodec != null && !params.videoCodec.isEmpty())
                ? params.videoCodec
                : (hw ? "h264_mediacodec" : "libx264");
        cmd.add("-c:v");
        cmd.add(vCodec);

        // 视频码率
        if ("original".equals(params.videoBitrateMode)) {
            int origBitrate = getOriginalVideoBitrate(inputPath);
            if (origBitrate > 0) {
                cmd.add("-b:v");
                cmd.add(origBitrate + "k");
            } else {
                cmd.add("-crf");
                cmd.add("18");
            }
        } else if ("custom".equals(params.videoBitrateMode)) {
            int val = params.videoBitrateValue;
            String unit = params.videoBitrateUnit;
            cmd.add("-b:v");
            cmd.add(unit.equals("Mbps") ? val + "M" : val + "k");
        } else {
            cmd.add("-crf");
            cmd.add("18");
        }

        // 音频编码器
        String aCodec = (params.audioCodec != null && !params.audioCodec.isEmpty())
                ? params.audioCodec
                : "aac";
        cmd.add("-c:a");
        cmd.add(aCodec);
        
        // 音频码率
        if ("original".equals(params.audioBitrateMode)) {
            int origBitrate = getOriginalAudioBitrate(inputPath);
            if (origBitrate > 0) {
                cmd.add("-b:a");
                cmd.add(origBitrate + "k");
            }
        } else if ("custom".equals(params.audioBitrateMode)) {
            cmd.add("-b:a");
            cmd.add(params.audioBitrateValue + "k");
        }

        // 容器格式
        String format = params.outputFormat;
        if ("mkv".equalsIgnoreCase(format)) {
            format = "matroska";
        }
        if (format != null && !"mp4".equals(format) && !"mov".equals(format)) {
            cmd.add("-f");
            cmd.add(format);
        }
    }

    private static void buildCompressArgs(ArrayList<String> cmd, ParameterData params, String inputPath, boolean hw) {
        String vCodec = (params.videoCodec != null && !params.videoCodec.isEmpty())
                ? params.videoCodec
                : (hw ? "h264_mediacodec" : "libx264");
        cmd.add("-c:v");
        cmd.add(vCodec);

        if (!hw && !"copy".equals(vCodec)) {
            cmd.add("-preset");
            cmd.add("medium");
        }
        
        // 视频码率
        if ("original".equals(params.videoBitrateMode)) {
            int origBitrate = getOriginalVideoBitrate(inputPath);
            if (origBitrate > 0) {
                cmd.add("-b:v");
                cmd.add(origBitrate + "k");
            } else {
                cmd.add("-crf");
                cmd.add("23");
            }
        } else if ("custom".equals(params.videoBitrateMode)) {
            int val = params.videoBitrateValue;
            String unit = params.videoBitrateUnit;
            cmd.add("-b:v");
            cmd.add(unit.equals("Mbps") ? val + "M" : val + "k");
        } else {
            cmd.add("-crf");
            cmd.add("23");
        }

        String aCodec = (params.audioCodec != null && !params.audioCodec.isEmpty())
                ? params.audioCodec
                : "aac";
        cmd.add("-c:a");
        cmd.add(aCodec);
        
        // 音频码率
        if ("original".equals(params.audioBitrateMode)) {
            int origBitrate = getOriginalAudioBitrate(inputPath);
            if (origBitrate > 0) {
                cmd.add("-b:a");
                cmd.add(origBitrate + "k");
            } else {
                cmd.add("-b:a");
                cmd.add("128k");
            }
        } else if ("custom".equals(params.audioBitrateMode)) {
            cmd.add("-b:a");
            cmd.add(params.audioBitrateValue + "k");
        } else {
            cmd.add("-b:a");
            cmd.add("128k");
        }
        
        cmd.add("-movflags");
        cmd.add("+faststart");
    }

    private static void buildCutVideoArgs(ArrayList<String> cmd, ParameterData params, String inputPath, boolean hw) {
        cmd.add("-ss");
        cmd.add(params.cutStartTime);
        cmd.add("-t");
        cmd.add(params.cutDuration);

        String vCodec = (params.videoCodec != null && !params.videoCodec.isEmpty())
                ? params.videoCodec
                : (hw ? "h264_mediacodec" : "libx264");
        cmd.add("-c:v");
        cmd.add(vCodec);

        if (!hw && !"copy".equals(vCodec)) {
            cmd.add("-preset");
            cmd.add("fast");
        }
        
        // 视频码率
        if ("original".equals(params.videoBitrateMode)) {
            int origBitrate = getOriginalVideoBitrate(inputPath);
            if (origBitrate > 0) {
                cmd.add("-b:v");
                cmd.add(origBitrate + "k");
            } else {
                cmd.add("-crf");
                cmd.add("18");
            }
        } else if ("custom".equals(params.videoBitrateMode)) {
            int val = params.videoBitrateValue;
            String unit = params.videoBitrateUnit;
            cmd.add("-b:v");
            cmd.add(unit.equals("Mbps") ? val + "M" : val + "k");
        } else {
            cmd.add("-crf");
            cmd.add("18");
        }

        String aCodec = (params.audioCodec != null && !params.audioCodec.isEmpty())
                ? params.audioCodec
                : "aac";
        cmd.add("-c:a");
        cmd.add(aCodec);
        
        // 音频码率
        if ("original".equals(params.audioBitrateMode)) {
            int origBitrate = getOriginalAudioBitrate(inputPath);
            if (origBitrate > 0) {
                cmd.add("-b:a");
                cmd.add(origBitrate + "k");
            } else {
                cmd.add("-b:a");
                cmd.add("128k");
            }
        } else if ("custom".equals(params.audioBitrateMode)) {
            cmd.add("-b:a");
            cmd.add(params.audioBitrateValue + "k");
        } else {
            cmd.add("-b:a");
            cmd.add("128k");
        }
        
        cmd.add("-avoid_negative_ts");
        cmd.add("make_zero");
    }

    private static void buildScreenshotArgs(ArrayList<String> cmd, ParameterData params) {
        cmd.add("-ss");
        cmd.add(params.cutStartTime);
        cmd.add("-vframes");
        cmd.add("1");

        if ("jpeg".equals(params.screenshotFormat)) {
            cmd.add("-q:v");
            cmd.add(String.valueOf(params.screenshotQuality));
        }

        String resolution = params.screenshotResolution;
        if (resolution != null && !"original".equals(resolution) && !resolution.isEmpty()) {
            cmd.add("-vf");
            cmd.add("scale=" + resolution.replace("x", ":"));
        }
    }

    private static void buildExtractAudioArgs(ArrayList<String> cmd, ParameterData params, String inputPath) {
        String aCodec = (params.audioCodec != null && !params.audioCodec.isEmpty())
                ? params.audioCodec
                : getDefaultAudioCodec(params.outputFormat);
        cmd.add("-c:a");
        cmd.add(aCodec);

        // 音频码率
        if ("original".equals(params.audioBitrateMode)) {
            int origBitrate = getOriginalAudioBitrate(inputPath);
            if (origBitrate > 0) {
                cmd.add("-b:a");
                cmd.add(origBitrate + "k");
            }
        } else if ("custom".equals(params.audioBitrateMode)) {
            cmd.add("-b:a");
            cmd.add(params.audioBitrateValue + "k");
        } else {
            cmd.add("-b:a");
            cmd.add("192k");
        }

        String format = params.outputFormat != null ? params.outputFormat : "mp3";
        if (!"mp3".equals(format) && !"wav".equals(format)) {
            cmd.add("-f");
            cmd.add(format);
        }
    }

    // 音频任务参数构建
    private static void buildConvertAudioArgs(ArrayList<String> cmd, ParameterData params, String inputPath) {
        String aCodec = (params.audioCodec != null && !params.audioCodec.isEmpty())
                ? params.audioCodec
                : getDefaultAudioCodec(params.outputFormat);
        cmd.add("-c:a");
        cmd.add(aCodec);

        // 音频码率
        if ("original".equals(params.audioBitrateMode)) {
            int origBitrate = getOriginalAudioBitrate(inputPath);
            if (origBitrate > 0) {
                cmd.add("-b:a");
                cmd.add(origBitrate + "k");
            }
        } else if ("custom".equals(params.audioBitrateMode)) {
            cmd.add("-b:a");
            cmd.add(params.audioBitrateValue + "k");
        } else {
            cmd.add("-b:a");
            cmd.add("192k");
        }

        String format = params.outputFormat != null ? params.outputFormat : "mp3";
        if (!"mp3".equals(format) && !"wav".equals(format)) {
            cmd.add("-f");
            cmd.add(format);
        }
    }

    private static void buildCutAudioArgs(ArrayList<String> cmd, ParameterData params, String inputPath) {
        cmd.add("-ss");
        cmd.add(params.cutStartTime);
        cmd.add("-t");
        cmd.add(params.cutDuration);

        String aCodec = (params.audioCodec != null && !params.audioCodec.isEmpty())
                ? params.audioCodec
                : getDefaultAudioCodec(params.outputFormat);
        cmd.add("-c:a");
        cmd.add(aCodec);

        // 音频码率
        if ("original".equals(params.audioBitrateMode)) {
            int origBitrate = getOriginalAudioBitrate(inputPath);
            if (origBitrate > 0) {
                cmd.add("-b:a");
                cmd.add(origBitrate + "k");
            } else {
                cmd.add("-b:a");
                cmd.add("192k");
            }
        } else if ("custom".equals(params.audioBitrateMode)) {
            cmd.add("-b:a");
            cmd.add(params.audioBitrateValue + "k");
        } else {
            cmd.add("-b:a");
            cmd.add("192k");
        }
    }
    
    /**
     * 获取原始视频码率 (kbps)，获取失败返回 -1
     */
    private static int getOriginalVideoBitrate(String inputPath) {
        if (inputPath == null || inputPath.isEmpty()) return -1;
        try {
            // 先尝试流级码率
            String probeCmd = "-v quiet -select_streams v:0 -show_entries stream=bit_rate -of default=noprint_wrappers=1:nokey=1 \"" + inputPath + "\"";
            FFprobeSession session = FFprobeKit.execute(probeCmd);
            if (session != null && ReturnCode.isSuccess(session.getReturnCode())) {
                String output = session.getOutput();
                if (output != null && !output.trim().isEmpty() && !"N/A".equals(output.trim())) {
                    int bps = Integer.parseInt(output.trim());
                    return bps / 1000;
                }
            }
            
            // 流级获取失败或返回 N/A，尝试容器级总码率
            probeCmd = "-v quiet -show_entries format=bit_rate -of default=noprint_wrappers=1:nokey=1 \"" + inputPath + "\"";
            session = FFprobeKit.execute(probeCmd);
            if (session != null && ReturnCode.isSuccess(session.getReturnCode())) {
                String output = session.getOutput();
                if (output != null && !output.trim().isEmpty() && !"N/A".equals(output.trim())) {
                    int bps = Integer.parseInt(output.trim());
                    return bps / 1000;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取原始视频码率失败: " + inputPath, e);
        }
        return -1;
    }
    
    /**
     * 获取原始音频码率 (kbps)，获取失败返回 -1
     */
    private static int getOriginalAudioBitrate(String inputPath) {
        if (inputPath == null || inputPath.isEmpty()) return -1;
        try {
            // 先尝试流级码率
            String probeCmd = "-v quiet -select_streams a:0 -show_entries stream=bit_rate -of default=noprint_wrappers=1:nokey=1 \"" + inputPath + "\"";
            FFprobeSession session = FFprobeKit.execute(probeCmd);
            if (session != null && ReturnCode.isSuccess(session.getReturnCode())) {
                String output = session.getOutput();
                if (output != null && !output.trim().isEmpty() && !"N/A".equals(output.trim())) {
                    int bps = Integer.parseInt(output.trim());
                    return bps / 1000;
                }
            }
            
            // 流级获取失败或返回 N/A，尝试容器级总码率（减去视频估算）
            probeCmd = "-v quiet -show_entries format=bit_rate -of default=noprint_wrappers=1:nokey=1 \"" + inputPath + "\"";
            session = FFprobeKit.execute(probeCmd);
            if (session != null && ReturnCode.isSuccess(session.getReturnCode())) {
                String output = session.getOutput();
                if (output != null && !output.trim().isEmpty() && !"N/A".equals(output.trim())) {
                    int bps = Integer.parseInt(output.trim());
                    return bps / 1000;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取原始音频码率失败: " + inputPath, e);
        }
        return -1;
    }

    /**
     * 工具方法
     */
    private static String getVideoFileExtension(String format) {
        if (format == null) return "mp4";
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

    private static String getAudioFileExtension(String format) {
        if (format == null) return "mp3";
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

    private static String getDefaultAudioCodec(String format) {
        if (format == null) return "libmp3lame";
        switch (format.toLowerCase()) {
            case "mp3": return "libmp3lame";
            case "aac": return "aac";
            case "flac": return "flac";
            case "wav": return "pcm_s16le";
            case "ogg": return "libvorbis";
            case "m4a": return "aac";
            default: return "libmp3lame";
        }
    }
}
