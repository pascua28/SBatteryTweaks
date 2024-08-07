package com.sammy.sbatterytweaks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
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
    public static boolean isCharging;
    public static int percentage;
    private final File fullCapFIle = new File("/sys/class/power_supply/battery/batt_full_capacity"),
            statsFile = new File("/data/system/batterystats.bin");
    private int mLevel, mStatus, mVolt;
    private float mTemp;
    private BroadcastReceiver batteryReceiver;
    Handler mHandler = new Handler();
    private Context context;
    final String CHANNELID = "Batt";
    NotificationChannel channel = new NotificationChannel(
            CHANNELID,
            CHANNELID,
            NotificationManager.IMPORTANCE_NONE
    );
    NotificationManager notificationManager;
    Notification.Builder notification;

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

        BatteryWorker.bypassSupported = (fullCapFIle.exists() && Utils.isRooted());
        try {
            Settings.System.getInt(getContentResolver(), "pass_through");
            BatteryWorker.pausePdSupported = true;
        } catch (Settings.SettingNotFoundException e) {
            BatteryWorker.pausePdSupported = false;
            e.printStackTrace();
        }

        final IntentFilter ifilter = new IntentFilter();
        ifilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        ifilter.addAction(Intent.ACTION_POWER_CONNECTED);
        ifilter.addAction(Intent.ACTION_POWER_DISCONNECTED);

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        notificationManager.createNotificationChannel(channel);

        notification = new Notification.Builder(this, CHANNELID).setOngoing(true)
                .setSmallIcon(R.drawable.ic_launcher)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true);

        startForeground(1002, notification.build());
        this.batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTemp = ((float) intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10);
                mLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                mStatus = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                mVolt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                isCharging = mStatus > 0;
                percentage = mLevel;

                BatteryWorker.voltage = mVolt + " mV";
                BatteryWorker.temperature = mTemp;

                if (intent.getAction().equals(Intent.ACTION_POWER_CONNECTED)) {
                    if (BatteryWorker.disableSync && !ContentResolver.getMasterSyncAutomatically())
                        ContentResolver.setMasterSyncAutomatically(true);
                } else if (intent.getAction().equals(Intent.ACTION_POWER_DISCONNECTED)) {
                    if (BatteryWorker.disableSync && ContentResolver.getMasterSyncAutomatically())
                        ContentResolver.setMasterSyncAutomatically(false);

                    if (BatteryWorker.autoReset) {
                        if (statsFile.exists())
                            ShellUtils.fastCmd("rm " + statsFile);
                    }
                }

                notification.setContentText("Temperature: " + mTemp + " °C");
                notificationManager.notify(1002, notification.build());
            }
        };
        this.registerReceiver(this.batteryReceiver, ifilter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.unregisterReceiver(this.batteryReceiver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        context = this;

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
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
                mHandler.postDelayed(this, 3000);
            }
        };
        mHandler.post(runnable);

        return super.onStartCommand(intent, flags, startId);
    }
}
