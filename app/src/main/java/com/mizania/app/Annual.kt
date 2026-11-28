package com.mizania.app

import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

/**
 * Yearly expenses (car licence, insurance, school fees): entered once, then a little is held back every day
 * until the due date, so the bill never lands on one month.
 *
 * Each one is a savings-type line with no monthly budget, so paying it never counts as a day's spending,
 * plus a settings row "annual:{lineId}" = "amount|due|start". The money that should already be put aside —
 * amount × (time since start ÷ time from start to due), less what was already paid — is held out of what you
 * can spend. Once the payment is sorted onto the line, the item moves to next year by itself.
 */
object Annual {
    data class Item(val cat: Category, val amount: Double, val due: Long, val start: Long) {
        val monthly: Double get() = amount * 30.0 / max(30.0, (due - start) / Period.DAY.toDouble())
    }

    data class State(val item: Item, val held: Double, val paid: Double, val overdue: Boolean, val daysToDue: Int)

    private const val KEY = "annual:"

    fun items(db: Db): List<Item> {
        val cats = db.categories().associateBy { it.id }
        return db.settingsWithPrefix(KEY).mapNotNull { (k, v) ->
            val cat = k.toLongOrNull()?.let { cats[it] } ?: return@mapNotNull null
            val p = v.split('|')
            val amount = p.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
            val due = p.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            val start = p.getOrNull(2)?.toLongOrNull() ?: return@mapNotNull null
            Item(cat, amount, due, start)
        }.sortedBy { it.due }
    }

    fun isAnnual(db: Db, catId: Long?): Boolean = catId != null && db.setting("$KEY$catId", "").isNotEmpty()

    private fun save(db: Db, catId: Long, amount: Double, due: Long, start: Long) =
        db.setSetting("$KEY$catId", "$amount|$due|$start")

    /** The next time [month] (1..12) / [day] comes round, at the start of that day, from [now]. */
    fun nextDate(month: Int, day: Int, now: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.MONTH, (month - 1).coerceIn(0, 11))
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.DAY_OF_MONTH, day.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH)))
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= Period.day(0).from) c.add(Calendar.YEAR, 1)
        return c.timeInMillis
    }

    /** Adds a yearly expense, or changes one ([catId] given). Holding starts today. */
    fun add(db: Db, name: String, icon: String, amount: Double, month: Int, day: Int, catId: Long? = null): Long {
        val now = System.currentTimeMillis()
        val id = catId?.also { id ->
            db.categories().firstOrNull { it.id == id }?.let { db.saveCategory(it.copy(name = name, icon = icon)) }
        } ?: db.saveCategory(Category(0, name, 0.0, Db.RESERVE, icon))
        val old = items(db).firstOrNull { it.cat.id == id }
        save(db, id, amount, nextDate(month, day, now), old?.start ?: now)
        return id
    }

    /** Stops treating the line as yearly. The line stays when it already has payments, so nothing is lost. */
    fun remove(db: Db, catId: Long) {
        db.setSetting("$KEY$catId", "")
        if (db.txnsInCategory(catId, 0, Long.MAX_VALUE).isEmpty()) db.deleteCategory(catId)
    }

    /** Marks this year's payment as done by hand (it was paid from somewhere the app does not see). */
    fun markPaid(db: Db, item: Item, now: Long = System.currentTimeMillis()) {
        var due = item.due
        while (due <= now) due = addYear(due)
        if (due == item.due) due = addYear(due)
        save(db, item.cat.id, item.amount, due, now)
    }

    private fun addYear(t: Long) = Calendar.getInstance().apply { timeInMillis = t; add(Calendar.YEAR, 1) }.timeInMillis

    /** Where one yearly expense stands now; moves it to next year once it has been paid. */
    fun state(db: Db, item: Item, now: Long = System.currentTimeMillis()): State {
        var it = item
        val paid = db.spentByCategory(it.start, now + 1)[it.cat.id] ?: 0.0
        if (paid >= it.amount * 0.8) {
            // Paid: next year's holding starts now.
            var due = it.due
            while (due <= now) due = addYear(due)
            if (due == it.due && now < it.due) due = addYear(due)
            save(db, it.cat.id, it.amount, due, now)
            it = it.copy(due = due, start = now)
            return State(it, 0.0, 0.0, false, ((it.due - Period.day(0).from) / Period.DAY).toInt())
        }
        val span = max(Period.DAY, it.due - it.start).toDouble()
        val share = if (now >= it.due) 1.0 else min(1.0, max(0.0, (now - it.start) / span))
        val held = max(0.0, it.amount * share - paid)
        return State(it, held, paid, now >= it.due, ((it.due - Period.day(0).from) / Period.DAY).toInt())
    }

    /** All the money that should already be set aside for yearly expenses. */
    fun held(db: Db, now: Long = System.currentTimeMillis()): Double = items(db).sumOf { state(db, it, now).held }
}
