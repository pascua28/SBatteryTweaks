package com.sammy.sbatterytweaks

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import com.sammy.sbatterytweaks.BatteryStatusWidgetProvider.Companion.updateAllWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class BatteryService : Service() {
    private lateinit var context: Context

    enum class BypassMode {
        AUTO,
        FORCE_ON,
        FORCE_OFF
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        context = this
        BatteryWorker.bypassSupported = (Utils.isPrivileged() &&
                Utils.runCmd("ls $FULLCAPFILE").contains(FULLCAPFILE))
        try {
            Settings.System.getInt(contentResolver, "pass_through")
            BatteryWorker.pausePdSupported = true
        } catch (e: SettingNotFoundException) {
            BatteryWorker.pausePdSupported = false
            e.printStackTrace()
        }
        updateStatusPref(context)
        buildNotif()

        val batteryReceiver = BatteryReceiver()
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, ifilter)
        val powerConnected = IntentFilter("android.intent.action.ACTION_POWER_CONNECTED")
        val powerDisconnected =
            IntentFilter("android.intent.action.ACTION_POWER_DISCONNECTED")
        registerReceiver(BatteryReceiver(), powerConnected)
        registerReceiver(BatteryReceiver(), powerDisconnected)

        val screenReceiver = ScreenReceiver()
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))

        val sharedPreferences = context.getSharedPreferences("PROTECT_SETTING", MODE_PRIVATE)
        try {
            sharedPreferences.edit().putInt(
                "PROTECT_ENABLED",
                Settings.Global.getInt(context.contentResolver, "protect_battery")
            ).apply()
        } catch (_: SettingNotFoundException) {
            sharedPreferences.edit().putInt("PROTECT_ENABLED", -1).apply()
        }

        startBackgroundTask(context)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBackgroundTask()
    }

    private fun buildNotif() {
        val channelID = "Batt"
        val channel = NotificationChannel(
            channelID,
            channelID,
            NotificationManager.IMPORTANCE_NONE
        )

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        checkNotNull(notificationManager)
        notificationManager!!.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        notification = Notification.Builder(this, channelID)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)

        startForeground(1002, notification!!.build())
    }

    companion object {
        const val FULLCAPFILE: String = "/sys/class/power_supply/battery/batt_full_capacity"
        const val INSTALL_ACTION = "com.sammy.sbatterytweaks.PROVIDER_INSTALL_STATUS"
        private const val PROVIDER_PACKAGE = "com.netvor.settings.database.provider"
        private const val PROVIDER_APK_ASSET = "settings-database-provider-v1.1-cli.apk"

        @JvmField
        var refreshInterval: Long = 2500

        @JvmField
        var isBypassed: Int = 0
        var notificationManager: NotificationManager? = null
        var notification: Notification.Builder? = null
        private var backgroundJob: Job? = null
        private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Volatile
        private var pendingInstallAfterUnknownSources = false

        private fun updateStatusPref(context: Context) {
            val prefs = context.getSharedPreferences("battery_widget", MODE_PRIVATE)
            prefs.edit()
                .putBoolean("bypass_supported", BatteryWorker.bypassSupported)
                .putBoolean("pausepd_supported", BatteryWorker.pausePdSupported)
                .commit()
        }

        @JvmStatic
        fun isBypassed(): Boolean {
            return BatteryReceiver.notCharging() || (BatteryReceiver.isCharging() &&
                    BatteryWorker.pausePdSupported &&
                    BatteryWorker.pausePdEnabled)
        }

        @Volatile
        private var currentBypassMode: BypassMode = BypassMode.AUTO

        @JvmStatic
        fun setBypassMode(mode: BypassMode) {
            currentBypassMode = mode
        }

        @JvmStatic
        fun getBypassMode(): BypassMode = currentBypassMode

        @JvmStatic
        fun startBackgroundTask(context: Context) {
            stopBackgroundTask()

            backgroundJob = coroutineScope.launch {
                while (isActive) {
                    BatteryWorker.fetchUpdates(context)

                    val charging = BatteryReceiver.isCharging()
                    val bypassed = isBypassed()

                    BatteryWorker.updateStats(context, charging)

                    if (BatteryReceiver.drainMonitorEnabled) {
                        DrainMonitor.handleChargeCounterChange(
                            context,
                            BatteryReceiver.getCounter(context)
                        )
                        updateNotif(context)
                    }

                    when (getBypassMode()) {
                        BypassMode.FORCE_ON -> {
                            if (charging) {
                                if (!bypassed) {
                                    BatteryWorker.setBypass(context, 1)
                                }
                            } else {
                                if (bypassed) {
                                    BatteryWorker.setBypass(context, 0)
                                }
                            }
                        }

                        BypassMode.FORCE_OFF -> {
                            if (bypassed) {
                                BatteryWorker.setBypass(context, 0)
                            }
                        }

                        BypassMode.AUTO -> {
                            if (!charging) {
                                if (bypassed) {
                                    BatteryWorker.setBypass(context, 0)
                                }

                                updateStatusPref(context)
                                updateAllWidgets(context)
                                delay(refreshInterval)
                                continue
                            }

                            if (!isBypassed()
                                && BatteryWorker.idleEnabled
                                && BatteryReceiver.mLevel >= BatteryWorker.idleLevel
                            ) {
                                BatteryWorker.setBypass(context, 1)
                            } else if (!BatteryWorker.pauseMode
                                && isBypassed()
                                && BatteryWorker.idleEnabled
                                && BatteryReceiver.mLevel < BatteryWorker.idleLevel
                            ) {
                                if (BatteryWorker.pausePdSupported) {
                                    Utils.changeSetting(
                                        context,
                                        Utils.Namespace.GLOBAL,
                                        "protect_battery",
                                        0
                                    )
                                    BatteryWorker.setBypass(context, 0)
                                } else {
                                    BatteryWorker.setBypass(BatteryWorker.idleLevel)
                                }
                            } else {
                                BatteryWorker.batteryWorker(context, true)
                            }
                        }
                    }

                    updateStatusPref(context)
                    updateAllWidgets(context)
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
        fun updateNotif(context: Context) {
            if (notificationManager == null) return

            val msg =
                context.getString(R.string.temperature_title) + BatteryReceiver.getTemp() + " °C"
            var msg2 = ""
            var msg3 = ""

            if (BatteryReceiver.drainMonitorEnabled) {
                if (BatteryReceiver.isCharging()) {
                    if (!isBypassed()) {
                        val rate = DrainMonitor.getChargingRate()
                        if (rate > 0f) {
                            val base = context.getString(R.string.charging_rate, rate)
                            val detail = DrainMonitor.getChargingDetail(context)
                            msg2 = if (detail.isNotEmpty()) {
                                context.getString(R.string.charging_rate_full, base, detail)
                            } else {
                                base
                            }
                        }
                    }
                } else {
                    val activeRate = DrainMonitor.getScreenOnDrainRate()
                    if (activeRate > 0f) {
                        val base = context.getString(R.string.active_drain, activeRate)
                        val detail = DrainMonitor.getScreenOnDetail(context)
                        msg2 = if (detail.isNotEmpty()) {
                            context.getString(R.string.active_drain_full, base, detail)
                        } else {
                            base
                        }
                    }

                    val idleRate = DrainMonitor.getScreenOffDrainRate()
                    if (idleRate > 0f) {
                        val base = context.getString(R.string.idle_drain, idleRate)
                        val detail = DrainMonitor.getScreenOffDetail(context)
                        msg3 = if (detail.isNotEmpty()) {
                            context.getString(R.string.idle_drain_full, base, detail)
                        } else {
                            base
                        }
                    }
                }
            }

            notification!!.setContentTitle(msg).style = Notification.InboxStyle()
                .addLine(msg2)
                .addLine(msg3)
            notificationManager!!.notify(1002, notification!!.build())
        }

        private fun providerInstalled(context: Context): Boolean {
            return try {
                context.packageManager.getPackageInfo(PROVIDER_PACKAGE, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

        @Throws(IOException::class)
        private fun copyProviderApk(context: Context): File {
            val outFile = File(context.getExternalFilesDir(null), PROVIDER_APK_ASSET)

            context.assets.open(PROVIDER_APK_ASSET).use { input ->
                FileOutputStream(outFile).use { os ->
                    val buffer = ByteArray(8192)
                    var length: Int
                    while (input.read(buffer).also { length = it } > 0) {
                        os.write(buffer, 0, length)
                    }
                }
            }

            return outFile
        }

        @Throws(IOException::class)
        private fun installProviderPrivileged(context: Context) {
            val outFile = copyProviderApk(context)
            val apkSize = outFile.length()

            val command =
                "cat \"${outFile.absolutePath}\" | pm install -S $apkSize --bypass-low-target-sdk-block"

            Utils.runCmd(command)
        }

        private fun startDirectInstall(
            activity: Activity,
            userActionLauncher: ActivityResultLauncher<Intent>,
            unknownSourcesLauncher: ActivityResultLauncher<Intent>
        ) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                pendingInstallAfterUnknownSources = true
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
                unknownSourcesLauncher.launch(intent)
                return
            }

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val apkFile = withContext(Dispatchers.IO) {
                        copyProviderApk(activity)
                    }

                    val packageInstaller = activity.packageManager.packageInstaller
                    val params = PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL
                    ).apply {
                        setAppPackageName(PROVIDER_PACKAGE)
                    }

                    val sessionId = packageInstaller.createSession(params)
                    val session = packageInstaller.openSession(sessionId)

                    session.use { s ->
                        FileInputStream(apkFile).use { input ->
                            s.openWrite("base.apk", 0, apkFile.length()).use { output ->
                                val buffer = ByteArray(8192)
                                var count: Int
                                while (input.read(buffer).also { count = it } != -1) {
                                    output.write(buffer, 0, count)
                                }
                                s.fsync(output)
                            }
                        }

                        val callbackIntent = Intent(INSTALL_ACTION).setPackage(activity.packageName)
                        val pendingIntent = PendingIntent.getBroadcast(
                            activity,
                            sessionId,
                            callbackIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        )

                        s.commit(pendingIntent.intentSender)
                    }
                } catch (_: Exception) {
                    Toast.makeText(activity, R.string.failure, Toast.LENGTH_SHORT).show()
                    MainActivity.showSetupDialog(activity)
                }
            }
        }

        @JvmStatic
        fun onUnknownSourcesResult(
            activity: Activity,
            userActionLauncher: ActivityResultLauncher<Intent>,
            unknownSourcesLauncher: ActivityResultLauncher<Intent>
        ) {
            if (!pendingInstallAfterUnknownSources) return
            pendingInstallAfterUnknownSources = false

            if (activity.packageManager.canRequestPackageInstalls()) {
                startDirectInstall(activity, userActionLauncher, unknownSourcesLauncher)
            } else {
                Toast.makeText(activity, R.string.failure, Toast.LENGTH_SHORT).show()
                MainActivity.showSetupDialog(activity)
            }
        }

        @JvmStatic
        fun handleInstallStatusIntent(
            activity: Activity,
            intent: Intent,
            userActionLauncher: ActivityResultLauncher<Intent>
        ) {
            val status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE
            )
            intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()

            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }

                    if (confirmIntent != null) {
                        userActionLauncher.launch(confirmIntent)
                    } else {
                        Toast.makeText(activity, R.string.failure, Toast.LENGTH_SHORT).show()
                        MainActivity.showSetupDialog(activity)
                    }
                }

                PackageInstaller.STATUS_SUCCESS -> {
                    if (providerInstalled(activity)) {
                        Toast.makeText(activity, R.string.success, Toast.LENGTH_SHORT).show()
                        MainActivity.forceStop(activity)
                    } else {
                        Toast.makeText(activity, R.string.failure, Toast.LENGTH_SHORT).show()
                        MainActivity.showSetupDialog(activity)
                    }
                }

                else -> {
                    Toast.makeText(activity, R.string.failure, Toast.LENGTH_SHORT).show()
                    MainActivity.showSetupDialog(activity)
                }
            }
        }

        @JvmStatic
        fun installDatabaseProvider(
            activity: Activity,
            userActionLauncher: ActivityResultLauncher<Intent>,
            unknownSourcesLauncher: ActivityResultLauncher<Intent>
        ) {
            CoroutineScope(Dispatchers.Main).launch {
                if (providerInstalled(activity)) {
                    return@launch
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        if (Utils.isShizuku() || Utils.isRooted()) {
                            Toast.makeText(activity, R.string.in_progress, Toast.LENGTH_SHORT)
                                .show()
                            withContext(Dispatchers.IO) {
                                installProviderPrivileged(activity)
                            }

                            if (providerInstalled(activity)) {
                                Toast.makeText(activity, R.string.success, Toast.LENGTH_SHORT)
                                    .show()
                                MainActivity.forceStop(activity)
                            } else {
                                Toast.makeText(activity, R.string.failure, Toast.LENGTH_SHORT)
                                    .show()
                                MainActivity.showSetupDialog(activity)
                            }
                        } else {
                            MainActivity.showSetupDialog(activity)
                        }
                    } else {
                        startDirectInstall(activity, userActionLauncher, unknownSourcesLauncher)
                    }
                } catch (_: Exception) {
                    Toast.makeText(activity, R.string.failure, Toast.LENGTH_SHORT).show()
                    MainActivity.showSetupDialog(activity)
                }
            }
        }
    }
}