package com.tech.ezconvert.utils;

import com.google.gson.annotations.SerializedName;

public class ParameterData {
    @SerializedName("preset_name")
    public String presetName;

    // 通用
    @SerializedName("task_type")
    public String taskType; // "convert", "compress", "extract_audio", "cut_video", "screenshot", "convert_audio", "cut_audio"

    // 视频参数
    @SerializedName("output_format")
    public String outputFormat; // mp4, mkv, webm, avi, mov, flv, gif, mp3, wav, aac, flac, ogg, m4a

    @SerializedName("video_codec")
    public String videoCodec; // 编码器名称

    @SerializedName("audio_codec")
    public String audioCodec;

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

    // 默认构造
    public ParameterData() {
        // 设置默认值
        presetName = "默认";
        taskType = "convert";
        outputFormat = "mp4";
        videoCodec = "h264_mediacodec";
        audioCodec = "aac";
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
    }

    // 拷贝构造
    public ParameterData copy() {
        ParameterData copy = new ParameterData();
        copy.presetName = this.presetName;
        copy.taskType = this.taskType;
        copy.outputFormat = this.outputFormat;
        copy.videoCodec = this.videoCodec;
        copy.audioCodec = this.audioCodec;
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
        return copy;
    }

    // 重置为出厂默认
    public static ParameterData createDefault() {
        return new ParameterData();
    }
}