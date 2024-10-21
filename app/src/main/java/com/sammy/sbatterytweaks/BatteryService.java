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

public class BatteryService extends Service {
    static NotificationManager notificationManager;
    static Notification.Builder notification;
    public static boolean manualBypass = true;

    public static int currentNow, refreshInterval = 2500;
    public static final String fullCapFIle = "/sys/class/power_supply/battery/batt_full_capacity";

    static Handler mHandler = new Handler();
    static Runnable runnable;
    private Context context;

    public static boolean isBypassed() {
        return BatteryReceiver.notCharging() || (BatteryReceiver.isCharging() &&
                BatteryWorker.pausePdSupported &&
                BatteryWorker.pausePdEnabled);
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

        BatteryManager manager = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
        runnable = new Runnable() {
            @Override
            public void run() {
                currentNow = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);

                if (BatteryWorker.bypassSupported)
                    BatteryWorker.battFullCap = Integer.parseInt(Utils.runCmd("cat " + fullCapFIle));

                BatteryWorker.updateStats(context, BatteryReceiver.isCharging());
                BatteryWorker.batteryWorker(context, BatteryReceiver.isCharging());

                if (!isBypassed() && BatteryWorker.pausePdSupported &&
                        BatteryWorker.idleEnabled && BatteryReceiver.mLevel >= BatteryWorker.idleLevel) {
                    BatteryWorker.setBypass(context, 1, false);
                }
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