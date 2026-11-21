package com.mizania.app

import android.content.Context
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

/**
 * Things worth a heads-up, worked out from the transactions already stored:
 * bills coming up, a week of spending far above the usual, a regular payment that got dearer,
 * and how many days the money would last without the next salary.
 */
object Watch {
    // ---------- bills ----------

    data class Bill(val cat: Category, val due: Long, val amount: Double, val left: Double, val paid: Boolean, val yearly: Boolean)

    /** Fixed lines with a payment day in this budget month, and yearly expenses due within 60 days, soonest first. */
    fun upcomingBills(db: Db, now: Long = System.currentTimeMillis()): List<Bill> {
        val ci = Budget.cycle(db, now = now)
        val spent = db.spentByCategory(ci.range.from, ci.range.to)
        val out = ArrayList<Bill>()
        for (c in db.categories()) {
            if (c.dueDay <= 0 || c.type != Db.FIXED) continue
            val due = dueInCycle(c.dueDay, ci.range.from, ci.range.to)
            val s = spent[c.id] ?: 0.0
            val paid = db.isPaid(c.id, ci.range.from) || (if (c.budget > 0) s >= c.budget * 0.9 else s > 0)
            out += Bill(c, due, c.budget, max(0.0, c.budget - s), paid, false)
        }
        for (a in Annual.items(db)) {
            val st = Annual.state(db, a, now)
            if (st.item.due - now > 60 * Period.DAY) continue
            out += Bill(a.cat, st.item.due, a.amount, max(0.0, a.amount - st.paid), false, true)
        }
        return out.sortedWith(compareBy<Bill> { it.paid }.thenBy { it.due })
    }

