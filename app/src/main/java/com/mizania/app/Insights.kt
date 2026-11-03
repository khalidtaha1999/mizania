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

/** One stacked bar for fixed bills, day-to-day spending, savings and what has no line yet. */
@Composable
fun TypeSplitSection(outs: List<Txn>, cats: List<Category>) {
    val type = cats.associate { it.id to it.type }
    val p = Ui.p
    val parts = listOf(
        Triple(tr("ثابت", "Fixed"), outs.filter { it.categoryId != null && type[it.categoryId] == Db.FIXED }.sumOf { it.amount }, MaterialTheme.colorScheme.tertiary),
        Triple(tr("يومي", "Daily"), outs.filter { it.categoryId != null && type[it.categoryId] == Db.DAILY }.sumOf { it.amount }, p.pine),
        Triple(tr("ادخار واحتياط", "Savings"), outs.filter { it.categoryId != null && type[it.categoryId] == Db.RESERVE }.sumOf { it.amount }, p.mint),
        Triple(tr("بدون بند", "No line"), outs.filter { it.categoryId == null || type[it.categoryId] == null }.sumOf { it.amount }, Gray),
    ).filter { it.second > 0 }
    val total = parts.sumOf { it.second }
    if (total <= 0) return
    Section(tr("ثابت ويومي وادخار", "Fixed, daily and savings")) {
        Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
            parts.forEach { (_, v, c) -> Box(Modifier.weight((v / total).toFloat().coerceAtLeast(0.01f)).height(14.dp).background(c)) }
        }
        Spacer(Modifier.height(8.dp))
        parts.forEach { (name, v, c) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                Text(name, Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                Text("${(v / total * 100).toInt()}%  ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Ui.numbers, fontSize = 13.sp)
                Text(fmt(v), fontFamily = Ui.numbers, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

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
@Composable
fun BudgetHistorySection(db: Db, cats: List<Category>, tick: Int) {
    val budgeted = cats.filter { it.budget > 0 }
    val months = remember(tick) {
        (5 downTo 0).map { i -> Budget.cycle(db, -i).range }.map { r -> r to db.spentByCategory(r.from, r.to) }
    }
    val first = remember(tick) { db.firstTxnTime() }
    if (budgeted.isEmpty() || first == null) return
    val p = Ui.p
    Section(tr("البنود عبر آخر 6 أشهر", "Lines over the last 6 months")) {
        Text(tr("كل دائرة شهر، الأقدم أولاً: أخضر ضمن الميزانية، أحمر فوقها، رمادي بلا بيانات.",
            "Each dot is a month, oldest first: green within budget, red over, grey no data."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        budgeted.forEach { c ->
            var over = 0
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                Text("${c.icon} ${c.name}", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                months.forEach { (r, spent) ->
                    val s = spent[c.id] ?: 0.0
                    val color = when {
                        r.to <= first -> MaterialTheme.colorScheme.surfaceContainerHighest
                        s > c.budget -> { over++; p.pomegranate }
                        else -> p.mint
                    }
                    Box(Modifier.padding(horizontal = 2.dp).size(12.dp).clip(CircleShape).background(color))
                }
            }
            if (over >= 2) Text(tr("فوق الميزانية $over من 6 أشهر: ربما ميزانيته (${fmt(c.budget)}) أقل من حاجتك.",
                "Over budget $over of 6 months: its budget (${fmt(c.budget)}) may be too low."),
                style = MaterialTheme.typography.bodySmall, color = p.pomegranate)
        }
    }
}

private val weekOrder = listOf(Calendar.SATURDAY, Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
    Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)

private fun dayShort(d: Int): String = when (d) {
    Calendar.SATURDAY -> tr("سبت", "Sat"); Calendar.SUNDAY -> tr("أحد", "Sun"); Calendar.MONDAY -> tr("إثنين", "Mon")
    Calendar.TUESDAY -> tr("ثلاثاء", "Tue"); Calendar.WEDNESDAY -> tr("أربعاء", "Wed"); Calendar.THURSDAY -> tr("خميس", "Thu")
    else -> tr("جمعة", "Fri")
}

/** Average day-to-day spending on each weekday, and the hours of the day it happens. */
@Composable
fun HabitsSection(outs: List<Txn>, cats: List<Category>, range: Range) {
    val type = cats.associate { it.id to it.type }
    val daily = outs.filter { it.categoryId == null || type[it.categoryId] == Db.DAILY }
    if (daily.isEmpty()) return
    val now = System.currentTimeMillis()
    val end = minOf(range.to, Period.day().to)
    // How many of each weekday the period has (up to today), so the bars are averages.
    val counts = HashMap<Int, Int>()
    val c = Calendar.getInstance().apply { timeInMillis = range.from }
    while (c.timeInMillis < end) { val d = c.get(Calendar.DAY_OF_WEEK); counts[d] = (counts[d] ?: 0) + 1; c.add(Calendar.DAY_OF_YEAR, 1) }
    val sums = HashMap<Int, Double>()
    daily.forEach { t -> val d = Calendar.getInstance().apply { timeInMillis = t.time }.get(Calendar.DAY_OF_WEEK); sums[d] = (sums[d] ?: 0.0) + t.amount }
    val avg = DoubleArray(7) { i -> val d = weekOrder[i]; (sums[d] ?: 0.0) / max(1, counts[d] ?: 0) }
    val today = weekOrder.indexOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)).takeIf { now in range.from until range.to } ?: -1

    val slots = listOf(
        tr("🌅 صباحاً (5–12)", "🌅 Morning (5–12)") to (5 until 12),
        tr("☀️ ظهراً (12–17)", "☀️ Afternoon (12–17)") to (12 until 17),
        tr("🌆 مساءً (17–22)", "🌆 Evening (17–22)") to (17 until 22),
    )
    fun hour(t: Txn) = Calendar.getInstance().apply { timeInMillis = t.time }.get(Calendar.HOUR_OF_DAY)
    val byTime = slots.map { (n, h) -> n to daily.filter { hour(it) in h } } +
        listOf(tr("🌙 ليلاً (22–5)", "🌙 Night (22–5)") to daily.filter { hour(it) >= 22 || hour(it) < 5 })
    val total = daily.sumOf { it.amount }

    Section(tr("عاداتك في الصرف", "Your spending habits")) {
        if (range.days >= 7) {
            Text(tr("معدل الصرف اليومي حسب يوم الأسبوع", "Average day-to-day spending by weekday"), style = MaterialTheme.typography.titleSmall)
            BarChart(avg, weekOrder.map(::dayShort), null, today)
            val top = avg.indices.maxByOrNull { avg[it] }
            if (top != null && avg[top] > 0) Text(tr("تصرف أكثر يوم ${dayShort(weekOrder[top])}: ${fmt(avg[top])} في المعدل.",
                "You spend most on ${dayShort(weekOrder[top])}: ${fmt(avg[top])} on average."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
        }
        Text(tr("أوقات اليوم", "Times of day"), style = MaterialTheme.typography.titleSmall)
        byTime.forEach { (n, xs) ->
            val v = xs.sumOf { it.amount }
            if (v > 0) KV(n + tr("  (${xs.size} حركة)", "  (${xs.size})"), fmt(v) + "  ·  ${(v / total * 100).toInt()}%")
        }
    }
}

/** Small amounts add up: everything under 3 in the period, and where it went. */
@Composable
fun SmallSpendsSection(outs: List<Txn>) {
    val limit = 3.0
    val small = outs.filter { it.amount < limit }
    if (small.size < 3) return
    val total = outs.sumOf { it.amount }
    val sum = small.sumOf { it.amount }
    val top = small.filter { it.merchant.isNotBlank() }.groupBy { it.merchant.trim().uppercase() }
        .map { (_, v) -> Triple(merchantLabel(v.first().merchant), v.sumOf { it.amount }, v.size) }
        .sortedByDescending { it.second }.take(3)
    Section(tr("المصاريف الصغيرة", "Small spends")) {
        Text(tr("${small.size} حركة أقل من ${fmt(limit)}، مجموعها ${fmt(sum)}", "${small.size} spends under ${fmt(limit)}, ${fmt(sum)} in all"),
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        if (total > 0) Text(tr("هذا ${(sum / total * 100).toInt()}% من صرفك في الفترة.", "That's ${(sum / total * 100).toInt()}% of your spending in the period."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        top.forEach { (n, v, c) -> KV("$n  " + tr("($c مرة)", "(${c}×)"), fmt(v)) }
    }
}

/** Shops you go back to week after week (last 90 days), with what they cost you in a month. */
@Composable
fun RecurringSection(db: Db, tick: Int) {
    val rows = remember(tick) {
        val from = Period.day(-90).from
        db.txns(from, Period.day().to).filter { !it.income && it.merchant.isNotBlank() && !it.kind.contains("كليك") && !it.kind.contains("حوالة") }
            .groupBy { it.merchant.trim().uppercase() }
            .mapNotNull { (_, v) ->
                val weeks = v.map { (it.time - from) / (7 * Period.DAY) }.distinct().size
                if (weeks >= 3) Triple(merchantLabel(v.first().merchant), v.sumOf { it.amount } / 3.0, v.size) else null
            }
            .sortedByDescending { it.second }.take(6)
    }
    if (rows.isEmpty()) return
    Section(tr("محلات تتكرر", "Places you keep going back to")) {
        Text(tr("في آخر 3 أشهر، مع كلفتها في الشهر بالمعدل.", "In the last 3 months, with what they cost a month on average."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        rows.forEach { (n, perMonth, c) -> KV("$n  " + tr("($c مرة)", "(${c}×)"), fmt(perMonth) + tr(" / شهر", " / month")) }
    }
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

/** Where this month ends if spending keeps its pace, and the daily amount that keeps you out of the red. */
@Composable
fun ForecastSection(db: Db, cats: List<Category>, tick: Int) {
    val al = remember(tick) { Budget.allowance(db) }
    // The same outlook as the crisis and month-end cards: one-off big spends don't set the pace.
    val o = remember(tick) { MonthEnd.outlook(db) } ?: return
    val pace = o.pace
    val endLeft = o.endLeft
    val p = Ui.p
    Section(tr("توقّع آخر الشهر", "Month-end forecast")) {
        KV(tr("معدل صرفك اليومي حتى الآن", "Your daily pace so far"), fmt(pace))
        Text(
            if (endLeft >= 0) tr("إذا كمّلت بنفس المعدل، سيبقى معك ${fmt(endLeft)} يوم الراتب.",
                "At this pace you'll have ${fmt(endLeft)} left on payday.")
            else tr("إذا كمّلت بنفس المعدل، ستقصّر ${fmt(-endLeft)} قبل الراتب.",
                "At this pace you'll be ${fmt(-endLeft)} short before payday."),
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
            color = if (endLeft >= 0) p.mint else p.pomegranate, modifier = Modifier.padding(vertical = 6.dp),
        )
        Text(tr("حتى تصل إلى الراتب دون عجز: لا تتجاوز ${fmt(al.allowance)} في اليوم (${daysText(al.daysLeft)} متبقية).",
            "To reach payday without running short: no more than ${fmt(al.allowance)} a day (${daysText(al.daysLeft)} left)."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val saveCsv = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val ok = try { Backup.write(ctx, uri, Backup.exportCsv(db, range.from, range.to, accountId)); true } catch (e: Exception) { false }
            android.widget.Toast.makeText(ctx, if (ok) tr("حُفظ ملف Excel", "Excel file saved") else tr("تعذّر الحفظ", "Couldn't save"),
                android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    Section(tr("تصدير الفترة", "Export this period")) {
        ReceiptButtons(tr("تقرير PDF", "PDF report"), "report.pdf") { Receipt.forPeriod(it, range, label, accountId) }
        androidx.compose.material3.OutlinedButton(
            onClick = { saveCsv.launch("mizania-" + label.replace(Regex("[^0-9]+"), "-").trim('-') + ".csv") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text(tr("حفظ ملف Excel (CSV)", "Save an Excel file (CSV)")) }
    }
}
