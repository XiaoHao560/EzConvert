package com.tech.ezconvert.ui;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.slider.Slider;
import com.tech.ezconvert.R;
import com.tech.ezconvert.utils.AnimationUtils;
import com.tech.ezconvert.utils.FileUtils;
import com.tech.ezconvert.utils.Log;
import com.tech.ezconvert.utils.ToastUtils;

import java.io.File;
import java.util.Locale;

public class PreviewActivity extends BaseActivity {

    @Override
    protected int getTitleContainerId() {
        return R.id.title_container;
    }

//    @Override 
//    protected int getScrollContentId() { 
//        return R.id.scroll_content; 
//    }

    private FrameLayout playerContainer;
    private PlayerView playerView;
    private MaterialToolbar toolbar;
    private MaterialButton selectFileBtn;
    private MaterialCardView emptyStateCard;
    private CircularProgressIndicator loadingIndicator;
    private ImageButton centerPlayBtn;
    private ImageButton centerReplayBtn;
    private LinearLayout bottomControls;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private TextView mediaInfoText;
    private TextView realtimeInfoText;
    private Slider progressSlider;
    private ImageButton playPauseBtn;
    private ImageButton fullscreenBtn;
    private ImageButton changeFileBtn;
    private ImageButton mediaInfoBtn;

    private ExoPlayer exoPlayer;

    // 状态
    private boolean isPlaying = false;
    private boolean isControlsVisible = true;
    private boolean isUserSeeking = false;
    private boolean isFullscreen = false;
    private boolean isMediaInfoVisible = false;

    // 媒体信息实时更新
    private Runnable mediaInfoUpdateRunnable;
    private long lastRenderedFrameCount = 0;
    private long lastStatsUpdateTime = 0;
    private String cachedStaticMediaInfo = "";
    private String currentFilePath = "";
    private Uri currentFileUri = null;

    // 手势与定时器
    private GestureDetector gestureDetector;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable hideControlsRunnable;
    private Runnable progressUpdateRunnable;

    private ActivityResultLauncher<Intent> filePickerLauncher;