    /** The date in [from, to) whose day of month is [day] (the last day of a short month for 29-31). */
    fun dueInCycle(day: Int, from: Long, to: Long): Long {
        val c = Calendar.getInstance().apply { timeInMillis = from }
        repeat(2) {
            val m = c.clone() as Calendar
            m.set(Calendar.DAY_OF_MONTH, 1)
            m.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(m.getActualMaximum(Calendar.DAY_OF_MONTH)))
            m.set(Calendar.HOUR_OF_DAY, 0); m.set(Calendar.MINUTE, 0); m.set(Calendar.SECOND, 0); m.set(Calendar.MILLISECOND, 0)
            if (m.timeInMillis >= dayStart(from) && m.timeInMillis < to) return m.timeInMillis
            c.add(Calendar.MONTH, 1)
        }
        return from
    }

    private fun dayStart(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Nightly: a bill due in two days or tomorrow and not paid yet. Each reminder once. */
    fun remindBills(ctx: Context, db: Db, now: Long = System.currentTimeMillis()) {
        val today = Period.day(0).from
        for (b in upcomingBills(db, now)) {
            if (b.paid) continue
            val days = ((b.due - today) / Period.DAY).toInt()
            if (days != 1 && days != 2) continue
            if (!db.firstTime("bill:${b.cat.id}:${b.due}:$days")) continue
            val whenText = if (days == 1) tr("بكرا", "tomorrow") else tr("بعد يومين", "in two days")
            Notifier.alert(ctx, 900_100L + b.cat.id, tr("${b.cat.icon} ${b.cat.name} مستحق $whenText", "${b.cat.icon} ${b.cat.name} is due $whenText"),
                if (b.left > 0) tr("المبلغ ${fmt(b.left)} — ${Period.dayMonth(b.due)}", "Amount ${fmt(b.left)} — ${Period.dayMonth(b.due)}")
                else tr("لا تنسَ الدفعة — ${Period.dayMonth(b.due)}", "Don't forget the payment — ${Period.dayMonth(b.due)}"))
        }
    }

    // ---------- unusual spending ----------

    data class Unusual(val name: String, val icon: String, val week: Double, val usual: Double)

    /** Day-to-day lines (and unsorted spending) where the last 7 days ran at least twice the usual week. */
    fun unusualWeeks(db: Db, now: Long = System.currentTimeMillis()): List<Unusual> {
        val end = Period.day(0).to
        val weekFrom = end - 7 * Period.DAY
        val histFrom = weekFrom - 56 * Period.DAY
        val week = db.spentByCategory(weekFrom, end)
        val hist = db.spentByCategory(histFrom, weekFrom)
        // Too little history to know what "usual" is.
        val firstTxn = db.txns(0, end).minOfOrNull { it.time } ?: return emptyList()
        if (firstTxn > histFrom + 28 * Period.DAY) return emptyList()
        val weeksOfHistory = max(4.0, (weekFrom - max(histFrom, firstTxn)) / (7.0 * Period.DAY))
        val cats = db.categories().associateBy { it.id }
        return week.mapNotNull { (id, w) ->
            val c = id?.let { cats[it] }
            if (c != null && c.type != Db.DAILY) return@mapNotNull null
            val usual = (hist[id] ?: 0.0) / weeksOfHistory
            if (w >= max(2 * usual, usual + 10.0) && w >= 10.0 && usual > 0)
                Unusual(c?.name ?: tr("بدون بند", "Unsorted"), c?.icon ?: "❔", w, usual) else null
        }.sortedByDescending { it.week - it.usual }
    }

    fun warnUnusual(ctx: Context, db: Db, now: Long = System.currentTimeMillis()) {
        val week = Period.day(0).from / (7 * Period.DAY)
        for (u in unusualWeeks(db, now)) {
            if (!db.firstTime("unusual:${u.name}:$week")) continue
            Notifier.alert(ctx, 900_200L + (u.name.hashCode() and 0xFFF),
                tr("${u.icon} صرف غير معتاد على ${u.name}", "${u.icon} Unusual spending on ${u.name}"),
                tr("صرفت ${fmt(u.week)} آخر 7 أيام، وعادتك حوالي ${fmt(u.usual)} بالأسبوع.",
                    "${fmt(u.week)} in the last 7 days; you usually spend about ${fmt(u.usual)} a week."))
        }
    }

    // ---------- a line running ahead of its budget ----------

    /**
     * Where a day-to-day line is heading this budget month. [forecast] is the likely total by payday:
     * the spending so far scaled by how much of this line usually goes by this day (from the last
     * three months), or by the share of days gone when there is no such history. [perDay] is what a
     * day may still take to finish within the budget (zero or less: the budget is already used up).
     */
    data class Pace(val cat: Category, val spent: Double, val forecast: Double, val perDay: Double, val passed: Int, val left: Int) {
        val over: Double get() = forecast - cat.budget
        /** Worth a word: heading over by more than a tenth of the budget and more than 5. */
        val fast: Boolean get() = over > max(cat.budget * 0.10, 5.0)
    }

    fun pace(db: Db, c: Category, now: Long = System.currentTimeMillis()): Pace? {
        if (c.type != Db.DAILY || c.budget <= 0) return null
        val r = Budget.cycle(db, now = now).range
        val total = max(1, r.days)
        val todayEnd = Period.day(0).to
        val passed = ((min(todayEnd, r.to) - r.from) / Period.DAY).toInt().coerceIn(1, total)
        val left = total - passed
        // Too early to tell, or too late to change anything.
        if (passed < 7 || left < 3) return null
        val until = min(r.to, todayEnd)
        var spent = db.spentByCategory(r.from, until)[c.id] ?: 0.0
        // A spend spread over the month counts only the part of it that belongs to the days gone.
        val spread = db.spreadIds()
        if (spread.isNotEmpty()) db.txns(r.from, until)
            .filter { it.id in spread && it.categoryId == c.id && !it.income }
            .forEach { spent -= it.amount * (1 - passed.toDouble() / total) }
        // How much of this line usually goes by this day: fuel goes in a few big fills, groceries day by day.
        val shares = (1..3).mapNotNull { i ->
            val pr = Budget.cycle(db, offset = -i, now = now).range
            val all = db.spentByCategory(pr.from, pr.to)[c.id] ?: 0.0
            if (all < 5) null
            else (db.spentByCategory(pr.from, min(pr.to, pr.from + passed * Period.DAY))[c.id] ?: 0.0) / all
        }
        val share = if (shares.size >= 2) shares.average().coerceIn(0.15, 1.0) else passed.toDouble() / total
        val forecast = max(spent, spent / share)
        return Pace(c, spent, forecast, (c.budget - spent) / left, passed, left)
    }

    /** Nightly: a line heading over its budget. Once per line a month, again only if it gets clearly worse. */
    fun warnPace(ctx: Context, db: Db, now: Long = System.currentTimeMillis()) {
        val from = Budget.cycle(db, now = now).range.from
        for (c in db.categories()) {
            val p = pace(db, c, now) ?: continue
            if (!p.fast) continue
            val key = "pace:$from:${c.id}"
            val last = db.setting(key, "").toDoubleOrNull()
            if (last != null && p.over < last * 1.5) continue
            db.setSetting(key, p.over.toString())
            val head = tr("صرفت ${fmt(p.spent)} من ${fmt(c.budget)} بأول ${daysText(p.passed)}. بهالسرعة بتوصل حوالي ${fmt(p.forecast)}.",
                "${fmt(p.spent)} of ${fmt(c.budget)} in the first ${daysText(p.passed)}. At this pace it reaches about ${fmt(p.forecast)}.")
            val tail = if (p.perDay > 0.005)
                tr(" عشان تضل ضمن الميزانية، خلّي صرفك عليه حوالي ${fmt(p.perDay)} باليوم.",
                    " To stay within the budget, keep it to about ${fmt(p.perDay)} a day.")
            else tr(" ميزانيته خلصت، وضايل ${daysText(p.left)} للراتب.", " Its budget is used up, with ${daysText(p.left)} to go.")
            Notifier.alert(ctx, 900_300L + c.id, tr("${c.name} ماشي بسرعة", "${c.name} is running fast"), head + tail)
        }
    }

    // ---------- a regular payment got dearer ----------

    data class Rise(val merchant: String, val before: Double, val now: Double)

    /** [t] is a payment that came about monthly at a steady price before and now costs more. */
    fun priceRise(db: Db, t: Txn): Rise? {
        if (t.income || t.manual || t.merchant.isBlank() || t.kind.contains("كليك") || t.kind.contains("حوالة")) return null
        val key = Db.ruleKey(t.merchant)
        val past = db.txns(t.time - 125 * Period.DAY, t.time)
            .filter { !it.income && it.id != t.id && Db.ruleKey(it.merchant) == key }.sortedBy { it.time }
        if (past.size < 2) return null
        val amounts = past.map { it.amount }
        val mid = amounts.sorted()[amounts.size / 2]
        if (amounts.any { kotlin.math.abs(it - mid) > max(0.1, mid * 0.03) }) return null
        val gaps = (past + t).zipWithNext { a, b -> (b.time - a.time) / Period.DAY.toDouble() }
        if (gaps.any { it < 24 || it > 38 }) return null
        return if (t.amount > mid * 1.05 && t.amount - mid >= 0.25) Rise(merchantLabel(t.merchant), mid, t.amount) else null
    }

    // ---------- how long the money lasts ----------

    /** Days the known balance lasts at the average daily spending of the last 90 days; null without a balance or history. */
    fun runwayDays(db: Db, now: Long = System.currentTimeMillis()): Int? {
        val balance = db.bankBalances().filter { !db.isExcluded(it.accountId, it.acctNo) }.mapNotNull { it.balance }
            .takeIf { it.isNotEmpty() }?.sum() ?: return null
        val to = Period.day(0).to
        val first = db.txns(0, to).minOfOrNull { it.time } ?: return null
        val from = max(first, to - 90 * Period.DAY)
        val days = (to - from) / Period.DAY.toDouble()
        if (days < 14) return null
        val perDay = db.spentByCategory(from, to).values.sum() / days
        if (perDay <= 0.01) return null
        return max(0.0, balance / perDay).toInt()
    }
}
