package com.sammy.sbatterytweaks

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class BatteryStatusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            renderWidget(
                context = context,
                appWidgetManager = appWidgetManager,
                appWidgetId = appWidgetId,
                fullUpdate = true
            )
        }
    }

    companion object {
        private const val PREFS_NAME = "battery_widget"
        private const val KEY_IDLE = "idle"
        private const val KEY_CHARGING = "charging"
        private const val KEY_BATTERY_LEVEL = "battery_level"

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, BatteryStatusWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)

            ids.forEach { appWidgetId ->
                renderWidget(
                    context = context,
                    appWidgetManager = manager,
                    appWidgetId = appWidgetId,
                    fullUpdate = false
                )
            }
        }

        private fun renderWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            fullUpdate: Boolean
        ) {
            val batteryPercent = getBatteryPercentage(context)
            if (batteryPercent <= 0) return

            val status = getBatteryStatus(context)
            val statusText = context.getString(status.labelRes)

            val views = RemoteViews(context.packageName, R.layout.widget_battery_status).apply {
                setTextViewText(R.id.battery_percent, "$batteryPercent%")
                setTextViewText(R.id.status_text, statusText)
                setInt(R.id.widget_root, "setBackgroundResource", status.backgroundRes)
                setImageViewResource(R.id.status_icon, status.iconRes)
                setOnClickPendingIntent(R.id.widget_root, buildLaunchPendingIntent(context))
            }

            if (fullUpdate) {
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } else {
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
            }
        }

        private fun buildLaunchPendingIntent(context: Context): PendingIntent {
            val launchIntent = Intent(context, MainActivity::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getActivity(context, 0, launchIntent, flags)
        }
    }
}

private data class WidgetStatus(
    val key: String,
    val labelRes: Int,
    val backgroundRes: Int,
    val iconRes: Int
)

private fun getBatteryStatus(context: Context): WidgetStatus {
    val prefs = context.getSharedPreferences("battery_widget", Context.MODE_PRIVATE)

    val isBypassed = prefs.getBoolean("idle", false)
    val isCharging = prefs.getBoolean("charging", false)

    return when {
        isBypassed -> WidgetStatus(
            key = "idle",
            labelRes = R.string.idle,
            backgroundRes = R.drawable.widget_bg_idle,
            iconRes = R.drawable.ic_shield
        )

        isCharging -> WidgetStatus(
            key = "charging",
            labelRes = R.string.charging,
            backgroundRes = R.drawable.widget_bg_charging,
            iconRes = R.drawable.ic_bolt
        )

        else -> WidgetStatus(
            key = "discharging",
            labelRes = R.string.discharging,
            backgroundRes = R.drawable.widget_bg_discharging,
            iconRes = R.drawable.ic_battery
        )
    }
}

private fun getBatteryPercentage(context: Context): Int {
    val prefs = context.getSharedPreferences("battery_widget", Context.MODE_PRIVATE)
    return prefs.getInt("battery_level", -1)
}