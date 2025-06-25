package com.sammy.sbatterytweaks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import androidx.preference.PreferenceManager

class BatteryService : Service() {
    private lateinit var context: Context

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        context = this
        BatteryWorker.bypassSupported = (Utils.isPrivileged() &&
                Utils.runCmd("ls " + fullCapFIle).contains(fullCapFIle))
        try {
            Settings.System.getInt(contentResolver, "pass_through")
            BatteryWorker.pausePdSupported = true
        } catch (e: SettingNotFoundException) {
            BatteryWorker.pausePdSupported = false
            e.printStackTrace()
        }
        buildNotif()

        val batteryReceiver = BatteryReceiver()
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, ifilter)
        val ACTION_POWER_CONNECTED = IntentFilter("android.intent.action.ACTION_POWER_CONNECTED")
        val ACTION_POWER_DISCONNECTED =
            IntentFilter("android.intent.action.ACTION_POWER_DISCONNECTED")
        registerReceiver(BatteryReceiver(), ACTION_POWER_CONNECTED)
        registerReceiver(BatteryReceiver(), ACTION_POWER_DISCONNECTED)

        val screenReceiver = ScreenReceiver()
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        val sharedPreferences = context.getSharedPreferences("PROTECT_SETTING", MODE_PRIVATE)
        try {
            sharedPreferences.edit().putInt(
                "PROTECT_ENABLED",
                Settings.Global.getInt(context.getContentResolver(), "protect_battery")
            ).apply()
        } catch (ignored: SettingNotFoundException) {
            sharedPreferences.edit().putInt("PROTECT_ENABLED", -1).apply()
        }

        runnable = object : Runnable {
            override fun run() {
                BatteryWorker.fetchUpdates(context)
                BatteryWorker.updateStats(context, BatteryReceiver.isCharging())

                batteryPct =
                    ((BatteryReceiver.getCounter(context) / 1000f) / BatteryReceiver.divisor) * 100

                if (manualBypass) return

                val sharedPref =
                    PreferenceManager.getDefaultSharedPreferences(context)
                drainMonitorEnabled =
                    sharedPref.getBoolean(SettingsActivity.KEY_PREF_DRAIN_MONITOR, false)

                if (drainMonitorEnabled && !BatteryReceiver.isCharging()) {
                    DrainMonitor.handleBatteryChange(
                        BatteryReceiver.divisor, BatteryReceiver.getCounter(context),
                        BatteryReceiver.isCharging()
                    )

                    var activeDrain = ""
                    var idleDrain = ""

                    if (DrainMonitor.getScreenOnDrainRate() > 0.0f) activeDrain = context.getString(
                        R.string.active_drain,
                        DrainMonitor.getScreenOnDrainRate()
                    )

                    if (DrainMonitor.getScreenOffDrainRate() > 0.0f) idleDrain =
                        context.getString(R.string.idle_drain, DrainMonitor.getScreenOffDrainRate())

                    updateNotif(context.getString(R.string.temperature_title) + BatteryReceiver.mTemp + " °C",
                        activeDrain, idleDrain
                    )

                    mHandler.postDelayed(this, refreshInterval.toLong())
                    return
                }

                if (!isBypassed() && BatteryWorker.idleEnabled && BatteryReceiver.mLevel >= BatteryWorker.idleLevel) {
                    BatteryWorker.setBypass(context, 1)
                } else if (!BatteryWorker.pauseMode && isBypassed() &&
                    BatteryWorker.idleEnabled && BatteryReceiver.mLevel < BatteryWorker.idleLevel
                ) {
                    if (BatteryWorker.pausePdSupported) {
                        Utils.changeSetting(context, Utils.Namespace.GLOBAL, "protect_battery", 0)
                        BatteryWorker.setBypass(context, 0)
                    } else BatteryWorker.setBypass(BatteryWorker.idleLevel)
                } else BatteryWorker.batteryWorker(context, BatteryReceiver.isCharging())

                mHandler.postDelayed(this, refreshInterval.toLong())
            }
        }
        startBackgroundTask()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBackgroundTask()
    }

    private fun buildNotif() {
        val CHANNELID = "Batt"
        val channel = NotificationChannel(
            CHANNELID,
            CHANNELID,
            NotificationManager.IMPORTANCE_NONE
        )

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        checkNotNull(notificationManager)
        notificationManager!!.createNotificationChannel(channel)

        notification = Notification.Builder(this, CHANNELID).setOngoing(true)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)

        startForeground(1002, notification!!.build())
    }

    companion object {
        const val fullCapFIle: String = "/sys/class/power_supply/battery/batt_full_capacity"
        @JvmField
        var refreshInterval: Int = 2500
        @JvmField
        var isBypassed: Int = 0
        @JvmField
        var batteryPct: Float = 0f
        @JvmField
        var drainMonitorEnabled: Boolean = false
        @JvmField
        var manualBypass: Boolean = false
        var notificationManager: NotificationManager? = null
        var notification: Notification.Builder? = null
        var mHandler: Handler = Handler()
        var runnable: Runnable? = null
        @JvmStatic
        fun isBypassed(): Boolean {
            return BatteryReceiver.notCharging() || (BatteryReceiver.isCharging() &&
                    BatteryWorker.pausePdSupported &&
                    BatteryWorker.pausePdEnabled)
        }

        @JvmStatic
        fun startBackgroundTask() {
            if (mHandler.hasMessages(0)) return

            mHandler.sendEmptyMessage(0)
            mHandler.post(runnable!!)
        }

        @JvmStatic
        fun stopBackgroundTask() {
            if (mHandler.hasMessages(0)) {
                mHandler.removeCallbacksAndMessages(null)
            }
        }

        @JvmStatic
        fun updateNotif(msg: String?, msg2: String?, msg3: String?) {
            if (notificationManager == null) return

            notification!!.setContentTitle(msg)
                .setStyle(
                    Notification.InboxStyle()
                        .addLine(msg2)
                        .addLine(msg3)
                )
            notificationManager!!.notify(1002, notification!!.build())
        }
    }
}