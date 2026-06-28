package com.tech.ezconvert.processor;

import android.content.Context;
import com.tech.ezconvert.utils.CacheManager;
import com.tech.ezconvert.utils.ConfigManager;
import com.tech.ezconvert.utils.FFmpegUtil;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.ParameterData;
import java.io.File;
import java.util.ArrayList;

/**
 * 视频处理器
 * 所有视频处理任务统一通过 processVideo() 方法，使用 ParameterData 传递参数
 */
public class VideoProcessor {

    private static final String TAG = "VideoProcessor";

    /**
     * 统一视频处理入口
     * inputPath 输入文件路径
     * outputPathBase 输出路径基础（不含扩展名）
     * params 参数对象
     * context 上下文
     * callback 回调
     */
    public static void processVideo(String inputPath, String outputPathBase,
                                    ParameterData params, Context context,
                                    FFmpegUtil.FFmpegCallback callback) {
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
        String outputPath = buildOutputPath(outputPathBase, params);

        // 根据任务类型分发
        switch (params.taskType) {
            case "convert":
                executeConvert(usablePath, outputPath, params, context, wrappedCallback, fileName);
                break;
            case "compress":
                executeCompress(usablePath, outputPath, params, context, wrappedCallback, fileName);
                break;
            case "cut_video":
                executeCutVideo(usablePath, outputPath, params, context, wrappedCallback, fileName);
                break;
            case "screenshot":
                executeScreenshot(usablePath, outputPath, params, context, wrappedCallback, fileName);
                break;
            case "extract_audio":
                executeExtractAudio(usablePath, outputPath, params, context, wrappedCallback, fileName);
                break;
            default:
                wrappedCallback.onError("不支持的任务类型: " + params.taskType);
        }
    }

    // 构建输出文件完整路径 (含扩展名)
    private static String buildOutputPath(String basePath, ParameterData params) {
        String ext;
        switch (params.taskType) {
            case "convert":
                ext = getFileExtension(params.outputFormat);
                break;
            case "compress":
                ext = "mp4";
                break;
            case "cut_video":
                ext = "mp4";
                break;
            case "screenshot":
                ext = params.screenshotFormat;
                break;
            case "extract_audio":
                ext = "mp3"; // 提取音频固定输出 MP3
                break;
            default:
                ext = "mp4";
        }
        return basePath + "." + ext;
    }

    // 获取视频文件扩展名 (根据格式)
    private static String getFileExtension(String format) {
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

    // 默认音频编码器 (用于提取音频)
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

    // 转换视频
    private static void executeConvert(String inputPath, String outputPath,
                                       ParameterData params, Context context,
                                       FFmpegUtil.FFmpegCallback callback, String fileName) {
        boolean hw = ConfigManager.getInstance(context).isHardwareAccelerationEnabled();
        boolean mt = ConfigManager.getInstance(context).isMultithreadingEnabled();

        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("-i");
        cmd.add(inputPath);
        if (mt) { cmd.add("-threads"); cmd.add("0"); }
        if (params.volume != 100) {
            cmd.add("-af");
            cmd.add("volume=" + (params.volume / 100.0));
        }

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
            // 原质量：使用 CRF 18
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

        // 容器格式 (非 MP4 需显式指定)
        String format = params.outputFormat;
        if (format != null && !"mp4".equals(format) && !"mov".equals(format)) {
            cmd.add("-f");
            cmd.add(format);
        }

        cmd.add("-y");
        cmd.add(outputPath);

        Log.d(TAG, "转换命令: " + String.join(" ", cmd));
        FFmpegUtil.executeCommand(cmd.toArray(new String[0]), callback, inputPath, fileName);
    }

    // 压缩视频
    private static void executeCompress(String inputPath, String outputPath,
                                        ParameterData params, Context context,
                                        FFmpegUtil.FFmpegCallback callback, String fileName) {
        boolean hw = ConfigManager.getInstance(context).isHardwareAccelerationEnabled();
        boolean mt = ConfigManager.getInstance(context).isMultithreadingEnabled();

        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("-i");
        cmd.add(inputPath);
        if (mt) { cmd.add("-threads"); cmd.add("0"); }
        if (params.volume != 100) {
            cmd.add("-af");
            cmd.add("volume=" + (params.volume / 100.0));
        }

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
            // 原质量：CRF 23（中等压缩）
            cmd.add("-crf");
            cmd.add("23");
        }

        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add("128k");
        cmd.add("-movflags");
        cmd.add("+faststart");
        cmd.add("-y");
        cmd.add(outputPath);

        Log.d(TAG, "压缩命令: " + String.join(" ", cmd));
        FFmpegUtil.executeCommand(cmd.toArray(new String[0]), callback, inputPath, fileName);
    }

