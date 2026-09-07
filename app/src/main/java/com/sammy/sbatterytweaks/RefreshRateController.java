package com.sammy.sbatterytweaks;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.view.Display;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RefreshRateController {

    private static float previousMinRefreshRate = -1.0f;
    private static float previousMaxRefreshRate = -1.0f;

    public static boolean isSupported(Context context) {
        float[] refreshRates = getSupportedRefreshRates(context);

        // 60Hz-only devices
        if (refreshRates.length <= 1) {
            return false;
        }

        // 120Hz and 60Hz devices
        if (refreshRates.length == 2) {
            String peak = Settings.System.getString(
                    context.getContentResolver(),
                    "peak_refresh_rate"
            );

            if (peak == null) {
                return false;
            }

            return Integer.parseInt(peak) > 60;
        }
        return true;
    }

    public static void onScreenOn(Context context) {
        if (previousMaxRefreshRate >= 0.0f) {
            restore(context);
        }

        updateCachedRates(context);
    }

    public static void onScreenOff(Context context) {
        if (previousMinRefreshRate < 0.0f ||
                previousMaxRefreshRate < 0.0f) {
            return;
        }

        forceLowest(context);
    }

    public static void updateCachedRates(Context context) {
        ContentResolver resolver = context.getContentResolver();

        String min = Settings.System.getString(
                resolver,
                "min_refresh_rate");

        String max = Settings.System.getString(
                resolver,
                "peak_refresh_rate");

        if (min != null) {
            try {
                previousMinRefreshRate = Float.parseFloat(min);
            } catch (NumberFormatException ignored) {
            }
        }

        if (max != null) {
            try {
                previousMaxRefreshRate = Float.parseFloat(max);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public static void forceLowest(Context context) {
        if (previousMinRefreshRate < 0.0f ||
                previousMaxRefreshRate < 0.0f) {
            return;
        }

        float lowestRate = getLowestRefreshRate(context);

        Utils.changeSetting(
                context,
                Utils.Namespace.SYSTEM,
                "min_refresh_rate",
                0
        );

        Utils.changeSetting(
                context,
                Utils.Namespace.SYSTEM,
                "peak_refresh_rate",
                (int) lowestRate
        );
    }

    public static void restore(Context context) {
        if (previousMinRefreshRate < 0.0f ||
                previousMaxRefreshRate < 0.0f) {
            return;
        }

        Utils.changeSetting(
                context,
                Utils.Namespace.SYSTEM,
                "min_refresh_rate",
                (int) previousMinRefreshRate
        );

        Utils.changeSetting(
                context,
                Utils.Namespace.SYSTEM,
                "peak_refresh_rate",
                (int) previousMaxRefreshRate
        );
    }

    public static float[] getSupportedRefreshRates(Context context) {
        WindowManager windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        if (windowManager == null) {
            return new float[0];
        }

        Display display = windowManager.getDefaultDisplay();
        Display.Mode[] supportedModes = display.getSupportedModes();

        if (supportedModes == null || supportedModes.length == 0) {
            return new float[0];
        }

        List<Float> refreshRates = new ArrayList<>();

        for (Display.Mode mode : supportedModes) {
            float refreshRate = mode.getRefreshRate();

            if (!refreshRates.contains(refreshRate)) {
                refreshRates.add(refreshRate);
            }
        }

        Collections.sort(refreshRates);

        float[] rates = new float[refreshRates.size()];

        for (int i = 0; i < refreshRates.size(); i++) {
            rates[i] = refreshRates.get(i);
        }

        return rates;
    }

    private static float getLowestRefreshRate(Context context) {
        float[] refreshRates = getSupportedRefreshRates(context);

        if (refreshRates.length == 0) {
            return 0f;
        }

        return refreshRates[0];
    }
}