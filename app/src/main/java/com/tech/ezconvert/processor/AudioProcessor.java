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
 * 音频处理器
 * 所有音频处理任务统一通过 processAudio() 方法，使用 ParameterData 传递参数
 */
public class AudioProcessor {

    private static final String TAG = "AudioProcessor";

    /**
     * 统一音频处理入口
     * inputPath 输入文件路径
     * outputPathBase 输出路径基础（不含扩展名）
     * params 参数对象
     * context 上下文
     * callback 回调
     */
    public static void processAudio(String inputPath, String outputPathBase,
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
            case "convert_audio":
                executeConvertAudio(usablePath, outputPath, params, context, wrappedCallback, fileName);
                break;
            case "cut_audio":
                executeCutAudio(usablePath, outputPath, params, context, wrappedCallback, fileName);
                break;
            default:
                wrappedCallback.onError("不支持的任务类型: " + params.taskType);
        }
    }

    // 构建输出文件完整路径 (含扩展名)
    private static String buildOutputPath(String basePath, ParameterData params) {
        String format = params.outputFormat != null ? params.outputFormat : "mp3";
        String ext;
        switch (format.toLowerCase()) {
            case "mp3": ext = "mp3"; break;
            case "wav": ext = "wav"; break;
            case "aac": ext = "aac"; break;
            case "flac": ext = "flac"; break;
            case "ogg": ext = "ogg"; break;
            case "m4a": ext = "m4a"; break;
            default: ext = "mp3";
        }
        return basePath + "." + ext;
    }

    // 默认音频编码器 (根据格式)
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

    // 音频转换 (转码)
    private static void executeConvertAudio(String inputPath, String outputPath,
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

        String format = params.outputFormat != null ? params.outputFormat : "mp3";
        if (!format.equals("mp3") && !format.equals("wav")) {
            cmd.add("-f");
            cmd.add(format);
        }

        cmd.add("-y");
        cmd.add(outputPath);

        Log.d(TAG, "音频转换命令: " + String.join(" ", cmd));
        FFmpegUtil.executeCommand(cmd.toArray(new String[0]), callback, inputPath, fileName);
    }

    // 裁剪音频
    private static void executeCutAudio(String inputPath, String outputPath,
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
        cmd.add("-ss");
        cmd.add(params.cutStartTime);
        cmd.add("-t");
        cmd.add(params.cutDuration);

        // 固定使用 libmp3lame 编码，码率 192k
        cmd.add("-c:a");
        cmd.add("libmp3lame");
        cmd.add("-b:a");
        cmd.add("192k");

        cmd.add("-y");
        cmd.add(outputPath);

        Log.d(TAG, "音频裁剪命令: " + String.join(" ", cmd));
        FFmpegUtil.executeCommand(cmd.toArray(new String[0]), callback, inputPath, fileName);
    }
}