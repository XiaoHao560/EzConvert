package com.tech.ezconvert.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.tech.ezconvert.MainActivity;
import com.tech.ezconvert.R;

public class NotificationHelper {
    private static final String CHANNEL_ID_PROGRESS = "conversion_progress";
    private static final String CHANNEL_ID_COMPLETE = "conversion_complete";
    private static final int NOTIFICATION_ID_PROGRESS = 1001;
    private static final int NOTIFICATION_ID_COMPLETE_BASE = 2000;

    private static int completeNotificationId = NOTIFICATION_ID_COMPLETE_BASE;

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);

            // 进度通知渠道
            NotificationChannel progressChannel = new NotificationChannel(
                    CHANNEL_ID_PROGRESS,
                    context.getString(R.string.notification_channel_progress_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            progressChannel.setDescription(context.getString(R.string.notification_channel_progress_desc));
            progressChannel.setSound(null, null);
            progressChannel.enableVibration(false);

            // 完成通知渠道
            NotificationChannel completeChannel = new NotificationChannel(
                    CHANNEL_ID_COMPLETE,
                    context.getString(R.string.notification_channel_complete_name),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            completeChannel.setDescription(context.getString(R.string.notification_channel_complete_desc));

            manager.createNotificationChannel(progressChannel);
            manager.createNotificationChannel(completeChannel);
        }
    }

    /**
     * 构建进度通知（供 Worker 的 ForegroundInfo 使用）
     */
    public static Notification buildProgressNotification(Context context, String fileName, int progress) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = context.getString(R.string.notification_progress_title, fileName);
        String text = context.getString(R.string.notification_progress_text, progress);

        return new NotificationCompat.Builder(context, CHANNEL_ID_PROGRESS)
                .setSmallIcon(R.drawable.ic_splash_logo)
                .setContentTitle(title)
                .setContentText(text)
                .setProgress(100, progress, false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .build();
    }

    public static void showProgressNotification(Context context, String fileName, int progress) {
        if (!ConfigManager.getInstance(context).isNotificationEnabled()) return;

        Notification notification = buildProgressNotification(context, fileName, progress);
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PROGRESS, notification);
    }

    public static void showCompleteNotification(Context context, String fileName, boolean success, String message) {
        if (!ConfigManager.getInstance(context).isNotificationEnabled()) return;

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = success ? 
            context.getString(R.string.notification_complete_title_success) :
            context.getString(R.string.notification_complete_title_fail);
        int icon = success ? R.drawable.play_outline : R.drawable.alert_circle;

        String contentText;
        if (success) {
            contentText = context.getString(R.string.notification_complete_text_success, fileName);
        } else {
            contentText = context.getString(R.string.notification_complete_text_fail, fileName, message);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_COMPLETE)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(contentText)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        // 取消进度通知
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROGRESS);

        // 显示完成通知（使用递增ID避免覆盖）
        NotificationManagerCompat.from(context).notify(completeNotificationId++, builder.build());
    }

    // 显示取消通知
    public static void showCancelledNotification(Context context, String fileName) {
        if (!ConfigManager.getInstance(context).isNotificationEnabled()) return;

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = context.getString(R.string.notification_cancelled_title);
        String fileNameDisplay = (fileName != null && !fileName.isEmpty()) ? fileName : context.getString(R.string.notification_file_placeholder);
        String content = context.getString(R.string.notification_cancelled_text, fileNameDisplay);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_COMPLETE)
                .setSmallIcon(R.drawable.ic_splash_logo)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        // 取消进度通知
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROGRESS);

        // 显示取消通知（使用递增ID避免覆盖）
        NotificationManagerCompat.from(context).notify(completeNotificationId++, builder.build());
    }

    public static void cancelProgressNotification(Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROGRESS);
    }

    public static boolean areNotificationsEnabled(Context context) {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }
}