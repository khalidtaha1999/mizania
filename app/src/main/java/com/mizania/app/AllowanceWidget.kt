package com.mizania.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlin.math.max

/** Home-screen widget: what you may still spend today. */
class AllowanceWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        update(ctx, manager, ids)
    }

    companion object {
        fun refreshAll(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx) ?: return
            val ids = m.getAppWidgetIds(ComponentName(ctx, AllowanceWidget::class.java))
            if (ids.isNotEmpty()) update(ctx, m, ids)
            val big = m.getAppWidgetIds(ComponentName(ctx, AllowanceWidgetLarge::class.java))
            if (big.isNotEmpty()) AllowanceWidgetLarge.update(ctx, m, big)
        }

        private fun update(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            val al = try { Budget.allowance(Db.get(ctx)) } catch (e: Exception) { null }
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            for (id in ids) {
                val v = RemoteViews(ctx.packageName, R.layout.widget_allowance)
                v.setTextViewText(R.id.widget_title, tr("المتاح لك اليوم", "You can spend today"))
                v.setInt(R.id.widget_root, "setLayoutDirection",
                    if (Lang.ar) android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR)
                if (al != null) {
                    v.setTextViewText(R.id.widget_amount, fmt(max(0.0, al.leftToday)))
                    v.setTextViewText(
                        R.id.widget_sub,
                        if (al.leftToday < 0) tr("تجاوزت حدك اليوم بمقدار ${fmt(-al.leftToday)}", "${fmt(-al.leftToday)} over today")
                        else al.balance?.let { tr("رصيدك ${fmt(it)}، متبقٍ ${daysText(al.daysLeft)}", "Balance ${fmt(it)}, ${daysText(al.daysLeft)} left") }
                            ?: tr("صرفت اليوم ${fmt(al.spentToday)}، متبقٍ ${daysText(al.daysLeft)}", "Spent ${fmt(al.spentToday)} today, ${daysText(al.daysLeft)} left"),
                    )
                    v.setInt(R.id.widget_root, "setBackgroundResource",
                        if (al.leftToday < 0) R.drawable.widget_bg_over else R.drawable.widget_bg)
                }
                v.setOnClickPendingIntent(R.id.widget_root, open)
                manager.updateAppWidget(id, v)
            }
        }
    }
}

/** The bigger widget: today's amount plus what's left in the three busiest day-to-day lines. */
class AllowanceWidgetLarge : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        update(ctx, manager, ids)
    }

    companion object {
        fun update(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            val db = Db.get(ctx)
            val al = try { Budget.allowance(db) } catch (e: Exception) { null }
            val open = PendingIntent.getActivity(
                ctx, 1, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val lines = try {
                val r = Budget.cycle(db).range
                val spent = db.spentByCategory(r.from, r.to)
                db.categories().filter { it.type == Db.DAILY && it.budget > 0 }
                    .sortedByDescending { spent[it.id] ?: 0.0 }.take(3)
                    .map { c ->
                        val left = c.budget - (spent[c.id] ?: 0.0)
                        "${c.icon}  ${c.name}: " + if (left >= 0) tr("متبقٍ ${fmt(left)}", "${fmt(left)} left")
                            else tr("بزيادة ${fmt(-left)}", "${fmt(-left)} over")
                    }
            } catch (e: Exception) { emptyList() }
            val rows = intArrayOf(R.id.large_line1, R.id.large_line2, R.id.large_line3)
            for (id in ids) {
                val v = RemoteViews(ctx.packageName, R.layout.widget_large)
                v.setInt(R.id.large_root, "setLayoutDirection",
                    if (Lang.ar) android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR)
                v.setTextViewText(R.id.large_title, tr("المتاح لك اليوم", "You can spend today"))
                if (al != null) {
                    v.setTextViewText(R.id.large_amount, fmt(max(0.0, al.leftToday)))
                    v.setTextViewText(R.id.large_sub,
                        if (al.leftToday < 0) tr("تجاوزت حدك اليوم بمقدار ${fmt(-al.leftToday)}", "${fmt(-al.leftToday)} over today")
                        else tr("صرفت اليوم ${fmt(al.spentToday)}، غداً ${fmt(al.tomorrow)}", "Spent ${fmt(al.spentToday)} today, tomorrow ${fmt(al.tomorrow)}"))
                    v.setInt(R.id.large_root, "setBackgroundResource",
                        if (al.leftToday < 0) R.drawable.widget_bg_over else R.drawable.widget_bg)
                }
                rows.forEachIndexed { i, rid ->
                    val text = lines.getOrNull(i)
                    v.setViewVisibility(rid, if (text == null) android.view.View.GONE else android.view.View.VISIBLE)
                    if (text != null) v.setTextViewText(rid, text)
                }
                v.setOnClickPendingIntent(R.id.large_root, open)
                manager.updateAppWidget(id, v)
            }
        }
    }
}
