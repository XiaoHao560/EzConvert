package com.tech.ezconvert.processor;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class VideoProcessorTest {

    private String getFileExtension(String format) {
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

    public String[] generateConvertVideoCommand(String inputPath, String outputPath,
                                               String format, boolean hardwareAccel, boolean multithreading) {
        String outputFile = outputPath + "." + getFileExtension(format);
        
        ArrayList<String> commandList = new ArrayList<>();
        commandList.add("-i");
        commandList.add(inputPath);
        
        if (multithreading) {
            commandList.add("-threads");
            commandList.add("0");
        }
        
        if ("mp4".equalsIgnoreCase(format) && hardwareAccel) {
            commandList.add("-c:v");
            commandList.add("h264_mediacodec");
        } else if ("gif".equalsIgnoreCase(format)) {
            commandList.add("-vf");
            commandList.add("fps=10,scale=480:-1");
        }
        
        commandList.add("-y");
        commandList.add(outputFile);
        
        return commandList.toArray(new String[0]);
    }

    @Test
    public void testVideoCommand_MP4_HardwareAccel() {
        String[] command = generateConvertVideoCommand(
            "/input/test.mov",
            "/output/test",
            "mp4",
            true,
            true
        );
        
        List<String> cmdList = Arrays.asList(command);
        assertTrue("应使用硬件加速", cmdList.contains("h264_mediacodec"));
        assertTrue("应包含输出文件", cmdList.contains("/output/test.mp4"));
    }

    @Test
    public void testVideoCommand_GIF_Format() {
        String[] command = generateConvertVideoCommand(
            "/input/video.mp4",
            "/output/animated",
            "gif",
            false,
            false
        );
        
        String joined = String.join(" ", command);
        assertTrue("应包含fps参数", joined.contains("fps=10"));
    }
}
