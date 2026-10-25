package com.mizania.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/*
 * Today shows one card at a time — the most important thing right now — and the Month page holds the
 * whole picture: salary, balance and what is set aside, the payday plan, every budget line, and how the
 * numbers add up to the balance.
 */

/** The one card for today, by priority: crisis, payday plan, month-end surplus, a big spend, then sorting. */
@Composable
fun TodayFocus(db: Db, tick: Int, al: Allowance, pending: List<Txn>, cats: List<Category>, onTriage: () -> Unit, refresh: () -> Unit) {
    val o = remember(tick) { MonthEnd.outlook(db) }
    val cycleFrom = al.cycle.range.from
    val planOn = remember(tick) {
        al.cycle.received && al.cycle.bySalary && !db.noSalary() && !SalaryPlan.hidden(db) && SalaryPlan.steps(db).any { !it.done }
    }
    val big = remember(tick) { Budget.bigOneOff(db, al) }
    val streak = remember(tick) { Streak.current(db) }
    when {
        o != null && MonthEnd.inCrisis(o) -> CrisisCard(db, tick, refresh)
        planOn -> SalaryPlanCard(db, tick, refresh)
        o != null && MonthEnd.suggestion(o) != null && !MonthEnd.skipped(db, cycleFrom) && !MonthEnd.saved(db, cycleFrom) ->
            MonthEndCard(db, tick, refresh)
        big != null -> BigSpendCard(db, big, al, refresh)
        pending.isNotEmpty() -> TriageCard(pending, cats, onTriage)
        streak >= 2 -> Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(18.dp))
                .background(Ui.p.dinar.copy(alpha = 0.14f)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔥", fontSize = 22.sp)
            Text(tr("${daysText(streak)} متتالية ضمن حدك اليومي", "${daysText(streak)} in a row within your daily limit"),
                Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** "A big spend today: spread it over the month?" */
@Composable
fun BigSpendCard(db: Db, big: Txn, al: Allowance, refresh: () -> Unit) {
    val ctx = LocalContext.current
    val p = Ui.p
    val days = al.daysLeft
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(20.dp))
            .background(p.dinar.copy(alpha = 0.14f)).border(1.dp, p.dinar.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(tr("صرفة كبيرة اليوم: ${big.title} بـ${fmt(big.amount)}", "A big spend today: ${big.title}, ${fmt(big.amount)}"),
            style = MaterialTheme.typography.titleSmall)
        Text(tr("هل هي لمرة واحدة (تعبئة بنزين، صيدلية…)؟ إن وزّعتها على ${daysText(days)} لن تحسب كلها على اليوم، وتنقص من كل يوم ${fmt(big.amount / days)} فقط.",
            "Was it a one-off (a full tank, a pharmacy run…)? Spread over ${daysText(days)}, it won't all count against today: each day gets ${fmt(big.amount / days)} less."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { db.setSpread(big.id, true); AllowanceWidget.refreshAll(ctx); refresh() },
                shape = RoundedCornerShape(12.dp)) { Text(tr("وزّعها على الشهر", "Spread it")) }
            TextButton(onClick = { db.setSpread(big.id, false); refresh() }) { Text(tr("لا، هي صرف اليوم", "No, it's today's")) }
        }
    }
}

/** Three things done every day, one tap each. */
@Composable
fun QuickActions(pendingCount: Int, onTriage: () -> Unit, onAddCash: () -> Unit, onWhatIf: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickButton("🗂️", if (pendingCount > 0) tr("صنّف ($pendingCount)", "Sort ($pendingCount)") else tr("صنّف", "Sort"),
            Modifier.weight(1f), onTriage)
        QuickButton("💵", tr("مصروف كاش", "Cash spend"), Modifier.weight(1f), onAddCash)
        QuickButton("🧮", tr("شو لو؟", "What if?"), Modifier.weight(1f), onWhatIf)
    }
}

@Composable
private fun QuickButton(icon: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Column(
        modifier.pressScale(src).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, Ui.p.line, RoundedCornerShape(18.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 22.sp)
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
    }
}

/** The three kinds of budget lines, as the app groups them everywhere. */
data class LineGroup(val type: Int, val icon: String, val title: String, val hint: String)

val LineGroups: List<LineGroup> get() = listOf(
    LineGroup(Db.FIXED, "📌", tr("المصاريف الشهرية", "Monthly expenses"), tr("تُدفع مرة في الشهر وتُحجز من الراتب", "Paid once a month, set aside from the salary")),
    LineGroup(Db.DAILY, "🛒", tr("المصاريف اليومية", "Daily expenses"), tr("تُصرف على أيام الشهر وتُحسب من حدّك اليومي", "Spent across the month, counted against your daily limit")),
    LineGroup(Db.RESERVE, "🏦", tr("الادخار والاحتياط", "Savings and reserves"), tr("مبالغ توضع جانباً", "Money put aside")),
)

/**
 * One group of lines: how much went on the whole group out of its budget, then every line in it
 * with its own spent / budget. [budgetOf] gives a line's budget for the period shown.
 */
@Composable
fun GroupBlock(g: LineGroup, lines: List<Category>, spent: Map<Long?, Double>, budgetOf: (Category) -> Double, onLine: (Long) -> Unit) {
    val p = Ui.p
    val total = lines.sumOf { spent[it.id] ?: 0.0 }
    val budget = lines.sumOf(budgetOf)
    Section("${g.icon}  ${g.title}") {
        Row(verticalAlignment = Alignment.Bottom) {
            Amount(total, 24.sp, weight = FontWeight.Bold)
            if (budget > 0) Text(tr("  من ${fmt(budget)}", "  of ${fmt(budget)}"), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
        }
        if (budget > 0) {
            val left = budget - total
            androidx.compose.material3.LinearProgressIndicator(
                progress = { (total / budget).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = if (left < 0) p.pomegranate else p.pine, trackColor = p.line, drawStopIndicator = {},
            )
            Text(if (left >= 0) tr("باقي ${fmt(left)}", "${fmt(left)} left") else tr("زيادة ${fmt(-left)}", "${fmt(-left)} over"),
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                color = if (left < 0) p.pomegranate else p.mint)
        }
        Text(g.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = p.line)
        lines.sortedByDescending { spent[it.id] ?: 0.0 }.forEach { c -> CatBar(c, c.name, spent[c.id] ?: 0.0, budgetOf(c)) { onLine(c.id) } }
    }
}

/** The Month page: the whole picture of the budget month. */
@Composable
fun MonthScreen(db: Db, tick: Int, onOpen: (Long) -> Unit, refresh: () -> Unit) {
    val ctx = LocalContext.current
    val al = remember(tick) { Budget.allowance(db) }
    val ci = al.cycle
    val cycle = ci.range
    val cats = remember(tick) { db.categories() }
    val spent = remember(tick) { db.spentByCategory(cycle.from, cycle.to) }
    val noSalary = remember(tick) { db.noSalary() }
    val challenge = remember(tick) { Plan.challenge(db)?.takeIf { it.from == cycle.from } }
    val p = Ui.p
    var showBalance by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var catSheet by remember { mutableStateOf<Long?>(null) }
    var showMath by remember { mutableStateOf(false) }
    val totalOut = spent.values.sum()

    LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)) {
        // Salary, balance, what is set aside and what is left to spend.
        item {
            Section(Period.label(Period.MODE_MONTH, cycle), action = {
                TextButton(onClick = { showBalance = true }) { Text(tr("التفاصيل", "Details")) }
            }) {
                if (!noSalary) {
                    val days = max(0, ((ci.nextSalary - Period.day().from) / Period.DAY).toInt())
                    KV(tr("الراتب", "Salary"), if (ci.received) fmt(ci.salary) else tr("لم يصل بعد", "Not yet"))
                    KV(tr("الراتب القادم", "Next salary"), when {
                        ci.late -> tr("متأخر", "Late")
                        days == 0 -> tr("اليوم", "Today")
                        else -> tr("بعد ${daysText(days)}", "In ${daysText(days)}")
                    })
                }
                al.balance?.let { KV(tr("رصيدك", "Balance"), fmt(it), color = if (it < 0) p.pomegranate else Color.Unspecified) }
                KV(tr("محجوز للفواتير والادخار", "Set aside for bills and savings"), fmt(al.committed))
                al.spendable?.let {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = p.line)
                    KV(tr("يمكنك صرفه حتى الراتب", "Free to spend until payday"), fmt(it), bold = true,
                        color = if (it < 0) p.pomegranate else p.mint)
                    KV(tr("يعني في اليوم", "That's a day"), tr("${fmt(al.allowance)} لـ${daysText(al.daysLeft)}", "${fmt(al.allowance)} for ${daysText(al.daysLeft)}"))
                }
            }
        }
        item { CrisisCard(db, tick, refresh) }
        item { SalaryPlanCard(db, tick, refresh) }
        item { MonthEndCard(db, tick, refresh) }
        if (challenge != null) item { ToolsCard(db, tick) { showTools = true } }

        // Monthly expenses, daily expenses and savings, each with its own total and its lines under it.
        LineGroups.forEach { g ->
            val lines = cats.filter { it.type == g.type && (it.budget > 0 || (spent[it.id] ?: 0.0) > 0) }
            if (lines.isNotEmpty()) item {
                GroupBlock(g, lines, spent, { it.budget }) { catSheet = it }
            }
        }
        val noLine = spent[null] ?: 0.0
        if (noLine > 0) item {
            Section(tr("بدون بند", "No line")) {
                CatBar(null, tr("حركات لم تُصنَّف", "Not sorted yet"), noLine, 0.0) { catSheet = -1L }
            }
        }

        // In and out this month, and how it all adds up to the real balance.
        item {
            Section(tr("الدخل والصرف", "In and out"), action = {
                TextButton(onClick = { MonthCard.share(ctx, db) }) { Text(tr("مشاركة 📤", "Share 📤")) }
            }) {
                val otherIn = remember(tick) { db.otherIncome(cycle.from, cycle.to) }
                val startBal = remember(tick) { db.totalBalanceAt(cycle.from) }
                val income = ci.salary + otherIn
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val slices = cats.map { c -> Slice(c.name, spent[c.id] ?: 0.0, Color(c.color)) } +
                        listOf(Slice(tr("بدون بند", "No line"), spent[null] ?: 0.0, Color(0xFF9E9E9E)))
                    Box(Modifier.width(120.dp)) {
                        if (income > 0) DonutChart(slices, "${((totalOut / income) * 100).toInt()}%", tr("من الدخل", "of income"), size = 116.dp, showLegend = false)
                        else DonutChart(slices, "", "", size = 116.dp, showLegend = false)
                    }
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(tr("دخل", "In"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Amount(income, 22.sp, p.mint)
                        Spacer(Modifier.height(8.dp))
                        Text(tr("صرف", "Out"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Amount(totalOut, 22.sp)
                    }
                }
                if (al.balance != null) {
                    TextButton(onClick = { showMath = !showMath }, modifier = Modifier.padding(top = 4.dp)) {
                        Text(if (showMath) tr("إخفاء التفاصيل", "Hide details") else tr("كيف وصلنا إلى رصيدك؟", "How did we get your balance?"))
                    }
                }
                AnimatedVisibility(showMath && al.balance != null) {
                    Column {
                        val bal = al.balance ?: 0.0
                        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = p.line)
                        if (startBal != null) KV(if (ci.bySalary) tr("رصيدك قبل الراتب", "Balance before salary") else tr("رصيدك في بداية الشهر", "Balance at month start"), fmt(startBal))
                        if (noSalary) KV(tr("+ الدخل", "+ Income"), fmt(income))
                        else KV(if (ci.received) tr("+ الراتب", "+ Salary") else tr("+ الراتب (لم يصل بعد)", "+ Salary (not arrived yet)"), fmt(ci.salary))
                        if (otherIn > 0 && !noSalary) KV(tr("+ دخل آخر (كليك وتحويلات واردة)", "+ Other income (CliQ and incoming transfers)"), fmt(otherIn))
                        KV(tr("− صرفت", "− Spent"), fmt(totalOut))
                        val gap = bal - ((startBal ?: 0.0) + income - totalOut)
                        if (startBal != null && kotlin.math.abs(gap) >= 0.5) {
                            KV(if (gap < 0) tr("− حركات لم تصل عنها رسالة", "− Movements with no SMS") else tr("+ حركات لم تصل عنها رسالة", "+ Movements with no SMS"),
                                fmt(kotlin.math.abs(gap)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = p.line)
                        KV(tr("= رصيدك الآن", "= Balance now"), fmt(bal), bold = true)
                    }
                }
            }
        }
    }

    if (showBalance) BalanceSheet(db) { showBalance = false; refresh() }
    if (showTools) ToolsSheet(db, tick) { showTools = false; refresh() }
    catSheet?.let { id ->
        CategorySheet(db, cats.firstOrNull { it.id == id }, cycle, tr("هذا الشهر", "This month"),
            onOpen = { tid -> catSheet = null; onOpen(tid) }, onDone = { catSheet = null }, onChanged = refresh)
    }
}
