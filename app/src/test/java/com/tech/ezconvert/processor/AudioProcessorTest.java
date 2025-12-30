package com.tech.ezconvert.processor;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/**
 * 纯 Java 测试 无法测试 Android 或 native 库
 * 测试命令生成逻辑，不是实际执行
 */
public class AudioProcessorTest {

    // 模拟 TranscodeSettingsActivity 的方法
    private boolean mockMultithreadingEnabled = true;

    // 模拟 getAudioExtension 方法（从 AudioProcessor 中提取的私有方法）
    private String getAudioExtension(String format) {
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

    // 模拟 convertAudio 的核心逻辑 - 只生成命令，不执行
    public String[] generateConvertAudioCommand(String inputPath, String outputPath,
                                               String format, int volume, boolean multithreading) {
        String outputFile = outputPath + "." + getAudioExtension(format);
        
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(inputPath);
        
        if (multithreading) {
            commandList.add("-threads");
            commandList.add("0");
        }
        
        if (volume != 100) {
            double volumeFactor = volume / 100.0;
            commandList.add("-af");
            commandList.add("volume=" + volumeFactor);
        }
        
        // 添加格式特定参数
        switch (format.toLowerCase()) {
            case "mp3":
                commandList.add("-c:a");
                commandList.add("libmp3lame");
                break;
            case "wav":
                commandList.add("-c:a");
                commandList.add("pcm_s16le");
                break;
        }
        
        commandList.add("-y");
        commandList.add(outputFile);
        
        return commandList.toArray(new String[0]);
    }

    @Test
    public void testCommandGeneration_MP3_WithVolume() {
        String[] command = generateConvertAudioCommand(
            "/input/test.wav",
            "/output/test",
            "mp3",
            150,
            true
        );
        
        List<String> cmdList = Arrays.asList(command);
        
        assertTrue("应包含输入文件", cmdList.contains("/input/test.wav"));
        assertTrue("应包含音量", cmdList.contains("volume=1.5"));
        assertTrue("应包含MP3编码器", cmdList.contains("libmp3lame"));
        assertTrue("应包含输出文件", cmdList.contains("/output/test.mp3"));
        assertTrue("应启用多线程", cmdList.contains("-threads"));
    }

    @Test
    public void testCommandGeneration_WAV_NoVolume() {
        String[] command = generateConvertAudioCommand(
            "/input/test.mp3",
            "/output/test",
            "wav",
            100,
            false
        );
        
        List<String> cmdList = Arrays.asList(command);
        
        assertTrue("应包含WAV编码器", cmdList.contains("pcm_s16le"));
        assertFalse("不应包含音量参数", cmdList.contains("volume=1.0"));
        assertFalse("不应包含多线程", cmdList.contains("-threads"));
    }
}
