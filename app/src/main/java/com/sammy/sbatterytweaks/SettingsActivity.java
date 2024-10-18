package com.sammy.sbatterytweaks;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Switch;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import java.util.Objects;

public class SettingsActivity extends AppCompatActivity {

    public static final String
            KEY_PREF_SERVICE = "enableservice",
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
            PREF_BATT_LVL_SWITCH = "lvlThresholdSwitch",
            PREF_BATT_LVL_THRESHOLD = "lvlThreshold",
            PREF_TOAST_NOTIF = "enabletoast";

    private Switch idleToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
            setTheme(R.style.Theme_ChargeRateAutomator_Settings);
        else
            setTheme(R.style.Theme_ChargeRateAutomator);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        SharedPreferences.OnSharedPreferenceChangeListener listener;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            SwitchPreferenceCompat pauseModeSwitch = findPreference("pauseMode");
            assert pauseModeSwitch != null;
            pauseModeSwitch.setEnabled(false);
            SwitchPreferenceCompat idleSwitch = findPreference(PREF_IDLE_SWITCH);
            assert idleSwitch != null;
            idleSwitch.setEnabled(false);
            SwitchPreferenceCompat resetSwitch = findPreference(PREF_RESET_STATS);
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getContext());

            if (BatteryWorker.bypassSupported || BatteryWorker.pausePdSupported) {
                pauseModeSwitch.setEnabled(true);

                idleSwitch.setEnabled(true);
            }

            if (pauseModeSwitch.isEnabled()) {
                pauseModeSwitch.setOnPreferenceClickListener(v-> {
                    BatteryWorker.setBypass(getContext(), 0, true);
                    return false;
                });
            }

            if (idleSwitch.isEnabled()) {
                idleSwitch.setOnPreferenceClickListener(v-> {
                    BatteryWorker.setBypass(getContext(), 0, true);
                    if (idleSwitch.isChecked()) {
                        if (!BatteryWorker.pausePdSupported) {
                            BatteryWorker.setBypass(sharedPreferences.getInt(PREF_IDLE_LEVEL, 75));
                        }
                    }
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
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            listener = (sharedPreferences, key) -> {
                assert key != null;
                if (key.equals(KEY_PREF_TIMER_SWITCH) && !sharedPreferences.getBoolean(KEY_PREF_TIMER_SWITCH, false))
                    BatteryWorker.isOngoing = false;
                else if (key.equals(PREF_IDLE_LEVEL) &&
                        (sharedPreferences.getInt(PREF_IDLE_LEVEL, 75) != BatteryWorker.battFullCap)) {
                    if (!BatteryWorker.pausePdSupported && BatteryWorker.bypassSupported) {
                        BatteryWorker.setBypass(sharedPreferences.getInt(PREF_IDLE_LEVEL, 75));
                    }
                    //Reset bypass state. BatteryService will change this anyway
                    if (BatteryWorker.pausePdSupported) {
                        BatteryWorker.setBypass(getContext(), 0, true);
                    }
                    BatteryService.manualBypass = false;
                } else if (key.equals(PREF_IDLE_SWITCH) && BatteryService.isBypassed() && !BatteryService.manualBypass)
                    BatteryWorker.setBypass(getContext(), 0, false);
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