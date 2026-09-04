package com.sammy.sbatterytweaks;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.ArrayDeque;
import java.util.Locale;

public class DrainMonitor {
    private static final String PREF_NAME = "DrainStatsPrefs";

    private static final Object LOCK = new Object();

    // ---------------------------------------------------------------------
    // Sampling
    // ---------------------------------------------------------------------

    private static final long MIN_SAMPLE_INTERVAL_MS = 1_000L;
    private static final long MIN_VALID_ELAPSED_MS = 20_000L;

    // ---------------------------------------------------------------------
    // Reporting thresholds
    // ---------------------------------------------------------------------

    private static final int SCREEN_ON_INITIAL_TENTHS = 10;
    private static final int SCREEN_OFF_INITIAL_TENTHS = 4;
    private static final int CHARGING_INITIAL_TENTHS = 10;

    private static final int REPORT_INTERVAL_TENTHS = 1;

    // ---------------------------------------------------------------------
    // Capacity regression
    // ---------------------------------------------------------------------
    private static final int MAX_REGRESSION_SAMPLES = 100;
    private static final int MIN_REGRESSION_SAMPLES = 2;

    private static final int MIN_BATTERY_LEVEL = 1;
    private static final int MAX_BATTERY_LEVEL = 100;

    /*
     * Sanity limits for the calculated battery capacity.
     */
    private static final long MIN_CAPACITY_UAH = 500_000L;
    private static final long MAX_CAPACITY_UAH = 20_000_000L;

    // ---------------------------------------------------------------------
    // SharedPreferences keys
    // ---------------------------------------------------------------------

    private static final String KEY_SCREEN_ON_DELTA =
            "total_screen_on_delta_pct";

    private static final String KEY_SCREEN_ON_ELAPSED =
            "total_screen_on_elapsed_ms";

    private static final String KEY_SCREEN_OFF_DELTA =
            "total_screen_off_delta_pct";

    private static final String KEY_SCREEN_OFF_ELAPSED =
            "total_screen_off_elapsed_ms";

    private static final String KEY_CHARGING_DELTA =
            "total_charging_delta_pct";

    private static final String KEY_CHARGING_ELAPSED =
            "total_charging_elapsed_ms";

    private static final String KEY_CHARGING_SAMPLES =
            "charging_regression_samples";

    private static final String KEY_DISCHARGING_SAMPLES =
            "discharging_regression_samples";

    // ---------------------------------------------------------------------
    // Context
    // ---------------------------------------------------------------------

    private static Context appContext;

    // ---------------------------------------------------------------------
    // Runtime baseline
    // ---------------------------------------------------------------------

    private static int lastChargeCounter;
    private static long lastSampleTime;
    private static long pendingElapsedMs;

    private static boolean hasBaseline;

    private static boolean baselineScreenOn = true;
    private static boolean baselineCharging;

    private static boolean screenOn = true;

    private static boolean discardNextDischargeSample = false;

    // ---------------------------------------------------------------------
    // Accumulated statistics
    // ---------------------------------------------------------------------

    private static float totalScreenOnDeltaPct;
    private static long totalScreenOnElapsedMs;

    private static float totalScreenOffDeltaPct;
    private static long totalScreenOffElapsedMs;

    private static float totalChargingDeltaPct;
    private static long totalChargingElapsedMs;

    private static float screenOnRate;
    private static float screenOffRate;
    private static float chargingRate;

    private static int screenOnLastReportedTenths;
    private static int screenOffLastReportedTenths;
    private static int chargingLastReportedTenths;

    // ---------------------------------------------------------------------
    // Regression windows
    // ---------------------------------------------------------------------

    private static final ArrayDeque<CapacitySample> chargingSamples =
            new ArrayDeque<>(MAX_REGRESSION_SAMPLES);

    private static final ArrayDeque<CapacitySample> dischargingSamples =
            new ArrayDeque<>(MAX_REGRESSION_SAMPLES);

