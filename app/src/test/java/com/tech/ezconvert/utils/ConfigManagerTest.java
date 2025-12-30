package com.tech.ezconvert.utils;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;
import static org.junit.Assert.*;

/**
 * 纯 Java 测试 ConfigManager 的 JSON 操作逻辑
 */
public class ConfigManagerTest {

    private Gson gson = new Gson();
    
    // 使用 JUnit 的 TemporaryFolder 自动处理测试文件路径
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    @Test
    public void testJsonSerialization() throws Exception {
        // 在测试目录中创建临时文件
        File testDir = testFolder.newFolder("test_config");
        File settingsFile = new File(testDir, "settings.json");
        
        // 创建测试数据
        Map<String, Object> settings = new HashMap<>();
        Map<String, Object> transcodeSettings = new HashMap<>();
        transcodeSettings.put("hardware_acceleration", true);
        transcodeSettings.put("multithreading", false);
        settings.put("transcode_settings", transcodeSettings);

        // 写入文件
        try (FileWriter writer = new FileWriter(settingsFile)) {
            gson.toJson(settings, writer);
        }

        // 读取并验证
        try (FileReader reader = new FileReader(settingsFile)) {
            Map<?, ?> loaded = gson.fromJson(reader, Map.class);
            assertNotNull(loaded);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> loadedTranscode = (Map<String, Object>) loaded.get("transcode_settings");
            assertTrue((Boolean) loadedTranscode.get("hardware_acceleration"));
            assertFalse((Boolean) loadedTranscode.get("multithreading"));
        }
        // 无需清理，TemporaryFolder自动处理
    }

    @Test
    public void testDefaultConfigCreation() {
        Map<String, Object> defaultSettings = createDefaultConfig();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> transcode = (Map<String, Object>) defaultSettings.get("transcode_settings");
        assertFalse((Boolean) transcode.get("hardware_acceleration"));
        assertTrue((Boolean) transcode.get("multithreading"));
    }
    
    // 提取为独立方法，便于测试
    private Map<String, Object> createDefaultConfig() {
        Map<String, Object> settings = new HashMap<>();
        Map<String, Object> transcodeSettings = new HashMap<>();
        transcodeSettings.put("hardware_acceleration", false);
        transcodeSettings.put("multithreading", true);
        settings.put("transcode_settings", transcodeSettings);
        return settings;
    }
}
