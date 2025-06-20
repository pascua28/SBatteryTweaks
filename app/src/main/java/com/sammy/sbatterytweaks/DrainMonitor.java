package com.sammy.sbatterytweaks;

import android.content.Context;
import android.os.BatteryManager;
import android.os.SystemClock;

public class DrainMonitor {
    private static float lastBatteryLevel = -1;
    public static float batteryPct;
    private static long lastUpdateTime = 0;
    private static boolean screenOn = true;
    private static float screenOnDrainRate = 0;
    private static float screenOffDrainRate = 0;
    private static int screenOnCount = 0;
    private static int screenOffCount = 0;
    private static float averageDrainRate = 0;
    private static int averageCount = 0;
    private static boolean ignoreNextDrop = false;
    private static float lastValidLevel = -1;
    private static boolean initialDropHandled = false;
    private static boolean isCharging = false;

    public static float getScreenOnDrainRate() {
        return round(screenOnDrainRate);
    }

    public static float getScreenOffDrainRate() {
        return round(screenOffDrainRate);
    }

    private static float round(float value) {
        return (float) (Math.round(value * 100) / 100.0);
    }

    public static void handleBatteryChange(Context context,
                                           int ratedCapacity, boolean chargingStatus) {
        if (getCounter(context) < 0) return;

        batteryPct = ((getCounter(context) / 1000f) / ratedCapacity) * 100;
        boolean wasCharging = isCharging;
        isCharging = chargingStatus;

        if (wasCharging != isCharging) {
            if (isCharging) {
                resetStats();
            } else {
                lastBatteryLevel = batteryPct;
                lastValidLevel = batteryPct;
                lastUpdateTime = SystemClock.elapsedRealtime();
                initialDropHandled = false;
                ignoreNextDrop = true;
            }
            return;
        }

        if (isCharging) return;

        if (lastBatteryLevel == -1) {
            lastBatteryLevel = batteryPct;
            lastValidLevel = batteryPct;
            lastUpdateTime = SystemClock.elapsedRealtime();
            return;
        }

        float pctDropped = lastBatteryLevel - batteryPct;

        if (pctDropped > 0) {
            if (ignoreNextDrop) {
                lastBatteryLevel = batteryPct;
                lastValidLevel = batteryPct;
                lastUpdateTime = SystemClock.elapsedRealtime();
                ignoreNextDrop = false;
                initialDropHandled = true;
                return;
            }

            long now = SystemClock.elapsedRealtime();
            long timeElapsed = now - lastUpdateTime;

            if (timeElapsed > 0 && initialDropHandled) {
                float hoursElapsed = timeElapsed / (1000f * 60f * 60f);
                float currentDrainRate = pctDropped / hoursElapsed;

                if (averageCount == 0) {
                    averageDrainRate = currentDrainRate;
                } else {
                    averageDrainRate = (averageDrainRate * averageCount + currentDrainRate) / (averageCount + 1);
                }
                averageCount++;

                if (screenOn) {
                    if (screenOnCount == 0) {
                        screenOnDrainRate = currentDrainRate;
                    } else {
                        screenOnDrainRate = (screenOnDrainRate * screenOnCount + currentDrainRate) / (screenOnCount + 1);
                    }
                    screenOnCount++;
                } else {
                    if (screenOffCount == 0) {
                        screenOffDrainRate = currentDrainRate;
                    } else {
                        screenOffDrainRate = (screenOffDrainRate * screenOffCount + currentDrainRate) / (screenOffCount + 1);
                    }
                    screenOffCount++;
                }
            }

            lastBatteryLevel = batteryPct;
            lastValidLevel = batteryPct;
            lastUpdateTime = now;
            initialDropHandled = true;
        } else if (batteryPct > lastBatteryLevel) {
            resetStats();
            lastBatteryLevel = batteryPct;
            lastValidLevel = batteryPct;
            lastUpdateTime = SystemClock.elapsedRealtime();
        }
    }

    public static void handleScreenChange(boolean newScreenOn) {
        if (screenOn != newScreenOn) {
            screenOn = newScreenOn;
            ignoreNextDrop = true;
            initialDropHandled = false;
            lastBatteryLevel = lastValidLevel;
            lastUpdateTime = SystemClock.elapsedRealtime();
        }
    }

    public static void resetStats() {
        lastBatteryLevel = -1;
        lastValidLevel = -1;
        lastUpdateTime = 0;
        initialDropHandled = false;
        averageDrainRate = 0f;
        screenOnDrainRate = 0f;
        screenOffDrainRate = 0f;
        averageCount = 0;
        screenOnCount = 0;
        screenOffCount = 0;
        ignoreNextDrop = false;
    }

    private static int getCounter(Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
    }
}
