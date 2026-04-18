package com.sammy.sbatterytweaks;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class DrainMonitor {
    private static final String PREF_NAME = "DrainStatsPrefs";

    // ---------------------------------------------------------------------
    // Preferences
    // ---------------------------------------------------------------------

    private static final String KEY_SCREEN_ON_DELTA_UAH =
            "total_screen_on_delta_uah";
    private static final String KEY_SCREEN_ON_ELAPSED_MS =
            "total_screen_on_elapsed_ms";

    private static final String KEY_SCREEN_OFF_DELTA_UAH =
            "total_screen_off_delta_uah";
    private static final String KEY_SCREEN_OFF_ELAPSED_MS =
            "total_screen_off_elapsed_ms";

    private static final String KEY_CHARGING_DELTA_UAH =
            "total_charging_delta_uah";
    private static final String KEY_CHARGING_ELAPSED_MS =
            "total_charging_elapsed_ms";

    // ---------------------------------------------------------------------
    // Constants
    // ---------------------------------------------------------------------

    private static final long MIN_SAMPLE_INTERVAL_MS = 1_000L;

    /*
     * Initial reporting thresholds:
     *
     * Screen-on drain : 1.0%
     * Screen-off drain: 0.4%
     * Charging        : 1.0%
     *
     * Values are expressed in tenths of a percent:
     *
     * 4  = 0.4%
     * 10 = 1.0%
     */
    private static final int SCREEN_ON_INITIAL_REPORT_TENTHS_PCT = 10;
    private static final int SCREEN_OFF_INITIAL_REPORT_TENTHS_PCT = 4;
    private static final int CHARGING_INITIAL_REPORT_TENTHS_PCT = 10;

    /*
     * After the first report, update after every additional 0.1%.
     */
    private static final int REPORT_INTERVAL_TENTHS_PCT = 1;
    private static final long MIN_VALID_ELAPSED_MS = 20_000L;

    private static final long MS_PER_HOUR = 3_600_000L;

    // ---------------------------------------------------------------------
    // Capacity estimation
    // ---------------------------------------------------------------------

    /*
     * Keep multiple capacity estimates because batteryLevel is an integer
     * and therefore:
     *
     *     chargeCounter / batteryLevel
     *
     * can fluctuate considerably between samples.
     */
    private static final int MAX_CAPACITY_ESTIMATES = 20;

    private static final int MIN_BATTERY_LEVEL = 1;
    private static final int MAX_BATTERY_LEVEL = 100;

    /*
     * Sanity limits:
     *
     * 500 mAh - 20,000 mAh.
     */
    private static final long MIN_CAPACITY_UAH = 500_000L;
    private static final long MAX_CAPACITY_UAH = 20_000_000L;

    // ---------------------------------------------------------------------
    // Synchronization / context
    // ---------------------------------------------------------------------

    private static final Object LOCK = new Object();
    private static final Deque<Long> capacityEstimates =
            new ArrayDeque<>();

    // ---------------------------------------------------------------------
    // Battery baseline
    // ---------------------------------------------------------------------
    private static Context appContext;
    private static int lastChargeCounter;
    private static long lastSampleTime;
    private static long pendingElapsedMs;
    private static boolean hasBaseline;
    private static boolean baselineScreenOn = true;
    private static boolean baselineCharging;

    // ---------------------------------------------------------------------
    // Accumulated statistics
    // ---------------------------------------------------------------------
    private static boolean screenOn = true;
    private static long totalScreenOnDeltaUah;
    private static long totalScreenOnElapsedMs;
    private static long totalScreenOffDeltaUah;
    private static long totalScreenOffElapsedMs;
    private static long totalChargingDeltaUah;

    // ---------------------------------------------------------------------
    // Last reported statistics
    // ---------------------------------------------------------------------
    private static long totalChargingElapsedMs;
    private static float reportedScreenOnRate;
    private static float reportedScreenOffRate;
    private static float reportedChargingRate;

    private static int screenOnLastReportedTenthsPct;
    private static int screenOffLastReportedTenthsPct;

    // ---------------------------------------------------------------------
    // Capacity estimation
    // ---------------------------------------------------------------------
    private static int chargingLastReportedTenthsPct;
    private static long estimatedCapacityUah;

    public static void init(Context context) {
        if (context == null) {
            return;
        }

        synchronized (LOCK) {
            if (appContext != null) {
                return;
            }

            appContext = context.getApplicationContext();
            loadStatsLocked(appContext);
        }
    }

    // ---------------------------------------------------------------------
    // Battery counter updates
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

            final long now =
                    SystemClock.elapsedRealtime();

            final boolean charging =
                    BatteryReceiver.isCharging();

            updateCapacityEstimateLocked(
                    chargeCounterUah,
                    BatteryReceiver.mLevel
            );

            if (!hasBaseline) {
                setBaselineLocked(
                        chargeCounterUah,
                        now,
                        screenOn,
                        charging
                );
                return;
            }

            final long elapsedMs =
                    now - lastSampleTime;

            /*
             * Ignore excessively frequent samples.
             */
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
                    (long) lastChargeCounter
                            - chargeCounterUah
            );

            lastChargeCounter = chargeCounterUah;
            lastSampleTime = now;

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

            addAccumulatorLocked(
                    charging,
                    screenOn,
                    deltaUah,
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

            totalScreenOnDeltaUah = 0L;
            totalScreenOnElapsedMs = 0L;

            totalScreenOffDeltaUah = 0L;
            totalScreenOffElapsedMs = 0L;

            totalChargingDeltaUah = 0L;
            totalChargingElapsedMs = 0L;

            reportedScreenOnRate = 0f;
            reportedScreenOffRate = 0f;
            reportedChargingRate = 0f;

            screenOnLastReportedTenthsPct = 0;
            screenOffLastReportedTenthsPct = 0;
            chargingLastReportedTenthsPct = 0;

            capacityEstimates.clear();
            estimatedCapacityUah = 0L;

            ensureContextLocked(context);
            persistStatsLocked();
        }
    }

    // ---------------------------------------------------------------------
    // Public rate getters
    // ---------------------------------------------------------------------

    public static float getScreenOnDrainRate() {
        synchronized (LOCK) {
            return reportedScreenOnRate;
        }
    }

    public static float getScreenOffDrainRate() {
        synchronized (LOCK) {
            return reportedScreenOffRate;
        }
    }

    public static float getChargingRate() {
        synchronized (LOCK) {
            return reportedChargingRate;
        }
    }

    public static String getScreenOnDetail(
            Context context
    ) {
        synchronized (LOCK) {
            return buildDetailLocked(
                    context,
                    false,
                    totalScreenOnDeltaUah,
                    totalScreenOnElapsedMs,
                    reportedScreenOnRate
            );
        }
    }

    public static String getScreenOffDetail(
            Context context
    ) {
        synchronized (LOCK) {
            return buildDetailLocked(
                    context,
                    false,
                    totalScreenOffDeltaUah,
                    totalScreenOffElapsedMs,
                    reportedScreenOffRate
            );
        }
    }

    public static String getChargingDetail(
            Context context
    ) {
        synchronized (LOCK) {
            return buildDetailLocked(
                    context,
                    true,
                    totalChargingDeltaUah,
                    totalChargingElapsedMs,
                    reportedChargingRate
            );
        }
    }

    // ---------------------------------------------------------------------
    // Baseline helpers
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
            long deltaUah,
            long elapsedMs
    ) {
        if (charging) {
            totalChargingDeltaUah += deltaUah;
            totalChargingElapsedMs += elapsedMs;
        } else if (screenState) {
            totalScreenOnDeltaUah += deltaUah;
            totalScreenOnElapsedMs += elapsedMs;
        } else {
            totalScreenOffDeltaUah += deltaUah;
            totalScreenOffElapsedMs += elapsedMs;
        }
    }

    // ---------------------------------------------------------------------
    // Reporting logic
    // ---------------------------------------------------------------------

    private static void updateReportedRateLocked(
            boolean charging,
            boolean screenState
    ) {
        final long deltaUah;
        final long elapsedMs;
        final ReportType type;

        if (charging) {
            deltaUah = totalChargingDeltaUah;
            elapsedMs = totalChargingElapsedMs;
            type = ReportType.CHARGING;
        } else if (screenState) {
            deltaUah = totalScreenOnDeltaUah;
            elapsedMs = totalScreenOnElapsedMs;
            type = ReportType.SCREEN_ON;
        } else {
            deltaUah = totalScreenOffDeltaUah;
            elapsedMs = totalScreenOffElapsedMs;
            type = ReportType.SCREEN_OFF;
        }

        if (deltaUah <= 0L
                || elapsedMs < MIN_VALID_ELAPSED_MS
                || estimatedCapacityUah <= 0L) {
            return;
        }

        final int accumulatedTenthsPct =
                getTenthsPercentLocked(deltaUah);

        final int initialThreshold =
                getInitialReportThreshold(type);

        /*
         * Don't report until the appropriate initial threshold:
         *
         * Screen-on : 1.0%
         * Screen-off: 0.4%
         * Charging  : 1.0%
         */
        if (accumulatedTenthsPct < initialThreshold) {
            return;
        }

        final int lastReported =
                getLastReportedTenthsPctLocked(type);

        /*
         * First report.
         */
        if (lastReported == 0) {
            setLastReportedTenthsPctLocked(
                    type,
                    initialThreshold
            );

            setReportedRateLocked(
                    type,
                    calculateRateLocked(
                            deltaUah,
                            elapsedMs
                    )
            );

            return;
        }

        if (accumulatedTenthsPct
                < lastReported + REPORT_INTERVAL_TENTHS_PCT) {
            return;
        }

        final int newReportedThreshold =
                initialThreshold
                        + (
                        (
                                accumulatedTenthsPct
                                        - initialThreshold
                        )
                                / REPORT_INTERVAL_TENTHS_PCT
                )
                        * REPORT_INTERVAL_TENTHS_PCT;

        if (newReportedThreshold <= lastReported) {
            return;
        }

        setLastReportedTenthsPctLocked(
                type,
                newReportedThreshold
        );

        setReportedRateLocked(
                type,
                calculateRateLocked(
                        deltaUah,
                        elapsedMs
                )
        );
    }

    private static int getInitialReportThreshold(
            ReportType type
    ) {
        switch (type) {
            case SCREEN_ON:
                return SCREEN_ON_INITIAL_REPORT_TENTHS_PCT;

            case SCREEN_OFF:
                return SCREEN_OFF_INITIAL_REPORT_TENTHS_PCT;

            case CHARGING:
                return CHARGING_INITIAL_REPORT_TENTHS_PCT;

            default:
                return SCREEN_ON_INITIAL_REPORT_TENTHS_PCT;
        }
    }

    private static int getLastReportedTenthsPctLocked(
            ReportType type
    ) {
        switch (type) {
            case SCREEN_ON:
                return screenOnLastReportedTenthsPct;

            case SCREEN_OFF:
                return screenOffLastReportedTenthsPct;

            case CHARGING:
                return chargingLastReportedTenthsPct;

            default:
                return 0;
        }
    }

    private static void setLastReportedTenthsPctLocked(
            ReportType type,
            int value
    ) {
        switch (type) {
            case SCREEN_ON:
                screenOnLastReportedTenthsPct = value;
                break;

            case SCREEN_OFF:
                screenOffLastReportedTenthsPct = value;
                break;

            case CHARGING:
                chargingLastReportedTenthsPct = value;
                break;
        }
    }

    private static void setReportedRateLocked(
            ReportType type,
            float rate
    ) {
        switch (type) {
            case SCREEN_ON:
                reportedScreenOnRate = rate;
                break;

            case SCREEN_OFF:
                reportedScreenOffRate = rate;
                break;

            case CHARGING:
                reportedChargingRate = rate;
                break;
        }
    }

    // ---------------------------------------------------------------------
    // Capacity estimation
    // ---------------------------------------------------------------------

    private static void updateCapacityEstimateLocked(
            long counterUah,
            int batteryLevel
    ) {
        if (counterUah <= 0L) {
            return;
        }

        if (batteryLevel < MIN_BATTERY_LEVEL
                || batteryLevel > MAX_BATTERY_LEVEL) {
            return;
        }

        /*
         * Estimate full capacity:
         *
         *     capacity = chargeCounter × 100 / batteryLevel
         */
        final long capacityUah =
                (counterUah * 100L)
                        / batteryLevel;

        if (capacityUah < MIN_CAPACITY_UAH
                || capacityUah > MAX_CAPACITY_UAH) {
            return;
        }

        capacityEstimates.addLast(
                capacityUah
        );

        while (capacityEstimates.size()
                > MAX_CAPACITY_ESTIMATES) {
            capacityEstimates.removeFirst();
        }

        estimatedCapacityUah =
                calculateMedianCapacityLocked();
    }

    private static long calculateMedianCapacityLocked() {
        if (capacityEstimates.isEmpty()) {
            return 0L;
        }

        final List<Long> values =
                new ArrayList<>(
                        capacityEstimates
                );

        Collections.sort(values);

        final int middle =
                values.size() / 2;

        if ((values.size() & 1) == 0) {
            return (
                    values.get(middle - 1)
                            + values.get(middle)
            ) / 2L;
        }

        return values.get(middle);
    }

    // ---------------------------------------------------------------------
    // Percentage conversion
    // ---------------------------------------------------------------------

    private static int getTenthsPercentLocked(
            long deltaUah
    ) {
        if (estimatedCapacityUah <= 0L) {
            return 0;
        }

        return (int) (
                deltaUah * 1000L
                        / estimatedCapacityUah
        );
    }

    // ---------------------------------------------------------------------
    // Rate calculation
    // ---------------------------------------------------------------------

    private static float calculateRateLocked(
            long deltaUah,
            long elapsedMs
    ) {
        if (deltaUah <= 0L
                || elapsedMs <= 0L
                || estimatedCapacityUah <= 0L) {
            return 0f;
        }

        final float deltaPct =
                deltaUah * 100f
                        / estimatedCapacityUah;

        final float elapsedHours =
                elapsedMs / (float) MS_PER_HOUR;

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
            long deltaUah,
            long elapsedMs,
            float reportedRate
    ) {
        if (context == null) {
            return "";
        }

        /*
         * No statistic until the first reporting threshold has been
         * reached.
         */
        if (reportedRate <= 0f
                || estimatedCapacityUah <= 0L) {
            return "";
        }

        final float deltaPct =
                deltaUah * 100f
                        / estimatedCapacityUah;

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
                TimeUnit.MILLISECONDS.toSeconds(
                        elapsedMs
                );

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
    // Context / persistence
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

    private static void persistStatsLocked() {
        if (appContext == null) {
            return;
        }

        appContext
                .getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                )
                .edit()
                .putLong(
                        KEY_SCREEN_ON_DELTA_UAH,
                        totalScreenOnDeltaUah
                )
                .putLong(
                        KEY_SCREEN_ON_ELAPSED_MS,
                        totalScreenOnElapsedMs
                )
                .putLong(
                        KEY_SCREEN_OFF_DELTA_UAH,
                        totalScreenOffDeltaUah
                )
                .putLong(
                        KEY_SCREEN_OFF_ELAPSED_MS,
                        totalScreenOffElapsedMs
                )
                .putLong(
                        KEY_CHARGING_DELTA_UAH,
                        totalChargingDeltaUah
                )
                .putLong(
                        KEY_CHARGING_ELAPSED_MS,
                        totalChargingElapsedMs
                )
                .apply();
    }

    private static void loadStatsLocked(
            Context context
    ) {
        final SharedPreferences prefs =
                context.getSharedPreferences(
                        PREF_NAME,
                        Context.MODE_PRIVATE
                );

        totalScreenOnDeltaUah =
                prefs.getLong(
                        KEY_SCREEN_ON_DELTA_UAH,
                        0L
                );

        totalScreenOnElapsedMs =
                prefs.getLong(
                        KEY_SCREEN_ON_ELAPSED_MS,
                        0L
                );

        totalScreenOffDeltaUah =
                prefs.getLong(
                        KEY_SCREEN_OFF_DELTA_UAH,
                        0L
                );

        totalScreenOffElapsedMs =
                prefs.getLong(
                        KEY_SCREEN_OFF_ELAPSED_MS,
                        0L
                );

        totalChargingDeltaUah =
                prefs.getLong(
                        KEY_CHARGING_DELTA_UAH,
                        0L
                );

        totalChargingElapsedMs =
                prefs.getLong(
                        KEY_CHARGING_ELAPSED_MS,
                        0L
                );

        /*
         * elapsedRealtime() cannot survive a process restart, so the
         * runtime baseline must be reconstructed from a new sample.
         */
        clearBaselineLocked();

        /*
         * Reported values are runtime state.
         */
        reportedScreenOnRate = 0f;
        reportedScreenOffRate = 0f;
        reportedChargingRate = 0f;

        screenOnLastReportedTenthsPct = 0;
        screenOffLastReportedTenthsPct = 0;
        chargingLastReportedTenthsPct = 0;

        /*
         * Capacity estimates are also rebuilt from new battery samples.
         */
        capacityEstimates.clear();
        estimatedCapacityUah = 0L;
    }

    // ---------------------------------------------------------------------
    // Report type
    // ---------------------------------------------------------------------

    private enum ReportType {
        SCREEN_ON,
        SCREEN_OFF,
        CHARGING
    }
}