    // 裁剪视频
    private static void executeCutVideo(String inputPath, String outputPath,
                                        ParameterData params, Context context,
                                        FFmpegUtil.FFmpegCallback callback, String fileName) {
        boolean hw = ConfigManager.getInstance(context).isHardwareAccelerationEnabled();
        boolean mt = ConfigManager.getInstance(context).isMultithreadingEnabled();

        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("-i");
        cmd.add(inputPath);
        if (mt) { cmd.add("-threads"); cmd.add("0"); }
        if (params.volume != 100) {
            cmd.add("-af");
            cmd.add("volume=" + (params.volume / 100.0));
        }
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
        cmd.add("-y");
        cmd.add(outputPath);

        Log.d(TAG, "裁剪命令: " + String.join(" ", cmd));
        FFmpegUtil.executeCommand(cmd.toArray(new String[0]), callback, inputPath, fileName);
    }

    // 视频截图
    private static void executeScreenshot(String inputPath, String outputPath,
                                          ParameterData params, Context context,
                                          FFmpegUtil.FFmpegCallback callback, String fileName) {
        boolean mt = ConfigManager.getInstance(context).isMultithreadingEnabled();

        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("-i");
        cmd.add(inputPath);
        if (mt) { cmd.add("-threads"); cmd.add("0"); }
        cmd.add("-ss");
        cmd.add(params.cutStartTime);
        cmd.add("-vframes");
        cmd.add("1");

        if ("jpeg".equals(params.screenshotFormat)) {
            cmd.add("-q:v");
            cmd.add(String.valueOf(params.screenshotQuality));
        }

        String resolution = params.screenshotResolution;
        if (resolution != null && !resolution.equals("original") && !resolution.isEmpty()) {
            cmd.add("-vf");
            cmd.add("scale=" + resolution.replace("x", ":"));
        }

        cmd.add("-y");
        cmd.add(outputPath);

        Log.d(TAG, "截图命令: " + String.join(" ", cmd));
        FFmpegUtil.executeCommand(cmd.toArray(new String[0]), callback, inputPath, fileName);
    }

    // 从视频提取音频
    private static void executeExtractAudio(String inputPath, String outputPath,
                                            ParameterData params, Context context,
                                            FFmpegUtil.FFmpegCallback callback, String fileName) {
        boolean mt = ConfigManager.getInstance(context).isMultithreadingEnabled();

        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("-i");
        cmd.add(inputPath);
        if (mt) { cmd.add("-threads"); cmd.add("0"); }
        if (params.volume != 100) {
            cmd.add("-af");
            cmd.add("volume=" + (params.volume / 100.0));
        }
        cmd.add("-vn");

        // 音频编码器
        String aCodec = (params.audioCodec != null && !params.audioCodec.isEmpty())
                ? params.audioCodec
                : getDefaultAudioCodec(params.outputFormat);
        cmd.add("-c:a");
        cmd.add(aCodec);

        if ("custom".equals(params.audioBitrateMode)) {
            cmd.add("-b:a");
            cmd.add(params.audioBitrateValue + "k");
        }

        // 输出格式默认为 mp3，如果指定了其他格式则使用
        String format = params.outputFormat != null ? params.outputFormat : "mp3";
        if (!format.equals("mp3") && !format.equals("wav")) {
            cmd.add("-f");
            cmd.add(format);
        }

        cmd.add("-y");
        cmd.add(outputPath);

        Log.d(TAG, "提取音频命令: " + String.join(" ", cmd));
        FFmpegUtil.executeCommand(cmd.toArray(new String[0]), callback, inputPath, fileName);
    }
}