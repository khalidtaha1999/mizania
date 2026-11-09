package com.mizania.app

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

data class Range(val from: Long, val to: Long) {
    val days: Int get() = max(1, ((to - from + Period.HOUR) / Period.DAY).toInt())
}

object Period {
    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR

    const val MODE_DAY = 0
    const val MODE_WEEK = 1
    const val MODE_MONTH = 2
    const val MODE_YEAR = 3
    const val MODE_ALL = 4
    /** A range the user picked (or "since payday"). */
    const val MODE_CUSTOM = 5

    private fun cal(t: Long = System.currentTimeMillis()): Calendar =
        Calendar.getInstance().apply {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

    private fun setDay(c: Calendar, day: Int) {
        c.set(Calendar.DAY_OF_MONTH, min(day, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
    }

    /** Budget month that starts on [startDay] and contains [now], shifted by [offset] months. */
    fun cycle(startDay: Int, now: Long = System.currentTimeMillis(), offset: Int = 0): Range {
        val c = cal(now)
        val today = c.get(Calendar.DAY_OF_MONTH)
        c.set(Calendar.DAY_OF_MONTH, 1)
        if (today < startDay) c.add(Calendar.MONTH, -1)
        c.add(Calendar.MONTH, offset)
        setDay(c, startDay)
        val from = c.timeInMillis
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.add(Calendar.MONTH, 1)
        setDay(c, startDay)
        return Range(from, c.timeInMillis)
    }

    /** Start of the day [t] falls in. */
    fun dayOf(t: Long): Long = cal(t).timeInMillis

    fun day(offset: Int = 0): Range {
        val c = cal()
        c.add(Calendar.DAY_OF_YEAR, offset)
        val from = c.timeInMillis
        c.add(Calendar.DAY_OF_YEAR, 1)
        return Range(from, c.timeInMillis)
    }

    /** Week starting Saturday, as in Jordan. */
    fun week(offset: Int = 0): Range {
        val c = cal()
        val back = (c.get(Calendar.DAY_OF_WEEK) - Calendar.SATURDAY + 7) % 7
        c.add(Calendar.DAY_OF_YEAR, -back + offset * 7)
        val from = c.timeInMillis
        c.add(Calendar.DAY_OF_YEAR, 7)
        return Range(from, c.timeInMillis)
    }

    /** Calendar year (1 January to 1 January), shifted by [offset] years. */
    fun year(offset: Int = 0): Range {
        val c = cal()
        c.set(Calendar.DAY_OF_YEAR, 1)
        c.add(Calendar.YEAR, offset)
        val from = c.timeInMillis
        c.add(Calendar.YEAR, 1)
        return Range(from, c.timeInMillis)
    }

    /** From the day of [first] (the first recorded transaction) to the end of today. */
    fun all(first: Long?): Range {
        val end = day().to
        return Range(min(cal(first ?: System.currentTimeMillis()).timeInMillis, end - DAY), end)
    }

    /** Calendar months touching [r], as (start, end) pairs. */
    fun months(r: Range): List<Range> {
        val out = ArrayList<Range>()
        val c = cal(r.from)
        c.set(Calendar.DAY_OF_MONTH, 1)
        while (c.timeInMillis < r.to) {
            val from = c.timeInMillis
            c.add(Calendar.MONTH, 1)
            out += Range(max(from, r.from), min(c.timeInMillis, r.to))
        }
        return out
    }

    fun range(mode: Int, offset: Int, startDay: Int): Range = when (mode) {
        MODE_DAY -> day(offset)
        MODE_WEEK -> week(offset)
        MODE_YEAR -> year(offset)
        else -> cycle(startDay, offset = offset)
    }

    /** Days left in the range counting today, at least 1. */
    fun daysLeft(r: Range, now: Long = System.currentTimeMillis()): Int {
        val today = cal(now).timeInMillis
        return max(1, ceil((r.to - today).toDouble() / DAY).toInt())
    }

    private val dayNamesAr = arrayOf("", "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت")
    private val dayNamesEn = arrayOf("", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val dayNames: Array<String> get() = if (Lang.ar) dayNamesAr else dayNamesEn

    fun label(mode: Int, r: Range): String {
        val dm = SimpleDateFormat("d/M", Locale.US)
        return when (mode) {
            MODE_DAY -> {
                val c = Calendar.getInstance().apply { timeInMillis = r.from }
                "${dayNames[c.get(Calendar.DAY_OF_WEEK)]} ${dm.format(Date(r.from))}"
            }
            MODE_YEAR -> SimpleDateFormat("yyyy", Locale.US).format(Date(r.from))
            MODE_ALL, MODE_CUSTOM -> "${dayMonthYear(r.from)} – ${dayMonthYear(r.to - 1)}"
            else -> "${dm.format(Date(r.from))} – ${dm.format(Date(r.to - 1))}"
        }
    }

    fun dayMonthYear(t: Long): String = SimpleDateFormat("d/M/yyyy", Locale.US).format(Date(t))

    fun dayMonth(t: Long): String = SimpleDateFormat("d/M", Locale.US).format(Date(t))

    fun clock(t: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(t))

    fun stamp(t: Long): String = SimpleDateFormat("d/M  HH:mm", Locale.US).format(Date(t))
}
