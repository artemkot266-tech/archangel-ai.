package com.archangel.ai.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.archangel.ai.MainActivity
import com.archangel.ai.R

/** Виджет на рабочий стол: сфера Архангела, касание — сразу слушает. */
class OrbWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val voice = PendingIntent.getActivity(
            context, 8001,
            Intent(context, MainActivity::class.java).setAction("com.archangel.ai.action.VOICE")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_orb)
            views.setOnClickPendingIntent(R.id.widget_root, voice)
            manager.updateAppWidget(id, views)
        }
    }
}
