package com.sammy.sbatterytweaks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;

import com.topjohnwu.superuser.ShellUtils;

import java.io.File;

public class BatteryService extends Service {
    BatteryWorker batteryWorker = new BatteryWorker();
    public static boolean isCharging;

    public static int percentage;
    private final File fullCapFIle = new File("/sys/class/power_supply/battery/batt_full_capacity");
    Handler mHandler = new Handler();
    private Context context;

    public static boolean isBypassed() {
        return isCharging && (BatteryWorker.bypassSupported && percentage >= BatteryWorker.battFullCap) ||
                (BatteryWorker.pausePdSupported && BatteryWorker.pausePdEnabled);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        context = this;
        final String CHANNELID = "Batt";
        NotificationChannel channel = new NotificationChannel(
                CHANNELID,
                CHANNELID,
                NotificationManager.IMPORTANCE_NONE
        );

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        notificationManager.createNotificationChannel(channel);

        Notification.Builder notification = new Notification.Builder(this, CHANNELID).setOngoing(true)
                .setSmallIcon(R.drawable.ic_launcher)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true);

        startForeground(1002, notification.build());

        BatteryReceiver batteryReceiver = new BatteryReceiver();

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, ifilter);

        IntentFilter ACTION_POWER_CONNECTED = new IntentFilter("android.intent.action.ACTION_POWER_CONNECTED");
        IntentFilter ACTION_POWER_DISCONNECTED = new IntentFilter("android.intent.action.ACTION_POWER_DISCONNECTED");

        registerReceiver(new BatteryReceiver(), ACTION_POWER_CONNECTED);
        registerReceiver(new BatteryReceiver(), ACTION_POWER_DISCONNECTED);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                BatteryWorker.bypassSupported = (fullCapFIle.exists() && Utils.isRooted()) ||
                        (BatteryWorker.pausePdSupported);
                isCharging = batteryReceiver.isCharging();

                if (MainActivity.isRunning) {
                    BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                    BatteryWorker.currentNow = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
                    BatteryWorker.voltage = batteryReceiver.getVolt() + " mV";
                }

                if (MainActivity.isRunning || isCharging) {
                    if (BatteryWorker.bypassSupported)
                        BatteryWorker.battFullCap = Integer.parseInt(ShellUtils.fastCmd("cat " + fullCapFIle));

                    percentage = batteryReceiver.getLevel();

                    if (Utils.isRooted())
                        BatteryWorker.temperature = Float.parseFloat(ShellUtils.fastCmd("cat /sys/class/power_supply/battery/temp"));
                    else
                        BatteryWorker.temperature = batteryReceiver.getTemp();

                    BatteryWorker.updateStats(isCharging);
                    batteryWorker.batteryWorker(context, isCharging);

                    notification.setContentText("Temperature: " + BatteryWorker.battTemp);
                    notificationManager.notify(1002, notification.build());
                } else {
                    notification.setContentText("");
                    notificationManager.notify(1002, notification.build());
                }
                mHandler.postDelayed(this, 2500);
            }
        };
        mHandler.post(runnable);

        return super.onStartCommand(intent, flags, startId);
    }
}
