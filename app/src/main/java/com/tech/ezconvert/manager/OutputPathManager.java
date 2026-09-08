package com.tech.ezconvert.manager;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.FileUtils;
import com.tech.ezconvert.utils.FfmpegCommandBuilder;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.ParameterData;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 负责生成输出文件路径
 *
 * 路径生成与 Activity 解耦，避免 UI 层同时处理文件名、目录和任务类型前缀
 */
public class OutputPathManager {
    private final Context context;

    public OutputPathManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /** 根据当前输入文件和任务类型生成不带最终扩展名的基础输出路径 */
    public String generateBasePath(String inputKey, Uri uri, String taskType) {
        String baseName = getBaseName(inputKey, uri, "file");
        File outputDir = getOutputDir();
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            Log.w("OutputPathManager", "无法创建输出目录: " + outputDir);
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String prefix = getPrefixTaskType(taskType);
        String path = new File(outputDir, baseName + "_" + prefix + timestamp).getAbsolutePath();
        Log.d("OutputPathManager", "输出路径基础: " + path);
        return path;
    }

    /** 为通过分享入口接收的文件生成临时/输出路径 */
    public String generateSharePath(String inputKey, Uri uri) {
        String base = getBaseName(inputKey, uri, "shared");
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File outputDir = getOutputDir();
        if (!outputDir.exists()) outputDir.mkdirs();
        return new File(outputDir, base + context.getString(R.string.filename_suffix_share) + timestamp).getAbsolutePath();
    }

    /** 根据参数决定最终输出扩展名和完整路径 */
    public String buildOutputPath(String basePath, ParameterData params) {
        return FfmpegCommandBuilder.buildOutputPath(basePath, params);
    }

    private File getOutputDir() {
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                context.getString(R.string.folder_output));
    }

    // Uri 能提供真实显示名称时优先使用，否则回退到队列中的 key
    private String getBaseName(String inputKey, Uri uri, String fallback) {
        String name = uri != null ? FileUtils.getDisplayName(context, uri) : null;
        if (name == null || name.isEmpty()) name = inputKey;
        if (name == null || name.isEmpty()) return fallback;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String getPrefixTaskType(String taskType) {
        if (taskType == null) return "converted_";
        switch (taskType) {
            case "convert": return "converted_";
            case "compress": return "compressed_";
            case "cut_video": return "cut_";
            case "screenshot": return "screenshot_";
            case "extract_audio": return "extract_audio_";
            case "convert_audio": return "convert_audio_";
            case "cut_audio": return "cut_audio_";
            case "convert_image": return "convert_image_";
            default: return "converted_";
        }
    }
}
