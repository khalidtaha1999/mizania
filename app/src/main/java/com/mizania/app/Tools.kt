package com.mizania.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/*
 * Planning tools worked out from what the app already has: the balance and allowance, the salary SMS,
 * the budget lines and their history, and the transactions themselves. Nothing extra to type in,
 * except the amount you want to try in "what if".
 */
object Plan {
    /** Working hours in a month: 8 hours, Sunday to Thursday. */
    private const val HOURS_A_MONTH = 22.0 * 8

    /** Latest salary from the bank SMS (or the one typed in settings). */
    fun salary(db: Db): Double = db.salaries().lastOrNull()?.amount ?: db.salary()

    /** What an hour of work brings in, or null when no salary is known. */
    fun hourly(db: Db): Double? = salary(db).takeIf { it > 0 }?.div(HOURS_A_MONTH)

    fun hoursText(hours: Double): String = when {
        hours < 1 -> tr("${(hours * 60).toInt()} دقيقة شغل", "${(hours * 60).toInt()} minutes of work")
        hours < 10 -> tr("${"%.1f".format(java.util.Locale.US, hours)} ساعة شغل", "${"%.1f".format(java.util.Locale.US, hours)} hours of work")
        else -> tr("${hours.toInt()} ساعة شغل (${"%.1f".format(java.util.Locale.US, hours / 8)} يوم)", "${hours.toInt()} hours of work (${"%.1f".format(java.util.Locale.US, hours / 8)} days)")
    }

    /** What a goal has been getting a month lately (last 90 days, corrections left out). */
    fun goalPace(db: Db, goalId: Long): Double =
        db.goalEntries(goalId).filter { it.note != Db.CORRECTION && it.time >= Period.day(-90).from }.sumOf { it.amount } / 3.0

    // ---------- subscriptions ----------

    data class Sub(val name: String, val monthly: Double, val times: Int, val lastAt: Long)

    /**
     * Payments that come back about once a month, to the same shop, for about the same amount
     * (Google One, internet, a gym…): found in the last 4 months of transactions.
     */
    fun subscriptions(db: Db): List<Sub> {
        val list = db.txns(Period.day(-125).from, Period.day().to)
            .filter { !it.income && it.merchant.isNotBlank() && !it.kind.contains("كليك") && !it.kind.contains("حوالة") && !it.manual }
        return list.groupBy { it.merchant.trim().uppercase() }.mapNotNull { (_, v) ->
            if (v.size < 2) return@mapNotNull null
            val s = v.sortedBy { it.time }
            val amounts = s.map { it.amount }
            val mid = amounts.sorted()[amounts.size / 2]
            if (amounts.any { abs(it - mid) > max(0.5, mid * 0.15) }) return@mapNotNull null
            val gaps = s.zipWithNext { a, b -> (b.time - a.time) / Period.DAY.toDouble() }
            if (gaps.any { it < 24 || it > 38 }) return@mapNotNull null
            Sub(merchantLabel(s.last().merchant), mid, s.size, s.last().time)
        }.sortedByDescending { it.monthly }
    }

    // ---------- the car ----------

    private val carWords = listOf("بنزين", "وقود", "مواصلات", "سيارة", "سياره", "صيانة", "ترخيص", "تأمين السيارة", "غسيل", "fuel", "car", "petrol")
    private val stationWords = listOf("PETROL", "GAS STATION", "MANASEER", "TOTAL", "GULF", "JO PETROL", "JOPETROL", "STATION")

    /** Lines that are about the car, by their names. */
    fun carLines(db: Db): List<Category> = db.categories().filter { c -> carWords.any { c.name.lowercase().contains(it) } }

    /** Car spending in a range: its lines, plus fuel stations not sorted into them. */
    fun carSpent(db: Db, from: Long, to: Long, lines: Set<Long>): Double =
        db.txns(from, to).filter { t ->
            !t.income && (t.categoryId in lines ||
                (t.categoryId == null && stationWords.any { t.merchant.uppercase().contains(it) }))
        }.sumOf { it.amount }
}

/** Under a goal: is it on track at its recent pace, and when it would be reached. */
@Composable
fun GoalPaceLine(db: Db, g: Goal, tick: Int) {
    val left = g.target - g.saved
    if (left <= 0) return
    val pace = remember(tick, g.id) { Plan.goalPace(db, g.id) }
    val p = Ui.p
    val text = when {
        pace <= 0 -> tr("لم تضف إليه شيئاً في آخر 3 أشهر.", "Nothing put in during the last 3 months.")
        g.deadline != null -> {
            val months = max(1, ceil((g.deadline - System.currentTimeMillis()).toDouble() / (30.44 * Period.DAY)).toInt())
            val need = left / months
            if (pace >= need) tr("✅ على المسار: معدلك ${fmt(pace)} بالشهر.", "✅ On track: you put in ${fmt(pace)} a month.")
            else tr("⚠️ متأخر: معدلك ${fmt(pace)} بالشهر وتحتاج ${fmt(need)}.", "⚠️ Behind: ${fmt(pace)} a month, you need ${fmt(need)}.")
        }
        else -> {
            val months = ceil(left / pace).toInt()
            tr("بمعدلك (${fmt(pace)} بالشهر) تصل بعد ${monthsText(months)}.", "At ${fmt(pace)} a month you'll get there in ${monthsText(months)}.")
        }
    }
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = if (text.startsWith("⚠️")) p.pomegranate else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp))
}

