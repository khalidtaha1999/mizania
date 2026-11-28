package com.mizania.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * The home-screen widgets, in the app's sticker look:
 *   small  - what you can spend today, on the sunny card (red when over);
 *   week   - this week's free money: spent of the amount, a bar, what's left;
 *   large  - today's amount, the week, three main lines with bars, and two buttons: add a spend, sort.
 * They refresh after every bank SMS, whenever the app goes to the background, at midnight and every half hour.
 */

private const val INK = 0xFF1E1B3A.toInt()
private const val RED = 0xFFC62828.toInt()

/** Colors for the white or the deep-indigo card, following the app's theme. */
private class Skin(dark: Boolean) {
    val card = if (dark) R.drawable.widget_card_dark else R.drawable.widget_card
    val text = if (dark) 0xFFF3F1FF.toInt() else INK
    val muted = if (dark) 0xFFB3AFD0.toInt() else 0xFF5E5A7A.toInt()
    val red = if (dark) 0xFFFF8A8A.toInt() else RED
    val plainButton = if (dark) R.drawable.widget_btn_plain_dark else R.drawable.widget_btn_plain
}

private fun n2(v: Double) = String.format(Locale.US, "%,.2f", v)

private fun dir(v: RemoteViews, root: Int) =
    v.setInt(root, "setLayoutDirection", if (Lang.ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)

/** Opens the app; [extra] asks it to open the add sheet or the quick sort straight away. */
private fun openApp(ctx: Context, code: Int, extra: String? = null): PendingIntent =
    PendingIntent.getActivity(
        ctx, code,
        Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (extra != null) putExtra(extra, true)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

/** One of three bars (green, amber, red) shown, at [percent]. */
private fun bar(v: RemoteViews, ids: IntArray, tone: Int, percent: Int) {
    ids.forEachIndexed { i, id ->
        v.setViewVisibility(id, if (i == tone) View.VISIBLE else View.GONE)
        v.setProgressBar(id, 100, percent.coerceIn(0, 100), false)
    }
}

/** The line icon for a budget-line emoji, as a drawable the widget can show. */
private fun lineIcon(e: String): Int = when (e.removeSuffix("️")) {
    "🏠" -> R.drawable.wic_home; "🤝" -> R.drawable.wic_handshake; "💝" -> R.drawable.wic_favorite
    "💡" -> R.drawable.wic_lightbulb; "💳" -> R.drawable.wic_credit_card; "🛒" -> R.drawable.wic_shopping_cart
    "🚗", "🚘", "⛽" -> R.drawable.wic_directions_car; "☕" -> R.drawable.wic_local_cafe
    "🚑" -> R.drawable.wic_medical_services; "💰", "🎯" -> R.drawable.wic_savings; "🍔", "🍽" -> R.drawable.wic_lunch_dining
    "👕" -> R.drawable.wic_checkroom; "💊" -> R.drawable.wic_medication; "🎓" -> R.drawable.wic_school
    "📱" -> R.drawable.wic_smartphone; "🎁" -> R.drawable.wic_redeem; "👶" -> R.drawable.wic_child_care
    "✈" -> R.drawable.wic_flight; "🏋", "🏋️" -> R.drawable.wic_fitness_center; "🐾" -> R.drawable.wic_pets
    "🧾" -> R.drawable.wic_receipt_long; "🔧" -> R.drawable.wic_build; "🎮" -> R.drawable.wic_sports_esports
    "📦" -> R.drawable.wic_inventory_2; "💵" -> R.drawable.wic_payments; "🌐" -> R.drawable.wic_language
    "🏦" -> R.drawable.wic_account_balance; "🎉", "👥" -> R.drawable.wic_group
    else -> R.drawable.wic_category
}

/** A line's own color, darkened enough to read on white. */
private fun deep(c: Int): Int {
    val f = 0.62f
    val r = (Color.red(c) * f + 0x1E * (1 - f)).roundToInt()
    val g = (Color.green(c) * f + 0x1B * (1 - f)).roundToInt()
    val b = (Color.blue(c) * f + 0x3A * (1 - f)).roundToInt()
    return Color.rgb(r, g, b)
}

/** A line's own color, lifted enough to read on the deep-indigo card. */
private fun light(c: Int): Int {
    val f = 0.55f
    return Color.rgb((Color.red(c) * f + 255 * (1 - f)).roundToInt(), (Color.green(c) * f + 255 * (1 - f)).roundToInt(),
        (Color.blue(c) * f + 255 * (1 - f)).roundToInt())
}

/** Small widget: what you can spend today. */
class AllowanceWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        update(ctx, manager, ids)
        Widgets.scheduleMidnight(ctx)
    }

    companion object {
        /** Every widget of the app, all sizes. */
        fun refreshAll(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx) ?: return
            m.getAppWidgetIds(ComponentName(ctx, AllowanceWidget::class.java)).let { if (it.isNotEmpty()) update(ctx, m, it) }
            m.getAppWidgetIds(ComponentName(ctx, AllowanceWidgetLarge::class.java)).let { if (it.isNotEmpty()) AllowanceWidgetLarge.update(ctx, m, it) }
            m.getAppWidgetIds(ComponentName(ctx, WeeklyWidget::class.java)).let { if (it.isNotEmpty()) WeeklyWidget.update(ctx, m, it) }
        }

        private fun update(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            val al = try { Budget.allowance(Db.get(ctx)) } catch (e: Exception) { null }
            for (id in ids) {
                val v = RemoteViews(ctx.packageName, R.layout.widget_allowance)
                dir(v, R.id.widget_root)
                if (al != null) {
                    val over = al.leftToday < 0
                    v.setTextViewText(R.id.widget_title, if (over) tr("تجاوزت حد اليوم", "Over today's limit") else tr("تقدر تصرف اليوم", "You can spend today"))
                    v.setTextViewText(R.id.widget_amount, n2(if (over) -al.leftToday else al.leftToday) + " " + currency())
                    v.setTextColor(R.id.widget_amount, if (over) RED else INK)
                    v.setTextViewText(R.id.widget_sub,
                        if (over) tr("حاول ما تصرف أكثر اليوم", "Try not to spend more today")
                        else tr("صرفت ${n2(al.spentToday)} · بكرا ${n2(al.tomorrow)}", "Spent ${n2(al.spentToday)} · tomorrow ${n2(al.tomorrow)}"))
                    v.setInt(R.id.widget_root, "setBackgroundResource", if (over) R.drawable.widget_bg_over else R.drawable.widget_bg)
                } else v.setTextViewText(R.id.widget_title, tr("تقدر تصرف اليوم", "You can spend today"))
                v.setOnClickPendingIntent(R.id.widget_root, openApp(ctx, 0))
                manager.updateAppWidget(id, v)
            }
        }
    }
}

