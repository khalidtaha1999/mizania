package com.mizania.app

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * Payday plan: once the salary lands, the month's steps in order — pay the fixed bills, put the
 * savings aside, pay the planned debt installments — then what is left for day-to-day.
 * Every step comes from the budget lines and debts; a step ticks itself once the money for it moved
 * (its line reached its budget, or the installment was paid), or by hand.
 */
object SalaryPlan {
    data class Step(val key: String, val icon: String, val title: String, val amount: Double, val done: Boolean, val auto: Boolean)

    fun steps(db: Db): List<Step> {
        val ci = Budget.cycle(db)
        val cycle = ci.range
        val cats = db.categories()
        val spent = db.spentByCategory(cycle.from, cycle.to)
        fun manual(key: String) = db.setting("plan:${cycle.from}:$key", "0") == "1"
        val out = ArrayList<Step>()
        // Fixed bills first, biggest first, then what goes aside.
        for (type in listOf(Db.FIXED, Db.RESERVE)) {
            cats.filter { it.type == type && it.budget > 0 }.sortedByDescending { it.budget }.forEach { c ->
                val s = spent[c.id] ?: 0.0
                val left = max(0.0, c.budget - s)
                val auto = left <= 0.0005
                val done = auto || db.isPaid(c.id, cycle.from)
                val goal = if (type == Db.RESERVE) db.goalsFor(c).firstOrNull() else null
                val title = when {
                    type == Db.FIXED -> tr("ادفع ${c.name}", "Pay ${c.name}")
                    goal != null -> tr("ضع في «${goal.name}» (${c.name})", "Put into “${goal.name}” (${c.name})")
                    else -> tr("ضع جانباً لـ${c.name}", "Set aside for ${c.name}")
                }
                out += Step("cat:${c.id}", c.icon, title, if (done) c.budget else left, done, auto)
            }
        }
        // Debt installments with a plan, unless a debt line already carries them.
        db.debts().filter { !it.owedToMe && !it.closed }.forEach { d ->
            val plan = db.debtPlan(d.id) ?: return@forEach
            val due = minOf(plan, d.remaining)
            val paid = db.debtPaidIn(d.id, cycle.from, cycle.to)
            val auto = paid + 0.0005 >= due
            out += Step("debt:${d.id}", "💳", tr("قسط ${d.person}", "Installment to ${d.person}"),
                max(0.0, due - paid).takeIf { !auto } ?: due, auto || manual("debt:${d.id}"), auto)
        }
        return out
    }

    /** Ticks or unticks a step by hand. Lines use the "paid" mark, so the allowance follows. */
    fun toggle(db: Db, step: Step) {
        val cycle = Budget.cycle(db).range
        when {
            step.key.startsWith("cat:") -> step.key.removePrefix("cat:").toLongOrNull()?.let { db.setPaid(it, cycle.from, !step.done) }
            else -> db.setSetting("plan:${cycle.from}:${step.key}", if (step.done) "0" else "1")
        }
    }

    fun hidden(db: Db): Boolean = db.setting("plan_hidden:${Budget.cycle(db).range.from}", "0") == "1"

    fun hide(db: Db, hide: Boolean) = db.setSetting("plan_hidden:${Budget.cycle(db).range.from}", if (hide) "1" else "0")
}

/** The payday plan on the home screen, shown once this month's salary has landed. */
@Composable
fun SalaryPlanCard(db: Db, tick: Int, refresh: () -> Unit) {
    var local by remember { mutableIntStateOf(0) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ci = remember(tick) { Budget.cycle(db) }
    if (!ci.received || !ci.bySalary || db.noSalary()) return
    val steps = remember(tick, local) { SalaryPlan.steps(db) }
    if (steps.isEmpty() || remember(tick, local) { SalaryPlan.hidden(db) }) return
    val done = steps.count { it.done }
    var open by remember { mutableStateOf(done < steps.size) }
    val al = remember(tick, local) { Budget.allowance(db) }
    val p = Ui.p
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).border(1.dp, p.line, RoundedCornerShape(22.dp))
            .animateContentSize().padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (done == steps.size) tr("✅ خطة الراتب اكتملت", "✅ Payday plan done") else tr("📋 خطة الراتب", "📋 Payday plan"),
                    style = MaterialTheme.typography.titleMedium)
                Text(tr("الراتب ${fmt(ci.salary)} · $done من ${steps.size} خطوات", "Salary ${fmt(ci.salary)} · $done of ${steps.size} steps"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Ui.numbers)
            }
            Text(if (open) "▴" else "▾", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { done.toFloat() / steps.size },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = p.mint, trackColor = p.line, drawStopIndicator = {},
        )
        if (!open) return@Column
        steps.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = p.line)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !s.auto) { SalaryPlan.toggle(db, s); local++; AllowanceWidget.refreshAll(ctx); refresh() }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape)
                        .background(if (s.done) p.mint else MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, if (s.done) p.mint else p.line, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (s.done) Text("✓", color = p.onPine, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Text("${s.icon}  ${s.title}", Modifier.weight(1f).padding(start = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (s.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (s.done) TextDecoration.LineThrough else null)
                Text(fmt(s.amount), fontFamily = Ui.numbers, fontSize = 14.sp,
                    color = if (s.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
        }
        HorizontalDivider(color = p.line)
        // What the plan leaves for every day.
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("🛒  يبقى للصرف اليومي", "🛒  Left for day-to-day"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold)
            Text(fmt(max(0.0, al.spendable ?: 0.0)), fontFamily = Ui.numbers, fontWeight = FontWeight.SemiBold, color = p.mint)
        }
        Text(tr("يعني ${fmt(al.allowance)} في اليوم لـ${daysText(al.daysLeft)}. تُعلَّم الخطوة وحدها حين تُصنَّف دفعتها على بندها، أو اضغط عليها.",
            "That's ${fmt(al.allowance)} a day for ${daysText(al.daysLeft)}. A step ticks itself once its payment is sorted into its line, or tap it."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        if (done == steps.size) TextButton(onClick = { SalaryPlan.hide(db, true); local++ }) { Text(tr("إخفاء حتى الراتب القادم", "Hide until next payday")) }
    }
}