    private static long chargingCapacityUah;
    private static long dischargingCapacityUah;

    // ---------------------------------------------------------------------
    // Initialization
    // ---------------------------------------------------------------------

    public static void init(Context context) {
        if (context == null) {
            return;
        }

        synchronized (LOCK) {
            if (appContext != null) {
                return;
            }

            appContext = context.getApplicationContext();
            loadStatsLocked();
        }
    }

    // ---------------------------------------------------------------------
    // Battery counter update
    // ---------------------------------------------------------------------

    public static void handleChargeCounterChange(
            Context context,
            int chargeCounterUah
    ) {
        if (chargeCounterUah <= 0) {
            return;
        }

        synchronized (LOCK) {
            ensureContextLocked(context);

            final long now = SystemClock.elapsedRealtime();
            final boolean charging = BatteryReceiver.isCharging();

            if (!hasBaseline) {
                setBaselineLocked(
                        chargeCounterUah,
                        now,
                        screenOn,
                        charging
                );
                return;
            }

            final long elapsedMs = now - lastSampleTime;

            if (elapsedMs < MIN_SAMPLE_INTERVAL_MS) {
                return;
            }

            if (baselineScreenOn != screenOn
                    || baselineCharging != charging) {

                setBaselineLocked(
                        chargeCounterUah,
                        now,
                        screenOn,
                        charging
                );
                return;
            }

            if (elapsedMs <= 0L) {
                return;
            }

            final long deltaUah = Math.abs(
                    (long) lastChargeCounter - chargeCounterUah
            );

            lastChargeCounter = chargeCounterUah;
            lastSampleTime = now;

            if (!charging && discardNextDischargeSample) {
                discardNextDischargeSample = false;
                pendingElapsedMs = 0L;
                return;
            }

            if (deltaUah == 0L) {
                pendingElapsedMs += elapsedMs;
                return;
            }

            final long totalElapsedMs =
                    pendingElapsedMs + elapsedMs;

            pendingElapsedMs = 0L;

            if (totalElapsedMs <= 0L) {
                return;
            }

            final long capacityUah =
                    charging ? chargingCapacityUah : dischargingCapacityUah;

            if (capacityUah <= 0L) {
                return;
            }

            final float deltaPct =
                    deltaUah * 100f / capacityUah;

            if (deltaPct <= 0f) {
                return;
            }

            addAccumulatorLocked(
                    charging,
                    screenOn,
                    deltaPct,
                    totalElapsedMs
            );

            updateReportedRateLocked(
                    charging,
                    screenOn
            );

            persistStatsLocked();
        }
    }

    // ---------------------------------------------------------------------
    // Screen state
    // ---------------------------------------------------------------------

    public static void handleScreenChange(
            boolean newScreenOn
    ) {
        synchronized (LOCK) {
            if (screenOn == newScreenOn) {
                return;
            }

            screenOn = newScreenOn;
            discardNextDischargeSample = true;

            if (lastChargeCounter > 0) {
                setBaselineLocked(
                        lastChargeCounter,
                        SystemClock.elapsedRealtime(),
                        screenOn,
                        BatteryReceiver.isCharging()
                );
            } else {
                clearBaselineLocked();
            }
        }
    }

    // ---------------------------------------------------------------------
    // Reset
    // ---------------------------------------------------------------------

    public static void resetStats(Context context) {
        synchronized (LOCK) {
            clearBaselineLocked();

            baselineScreenOn = true;
            baselineCharging = false;
            screenOn = true;

            totalScreenOnDeltaPct = 0f;
            totalScreenOnElapsedMs = 0L;

            totalScreenOffDeltaPct = 0f;
            totalScreenOffElapsedMs = 0L;

            totalChargingDeltaPct = 0f;
            totalChargingElapsedMs = 0L;

            screenOnRate = 0f;
            screenOffRate = 0f;
            chargingRate = 0f;

            screenOnLastReportedTenths = 0;
            screenOffLastReportedTenths = 0;
            chargingLastReportedTenths = 0;

            ensureContextLocked(context);
            persistStatsLocked();
        }
    }

