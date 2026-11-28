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
    private const val SINCE = "weekly_since"

    fun cap(db: Db): Double = db.setting(CAP, "0").toDoubleOrNull() ?: 0.0
    fun on(db: Db): Boolean = cap(db) > 0
    fun setCap(db: Db, v: Double) {
        db.setSetting(CAP, if (v > 0) v.toString() else "0")
        // Overspends are carried only from weeks after the habit began.
        if (v > 0 && db.setting(SINCE, "").isBlank()) db.setSetting(SINCE, range(db).from.toString())
    }

    /** Start of the first week the weekly money was on; older weeks never take anything off. */
    fun since(db: Db): Long = db.setting(SINCE, "").toLongOrNull() ?: run {
        val s = range(db).from
        if (on(db)) db.setSetting(SINCE, s.toString())
        s
    }

    /**
     * Lines that count: every spending line with no budget. Giving a line a budget takes it out.
     * Savings and yearly-expense lines (kept as reserves) never count: they are put-aside money, not spending.
     */
    fun lines(db: Db): Set<Long> = db.categories()
        .filter { it.budget <= 0 && it.type != Db.RESERVE && !Annual.isAnnual(db, it.id) }.map { it.id }.toSet()

    private const val AVG_DAYS = 30.44

    /**
     * The real money for the weekly spending until payday: what's free in the bank after the bills and savings
     * still to come ([spendable]), less what the budgeted day-to-day lines (fuel, smoking…) still need this month.
     */
    data class Pool(val spendable: Double, val dailyNeed: Double, val daysLeft: Int) {
        val free: Double get() = spendable - dailyNeed
        /** What a week can really have from now to payday. */
        val perWeek: Double get() = max(0.0, free) * 7 / kotlin.math.max(1, daysLeft)
    }

    fun pool(db: Db, now: Long = System.currentTimeMillis()): Pool? {
        val al = Budget.allowance(db, now)
        val spendable = al.spendable ?: return null
        val r = al.cycle.range
        val spent = db.spentByCategory(r.from, minOf(r.to, now + Period.DAY))
        val counted = lines(db)
        val need = db.categories().filter { it.type == Db.DAILY && it.budget > 0 && it.id !in counted }
            .sumOf { max(0.0, it.budget - (spent[it.id] ?: 0.0)) }
        return Pool(spendable, need, al.daysLeft)
    }

    /**
     * Does the weekly amount fit the month? The expected salary, less every line's budget and what the
     * yearly expenses put aside each month, is what's free; the weekly amount takes [weeklyMonthly] of it.
     */
    data class Fit(val salary: Double, val budgets: Double, val yearly: Double, val weeklyMonthly: Double,
                   val amount: Double = 0.0, val real: Pool? = null) {
        val free: Double get() = salary - budgets - yearly
        val spare: Double get() = free - weeklyMonthly
        val ok: Boolean get() = spare >= -0.005
        /** The most a week can have and still fit. */
        val maxWeekly: Double get() = max(0.0, free) * 7 / AVG_DAYS
    }

    fun fit(db: Db, amount: Double = cap(db)): Fit? {
        val salary = db.salary()
        if (salary <= 0 || amount <= 0) return null
        val budgets = db.categories().filter { it.budget > 0 }.sumOf { it.budget }
        val yearly = try { Annual.items(db).sumOf { it.monthly } } catch (e: Exception) { 0.0 }
        val real = try { pool(db) } catch (e: Exception) { null }
        return Fit(salary, budgets, yearly, amount * AVG_DAYS / 7, amount, real)
    }

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

    /**
     * What a week really gets: its amount, less whatever the week before went over by (that week's own
     * real amount counted the same way). A leftover is not carried. Never below zero.
     */
    fun effectiveCap(db: Db, r: Range, start: Long = since(db), depth: Int = 0): Double {
        val base = capFor(db, r)
        if (depth >= 8) return base
        val prev = rangeAt(db, r.from - 1)
        if (prev.from < start) return base
        val over = spentIn(db, prev) - effectiveCap(db, prev, start, depth + 1)
        return max(0.0, base - max(0.0, over))
    }

    fun spentIn(db: Db, r: Range): Double {
        val ids = lines(db)
        return db.spentByCategory(r.from, r.to).filter { (k, _) -> k == null || k in ids }.values.sum()
    }

    /**
     * [bank] is what is really free until payday (the balance less the bills still to pay), when known.
     * The week never offers more than that: [left] is the smaller of the two.
     */
    data class Week(val range: Range, val cap: Double, val spent: Double, val daysLeft: Int, val bank: Double? = null,
                    /** Taken off this week for last week's overspend. */ val deducted: Double = 0.0) {
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
        val cap = effectiveCap(db, r)
        val deducted = max(0.0, capFor(db, r) - cap)
        val todayStart = Period.day(0).from
        val daysLeft = if (offset == 0) ((r.to - todayStart + Period.HOUR) / Period.DAY).toInt().coerceIn(1, 7) else 0
        // The real money: free in the bank, less what fuel, smoking and the like still need until payday.
        val bank = if (offset == 0) pool(db, now)?.free else null
        return Week(r, max(0.0, cap), spentIn(db, r), daysLeft, bank, deducted)
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
        if (w.deducted > 0.005) Text(
            tr("انخصم ${n2(w.deducted)} زيادة الأسبوع الماضي.", "${n2(w.deducted)} came off for last week's overspend."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (w.limitedByBank) Text(
            tr("الأسبوع بيسمح ${n2(w.byPlan)}، بس اللي معك فعلاً ${n2(w.left)} لحد الراتب، بعد الفواتير والباقي لبنود زي البنزين.",
                "The week allows ${n2(w.byPlan)}, but only ${n2(w.left)} is really free until payday, after the bills and what lines like fuel still need."),
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
        val fit = remember(tick, v, text) { Weekly.fit(db, text.toDoubleOrNull() ?: 0.0) }
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
        if (fit != null) WeeklyFitLine(fit, Modifier.padding(top = 8.dp))
        if (cap > 0) {
            Text(tr("الأسبوع بيبلّش بنفس يوم نزول الراتب. والأيام القليلة قبل الراتب الجاي بتكون أسبوع قصير، ومبلغه على قدّ أيامه.",
                "The week starts on the weekday your salary came; the few days before the next salary make a short week with its share of the amount."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Text(tr("إذا صرفتوا أكثر من المبلغ، الزيادة بتنخصم من الأسبوع اللي بعده. واللي بيضل ما بيتراكم.",
                "Going over comes off the next week; what's left is not carried."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
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

/** One line: does the weekly amount fit what's left of the salary after every budget? */
@Composable
fun WeeklyFitLine(f: Weekly.Fit, modifier: Modifier = Modifier) {
    val p = Ui.p
    val tone = if (f.ok) p.mint else p.pomegranate
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.pastel(tone)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(if (f.ok) AppIcons.CheckCircle else AppIcons.Warning, null, tint = p.deep(tone), modifier = Modifier.size(20.dp))
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                if (f.ok) tr("مصروف الأسبوع بيكفي، وبيضل ${n2(f.spare)} احتياط بالشهر.",
                    "The weekly money fits, with ${n2(f.spare)} a month to spare.")
                else tr("مصروف الأسبوع ما بيكفي: ناقص ${n2(-f.spare)} بالشهر.", "The weekly money doesn't fit: ${n2(-f.spare)} a month short."),
                color = p.deep(tone), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            )
            Text(
                tr("الراتب ${n2(f.salary)} − البنود ${n2(f.budgets)}", "Salary ${n2(f.salary)} − lines ${n2(f.budgets)}") +
                    (if (f.yearly > 0.005) tr(" − السنوية ${n2(f.yearly)}", " − yearly ${n2(f.yearly)}") else "") +
                    tr(" = ${n2(f.free)} حر؛ الأسبوع بياخد ${n2(f.weeklyMonthly)} بالشهر.", " = ${n2(f.free)} free; the week takes ${n2(f.weeklyMonthly)} a month."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            f.real?.let { r ->
                val enough = r.perWeek >= f.amount - 0.005
                Text(
                    if (enough) tr("وحسب رصيدك هلأ كمان بيكفي: بتقدروا لحد ${n2(r.perWeek)} بالأسبوع لحد الراتب.",
                        "And by your balance now it fits too: up to ${n2(r.perWeek)} a week until payday.")
                    else tr("بس حسب رصيدك هلأ: بتقدروا حوالي ${n2(r.perWeek)} بالأسبوع لحد الراتب، مش ${n2(f.amount)}.",
                        "But by your balance now: about ${n2(r.perWeek)} a week until payday, not ${n2(f.amount)}."),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp),
                    color = if (enough) p.deep(p.mint) else p.deep(p.pomegranate),
                )
                Text(
                    tr("رصيدك الحر ${n2(r.spendable)} − الباقي للبنود اليومية ${n2(r.dailyNeed)}، لـ${daysText(r.daysLeft)}.",
                        "Free balance ${n2(r.spendable)} − what day-to-day lines still need ${n2(r.dailyNeed)}, for ${daysText(r.daysLeft)}."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!f.ok) Text(
                if (f.maxWeekly > 0.5) tr("أقصى مبلغ أسبوعي بيكفي: ${n2(f.maxWeekly)}. أو خفّف ميزانية بند.",
                    "The most a week can have: ${n2(f.maxWeekly)}. Or lower a line's budget.")
                else tr("البنود لحالها أكثر من الراتب. خفّف ميزانية بند أول.", "The lines alone are more than the salary: lower a budget first."),
                style = MaterialTheme.typography.bodySmall, color = p.deep(tone), modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Where the month left the plan: lines that took more than their budget, and the weekly-money lines
 * (no budget, or no line at all) set against what the weeks gone so far allowed.
 */
@Composable
fun OverPlanCard(db: Db, tick: Int) {
    val p = Ui.p
    data class Row3(val name: String, val icon: String, val color: Long, val budget: Double, val spent: Double)
    val data = remember(tick) {
        val r = Budget.cycle(db).range
        val spent = db.spentByCategory(r.from, r.to)
        val over = db.categories().filter { it.budget > 0 && (spent[it.id] ?: 0.0) > it.budget + 0.5 }
            .map { Row3(it.name, it.icon, it.color, it.budget, spent[it.id] ?: 0.0) }
            .sortedByDescending { it.spent - it.budget }
        val counted = Weekly.lines(db)
        val free = spent.filter { (k, _) -> k == null || k in counted }.values.sum()
        val daysGone = ((minOf(System.currentTimeMillis(), r.to) - r.from) / Period.DAY).toInt() + 1
        val allowed = if (Weekly.on(db)) Weekly.cap(db) * daysGone / 7.0 else 0.0
        Triple(over, free, allowed)
    }
    val (over, free, allowed) = data
    val freeOver = Weekly.on(db) && free > allowed + 0.5
    if (over.isEmpty() && !freeOver) return
    val total = over.sumOf { it.spent - it.budget } + if (freeOver) free - allowed else 0.0
    Section(tr("اللي طلع عن الخطة هالشهر", "Where this month left the plan")) {
        Text(tr("مجموعه ${n2(total)}. هاد اللي بيخلي رصيدك أقل من الخطة.", "${n2(total)} in all: why the balance is below the plan."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        over.forEach { o ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                LineIcon(o.icon, Color(o.color), 32.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(o.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tr("الميزانية ${n2(o.budget)} · انصرف ${n2(o.spent)}", "Budget ${n2(o.budget)} · spent ${n2(o.spent)}"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Tag(tr("+${n2(o.spent - o.budget)}", "+${n2(o.spent - o.budget)}"), p.pomegranate)
            }
        }
        if (freeOver) Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            VecTile(AppIcons.Payments, p.pomegranate, 32.dp)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(tr("مصروف الأسبوع والصرف بلا بند", "Weekly money and unsorted spending"),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(tr("المسموح لهلأ ${n2(allowed)} · انصرف ${n2(free)}", "Allowed so far ${n2(allowed)} · spent ${n2(free)}"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Tag("+${n2(free - allowed)}", p.pomegranate)
        }
        Text(tr("الشهر الجاي: ارفع ميزانية البند للرقم الحقيقي، أو خفّف عليه.", "Next month: raise the line's budget to the real figure, or spend less on it."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
    }
}
