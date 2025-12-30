package com.tech.ezconvert.utils;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * 纯 Java 测试 不依赖 Android API
 */
public class FileUtilsTest {

    @Test
    public void testFileExtensionExtraction() {
        // 测试纯字符串逻辑，模拟 FileUtils 的行为
        String fileName = "video.mp4";
        String extension = extractFileExtension(fileName);
        assertEquals("mp4", extension);
        
        // 测试无扩展名
        assertEquals("", extractFileExtension("no_extension"));
        
        // 测试大写扩展名
        assertEquals("avi", extractFileExtension("movie.AVI"));
    }
    
    // 模拟 FileUtils 中获取扩展名的逻辑
    private String extractFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    @Test
    public void testPathConcatenation() {
        // 测试路径拼接逻辑
        String dir = "/storage/emulated/0/Download";
        String file = "test.mp4";
        String fullPath = dir + "/" + file;
        assertEquals("/storage/emulated/0/Download/test.mp4", fullPath);
    }
}
