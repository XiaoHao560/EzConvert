package com.tech.ezconvert.utils;

import android.content.Context;
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
                ext = "mp4";
                break;
            case "cut_video":
                ext = "mp4";
                break;
            case "screenshot":
                ext = params.screenshotFormat != null ? params.screenshotFormat : "jpeg";
                break;
            case "extract_audio":
                ext = "mp3";
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
               "cut_video".equals(taskType) || "screenshot".equals(taskType) ||
               "extract_audio".equals(taskType);
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
                buildConvertArgs(cmd, params, hw);
                break;
            case "compress":
                buildCompressArgs(cmd, params, hw);
                break;
            case "cut_video":
                buildCutVideoArgs(cmd, params, hw);
                break;
            case "screenshot":
                buildScreenshotArgs(cmd, params);
                break;
            case "extract_audio":
                buildExtractAudioArgs(cmd, params);
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

        if (params.volume != 100) {
            cmd.add("-af");
            cmd.add("volume=" + (params.volume / 100.0));
        }

        switch (params.taskType) {
            case "convert_audio":
                buildConvertAudioArgs(cmd, params);
                break;
            case "cut_audio":
                buildCutAudioArgs(cmd, params);
                break;
        }

        cmd.add("-y");
        cmd.add(outputPath);

        return cmd.toArray(new String[0]);
    }

    // 视频任务参数构建
    private static void buildConvertArgs(ArrayList<String> cmd, ParameterData params, boolean hw) {
        // 视频编码器
        String vCodec = (params.videoCodec != null && !params.videoCodec.isEmpty())
                ? params.videoCodec
                : (hw ? "h264_mediacodec" : "libx264");
        cmd.add("-c:v");
        cmd.add(vCodec);

        // 视频码率
        if ("custom".equals(params.videoBitrateMode)) {
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

        if ("custom".equals(params.audioBitrateMode)) {
            cmd.add("-b:a");
            cmd.add(params.audioBitrateValue + "k");
        }

        // 容器格式
        String format = params.outputFormat;
        if (format != null && !"mp4".equals(format) && !"mov".equals(format)) {
            cmd.add("-f");
            cmd.add(format);
        }
    }

    private static void buildCompressArgs(ArrayList<String> cmd, ParameterData params, boolean hw) {
        if (hw) {
            cmd.add("-c:v");
            cmd.add("h264_mediacodec");
        } else {
            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-preset");
            cmd.add("medium");
        }

        if ("custom".equals(params.videoBitrateMode)) {
            int val = params.videoBitrateValue;
            String unit = params.videoBitrateUnit;
            cmd.add("-b:v");
            cmd.add(unit.equals("Mbps") ? val + "M" : val + "k");
        } else {
            cmd.add("-crf");
            cmd.add("23");
        }

        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add("128k");
        cmd.add("-movflags");
        cmd.add("+faststart");
    }

    private static void buildCutVideoArgs(ArrayList<String> cmd, ParameterData params, boolean hw) {
        cmd.add("-ss");
        cmd.add(params.cutStartTime);
        cmd.add("-t");
        cmd.add(params.cutDuration);

        if (hw) {
            cmd.add("-c:v");
            cmd.add("h264_mediacodec");
        } else {
            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-preset");
            cmd.add("fast");
        }
        cmd.add("-c:a");
        cmd.add("aac");
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

    private static void buildExtractAudioArgs(ArrayList<String> cmd, ParameterData params) {
        cmd.add("-vn");

        String aCodec = (params.audioCodec != null && !params.audioCodec.isEmpty())
                ? params.audioCodec
                : getDefaultAudioCodec(params.outputFormat);
        cmd.add("-c:a");
        cmd.add(aCodec);

        if ("custom".equals(params.audioBitrateMode)) {
            cmd.add("-b:a");
            cmd.add(params.audioBitrateValue + "k");
        }

        String format = params.outputFormat != null ? params.outputFormat : "mp3";
        if (!"mp3".equals(format) && !"wav".equals(format)) {
            cmd.add("-f");
            cmd.add(format);
        }
    }

    // 音频任务参数构建
    private static void buildConvertAudioArgs(ArrayList<String> cmd, ParameterData params) {
        String aCodec = (params.audioCodec != null && !params.audioCodec.isEmpty())
                ? params.audioCodec
                : getDefaultAudioCodec(params.outputFormat);
        cmd.add("-c:a");
        cmd.add(aCodec);

        if ("custom".equals(params.audioBitrateMode)) {
            cmd.add("-b:a");
            cmd.add(params.audioBitrateValue + "k");
        }

        String format = params.outputFormat != null ? params.outputFormat : "mp3";
        if (!"mp3".equals(format) && !"wav".equals(format)) {
            cmd.add("-f");
            cmd.add(format);
        }
    }

    private static void buildCutAudioArgs(ArrayList<String> cmd, ParameterData params) {
        cmd.add("-ss");
        cmd.add(params.cutStartTime);
        cmd.add("-t");
        cmd.add(params.cutDuration);
        cmd.add("-c:a");
        cmd.add("libmp3lame");
        cmd.add("-b:a");
        cmd.add("192k");
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
