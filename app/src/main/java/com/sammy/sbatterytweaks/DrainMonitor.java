package com.sammy.sbatterytweaks;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DrainMonitor {
    private static final String PREF_NAME = "DrainStatsPrefs";
    private static final String KEY_SCREEN_ON = "screen_on_drains";
    private static final String KEY_SCREEN_OFF = "screen_off_drains";
    private static final String KEY_CHARGE_RATE = "charging_rate";

    private static final List<Float> screenOnDrains = new ArrayList<>();
    private static final List<Float> screenOffDrains = new ArrayList<>();
    private static final List<Float> chargingRates = new ArrayList<>();

    private static int lastChargeCounter = Integer.MIN_VALUE;
    private static long lastSampleTime = 0L;
    private static long pendingElapsedMs = 0L;
    private static boolean screenOn = true;
    private static boolean ignoreNextSample = false;

    private static float totalScreenOnDeltaPct = 0f;
    private static long totalScreenOnElapsedMs = 0L;

    private static float totalScreenOffDeltaPct = 0f;
    private static long totalScreenOffElapsedMs = 0L;

    private static float totalChargingDeltaPct = 0f;
    private static long totalChargingElapsedMs = 0L;

    public static void handleChargeCounterChange(Context context, int chargeCounterUah) {
        if (chargeCounterUah <= 0) return;

        long now = SystemClock.elapsedRealtime();

        if (lastChargeCounter == Integer.MIN_VALUE) {
            lastChargeCounter = chargeCounterUah;
            lastSampleTime = now;
            pendingElapsedMs = 0L;
            return;
        }

        if (ignoreNextSample) {
            lastChargeCounter = chargeCounterUah;
            lastSampleTime = now;
            pendingElapsedMs = 0L;
            ignoreNextSample = false;
            return;
        }

        long elapsedMs = now - lastSampleTime;
        if (elapsedMs <= 0) return;

        int deltaUah = Math.abs(lastChargeCounter - chargeCounterUah);
        lastSampleTime = now;

        if (deltaUah == 0) {
            pendingElapsedMs += elapsedMs;
            return;
        }

        long totalElapsedMs = pendingElapsedMs + elapsedMs;
        pendingElapsedMs = 0L;
        lastChargeCounter = chargeCounterUah;

        if (totalElapsedMs <= 0) return;

        float elapsedHours = totalElapsedMs / 3600000f;
        float drainUahPerHour = deltaUah / elapsedHours;

        int divisor = BatteryReceiver.divisor;
        if (divisor <= 0) return;

        float deltaPct = deltaUah / (divisor * 10f);
        float drainPctPerHour = (drainUahPerHour / divisor) / 10f;

        if (drainPctPerHour <= 0f) return;

        if (BatteryReceiver.isCharging()) {
            chargingRates.add(drainPctPerHour);
            totalChargingDeltaPct += deltaPct;
            totalChargingElapsedMs += totalElapsedMs;
        } else if (screenOn) {
            screenOnDrains.add(drainPctPerHour);
            totalScreenOnDeltaPct += deltaPct;
            totalScreenOnElapsedMs += totalElapsedMs;
        } else {
            screenOffDrains.add(drainPctPerHour);
            totalScreenOffDeltaPct += deltaPct;
            totalScreenOffElapsedMs += totalElapsedMs;
        }

        persistStats(context);
    }

    public static void handleScreenChange(boolean newScreenOn) {
        if (screenOn != newScreenOn) {
            screenOn = newScreenOn;
            ignoreNextSample = true;
            pendingElapsedMs = 0L;
        }
    }

    public static void resetStats(Context context) {
        lastChargeCounter = Integer.MIN_VALUE;
        lastSampleTime = 0L;
        pendingElapsedMs = 0L;
        ignoreNextSample = false;

        totalScreenOnDeltaPct = 0f;
        totalScreenOnElapsedMs = 0L;
        totalScreenOffDeltaPct = 0f;
        totalScreenOffElapsedMs = 0L;
        totalChargingDeltaPct = 0f;
        totalChargingElapsedMs = 0L;

        screenOnDrains.clear();
        screenOffDrains.clear();
        chargingRates.clear();

        persistStats(context);
    }

    public static float getChargingRate() {
        return round(average(chargingRates));
    }

    public static float getScreenOnDrainRate() {
        return round(average(screenOnDrains));
    }

    public static float getScreenOffDrainRate() {
        return round(average(screenOffDrains));
    }

    public static String getChargingDetail(Context context) {
        return buildDetail(context, true, totalChargingDeltaPct, totalChargingElapsedMs);
    }

    public static String getScreenOnDetail(Context context) {
        return buildDetail(context, false, totalScreenOnDeltaPct, totalScreenOnElapsedMs);
    }

    public static String getScreenOffDetail(Context context) {
        return buildDetail(context, false, totalScreenOffDeltaPct, totalScreenOffElapsedMs);
    }

    private static String buildDetail(Context context, boolean charging, float deltaPct, long elapsedMs) {
        if (elapsedMs < 1000L) {
            return "";
        }

        if (deltaPct < 0.99f) {
            return "";
        }

        String sign = context.getString(
                charging ? R.string.drain_sign_positive : R.string.drain_sign_negative
        );

        return context.getString(
                R.string.drain_detail,
                sign,
                deltaPct,
                formatElapsed(elapsedMs)
        );
    }

    private static String formatElapsed(long elapsedMs) {
        long totalSeconds = elapsedMs / 1000L;

        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
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
        saveFloatList(context, KEY_CHARGE_RATE, chargingRates);
    }
}