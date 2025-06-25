package com.sammy.sbatterytweaks;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

import com.topjohnwu.superuser.ShellUtils;

import java.io.File;

public class BatteryReceiver extends BroadcastReceiver {
    public static int mLevel, mVolt, divisor = -1;
    public static float mTemp;
    private static int mPlugged, mStatus;
    private final File statsFile = new File("/data/system/batterystats.bin");

    private String activeDrain = "", idleDrain = "";

    public static boolean isCharging() {
        return mPlugged > 0;
    }

    public static boolean notCharging() {
        return mPlugged > 0 && mStatus == BatteryManager.BATTERY_STATUS_NOT_CHARGING;
    }

    public static int getCounter(Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
    }

    private static int getDivisor(Context context) {
        int batteryLevel = BatteryReceiver.mLevel;
        int counter = getCounter(context);

        int lowerBound = counter / (batteryLevel + 1) / 10;
        int upperBound = counter / batteryLevel / 10;

        int bestDivisor = 0;
        int lowestDigitCount = Integer.MAX_VALUE;

        for (int testDivisor = lowerBound; testDivisor <= upperBound; testDivisor++) {
            float testValue = counter / (testDivisor * 10.0f);
            int digitCount = String.valueOf(testValue).length();

            if (digitCount < lowestDigitCount) {
                lowestDigitCount = digitCount;
                bestDivisor = testDivisor;
            }
        }

        return bestDivisor;
    }

    private static int getStableDivisor(Context context) {
        int consecutiveMatches = 0;
        int lastDivisor = -1;
        int currentDivisor;
        int maxIterations = 100;
        int iterations = 0;

        while (consecutiveMatches < 3 && iterations < maxIterations) {
            iterations++;
            currentDivisor = getDivisor(context);

            if (currentDivisor == lastDivisor) {
                consecutiveMatches++;
            } else {
                consecutiveMatches = 1;
                lastDivisor = currentDivisor;
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return lastDivisor;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        mTemp = ((float) intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10);
        mLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        mPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        mStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        mVolt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);

        BatteryWorker.fetchUpdates(context);
        BatteryWorker.updateStats(context, isCharging());

        if (divisor <= 0)
            divisor = getStableDivisor(context);


        if (intent.getAction().equals(Intent.ACTION_POWER_CONNECTED)) {
            if (BatteryWorker.disableSync && !ContentResolver.getMasterSyncAutomatically())
                ContentResolver.setMasterSyncAutomatically(true);

            BatteryService.startBackgroundTask(context);
        } else if (intent.getAction().equals(Intent.ACTION_POWER_DISCONNECTED)) {
            if (BatteryWorker.disableSync && ContentResolver.getMasterSyncAutomatically())
                ContentResolver.setMasterSyncAutomatically(false);

            if (BatteryWorker.autoReset) {
                if (statsFile.exists())
                    ShellUtils.fastCmd("rm " + statsFile);
            }

            if (BatteryService.isBypassed == 1) {
                BatteryWorker.setBypass(context, 0);
                BatteryService.manualBypass = false;
            }

            if (!MainActivity.isRunning && !BatteryService.drainMonitorEnabled) {
                BatteryService.stopBackgroundTask();
            }
        }

        if (MainActivity.isRunning) {
            MainActivity.updateWaves(mLevel);
        }

        if (isCharging() || !BatteryService.drainMonitorEnabled) {
            activeDrain = "";
            idleDrain = "";
        } else {
            if (DrainMonitor.getScreenOnDrainRate() > 0.0f)
                activeDrain = context.getString(R.string.active_drain, DrainMonitor.getScreenOnDrainRate());

            if (DrainMonitor.getScreenOffDrainRate() > 0.0f)
                idleDrain = context.getString(R.string.idle_drain, DrainMonitor.getScreenOffDrainRate());
        }

        BatteryService.updateNotif(context.getString(R.string.temperature_title) + getTemp() + " °C",
                activeDrain, idleDrain);
    }

    private float getTemp() {
        return mTemp;
    }
}
