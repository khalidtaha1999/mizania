package com.mizania.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * One nightly check at about 10 pm:
 * a short day summary, tomorrow's due payments, and the month report on the cycle's last day.
 */
object Daily {
    const val HOUR = 22

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, HOUR); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 60_000) add(Calendar.DAY_OF_YEAR, 1)
        }
        // Inexact on purpose: needs no special alarm permission and a few minutes late is fine.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pending(ctx))
    }

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 7, Intent(ctx, DailyReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun run(ctx: Context) {
        val db = Db.get(ctx)
        val now = System.currentTimeMillis()
        val dayKey = Period.day().from

        if (db.setting("daily_summary", "1") == "1" && db.firstTime("summary:$dayKey")) {
            val al = Budget.allowance(db, now)
            val today = Period.day()
            val spentAll = db.spentByCategory(today.from, today.to).values.sum()
            val pending = db.pending().size
            val text = buildString {
                append(tr("صرفت اليوم ${fmt(spentAll)}", "You spent ${fmt(spentAll)} today"))
                if (al.leftToday < 0) append(tr(" وتجاوزت حدك بمقدار ${fmt(-al.leftToday)}", ", ${fmt(-al.leftToday)} over your limit"))
                append(tr(". المتاح لك غداً ${fmt(al.tomorrow)}.", ". Tomorrow you can spend ${fmt(al.tomorrow)}."))
                al.balance?.let { append(tr(" رصيدك ${fmt(it)}.", " Balance: ${fmt(it)}.")) }
                if (pending > 0) append(tr(" لديك ${txnsText(pending)} بحاجة إلى تصنيف.", " ${txnsText(pending)} to sort."))
            }
            Notifier.alert(ctx, 900_001, tr("ملخص اليوم", "Today's summary"), text)
        }

        val ci = Budget.cycle(db, now = now)
        val cycle = ci.range
        val spent = db.spentByCategory(cycle.from, cycle.to)
        // Bills due in two days or tomorrow, and a week of spending far above the usual.
        Watch.remindBills(ctx, db, now)
        if (db.setting("watch_unusual", "1") == "1") Watch.warnUnusual(ctx, db, now)
        // A day-to-day line heading over its budget, while there's still time to slow down.
        if (db.setting("watch_pace", "1") == "1") Watch.warnPace(ctx, db, now)
        // The weekly backup file, when a folder was picked for it.
        AutoBackup.runIfDue(ctx, db, now)
        // The week's free money: near it, over it, and how the week went.
        Weekly.notify(ctx, db, now)

        // The month's outlook: a surplus to save in the last days, or a crisis on the way. Each once a month.
        MonthEnd.outlook(db)?.let { o ->
            MonthEnd.suggestion(o)?.let { s ->
                if (db.firstTime("monthend:${cycle.from}")) Notifier.alert(ctx, 900_003,
                    tr("🌱 عندك فايض قبل الراتب", "🌱 Something left before payday"),
                    tr("بمعدل صرفك سيبقى حوالي ${fmt(o.endLeft)}. ضع ${fmt(s)} في الادخار من الصفحة الرئيسية.",
                        "At your pace about ${fmt(o.endLeft)} will be left. Put ${fmt(s)} aside from the home page."))
            }
            if (MonthEnd.inCrisis(o) && db.firstTime("crisis:${cycle.from}")) Notifier.alert(ctx, 900_004,
                tr("⚠️ الشهر لن يكفي بهذا المعدل", "⚠️ The month won't last at this pace"),
                tr("ستقصّر حوالي ${fmt(-o.endLeft)} قبل الراتب. افتح التطبيق لترى ما يمكنك تأجيله.",
                    "You'll be about ${fmt(-o.endLeft)} short before payday. Open the app to see what can wait."))
        }

        // Last day of the budget month: the month report.
        if (cycle.to - Period.day().to < Period.HOUR && db.firstTime("month:${cycle.from}")) {
            val prev = Budget.cycle(db, offset = -1, now = now).range
            val cats = db.categories()
            val prevSpent = db.spentByCategory(prev.from, prev.to)
            val total = spent.values.sum()
            val tip = monthTip(cats, spent, prevSpent, current = false) ?: ""
            Notifier.alert(ctx, 900_002, tr("تقرير الشهر جاهز 📊", "Your month report is ready 📊"),
                (if (ci.received && !db.noSalary()) tr("صرفت ${fmt(total)} من راتب ${fmt(ci.salary)}. ", "You spent ${fmt(total)} of a ${fmt(ci.salary)} salary. ")
                else tr("صرفت ${fmt(total)} هذا الشهر. ", "You spent ${fmt(total)} this month. ")) + tip)
        }
    }
}

class DailyReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        try {
            Daily.run(ctx)
        } finally {
            Daily.schedule(ctx)
            AllowanceWidget.refreshAll(ctx)
        }
    }
}

/** Alarms are cleared on reboot; set the nightly one again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Daily.schedule(ctx)
    }
}
