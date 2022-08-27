package com.sammy.chargerateautomator;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;

public class BatteryService extends Service {
    Handler mHandler = new Handler();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent battIntent = new Intent();
        battIntent.setAction("com.sammy.chargerateautomator.notifier");

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                mHandler.postDelayed(this, 2500);
                if (MainActivity.isRunning || BatteryWorker.isCharging) sendBroadcast(battIntent);
            }
        };
        mHandler.post(runnable);

        final String CHANNELID = "Batt";
        NotificationChannel channel = new NotificationChannel(
                CHANNELID,
                CHANNELID,
                NotificationManager.IMPORTANCE_LOW
        );

        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        Notification.Builder notification = new Notification.Builder(this, CHANNELID)
                .setSmallIcon(R.drawable.ic_launcher);

        startForeground(1001, notification.build());
        return super.onStartCommand(intent, flags, startId);
    }
}