    public static void recordRegressionSample(Context context, int batteryLevel, int chargeCounterUah, boolean charging) {
        if (context == null || batteryLevel < MIN_BATTERY_LEVEL || batteryLevel > MAX_BATTERY_LEVEL || chargeCounterUah <= 0) {
            return;
        }

        synchronized (LOCK) {
            ensureContextLocked(context);

            final ArrayDeque<CapacitySample> samples = charging ? chargingSamples : dischargingSamples;

            if (!samples.isEmpty()) {
                final CapacitySample last = samples.peekLast();
                if (charging && batteryLevel < last.level) {
                    return;
                }
                if (!charging && batteryLevel > last.level) {
                    return;
                }
            }

            samples.addLast(new CapacitySample(batteryLevel, chargeCounterUah));

            while (samples.size() > MAX_REGRESSION_SAMPLES) {
                samples.removeFirst();
            }

            long capacity = calculateRegressionCapacity(samples);
            if (capacity > 0L) {
                if (charging) {
                    chargingCapacityUah = capacity;
                } else {
                    dischargingCapacityUah = capacity;
                }
            }
            persistStatsLocked();
        }
    }

    // ---------------------------------------------------------------------
    // Rate getters
    // ---------------------------------------------------------------------

    public static float getChargingRate() {
        synchronized (LOCK) {
            return chargingRate;
        }
    }

    public static float getScreenOnDrainRate() {
        synchronized (LOCK) {
            return screenOnRate;
        }
    }

    public static float getScreenOffDrainRate() {
        synchronized (LOCK) {
            return screenOffRate;
        }
    }

    // ---------------------------------------------------------------------
    // Detail getters
    // ---------------------------------------------------------------------

    public static String getChargingDetail(Context context) {
        synchronized (LOCK) {
            return buildDetailLocked(
                    context,
                    true,
                    totalChargingDeltaPct,
                    totalChargingElapsedMs,
                    chargingRate
            );
        }
    }

    public static String getScreenOnDetail(Context context) {
        synchronized (LOCK) {
            return buildDetailLocked(
                    context,
                    false,
                    totalScreenOnDeltaPct,
                    totalScreenOnElapsedMs,
                    screenOnRate
            );
        }
    }

    public static String getScreenOffDetail(Context context) {
        synchronized (LOCK) {
            return buildDetailLocked(
                    context,
                    false,
                    totalScreenOffDeltaPct,
                    totalScreenOffElapsedMs,
                    screenOffRate
            );
        }
    }

    // ---------------------------------------------------------------------
    // Baseline
    // ---------------------------------------------------------------------

    private static void setBaselineLocked(
            int chargeCounter,
            long time,
            boolean screenState,
            boolean chargingState
    ) {
        lastChargeCounter = chargeCounter;
        lastSampleTime = time;
        pendingElapsedMs = 0L;

        baselineScreenOn = screenState;
        baselineCharging = chargingState;

        hasBaseline = true;
    }

    private static void clearBaselineLocked() {
        lastChargeCounter = 0;
        lastSampleTime = 0L;
        pendingElapsedMs = 0L;
        hasBaseline = false;
    }

    // ---------------------------------------------------------------------
    // Accumulators
    // ---------------------------------------------------------------------

    private static void addAccumulatorLocked(
            boolean charging,
            boolean screenState,
            float deltaPct,
            long elapsedMs
    ) {
        if (charging) {
            totalChargingDeltaPct += deltaPct;
            totalChargingElapsedMs += elapsedMs;
        } else if (screenState) {
            totalScreenOnDeltaPct += deltaPct;
            totalScreenOnElapsedMs += elapsedMs;
        } else {
            totalScreenOffDeltaPct += deltaPct;
            totalScreenOffElapsedMs += elapsedMs;
        }
    }

