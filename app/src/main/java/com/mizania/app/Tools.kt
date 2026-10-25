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

    // ---------- the monthly challenge ----------

    data class Challenge(val catId: Long, val pct: Int, val from: Long, val target: Double)

    fun challenge(db: Db): Challenge? {
        val p = db.setting("challenge", "").split("|")
        if (p.size < 4) return null
        return Challenge(p[0].toLongOrNull() ?: return null, p[1].toIntOrNull() ?: return null,
            p[2].toLongOrNull() ?: return null, p[3].toDoubleOrNull() ?: return null)
    }

    fun setChallenge(db: Db, c: Challenge?) =
        db.setSetting("challenge", c?.let { "${it.catId}|${it.pct}|${it.from}|${it.target}" } ?: "")

    /**
     * The day-to-day line that grew most against its own average of the three months before,
     * with a target 20% under last month's spending on it.
     */
    fun suggestChallenge(db: Db): Pair<Category, Double>? {
        val cats = db.categories().filter { it.type == Db.DAILY }
        val last = Budget.cycle(db, -1).range
        val lastSpent = db.spentByCategory(last.from, last.to)
        val before = (2..4).map { Budget.cycle(db, -it).range }.map { db.spentByCategory(it.from, it.to) }
        return cats.mapNotNull { c ->
            val l = lastSpent[c.id] ?: 0.0
            if (l < 10) return@mapNotNull null
            val avg = before.map { it[c.id] ?: 0.0 }.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.0
            Triple(c, l, l - avg)
        }.maxByOrNull { it.third }?.let { (c, l, _) -> c to l * 0.8 }
    }

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

/** The tools: what if, the monthly challenge, subscriptions, the car, and your hour of work. */
@Composable
fun ToolsSheet(db: Db, tick: Int, onDone: () -> Unit) {
    SheetFrame(onDone) {
        Text(tr("أدوات التخطيط", "Planning tools"), style = MaterialTheme.typography.titleLarge)
        ToolsContent(db, tick)
    }
}

