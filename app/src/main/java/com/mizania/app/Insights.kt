package com.mizania.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import kotlin.math.max

/*
 * Extra report sections: where the money goes (fixed / daily / savings, people, lines over the months),
 * habits (weekdays, time of day, small spends, regular shops) and the trend (balance, forecast, saving rate).
 */

private val Gray = Color(0xFF9E9E9E)

/** People you sent money to and got money from (CliQ and transfers), by total. */
@Composable
fun PeopleSection(list: List<Txn>) {
    fun isPerson(t: Txn) = (t.kind.contains("كليك") || t.kind.contains("حوالة")) && t.merchant.isNotBlank() && !t.merchant.startsWith("من ")
    fun top(xs: List<Txn>) = xs.groupBy { it.merchant.trim().uppercase() }
        .map { (_, v) -> Triple(merchantLabel(v.first().merchant), v.sumOf { it.amount }, v.size) }
        .sortedByDescending { it.second }.take(5)
    val sent = top(list.filter { !it.income && isPerson(it) })
    val got = top(list.filter { it.income && isPerson(it) })
    if (sent.isEmpty() && got.isEmpty()) return
    Section(tr("حوّلت واستلمت", "Sent and received")) {
        if (sent.isNotEmpty()) {
            Text(tr("حوّلت إلى", "Sent to"), style = MaterialTheme.typography.titleSmall)
            sent.forEach { (n, v, c) -> KV("$n  " + tr("($c مرة)", "(${c}×)"), "−" + fmt(v)) }
        }
        if (got.isNotEmpty()) {
            Text(tr("استلمت من", "Received from"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            got.forEach { (n, v, c) -> KV("$n  " + tr("($c مرة)", "(${c}×)"), "+" + fmt(v), color = Ui.p.mint) }
        }
    }
}

/** Each budgeted line over the last six budget months: a green dot when within budget, red when over. */
private fun dayShort(d: Int): String = when (d) {
    Calendar.SATURDAY -> tr("سبت", "Sat"); Calendar.SUNDAY -> tr("أحد", "Sun"); Calendar.MONDAY -> tr("إثنين", "Mon")
    Calendar.TUESDAY -> tr("ثلاثاء", "Tue"); Calendar.WEDNESDAY -> tr("أربعاء", "Wed"); Calendar.THURSDAY -> tr("خميس", "Thu")
    else -> tr("جمعة", "Fri")
}

/** The balance day by day (or month by month for long periods). */
@Composable
fun BalanceLineSection(db: Db, range: Range, tick: Int) {
    val points = remember(tick, range) {
        val end = minOf(range.to, Period.day().to)
        val times = ArrayList<Long>()
        if (range.days <= 62) {
            var t = range.from + Period.DAY
            while (t <= end) { times += t; t += Period.DAY }
        } else {
            Period.months(Range(range.from, end)).forEach { times += it.to }
        }
        times.zip(db.balanceSeries(times)).filter { it.second != null }.map { it.first to it.second!! }
    }
    if (points.size < 2) return
    val line = Ui.p.pine
    val grid = Ui.p.line
    val lo = points.minOf { it.second }
    val hi = points.maxOf { it.second }
    Section(tr("رصيدك عبر الفترة", "Your balance over the period")) {
        Row {
            Text(tr("أعلى ${fmt(hi)}", "High ${fmt(hi)}"), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tr("أدنى ${fmt(lo)}", "Low ${fmt(lo)}"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(140.dp).padding(vertical = 8.dp)) {
            val span = (hi - lo).takeIf { it > 0.0001 } ?: 1.0
            val n = points.size
            // Oldest on the right, like the other charts in Arabic.
            fun x(i: Int) = if (Lang.ar) size.width - i * size.width / (n - 1) else i * size.width / (n - 1)
            fun y(v: Double) = (size.height - (v - lo) / span * size.height).toFloat()
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1f)
            val path = Path()
            points.forEachIndexed { i, (_, v) -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, line, style = Stroke(width = 3.dp.toPx()))
            drawCircle(line, 4.dp.toPx(), Offset(x(n - 1), y(points.last().second)))
        }
        KV(tr("في البداية", "At the start"), fmt(points.first().second))
        KV(tr("في النهاية", "At the end"), fmt(points.last().second), bold = true)
    }
}

/** How much of the income stayed or went into goals, and how it compares with the months before. */
@Composable
fun SavingRateSection(db: Db, range: Range, inc: Double, out: Double, monthly: Boolean, offset: Int, tick: Int) {
    if (inc <= 0) return
    val goals = remember(tick, range) { db.goalDepositsIn(range.from, range.to) }
    val rate = (inc - out) / inc
    val history = remember(tick, monthly, offset) {
        if (!monthly) emptyList() else (3 downTo 1).mapNotNull { i ->
            val r = Budget.cycle(db, offset - i).range
            val list = db.txns(r.from, r.to)
            val ii = list.filter { it.income }.sumOf { it.amount }
            if (ii <= 0) null else Period.label(Period.MODE_MONTH, r) to (ii - list.filter { !it.income }.sumOf { it.amount }) / ii
        }
    }
    val p = Ui.p
    Section(tr("معدل الادخار", "Saving rate")) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${(rate * 100).toInt()}%", fontFamily = Ui.numbers, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                color = if (rate >= 0) p.mint else p.pomegranate)
            Text(tr("  من الدخل بقي معك", "  of income kept"), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (goals > 0.0005) KV(tr("زاد في أهداف الادخار", "Goals grew by"), fmt(goals), color = p.mint)
        else if (goals < -0.0005) KV(tr("سحبت من أهداف الادخار", "Taken out of goals"), fmt(-goals), color = p.pomegranate)
        history.forEach { (label, r) ->
            KV(label, "${(r * 100).toInt()}%", color = if (r >= 0) MaterialTheme.colorScheme.onSurfaceVariant else p.pomegranate)
        }
    }
}

/** Share or save the period as a PDF report, or as an Excel (CSV) file. */
@Composable
fun ExportSection(db: Db, range: Range, label: String, accountId: Long?) {
    Section(tr("تقرير الفترة", "Report for this period")) {
        ReceiptButtons(tr("تقرير PDF", "PDF report"), "report.pdf") { Receipt.forPeriod(it, range, label, accountId) }
    }
}
