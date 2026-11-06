package com.dizicambazi.app.work

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dizicambazi.app.R
import com.dizicambazi.app.data.Repo
import com.dizicambazi.app.data.Series
import com.dizicambazi.app.ui.MainActivity
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Home-screen widget that lists tonight's episodes. Reads the local store directly; no network. */
class WidgetProvider : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        Repo.init(ctx.applicationContext)
        ids.forEach { id -> mgr.updateAppWidget(id, build(ctx)) }
    }

    companion object {
        private val TR = Locale("tr")
        private val DAYS = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, WidgetProvider::class.java))
            if (ids.isEmpty()) return
            Repo.init(ctx.applicationContext)
            ids.forEach { mgr.updateAppWidget(it, build(ctx)) }
        }

        private fun tonight(): List<Series> {
            val today = LocalDate.now(ZoneId.of("Europe/Istanbul"))
            val wd = DAYS[today.dayOfWeek.value - 1]; val weekday = today.dayOfWeek.value <= 5
            return Repo.series.value.filter { !it.ended && (it.day == wd || (it.day == "Hafta içi her gün" && weekday)) }
                .sortedBy { it.time }
        }

        private fun build(ctx: Context): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget)
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
            rv.setOnClickPendingIntent(R.id.widget_root, open)
            rv.removeAllViews(R.id.widget_list)
            val list = tonight()
            if (list.isEmpty()) {
                val row = RemoteViews(ctx.packageName, R.layout.widget_row)
                row.setTextViewText(R.id.w_time, "")
                row.setTextViewText(R.id.w_title, "Bu akşam yeni bölüm yok")
                row.setTextViewText(R.id.w_channel, "")
                rv.addView(R.id.widget_list, row)
            } else list.take(4).forEach { s ->
                val row = RemoteViews(ctx.packageName, R.layout.widget_row)
                row.setTextViewText(R.id.w_time, s.time)
                row.setTextViewText(R.id.w_title, s.name)
                row.setTextViewText(R.id.w_channel, s.channel)
                rv.addView(R.id.widget_list, row)
            }
            return rv
        }
    }
}
