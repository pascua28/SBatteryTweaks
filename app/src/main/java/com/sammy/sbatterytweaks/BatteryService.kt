package com.sammy.sbatterytweaks

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.IBinder
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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

        startBackgroundTask(context)
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
        var refreshInterval: Long = 2500
        @JvmField
        var isBypassed: Int = 0
        @JvmField
        var manualBypass: Boolean = false
        var notificationManager: NotificationManager? = null
        var notification: Notification.Builder? = null
        private var backgroundJob: Job? = null
        private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        @JvmStatic
        fun isBypassed(): Boolean {
            return BatteryReceiver.notCharging() || (BatteryReceiver.isCharging() &&
                    BatteryWorker.pausePdSupported &&
                    BatteryWorker.pausePdEnabled)
        }

        @JvmStatic
        fun startBackgroundTask(context: Context) {
            stopBackgroundTask()

            backgroundJob = coroutineScope.launch {
                while (isActive) {
                    BatteryWorker.fetchUpdates(context)
                    BatteryWorker.updateStats(context, BatteryReceiver.isCharging())

                    if (manualBypass) return@launch

                    if (BatteryReceiver.drainMonitorEnabled && !BatteryReceiver.isCharging()) {
                        DrainMonitor.handleBatteryChange(context,
                            BatteryReceiver.divisor,
                            BatteryReceiver.getCounter(context),
                            BatteryReceiver.isCharging()
                        )

                        delay(refreshInterval)
                        continue
                    }

                    if (!isBypassed() && BatteryWorker.idleEnabled && BatteryReceiver.mLevel >= BatteryWorker.idleLevel) {
                        BatteryWorker.setBypass(context, 1)
                    } else if (!BatteryWorker.pauseMode && isBypassed()
                        && BatteryWorker.idleEnabled && BatteryReceiver.mLevel < BatteryWorker.idleLevel
                    ) {
                        if (BatteryWorker.pausePdSupported) {
                            Utils.changeSetting(context, Utils.Namespace.GLOBAL, "protect_battery", 0)
                            BatteryWorker.setBypass(context, 0)
                        } else {
                            BatteryWorker.setBypass(BatteryWorker.idleLevel)
                        }
                    } else {
                        BatteryWorker.batteryWorker(context, BatteryReceiver.isCharging())
                    }

                    delay(refreshInterval)
                }
            }
        }

        @JvmStatic
        fun stopBackgroundTask() {
            backgroundJob?.cancel()
            backgroundJob = null
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

        private fun providerInstalled(context: Context): Boolean {
            try {
                context.packageManager.getPackageGids("com.netvor.settings.database.provider")
                return true
            } catch (ignored: PackageManager.NameNotFoundException) {
                return false
            }
        }

        @Throws(IOException::class)
        private fun installProvider(context: Context) {
            val outFile =
                File(context.getExternalFilesDir(null), "settings-database-provider-v1.1-cli.apk")

            context.assets.open("settings-database-provider-v1.1-cli.apk").use { `is` ->
                FileOutputStream(outFile).use { os ->
                    val buffer = ByteArray(4096)
                    var length: Int
                    while ((`is`.read(buffer).also { length = it }) > 0) {
                        os.write(buffer, 0, length)
                    }
                }
            }
            val apkSize = outFile.length()

            val command =
                "cat " + outFile.absolutePath + " | pm install -S " + apkSize + " --bypass-low-target-sdk-block"
            Utils.runCmd(command)
        }

        @JvmStatic
        fun installDatabaseProvider(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                if (!providerInstalled(context) && Utils.isShizuku()) {
                    try {
                        installProvider(context)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                R.string.success,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (ignored: IOException) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                R.string.failure,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }
}