/** Week widget: this week's free money. */
class WeeklyWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        update(ctx, manager, ids)
        Widgets.scheduleMidnight(ctx)
    }

    companion object {
        fun update(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            val db = Db.get(ctx)
            val w = try { if (Weekly.on(db)) Weekly.week(db) else null } catch (e: Exception) { null }
            val bars = intArrayOf(R.id.week_green, R.id.week_amber, R.id.week_red)
            val k = Skin(Look.isDark(ctx))
            for (id in ids) {
                val v = RemoteViews(ctx.packageName, R.layout.widget_week)
                dir(v, R.id.week_root)
                v.setInt(R.id.week_root, "setBackgroundResource", k.card)
                v.setTextColor(R.id.week_title, k.muted)
                v.setTextColor(R.id.week_sub, k.muted)
                v.setTextColor(R.id.week_amount, k.text)
                v.setTextViewText(R.id.week_title, tr("مصروف الأسبوع", "This week's money"))
                if (w == null) {
                    v.setTextViewText(R.id.week_amount, "—")
                    v.setViewVisibility(R.id.week_left, View.GONE)
                    bar(v, bars, 0, 0)
                    v.setTextViewText(R.id.week_sub, tr("شغّله من الإعدادات ← البنود ← مصروف الأسبوع", "Turn it on in Settings → Lines → Weekly money"))
                } else {
                    val tone = when { w.over || w.limitedByBank -> 2; w.near -> 1; else -> 0 }
                    v.setTextViewText(R.id.week_amount, tr("${n2(w.spent)} من ${n2(w.cap)}", "${n2(w.spent)} of ${n2(w.cap)}"))
                    v.setTextColor(R.id.week_amount, if (w.over) k.red else k.text)
                    v.setViewVisibility(R.id.week_left, View.VISIBLE)
                    v.setTextViewText(R.id.week_left,
                        if (w.over) tr("زيادة ${n2(-w.byPlan)}", "${n2(-w.byPlan)} over") else tr("باقي ${n2(w.left)}", "${n2(w.left)} left"))
                    v.setInt(R.id.week_left, "setBackgroundResource",
                        when (tone) { 2 -> R.drawable.widget_pill_red; 1 -> R.drawable.widget_pill_amber; else -> R.drawable.widget_pill_green })
                    v.setTextColor(R.id.week_left, when (tone) { 2 -> 0xFF8E1B1B.toInt(); 1 -> 0xFF6A4E00.toInt(); else -> 0xFF0B5236.toInt() })
                    bar(v, bars, tone, (w.share * 100).roundToInt())
                    v.setTextViewText(R.id.week_sub, when {
                        w.over -> tr("تجاوزتوا الحد، وضايل ${daysText(w.daysLeft)}", "Over the limit, ${daysText(w.daysLeft)} to go")
                        w.limitedByBank -> tr("اللي معك فعلاً ${n2(w.left)} لحد الراتب", "Only ${n2(w.left)} is really free until payday")
                        w.daysLeft == 1 -> tr("آخر يوم بالأسبوع", "Last day of the week")
                        else -> tr("حوالي ${n2(w.perDay)} باليوم لـ${daysText(w.daysLeft)}", "About ${n2(w.perDay)} a day for ${daysText(w.daysLeft)}")
                    })
                }
                v.setOnClickPendingIntent(R.id.week_root, openApp(ctx, 2))
                manager.updateAppWidget(id, v)
            }
        }
    }
}

