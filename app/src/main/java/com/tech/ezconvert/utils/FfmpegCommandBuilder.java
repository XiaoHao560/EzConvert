package com.tech.ezconvert.utils;

import android.content.Context;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.FFprobeSession;
import com.arthenica.ffmpegkit.ReturnCode;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.R;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * FFmpeg 命令生成器
 * 从 VideoProcessor / AudioProcessor 提取命令生成逻辑，供 Worker 和 Processor 共用
 */
public class FfmpegCommandBuilder {
    private static final String TAG = "FfmpegCommandBuilder";
    private static final ThreadLocal<ArrayList<String>> COMPATIBILITY_CHANGES = new ThreadLocal<>();
    private static final ThreadLocal<Context> COMPATIBILITY_CONTEXT = new ThreadLocal<>();

    /**
     * 收集命令构建过程中实际触发的兼容性调整，供开始任务前向用户说明
     */
    public static ArrayList<String> collectCompatibilityChanges(String inputPath, ParameterData params,
                                                                 Context context) {
        ArrayList<String> changes = new ArrayList<>();
        COMPATIBILITY_CHANGES.set(changes);
        try {
            buildCommand(inputPath, buildOutputPath("/compatibility_preview", params), params, context);
        } catch (Exception e) {
            Log.w(TAG, context.getString(R.string.compatibility_precheck_failed, e.getMessage()));
        } finally {
            COMPATIBILITY_CHANGES.remove();
            COMPATIBILITY_CONTEXT.remove();
        }
        return changes;
    }

    private static String compatibilityText(int resourceId, Object... args) {
        Context context = COMPATIBILITY_CONTEXT.get();
        if (context == null) return "";
        return context.getString(resourceId, args);
    }