    // ---------------------------------------------------------------------
    // Reporting
    // ---------------------------------------------------------------------

    private static void updateReportedRateLocked(
            boolean charging,
            boolean screenState
    ) {
        final ReportType type;
        final float deltaPct;
        final long elapsedMs;

        final int regressionSampleCount = charging
                ? chargingSamples.size()
                : dischargingSamples.size();

        if (regressionSampleCount < MIN_REGRESSION_SAMPLES) {
            return;
        }

        if (charging) {
            type = ReportType.CHARGING;
            deltaPct = totalChargingDeltaPct;
            elapsedMs = totalChargingElapsedMs;
        } else if (screenState) {
            type = ReportType.SCREEN_ON;
            deltaPct = totalScreenOnDeltaPct;
            elapsedMs = totalScreenOnElapsedMs;
        } else {
            type = ReportType.SCREEN_OFF;
            deltaPct = totalScreenOffDeltaPct;
            elapsedMs = totalScreenOffElapsedMs;
        }

        if (deltaPct <= 0f
                || elapsedMs < MIN_VALID_ELAPSED_MS) {
            return;
        }

        final int accumulatedTenths =
                (int) (deltaPct * 10f);

        final int initialThreshold =
                getInitialThreshold(type);

        // Wait for the initial threshold.
        if (accumulatedTenths < initialThreshold) {
            return;
        }

        final int lastReported =
                getLastReportedTenths(type);

        if (lastReported == 0) {
            setLastReportedTenths(
                    type,
                    initialThreshold
            );

            setReportedRate(
                    type,
                    calculateRate(deltaPct, elapsedMs)
            );

            return;
        }

        if (accumulatedTenths
                < lastReported + REPORT_INTERVAL_TENTHS) {
            return;
        }

        final int newBoundary =
                initialThreshold
                        + (
                        (accumulatedTenths - initialThreshold)
                                / REPORT_INTERVAL_TENTHS
                )
                        * REPORT_INTERVAL_TENTHS;

        if (newBoundary <= lastReported) {
            return;
        }

        setLastReportedTenths(
                type,
                newBoundary
        );

        setReportedRate(
                type,
                calculateRate(deltaPct, elapsedMs)
        );
    }

    private static int getInitialThreshold(
            ReportType type
    ) {
        return switch (type) {
            case SCREEN_ON -> SCREEN_ON_INITIAL_TENTHS;
            case SCREEN_OFF -> SCREEN_OFF_INITIAL_TENTHS;
            case CHARGING -> CHARGING_INITIAL_TENTHS;
            default -> SCREEN_ON_INITIAL_TENTHS;
        };
    }

    private static int getLastReportedTenths(
            ReportType type
    ) {
        return switch (type) {
            case SCREEN_ON -> screenOnLastReportedTenths;
            case SCREEN_OFF -> screenOffLastReportedTenths;
            case CHARGING -> chargingLastReportedTenths;
            default -> 0;
        };
    }

    private static void setLastReportedTenths(
            ReportType type,
            int value
    ) {
        switch (type) {
            case SCREEN_ON:
                screenOnLastReportedTenths = value;
                break;

            case SCREEN_OFF:
                screenOffLastReportedTenths = value;
                break;

            case CHARGING:
                chargingLastReportedTenths = value;
                break;
        }
    }

    private static void setReportedRate(
            ReportType type,
            float rate
    ) {
        switch (type) {
            case SCREEN_ON:
                screenOnRate = rate;
                break;

            case SCREEN_OFF:
                screenOffRate = rate;
                break;

            case CHARGING:
                chargingRate = rate;
                break;
        }
    }

    // ---------------------------------------------------------------------
    // Regression
    // ---------------------------------------------------------------------

