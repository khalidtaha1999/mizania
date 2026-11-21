package com.mizania.app

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

/**
 * The week's free money: a fixed amount a week (50, say) for everything that has no budget of its own,
 * once the commitments and the monthly shopping are paid. The rule is simple: a line whose budget is
 * zero counts, of any kind; a line with a budget (rent, fuel, smoking…) never does. Unsorted spending counts.
 */
object Weekly {
    private const val CAP = "weekly_cap"
    private const val CARRY = "weekly_carry"

    fun cap(db: Db): Double = db.setting(CAP, "0").toDoubleOrNull() ?: 0.0
    fun on(db: Db): Boolean = cap(db) > 0
    fun setCap(db: Db, v: Double) = db.setSetting(CAP, if (v > 0) v.toString() else "0")

    /** Leftover carried into the next week (and an overspend taken off it). */
    fun carries(db: Db): Boolean = db.setting(CARRY, "0") == "1"
    fun setCarries(db: Db, on: Boolean) = db.setSetting(CARRY, if (on) "1" else "0")

    /** Lines that count: every line with no budget. Giving a line a budget takes it out. */
    fun lines(db: Db): Set<Long> = db.categories().filter { it.budget <= 0 }.map { it.id }.toSet()

    private fun dayStart(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * The week holding [at]. Weeks start on payday's weekday: counted in sevens from the day the salary came,
     * and the last few days before the next salary make a short week of their own.
     */
    fun rangeAt(db: Db, at: Long): Range {
        val cycle = Budget.cycle(db, now = at).range
        val anchor = dayStart(cycle.from)
        val k = ((dayStart(at) - anchor) / Period.DAY / 7).toInt().coerceAtLeast(0)
        val c = Calendar.getInstance().apply { timeInMillis = anchor; add(Calendar.DAY_OF_YEAR, 7 * k) }
        val from = c.timeInMillis
        c.add(Calendar.DAY_OF_YEAR, 7)
        // A late salary leaves the expected payday behind: then the week simply runs its seven days.
        val end = if (cycle.to > dayStart(at)) minOf(c.timeInMillis, cycle.to) else c.timeInMillis
        return Range(from, end)
    }

    /** The current week, or [offset] weeks back (each step is the week just before the last one's start). */
    fun range(db: Db, now: Long = System.currentTimeMillis(), offset: Int = 0): Range {
        var r = rangeAt(db, now)
        repeat(-offset) { r = rangeAt(db, r.from - 1) }
        return r
    }

    /** Days in a week: 7, or fewer for the short week before payday. */
    fun days(r: Range): Int = ((r.to - r.from + Period.HOUR) / Period.DAY).toInt().coerceIn(1, 7)

    /** The week's amount, cut down in proportion for a short week. */
    fun capFor(db: Db, r: Range): Double = cap(db) * days(r) / 7.0

    fun spentIn(db: Db, r: Range): Double {
        val ids = lines(db)
        return db.spentByCategory(r.from, r.to).filter { (k, _) -> k == null || k in ids }.values.sum()
    }

    /**
     * [bank] is what is really free until payday (the balance less the bills still to pay), when known.
     * The week never offers more than that: [left] is the smaller of the two.
     */
    data class Week(val range: Range, val cap: Double, val spent: Double, val daysLeft: Int, val bank: Double? = null) {
        val byPlan: Double get() = cap - spent
        val limitedByBank: Boolean get() = bank != null && !over && bank < byPlan - 0.005
        val left: Double get() = if (limitedByBank) max(0.0, bank!!) else byPlan
        val perDay: Double get() = if (daysLeft > 0) max(0.0, left) / daysLeft else 0.0
        val share: Float get() = if (cap > 0) (spent / cap).toFloat() else 0f
        val over: Boolean get() = spent > cap + 0.005
        val near: Boolean get() = !over && (share >= 0.8f || (limitedByBank && left < cap * 0.2))
    }

    fun week(db: Db, now: Long = System.currentTimeMillis(), offset: Int = 0): Week {
        val r = range(db, now, offset)
        var cap = capFor(db, r)
        if (carries(db) && offset == 0) {
            val prev = range(db, now, -1)
            // Only once the habit has a week behind it: no carry from before it was switched on.
            if (db.txns(prev.from, prev.to).isNotEmpty()) cap += capFor(db, prev) - spentIn(db, prev)
        }
        val todayStart = Period.day(0).from
        val daysLeft = if (offset == 0) ((r.to - todayStart + Period.HOUR) / Period.DAY).toInt().coerceIn(1, 7) else 0
        val bank = if (offset == 0) Budget.allowance(db, now).spendable else null
        return Week(r, max(0.0, cap), spentIn(db, r), daysLeft, bank)
    }

    /** This week's transactions that count, newest first. */
    fun txns(db: Db, r: Range): List<Txn> {
        val ids = lines(db)
        return db.txns(r.from, r.to).filter { !it.income && (it.categoryId == null || it.categoryId in ids) }
            .sortedByDescending { it.time }
    }

    /** Nightly: near the limit, over it, and the week's result on its last night. Each once a week. */
    fun notify(ctx: Context, db: Db, now: Long = System.currentTimeMillis()) {
        if (!on(db)) return
        val w = week(db, now)
        val key = w.range.from
        if (w.over && db.firstTime("weekly-over:$key")) Notifier.alert(ctx, 900_401,
            tr("تجاوزتوا مصروف الأسبوع", "Over this week's money"),
            tr("صرفتوا ${fmt(w.spent)} من ${fmt(w.cap)} هالأسبوع، وضايل ${daysText(w.daysLeft)}.",
                "${fmt(w.spent)} of ${fmt(w.cap)} this week, with ${daysText(w.daysLeft)} to go."))
        else if (w.near && db.firstTime("weekly-near:$key")) Notifier.alert(ctx, 900_401,
            tr("قربتوا تخلصوا مصروف الأسبوع", "This week's money is nearly gone"),
            tr("ضايلكم ${fmt(w.left)} من ${fmt(w.cap)} لآخر الأسبوع.", "${fmt(w.left)} of ${fmt(w.cap)} left for the rest of the week."))
        // The last night of the week: how it went.
        if (w.range.to - Period.day(0).to < Period.HOUR && db.firstTime("weekly-end:$key")) Notifier.alert(ctx, 900_402,
            if (w.over) tr("الأسبوع خلص فوق الحد", "The week ended over") else tr("أسبوع ضمن الحد", "A week within the limit"),
            if (w.over) tr("صرفتوا ${fmt(w.spent)} من ${fmt(w.cap)}، يعني زيادة ${fmt(-w.left)}.",
                "${fmt(w.spent)} of ${fmt(w.cap)}: ${fmt(-w.left)} over.")
            else tr("صرفتوا ${fmt(w.spent)} من ${fmt(w.cap)}، ووفّرتوا ${fmt(w.left)}.",
                "${fmt(w.spent)} of ${fmt(w.cap)}, with ${fmt(w.left)} saved."))
    }
}

private fun n2(v: Double) = String.format(Locale.US, "%,.2f", v)

/** On Today: the week's free money, its days, and the last weeks at a glance; tap to see what counted. */
@Composable
fun WeeklyCard(db: Db, tick: Int, onOpen: (Long) -> Unit) {
    val p = Ui.p
    val w = remember(tick) { Weekly.week(db) }
    val past = remember(tick) { (8 downTo 1).map { Weekly.week(db, offset = -it) } }
    var open by remember { mutableStateOf(false) }
    val tone = when { w.over || w.limitedByBank -> p.pomegranate; w.near -> p.warning; else -> p.mint }
    StickerCard(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).animateContentSize(),
        onClick = { open = !open }, onClickLabel = tr("تفاصيل الأسبوع", "Week details"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VecTile(AppIcons.Payments, tone, 40.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(tr("مصروف الأسبوع", "This week's money"), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    Text(n2(w.spent), fontFamily = Ui.numbers, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp,
                        color = if (w.over) p.pomegranate else MaterialTheme.colorScheme.onSurface, modifier = Modifier.alignByBaseline())
                    Text(tr("  من ${n2(w.cap)}", "  of ${n2(w.cap)}"), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alignByBaseline())
                }
            }
            Tag(
                when {
                    w.over -> tr("زيادة ${n2(-w.left)}", "${n2(-w.left)} over")
                    else -> tr("باقي ${n2(w.left)}", "${n2(w.left)} left")
                }, tone,
            )
        }
        StickerBar(w.share, tone, p.pastel(tone), Modifier.padding(top = 12.dp), height = 10.dp)
        // The seven days, with today marked.
        val today = ((Period.day(0).from - w.range.from) / Period.DAY).toInt()
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 0 until Weekly.days(w.range)) {
                val c = Calendar.getInstance().apply { timeInMillis = w.range.from + i * Period.DAY + Period.HOUR }
                Text(weekDay(c.get(Calendar.DAY_OF_WEEK)), style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == today) FontWeight.ExtraBold else FontWeight.Normal,
                    color = if (i == today) MaterialTheme.colorScheme.onSurface
                    else if (i < today) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
            }
        }
        if (w.limitedByBank) Text(
            tr("الأسبوع بيسمح ${n2(w.byPlan)}، بس اللي معك فعلاً ${n2(w.left)} لحد الراتب بعد الفواتير.",
                "The week allows ${n2(w.byPlan)}, but only ${n2(w.left)} is really free until payday after the bills."),
            style = MaterialTheme.typography.bodySmall, color = p.pomegranate, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            when {
                w.over -> tr("تجاوزتوا الحد. حاولوا توقفوا الصرف الزيادة لآخر الأسبوع.", "Over the limit: try to hold off on extras until the week ends.")
                w.daysLeft == 1 -> tr("آخر يوم بالأسبوع، وضايلكم ${n2(w.left)}.", "Last day of the week, with ${n2(w.left)} left.")
                else -> tr("يعني حوالي ${n2(w.perDay)} باليوم لـ${daysText(w.daysLeft)}.", "About ${n2(w.perDay)} a day for ${daysText(w.daysLeft)}.")
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (open) {
            val list = remember(tick) { Weekly.txns(db, w.range) }
            val cats = remember(tick) { db.categories().associateBy { it.id } }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = p.line)
            Text(tr("آخر 8 أسابيع", "The last 8 weeks"), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                past.forEach { pw ->
                    val none = pw.spent <= 0.005
                    val c = when { none -> p.line; pw.over -> p.pomegranate; else -> p.mint }
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(if (none) Color.Transparent else p.pastel(c))
                            .border(2.dp, c, RoundedCornerShape(50))
                            .semantics {
                                contentDescription = Period.dayMonth(pw.range.from) + ": " + n2(pw.spent)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!none) Text(if (pw.over) "!" else "✓", color = c, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (list.isEmpty()) Text(tr("ما صرفتوا إشي من مصروف الأسبوع لسا.", "Nothing spent from this week's money yet."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(t.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val cat = t.categoryId?.let { cats[it] }
                    LineIcon(cat?.icon ?: "❔", cat?.let { Color(it.color) } ?: p.pomegranate, 32.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text(Period.dayMonth(t.time) + " · " + (cat?.name ?: tr("بدها بند", "Needs a line")),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(n2(t.amount), fontFamily = Ui.numbers, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun weekDay(d: Int): String = if (Lang.ar) when (d) {
    Calendar.SATURDAY -> "سبت"; Calendar.SUNDAY -> "أحد"; Calendar.MONDAY -> "إثنين"; Calendar.TUESDAY -> "ثلاثاء"
    Calendar.WEDNESDAY -> "أربعاء"; Calendar.THURSDAY -> "خميس"; else -> "جمعة"
} else when (d) {
    Calendar.SATURDAY -> "Sat"; Calendar.SUNDAY -> "Sun"; Calendar.MONDAY -> "Mon"; Calendar.TUESDAY -> "Tue"
    Calendar.WEDNESDAY -> "Wed"; Calendar.THURSDAY -> "Thu"; else -> "Fri"
}

/** Settings: the amount, the first day, carrying over, and which day-to-day lines count. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklySettings(db: Db, tick: Int, onChanged: () -> Unit) {
    var v by remember { mutableIntStateOf(0) }
    val cap = remember(tick, v) { Weekly.cap(db) }
    var text by remember(tick) { mutableStateOf(if (cap > 0) String.format(Locale.US, "%.0f", cap) else "") }
    val counted = remember(tick, v) { db.categories().filter { it.budget <= 0 } }
    Section(tr("مصروف الأسبوع", "Weekly money")) {
        Text(tr("مبلغ ثابت بالأسبوع لكل بند ميزانيته صفر، وللحركات اللي ما إلها بند. أي بند إله ميزانية ما بينحسب.",
            "A set amount a week for every line whose budget is zero, and for spending without a line. A line with a budget never counts."),
            style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = text,
            onValueChange = { s ->
                text = s.filter { it.isDigit() || it == '.' }
                Weekly.setCap(db, text.toDoubleOrNull() ?: 0.0); v++; onChanged()
            },
            label = { Text(tr("المبلغ بالأسبوع (فاضي = موقّف)", "Amount a week (empty = off)")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (cap > 0) {
            Text(tr("الأسبوع بيبلّش بنفس يوم نزول الراتب. والأيام القليلة قبل الراتب الجاي بتكون أسبوع قصير، ومبلغه على قدّ أيامه.",
                "The week starts on the weekday your salary came; the few days before the next salary make a short week with its share of the amount."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("اللي بيضل بيتراكم للأسبوع الجاي", "Carry what's left into next week"))
                    Text(tr("والزيادة بتنخصم منه", "and take an overspend off it"), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = Weekly.carries(db), onCheckedChange = { Weekly.setCarries(db, it); v++; onChanged() })
            }
            Text(tr("البنود اللي بتنحسب منه هلأ", "Lines that count now"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
            Text(
                if (counted.isEmpty()) tr("ما في بند ميزانيته صفر. بتنحسب بس الحركات اللي ما إلها بند.", "No line has a zero budget; only spending without a line counts.")
                else counted.joinToString(tr("، ", ", ")) { it.name } + tr("، والحركات اللي ما إلها بند.", ", and spending without a line."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(tr("بدك بند يطلع منه؟ حطّله ميزانية. بدك يدخل؟ خلّي ميزانيته صفر.", "To take a line out, give it a budget; to put it in, set its budget to zero."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
