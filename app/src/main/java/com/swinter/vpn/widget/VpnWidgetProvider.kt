package com.swinter.vpn.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.swinter.vpn.R
import com.swinter.vpn.data.VpnState
import com.swinter.vpn.data.VpnStateStore
import com.swinter.vpn.data.model.AppConfig
import com.swinter.vpn.receiver.VpnToggleReceiver

class VpnWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val state = VpnStateStore.get(context)
            val views = RemoteViews(context.packageName, R.layout.widget_vpn)

            // Status text — show server name when connected
            val appConfig = try { AppConfig.load(context) } catch (_: Exception) { null }
            val serverName = appConfig?.activeServer?.let { s ->
                s.name.ifBlank { s.address }.take(20)
            }
            val statusText = when (state) {
                VpnState.CONNECTED -> serverName?.let { "$it" } ?: "VPN ON"
                VpnState.CONNECTING -> "..."
                VpnState.DISCONNECTING -> "..."
                VpnState.DISCONNECTED -> "VPN OFF"
            }
            views.setTextViewText(R.id.widget_status, statusText)

            // Toggle icon tint
            val tintColor = if (state == VpnState.CONNECTED) 0xFF4ADE80.toInt() else 0xFF9CA3AF.toInt()
            views.setInt(R.id.widget_icon, "setColorFilter", tintColor)

            // Toggle click
            val toggleIntent = Intent(context, VpnToggleReceiver::class.java).apply {
                action = VpnToggleReceiver.ACTION_TOGGLE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, VpnWidgetProvider::class.java))
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }
    }
}
