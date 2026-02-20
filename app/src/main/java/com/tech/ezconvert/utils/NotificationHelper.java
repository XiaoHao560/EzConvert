package com.tech.ezconvert.utils;

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
                "转换进度",
                NotificationManager.IMPORTANCE_LOW
            );
            progressChannel.setDescription("显示媒体文件转换进度");
            progressChannel.setSound(null, null);
            progressChannel.enableVibration(false);
            
            // 完成通知渠道
            NotificationChannel completeChannel = new NotificationChannel(
                CHANNEL_ID_COMPLETE,
                "转换完成",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            completeChannel.setDescription("转换成功或失败的通知");
            
            manager.createNotificationChannel(progressChannel);
            manager.createNotificationChannel(completeChannel);
        }
    }
    
    public static void showProgressNotification(Context context, String fileName, int progress) {
        if (!ConfigManager.getInstance(context).isNotificationEnabled()) return;
        
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_PROGRESS)
            .setSmallIcon(R.drawable.ic_splash_logo)
            .setContentTitle("正在转换: " + fileName)
            .setContentText("进度: " + progress + "%")
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true);
        
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PROGRESS, builder.build());
    }
    
    public static void showCompleteNotification(Context context, String fileName, boolean success, String message) {
        if (!ConfigManager.getInstance(context).isNotificationEnabled()) return;
        
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        String title = success ? "转换完成" : "转换失败";
        int icon = success ? R.drawable.play_outline : R.drawable.alert_circle;
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_COMPLETE)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(fileName + (success ? " 转换成功" : " " + message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true);
        
        // 取消进度通知
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROGRESS);
        
        // 显示完成通知（使用递增ID避免覆盖）
        NotificationManagerCompat.from(context).notify(completeNotificationId++, builder.build());
    }
    
    public static void cancelProgressNotification(Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROGRESS);
    }
    
    public static boolean areNotificationsEnabled(Context context) {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }
}