/** The four tools under one row of tabs; used in the sheet and in "My money". */
@Composable
fun ToolsContent(db: Db, tick: Int) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column {
        Text(tr("كلها محسوبة من رصيدك وراتبك وحركاتك.", "All worked out from your balance, salary and transactions."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        val tabs = listOf(tr("شو لو", "What if"), tr("التحدي", "Challenge"), tr("اشتراكات", "Subscriptions"), tr("السيارة", "Car"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEachIndexed { i, n ->
                val on = i == tab
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(50))
                        .background(if (on) Ui.p.pine else MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { tab = i }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(n, color = if (on) Ui.p.onPine else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            0 -> WhatIfTool(db, tick)
            1 -> ChallengeTool(db, tick)
            2 -> SubscriptionsTool(db, tick)
            else -> CarTool(db, tick)
        }
    }
}

@Composable
private fun WhatIfTool(db: Db, tick: Int) {
    var amount by rememberSaveable { mutableStateOf("") }
    val x = amount.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val al = remember(tick) { Budget.allowance(db) }
    val goals = remember(tick) { db.goals().filter { it.target - it.saved > 0 } }
    val hourly = remember(tick) { Plan.hourly(db) }
    val p = Ui.p
    Text(tr("اكتب ثمن ما تفكر بشرائه، وشوف أثره قبل ما تشتري.", "Type the price of what you're thinking of buying and see what it does first."),
        style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = amount, onValueChange = { amount = it }, singleLine = true, label = { Text(tr("المبلغ", "Amount")) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
    if (x == null) return
    val spendable = al.spendable
    Spacer(Modifier.height(10.dp))
    if (spendable != null) {
        val now = al.allowance
        val after = max(0.0, spendable + al.spentToday - x) / al.daysLeft
        KV(tr("مسموحك اليومي الآن", "Your daily allowance now"), fmt(now))
        KV(tr("بعد الشراء", "After buying"), fmt(after), bold = true, color = if (after < now * 0.7) p.pomegranate else p.mint)
        KV(tr("الفرق كل يوم لحد الراتب", "Less each day until payday"), fmt(now - after))
        if (spendable - x < 0) Text(tr("هذا المبلغ أكبر مما يمكنك صرفه قبل الراتب بـ${fmt(x - spendable)}: سيأكل من الفواتير أو الادخار.",
            "That's ${fmt(x - spendable)} more than you can spend before payday: it would eat into bills or savings."),
            color = p.pomegranate, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
    if (hourly != null) KV(tr("يساوي", "Equals"), Plan.hoursText(x / hourly))
    goals.forEach { g ->
        val pace = Plan.goalPace(db, g.id)
        if (pace > 0) KV(tr("هدف «${g.name}» يتأخر", "“${g.name}” is delayed by"),
            tr("حوالي ${"%.1f".format(java.util.Locale.US, x / pace)} شهر", "about ${"%.1f".format(java.util.Locale.US, x / pace)} months"))
    }
}

@Composable
private fun ChallengeTool(db: Db, tick: Int) {
    var local by remember { mutableIntStateOf(0) }
    val c = remember(tick, local) { Plan.challenge(db) }
    val cycle = remember(tick) { Budget.cycle(db).range }
    val cats = remember(tick) { db.categories() }
    val p = Ui.p
    if (c != null) {
        val cat = cats.firstOrNull { it.id == c.catId }
        val current = c.from == cycle.from
        val range = if (current) cycle else Range(c.from, cycle.from)
        val spent = remember(tick, local) { db.spentByCategory(range.from, range.to)[c.catId] ?: 0.0 }
        Text(tr("تحدّي: ${cat?.icon ?: ""} ${cat?.name ?: ""} أقل بـ${c.pct}%", "Challenge: ${cat?.name ?: ""}, ${c.pct}% less"),
            style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { (spent / c.target).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = if (spent > c.target) p.pomegranate else p.mint, trackColor = p.line, drawStopIndicator = {},
        )
        KV(tr("صرفت", "Spent"), fmt(spent))
        KV(tr("الهدف", "Target"), tr("أقل من ${fmt(c.target)}", "under ${fmt(c.target)}"), bold = true)
        if (current) {
            val days = Period.daysLeft(cycle)
            Text(
                if (spent <= c.target) tr("باقي لك ${fmt(c.target - spent)} على ${daysText(days)}: ${fmt((c.target - spent) / days)} في اليوم.",
                    "${fmt(c.target - spent)} left over ${daysText(days)}: ${fmt((c.target - spent) / days)} a day.")
                else tr("تجاوزت الهدف بـ${fmt(spent - c.target)}. حاول أن تتوقف عن هذا البند حتى الراتب.",
                    "Over the target by ${fmt(spent - c.target)}. Try to stop on this line until payday."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(if (spent <= c.target) tr("🎉 نجحت في التحدي! وفّرت ${fmt(c.target / (1 - c.pct / 100.0) - spent)}.",
                "🎉 You did it! You saved ${fmt(c.target / (1 - c.pct / 100.0) - spent)}.")
                else tr("لم تنجح هذه المرة. جرّب تحدياً أخف.", "Not this time. Try a lighter one."),
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                color = if (spent <= c.target) p.mint else p.pomegranate)
        }
        TextButton(onClick = { Plan.setChallenge(db, null); local++ }) {
            Text(if (current) tr("إلغاء التحدي", "Stop the challenge") else tr("تحدٍّ جديد", "New challenge"))
        }
        return
    }
    val suggestion = remember(tick, local) { Plan.suggestChallenge(db) }
    if (suggestion == null) {
        Text(tr("لا يوجد بعد شهر كامل من الصرف اليومي لنقترح تحدياً منه. ارجع بعد الراتب القادم.",
            "There isn't a full month of day-to-day spending yet to suggest a challenge from. Come back after next payday."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val (cat, target) = suggestion
    Text(tr("اقتراح من صرفك", "Suggested from your spending"), style = MaterialTheme.typography.titleSmall)
    Text(tr("صرفك على ${cat.icon} ${cat.name} زاد الشهر الماضي. التحدي: هذا الشهر أقل من ${fmt(target)} (أقل بـ20%).",
        "Your ${cat.name} spending went up last month. The challenge: under ${fmt(target)} this month (20% less)."),
        style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 6.dp))
    Button(onClick = { Plan.setChallenge(db, Plan.Challenge(cat.id, 20, cycle.from, target)); local++ }) {
        Text(tr("ابدأ التحدي", "Start the challenge"))
    }
}

@Composable
private fun SubscriptionsTool(db: Db, tick: Int) {
    val subs = remember(tick) { Plan.subscriptions(db) }
    if (subs.isEmpty()) {
        Text(tr("لم نجد دفعات تتكرر كل شهر بنفس المبلغ في آخر 4 أشهر.", "No payments repeating every month for the same amount in the last 4 months."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val monthly = subs.sumOf { it.monthly }
    Text(tr("اشتراكاتك ${fmt(monthly)} في الشهر", "Your subscriptions: ${fmt(monthly)} a month"),
        style = MaterialTheme.typography.titleMedium)
    Text(tr("يعني ${fmt(monthly * 12)} في السنة.", "That's ${fmt(monthly * 12)} a year."),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    subs.forEach { s ->
        KV(s.name, fmt(s.monthly) + tr(" / شهر", " / month"))
        Text(tr("${s.times} مرات، آخرها ${Period.dayMonth(s.lastAt)} · ${fmt(s.monthly * 12)} في السنة",
            "${s.times} times, last on ${Period.dayMonth(s.lastAt)} · ${fmt(s.monthly * 12)} a year"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Ui.p.line)
    }
    Text(tr("اشتراك لم تعد تستعمله؟ إلغاؤه يوفّر سعره كل شهر.", "One you no longer use? Cancelling saves its price every month."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CarTool(db: Db, tick: Int) {
    val lines = remember(tick) { Plan.carLines(db) }
    val ids = lines.map { it.id }.toSet()
    val cycle = remember(tick) { Budget.cycle(db).range }
    val now = remember(tick) { Plan.carSpent(db, cycle.from, cycle.to, ids) }
    val past = remember(tick) { (1..3).map { Budget.cycle(db, -it).range }.map { Plan.carSpent(db, it.from, it.to, ids) }.filter { it > 0 } }
    val avg = if (past.isNotEmpty()) past.average() else now
    val hourly = remember(tick) { Plan.hourly(db) }
    if (now <= 0 && past.isEmpty()) {
        Text(tr("لم نجد صرفاً على السيارة: لا بنود باسم بنزين أو مواصلات أو صيانة، ولا محطات وقود في الحركات.",
            "No car spending found: no lines named fuel, transport or maintenance, and no fuel stations in your transactions."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (lines.isNotEmpty()) Text(tr("من بنود: ", "From lines: ") + lines.joinToString("، ") { "${it.icon} ${it.name}" } + tr("، ومحطات الوقود", ", and fuel stations"),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    KV(tr("هذا الشهر", "This month"), fmt(now), bold = true)
    KV(tr("معدلك الشهري", "Your monthly average"), fmt(avg))
    KV(tr("في السنة تقريباً", "About a year"), fmt(avg * 12))
    KV(tr("كل يوم دوام (22 يوماً)", "Each workday (22 a month)"), fmt(avg / 22))
    if (hourly != null && avg > 0) KV(tr("تكلفك السيارة شهرياً", "The car costs you monthly"), Plan.hoursText(avg / hourly))
}

/** A small card on home: the active challenge's progress, or the way into the tools. */
@Composable
fun ToolsCard(db: Db, tick: Int, onOpen: () -> Unit) {
    val c = remember(tick) { Plan.challenge(db) }
    val cycle = remember(tick) { Budget.cycle(db).range }
    val p = Ui.p
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).border(1.dp, p.line, RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen).padding(16.dp)
    ) {
        if (c != null && c.from == cycle.from) {
            val cat = remember(tick) { db.category(c.catId) }
            val spent = remember(tick) { db.spentByCategory(cycle.from, cycle.to)[c.catId] ?: 0.0 }
            Text(tr("🏆 تحدّي ${cat?.name ?: ""}: ${fmt(spent)} من ${fmt(c.target)}", "🏆 ${cat?.name ?: ""} challenge: ${fmt(spent)} of ${fmt(c.target)}"),
                style = MaterialTheme.typography.titleSmall)
            LinearProgressIndicator(
                progress = { (spent / c.target).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (spent > c.target) p.pomegranate else p.mint, trackColor = p.line, drawStopIndicator = {},
            )
        } else {
            Text(tr("🧮 أدوات التخطيط", "🧮 Planning tools"), style = MaterialTheme.typography.titleSmall)
            Text(tr("شو لو اشتريت… · تحدي الشهر · اشتراكاتك · تكلفة السيارة", "What if… · monthly challenge · subscriptions · car cost"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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

/** Occasions to save for, with their next (approximate) date. Hijri dates move about 11 days a year. */
object Occasions {
    data class Occ(val icon: String, val name: String, val date: Long)

    private fun d(y: Int, m: Int, day: Int) = java.util.Calendar.getInstance().apply { clear(); set(y, m - 1, day) }.timeInMillis

    private val dated = listOf(
        "🌙" to tr("رمضان", "Ramadan") to listOf(d(2027, 2, 8), d(2028, 1, 28), d(2029, 1, 16)),
        "🎁" to tr("عيد الفطر", "Eid al-Fitr") to listOf(d(2027, 3, 10), d(2028, 2, 27), d(2029, 2, 15)),
        "🐑" to tr("عيد الأضحى", "Eid al-Adha") to listOf(d(2027, 5, 17), d(2028, 5, 5), d(2029, 4, 24)),
    )

    /** The next date of each occasion, plus the school year (1 September). */
    fun upcoming(): List<Occ> {
        val now = System.currentTimeMillis()
        val out = dated.mapNotNull { (head, dates) -> dates.firstOrNull { it > now }?.let { Occ(head.first, head.second, it) } }.toMutableList()
        val c = java.util.Calendar.getInstance()
        var school = d(c.get(java.util.Calendar.YEAR), 9, 1)
        if (school <= now) school = d(c.get(java.util.Calendar.YEAR) + 1, 9, 1)
        out += Occ("🎒", tr("المدارس", "Back to school"), school)
        return out.sortedBy { it.date }
    }
}
