package com.sammy.sbatterytweaks;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

public class DrainMonitor {
    private static final String PREF_NAME = "DrainStatsPrefs";
    private static final String KEY_SCREEN_ON = "screen_on_drains";
    private static final String KEY_SCREEN_OFF = "screen_off_drains";
    // Store %/h samples
    private static final List<Float> screenOnDrains = new ArrayList<>();
    private static final List<Float> screenOffDrains = new ArrayList<>();
    private static int lastChargeCounter = Integer.MIN_VALUE;
    private static long lastSampleTime = 0L;
    private static boolean screenOn = true;
    private static boolean ignoreNextSample = false;

    public static void handleChargeCounterChange(Context context, int chargeCounterUah) {
        if (chargeCounterUah <= 0) return;

        long now = SystemClock.elapsedRealtime();

        if (lastChargeCounter == Integer.MIN_VALUE) {
            lastChargeCounter = chargeCounterUah;
            lastSampleTime = now;
            return;
        }

        if (ignoreNextSample) {
            lastChargeCounter = chargeCounterUah;
            lastSampleTime = now;
            ignoreNextSample = false;
            return;
        }

        int deltaUah = lastChargeCounter - chargeCounterUah;
        long elapsedMs = now - lastSampleTime;

        lastChargeCounter = chargeCounterUah;
        lastSampleTime = now;

        if (elapsedMs <= 0 || deltaUah <= 0) return;

        // Noise filter
        if (deltaUah < 1000) return;

        float elapsedHours = elapsedMs / 3600000f;

        float drainUahPerHour = deltaUah / elapsedHours;

        int divisor = BatteryReceiver.divisor;
        if (divisor <= 0) return;

        float drainPctPerHour = (drainUahPerHour / divisor) / 10f;

        if (drainPctPerHour <= 0f) return;

        if (screenOn) {
            screenOnDrains.add(drainPctPerHour);
        } else {
            screenOffDrains.add(drainPctPerHour);
        }

        persistStats(context);
    }

    public static void handleScreenChange(boolean newScreenOn) {
        if (screenOn != newScreenOn) {
            screenOn = newScreenOn;
            ignoreNextSample = true;
        }
    }

    public static void resetStats(Context context) {
        lastChargeCounter = Integer.MIN_VALUE;
        lastSampleTime = 0L;
        ignoreNextSample = false;

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
        if (list.isEmpty()) return 0f;

        float sum = 0f;
        for (float value : list) {
            sum += value;
        }
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
        saveFloatList(context, KEY_SCREEN_ON, screenOnDrains);
        saveFloatList(context, KEY_SCREEN_OFF, screenOffDrains);
    }
}