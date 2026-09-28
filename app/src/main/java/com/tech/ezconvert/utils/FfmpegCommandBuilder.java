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
            case "convert_image":
                ext = getImageFileExtension(params.outputFormat);
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
        if ("convert_image".equals(params.taskType)) {
            return buildImageCommand(inputPath, outputPath, params);
        } else if (isVideoTask(params.taskType)) {
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
    
    private static String[] buildImageCommand(String inputPath, String outputPath, ParameterData params) {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("-i");
        cmd.add(inputPath);

        String format = params.outputFormat != null ? params.outputFormat.toLowerCase() : "jpg";
        String imageCodec = getImageCodec(format);
        if (imageCodec != null) {
            cmd.add("-c:v");
            cmd.add(imageCodec);
        }
        if ("ico".equals(format)) {
            // FFmpeg 的 ICO muxer 要求 PNG-backed ICO 使用 RGBA 像素格式
            cmd.add("-pix_fmt");
            cmd.add("rgba");
        }

        // 质量控制（仅对明确支持通用质量参数的格式启用）
        if ("custom".equals(params.imageQualityMode)) {
            int quality = Math.min(100, Math.max(1, params.imageQuality));
            switch (format) {
                case "jpg":
                case "jpeg":
                    cmd.add("-q:v");
                    cmd.add(String.valueOf(Math.max(2, Math.min(31, 31 - (quality * 29 / 100)))));
                    break;
                case "webp":
                case "heif":
                case "heic":
                    cmd.add("-quality");
                    cmd.add(String.valueOf(quality));
                    break;
                case "avif":
                    // libaom-av1 使用 CRF：100=无损附近，1=高压缩
                    cmd.add("-crf");
                    cmd.add(String.valueOf(Math.max(0, 63 - (quality * 63 / 100))));
                    break;
                case "jxl":
                    // libjxl distance：0=无损，15=高压缩
                    cmd.add("-distance");
                    cmd.add(String.valueOf(15.0 - (quality * 15.0 / 100.0)));
                    break;
            }
        }

        if ("custom".equals(params.imageResolutionMode)
                && params.imageResolution != null
                && !params.imageResolution.isEmpty()
                && !"original".equalsIgnoreCase(params.imageResolution)) {
            cmd.add("-vf");
            cmd.add("scale=" + params.imageResolution + ":flags=lanczos");
        }

        cmd.add("-y");
        cmd.add(outputPath);
        return cmd.toArray(new String[0]);
    }

    // 视频任务参数构建
    private static void buildConvertArgs(ArrayList<String> cmd, ParameterData params, String inputPath, boolean hw) {
        String vCodec = getVideoCodecOrDefault(params, hw);
        cmd.add("-c:v");
        cmd.add(vCodec);
        addVideoQualityArgs(cmd, vCodec, params, inputPath, 18);

        addAudioCodecArgs(cmd, params.audioCodec, inputPath, params.audioBitrateMode,
                params.audioBitrateValue, false, false);

        if ("swf".equalsIgnoreCase(params.outputFormat)) {
            cmd.add("-ar");
            cmd.add("44100");
        }

        String muxer = getVideoMuxer(params.outputFormat);
        if (muxer != null) {
            cmd.add("-f");
            cmd.add(muxer);
        }
    }

    private static void buildCompressArgs(ArrayList<String> cmd, ParameterData params, String inputPath, boolean hw) {
        String vCodec = getVideoCodecOrDefault(params, hw);
        cmd.add("-c:v");
        cmd.add(vCodec);
        addVideoQualityArgs(cmd, vCodec, params, inputPath, 23);

        addAudioCodecArgs(cmd, params.audioCodec, inputPath, params.audioBitrateMode,
                params.audioBitrateValue, true, true);

        if ("swf".equalsIgnoreCase(params.outputFormat)) {
            cmd.add("-ar");
            cmd.add("44100");
        }

        String muxer = getVideoMuxer(params.outputFormat);
        if (muxer != null) {
            cmd.add("-f");
            cmd.add(muxer);
        }

        // faststart 只适用于 ISO-BMFF 类容器
        if ("mp4".equalsIgnoreCase(params.outputFormat)
                || "mov".equalsIgnoreCase(params.outputFormat)
                || "m4v".equalsIgnoreCase(params.outputFormat)) {
            cmd.add("-movflags");
            cmd.add("+faststart");
        }
    }

    private static void buildCutVideoArgs(ArrayList<String> cmd, ParameterData params, String inputPath, boolean hw) {
        cmd.add("-ss");
        cmd.add(params.cutStartTime);
        cmd.add("-t");
        cmd.add(params.cutDuration);

        String vCodec = getVideoCodecOrDefault(params, hw);
        cmd.add("-c:v");
        cmd.add(vCodec);
        addVideoQualityArgs(cmd, vCodec, params, inputPath, 18);

        addAudioCodecArgs(cmd, params.audioCodec, inputPath, params.audioBitrateMode,
                params.audioBitrateValue, true, false);

        if ("swf".equalsIgnoreCase(params.outputFormat)) {
            cmd.add("-ar");
            cmd.add("44100");
        }

        String muxer = getVideoMuxer(params.outputFormat);
        if (muxer != null) {
            cmd.add("-f");
            cmd.add(muxer);
        }

        cmd.add("-avoid_negative_ts");
        cmd.add("make_zero");
    }

    private static String getVideoCodecOrDefault(ParameterData params, boolean hw) {
        if (params.videoCodec != null && !params.videoCodec.isEmpty()) {
            return params.videoCodec;
        }
        return hw ? "h264_mediacodec" : "libx264";
    }

    private static void addVideoQualityArgs(ArrayList<String> cmd, String codec,
                                            ParameterData params, String inputPath,
                                            int defaultCrf) {
        String c = codec != null ? codec.toLowerCase() : "";

        if ("original".equals(params.videoBitrateMode)) {
            int origBitrate = getOriginalVideoBitrate(inputPath);
            if (origBitrate > 0) {
                cmd.add("-b:v");
                cmd.add(origBitrate + "k");
                return;
            }
        }

        if ("custom".equals(params.videoBitrateMode)) {
            int val = Math.max(1, params.videoBitrateValue);
            String unit = params.videoBitrateUnit;
            cmd.add("-b:v");
            cmd.add("Mbps".equalsIgnoreCase(unit) ? val + "M" : val + "k");
            return;
        }

        // MediaCodec 以及不稳定/不支持 CRF 的编码器统一采用码率
        if (c.contains("mediacodec") || c.contains("openh264") || c.contains("vvenc")
                || c.contains("kvazaar")) {
            cmd.add("-b:v");
            cmd.add(defaultBitrateForCodec(c));
            return;
        }

        if (supportsCrf(c)) {
            cmd.add("-crf");
            cmd.add(String.valueOf(defaultCrf));
            if (supportsPreset(c)) {
                cmd.add("-preset");
                cmd.add(defaultPreset(c));
            }
            return;
        }

        if (supportsQScale(c)) {
            cmd.add("-q:v");
            cmd.add(defaultQScale(c));
        }
    }

    private static boolean supportsCrf(String c) {
        return c.contains("libx264") || c.contains("libx265")
                || c.equals("libvpx") || c.contains("libvpx-vp9")
                || c.contains("libaom-av1") || c.contains("libsvtav1");
    }

    private static boolean supportsPreset(String c) {
        return c.contains("libx264") || c.contains("libx265");
    }

    private static String defaultPreset(String c) {
        return c.contains("libx265") ? "medium" : "medium";
    }

    private static boolean supportsQScale(String c) {
        return c.equals("mpeg4") || c.contains("libxvid") || c.equals("mpeg1video")
                || c.equals("mpeg2video") || c.equals("mjpeg") || c.equals("flv1")
                || c.equals("h263") || c.equals("h263p") || c.contains("libtheora");
    }

    private static String defaultQScale(String c) {
        if (c.contains("libtheora")) return "5";
        if (c.equals("mjpeg")) return "5";
        return "5";
    }

    private static String defaultBitrateForCodec(String c) {
        if (c.equals("dnxhd")) return "36M";
        if (c.contains("mediacodec") && c.contains("hevc")) return "6M";
        return "4M";
    }

    private static void addAudioCodecArgs(ArrayList<String> cmd, String audioCodec, String inputPath,
                                          String bitrateMode, int bitrateValue,
                                          boolean fallback128k, boolean allowOriginalFallback) {
        String aCodec = (audioCodec != null && !audioCodec.isEmpty()) ? audioCodec : "aac";
        if ("none".equalsIgnoreCase(aCodec)) {
            cmd.add("-an");
            return;
        }

        cmd.add("-c:a");
        cmd.add(aCodec);

        if ("dca".equalsIgnoreCase(aCodec) || "truehd".equalsIgnoreCase(aCodec)) {
            cmd.add("-strict");
            cmd.add("-2");
        }

        // AMR/iLBC 等编码器使用固定或受限码率，不能套用通用的 b:a 参数
        boolean fixedBitrateCodec = isFixedBitrateAudioCodec(aCodec);
        if (fixedBitrateCodec) {
            return;
        }

        if ("original".equals(bitrateMode)) {
            int origBitrate = getOriginalAudioBitrate(inputPath);
            if (origBitrate > 0) {
                cmd.add("-b:a");
                cmd.add(origBitrate + "k");
            } else if (fallback128k) {
                cmd.add("-b:a");
                cmd.add("128k");
            }
        } else if ("custom".equals(bitrateMode)) {
            cmd.add("-b:a");
            cmd.add(Math.max(1, bitrateValue) + "k");
        } else if (fallback128k) {
            cmd.add("-b:a");
            cmd.add("128k");
        } else if (allowOriginalFallback) {
            cmd.add("-b:a");
            cmd.add("192k");
        }
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
                ? params.audioCodec : getDefaultAudioCodec(params.outputFormat);
        addAudioCodecArgs(cmd, aCodec, inputPath, params.audioBitrateMode,
                params.audioBitrateValue, true, false);

        String muxer = getAudioMuxer(params.outputFormat);
        if (muxer != null) {
            cmd.add("-f");
            cmd.add(muxer);
        }
    }

    // 音频任务参数构建
    private static void buildConvertAudioArgs(ArrayList<String> cmd, ParameterData params, String inputPath) {
        String aCodec = (params.audioCodec != null && !params.audioCodec.isEmpty())
                ? params.audioCodec : getDefaultAudioCodec(params.outputFormat);
        addAudioCodecArgs(cmd, aCodec, inputPath, params.audioBitrateMode,
                params.audioBitrateValue, false, true);

        String muxer = getAudioMuxer(params.outputFormat);
        if (muxer != null) {
            cmd.add("-f");
            cmd.add(muxer);
        }
    }

    private static void buildCutAudioArgs(ArrayList<String> cmd, ParameterData params, String inputPath) {
        cmd.add("-ss");
        cmd.add(params.cutStartTime);
        cmd.add("-t");
        cmd.add(params.cutDuration);

        String aCodec = (params.audioCodec != null && !params.audioCodec.isEmpty())
                ? params.audioCodec : getDefaultAudioCodec(params.outputFormat);
        addAudioCodecArgs(cmd, aCodec, inputPath, params.audioBitrateMode,
                params.audioBitrateValue, true, false);

        String muxer = getAudioMuxer(params.outputFormat);
        if (muxer != null) {
            cmd.add("-f");
            cmd.add(muxer);
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
    private static boolean isFixedBitrateAudioCodec(String codec) {
        if (codec == null) return false;
        String c = codec.toLowerCase();
        return c.contains("opencore_amrnb") || c.contains("vo_amrwbenc") || c.equals("libilbc")
                || c.equals("ilbc");
    }

    private static String getVideoMuxer(String format) {
        if (format == null) return "mp4";
        switch (format.toLowerCase()) {
            case "mp4": return "mp4";
            case "mov": return "mov";
            case "mkv": return "matroska";
            case "webm": return "webm";
            case "avi": return "avi";
            case "flv": return "flv";
            case "3gp": return "3gp";
            case "3g2": return "3g2";
            case "mpg":
            case "mpeg": return "mpeg";
            case "ts":
            case "mts":
            case "m2ts": return "mpegts";
            case "mxf": return "mxf";
            case "ogv": return "ogg";
            case "asf": return "asf";
            case "nut": return "nut";
            case "swf": return "swf";
            case "vob": return "vob";
            case "m4v": return "mp4";
            case "gif": return "gif";
            default: return null;
        }
    }

    private static String getAudioMuxer(String format) {
        if (format == null) return "mp3";
        switch (format.toLowerCase()) {
            case "mp3": return "mp3";
            case "wav": return "wav";
            case "aac": return "adts";
            case "flac": return "flac";
            case "ogg": return "ogg";
            case "oga": return "oga";
            case "m4a": return "ipod";
            case "opus": return "opus";
            case "ac3": return "ac3";
            case "eac3": return "eac3";
            case "dts": return "dts";
            case "truehd": return "truehd";
            case "mp2": return "mp2";
            case "aiff": return "aiff";
            case "amr": return "amr";
            case "caf": return "caf";
            case "wma": return "asf";
            case "tta": return "tta";
            case "wv": return "wv";
            case "spx": return "spx";
            case "lc3": return "lc3";
            case "ilbc": return "ilbc";
            default: return null;
        }
    }

    private static String getVideoFileExtension(String format) {
        if (format == null) return "mp4";
        switch (format.toLowerCase()) {
            case "mp4": return "mp4";
            case "mov": return "mov";
            case "mkv": return "mkv";
            case "webm": return "webm";
            case "avi": return "avi";
            case "flv": return "flv";
            case "3gp": return "3gp";
            case "3g2": return "3g2";
            case "mpg": return "mpg";
            case "mpeg": return "mpeg";
            case "ts": return "ts";
            case "mts": return "mts";
            case "m2ts": return "m2ts";
            case "mxf": return "mxf";
            case "ogv": return "ogv";
            case "asf": return "asf";
            case "nut": return "nut";
            case "swf": return "swf";
            case "vob": return "vob";
            case "m4v": return "m4v";
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
            case "oga": return "oga";
            case "m4a": return "m4a";
            case "opus": return "opus";
            case "ac3": return "ac3";
            case "eac3": return "eac3";
            case "dts": return "dts";
            case "truehd": return "truehd";
            case "mp2": return "mp2";
            case "aiff": return "aiff";
            case "amr": return "amr";
            case "caf": return "caf";
            case "wma": return "wma";
            case "tta": return "tta";
            case "wv": return "wv";
            case "spx": return "spx";
            case "lc3": return "lc3";
            case "ilbc": return "ilbc";
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
            case "ogg":
            case "oga": return "libvorbis";
            case "m4a": return "aac";
            case "opus": return "libopus";
            case "ac3": return "ac3";
            case "eac3": return "eac3";
            case "dts": return "dca";
            case "truehd": return "truehd";
            case "mp2": return "mp2";
            case "aiff": return "pcm_s16be";
            case "amr": return "libopencore_amrnb";
            case "caf": return "pcm_s16le";
            case "wma": return "wmav2";
            case "tta": return "tta";
            case "wv": return "wavpack";
            case "spx": return "libspeex";
            case "lc3": return "liblc3";
            case "ilbc": return "libilbc";
            default: return "libmp3lame";
        }
    }

    private static String getImageFileExtension(String format) {
        if (format == null) return "jpg";
        switch (format.toLowerCase()) {
            case "jpg":
            case "jpeg": return "jpg";
            case "png": return "png";
            case "webp": return "webp";
            case "bmp": return "bmp";
            case "tiff": return "tiff";
            case "heif": return "heif";
            case "heic": return "heic";
            case "avif": return "avif";
            case "jxl": return "jxl";
            case "jp2": return "jp2";
            case "apng": return "apng";
            case "qoi": return "qoi";
            case "tga": return "tga";
            case "dpx": return "dpx";
            case "exr": return "exr";
            case "ico": return "png";
            default: return "jpg";
        }
    }

    private static String getImageCodec(String format) {
        if (format == null) return "mjpeg";
        switch (format.toLowerCase()) {
            case "jpg":
            case "jpeg": return "mjpeg";
            case "png": return "png";
            case "webp": return "libwebp";
            case "bmp": return "bmp";
            case "tiff": return "tiff";
            case "avif": return "libaom-av1";
            case "jxl": return "libjxl";
            case "jp2": return "jpeg2000";
            case "apng": return "apng";
            case "qoi": return "qoi";
            case "tga": return "targa";
            case "dpx": return "dpx";
            case "exr": return "exr";
            case "ico": return "ico";
            // HEIF/HEIC 的实际编码器能力依赖当前 FFmpeg 图像封装，因此保持自动选择
            case "heif":
            case "heic":
                return null;
            default: return null;
        }
    }
}
