package com.focus.guard

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class FocusWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) { refreshWidgets(context) }
}

fun refreshWidgets(c: Context) {
    try {
        val m = AppWidgetManager.getInstance(c)
        val ids = m.getAppWidgetIds(ComponentName(c, FocusWidget::class.java))
        if (ids.isEmpty()) return
        val s = Store(c); val lang = resolveLang(s.lang)
        val used = if (hasUsage(c)) Usage.scan(c, s).total else 0L
        val left = ((s.limit * 60_000L - used) / 60_000L).coerceAtLeast(0L)
        val rv = RemoteViews(c.packageName, R.layout.widget)
        rv.setTextViewText(R.id.w_time, "$left ${tl(lang, "min")}")
        rv.setTextViewText(R.id.w_sub, tl(lang, "wLeft"))
        rv.setTextViewText(R.id.w_streak, "${s.streak()} ${tl(lang, "days")} ✓")
        rv.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        m.updateAppWidget(ids, rv)
    } catch (_: Exception) {}
}
