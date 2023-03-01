package com.sammy.sbatterytweaks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;

import com.topjohnwu.superuser.ShellUtils;

import java.io.File;

public class BatteryService extends Service {
    private Context context;
    Handler mHandler = new Handler();
    private final File testmodeFIle = new File("/sys/class/power_supply/battery/test_mode");
    public static boolean isCharging;

    private static int battTestMode = 0;

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

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                mHandler.postDelayed(this, 2000);
                BatteryWorker.bypassSupported = testmodeFIle.exists();
                isCharging = batteryReceiver.isCharging();

                if (!isCharging) {
                    if (BatteryWorker.disableSync && ContentResolver.getMasterSyncAutomatically())
                        ContentResolver.setMasterSyncAutomatically(false);
                }

                if (MainActivity.isRunning) {
                    BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                    BatteryWorker.currentNow = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE) + " mA";
                }

                if (MainActivity.isRunning || isCharging) {
                    if (BatteryWorker.disableSync && !ContentResolver.getMasterSyncAutomatically())
                        ContentResolver.setMasterSyncAutomatically(true);

                    if (BatteryWorker.bypassSupported)
                        battTestMode = Integer.parseInt(ShellUtils.fastCmd("cat " + testmodeFIle.toString()));

                    BatteryWorker.percentage = batteryReceiver.getLevel();
                    BatteryWorker.temperature = batteryReceiver.getTemp();

                    BatteryWorker.updateStats(isCharging);
                    BatteryWorker.batteryWorker(context);
                }

                if (!isCharging && BatteryWorker.battTestMode == 1)
                    BatteryWorker.setBypass(false, false);
            }
        };
        mHandler.post(runnable);

        return super.onStartCommand(intent, flags, startId);
    }
    public static boolean isBypassed() {
        return battTestMode == 1 && isCharging;
    }
}
