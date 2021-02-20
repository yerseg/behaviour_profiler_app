package com.yerseg.profiler;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class ReminderNotificationPeriodicWorker extends Worker {

    public ReminderNotificationPeriodicWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel notificationChannel = notificationManager.getNotificationChannel(ProfilingService.NOTIFICATION_CHANNEL_ID);

        if (notificationChannel == null)
            return Result.success();

        Intent resultIntent = new Intent(getApplicationContext(), MainActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(getApplicationContext());
        stackBuilder.addNextIntentWithParentStack(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getApplicationContext(), ProfilingService.NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder
                .setContentTitle("Profiler")
                .setContentText("Please, send statistics!")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setPriority(NotificationManager.IMPORTANCE_MAX)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(resultPendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .build();

        notificationManager.notify(ProfilingService.REMINDER_NOTIFICATION_ID, notification);

        return Result.success();
    }
}