    private static long calculateRegressionCapacity(
            ArrayDeque<CapacitySample> samples
    ) {
        if (samples.size() < MIN_REGRESSION_SAMPLES) {
            return 0L;
        }

        /*
         * Least-squares linear regression:
         *
         *     y = slope * x + intercept
         *
         * where:
         *
         *     x = battery percentage
         *     y = charge counter in µAh
         *
         * slope = µAh per 1% battery
         *
         * capacity = slope * 100
         */

        double sumX = 0.0;
        double sumY = 0.0;
        double sumXY = 0.0;
        double sumXX = 0.0;

        final int n = samples.size();

        for (CapacitySample sample : samples) {
            final double x = sample.level;
            final double y = sample.chargeCounterUah;

            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }

        final double denominator =
                n * sumXX - sumX * sumX;

        if (Math.abs(denominator) < 0.000001) {
            return 0L;
        }

        final double slope =
                (n * sumXY - sumX * sumY)
                        / denominator;

        if (slope <= 0.0) {
            return 0L;
        }

        final double capacity =
                slope * 100.0;

        if (capacity < MIN_CAPACITY_UAH
                || capacity > MAX_CAPACITY_UAH) {
            return 0L;
        }

        return Math.round(capacity);
    }

    // ---------------------------------------------------------------------
    // Rate calculation
    // ---------------------------------------------------------------------

    private static float calculateRate(
            float deltaPct,
            long elapsedMs
    ) {
        if (deltaPct <= 0f
                || elapsedMs <= 0L) {
            return 0f;
        }

        final float elapsedHours =
                elapsedMs / 3_600_000f;

        if (elapsedHours <= 0f) {
            return 0f;
        }

        return deltaPct / elapsedHours;
    }

    // ---------------------------------------------------------------------
    // Details
    // ---------------------------------------------------------------------

    private static String buildDetailLocked(
            Context context,
            boolean charging,
            float deltaPct,
            long elapsedMs,
            float rate
    ) {
        if (context == null || rate <= 0f) {
            return "";
        }

        final int regressionSampleCount = charging
                ? chargingSamples.size()
                : dischargingSamples.size();

        if (regressionSampleCount < MIN_REGRESSION_SAMPLES) {
            return "";
        }

        final String sign =
                context.getString(
                        charging
                                ? R.string.drain_sign_positive
                                : R.string.drain_sign_negative
                );

        return context.getString(
                R.string.drain_detail,
                sign,
                deltaPct,
                formatElapsed(elapsedMs)
        );
    }

    private static String formatElapsed(
            long elapsedMs
    ) {
        final long totalSeconds =
                elapsedMs / 1_000L;

        final long hours =
                totalSeconds / 3_600L;

        final long minutes =
                (totalSeconds % 3_600L) / 60L;

        final long seconds =
                totalSeconds % 60L;

        return String.format(
                Locale.US,
                "%02d:%02d:%02d",
                hours,
                minutes,
                seconds
        );
    }

    // ---------------------------------------------------------------------
    // Context
    // ---------------------------------------------------------------------

    private static void ensureContextLocked(
            Context context
    ) {
        if (appContext == null
                && context != null) {
            appContext =
                    context.getApplicationContext();
        }
    }

    // ---------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------

    private static void persistStatsLocked() {
        if (appContext == null) {
            return;
        }

        final SharedPreferences prefs =
                appContext.getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                );