    private static void logCompatibilityChange(String message) {
        Log.w(TAG, message);
        ArrayList<String> changes = COMPATIBILITY_CHANGES.get();
        if (changes != null && !changes.contains(message)) {
            changes.add(message);
        }
    }

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
        COMPATIBILITY_CONTEXT.set(context.getApplicationContext());
        try {
            String[] generated;
            if ("convert_image".equals(params.taskType)) {
                generated = buildImageCommand(inputPath, outputPath, params);
            } else if (isVideoTask(params.taskType)) {
                generated = buildVideoCommand(inputPath, outputPath, params, context);
            } else {
                generated = buildAudioCommand(inputPath, outputPath, params, context);
            }
            return applyAdvancedOptions(generated, params);
        } finally {
            // 正常转换结束后清除线程上下文，避免线程复用时沿用旧语言环境
            if (COMPATIBILITY_CHANGES.get() == null) COMPATIBILITY_CONTEXT.remove();
        }
    }

    /**
     * 将高级参数应用到命令中。结构化选项位于应用默认选项之后，通用自定义输出参数优先级最高
     */
    private static String[] applyAdvancedOptions(String[] baseCommand, ParameterData params) {
        ArrayList<String> cmd = new ArrayList<>(Arrays.asList(baseCommand));
        List<String> inputOptions = tokenizeArguments(params.customInputOptions);
        List<String> rawOutputOptions = tokenizeArguments(params.customOutputOptions);
        rejectManagedOptions(inputOptions);
        rejectManagedOptions(rawOutputOptions);

        int inputIndex = cmd.indexOf("-i");
        if (inputIndex >= 0 && !inputOptions.isEmpty()) {
            cmd.addAll(inputIndex, inputOptions);
        }

        boolean quick = params.quickConversionEnabled
                && ("convert".equals(params.taskType) || "convert_audio".equals(params.taskType)
                || "extract_audio".equals(params.taskType));
        if (!quick) {
            boolean videoTask = "convert".equals(params.taskType) || "compress".equals(params.taskType)
                    || "cut_video".equals(params.taskType);
            boolean audioTask = "convert_audio".equals(params.taskType) || "cut_audio".equals(params.taskType)
                    || "extract_audio".equals(params.taskType);
            boolean screenshotTask = "screenshot".equals(params.taskType);
            boolean imageTask = "convert_image".equals(params.taskType);
            ArrayList<String> structuredOptions = new ArrayList<>();

            if (videoTask || audioTask) {
                if (!"none".equalsIgnoreCase(nullToEmpty(params.audioCodec))) {
                    addOptionalOption(structuredOptions, "-ar", params.audioSampleRate);
                    addOptionalOption(structuredOptions, "-ac", params.audioChannels);
                    addOptionalOption(structuredOptions, "-ch_layout", params.audioChannelLayout);
                    addOptionalOption(structuredOptions, "-sample_fmt", params.audioSampleFormat);
                    if (!isBlank(params.audioFilter)) {
                        mergeFilterOption(cmd, "-af", params.audioFilter.trim());
                    }
                }
            }

            if (videoTask) {
                ArrayList<String> filters = new ArrayList<>();
                if (!isBlank(params.videoResolution)) {
                    String[] dimensions = params.videoResolution.trim().toLowerCase().split("x", -1);
                    if (dimensions.length == 2) {
                        filters.add("scale=" + dimensions[0].trim() + ":" + dimensions[1].trim());
                    }
                }
                if (!isBlank(params.videoFilter)) filters.add(params.videoFilter.trim());
                if (!filters.isEmpty()) mergeFilterOption(cmd, "-vf", joinWithSeparator(filters, ","));
                addOptionalOption(structuredOptions, "-r", params.videoFrameRate);
                addOptionalOption(structuredOptions, "-pix_fmt", params.videoPixelFormat);
                addOptionalOption(structuredOptions, "-crf", params.videoCrf);
                addOptionalOption(structuredOptions, "-preset", params.videoPreset);
                addOptionalOption(structuredOptions, "-tune", params.videoTune);
                addOptionalOption(structuredOptions, "-profile:v", params.videoProfile);
                addOptionalOption(structuredOptions, "-level:v", params.videoLevel);
                addOptionalOption(structuredOptions, "-g", params.videoGop);
                addOptionalOption(structuredOptions, "-bf", params.videoBFrames);
            } else if (screenshotTask || imageTask) {
                // 图片任务只使用图片专用像素格式，避免旧预设中隐藏的视频参数意外影响图片输出
                addOptionalOption(structuredOptions, "-pix_fmt", params.imagePixelFormat);
            }

            if (!structuredOptions.isEmpty()) {
                insertOutputOptions(cmd, structuredOptions);
            }
        }

        if (!rawOutputOptions.isEmpty()) {
            insertOutputOptions(cmd, rawOutputOptions);
        }
        return cmd.toArray(new String[0]);
    }

    /**
     * 支持简单的单引号/双引号分组，不执行 shell 展开或命令替换
     */
    public static ArrayList<String> tokenizeArguments(String raw) {
        ArrayList<String> tokens = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) return tokens;
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean escaping = false;
        boolean tokenStarted = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (escaping) {
                // 只把转义引号和反斜杠去掉转义符，其他反斜杠原样保留，兼容滤镜表达式中的转义字符
                if (c != quote && c != '\\') token.append('\\');
                token.append(c);
                escaping = false;
                tokenStarted = true;
            } else if (c == '\\' && quote != 0) {
                escaping = true;
            } else if ((c == '\'' || c == '"') && (quote == 0 || quote == c)) {
                quote = quote == 0 ? c : 0;
                tokenStarted = true;
            } else if (Character.isWhitespace(c) && quote == 0) {
                if (tokenStarted) {
                    tokens.add(token.toString());
                    token.setLength(0);
                    tokenStarted = false;
                }
            } else {
                token.append(c);
                tokenStarted = true;
            }
        }
        if (escaping) token.append('\\');
        if (quote != 0) throw new IllegalArgumentException("高级参数中存在未闭合的引号");
        if (tokenStarted) tokens.add(token.toString());
        return tokens;
    }

    public static void validateCustomOptions(String inputOptions, String outputOptions) {
        rejectManagedOptions(tokenizeArguments(inputOptions));
        rejectManagedOptions(tokenizeArguments(outputOptions));
    }

    private static void rejectManagedOptions(List<String> options) {
        for (String option : options) {
            String normalized = option.toLowerCase(java.util.Locale.ROOT);
            if ("-i".equals(normalized) || "-y".equals(normalized) || "-n".equals(normalized)
                    || normalized.startsWith("-i=") || normalized.startsWith("-y=") || normalized.startsWith("-n=")) {
                throw new IllegalArgumentException("高级参数不能包含由应用管理的选项：" + option);
            }
        }
    }

    private static void addOptionalOption(List<String> target, String option, String value) {
        if (isBlank(value)) return;
        target.add(option);
        target.add(value.trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String joinWithSeparator(List<String> values, String separator) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(separator);
            result.append(value);
        }
        return result.toString();
    }

    private static void mergeFilterOption(ArrayList<String> cmd, String option, String filter) {
        if (isBlank(filter)) return;
        int found = -1;
        for (int i = 0; i < cmd.size() - 1; i++) {
            if (option.equals(cmd.get(i))) found = i;
        }
        if (found >= 0) {
            String previous = cmd.get(found + 1);
            cmd.set(found + 1, isBlank(previous) ? filter : previous + "," + filter);
        } else {
            insertOutputOptions(cmd, Arrays.asList(option, filter));
        }
    }

    private static void insertOutputOptions(ArrayList<String> cmd, List<String> options) {
        if (options == null || options.isEmpty()) return;
        int index = cmd.lastIndexOf("-y");
        if (index < 0) index = Math.max(0, cmd.size() - 1);
        cmd.addAll(index, options);
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

        if (params.quickConversionEnabled && "convert".equals(params.taskType)) {
            // 快速转换只复制音视频流，避免滤镜和编码参数与 stream copy 冲突
            cmd.add("-map");
            cmd.add("0:v?");
            cmd.add("-map");
            cmd.add("0:a?");
            cmd.add("-c");
            cmd.add("copy");

            String muxer = getVideoMuxer(params.outputFormat);
            if (muxer != null) {
                cmd.add("-f");
                cmd.add(muxer);
            }

            cmd.add("-y");
            cmd.add(outputPath);
            return cmd.toArray(new String[0]);
        }

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

        if (params.quickConversionEnabled
                && ("convert_audio".equals(params.taskType) || "extract_audio".equals(params.taskType))) {
            // 音频快速转换仅复制音频流，不应用音量、码率或编码器设置
            cmd.add("-map");
            cmd.add("0:a?");
            cmd.add("-c:a");
            cmd.add("copy");

            String muxer = getAudioMuxer(params.outputFormat);
            if (muxer != null) {
                cmd.add("-f");
                cmd.add(muxer);
            }

            cmd.add("-y");
            cmd.add(outputPath);
            return cmd.toArray(new String[0]);
        }

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
                buildConvertAudioArgs(cmd, params, inputPath);
                break;
            case "cut_audio":
                buildCutAudioArgs(cmd, params, inputPath);
                break;
            case "extract_audio":
                buildExtractAudioArgs(cmd, params, inputPath);
                break;
        }

        // 所有纯音频任务统一在这里补充编码器/容器兼容参数
        // 避免某一个任务路径遗漏 -ar/-ac 等限制参数
        String effectiveAudioCodec = (params.audioCodec != null && !params.audioCodec.isEmpty())
                ? params.audioCodec : getDefaultAudioCodec(params.outputFormat);
        addAudioContainerCompatibilityArgs(cmd, params.outputFormat, effectiveAudioCodec, inputPath, params);

        cmd.add("-y");
        cmd.add(outputPath);

        return cmd.toArray(new String[0]);
    }
    
    private static String[] buildImageCommand(String inputPath, String outputPath, ParameterData params) {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.add("-i");
        cmd.add(inputPath);

        String format = params.outputFormat != null ? params.outputFormat.toLowerCase() : "jpg";
        // 兼容旧版本可能残留的 HEIF/HEIC 参数：当前 FFmpeg 构建没有对应 muxer，统一回退为 JPEG
        // 同时由 buildOutputPath() 输出 .jpg，避免生成“扩展名是 HEIC、实际内容却无法封装”的坏文件
        if ("heif".equals(format) || "heic".equals(format)) {
            logCompatibilityChange(compatibilityText(R.string.compatibility_change_heif_fallback, format));
            format = "jpg";
        }
        // 图片任务只处理视频流，避免输入文件中存在其他流时被错误带入输出
        cmd.add("-map");
        cmd.add("0:v:0");

        String imageCodec = getImageCodec(format);
        if (imageCodec != null) {
            cmd.add("-c:v");
            cmd.add(imageCodec);
        }

        if ("avif".equals(format)) {
            // AVIF 是静态图像容器，明确要求 libaom 采用 still-picture 模式
            cmd.add("-still-picture");
            cmd.add("1");
        }

        // 图片缩放滤镜统一在这里组合
        // 用户选择“自定义分辨率”时，完全尊重用户输入，不额外替用户修改尺寸
        // 如果输入尺寸不符合目标格式（例如 ICO 超过 256x256），直接交给 FFmpeg 报错
        ArrayList<String> imageFilters = new ArrayList<>();
        if ("custom".equals(params.imageResolutionMode)
                && params.imageResolution != null
                && !params.imageResolution.isEmpty()
                && !"original".equalsIgnoreCase(params.imageResolution)) {
            imageFilters.add("scale=" + params.imageResolution + ":flags=lanczos");
        }
        if (!imageFilters.isEmpty()) {
            cmd.add("-vf");
            cmd.add(joinWithSeparator(imageFilters, ","));
        }

        if ("ico".equals(format)) {
            cmd.add("-pix_fmt");
            cmd.add("rgba");
            cmd.add("-frames:v");
            cmd.add("1");
            cmd.add("-f");
            cmd.add("ico");
        } else if (!"apng".equals(format)) {
            // 除 APNG 外，其余图片输出均按单帧图片处理
            // 这样输入为动画 WebP/GIF/APNG 时，不会因为多帧写入同一个静态输出路径而失败
            cmd.add("-frames:v");
            cmd.add("1");
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

        String audioCodec = (params.audioCodec != null && !params.audioCodec.isEmpty())
                ? params.audioCodec : "aac";
        addAudioCodecArgs(cmd, audioCodec, inputPath, params.audioBitrateMode,
                params.audioBitrateValue, false, false, params.skipAutomaticAudioBitrate);
        addAudioContainerCompatibilityArgs(cmd, params.outputFormat, audioCodec, inputPath, params);

        if ("swf".equalsIgnoreCase(params.outputFormat)) {
            addSwfSampleRateCompatibilityArgs(cmd, params, inputPath);
        }

        String muxer = getVideoMuxer(params.outputFormat);
        if (muxer != null) {
            cmd.add("-f");
            cmd.add(muxer);
        }
    }

    private static void addSwfSampleRateCompatibilityArgs(ArrayList<String> cmd, ParameterData params,
                                                           String inputPath) {
        int requestedRate = parsePositiveInt(params.audioSampleRate);
        if (requestedRate > 0) {
            if (requestedRate != 44100) {
                logCompatibilityChange(compatibilityText(R.string.compatibility_change_custom_swf_sample_rate_conflict,
                        requestedRate));
            }
            return;
        }
        if (params.skipAutomaticAudioSampleRate) {
            int sourceRate = getOriginalAudioSampleRate(inputPath);
            if (sourceRate <= 0 || sourceRate != 44100) {
                String sourceRateText = sourceRate > 0 ? sourceRate + " Hz"
                        : compatibilityText(R.string.compatibility_value_unknown);
                logCompatibilityChange(compatibilityText(R.string.compatibility_change_auto_audio_sample_rate_skipped,
                        "SWF", sourceRateText, 44100));
            }
            return;
        }
        logCompatibilityChange(compatibilityText(R.string.compatibility_change_swf_sample_rate));
        cmd.add("-ar");
        cmd.add("44100");
    }

    private static void buildCompressArgs(ArrayList<String> cmd, ParameterData params, String inputPath, boolean hw) {
        String vCodec = getVideoCodecOrDefault(params, hw);
        cmd.add("-c:v");
        cmd.add(vCodec);
        addVideoQualityArgs(cmd, vCodec, params, inputPath, 23);

        String audioCodec = (params.audioCodec != null && !params.audioCodec.isEmpty())
                ? params.audioCodec : "aac";
        addAudioCodecArgs(cmd, audioCodec, inputPath, params.audioBitrateMode,
                params.audioBitrateValue, true, true, params.skipAutomaticAudioBitrate);
        addAudioContainerCompatibilityArgs(cmd, params.outputFormat, audioCodec, inputPath, params);

        if ("swf".equalsIgnoreCase(params.outputFormat)) {
            addSwfSampleRateCompatibilityArgs(cmd, params, inputPath);
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

        String audioCodec = (params.audioCodec != null && !params.audioCodec.isEmpty())
                ? params.audioCodec : "aac";
        addAudioCodecArgs(cmd, audioCodec, inputPath, params.audioBitrateMode,
                params.audioBitrateValue, true, false, params.skipAutomaticAudioBitrate);
        addAudioContainerCompatibilityArgs(cmd, params.outputFormat, audioCodec, inputPath, params);

        if ("swf".equalsIgnoreCase(params.outputFormat)) {
            addSwfSampleRateCompatibilityArgs(cmd, params, inputPath);
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

        // 显式设置 CRF 时不再叠加应用自动选择的源文件码率或默认质量值
        if (!isBlank(params.videoCrf)) return;

        if ("original".equals(params.videoBitrateMode) && params.skipAutomaticVideoQuality) {
            // 用户选择使用编码器默认值时，不继承源码率，也不强制指定 CRF/Preset
            return;
        }

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
            if (supportsPreset(c) && isBlank(params.videoPreset)) {
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
                                          boolean fallback128k, boolean allowOriginalFallback,
                                          boolean skipAutomaticAudioBitrate) {
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

        String aCodecLower = aCodec.toLowerCase();

        // 以下编码器使用固定/受限码率，不能套用“原始码率”
        if (isFixedBitrateAudioCodec(aCodecLower)) {
            return;
        }

        // 无损/PCM 类编码器没有“目标音频码率”这一概念
        // 不再把源文件的 FLAC/WAV 等高码率伪装成目标码率传进去
        if (isLosslessOrPcmAudioCodec(aCodecLower)) {
            return;
        }

        if (skipAutomaticAudioBitrate && "original".equals(bitrateMode)) {
            // 用户选择不指定自动音频码率时，交由编码器采用自身默认值，不再添加应用建议码率
            return;
        }

        // Speex 在未指定码率时使用保守值；用户手动选择码率时则尊重显式设置
        if (aCodecLower.contains("speex")) {
            cmd.add("-b:a");
            if ("custom".equals(bitrateMode)) {
                cmd.add(Math.max(1, bitrateValue) + "k");
            } else {
                logCompatibilityChange(compatibilityText(R.string.compatibility_change_speex_bitrate));
                cmd.add("32k");
            }
            return;
        }

        // LC3 的码率由帧时长、采样率和通道数共同决定
        // 未选择跳过自动码率时，避免把 FLAC 等源文件的 Mbps 级码率直接当作 LC3 目标码率
        if (aCodecLower.equals("liblc3") || aCodecLower.equals("lc3")) {
            if ("custom".equals(bitrateMode)) {
                cmd.add("-b:a");
                cmd.add(Math.max(1, bitrateValue) + "k");
            } else {
                logCompatibilityChange(compatibilityText(R.string.compatibility_change_lc3_bitrate));
                cmd.add("-b:a");
                cmd.add("64k");
            }
            return;
        }

        int requestedKbps = -1;
        boolean hasRequestedBitrate = false;

        if ("original".equals(bitrateMode)) {
            int origBitrate = getOriginalAudioBitrate(inputPath);
            if (origBitrate > 0) {
                requestedKbps = origBitrate;
                hasRequestedBitrate = true;
            }
        } else if ("custom".equals(bitrateMode)) {
            requestedKbps = Math.max(1, bitrateValue);
            hasRequestedBitrate = true;
        } else if (fallback128k) {
            requestedKbps = 128;
            hasRequestedBitrate = true;
        } else if (allowOriginalFallback) {
            requestedKbps = 192;
            hasRequestedBitrate = true;
        }

        if (!hasRequestedBitrate) {
            return;
        }

        int safeKbps = sanitizeAudioBitrateKbps(aCodecLower, requestedKbps);
        if (safeKbps != requestedKbps) {
            logCompatibilityChange(compatibilityText(R.string.compatibility_change_audio_bitrate,
                    aCodecLower, requestedKbps, safeKbps));
        }

        cmd.add("-b:a");
        cmd.add(safeKbps + "k");
    }

    /**
     * 将用户选择/源文件继承的音频码率限制在目标编码器的安全范围内
     * 所有输入单位均为 kbps
     */
    private static int sanitizeAudioBitrateKbps(String codec, int requestedKbps) {
        int value = Math.max(1, requestedKbps);
        if (codec == null) return value;

        String c = codec.toLowerCase();

        if (c.equals("libopus")) {
            // libopus: 500..512000 bit/s
            return clamp(value, 1, 512);
        }

        if (c.equals("libmp3lame")) {
            return clamp(value, 8, 320);
        }

        if (c.equals("libshine")) {
            return clamp(value, 32, 320);
        }

        if (c.equals("libvorbis")) {
            // libvorbis 在极低/极高码率下兼容性较差，使用稳定范围
            return clamp(value, 32, 500);
        }

        if (c.equals("ac3")) {
            return clamp(value, 32, 640);
        }

        if (c.equals("eac3")) {
            return clamp(value, 32, 6144);
        }

        if (c.equals("mp2") || c.equals("libtwolame")) {
            return clamp(value, 32, 384);
        }

        if (c.equals("dca")) {
            // FFmpeg DCA encoder 的常用 Core DTS 范围
            return clamp(value, 320, 1536);
        }

        if (c.equals("wmav2") || c.equals("wma")) {
            // WMA v2 在移动端构建中使用保守上限，避免源文件高码率直接导致编码器初始化失败。
            return clamp(value, 8, 768);
        }

        if (c.equals("aac")) {
            return clamp(value, 8, 6144);
        }

        return value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isLosslessOrPcmAudioCodec(String codec) {
        if (codec == null) return false;
        String c = codec.toLowerCase();
        return c.startsWith("pcm_")
                || c.equals("flac")
                || c.equals("alac")
                || c.equals("tta")
                || c.equals("wavpack")
                || c.equals("truehd");
    }

    /**
     * 针对编码器/容器对音频采样率、声道数的限制补充参数
     *
     * 注意：这个方法会被所有视频+音频以及纯音频任务统一调用
     */
    private static void addAudioContainerCompatibilityArgs(ArrayList<String> cmd,
                                                            String outputFormat,
                                                            String audioCodec,
                                                            String inputPath,
                                                            ParameterData params) {
        if (audioCodec == null) return;
        String format = outputFormat == null ? "" : outputFormat.toLowerCase();
        String codec = audioCodec.toLowerCase();

        // FLV 的 Speex 使用 16 kHz 单声道；这是容器层面的额外限制
        if ("flv".equals(format) && codec.contains("speex")) {
            addFixedAudioFormat(cmd, inputPath, codec, 16000, 1, R.string.compatibility_reason_flv_speex, params);
            return;
        }

        // Speex 编码器只使用有限采样率，若输入不是 8/16/32 kHz
        // 选择距离最近的受支持采样率
        if (codec.contains("speex")) {
            int sampleRate = getOriginalAudioSampleRate(inputPath);
            int targetRate = nearestSupportedSampleRate(sampleRate, 8000, 16000, 32000);
            if (targetRate <= 0) targetRate = 16000;
            addAudioSampleRate(cmd, sampleRate, targetRate, codec, params);
            return;
        }

        // libopus 只接受 8/12/16/24/48 kHz
        if (codec.equals("libopus")) {
            int sampleRate = getOriginalAudioSampleRate(inputPath);
            int targetRate = nearestSupportedSampleRate(sampleRate, 8000, 12000, 16000, 24000, 48000);
            if (targetRate <= 0) targetRate = 48000;
            addAudioSampleRate(cmd, sampleRate, targetRate, codec, params);
            return;
        }

        // MP3 / libshine 的采样率集合有限
        if (codec.equals("libmp3lame")) {
            int sampleRate = getOriginalAudioSampleRate(inputPath);
            int targetRate = nearestSupportedSampleRate(sampleRate,
                    8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000);
            if (targetRate <= 0) targetRate = 44100;
            addAudioSampleRate(cmd, sampleRate, targetRate, codec, params);
            return;
        }

        if (codec.equals("libshine")) {
            int sampleRate = getOriginalAudioSampleRate(inputPath);
            int targetRate = nearestSupportedSampleRate(sampleRate, 32000, 44100, 48000);
            if (targetRate <= 0) targetRate = 44100;
            addAudioSampleRate(cmd, sampleRate, targetRate, codec, params);
            return;
        }

        // libvorbis 在移动端 FFmpeg 构建下对极高采样率的源音频兼容性不稳定
        // 将超过 48 kHz 的输入安全降到 48 kHz
        if (codec.equals("libvorbis")) {
            int sampleRate = getOriginalAudioSampleRate(inputPath);
            int targetRate = sampleRate > 48000 ? 48000 : sampleRate;
            if (targetRate <= 0) targetRate = 48000;
            addAudioSampleRate(cmd, sampleRate, targetRate, codec, params);
            return;
        }

        // AC-3 / E-AC-3 只使用 32/44.1/48 kHz
        if (codec.equals("ac3") || codec.equals("eac3")) {
            int sampleRate = getOriginalAudioSampleRate(inputPath);
            int targetRate = nearestSupportedSampleRate(sampleRate, 32000, 44100, 48000);
            if (targetRate <= 0) targetRate = 48000;
            addAudioSampleRate(cmd, sampleRate, targetRate, codec, params);
            return;
        }

        // MP2 / libtwolame 只支持有限的 MPEG 音频采样率
        if (codec.equals("mp2") || codec.equals("libtwolame")) {
            int sampleRate = getOriginalAudioSampleRate(inputPath);
            int targetRate = nearestSupportedSampleRate(sampleRate,
                    16000, 22050, 24000, 32000, 44100, 48000);
            if (targetRate <= 0) targetRate = 44100;
            addAudioSampleRate(cmd, sampleRate, targetRate, codec, params);
            return;
        }

        // DTS Core 编码器只接受固定采样率集合
        if (codec.equals("dca")) {
            int sampleRate = getOriginalAudioSampleRate(inputPath);
            int targetRate = nearestSupportedSampleRate(sampleRate,
                    8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000);
            if (targetRate <= 0) targetRate = 48000;
            addAudioSampleRate(cmd, sampleRate, targetRate, codec, params);
            return;
        }

        // AMR-NB/AMR-WB 编码器分别固定在 8/16 kHz，并且为单声道
        if (codec.contains("opencore_amrnb")) {
            addFixedAudioFormat(cmd, inputPath, codec, 8000, 1, R.string.compatibility_reason_amr_nb, params);
            return;
        }
        if (codec.contains("vo_amrwbenc")) {
            addFixedAudioFormat(cmd, inputPath, codec, 16000, 1, R.string.compatibility_reason_amr_wb, params);
            return;
        }

        // iLBC 严格要求 8 kHz 单声道
        if (codec.equals("libilbc") || codec.equals("ilbc")) {
            addFixedAudioFormat(cmd, inputPath, codec, 8000, 1, R.string.compatibility_reason_ilbc, params);
            return;
        }

        // WMA v2 最高只支持 48 kHz；移动端构建中使用 48 kHz 作为高采样率上限
        if (codec.equals("wmav2") || codec.equals("wma")) {
            int sampleRate = getOriginalAudioSampleRate(inputPath);
            int targetRate = sampleRate > 48000 ? 48000 : sampleRate;
            if (targetRate <= 0) targetRate = 48000;
            addAudioSampleRate(cmd, sampleRate, targetRate, codec, params);
            return;
        }

        // liblc3 只接受离散采样率
        if (codec.equals("liblc3") || codec.equals("lc3")) {
            int sampleRate = getOriginalAudioSampleRate(inputPath);
            int targetRate = nearestSupportedSampleRate(sampleRate,
                    8000, 16000, 24000, 32000, 48000, 96000);
            if (targetRate > 0) {
                addAudioSampleRate(cmd, sampleRate, targetRate, codec, params);
            } else {
                addAudioSampleRate(cmd, sampleRate, 48000, codec, params);
            }
        }
    }

    /**
     * 对必须固定采样率/声道数的编码器统一应用兼容参数，并在实际发生变化时输出 WARN
     */
    private static void addFixedAudioFormat(ArrayList<String> cmd, String inputPath,
                                            String codec, int targetRate, int targetChannels,
                                            int reasonResourceId, ParameterData params) {
        int sourceRate = getOriginalAudioSampleRate(inputPath);
        int sourceChannels = getOriginalAudioChannels(inputPath);
        int requestedRate = parsePositiveInt(params.audioSampleRate);
        int requestedChannels = parsePositiveInt(params.audioChannels);
        boolean rateChanged = sourceRate > 0 && sourceRate != targetRate;
        boolean channelsChanged = sourceChannels > 0 && sourceChannels != targetChannels;
        boolean hasRequestedLayout = !isBlank(params.audioChannelLayout);
        int requestedLayoutChannels = getKnownChannelLayoutChannels(params.audioChannelLayout);
        boolean needsAutomaticRate = requestedRate <= 0 && (rateChanged || sourceRate <= 0);
        boolean needsAutomaticChannels = requestedChannels <= 0 && !hasRequestedLayout
                && (channelsChanged || sourceChannels <= 0);
        boolean automaticRateAdjustment = needsAutomaticRate && !params.skipAutomaticAudioSampleRate;
        boolean automaticChannelsAdjustment = needsAutomaticChannels && !params.skipAutomaticAudioChannels;
        String sourceRateText = sourceRate > 0 ? sourceRate + " Hz" : compatibilityText(R.string.compatibility_value_unknown);
        String sourceChannelsText = sourceChannels > 0 ? String.valueOf(sourceChannels) : compatibilityText(R.string.compatibility_value_unknown);

        if (automaticRateAdjustment || automaticChannelsAdjustment) {
            if (automaticRateAdjustment && automaticChannelsAdjustment
                    && requestedRate <= 0 && requestedChannels <= 0 && !hasRequestedLayout) {
                logCompatibilityChange(compatibilityText(R.string.compatibility_change_fixed_audio_format,
                        compatibilityText(reasonResourceId), codec, sourceRateText, targetRate,
                        sourceChannelsText, targetChannels));
            } else {
                // 有任一维度由用户指定或主动跳过时，分别说明仍会发生的自动调整
                if (automaticRateAdjustment) {
                    logCompatibilityChange(compatibilityText(R.string.compatibility_change_auto_audio_sample_rate,
                            codec, sourceRateText, targetRate));
                }
                if (automaticChannelsAdjustment) {
                    logCompatibilityChange(compatibilityText(R.string.compatibility_change_auto_audio_channels,
                            codec, sourceChannelsText, targetChannels));
                }
            }
        }
        if (requestedRate > 0 && requestedRate != targetRate) {
            logCompatibilityChange(compatibilityText(R.string.compatibility_change_custom_audio_rate_conflict,
                    codec, requestedRate, targetRate));
        }
        if (requestedChannels > 0 && requestedChannels != targetChannels) {
            logCompatibilityChange(compatibilityText(R.string.compatibility_change_custom_audio_channels_conflict,
                    codec, requestedChannels, targetChannels));
        }
        if (hasRequestedLayout && requestedLayoutChannels > 0 && requestedLayoutChannels != targetChannels) {
            logCompatibilityChange(compatibilityText(R.string.compatibility_change_custom_audio_layout_conflict,
                    codec, params.audioChannelLayout.trim(), targetChannels));
        }
        if (needsAutomaticRate && params.skipAutomaticAudioSampleRate) {
            logCompatibilityChange(compatibilityText(R.string.compatibility_change_auto_audio_sample_rate_skipped,
                    codec, sourceRateText, targetRate));
        }
        if (needsAutomaticChannels && params.skipAutomaticAudioChannels) {
            logCompatibilityChange(compatibilityText(R.string.compatibility_change_auto_audio_channels_skipped,
                    codec, sourceChannelsText, targetChannels));
        }

        // 只有用户未明确指定或主动跳过时，才决定是否由兼容性规则补入固定参数
        if (requestedRate <= 0 && !params.skipAutomaticAudioSampleRate) {
            cmd.add("-ar");
            cmd.add(String.valueOf(targetRate));
        }
        if (requestedChannels <= 0 && !hasRequestedLayout && !params.skipAutomaticAudioChannels) {
            cmd.add("-ac");
            cmd.add(String.valueOf(targetChannels));
        }
    }

    private static int getKnownChannelLayoutChannels(String layout) {
        if (isBlank(layout)) return -1;
        switch (layout.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "mono":
            case "1.0": return 1;
            case "stereo":
            case "2.0":
            case "binaural":
            case "stereo_downmix": return 2;
            case "2.1":
            case "3.0":
            case "surround": return 3;
            case "3.1":
            case "4.0":
            case "quad": return 4;
            case "4.1":
            case "5.0": return 5;
            case "5.1":
            case "5.1(side)": return 6;
            case "6.1": return 7;
            case "7.1": return 8;
            default: return -1;
        }
    }

    private static void addAudioSampleRate(ArrayList<String> cmd,
                                           int sourceRate,
                                           int targetRate,
                                           String codec,
                                           ParameterData params) {
        int requestedRate = parsePositiveInt(params.audioSampleRate);
        if (requestedRate > 0) {
            // 用户显式填写的采样率优先保留；如果不在编码器常见支持集合内，提前说明可能失败
            if (!isCommonSampleRateSupported(codec, requestedRate)) {
                logCompatibilityChange(compatibilityText(R.string.compatibility_change_custom_audio_rate_unsupported,
                        codec, requestedRate, targetRate > 0 ? targetRate : 0));
            }
            return;
        }
        if (targetRate <= 0) return;
        if (params.skipAutomaticAudioSampleRate) {
            if (sourceRate <= 0 || sourceRate != targetRate) {
                String sourceRateText = sourceRate > 0 ? sourceRate + " Hz"
                        : compatibilityText(R.string.compatibility_value_unknown);
                logCompatibilityChange(compatibilityText(R.string.compatibility_change_auto_audio_sample_rate_skipped,
                        codec, sourceRateText, targetRate));
            }
            return;
        }
        // 对于完全相同的采样率无需增加参数，避免冗余命令
        if (sourceRate > 0 && sourceRate == targetRate) return;
        cmd.add("-ar");
        cmd.add(String.valueOf(targetRate));
        if (sourceRate > 0 && sourceRate != targetRate) {
            logCompatibilityChange(compatibilityText(R.string.compatibility_change_audio_sample_rate,
                    codec, sourceRate, targetRate));
        }
    }

    private static boolean isCommonSampleRateSupported(String codec, int rate) {
        String c = codec == null ? "" : codec.toLowerCase();
        if (c.contains("speex")) return rate == 8000 || rate == 16000 || rate == 32000;
        if (c.equals("libopus")) return rate == 8000 || rate == 12000 || rate == 16000 || rate == 24000 || rate == 48000;
        if (c.equals("libmp3lame")) return containsRate(rate, 8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000);
        if (c.equals("libshine")) return containsRate(rate, 32000, 44100, 48000);
        if (c.equals("ac3") || c.equals("eac3")) return containsRate(rate, 32000, 44100, 48000);
        if (c.equals("mp2") || c.equals("libtwolame")) return containsRate(rate, 16000, 22050, 24000, 32000, 44100, 48000);
        if (c.equals("dca")) return containsRate(rate, 8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000);
        if (c.equals("opencore_amrnb")) return rate == 8000;
        if (c.equals("vo_amrwbenc")) return rate == 16000;
        if (c.equals("libilbc") || c.equals("ilbc")) return rate == 8000;
        if (c.equals("wmav2") || c.equals("wma") || c.equals("libvorbis")) return rate <= 48000;
        if (c.equals("liblc3") || c.equals("lc3")) return containsRate(rate, 8000, 16000, 24000, 32000, 48000, 96000);
        return true;
    }

    private static boolean containsRate(int rate, int... supportedRates) {
        for (int supportedRate : supportedRates) if (rate == supportedRate) return true;
        return false;
    }

    private static int parsePositiveInt(String value) {
        if (value == null || value.trim().isEmpty()) return -1;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static int nearestSupportedSampleRate(int sourceRate, int... supportedRates) {
        if (supportedRates == null || supportedRates.length == 0) return -1;
        if (sourceRate <= 0) return -1;
        int best = supportedRates[0];
        long bestDistance = Math.abs((long) sourceRate - best);
        for (int rate : supportedRates) {
            long distance = Math.abs((long) sourceRate - rate);
            if (distance < bestDistance) {
                best = rate;
                bestDistance = distance;
            }
        }
        return best;
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
                params.audioBitrateValue, true, false, params.skipAutomaticAudioBitrate);

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
                params.audioBitrateValue, false, true, params.skipAutomaticAudioBitrate);

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
                params.audioBitrateValue, true, false, params.skipAutomaticAudioBitrate);

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
     * 获取原始音频采样率 (Hz)，获取失败返回 -1
     * 仅在需要离散采样率适配的编码器上调用，避免无意义的额外探测
     */
    private static int getOriginalAudioSampleRate(String inputPath) {
        return probeAudioInteger(inputPath, "sample_rate", "采样率");
    }

    /**
     * 获取原始音频声道数，获取失败返回 -1
     */
    private static int getOriginalAudioChannels(String inputPath) {
        return probeAudioInteger(inputPath, "channels", "声道数");
    }

    /**
     * 统一探测音频流参数，兼容输出中的空行并记录失败原因，便于定位 FFprobe 问题
     */
    private static int probeAudioInteger(String inputPath, String entry, String label) {
        if (inputPath == null || inputPath.trim().isEmpty()) return -1;
        try {
            String escapedPath = inputPath.replace("\\", "\\\\")
                    .replace("\"", "\\\"");
            String probeCmd = "-v error -select_streams a:0 -show_entries stream=" + entry
                    + " -of default=noprint_wrappers=1:nokey=1 \"" + escapedPath + "\"";
            FFprobeSession session = FFprobeKit.execute(probeCmd);
            if (session == null) {
                Log.w(TAG, "FFprobe 获取音频" + label + "失败：session 为空，输入=" + inputPath);
                return -1;
            }

            String output = session.getOutput();
            if (ReturnCode.isSuccess(session.getReturnCode()) && output != null) {
                for (String line : output.split("\\R")) {
                    String value = line.trim();
                    if (value.isEmpty() || "N/A".equalsIgnoreCase(value)) continue;
                    try {
                        int parsed = Integer.parseInt(value);
                        if (parsed > 0) return parsed;
                    } catch (NumberFormatException ignored) {
                        // 忽略非数值行，继续查找有效参数
                    }
                }
            }
            Log.w(TAG, "FFprobe 获取音频" + label + "失败：returnCode="
                    + session.getReturnCode() + ", output=" + output + ", 输入=" + inputPath);
        } catch (Exception e) {
            Log.e(TAG, "获取原始音频" + label + "失败: " + inputPath, e);
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
            case "heif":
            case "heic":
                // 兼容旧参数：当前构建没有 HEIF/HEIC muxer，实际输出回退到 JPEG
                return "jpg";
            case "avif": return "avif";
            case "jxl": return "jxl";
            case "jp2": return "jp2";
            case "apng": return "apng";
            case "qoi": return "qoi";
            case "tga": return "tga";
            case "dpx": return "dpx";
            case "exr": return "exr";
            case "ico": return "ico";
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
            case "ico":
                // ICO 是 muxer，不是 encoder；使用 PNG 编码器并指定 ICO muxer
                return "png";
            // 当前 FFmpeg 构建未启用 libheif，因此 HEIF/HEIC 不作为输出格式
            case "heif":
            case "heic":
                return null;
            default: return null;
        }
    }
}
