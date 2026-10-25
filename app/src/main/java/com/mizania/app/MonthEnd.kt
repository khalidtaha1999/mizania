package com.mizania.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The end of the month, read from the balance and the pace of day-to-day spending:
 * money left over in the last days before payday is offered to a savings goal, and a month heading
 * for the red gets a crisis plan (what to put off, what to cut, and the daily cap that gets you there).
 */
object MonthEnd {
    /** How the rest of the month looks at today's pace. */
    data class Outlook(
        val spendable: Double,
        /** Usual day-to-day spending a day: the last two weeks, one-off big spends left out. */
        val pace: Double,
        val daysLeft: Int,
        /** What is left on payday if the pace holds (negative = short). */
        val endLeft: Double,
        /** The same pace per budget line (null = no line yet). */
        val perLine: Map<Long?, Double> = emptyMap(),
    )

    fun outlook(db: Db): Outlook? {
        val al = Budget.allowance(db)
        val spendable = al.spendable ?: return null
        val type = db.categories().associate { it.id to it.type }
        val spread = db.spreadIds()
        // The last 14 full days, across the salary if needed, so a few days after payday don't skew it.
        val today = Period.day().from
        val first = db.firstTxnTime()?.let { Period.dayOf(it) } ?: today
        val from = max(Period.day(-14).from, first)
        val days = ((today - from) / Period.DAY).toInt()
        // A single spend far above a normal day (furniture, a full tank) is a one-off, not the pace.
        val oneOff = max(20.0, al.allowance * 3)
        val usual = db.txns(from, today).filter { Budget.countsToday(it, type) && it.id !in spread && it.amount <= oneOff }
        val pace = if (days >= 3) usual.sumOf { it.amount } / days else al.allowance
        val perLine = if (days >= 3) usual.groupBy { it.categoryId }.mapValues { (_, v) -> v.sumOf { it.amount } / days } else emptyMap()
        // Today's spending already left the balance; today's remaining room counts as one more day at the pace.
        val endLeft = spendable - pace * max(0, al.daysLeft - 1) - max(0.0, pace - al.spentToday)
        return Outlook(spendable, pace, al.daysLeft, endLeft, perLine)
    }

    /** A round amount (multiple of 5) to save from what is left over, keeping a 15% cushion. */
    fun suggestion(o: Outlook): Double? {
        if (o.daysLeft > 5 || o.endLeft < 10) return null
        val s = floor(o.endLeft * 0.85 / 5) * 5
        return s.takeIf { it >= 5 }
    }

    /** In trouble: short before payday at this pace, or already spending past the balance. */
    fun inCrisis(o: Outlook): Boolean = o.spendable < 0 || o.endLeft < -5

    fun saved(db: Db, cycleFrom: Long): Boolean = db.setting("monthend:$cycleFrom", "") == "done"
    fun skipped(db: Db, cycleFrom: Long): Boolean = db.setting("monthend:$cycleFrom", "") == "skip"
    fun mark(db: Db, cycleFrom: Long, v: String) = db.setSetting("monthend:$cycleFrom", v)
}

/** "Before payday: put 40 into احتياط?" — only in the last five days, and only when there is a surplus. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonthEndCard(db: Db, tick: Int, refresh: () -> Unit) {
    var local by remember { mutableIntStateOf(0) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val o = remember(tick, local) { MonthEnd.outlook(db) } ?: return
    val cycle = remember(tick) { Budget.cycle(db).range }
    val suggested = MonthEnd.suggestion(o) ?: return
    if (MonthEnd.skipped(db, cycle.from)) return
    val goals = remember(tick, local) { db.goals().filter { it.target > it.saved } }
    val p = Ui.p
    val done = MonthEnd.saved(db, cycle.from)
    var goal by remember { mutableStateOf(goals.firstOrNull()?.id) }
    var amount by remember { mutableStateOf(suggested) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(22.dp))
            .background(p.mint.copy(alpha = 0.10f)).border(1.dp, p.mint.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        if (done) {
            Text(tr("🌱 وضعت فايض هذا الشهر في الادخار. أحسنت!", "🌱 You saved this month's surplus. Well done!"),
                style = MaterialTheme.typography.titleSmall)
            return@Column
        }
        Text(tr("🌱 باقي ${daysText(o.daysLeft)} للراتب، ومعك فايض", "🌱 ${daysText(o.daysLeft)} to payday, and you have some left"),
            style = MaterialTheme.typography.titleSmall)
        Text(tr("بمعدل صرفك (${fmt(o.pace)} في اليوم) سيبقى معك حوالي ${fmt(o.endLeft)} يوم الراتب. ضع جزءاً منه في الادخار قبل أن يذهب على أشياء صغيرة.",
            "At your pace (${fmt(o.pace)} a day) about ${fmt(o.endLeft)} will be left on payday. Put some of it aside before it goes on small things."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            listOf(suggested, floor(suggested / 2 / 5) * 5).filter { it >= 5 }.distinct().forEach { a ->
                FilterChip(amount == a, { amount = a }, { Text(fmt(a)) })
            }
        }
        if (goals.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                goals.forEach { g -> FilterChip(goal == g.id, { goal = g.id }, { Text("${g.icon} ${g.name}") }) }
            }
        }
        Text(tr("حوّلها إلى حساب الادخار أو محفظتك، أو ضعها جانباً نقداً، ثم سجّلها هنا.",
            "Move it to your savings account or wallet, or put it aside in cash, then record it here."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Button(enabled = goal != null, onClick = {
                val g = goal ?: return@Button
                // Booked on the goal's line, so it counts as money that left the day-to-day budget.
                val line = db.goalLineOrDefault(g)
                if (line != null) db.addToGoalOnLine(g, amount, line) else db.addToGoal(g, amount)
                MonthEnd.mark(db, cycle.from, "done")
                AllowanceWidget.refreshAll(ctx); local++; refresh()
            }, shape = RoundedCornerShape(12.dp)) { Text(tr("وضعت ${fmt(amount)}", "Saved ${fmt(amount)}")) }
            TextButton(onClick = { MonthEnd.mark(db, cycle.from, "skip"); local++; refresh() }) { Text(tr("ليس هذا الشهر", "Not this month")) }
        }
        if (goals.isEmpty()) Text(tr("أنشئ هدف ادخار من «أموالي ← الادخار» لتضع فيه.", "Create a savings goal in “My money → Savings” to put it in."),
            style = MaterialTheme.typography.bodySmall, color = p.pomegranate)
    }
}

/**
 * Crisis mode: the month is heading for the red. Says by how much, then what can be put off
 * (savings and reserves not set aside yet), what to cut (the day-to-day lines eating most),
 * subscriptions that could wait, and the daily cap that still reaches payday.
 */