        prefs.edit()
                .putFloat(
                        KEY_SCREEN_ON_DELTA,
                        totalScreenOnDeltaPct
                )
                .putLong(
                        KEY_SCREEN_ON_ELAPSED,
                        totalScreenOnElapsedMs
                )
                .putFloat(
                        KEY_SCREEN_OFF_DELTA,
                        totalScreenOffDeltaPct
                )
                .putLong(
                        KEY_SCREEN_OFF_ELAPSED,
                        totalScreenOffElapsedMs
                )
                .putFloat(
                        KEY_CHARGING_DELTA,
                        totalChargingDeltaPct
                )
                .putLong(
                        KEY_CHARGING_ELAPSED,
                        totalChargingElapsedMs
                )
                .putString(
                        KEY_CHARGING_SAMPLES,
                        serializeSamples(chargingSamples)
                )
                .putString(
                        KEY_DISCHARGING_SAMPLES,
                        serializeSamples(dischargingSamples)
                )
                .apply();
    }

    private static void loadStatsLocked() {
        if (appContext == null) {
            return;
        }

        final SharedPreferences prefs =
                appContext.getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                );

        totalScreenOnDeltaPct =
                prefs.getFloat(
                        KEY_SCREEN_ON_DELTA,
                        0f
                );

        totalScreenOnElapsedMs =
                prefs.getLong(
                        KEY_SCREEN_ON_ELAPSED,
                        0L
                );

        totalScreenOffDeltaPct =
                prefs.getFloat(
                        KEY_SCREEN_OFF_DELTA,
                        0f
                );

        totalScreenOffElapsedMs =
                prefs.getLong(
                        KEY_SCREEN_OFF_ELAPSED,
                        0L
                );

        totalChargingDeltaPct =
                prefs.getFloat(
                        KEY_CHARGING_DELTA,
                        0f
                );

        totalChargingElapsedMs =
                prefs.getLong(
                        KEY_CHARGING_ELAPSED,
                        0L
                );

        chargingSamples.clear();
        dischargingSamples.clear();

        deserializeSamples(
                prefs.getString(
                        KEY_CHARGING_SAMPLES,
                        ""
                ),
                chargingSamples
        );

        deserializeSamples(
                prefs.getString(
                        KEY_DISCHARGING_SAMPLES,
                        ""
                ),
                dischargingSamples
        );

        chargingCapacityUah =
                calculateRegressionCapacity(
                        chargingSamples
                );

        dischargingCapacityUah =
                calculateRegressionCapacity(
                        dischargingSamples
                );

        clearBaselineLocked();

        screenOnRate = 0f;
        screenOffRate = 0f;
        chargingRate = 0f;

        screenOnLastReportedTenths = 0;
        screenOffLastReportedTenths = 0;
        chargingLastReportedTenths = 0;
    }

    private static String serializeSamples(
            ArrayDeque<CapacitySample> samples
    ) {
        final StringBuilder result =
                new StringBuilder();

        for (CapacitySample sample : samples) {
            if (result.length() > 0) {
                result.append(';');
            }

            result
                    .append(sample.level)
                    .append(',')
                    .append(sample.chargeCounterUah);
        }

        return result.toString();
    }

    private static void deserializeSamples(
            String serialized,
            ArrayDeque<CapacitySample> destination
    ) {
        if (serialized == null
                || serialized.isEmpty()) {
            return;
        }

        final String[] entries =
                serialized.split(";");

        for (String entry : entries) {
            final String[] values =
                    entry.split(",");

            if (values.length != 2) {
                continue;
            }

            try {
                final int level =
                        Integer.parseInt(values[0]);

                final long counter =
                        Long.parseLong(values[1]);

                if (level < MIN_BATTERY_LEVEL
                        || level > MAX_BATTERY_LEVEL
                        || counter <= 0L) {
                    continue;
                }

                destination.addLast(
                        new CapacitySample(
                                level,
                                counter
                        )
                );
            } catch (NumberFormatException ignored) {
                // Ignore malformed persisted samples.
            }
        }

        while (destination.size()
                > MAX_REGRESSION_SAMPLES) {
            destination.removeFirst();
        }
    }

    // ---------------------------------------------------------------------
    // Data classes
    // ---------------------------------------------------------------------

    private static final class CapacitySample {

        final int level;
        final long chargeCounterUah;

        CapacitySample(
                int level,
                long chargeCounterUah
        ) {
            this.level = level;
            this.chargeCounterUah = chargeCounterUah;
        }
    }

    private enum ReportType {
        SCREEN_ON,
        SCREEN_OFF,
        CHARGING
    }
}