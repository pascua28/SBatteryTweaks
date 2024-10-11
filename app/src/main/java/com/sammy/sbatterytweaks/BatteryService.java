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

import com.topjohnwu.superuser.ShellUtils;

import java.io.File;

public class BatteryService extends Service {
    static NotificationManager notificationManager;
    static Notification.Builder notification;
    public static boolean isCharging, manualBypass = false;

    public static int percentage;
    private final File fullCapFIle = new File("/sys/class/power_supply/battery/batt_full_capacity");

    Handler mHandler = new Handler();
    private Context context;

    public static boolean isBypassed() {
        return isCharging && (BatteryWorker.bypassSupported && percentage >= BatteryWorker.battFullCap) ||
                (BatteryWorker.pausePdSupported && BatteryWorker.pausePdEnabled) ||
                (BatteryWorker.currentNow < 50 && BatteryWorker.currentNow > -50);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        BatteryWorker.bypassSupported = (fullCapFIle.exists() && Utils.isRooted());
        try {
            Settings.System.getInt(getContentResolver(), "pass_through");
            BatteryWorker.pausePdSupported = true;
        } catch (Settings.SettingNotFoundException e) {
            BatteryWorker.pausePdSupported = false;
            e.printStackTrace();
        }

        buildNotif();

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
                isCharging = batteryReceiver.isCharging();

                if (MainActivity.isRunning || isCharging) {
                    BatteryManager manager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
                    BatteryWorker.currentNow = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);

                    if (BatteryWorker.bypassSupported)
                        BatteryWorker.battFullCap = Integer.parseInt(ShellUtils.fastCmd("cat " + fullCapFIle));

                    BatteryWorker.updateStats(isCharging);
                    BatteryWorker.batteryWorker(context, isCharging);

                    if (!isBypassed() && BatteryWorker.pausePdSupported &&
                            BatteryWorker.idleEnabled && percentage >= BatteryWorker.idleLevel) {
                        BatteryWorker.setBypass(context, 1, false);
                    }
                }
                mHandler.postDelayed(this, 5000);
            }
        };
        mHandler.post(runnable);
    }

    private void buildNotif() {
        final String CHANNELID = "Batt";
        NotificationChannel channel = new NotificationChannel(
                CHANNELID,
                CHANNELID,
                NotificationManager.IMPORTANCE_NONE
        );

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        notificationManager.createNotificationChannel(channel);

        notification = new Notification.Builder(this, CHANNELID).setOngoing(true)
                .setSmallIcon(R.drawable.ic_launcher)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true);

        startForeground(1002, notification.build());
    }

    public static void updateNotif(String msg) {
        if (notificationManager == null)
            return;
        notification.setContentText(msg);
        notificationManager.notify(1002, notification.build());
    }
}
