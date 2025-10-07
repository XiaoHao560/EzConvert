package com.tech.ezconvert;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements FFmpegExecutor.ConversionCallback {
    
    private FFmpegExecutor ffmpegExecutor;
    private TextView statusText;
    private Button testInfoButton, testVideoButton, testAudioButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        ffmpegExecutor = new FFmpegExecutor();
        statusText = findViewById(R.id.status_text);
        testInfoButton = findViewById(R.id.test_info_button);
        testVideoButton = findViewById(R.id.test_video_button);
        testAudioButton = findViewById(R.id.test_audio_button);
        
        testInfoButton.setOnClickListener(v -> {
            // 测试获取媒体信息
            String info = ffmpegExecutor.getMediaInfo("/sdcard/Download/test.mp4");
            statusText.setText("媒体信息:\n" + info);
        });
        
        testVideoButton.setOnClickListener(v -> {
            // 测试视频转换
            statusText.setText("开始模拟视频转换...");
            ffmpegExecutor.convertVideo(
                "/sdcard/Download/input.mp4", 
                "/sdcard/Download/output.avi", 
                "avi", 
                this
            );
        });
        
        testAudioButton.setOnClickListener(v -> {
            // 测试音频转换
            statusText.setText("开始模拟音频转换...");
            ffmpegExecutor.convertAudio(
                "/sdcard/Download/input.mp3", 
                "/sdcard/Download/output.wav", 
                "wav", 
                this
            );
        });
        
        updateStatus("EzConvert v0.0.1 - 模拟模式已启动");
    }
    
    private void updateStatus(String message) {
        runOnUiThread(() -> statusText.setText(message));
    }
    
    @Override
    public void onProgressUpdate(int progress) {
        runOnUiThread(() -> 
            statusText.setText("处理进度: " + progress + "%"));
    }
    
    @Override
    public boolean isCancelled() {
        return false;
    }
    
    @Override
    public void onConversionComplete(boolean success, String message) {
        runOnUiThread(() -> {
            statusText.setText(message);
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ffmpegExecutor != null) {
            ffmpegExecutor.release();
        }
    }
}