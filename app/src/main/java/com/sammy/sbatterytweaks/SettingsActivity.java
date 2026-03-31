package com.sammy.sbatterytweaks;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Switch;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeslSwitchPreferenceScreen;

import com.google.android.material.appbar.MaterialToolbar;
import com.sammy.sbatterytweaks.preference.FloatSeekBarPreference;

import java.util.Objects;

public class SettingsActivity extends AppCompatActivity {

    public static final String
            KEY_PREF_SERVICE = "enableservice",
            KEY_PREF_DRAIN_MONITOR = "drainmonitor",
            KEY_PREF_THRESHOLD_UP = "thresholdUp",
            KEY_PREF_TEMP_DELTA = "tempDelta",
            KEY_PREF_BYPASS_MODE = "pauseMode",
            KEY_PREF_TIMER_SWITCH = "timerSwitch",
            KEY_PREF_CD_SECONDS = "cdSeconds",
            PREF_SCHED_ENABLED = "schedSwitch",
            PREF_IDLE_SWITCH = "idleSwitch",
            PREF_IDLE_LEVEL = "idleLevel",
            PREF_DISABLE_SYNC = "disablesync",
            PREF_RESET_STATS = "resetstats",
            PREF_SLOW_CHARGE_THRESHOLD_SWITCH = "slowChargeThresholdSwitch",
            PREF_SLOW_CHARGE_THRESHOLD = "slowChargeThreshold",
            PREF_FAST_CHARGE_THRESHOLD_SWITCH = "fastChargeThresholdSwitch",
            PREF_FAST_CHARGE_THRESHOLD = "fastChargeThreshold",
            PREF_TOAST_NOTIF = "enabletoast";

    private Switch idleToggle;

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        SharedPreferences.OnSharedPreferenceChangeListener listener;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            SeslSwitchPreferenceScreen pauseModeSwitch = findPreference("pauseMode");
            assert pauseModeSwitch != null;
            pauseModeSwitch.setEnabled(false);
            SeslSwitchPreferenceScreen idleSwitch = findPreference(PREF_IDLE_SWITCH);
            assert idleSwitch != null;
            idleSwitch.setEnabled(false);
            SeslSwitchPreferenceScreen resetSwitch = findPreference(PREF_RESET_STATS);

            SeslSwitchPreferenceScreen drainMonitorSwitch = findPreference(KEY_PREF_DRAIN_MONITOR);
            if (!drainMonitorSwitch.isEnabled()) {
                pauseModeSwitch.setOnPreferenceClickListener(v -> {
                    DrainMonitor.resetStats(getContext());
                    return false;
                });
            }

            if (BatteryWorker.bypassSupported || BatteryWorker.pausePdSupported) {
                pauseModeSwitch.setEnabled(true);

                idleSwitch.setEnabled(true);
            }

            if (pauseModeSwitch.isEnabled()) {
                pauseModeSwitch.setOnPreferenceClickListener(v -> {
                    BatteryService.setBypassMode(BatteryService.BypassMode.AUTO);
                    BatteryWorker.setBypass(getContext(), 0);
                    return false;
                });
            }

            if (!Utils.isRooted()) {
                if (resetSwitch != null) {
                    resetSwitch.setEnabled(false);
                }
            }

            Preference timePreference = findPreference("timePickerBtn");
            if (timePreference != null) {
                timePreference.setOnPreferenceClickListener(p -> {
                    TimePicker picker = new TimePicker(getActivity());
                    picker.show();
                    return true;
                });
            }

            SeslSwitchPreferenceScreen slowChargeThresholdSwitch = findPreference(PREF_SLOW_CHARGE_THRESHOLD_SWITCH);
            SeslSwitchPreferenceScreen fastChargeThresholdSwitch = findPreference(PREF_FAST_CHARGE_THRESHOLD_SWITCH);
            FloatSeekBarPreference slowChargeThreshold = findPreference(PREF_SLOW_CHARGE_THRESHOLD);
            FloatSeekBarPreference fastChargeThreshold = findPreference(PREF_FAST_CHARGE_THRESHOLD);

            slowChargeThreshold.setOnPreferenceChangeListener(((preference, newValue) -> {
                int slow = (int) newValue;
                int fast = (int) fastChargeThreshold.getValue();

                if (fastChargeThresholdSwitch.isEnabled()) {
                    if (slow <= fast) {
                        slow = fast + 1;
                        slowChargeThreshold.setValue((float) slow, true);
                        return false;
                    }
                }

                return true;
            }));

            fastChargeThreshold.setOnPreferenceChangeListener(((preference, newValue) -> {
                int fast = (int) newValue;
                int slow = (int) slowChargeThreshold.getValue();

                if (slowChargeThresholdSwitch.isEnabled()) {
                    if (fast >= slow) {
                        fast = slow - 1;
                        fastChargeThreshold.setValue((float)fast, true);
                        return false;
                    }
                }

                return true;
            }));
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            listener = (sharedPreferences, key) -> {
                if (key == null) return;

                if (key.equals(KEY_PREF_TIMER_SWITCH) &&
                        !sharedPreferences.getBoolean(KEY_PREF_TIMER_SWITCH, false))
                    BatteryWorker.isOngoing = false;

                if (key.equals(PREF_IDLE_LEVEL) || key.equals(PREF_IDLE_SWITCH)) {
                    BatteryService.setBypassMode(BatteryService.BypassMode.AUTO);
                    BatteryWorker.setBypass(getContext(), 0);
                }
            };
        }

        @Override
        public void onResume() {
            super.onResume();
            Objects.requireNonNull(getPreferenceScreen().getSharedPreferences())
                    .registerOnSharedPreferenceChangeListener(listener);
        }

        @Override
        public void onPause() {
            super.onPause();
            Objects.requireNonNull(getPreferenceScreen().getSharedPreferences())
                    .unregisterOnSharedPreferenceChangeListener(listener);
        }
    }
}