@Composable
fun CrisisCard(db: Db, tick: Int, refresh: () -> Unit) {
    var local by remember { mutableIntStateOf(0) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val o = remember(tick, local) { MonthEnd.outlook(db) } ?: return
    if (!MonthEnd.inCrisis(o)) return
    val cycle = remember(tick) { Budget.cycle(db).range }
    val cats = remember(tick, local) { db.categories() }
    val spent = remember(tick, local) { db.spentByCategory(cycle.from, cycle.to) }
    var open by remember { mutableStateOf(true) }
    val p = Ui.p
    val short = max(0.0, -o.endLeft)
    val elapsed = max(1, ((Period.day().from - cycle.from) / Period.DAY).toInt() + 1)

    // Money not set aside yet that could wait a month: reserve lines, still reserved.
    val putOff = cats.filter { it.type == Db.RESERVE && it.budget > 0 && !db.isPaid(it.id, cycle.from) }
        .map { it to max(0.0, it.budget - (spent[it.id] ?: 0.0)) }.filter { it.second > 0 }
    // Day-to-day lines eating most this month, with what stopping each would free until payday.
    val cuts = cats.filter { it.type == Db.DAILY }.map { it to (o.perLine[it.id] ?: 0.0) }
        .filter { it.second > 0.2 }.sortedByDescending { it.second }.take(4)
    val subs = remember(tick) { Plan.subscriptions(db) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(22.dp))
            .background(p.pomegranate.copy(alpha = 0.10f)).border(1.dp, p.pomegranate.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("⚠️ وضع الأزمة", "⚠️ Crisis mode"), style = MaterialTheme.typography.titleMedium, color = p.pomegranate)
                Text(
                    if (o.spendable < 0) tr("رصيدك أقل من التزامات هذا الشهر بـ${fmt(-o.spendable)}.", "Your balance is ${fmt(-o.spendable)} short of this month's commitments.")
                    else tr("بمعدلك الحالي (${fmt(o.pace)} في اليوم) ستقصّر ${fmt(short)} قبل الراتب بـ${daysText(o.daysLeft)}.",
                        "At your pace (${fmt(o.pace)} a day) you'll be ${fmt(short)} short, ${daysText(o.daysLeft)} before payday."),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(if (open) "▴" else "▾", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!open) return@Column
        val cap = max(0.0, o.spendable) / max(1, o.daysLeft)
        Text(tr("حتى تصل إلى الراتب: لا تتجاوز ${fmt(cap)} في اليوم.", "To reach payday: no more than ${fmt(cap)} a day."),
            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))

        if (putOff.isNotEmpty()) {
            Text(tr("أجّل هذا الشهر", "Put off this month"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            putOff.forEach { (c, v) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("${c.icon} ${c.name}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        // Freed from this month's reserve: back into what can be spent.
                        db.setPaid(c.id, cycle.from, true); AllowanceWidget.refreshAll(ctx); local++; refresh()
                    }) { Text(tr("أجّل ويرجع لك ${fmt(v)}", "Put off: +${fmt(v)}")) }
                }
            }
        }
        if (cuts.isNotEmpty()) {
            Text(tr("خفّف", "Cut back"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            cuts.forEach { (c, perDay) ->
                KV("${c.icon} ${c.name}  " + tr("(${fmt(perDay)} يومياً)", "(${fmt(perDay)} a day)"),
                    tr("نصّه يوفّر ${fmt(perDay / 2 * o.daysLeft)}", "half saves ${fmt(perDay / 2 * o.daysLeft)}"))
            }
        }
        if (subs.isNotEmpty()) {
            Text(tr("اشتراكات يمكن إيقافها مؤقتاً", "Subscriptions that could pause"), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp))
            subs.take(4).forEach { s -> KV(s.name, fmt(s.monthly) + tr(" / شهر", " / month")) }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = p.line)
        val freed = putOff.sumOf { it.second } + cuts.sumOf { it.second / 2 * o.daysLeft }
        Text(
            if (freed >= short) tr("التأجيل وتخفيف هذه البنود للنصف يغطّي العجز.", "Putting these off and halving these lines covers the gap.")
            else tr("حتى مع التأجيل والتخفيف يبقى عجز ${fmt(short - freed)}: فكّر بتأجيل دفعة ثابتة أو باستلاف مؤقت.",
                "Even then ${fmt(short - freed)} is missing: think about delaying a fixed payment or a short loan."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
