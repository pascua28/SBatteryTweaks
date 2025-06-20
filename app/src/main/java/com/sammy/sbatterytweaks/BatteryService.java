package com.sammy.sbatterytweaks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

public class BatteryService extends Service {
    public static final String fullCapFIle = "/sys/class/power_supply/battery/batt_full_capacity";
    public static int refreshInterval = 2500, isBypassed;
    static NotificationManager notificationManager;
    static Notification.Builder notification;
    static Handler mHandler = new Handler();
    static Runnable runnable;
    private Context context;

    public static boolean manualBypass;

    public static boolean isBypassed() {
        return BatteryReceiver.notCharging() || (BatteryReceiver.isCharging() &&
                BatteryWorker.pausePdSupported &&
                BatteryWorker.pausePdEnabled);
    }

    public static void startBackgroundTask() {
        if (mHandler.hasMessages(0))
            return;

        mHandler.sendEmptyMessage(0);
        mHandler.post(runnable);
    }

    public static void stopBackgroundTask() {
        if (mHandler.hasMessages(0)) {
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    public static void updateNotif(String msg, String msg2, String msg3) {
        if (notificationManager == null)
            return;

        notification.setContentTitle(msg)
                        .setStyle(
                                new Notification.InboxStyle()
                                        .addLine(msg2)
                                        .addLine(msg3)
                        );
        notificationManager.notify(1002, notification.build());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        BatteryWorker.bypassSupported = (Utils.isPrivileged() &&
                Utils.runCmd("ls " + fullCapFIle).contains(fullCapFIle));
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

        ScreenReceiver screenReceiver = new ScreenReceiver();
        registerReceiver(screenReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
        registerReceiver(screenReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));

        SharedPreferences sharedPreferences = context.getSharedPreferences("PROTECT_SETTING", Context.MODE_PRIVATE);
        try {
            sharedPreferences.edit().putInt("PROTECT_ENABLED",
                    Settings.Global.getInt(context.getContentResolver(), "protect_battery")).apply();
        } catch (Settings.SettingNotFoundException ignored) {
            sharedPreferences.edit().putInt("PROTECT_ENABLED", -1).apply();
        }

        runnable = new Runnable() {
            @Override
            public void run() {
                BatteryWorker.fetchUpdates(context);
                BatteryWorker.updateStats(context, BatteryReceiver.isCharging());

                if (manualBypass)
                    return;

                if (!isBypassed() && BatteryWorker.idleEnabled &&
                        BatteryReceiver.mLevel >= BatteryWorker.idleLevel) {
                    BatteryWorker.setBypass(context, 1);
                } else if (!BatteryWorker.pauseMode && isBypassed() &&
                        BatteryWorker.idleEnabled &&
                        BatteryReceiver.mLevel < BatteryWorker.idleLevel) {
                    if (BatteryWorker.pausePdSupported) {
                        Utils.changeSetting(context, Utils.Namespace.GLOBAL, "protect_battery", 0);
                        BatteryWorker.setBypass(context, 0);
                    } else BatteryWorker.setBypass(BatteryWorker.idleLevel);
                } else BatteryWorker.batteryWorker(context, BatteryReceiver.isCharging());

                mHandler.postDelayed(this, refreshInterval);
            }
        };
        startBackgroundTask();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopBackgroundTask();
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
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true);

        startForeground(1002, notification.build());
    }
}