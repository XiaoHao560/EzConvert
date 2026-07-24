package com.tech.ezconvert.processor;

import android.content.Context;
import com.tech.ezconvert.utils.CacheManager;
import com.tech.ezconvert.utils.FfmpegCommandBuilder;
import com.tech.ezconvert.utils.FFmpegUtil;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.ParameterData;
import java.io.File;

/**
 * 音频处理器
 * 命令生成逻辑已提取到 FfmpegCommandBuilder，本类保留向后兼容的同步执行接口
 */
@Deprecated
public class AudioProcessor {

    private static final String TAG = "AudioProcessor";

    /**
     * 统一音频处理入口（向后兼容，直接执行）
     * 新代码应使用 FfmpegWorker 通过 WorkManager 执行
     */
    public static void processAudio(String inputPath, String outputPathBase,
                                    ParameterData params, Context context,
                                    FFmpegUtil.FFmpegCallback callback) {
        // 检查并准备文件路径
        CacheManager.AccessResult accessResult = CacheManager.prepareFileForProcessing(context, inputPath, null);
        if (accessResult == null) {
            callback.onError("无法访问输入文件");
            return;
        }

        final String usablePath = accessResult.usablePath;
        final boolean isFromCache = accessResult.isFromCache;
        FFmpegUtil.FFmpegCallback wrappedCallback = wrapCallback(callback, usablePath, isFromCache);

        String fileName = new File(inputPath).getName();
        String outputPath = FfmpegCommandBuilder.buildOutputPath(outputPathBase, params);

        // 使用 FfmpegCommandBuilder 生成命令，然后交给 FFmpegUtil 执行
        String[] command = FfmpegCommandBuilder.buildCommand(usablePath, outputPath, params, context);
        Log.d(TAG, "音频处理命令: " + String.join(" ", command));
        FFmpegUtil.executeCommand(command, wrappedCallback, usablePath, fileName);
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
}