    // 常量
    private static final long CONTROLS_HIDE_DELAY_MS = 3000;
    private static final long DOUBLE_TAP_SEEK_MS = 10000; // 双击快进/快退 10秒

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_preview);

        setupActivityResultLaunchers();
        initializeViews();
        setupPlayer();
        setupGestureDetector();
        setupListeners();
        startProgressUpdateLoop();

        // 检查是否有传入的文件
        handleIntent(getIntent());
    }

    private void setupActivityResultLaunchers() {
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        // 直接使用 Uri 加载
                        loadMedia(uri);
                    } else {
                        ToastUtils.show(this, getString(R.string.toast_file_not_exist_or_inaccessible));
                        Log.e("PreviewActivity", "无法访问文件");
                    }
                }
            }
        );
    }

    private void initializeViews() {
        playerContainer = findViewById(R.id.player_container);
        playerView = findViewById(R.id.player_view);
        toolbar = findViewById(R.id.title_container);
        selectFileBtn = findViewById(R.id.select_file_btn);
        emptyStateCard = findViewById(R.id.empty_state_card);
        loadingIndicator = findViewById(R.id.loading_indicator);
        centerPlayBtn = findViewById(R.id.center_play_btn);
        centerReplayBtn = findViewById(R.id.center_replay_btn);
        bottomControls = findViewById(R.id.bottom_controls);
        currentTimeText = findViewById(R.id.current_time_text);
        totalTimeText = findViewById(R.id.total_time_text);
        progressSlider = findViewById(R.id.progress_slider);
        playPauseBtn = findViewById(R.id.play_pause_btn);
        fullscreenBtn = findViewById(R.id.fullscreen_btn);
        changeFileBtn = findViewById(R.id.change_file_btn);
        mediaInfoText = findViewById(R.id.media_info_text);
        realtimeInfoText = findViewById(R.id.realtime_info_text);
        mediaInfoBtn = findViewById(R.id.media_info_btn);

        // 设置 toolbar 返回按钮
        toolbar.setNavigationOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            finish();
        });

        // 设置 tooltips
        fullscreenBtn.setTooltipText(getString(R.string.tooltip_fullscreen));
        changeFileBtn.setTooltipText(getString(R.string.tooltip_folder));
        mediaInfoBtn.setTooltipText(getString(R.string.tooltip_mediainfo));
        playPauseBtn.setTooltipText(getString(R.string.tooltip_pause)); // 初始为暂停，但会在状态更新时调整

        // 初始状态
        showEmptyState(true);
        hideCenterButtons();
        hideBottomControls(false);
    }

    private void setupPlayer() {
        exoPlayer = new ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(DOUBLE_TAP_SEEK_MS)
            .setSeekForwardIncrementMs(DOUBLE_TAP_SEEK_MS)
            .build();

        // 媒体信息按钮
        mediaInfoBtn.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            toggleMediaInfo();
        });

        // 将 ExoPlayer 绑定到 PlayerView，但禁用默认控制器
        playerView.setPlayer(exoPlayer);
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);

        // 播放器状态监听
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                updatePlaybackState(playbackState);
            }

            @Override
            public void onIsPlayingChanged(boolean playing) {
                isPlaying = playing;
                updateCenterButtonState();
                updatePlayPauseIcon();
                if (playing) {
                    scheduleHideControls();
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                ToastUtils.show(PreviewActivity.this,
                    getString(R.string.toast_playback_error, error.getMessage()));
                Log.e("exoPlayer", "播放错误: " + error.getMessage());
                loadingIndicator.setVisibility(View.GONE);
            }
        });
    }

    // 切换媒体信息悬浮层显示/隐藏
    private void toggleMediaInfo() {
        if (isMediaInfoVisible) {
            hideMediaInfo();
        } else {
            showMediaInfo();
        }
    }

    // 显示媒体信息悬浮层
    private void showMediaInfo() {
        if (currentFileUri == null || mediaInfoText == null) return;

        // 首次显示时加载静态信息（只有路径有效时才加载）
        if (cachedStaticMediaInfo.isEmpty() && currentFilePath != null && !currentFilePath.isEmpty()) {
            loadMediaInfoStatic();
        } else if (cachedStaticMediaInfo.isEmpty()) {
            cachedStaticMediaInfo = getString(R.string.media_info_no_file);
        }

        // 取消可能正在进行的隐藏动画
        mediaInfoText.animate().cancel();
        if (realtimeInfoText != null) realtimeInfoText.animate().cancel();

        mediaInfoText.setVisibility(View.VISIBLE);
        mediaInfoText.setAlpha(0f);
        mediaInfoText.animate()
            .alpha(1f)
            .setDuration(200)
            .start();

        if (isFullscreen && realtimeInfoText != null) {
            realtimeInfoText.setVisibility(View.VISIBLE);
            realtimeInfoText.setAlpha(0f);
            realtimeInfoText.animate()
                .alpha(1f)
                .setDuration(200)
                .start();
        }

        isMediaInfoVisible = true;

        // 启动实时刷新循环
        startMediaInfoRealtimeLoop();
        cancelHideControls();
    }

    // 隐藏媒体信息悬浮层
    private void hideMediaInfo() {
        if (mediaInfoText == null) return;

        // 停止实时刷新
        stopMediaInfoRealtimeLoop();

        // 取消可能正在进行的显示动画
        mediaInfoText.animate().cancel();

        mediaInfoText.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction(() -> mediaInfoText.setVisibility(View.GONE))
            .start();

        if (realtimeInfoText != null) {
            realtimeInfoText.animate().cancel();
            realtimeInfoText.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> realtimeInfoText.setVisibility(View.GONE))
                .start();
        }

        isMediaInfoVisible = false;

        if (isPlaying) {
            scheduleHideControls();
        }
    }

    // 使用 FFprobe 加载文件静态信息（仅当 currentFilePath 有效时）
    private void loadMediaInfoStatic() {
        if (currentFilePath == null || currentFilePath.isEmpty()) {
            cachedStaticMediaInfo = getString(R.string.media_info_no_file);
            return;
        }

        String probeCommand = "-v quiet -print_format json -show_format -show_streams \"" + currentFilePath + "\"";

        com.arthenica.ffmpegkit.FFprobeKit.executeAsync(probeCommand, session -> {
            String output = session.getOutput();
            if (output != null && !output.isEmpty()) {
                cachedStaticMediaInfo = parseStaticMediaInfo(output);
            } else {
                cachedStaticMediaInfo = getString(R.string.media_info_cannot_get);
                Log.e("PreviewActivity", "获取媒体信息失败");
            }
        });
    }

    // 解析 FFprobe JSON 为静态信息字符串
    private String parseStaticMediaInfo(String jsonOutput) {
        StringBuilder info = new StringBuilder();
        String unknown = getString(R.string.media_info_unknown);

        try {
            org.json.JSONObject root = new org.json.JSONObject(jsonOutput);

            if (root.has("format")) {
                org.json.JSONObject format = root.getJSONObject("format");
                String fileName = currentFilePath != null ? new File(currentFilePath).getName() : "?";
                info.append(getString(R.string.media_info_file))
                    .append(fileName).append("\n");
                info.append(getString(R.string.media_info_format))
                    .append(format.optString("format_name", unknown)).append("\n");
                info.append(getString(R.string.media_info_duration))
                    .append(formatTime((long)(format.optDouble("duration", 0) * 1000))).append("\n");
                info.append(getString(R.string.media_info_size))
                    .append(formatFileSize(format.optLong("size", 0))).append("\n");
                info.append(getString(R.string.media_info_bitrate))
                    .append(formatBitrate(format.optLong("bit_rate", 0))).append("\n\n");
            }

            if (root.has("streams")) {
                org.json.JSONArray streams = root.getJSONArray("streams");
                for (int i = 0; i < streams.length(); i++) {
                    org.json.JSONObject stream = streams.getJSONObject(i);
                    String codecType = stream.optString("codec_type", "");

                    if ("video".equals(codecType)) {
                        info.append(getString(R.string.media_info_video))
                            .append(stream.optString("codec_name", unknown)).append("\n");
                        info.append(getString(R.string.media_info_resolution))
                            .append(stream.optInt("width", 0))
                            .append("x").append(stream.optInt("height", 0)).append("\n");

                        String fps = stream.optString("avg_frame_rate", "");
                        if (fps.isEmpty() || "0/0".equals(fps)) {
                            fps = stream.optString("r_frame_rate", "");
                        }

                        if (!fps.isEmpty() && fps.contains("/")) {
                            String[] parts = fps.split("/");
                            if (parts.length == 2) {
                                try {
                                    double num = Double.parseDouble(parts[0]);
                                    double den = Double.parseDouble(parts[1]);
                                    if (den > 0) {
                                        double frameRate = num / den;
                                        if (frameRate > 0 && frameRate < 1000) {
                                            info.append(getString(R.string.media_info_framerate))
                                                .append(String.format("%.2f", frameRate)).append(" fps\n");
                                        }
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                        info.append(getString(R.string.media_info_pixel_format))
                            .append(stream.optString("pix_fmt", unknown)).append("\n\n");

                    } else if ("audio".equals(codecType)) {
                        info.append(getString(R.string.media_info_audio))
                            .append(stream.optString("codec_name", unknown)).append("\n");
                        info.append(getString(R.string.media_info_samplerate))
                            .append(stream.optInt("sample_rate", 0) / 1000).append(" kHz\n");
                        info.append(getString(R.string.media_info_channels))
                            .append(stream.optInt("channels", 0)).append("\n\n");
                    }
                }
            }

        } catch (Exception e) {
            info.append(getString(R.string.media_info_parse_failed)).append(e.getMessage());
        }

        return info.toString().trim();
    }

    // 启动媒体信息实时刷新循环 (500ms)
    private void startMediaInfoRealtimeLoop() {
        stopMediaInfoRealtimeLoop(); // 防止重复启动

        mediaInfoUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isMediaInfoVisible && exoPlayer != null) {
                    updateMediaInfoRealtime();
                    uiHandler.postDelayed(this, 500);
                }
            }
        };
        uiHandler.post(mediaInfoUpdateRunnable);
    }

    // 停止实时刷新
    private void stopMediaInfoRealtimeLoop() {
        if (mediaInfoUpdateRunnable != null) {
            uiHandler.removeCallbacks(mediaInfoUpdateRunnable);
            mediaInfoUpdateRunnable = null;
        }
    }

    // 从 ExoPlayer 获取实时数据并更新 OSD
    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private void updateMediaInfoRealtime() {
        if (mediaInfoText == null || exoPlayer == null) return;

        // 静态信息
        String staticInfo = cachedStaticMediaInfo;

        // ── 实时播放统计 ──
        StringBuilder realtime = new StringBuilder();
        realtime.append(getString(R.string.media_info_realtime_title)).append("\n");

        // 播放位置
        long position = exoPlayer.getCurrentPosition();
        long duration = exoPlayer.getDuration();
        realtime.append(getString(R.string.media_info_position)).append(formatTime(position));
        if (duration > 0) {
            realtime.append(" / ").append(formatTime(duration));
            realtime.append(String.format(getString(R.string.media_info_position_percent), position * 100.0 / duration));
        }
        realtime.append("\n");

        // 缓冲
        long buffered = exoPlayer.getTotalBufferedDuration();
        if (buffered > 0) {
            realtime.append(getString(R.string.media_info_buffered_format, buffered / 1000f)).append("\n");
        }

        // 播放速度
        float speed = exoPlayer.getPlaybackParameters().speed;
        if (speed != 1.0f) {
            realtime.append(getString(R.string.media_info_speed_format, speed)).append("\n");
        }

        // 视频输出尺寸
        androidx.media3.common.Format videoFormat = exoPlayer.getVideoFormat();
        if (videoFormat != null) {
            if (videoFormat.width != androidx.media3.common.Format.NO_VALUE
                && videoFormat.height != androidx.media3.common.Format.NO_VALUE) {
                realtime.append(getString(R.string.media_info_output_format, videoFormat.width, videoFormat.height)).append("\n");
            }
        }

        // 解码器统计（实时 FPS / 丢帧）
        androidx.media3.exoplayer.DecoderCounters videoCounters = exoPlayer.getVideoDecoderCounters();
        if (videoCounters != null) {
            videoCounters.ensureUpdated();
            long currentTime = System.currentTimeMillis();
            long rendered = videoCounters.renderedOutputBufferCount;

            if (lastStatsUpdateTime > 0 && lastRenderedFrameCount > 0 && currentTime > lastStatsUpdateTime) {
                long deltaFrames = rendered - lastRenderedFrameCount;
                double deltaSeconds = (currentTime - lastStatsUpdateTime) / 1000.0;
                double currentFps = deltaFrames / deltaSeconds;
                realtime.append(getString(R.string.media_info_fps_format, currentFps)).append("\n");
            }

            if (videoCounters.droppedBufferCount > 0) {
                realtime.append(getString(R.string.media_info_dropped_format, videoCounters.droppedBufferCount)).append("\n");
            }
            if (videoCounters.skippedOutputBufferCount > 0) {
                realtime.append(getString(R.string.media_info_skipped_format, videoCounters.skippedOutputBufferCount)).append("\n");
            }

            lastRenderedFrameCount = rendered;
            lastStatsUpdateTime = currentTime;
        }

        if (isFullscreen) {
            mediaInfoText.setText(staticInfo);
            if (realtimeInfoText != null) {
                realtimeInfoText.setText(realtime.toString().trim());
                if (isMediaInfoVisible && realtimeInfoText.getVisibility() != View.VISIBLE) {
                    realtimeInfoText.setVisibility(View.VISIBLE);
                    realtimeInfoText.setAlpha(1f);
                }
            }
        } else {
            StringBuilder full = new StringBuilder();
            if (!staticInfo.isEmpty()) {
                full.append(staticInfo).append("\n\n");
            }
            full.append(realtime);
            mediaInfoText.setText(full.toString().trim());
            if (realtimeInfoText != null) {
                realtimeInfoText.setVisibility(View.GONE);
            }
        }
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format(Locale.getDefault(), "%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private String formatBitrate(long bitRate) {
        if (bitRate <= 0) return getString(R.string.media_info_unknown);
        if (bitRate < 1000) return bitRate + " bps";
        if (bitRate < 1000000) return String.format("%.1f Kbps", bitRate / 1000.0);
        return String.format("%.2f Mbps", bitRate / 1000000.0);
    }

    private void setupGestureDetector() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                toggleControlsVisibility();
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (exoPlayer == null || currentFileUri == null) return false;

                float x = e.getX();
                float width = playerContainer.getWidth();

                if (x < width / 3) {
                    // 左侧双击：快退
                    long newPos = Math.max(0, exoPlayer.getCurrentPosition() - DOUBLE_TAP_SEEK_MS);
                    exoPlayer.seekTo(newPos);
                    showSeekFeedback(getString(R.string.seek_back_format, DOUBLE_TAP_SEEK_MS / 1000));
                } else if (x > width * 2 / 3) {
                    // 右侧双击：快进
                    long duration = exoPlayer.getDuration();
                    long newPos = Math.min(duration, exoPlayer.getCurrentPosition() + DOUBLE_TAP_SEEK_MS);
                    exoPlayer.seekTo(newPos);
                    showSeekFeedback(getString(R.string.seek_forward_format, DOUBLE_TAP_SEEK_MS / 1000));
                } else {
                    // 中间双击：播放/暂停
                    togglePlayPause();
                }
                return true;
            }
        });
    }

    private void setupListeners() {
        // 选择文件按钮 (空状态卡片)
        selectFileBtn.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            openFilePicker();
        });

        // 中央播放按钮
        centerPlayBtn.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            if (exoPlayer.getPlaybackState() == Player.STATE_ENDED) {
                exoPlayer.seekTo(0);
            }
            exoPlayer.play();
        });

        // 中央重播按钮
        centerReplayBtn.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            exoPlayer.seekTo(0);
            exoPlayer.play();
        });

        // 底部播放/暂停按钮
        playPauseBtn.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            togglePlayPause();
        });

        // 更换文件按钮 - 返回到空状态卡片
        changeFileBtn.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            returnToEmptyState();
        });

        // 全屏按钮
        fullscreenBtn.setOnClickListener(v -> {
            AnimationUtils.animateButtonClick(v);
            toggleFullscreen();
        });

        // MD3 Slider 进度条监听
        progressSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                isUserSeeking = true;
                cancelHideControls();
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                isUserSeeking = false;
                if (exoPlayer != null) {
                    long duration = exoPlayer.getDuration();
                    long position = (long) ((duration * slider.getValue()) / 1000f);
                    exoPlayer.seekTo(position);
                }
                scheduleHideControls();
            }
        });

        progressSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && exoPlayer != null) {
                long duration = exoPlayer.getDuration();
                if (duration > 0) {
                    long position = (long) ((duration * value) / 1000f);
                    currentTimeText.setText(formatTime(position));
                }
            }
        });

        // 播放器容器触摸事件（手势）
        playerContainer.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });
    }

    private void startProgressUpdateLoop() {
        progressUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null && !isUserSeeking && isPlaying) {
                    long duration = exoPlayer.getDuration();
                    long position = exoPlayer.getCurrentPosition();

                    if (duration > 0) {
                        float progress = (position * 1000f) / duration;
                        setProgressSafe(progress);
                        currentTimeText.setText(formatTime(position));
                        totalTimeText.setText(formatTime(duration));
                    }
                }
                uiHandler.postDelayed(this, 200);
            }
        };
        uiHandler.post(progressUpdateRunnable);
    }

    private void setProgressSafe(float value) {
        float clamped = Math.max(0f, Math.min(1000f, value));
        progressSlider.setValue(clamped);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        String[] mimeTypes = {"video/*", "audio/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);

        try {
            filePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.picker_title_simple)));
        } catch (Exception e) {
            ToastUtils.show(this, getString(R.string.toast_cannot_open_picker));
            Log.e("PreviewActivity", "打开文件选择器失败");
        }
    }

    // 使用 Uri 加载媒体
    private void loadMedia(Uri uri) {
        if (uri == null) return;
        currentFileUri = uri;

        // 尝试获取显示名称
        String displayName = FileUtils.getDisplayName(this, uri);
        if (displayName == null || displayName.isEmpty()) {
            displayName = uri.getLastPathSegment();
            if (displayName == null) displayName = "file";
        }

        // 设置标题
        toolbar.setTitle(displayName);

        // 尝试获取文件路径（用于 FFprobe 信息，可能为 null）
        String filePath = FileUtils.getPath(this, uri);
        currentFilePath = (filePath != null && !filePath.isEmpty()) ? filePath : null;
        // 如果路径为空，则清空静态缓存
        if (currentFilePath == null) {
            cachedStaticMediaInfo = getString(R.string.media_info_no_file);
        } else {
            cachedStaticMediaInfo = ""; // 延迟加载
        }

        // 显示加载指示器
        loadingIndicator.setVisibility(View.VISIBLE);
        hideCenterButtons();

        // 使用 Uri 构建 MediaItem
        MediaItem mediaItem = MediaItem.fromUri(uri);
        exoPlayer.setMediaItem(mediaItem);
        exoPlayer.prepare();

        // 隐藏空状态，显示播放器
        showEmptyState(false);

        // 自动开始播放
        exoPlayer.play();
    }

    // 保留原有 loadMedia(String) 作为兼容，内部调用新方法
    private void loadMedia(String filePath) {
        if (filePath == null || filePath.isEmpty()) return;
        // 尝试将路径转换为 Uri
        File file = new File(filePath);
        Uri uri = Uri.fromFile(file);
        loadMedia(uri);
    }

    // 返回空状态界面 (选择文件卡片)，停止当前播放
    private void returnToEmptyState() {
        if (isMediaInfoVisible && mediaInfoText != null) {
            stopMediaInfoRealtimeLoop();
            mediaInfoText.animate().cancel();
            mediaInfoText.setVisibility(View.GONE);
            isMediaInfoVisible = false;
        }

        if (realtimeInfoText != null) {
            realtimeInfoText.animate().cancel();
            realtimeInfoText.setVisibility(View.GONE);
        }

        // 清空静态缓存
        cachedStaticMediaInfo = "";
        lastRenderedFrameCount = 0;
        lastStatsUpdateTime = 0;

        // 如果信息悬浮层正在显示，先隐藏
        if (isMediaInfoVisible) {
            hideMediaInfo();
        }

        // 停止播放并释放当前媒体
        if (exoPlayer != null) {
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        }

        // 重置状态
        currentFilePath = "";
        currentFileUri = null;
        isPlaying = false;

        // 如果当前是全屏状态，先退出全屏
        if (isFullscreen) {
            exitFullscreen();
        }

        // 隐藏播放器，显示空状态卡片
        showEmptyState(true);

        // 重置进度条
        progressSlider.setValue(0);
        currentTimeText.setText("00:00");
        totalTimeText.setText("00:00");

        // 重置标题
        toolbar.setTitle(getString(R.string.title_preview));
    }

    private void showEmptyState(boolean show) {
        if (show) {
            emptyStateCard.setVisibility(View.VISIBLE);
            playerContainer.setVisibility(View.GONE);
            loadingIndicator.setVisibility(View.GONE);
            hideCenterButtons();
            hideBottomControls(false);
            hideToolbar(false);
            // 取消自动隐藏控制栏的定时器
            cancelHideControls();
        } else {
            emptyStateCard.setVisibility(View.GONE);
            playerContainer.setVisibility(View.VISIBLE);
            // 初始显示控制栏
            showBottomControls();
            showToolbar();
            isControlsVisible = true;
            // 确保全屏按钮tooltip正确（初始化时已设置）
            fullscreenBtn.setTooltipText(getString(R.string.tooltip_fullscreen));
        }
    }

    private void updatePlaybackState(int playbackState) {
        switch (playbackState) {
            case Player.STATE_BUFFERING:
                loadingIndicator.setVisibility(View.VISIBLE);
                break;
            case Player.STATE_READY:
                loadingIndicator.setVisibility(View.GONE);
                // 更新总时长显示
                long duration = exoPlayer.getDuration();
                if (duration > 0) {
                    totalTimeText.setText(formatTime(duration));
                }
                break;
            case Player.STATE_ENDED:
                loadingIndicator.setVisibility(View.GONE);
                showCenterReplayButton();
                break;
            case Player.STATE_IDLE:
                loadingIndicator.setVisibility(View.GONE);
                break;
        }
    }

    private void updateCenterButtonState() {
        if (isPlaying) {
            hideCenterButtons();
        } else {
            if (exoPlayer.getPlaybackState() == Player.STATE_ENDED) {
                showCenterReplayButton();
            } else {
                showCenterPlayButton();
            }
        }
    }

    private void updatePlayPauseIcon() {
        if (isPlaying) {
            playPauseBtn.setImageResource(R.drawable.round_pause);
            playPauseBtn.setTooltipText(getString(R.string.tooltip_pause));
        } else {
            playPauseBtn.setImageResource(R.drawable.round_play_arrow);
            playPauseBtn.setTooltipText(getString(R.string.tooltip_resume));
        }
    }

    private void showCenterPlayButton() {
        centerPlayBtn.setVisibility(View.VISIBLE);
        centerPlayBtn.setAlpha(0f);
        centerPlayBtn.animate()
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(new DecelerateInterpolator())
            .start();
        centerReplayBtn.setVisibility(View.GONE);
    }

    private void showCenterReplayButton() {
        centerReplayBtn.setVisibility(View.VISIBLE);
        centerReplayBtn.setAlpha(0f);
        centerReplayBtn.animate()
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(new DecelerateInterpolator())
            .start();
        centerPlayBtn.setVisibility(View.GONE);
    }

    private void hideCenterButtons() {
        centerPlayBtn.setVisibility(View.GONE);
        centerReplayBtn.setVisibility(View.GONE);
    }

    private void togglePlayPause() {
        if (exoPlayer == null) return;

        if (isPlaying) {
            exoPlayer.pause();
            showCenterPlayButton();
        } else {
            if (exoPlayer.getPlaybackState() == Player.STATE_ENDED) {
                exoPlayer.seekTo(0);
            }
            exoPlayer.play();
            hideCenterButtons();
        }
    }

    private void toggleControlsVisibility() {
        if (isControlsVisible) {
            hideBottomControls(true);
            hideToolbar(true);
        } else {
            showBottomControls();
            showToolbar();
            if (isPlaying) {
                scheduleHideControls();
            }
        }
        isControlsVisible = !isControlsVisible;
    }

    private void showBottomControls() {
        bottomControls.setVisibility(View.VISIBLE);
        bottomControls.setAlpha(0f);
        bottomControls.setTranslationY(bottomControls.getHeight() * 0.3f);
        bottomControls.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    private void hideBottomControls(boolean animate) {
        if (animate) {
            bottomControls.animate()
                .alpha(0f)
                .translationY(bottomControls.getHeight() * 0.3f)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> bottomControls.setVisibility(View.GONE))
                .start();
        } else {
            bottomControls.setVisibility(View.GONE);
            bottomControls.setAlpha(0f);
        }
    }

    private void showToolbar() {
        toolbar.setVisibility(View.VISIBLE);
        toolbar.setAlpha(0f);
        toolbar.setTranslationY(-toolbar.getHeight() * 0.3f);
        toolbar.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }

    private void hideToolbar(boolean animate) {
        if (animate) {
            toolbar.animate()
                .alpha(0f)
                .translationY(-toolbar.getHeight() * 0.3f)
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> toolbar.setVisibility(View.GONE))
                .start();
        } else {
            toolbar.setVisibility(View.GONE);
            toolbar.setAlpha(0f);
        }
    }

    private void scheduleHideControls() {
        cancelHideControls();
        hideControlsRunnable = () -> {
            if (isPlaying && !isUserSeeking) {
                hideBottomControls(true);
                hideToolbar(true);
                isControlsVisible = false;
            }
        };
        uiHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS);
    }

    private void cancelHideControls() {
        if (hideControlsRunnable != null) {
            uiHandler.removeCallbacks(hideControlsRunnable);
        }
    }

    private void showSeekFeedback(String text) {
        ToastUtils.show(this, text);
    }

    // 切换全屏模式 - 进入横屏
    private void toggleFullscreen() {
        if (isFullscreen) {
            exitFullscreen();
        } else {
            enterFullscreen();
        }
    }

    // 进入全屏横屏模式
    private void enterFullscreen() {
        isFullscreen = true;

        // 切换为横屏
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // 隐藏系统 UI（沉浸式）
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );

        // 更新图标
        fullscreenBtn.setImageResource(R.drawable.round_fullscreen_exit);
        fullscreenBtn.setTooltipText(getString(R.string.tooltip_exit_fullscreen));

        // 切换 PlayerView 为适配模式
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);

        // 如果信息悬浮层可见，立即按新布局刷新
        if (isMediaInfoVisible) {
            updateMediaInfoRealtime();
        }
    }

    // 退出全屏，恢复竖屏
    private void exitFullscreen() {
        isFullscreen = false;

        // 恢复竖屏
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // 显示系统 UI
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);

        // 更新图标
        fullscreenBtn.setImageResource(R.drawable.round_fullscreen);
        fullscreenBtn.setTooltipText(getString(R.string.tooltip_fullscreen));

        // 恢复 PlayerView 为适应模式
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);

        // 刷新信息悬浮层
        if (isMediaInfoVisible) {
            updateMediaInfoRealtime();
        }
    }

    @Override
    public void onBackPressed() {
        // 如果当前是全屏状态，先退出全屏而不是退出 Activity
        if (isFullscreen) {
            exitFullscreen();
            return;
        }
        super.onBackPressed();
    }

    private String formatTime(long millis) {
        if (millis < 0) return "00:00";
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    private void handleIntent(Intent intent) {
        if (intent != null) {
            // 优先使用 file_uri
            String uriString = intent.getStringExtra("file_uri");
            if (uriString != null && !uriString.isEmpty()) {
                Uri uri = Uri.parse(uriString);
                loadMedia(uri);
                return;
            }
            // 兼容旧版 file_path
            String path = intent.getStringExtra("file_path");
            if (path != null && !path.isEmpty()) {
                loadMedia(path);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (exoPlayer != null && currentFileUri != null && !isPlaying) {
            // 不自动播放，等待操作
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null) {
            exoPlayer.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacksAndMessages(null);
        stopMediaInfoRealtimeLoop();
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
