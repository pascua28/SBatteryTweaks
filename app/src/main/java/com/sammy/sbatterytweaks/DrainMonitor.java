package com.sammy.sbatterytweaks;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

public class DrainMonitor {
    public static float batteryPct;
    private static float lastBatteryLevel = -1;
    private static long lastUpdateTime = 0;
    private static boolean screenOn = true;
    private static boolean ignoreNextDrop = false;
    private static float lastValidLevel = -1;
    private static boolean initialDropHandled = false;
    private static boolean isCharging = false;

    private static final String PREF_NAME = "DrainStatsPrefs";
    private static final String KEY_SCREEN_ON_DRAINS = "screen_on_drains";
    private static final String KEY_SCREEN_OFF_DRAINS = "screen_off_drains";

    private static List<Float> screenOnDrains = new ArrayList<>();
    private static List<Float> screenOffDrains = new ArrayList<>();

    public static void handleBatteryChange(Context context, int divisor, int counter, boolean chargingStatus) {
        if (divisor <= 0 || counter < 0) return;

        batteryPct = ((counter / 1000f) / divisor) * 100;
        boolean wasCharging = isCharging;
        isCharging = chargingStatus;

        if (wasCharging != isCharging) {
            if (isCharging) {
                resetStats(context);
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

                if (screenOn) {
                    screenOnDrains.add(currentDrainRate);
                } else {
                    screenOffDrains.add(currentDrainRate);
                }

                persistStats(context);
            }

            lastBatteryLevel = batteryPct;
            lastValidLevel = batteryPct;
            lastUpdateTime = now;
            initialDropHandled = true;
        } else if (batteryPct > lastBatteryLevel) {
            resetStats(context);
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

    public static void resetStats(Context context) {
        lastBatteryLevel = -1;
        lastValidLevel = -1;
        lastUpdateTime = 0;
        initialDropHandled = false;
        ignoreNextDrop = false;
        isCharging = false;

        screenOnDrains.clear();
        screenOffDrains.clear();

        persistStats(context);
    }

    public static float getScreenOnDrainRate() {
        return round(average(screenOnDrains));
    }

    public static float getScreenOffDrainRate() {
        return round(average(screenOffDrains));
    }

    private static float average(List<Float> list) {
        if (list.isEmpty()) return 0;
        float sum = 0;
        for (float val : list) sum += val;
        return sum / list.size();
    }

    private static float round(float value) {
        return (float) (Math.round(value * 100) / 100.0);
    }

    private static void saveFloatList(Context context, String key, List<Float> list) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        StringBuilder builder = new StringBuilder();
        for (float value : list) {
            builder.append(value).append(",");
        }

        editor.putString(key, builder.toString());
        editor.apply();
    }

    private static void persistStats(Context context) {
        saveFloatList(context, KEY_SCREEN_ON_DRAINS, screenOnDrains);
        saveFloatList(context, KEY_SCREEN_OFF_DRAINS, screenOffDrains);
    }
}
