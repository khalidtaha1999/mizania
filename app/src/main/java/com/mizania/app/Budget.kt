package com.mizania.app

import java.util.Calendar
import kotlin.math.max

/** The budget month and what is known about its salary. */
data class CycleInfo(
    val range: Range,
    /** Salary that landed inside [range] (0 = not yet). */
    val salary: Double,
    /** When the salary landed, if it did. */
    val salaryAt: Long?,
    /** Expected day of the next salary (start of day). */
    val nextSalary: Long,
    /** The month starts on the salary day (false = fixed calendar day, no salary seen yet). */
    val bySalary: Boolean,
    /** The next salary was due and has not arrived. */
    val late: Boolean,
) {
    val received: Boolean get() = salary > 0
}

/** Today's spending room, shared by the home screen, the widget and the nightly summary. */
data class Allowance(
    /** What may still be spent today (negative = over). */
    val leftToday: Double,
    /** Today's full allowance before today's spending. */
    val allowance: Double,
    /** Flexible spending today (not rent, bills or savings). */
    val spentToday: Double,
    val daysLeft: Int,
    /** Allowance for tomorrow if nothing more is spent today. */
    val tomorrow: Double,
    /** Money in the bank accounts we know the balance of; null when none is known. */
    val balance: Double?,
    /** Fixed bills not yet paid this month plus savings and emergency money not yet used. */
    val committed: Double,
    /** balance - committed, or null when the balance is unknown. */
    val spendable: Double?,
    val cycle: CycleInfo,
) {
    val fromBalance: Boolean get() = balance != null
}

object Budget {
    private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun addMonths(t: Long, k: Int): Long = Calendar.getInstance().apply {
        timeInMillis = t
        add(Calendar.MONTH, k)
    }.timeInMillis

    /** Salary days, one per pay period: deposits within a week of each other (salary + bonus) count once. */
    private fun salaryStarts(salaries: List<Salary>): List<Long> {
        val out = ArrayList<Long>()
        for (s in salaries) {
            val d = startOfDay(s.time)
            if (out.isEmpty() || d - out.last() > 7 * Period.DAY) out += d
        }
        return out
    }

    /**
     * The budget month containing now, shifted by [offset] months.
     * With salary mode on and at least one salary seen, a month runs from one salary to the next
     * (the current one to the expected next salary, one calendar month after the last).
     */
    fun cycle(db: Db, offset: Int = 0, now: Long = System.currentTimeMillis()): CycleInfo {
        val salaries = db.salaries().filter { it.time <= now }
        val starts = if (!db.noSalary() && db.setting("cycle_mode", "salary") == "salary") salaryStarts(salaries) else emptyList()
        if (starts.isEmpty()) {
            val r = Period.cycle(db.cycleDay(), now, offset)
            val inRange = salaries.filter { it.time >= r.from && it.time < r.to }
            return CycleInfo(r, inRange.sumOf { it.amount }, inRange.lastOrNull()?.time,
                Period.cycle(db.cycleDay(), now).to, bySalary = false, late = false)
        }
        val last = starts.size - 1
        val expected = addMonths(starts[last], 1)
        val tomorrow = startOfDay(now) + Period.DAY
        val idx = last + offset
        val range = when {
            idx in 0 until last -> Range(starts[idx], starts[idx + 1])
            idx == last -> Range(starts[last], max(expected, tomorrow))
            idx < 0 -> Range(addMonths(starts[0], idx), addMonths(starts[0], idx + 1))
            else -> Range(addMonths(starts[last], idx - last), addMonths(starts[last], idx - last + 1))
        }
        val inRange = salaries.filter { it.time >= range.from && it.time < range.to }
        return CycleInfo(
            range, inRange.sumOf { it.amount }, inRange.firstOrNull()?.time, expected,
            bySalary = true, late = offset == 0 && now >= expected,
        )
    }

    /** Day-to-day spending: no line yet, or a "daily" line. Fixed lines and reserves never count against a day. */
    fun countsToday(t: Txn, type: Map<Long, Int>): Boolean =
        !t.income && t.categoryId.let { it == null || type[it] == Db.DAILY || type[it] == null }

    /**
     * Today's spending that is big next to the day's allowance and not decided yet: worth asking whether it was
     * a one-off to spread over the month (a full tank, a pharmacy run).
     */
    fun bigOneOff(db: Db, al: Allowance): Txn? {
        val type = db.categories().associate { it.id to it.type }
        val today = Period.day()
        val limit = max(10.0, al.allowance)
        return db.txns(today.from, today.to)
            .filter { !it.manual && countsToday(it, type) && it.amount > limit && db.spreadChoice(it.id) == null }
            .maxByOrNull { it.amount }
    }

    fun allowance(db: Db, now: Long = System.currentTimeMillis()): Allowance {
        val ci = cycle(db, now = now)
        val cats = db.categories()
        val type = cats.associate { it.id to it.type }
        val today = Period.day()
        val daysLeft = Period.daysLeft(ci.range, now)
        val spentCycle = db.spentByCategory(ci.range.from, ci.range.to)

        val spread = db.spreadIds()
        val todays = db.txns(today.from, today.to).filter { countsToday(it, type) && it.id !in spread }
        val bankToday = todays.filter { !it.manual }.sumOf { it.amount }
        val cashToday = todays.filter { it.manual }.sumOf { it.amount }
        val flexToday = bankToday + cashToday

        val known = db.bankBalances().filter { !db.isExcluded(it.accountId, it.acctNo) }.mapNotNull { it.balance }
        if (known.isNotEmpty()) {
            val balance = known.sum()
            val committed = cats.filter { (it.type == Db.FIXED || it.type == Db.RESERVE) && !db.isPaid(it.id, ci.range.from) }
                .sumOf { max(0.0, it.budget - (spentCycle[it.id] ?: 0.0)) }
            val spendable = balance - committed
            // Bank spending today already left the balance: add it back to get this morning's room.
            val allowance = max(0.0, spendable + bankToday) / daysLeft
            val tomorrow = if (daysLeft > 1) max(0.0, spendable - cashToday) / (daysLeft - 1) else 0.0
            return Allowance(allowance - flexToday, allowance, flexToday, daysLeft, tomorrow,
                balance, committed, spendable, ci)
        }

        // No balance known yet: plan from the budget lines instead.
        val dailyIds = cats.filter { it.type == Db.DAILY }.map { it.id }.toSet()
        val dailyBudget = cats.filter { it.type == Db.DAILY }.sumOf { it.budget }
        val dailyCycle = spentCycle.filterKeys { it != null && it in dailyIds }.values.sum()
        val dailyToday = db.spentByCategory(today.from, today.to).filterKeys { it != null && it in dailyIds }.values.sum()
        val allowance = max(0.0, dailyBudget - (dailyCycle - dailyToday)) / daysLeft
        val tomorrow = if (daysLeft > 1) max(0.0, dailyBudget - dailyCycle) / (daysLeft - 1) else 0.0
        return Allowance(allowance - dailyToday, allowance, dailyToday, daysLeft, tomorrow, null, 0.0, null, ci)
    }
}
