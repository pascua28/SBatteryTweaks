package com.sammy.sbatterytweaks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;

import com.topjohnwu.superuser.ShellUtils;

import java.io.File;
import java.util.Objects;

public class BatteryService extends Service {
    Handler mHandler = new Handler();
    private Boolean readMode;
    private final String chargingFile = "/sys/class/power_supply/battery/charge_now";
    private final File chargeFile = new File (chargingFile);

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent battIntent = new Intent();
        battIntent.setAction("com.sammy.sbatterytweaks.notifier");

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

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                mHandler.postDelayed(this, 2000);
                readMode = chargeFile.canRead();
                if (readMode) {
                    BatteryWorker.isCharging = Objects.equals(Utils.readFile(chargingFile), "1");
                } else {
                    BatteryWorker.isCharging = ShellUtils.fastCmd("cat " + chargingFile).equals("1");
                }
                if (MainActivity.isRunning || BatteryWorker.isCharging)
                    BatteryWorker.updateStats(readMode);
                sendBroadcast(battIntent);
            }
        };
        mHandler.post(runnable);

        return super.onStartCommand(intent, flags, startId);
    }
}
