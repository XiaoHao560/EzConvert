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
    private static final int NOTIFICATION_ID_NEED_PARAMETERS = 1002;
    private static final int NOTIFICATION_ID_ALL_COMPLETE = 1003;
    private static final int NOTIFICATION_ID_COMPLETE_BASE = 2000;

    private static int completeNotificationId = NOTIFICATION_ID_COMPLETE_BASE;
    // 记录通知渠道名称使用的语言，语言切换后需要更新系统保存的渠道名称
    private static volatile String channelsLocaleTag;

    /**
     * 确保通知渠道已创建，并在应用语言变化后更新系统显示的渠道名称
     */
    private static void ensureChannelsCreated(Context localizedContext) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        String localeTag = localizedContext.getResources().getConfiguration()
                .getLocales().toLanguageTags();
        if (localeTag.equals(channelsLocaleTag)) return;

        synchronized (NotificationHelper.class) {
            if (localeTag.equals(channelsLocaleTag)) return;

            NotificationManager manager = localizedContext.getSystemService(NotificationManager.class);
            if (manager == null) return;

            // 进度通知渠道
            NotificationChannel progressChannel = new NotificationChannel(
                    CHANNEL_ID_PROGRESS,
                    localizedContext.getString(R.string.notification_channel_progress_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            progressChannel.setDescription(localizedContext.getString(R.string.notification_channel_progress_desc));
            progressChannel.setSound(null, null);
            progressChannel.enableVibration(false);

            // 完成通知渠道
            NotificationChannel completeChannel = new NotificationChannel(
                    CHANNEL_ID_COMPLETE,
                    localizedContext.getString(R.string.notification_channel_complete_name),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            completeChannel.setDescription(localizedContext.getString(R.string.notification_channel_complete_desc));

            manager.createNotificationChannel(progressChannel);
            manager.createNotificationChannel(completeChannel);
            channelsLocaleTag = localeTag;
        }
    }

    /**
     * 返回使用应用内语言设置的 Context，后台 Worker 不会经过 BaseActivity 的语言包装
     */
    private static Context getLocalizedContext(Context context) {
        return LanguageManager.applySavedLanguage(context);
    }

    /**
     * 构建进度通知（供 Worker 的 ForegroundInfo 使用）
     * 前台服务通知不依赖 POST_NOTIFICATIONS 权限
     */
    public static Notification buildProgressNotification(Context context, String fileName, int progress) {
        return buildProgressNotification(context, fileName, progress, false);
    }

    /** 构建支持不确定进度状态的通知，避免未知媒体时长时进度条一直停留在 0% */
    public static Notification buildProgressNotification(Context context, String fileName, int progress,
                                                         boolean indeterminate) {
        Context localizedContext = getLocalizedContext(context);
        ensureChannelsCreated(localizedContext);

        Intent intent = new Intent(localizedContext, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                localizedContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        int clampedProgress = Math.max(0, Math.min(100, progress));
        String title = localizedContext.getString(R.string.notification_progress_title, fileName);
        String text = indeterminate
                ? localizedContext.getString(R.string.notification_progress_indeterminate)
                : localizedContext.getString(R.string.notification_progress_text, clampedProgress);

        return new NotificationCompat.Builder(localizedContext, CHANNEL_ID_PROGRESS)
                .setSmallIcon(R.drawable.ic_splash_logo)
                .setContentTitle(title)
                .setContentText(text)
                .setProgress(indeterminate ? 0 : 100, clampedProgress, indeterminate)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true)
                .build();
    }

    public static void showProgressNotification(Context context, String fileName, int progress) {
        if (!ConfigManager.getInstance(context).isNotificationEnabled()) return;
        if (!areNotificationsEnabled(context)) return;

        Notification notification = buildProgressNotification(context, fileName, progress);
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PROGRESS, notification);
    }

    public static void showCompleteNotification(Context context, String fileName, boolean success, String message) {
        if (!ConfigManager.getInstance(context).isNotificationEnabled()) return;
        if (!areNotificationsEnabled(context)) return;

        Context localizedContext = getLocalizedContext(context);
        ensureChannelsCreated(localizedContext);

        Intent intent = new Intent(localizedContext, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                localizedContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = success ? 
            localizedContext.getString(R.string.notification_complete_title_success) :
            localizedContext.getString(R.string.notification_complete_title_fail);
        int icon = success ? R.drawable.play_outline : R.drawable.alert_circle;

        String contentText;
        if (success) {
            contentText = localizedContext.getString(R.string.notification_complete_text_success, fileName);
        } else {
            contentText = localizedContext.getString(R.string.notification_complete_text_fail, fileName, message);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(localizedContext, CHANNEL_ID_COMPLETE)
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
        if (!areNotificationsEnabled(context)) return;

        Context localizedContext = getLocalizedContext(context);
        ensureChannelsCreated(localizedContext);

        Intent intent = new Intent(localizedContext, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                localizedContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = localizedContext.getString(R.string.notification_cancelled_title);
        String fileNameDisplay = (fileName != null && !fileName.isEmpty()) ? fileName : localizedContext.getString(R.string.notification_file_placeholder);
        String content = localizedContext.getString(R.string.notification_cancelled_text, fileNameDisplay);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(localizedContext, CHANNEL_ID_COMPLETE)
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

    /**
     * 下一项需要用户设置参数时的提示通知
     * 使用与转换进度通知相同的低打扰频道，并保持常驻，直到参数真正确认
     */
    public static void showNeedParametersNotification(Context context) {
        if (!ConfigManager.getInstance(context).isNotificationEnabled()) return;
        if (!areNotificationsEnabled(context)) return;

        Context localizedContext = getLocalizedContext(context);
        ensureChannelsCreated(localizedContext);

        Intent intent = new Intent(localizedContext, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                localizedContext, 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(localizedContext, CHANNEL_ID_PROGRESS)
                .setSmallIcon(R.drawable.ic_splash_logo)
                .setContentTitle(localizedContext.getString(R.string.notification_need_parameters_title))
                .setContentText(localizedContext.getString(R.string.notification_need_parameters_text))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent);

        // setOngoing(true) 主要用于表示通知处于持续状态；部分 Android/OEM 的
        // “清除全部通知”仍可能移除它，使用 FLAG_NO_CLEAR 明确要求系统不要允许用户手动清除
        Notification notification = builder.build();
        notification.flags |= Notification.FLAG_NO_CLEAR;

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_NEED_PARAMETERS, notification);
    }

    /** 参数确认后移除“请返回设置参数”的常驻通知 */
    public static void cancelNeedParametersNotification(Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_NEED_PARAMETERS);
    }

    /** 整个多文件队列全部处理完成后的最终通知 */
    public static void showAllTasksCompleteNotification(Context context) {
        if (!ConfigManager.getInstance(context).isNotificationEnabled()) return;
        if (!areNotificationsEnabled(context)) return;

        Context localizedContext = getLocalizedContext(context);
        ensureChannelsCreated(localizedContext);

        Intent intent = new Intent(localizedContext, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                localizedContext, 3, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(localizedContext, CHANNEL_ID_COMPLETE)
                .setSmallIcon(R.drawable.ic_splash_logo)
                .setContentTitle(localizedContext.getString(R.string.notification_all_tasks_complete_title))
                .setContentText(localizedContext.getString(R.string.notification_all_tasks_complete_text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROGRESS);
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_NEED_PARAMETERS);
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_ALL_COMPLETE, builder.build());
    }

    public static void cancelProgressNotification(Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROGRESS);
    }

    public static boolean areNotificationsEnabled(Context context) {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }
}
