package com.tech.ezconvert.utils;

import com.google.gson.annotations.SerializedName;

public class ParameterData {
    @SerializedName("preset_name")
    public String presetName;

    // 通用
    @SerializedName("task_type")
    public String taskType; // "convert", "compress", "extract_audio", "cut_video", "screenshot", "convert_audio", "cut_audio", "convert_image"

    // 视频参数
    @SerializedName("output_format")
    public String outputFormat; // 视频、音频和图片输出格式；具体可选项由 ParameterDialogFragment 提供

    @SerializedName("video_codec")
    public String videoCodec; // 编码器名称

    @SerializedName("audio_codec")
    public String audioCodec;

    @SerializedName("quick_conversion_enabled")
    public boolean quickConversionEnabled;

    // 高级 FFmpeg 参数：空字符串表示用户没有主动指定，继续使用应用/FFmpeg 的现有行为
    @SerializedName("audio_sample_rate")
    public String audioSampleRate;
    @SerializedName("audio_channels")
    public String audioChannels;
    @SerializedName("audio_channel_layout")
    public String audioChannelLayout;
    @SerializedName("audio_sample_format")
    public String audioSampleFormat;
    @SerializedName("video_resolution")
    public String videoResolution;
    @SerializedName("video_frame_rate")
    public String videoFrameRate;
    @SerializedName("video_pixel_format")
    public String videoPixelFormat;
    @SerializedName("image_pixel_format")
    public String imagePixelFormat;
    @SerializedName("video_crf")
    public String videoCrf;
    @SerializedName("video_preset")
    public String videoPreset;
    @SerializedName("video_tune")
    public String videoTune;
    @SerializedName("video_profile")
    public String videoProfile;
    @SerializedName("video_level")
    public String videoLevel;
    @SerializedName("video_gop")
    public String videoGop;
    @SerializedName("video_b_frames")
    public String videoBFrames;
    @SerializedName("video_filter")
    public String videoFilter;
    @SerializedName("audio_filter")
    public String audioFilter;
    @SerializedName("custom_input_options")
    public String customInputOptions;
    @SerializedName("custom_output_options")
    public String customOutputOptions;
    @SerializedName("skip_automatic_video_quality")
    public boolean skipAutomaticVideoQuality;
    @SerializedName("skip_automatic_audio_bitrate")
    public boolean skipAutomaticAudioBitrate;
    @SerializedName("skip_automatic_audio_sample_rate")
    public boolean skipAutomaticAudioSampleRate;
    @SerializedName("skip_automatic_audio_channels")
    public boolean skipAutomaticAudioChannels;

    @SerializedName("video_bitrate_mode")
    public String videoBitrateMode; // "original" 或 "custom"

    @SerializedName("video_bitrate_value")
    public int videoBitrateValue; // 码率数值

    @SerializedName("video_bitrate_unit")
    public String videoBitrateUnit; // "Kbps" 或 "Mbps"

    @SerializedName("audio_bitrate_mode")
    public String audioBitrateMode; // "original" 或 "custom"

    @SerializedName("audio_bitrate_value")
    public int audioBitrateValue;

    @SerializedName("audio_bitrate_unit")
    public String audioBitrateUnit; // "Kbps"

    // 截图专用
    @SerializedName("screenshot_format")
    public String screenshotFormat; // "jpeg" 或 "png"

    @SerializedName("screenshot_resolution")
    public String screenshotResolution; // "original" 或 "1920x1080" 等

    @SerializedName("screenshot_quality")
    public int screenshotQuality; // 1-100

    // 裁剪
    @SerializedName("cut_start_time")
    public String cutStartTime;

    @SerializedName("cut_duration")
    public String cutDuration;

    // 音量
    @SerializedName("volume")
    public int volume; // 0-200

    // 图片转换参数
    @SerializedName("image_quality_mode")
    public String imageQualityMode; // "original" 或 "custom"

    @SerializedName("image_quality")
    public int imageQuality; // 1-100，仅当 mode 为 custom 时有效

    @SerializedName("image_resolution_mode")
    public String imageResolutionMode; // "original" 或 "custom"

    @SerializedName("image_resolution")
    public String imageResolution; // 如 "1920x1080"

