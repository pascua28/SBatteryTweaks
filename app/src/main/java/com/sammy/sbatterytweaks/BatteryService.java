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

import com.topjohnwu.superuser.ShellUtils;

import java.io.File;

public class BatteryService extends Service {
    public static boolean isCharging;

    public static int percentage;
    private final File fullCapFIle = new File("/sys/class/power_supply/battery/batt_full_capacity");
    Handler mHandler = new Handler();
    private Context context;

    public static boolean isBypassed() {
        return isCharging && BatteryWorker.bypassSupported && percentage >= BatteryWorker.battFullCap;
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
                NotificationManager.IMPORTANCE_LOW
        );

        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        Notification.Builder notification = new Notification.Builder(this, CHANNELID)
                .setSmallIcon(R.drawable.ic_launcher);

        startForeground(1001, notification.build());

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
                mHandler.postDelayed(this, 2000);
                BatteryWorker.bypassSupported = fullCapFIle.exists() && Utils.isRooted();
                isCharging = batteryReceiver.isCharging();

                if (MainActivity.isRunning) {
                    BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                    BatteryWorker.currentNow = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) + " mA";
                    BatteryWorker.voltage = batteryReceiver.getVolt() + " mV";
                }

                if (MainActivity.isRunning || isCharging) {
                    if (BatteryWorker.bypassSupported)
                        BatteryWorker.battFullCap = Integer.parseInt(ShellUtils.fastCmd("cat " + fullCapFIle));

                    percentage = batteryReceiver.getLevel();
                    BatteryWorker.temperature = batteryReceiver.getTemp();

                    BatteryWorker.updateStats(isCharging);
                    BatteryWorker.batteryWorker(context, isCharging);
                }
            }
        };
        mHandler.post(runnable);

        return super.onStartCommand(intent, flags, startId);
    }
}
