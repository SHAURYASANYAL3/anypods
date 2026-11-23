package me.anypods.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import me.anypods.app.MainActivity
import me.anypods.app.R
import me.anypods.app.anc.NoiseControlManager
import me.anypods.app.anc.NoiseMode
import me.anypods.app.bluetooth.BluetoothTracker

class AnyPodsWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_SET_ANC = "me.anypods.app.widget.ACTION_SET_ANC"
        const val ACTION_SET_TRANSPARENCY = "me.anypods.app.widget.ACTION_SET_TRANSPARENCY"
        const val ACTION_SET_OFF = "me.anypods.app.widget.ACTION_SET_OFF"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, AnyPodsWidgetProvider::class.java)
            )
            for (id in ids) {
                updateWidget(context, appWidgetManager, id)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_anypods)
            val state = BluetoothTracker.earbudState.value

            views.setTextViewText(
                R.id.widget_left_battery,
                if (state.leftBattery >= 0) "${state.leftBattery}%" + (if (state.isLeftCharging) " ⚡" else "") else "--"
            )
            views.setTextViewText(
                R.id.widget_case_battery,
                if (state.caseBattery >= 0) "${state.caseBattery}%" + (if (state.isCaseCharging) " ⚡" else "") else "--"
            )
            views.setTextViewText(
                R.id.widget_right_battery,
                if (state.rightBattery >= 0) "${state.rightBattery}%" + (if (state.isRightCharging) " ⚡" else "") else "--"
            )

            views.setTextViewText(
                R.id.widget_status,
                if (state.isConnected) "Connected" else "Disconnected"
            )

            // Open app on click title/root
            val appIntent = Intent(context, MainActivity::class.java)
            val appPendingIntent = PendingIntent.getActivity(
                context, 0, appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent)

            // ANC button intent
            val ancIntent = Intent(context, AnyPodsWidgetProvider::class.java).apply {
                action = ACTION_SET_ANC
            }
            val ancPi = PendingIntent.getBroadcast(
                context, 1, ancIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            views.setOnClickPendingIntent(R.id.widget_btn_anc, ancPi)

            // Transparency button intent
            val transIntent = Intent(context, AnyPodsWidgetProvider::class.java).apply {
                action = ACTION_SET_TRANSPARENCY
            }
            val transPi = PendingIntent.getBroadcast(
                context, 2, transIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            views.setOnClickPendingIntent(R.id.widget_btn_transparency, transPi)

            // Off button intent
            val offIntent = Intent(context, AnyPodsWidgetProvider::class.java).apply {
                action = ACTION_SET_OFF
            }
            val offPi = PendingIntent.getBroadcast(
                context, 3, offIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            views.setOnClickPendingIntent(R.id.widget_btn_off, offPi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_SET_ANC -> {
                NoiseControlManager.setMode(context, NoiseMode.NOISE_CANCELLATION)
                updateAllWidgets(context)
            }
            ACTION_SET_TRANSPARENCY -> {
                NoiseControlManager.setMode(context, NoiseMode.TRANSPARENCY)
                updateAllWidgets(context)
            }
            ACTION_SET_OFF -> {
                NoiseControlManager.setMode(context, NoiseMode.OFF)
                updateAllWidgets(context)
            }
        }
    }
}
