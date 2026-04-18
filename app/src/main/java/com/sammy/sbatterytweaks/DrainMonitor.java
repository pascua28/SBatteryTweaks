package com.sammy.sbatterytweaks;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.Locale;

public class DrainMonitor {
    private static final String PREF_NAME = "DrainStatsPrefs";
    private static final Object LOCK = new Object();

    private static int lastChargeCounter = 0;
    private static long lastSampleTime = 0L;
    private static long pendingElapsedMs = 0L;
    private static boolean screenOn = true;
    private static boolean hasBaseline = false;

    private static float totalScreenOnDeltaPct = 0f;
    private static long totalScreenOnElapsedMs = 0L;

    private static float totalScreenOffDeltaPct = 0f;
    private static long totalScreenOffElapsedMs = 0L;

    private static float totalChargingDeltaPct = 0f;
    private static long totalChargingElapsedMs = 0L;

    public static void handleChargeCounterChange(Context context, int chargeCounterUah) {
        synchronized (LOCK) {
            if (chargeCounterUah <= 0) return;

            long now = SystemClock.elapsedRealtime();

            if (!hasBaseline) {
                lastChargeCounter = chargeCounterUah;
                lastSampleTime = now;
                pendingElapsedMs = 0L;
                hasBaseline = true;
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

            int divisor = BatteryReceiver.divisor;
            if (divisor <= 0) return;

            float deltaPct = deltaUah / (divisor * 10f);
            if (deltaPct <= 0f) return;

            if (BatteryReceiver.isCharging()) {
                totalChargingDeltaPct += deltaPct;
                totalChargingElapsedMs += totalElapsedMs;
            } else if (screenOn) {
                totalScreenOnDeltaPct += deltaPct;
                totalScreenOnElapsedMs += totalElapsedMs;
            } else {
                totalScreenOffDeltaPct += deltaPct;
                totalScreenOffElapsedMs += totalElapsedMs;
            }

            persistStats(context);
        }
    }

    public static void handleScreenChange(boolean newScreenOn) {
        synchronized (LOCK) {
            screenOn = newScreenOn;
            lastChargeCounter = 0;
            lastSampleTime = 0L;
            pendingElapsedMs = 0L;
            hasBaseline = false;
        }
    }

    public static void resetStats(Context context) {
        synchronized (LOCK) {
            lastChargeCounter = 0;
            lastSampleTime = 0L;
            pendingElapsedMs = 0L;
            hasBaseline = false;

            totalScreenOnDeltaPct = 0f;
            totalScreenOnElapsedMs = 0L;
            totalScreenOffDeltaPct = 0f;
            totalScreenOffElapsedMs = 0L;
            totalChargingDeltaPct = 0f;
            totalChargingElapsedMs = 0L;

            persistStats(context);
        }
    }

    public static float getChargingRate() {
        synchronized (LOCK) {
            return computeRate(totalChargingDeltaPct, totalChargingElapsedMs);
        }
    }

    public static float getScreenOnDrainRate() {
        synchronized (LOCK) {
            return computeRate(totalScreenOnDeltaPct, totalScreenOnElapsedMs);
        }
    }

    public static float getScreenOffDrainRate() {
        synchronized (LOCK) {
            return computeRate(totalScreenOffDeltaPct, totalScreenOffElapsedMs);
        }
    }

    public static String getChargingDetail(Context context) {
        synchronized (LOCK) {
            return buildDetail(context, true, totalChargingDeltaPct, totalChargingElapsedMs);
        }
    }

    public static String getScreenOnDetail(Context context) {
        synchronized (LOCK) {
            return buildDetail(context, false, totalScreenOnDeltaPct, totalScreenOnElapsedMs);
        }
    }

    public static String getScreenOffDetail(Context context) {
        synchronized (LOCK) {
            return buildDetail(context, false, totalScreenOffDeltaPct, totalScreenOffElapsedMs);
        }
    }

    private static float computeRate(float deltaPct, long elapsedMs) {
        if (elapsedMs <= 0L) return 0f;

        float elapsedHours = elapsedMs / 3600000f;
        if (elapsedHours <= 0f) return 0f;

        return (deltaPct / elapsedHours);
    }

    private static String buildDetail(Context context, boolean charging, float deltaPct, long elapsedMs) {
        if (elapsedMs < 1000L && deltaPct < 0.3f) {
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

    private static void persistStats(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putFloat("total_screen_on_delta_pct", totalScreenOnDeltaPct)
                .putLong("total_screen_on_elapsed_ms", totalScreenOnElapsedMs)
                .putFloat("total_screen_off_delta_pct", totalScreenOffDeltaPct)
                .putLong("total_screen_off_elapsed_ms", totalScreenOffElapsedMs)
                .putFloat("total_charging_delta_pct", totalChargingDeltaPct)
                .putLong("total_charging_elapsed_ms", totalChargingElapsedMs)
                .apply();
    }
}