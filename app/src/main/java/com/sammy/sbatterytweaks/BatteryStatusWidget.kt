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
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
            Intent.ACTION_BATTERY_CHANGED,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                updateAllWidgets(context)
            }
        }
    }

    companion object {

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, BatteryStatusWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { id ->
                updateWidget(context, manager, id)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val (status, _) = getBatteryStatus(context)
            val battPercent = getBatteryPercentage(context)

            val views = RemoteViews(context.packageName, R.layout.widget_battery_status)

            views.setTextViewText(R.id.battery_percent, "${battPercent}%")
            views.setTextViewText(R.id.status_text, status)

            when (status) {
                "Charging" -> {
                    views.setInt(
                        R.id.widget_root,
                        "setBackgroundResource",
                        R.drawable.widget_bg_charging
                    )
                    views.setImageViewResource(R.id.status_icon, R.drawable.ic_bolt)
                }

                "Idle" -> {
                    views.setInt(
                        R.id.widget_root,
                        "setBackgroundResource",
                        R.drawable.widget_bg_idle
                    )
                    views.setImageViewResource(R.id.status_icon, R.drawable.ic_shield)
                }

                else -> {
                    views.setInt(
                        R.id.widget_root,
                        "setBackgroundResource",
                        R.drawable.widget_bg_discharging
                    )
                    views.setImageViewResource(R.id.status_icon, R.drawable.ic_battery)
                }
            }

            val launchIntent = Intent(context, MainActivity::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getActivity(context, 0, launchIntent, flags)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

private fun getBatteryStatus(context: Context): Pair<String, Boolean> {
    val prefs = context.getSharedPreferences("battery_widget", Context.MODE_PRIVATE)

    val isBypassed = prefs.getBoolean("idle", false)
    val isCharging = prefs.getBoolean("charging", false)

    return when {
        isBypassed -> "Idle" to false
        isCharging -> "Charging" to true
        else -> "Discharging" to false
    }
}

private fun getBatteryPercentage(context: Context): Int {
    val prefs = context.getSharedPreferences("battery_widget", Context.MODE_PRIVATE)

    val percent = prefs.getInt("battery_level", -1)

    return percent
}