    // 默认构造
    public ParameterData() {
        // 设置默认值
        presetName = "默认";
        taskType = "convert";
        outputFormat = "mp4";
        videoCodec = "h264_mediacodec";
        audioCodec = "aac";
        quickConversionEnabled = false;
        audioSampleRate = "";
        audioChannels = "";
        audioChannelLayout = "";
        audioSampleFormat = "";
        videoResolution = "";
        videoFrameRate = "";
        videoPixelFormat = "";
        imagePixelFormat = "";
        videoCrf = "";
        videoPreset = "";
        videoTune = "";
        videoProfile = "";
        videoLevel = "";
        videoGop = "";
        videoBFrames = "";
        videoFilter = "";
        audioFilter = "";
        customInputOptions = "";
        customOutputOptions = "";
        skipAutomaticVideoQuality = false;
        skipAutomaticAudioBitrate = false;
        skipAutomaticAudioSampleRate = false;
        skipAutomaticAudioChannels = false;
        videoBitrateMode = "original";
        videoBitrateValue = 0;
        videoBitrateUnit = "Mbps";
        audioBitrateMode = "original";
        audioBitrateValue = 0;
        audioBitrateUnit = "Kbps";
        screenshotFormat = "jpeg";
        screenshotResolution = "original";
        screenshotQuality = 90;
        cutStartTime = "00:00:00";
        cutDuration = "00:00:10";
        volume = 100;
        imageQualityMode = "original";
        imageQuality = 90;
        imageResolutionMode = "original";
        imageResolution = "original";
    }

    // 拷贝构造
    public ParameterData copy() {
        ParameterData copy = new ParameterData();
        copy.presetName = this.presetName;
        copy.taskType = this.taskType;
        copy.outputFormat = this.outputFormat;
        copy.videoCodec = this.videoCodec;
        copy.audioCodec = this.audioCodec;
        copy.quickConversionEnabled = this.quickConversionEnabled;
        copy.audioSampleRate = this.audioSampleRate;
        copy.audioChannels = this.audioChannels;
        copy.audioChannelLayout = this.audioChannelLayout;
        copy.audioSampleFormat = this.audioSampleFormat;
        copy.videoResolution = this.videoResolution;
        copy.videoFrameRate = this.videoFrameRate;
        copy.videoPixelFormat = this.videoPixelFormat;
        copy.imagePixelFormat = this.imagePixelFormat;
        copy.videoCrf = this.videoCrf;
        copy.videoPreset = this.videoPreset;
        copy.videoTune = this.videoTune;
        copy.videoProfile = this.videoProfile;
        copy.videoLevel = this.videoLevel;
        copy.videoGop = this.videoGop;
        copy.videoBFrames = this.videoBFrames;
        copy.videoFilter = this.videoFilter;
        copy.audioFilter = this.audioFilter;
        copy.customInputOptions = this.customInputOptions;
        copy.customOutputOptions = this.customOutputOptions;
        copy.skipAutomaticVideoQuality = this.skipAutomaticVideoQuality;
        copy.skipAutomaticAudioBitrate = this.skipAutomaticAudioBitrate;
        copy.skipAutomaticAudioSampleRate = this.skipAutomaticAudioSampleRate;
        copy.skipAutomaticAudioChannels = this.skipAutomaticAudioChannels;
        copy.videoBitrateMode = this.videoBitrateMode;
        copy.videoBitrateValue = this.videoBitrateValue;
        copy.videoBitrateUnit = this.videoBitrateUnit;
        copy.audioBitrateMode = this.audioBitrateMode;
        copy.audioBitrateValue = this.audioBitrateValue;
        copy.audioBitrateUnit = this.audioBitrateUnit;
        copy.screenshotFormat = this.screenshotFormat;
        copy.screenshotResolution = this.screenshotResolution;
        copy.screenshotQuality = this.screenshotQuality;
        copy.cutStartTime = this.cutStartTime;
        copy.cutDuration = this.cutDuration;
        copy.volume = this.volume;
        copy.imageQualityMode = this.imageQualityMode;
        copy.imageQuality = this.imageQuality;
        copy.imageResolutionMode = this.imageResolutionMode;
        copy.imageResolution = this.imageResolution;
        return copy;
    }

    // 重置为出厂默认
    public static ParameterData createDefault() {
        return new ParameterData();
    }
}