/** Large widget: today, the week, three main lines, and buttons to add a spend or sort. */
class AllowanceWidgetLarge : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        update(ctx, manager, ids)
        Widgets.scheduleMidnight(ctx)
    }

    companion object {
        private data class Line(val icon: Int, val color: Int, val light: Int, val name: String, val left: String, val over: Boolean, val percent: Int)

        fun update(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
            val db = Db.get(ctx)
            val al = try { Budget.allowance(db) } catch (e: Exception) { null }
            val w = try { if (Weekly.on(db)) Weekly.week(db) else null } catch (e: Exception) { null }
            val pending = try { db.pending().size } catch (e: Exception) { 0 }
            val lines = try {
                val r = Budget.cycle(db).range
                val spent = db.spentByCategory(r.from, r.to)
                db.categories().filter { it.type == Db.DAILY && it.budget > 0 }
                    .sortedByDescending { spent[it.id] ?: 0.0 }.take(3)
                    .map { c ->
                        val s = spent[c.id] ?: 0.0
                        val left = c.budget - s
                        Line(lineIcon(c.icon), deep(c.color.toInt()), light(c.color.toInt()), c.name,
                            if (left >= 0) tr("باقي ${n2(left)}", "${n2(left)} left") else tr("زيادة ${n2(-left)}", "${n2(-left)} over"),
                            left < 0, (s / c.budget * 100).roundToInt())
                    }
            } catch (e: Exception) { emptyList() }
            val rows = listOf(
                intArrayOf(R.id.line1, R.id.line1_icon, R.id.line1_name, R.id.line1_left, R.id.line1_bar),
                intArrayOf(R.id.line2, R.id.line2_icon, R.id.line2_name, R.id.line2_left, R.id.line2_bar),
                intArrayOf(R.id.line3, R.id.line3_icon, R.id.line3_name, R.id.line3_left, R.id.line3_bar),
            )
            val k = Skin(Look.isDark(ctx))
            for (id in ids) {
                val v = RemoteViews(ctx.packageName, R.layout.widget_large)
                dir(v, R.id.large_root)
                v.setInt(R.id.large_root, "setBackgroundResource", k.card)
                v.setTextColor(R.id.large_week_text, k.text)
                v.setInt(R.id.btn_sort, "setBackgroundResource", k.plainButton)
                v.setTextColor(R.id.btn_sort, k.text)
                if (al != null) {
                    val over = al.leftToday < 0
                    v.setTextViewText(R.id.large_title, if (over) tr("تجاوزت حد اليوم", "Over today's limit") else tr("تقدر تصرف اليوم", "You can spend today"))
                    v.setTextViewText(R.id.large_amount, n2(if (over) -al.leftToday else al.leftToday) + " " + currency())
                    v.setTextColor(R.id.large_amount, if (over) RED else INK)
                    v.setTextViewText(R.id.large_sub,
                        tr("صرفت ${n2(al.spentToday)} · بكرا ${n2(al.tomorrow)}", "Spent ${n2(al.spentToday)} · tomorrow ${n2(al.tomorrow)}"))
                    v.setInt(R.id.large_top, "setBackgroundResource", if (over) R.drawable.widget_sun_over else R.drawable.widget_sun)
                }
                // The week, when it is on.
                v.setViewVisibility(R.id.large_week, if (w == null) View.GONE else View.VISIBLE)
                if (w != null) {
                    val tone = when { w.over || w.limitedByBank -> 2; w.near -> 1; else -> 0 }
                    v.setTextViewText(R.id.large_week_text,
                        tr("الأسبوع: ${n2(w.spent)} من ${n2(w.cap)} · ", "Week: ${n2(w.spent)} of ${n2(w.cap)} · ") +
                            if (w.over) tr("زيادة ${n2(-w.byPlan)}", "${n2(-w.byPlan)} over") else tr("باقي ${n2(w.left)}", "${n2(w.left)} left"))
                    bar(v, intArrayOf(R.id.lweek_green, R.id.lweek_amber, R.id.lweek_red), tone, (w.share * 100).roundToInt())
                }
                rows.forEachIndexed { i, ids5 ->
                    val l = lines.getOrNull(i)
                    v.setViewVisibility(ids5[0], if (l == null) View.GONE else View.VISIBLE)
                    if (l != null) {
                        v.setImageViewResource(ids5[1], l.icon)
                        v.setInt(ids5[1], "setColorFilter", if (k.text == INK) l.color else l.light)
                        v.setTextViewText(ids5[2], l.name)
                        v.setTextColor(ids5[2], k.text)
                        v.setTextViewText(ids5[3], l.left)
                        v.setTextColor(ids5[3], if (l.over) k.red else k.muted)
                        v.setProgressBar(ids5[4], 100, l.percent.coerceIn(0, 100), false)
                    }
                }
                v.setTextViewText(R.id.btn_add_text, tr("مصروف", "Spend"))
                v.setTextViewText(R.id.btn_sort, if (pending > 0) tr("صنّف ($pending)", "Sort ($pending)") else tr("الحركات", "Activity"))
                v.setOnClickPendingIntent(R.id.large_root, openApp(ctx, 1))
                v.setOnClickPendingIntent(R.id.btn_add, openApp(ctx, 3, MainActivity.EXTRA_ADD_CASH))
                v.setOnClickPendingIntent(R.id.btn_sort, openApp(ctx, 4, if (pending > 0) MainActivity.EXTRA_TRIAGE else MainActivity.EXTRA_ACTIVITY))
                manager.updateAppWidget(id, v)
            }
        }
    }
}

/** A refresh just after midnight, so the widgets never show yesterday's numbers in the morning. */
object Widgets {
    const val MIDNIGHT = "com.mizania.app.MIDNIGHT"

    fun scheduleMidnight(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val c = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 1); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val pi = PendingIntent.getBroadcast(ctx, 8, Intent(ctx, DailyReceiver::class.java).setAction(MIDNIGHT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // Inexact on purpose: no special permission, and a few minutes late is fine.
        am.setAndAllowWhileIdle(AlarmManager.RTC, c.timeInMillis, pi)
    }
}
