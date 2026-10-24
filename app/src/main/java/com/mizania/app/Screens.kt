@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.mizania.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val Amber = Color(0xFFE6A100)

// ---------------- shared pieces ----------------

/** Amount in the numbers face with a smaller currency after it. */
@Composable
fun Amount(
    v: Double, size: TextUnit, color: Color = Color.Unspecified,
    sign: String = "", weight: FontWeight = FontWeight.SemiBold,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(sign + String.format(Locale.US, "%,.2f", v), fontFamily = Ui.numbers, fontSize = size,
            fontWeight = weight, color = color, maxLines = 1)
        Text(" " + currency(), fontSize = size * 0.5f, color = color.orElse(MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.padding(bottom = (size.value * 0.12f).dp))
    }
}

private fun Color.orElse(other: Color) = if (this == Color.Unspecified) other else this

/** A titled group on the page; the rows sit on one raised sheet. */
@Composable
fun Section(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            action?.invoke()
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .background(Ui.p.glass)
                .border(1.dp, Ui.p.line, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            content = content,
        )
    }
}

@Composable
fun KV(k: String, v: String, color: Color = Color.Unspecified, bold: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(k, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
        Text(v, color = color, fontFamily = Ui.numbers,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, fontSize = if (bold) 16.sp else 15.sp)
    }
}

/** Emoji on a soft tile of the category's color. */
@Composable
fun IconBadge(icon: String, color: Color, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Text(icon, fontSize = (size.value * 0.46f).sp) }
}

@Composable
fun CatBar(cat: Category?, name: String, spent: Double, budget: Double, onClick: (() -> Unit)? = null) {
    val base = cat?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(cat?.icon ?: "❔", base, 36.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                if (budget > 0) {
                    val left = budget - spent
                    Text(
                        if (left >= 0) tr("المتبقي ${fmt(left)}", "${fmt(left)} left") else tr("بزيادة ${fmt(-left)}", "${fmt(-left)} over"),
                        fontFamily = Ui.numbers, fontSize = 13.sp,
                        color = if (left >= 0) MaterialTheme.colorScheme.onSurfaceVariant else Ui.p.pomegranate,
                    )
                } else {
                    Text(fmt(spent), fontFamily = Ui.numbers, fontSize = 13.sp)
                }
            }
            if (budget > 0) {
                val grow = appearProgress(150)
                val p = (spent / budget).toFloat() * grow
                val color = when {
                    p >= 1f -> Ui.p.pomegranate
                    p >= 0.8f -> Amber
                    else -> base
                }
                LinearProgressIndicator(
                    progress = { p.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = color,
                    trackColor = base.copy(alpha = 0.14f),
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
fun TxnRow(t: Txn, cat: Category?, accountName: String? = null, onClick: () -> Unit) {
    val sub = when {
        t.income -> t.kindText
        cat != null -> cat.name
        else -> tr("بحاجة إلى تصنيف", "Needs a budget line")
    } + (if (accountName != null) tr("، ", ", ") + accountName else "")
    val badgeColor = when {
        t.income -> Ui.p.mint
        cat != null -> Color(cat.color)
        else -> Ui.p.pomegranate
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(if (t.income) "💵" else cat?.icon ?: "❔", badgeColor)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                color = if (!t.income && cat == null) Ui.p.pomegranate else MaterialTheme.colorScheme.onSurfaceVariant)
            if (t.note.isNotBlank()) {
                Text(t.note, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Amount(t.amount, 16.sp, if (t.income) Ui.p.mint else MaterialTheme.colorScheme.onSurface,
                sign = if (t.income) "+" else "")
            Text(Period.clock(t.time), fontFamily = Ui.numbers, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A spend waiting for a budget line: one tap on a suggested line classifies it,
 * swiping toward the end ignores it, swiping toward the start opens the full sheet.
 */
@Composable
fun PendingRow(db: Db, t: Txn, quick: List<Category>, onOpen: (Long) -> Unit, refresh: () -> Unit) {
    val ctx = LocalContext.current
    var askIgnore by remember { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            // Ask first: the row springs back, and only "yes" takes it out.
            SwipeToDismissBoxValue.EndToStart -> { askIgnore = true; false }
            SwipeToDismissBoxValue.StartToEnd -> { onOpen(t.id); false }
            else -> false
        }
    })
    if (askIgnore) {
        AlertDialog(
            onDismissRequest = { askIgnore = false },
            title = { Text(tr("متأكد؟", "Are you sure?")) },
            text = {
                Text(tr("«${t.merchant.ifBlank { t.kind }}» بمبلغ ${fmt(t.amount)} لن تُحتسب مصروفاً ولن تدخل في أي مجموع. يمكنك إرجاعها من الإعدادات ← حركات مستبعدة.",
                    "\"${t.merchant.ifBlank { t.kind }}\" (${fmt(t.amount)}) won't count as spending or in any total. You can bring it back from Settings → Left-out transactions."))
            },
            confirmButton = {
                TextButton(onClick = {
                    askIgnore = false
                    db.ignoreTxn(t.id); Notifier.cancel(ctx, t.id); refresh()
                }) { Text(tr("نعم، ليست مصروفاً", "Yes, not spending"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { askIgnore = false }) { Text(tr("إلغاء", "Cancel")) } },
        )
    }
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val toIgnore = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))
                    .background(if (toIgnore) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (toIgnore) Alignment.CenterEnd else Alignment.CenterStart,
            ) { Text(if (toIgnore) tr("تجاهل", "Ignore") else tr("تفاصيل", "Details"), fontWeight = FontWeight.SemiBold) }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            TxnRow(t, null) { onOpen(t.id) }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quick.forEach { c ->
                    AssistChip(
                        onClick = {
                            if (db.needsLink(c, t.income)) onOpen(t.id) else {
                                db.updateTxn(t.id, c.id, t.note)
                                Notifier.cancel(ctx, t.id)
                                Processor.afterCategorized(ctx, t.id, c.id, notifyRecorded = false)
                                refresh()
                            }
                        },
                        label = { Text("${c.icon}  ${c.name}", maxLines = 1) },
                        shape = RoundedCornerShape(50),
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(c.color).copy(alpha = 0.12f)),
                        border = null,
                    )
                }
                AssistChip(onClick = { onOpen(t.id) }, label = { Text(tr("بند آخر", "Other line")) }, shape = RoundedCornerShape(50))
            }
        }
    }
}

/** Four budget lines to offer for one-tap classification: most used first, then day-to-day ones. */
fun quickCategories(db: Db, cats: List<Category>): List<Category> {
    val byId = cats.associateBy { it.id }
    val top = db.topCategories(4).mapNotNull { byId[it] }
    return (top + cats.filter { it.type == Db.DAILY } + cats).distinctBy { it.id }.take(4)
}

/** Account filter, shown only when there is more than one account. */
@Composable
fun AccountChips(accounts: List<Account>, selected: Long?, onSelect: (Long?) -> Unit) {
    if (accounts.size < 2) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(tr("كل الحسابات", "All accounts")) },
            shape = RoundedCornerShape(50))
        accounts.forEach { a ->
            FilterChip(selected = selected == a.id, onClick = { onSelect(a.id) }, label = { Text(a.name) },
                shape = RoundedCornerShape(50))
        }
    }
}

/** Two or three choices as one pill control (day / week / month). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(4.dp),
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (on) Ui.p.hero else androidx.compose.ui.graphics.SolidColor(Color.Transparent))
                    .clickable { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(o, color = if (on) Ui.p.onPine else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

private val typeNames: Map<Int, String> get() = mapOf(
    Db.FIXED to tr("المصاريف الثابتة", "Fixed costs"),
    Db.DAILY to tr("المصروف اليومي", "Day-to-day spending"),
    Db.RESERVE to tr("الاحتياط والادخار", "Reserves & savings"),
)

private val shortDays: Array<String> get() = if (Lang.ar) arrayOf("", "أحد", "إثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت")
    else arrayOf("", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

/** Labels for a bar per day of [r]: weekday names for a week, day numbers otherwise. */
private fun dayLabels(r: Range): List<String> {
    val c = Calendar.getInstance().apply { timeInMillis = r.from }
    return List(r.days) {
        val l = if (r.days <= 7) shortDays[c.get(Calendar.DAY_OF_WEEK)] else c.get(Calendar.DAY_OF_MONTH).toString()
        c.add(Calendar.DAY_OF_YEAR, 1)
        l
    }
}

private fun todayIndex(r: Range): Int {
    val now = System.currentTimeMillis()
    return if (now in r.from until r.to) ((now - r.from) / Period.DAY).toInt() else -1
}

// ---------------- home ----------------

/**
 * The month as a row of days: green = stayed under the daily limit, red = went over,
 * the gold outline is today, faded ones are still ahead.
 */
@Composable
fun DayStrip(perDay: DoubleArray, limit: Double, today: Int, onDark: Color) {
    Row(Modifier.fillMaxWidth().height(26.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in perDay.indices) {
            val spent = perDay[i]
            val c = when {
                i > today -> onDark.copy(alpha = 0.10f)
                spent <= 0.0 -> onDark.copy(alpha = 0.22f)
                limit > 0 && spent > limit -> Ui.p.pomegranate
                else -> Ui.p.mint
            }
            Box(
                Modifier.weight(1f).height(if (i == today) 26.dp else 18.dp).align(Alignment.Bottom)
                    .clip(RoundedCornerShape(3.dp)).background(c)
                    .then(if (i == today) Modifier.border(2.dp, Ui.p.dinar, RoundedCornerShape(3.dp)) else Modifier)
            )
        }
    }
}

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (h) {
        in 4..11 -> tr("صباح الخير", "Good morning")
        in 12..16 -> tr("طاب يومك", "Good afternoon")
        else -> tr("مساء الخير", "Good evening")
    }
}

@Composable
fun HomeScreen(
    db: Db, tick: Int, missingPerms: Boolean,
    onAskPerms: () -> Unit, onOpen: (Long) -> Unit, onGoSettings: () -> Unit, onGoDebts: () -> Unit,
    onTriage: () -> Unit, refresh: () -> Unit,
) {
    val al = remember(tick) { Budget.allowance(db) }
    val ci = al.cycle
    val cycle = ci.range
    val cats = remember(tick) { db.categories() }
    val spent = remember(tick) { db.spentByCategory(cycle.from, cycle.to) }
    val pending = remember(tick) { db.pending() }
    val debts = remember(tick) { db.debts().filter { !it.closed } }
    val senders = remember(tick) { db.senders() }
    val hasTxns = remember(tick) { db.recent(1).isNotEmpty() }
    val dailyIds = cats.filter { it.type == Db.DAILY }.map { it.id }.toSet()
    val perDayLimit = cats.filter { it.type == Db.DAILY }.sumOf { it.budget } / cycle.days
    val perDay = remember(tick) { db.spentPerDay(cycle.from, cycle.to, dailyIds) }
    val totalOut = spent.values.sum()
    val p = Ui.p
    var showBalance by remember { mutableStateOf(false) }
    var showToday by remember { mutableStateOf(false) }
    val bigOne = remember(tick) { Budget.bigOneOff(db, al) }
    var catSheet by remember { mutableStateOf<Long?>(null) }
    var showMath by remember { mutableStateOf(false) }
    var allLines by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val streak = remember(tick) { Streak.current(db) }
    val ctx = LocalContext.current
    val noSalary = remember(tick) { db.noSalary() }

    val steps = listOf(
        SetupStep("📩", tr("اسمح بقراءة الرسائل", "Allow reading SMS"), !missingPerms, onAskPerms, onHelp = { showHelp = true }),
        SetupStep("🏦", tr("اختر رسائل بنكك", "Pick your bank's messages"), senders.isNotEmpty(), onGoSettings),
        SetupStep("🎯", tr("حدّد ميزانيتك الشهرية", "Set your monthly budget"), cats.any { it.budget > 0 }, onGoSettings),
    )
    // Until the bank is connected there is nothing to show but zeros: show only the welcome and the steps.
    val welcome = !hasTxns && (missingPerms || senders.isEmpty())

    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(greeting(), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onGoSettings) {
                    Icon(Icons.Filled.Settings, tr("الإعدادات", "Settings"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (welcome) item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("👋", fontSize = 56.sp)
                Text(tr("أهلاً بك في ميزانيتي", "Welcome to My Budget"), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 8.dp))
                Text(tr("التطبيق يقرأ رسائل البنك ويحسب لك كم يمكنك أن تصرف كل يوم.",
                    "The app reads your bank SMS and tells you how much you can spend each day."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (steps.any { !it.done }) item { SetupCard(steps) }
        if (welcome) return@LazyColumn

        // The one number that matters: what you can spend today. Tap for how it's worked out.
        item {
            val over = al.leftToday < 0
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .shadow(28.dp, RoundedCornerShape(30.dp), ambientColor = p.heroStart, spotColor = p.heroStart)
                    .clip(RoundedCornerShape(30.dp)).background(p.hero)
                    .clickable { showBalance = true }.padding(22.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (over) tr("تجاوزت حدك اليوم", "Over today's limit") else tr("يمكنك أن تصرف اليوم", "You can spend today"),
                        Modifier.weight(1f), color = p.onPine.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyLarge)
                    Text("ⓘ", color = p.onPine.copy(alpha = 0.7f), fontSize = 18.sp)
                }
                AnimatedAmount(if (over) -al.leftToday else al.leftToday, 52.sp, if (over) Color(0xFFFFC2CC) else p.onPine,
                    weight = FontWeight.Bold)
                Text(
                    when {
                        al.spendable != null && al.spendable < 0 -> tr("رصيدك لا يكفي فواتيرك هذا الشهر", "Your balance won't cover this month's bills")
                        over -> tr("حاول ألا تصرف أكثر اليوم", "Try not to spend more today")
                        noSalary -> tr("في اليوم، لمدة ${daysText(al.daysLeft)} حتى نهاية الشهر", "a day, for the ${daysText(al.daysLeft)} left this month")
                        else -> tr("في اليوم، لمدة ${daysText(al.daysLeft)} حتى الراتب", "a day, for ${daysText(al.daysLeft)} until payday")
                    },
                    color = if (al.spendable != null && al.spendable < 0) Color(0xFFFFC2CC) else p.onPine.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                DayStrip(perDay, perDayLimit, todayIndex(cycle), p.onPine)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    HeroStat(tr("صرفت اليوم ›", "Spent today ›"), fmt(al.spentToday),
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { showToday = true })
                    HeroStat(tr("غداً", "Tomorrow"), fmt(al.tomorrow), Modifier.weight(1f))
                }
            }
        }
        // A big spend today: ask once whether it was a one-off to spread over the month.
        if (bigOne != null) item {
            val days = al.daysLeft
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(20.dp))
                    .background(p.dinar.copy(alpha = 0.14f)).border(1.dp, p.dinar.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Text(tr("صرفة كبيرة اليوم: ${bigOne.title} بـ${fmt(bigOne.amount)}", "A big spend today: ${bigOne.title}, ${fmt(bigOne.amount)}"),
                    style = MaterialTheme.typography.titleSmall)
                Text(tr("هل هي لمرة واحدة (تعبئة بنزين، صيدلية…)؟ إن وزّعتها على ${daysText(days)} لن تحسب كلها على اليوم، وتنقص من كل يوم ${fmt(bigOne.amount / days)} فقط.",
                    "Was it a one-off (a full tank, a pharmacy run…)? Spread over ${daysText(days)}, it won't all count against today: each day gets ${fmt(bigOne.amount / days)} less."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { db.setSpread(bigOne.id, true); AllowanceWidget.refreshAll(ctx); refresh() },
                        shape = RoundedCornerShape(12.dp)) { Text(tr("وزّعها على الشهر", "Spread it")) }
                    TextButton(onClick = { db.setSpread(bigOne.id, false); refresh() }) { Text(tr("لا، هي صرف اليوم", "No, it's today's")) }
                }
            }
        }
        // Balance and salary as two small tiles.
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MiniTile(
                    "🏦", tr("رصيدك", "Balance"),
                    al.balance?.let { fmt(it) } ?: tr("أدخله", "Add it"),
                    Modifier.weight(1f), onClick = { showBalance = true },
                    valueColor = if ((al.balance ?: 0.0) < 0) p.pomegranate else Color.Unspecified,
                )
                val days = max(0, ((ci.nextSalary - Period.day().from) / Period.DAY).toInt())
                if (!noSalary) MiniTile(
                    "💰", tr("الراتب", "Salary"),
                    when {
                        ci.late -> tr("متأخر", "Late")
                        days == 0 -> tr("اليوم", "Today")
                        else -> tr("بعد ${daysText(days)}", "In ${daysText(days)}")
                    },
                    Modifier.weight(1f),
                    valueColor = if (ci.late) p.dinar else Color.Unspecified,
                )
            }
        }
        if (streak >= 2) item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                    .background(p.dinar.copy(alpha = 0.14f)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔥", fontSize = 22.sp)
                Text(tr("${daysText(streak)} متتالية ضمن حدك اليومي", "${daysText(streak)} in a row within your daily limit"),
                    Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        if (pending.isNotEmpty()) item {
            TriageCard(pending, cats, onTriage)
        }
        // Budget lines: the ones in use, most used first; the rest behind "show all".
        item {
            val used = cats.filter { it.budget > 0 || (spent[it.id] ?: 0.0) > 0 }
                .sortedByDescending { spent[it.id] ?: 0.0 }
            val shown = if (allLines) cats.sortedByDescending { spent[it.id] ?: 0.0 } else used.take(5)
            val more = if (allLines) 0 else cats.size - shown.size
            Section(tr("بنودك هذا الشهر", "Your budget this month")) {
                if (shown.isEmpty()) {
                    Text(tr("لا يوجد صرف بعد هذا الشهر.", "No spending yet this month."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                }
                shown.forEach { c -> CatBar(c, c.name, spent[c.id] ?: 0.0, c.budget) { catSheet = c.id } }
                if (more > 0 || allLines) {
                    TextButton(onClick = { allLines = !allLines }) {
                        Text(if (allLines) tr("عرض أقل", "Show less") else tr("عرض كل البنود ($more)", "Show all lines ($more)"))
                    }
                }
            }
        }
        item {
            Section(tr("الشهر", "This month"), action = {
                TextButton(onClick = { MonthCard.share(ctx, db) }) { Text(tr("مشاركة 📤", "Share 📤")) }
            }) {
                val otherIn = remember(tick) { db.otherIncome(cycle.from, cycle.to) }
                val startBal = remember(tick) { db.totalBalanceAt(cycle.from) }
                val income = ci.salary + otherIn
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val slices = cats.map { c -> Slice(c.name, spent[c.id] ?: 0.0, Color(c.color)) } +
                        listOf(Slice(tr("بدون بند", "No line"), spent[null] ?: 0.0, Color(0xFF9E9E9E)))
                    Box(Modifier.width(120.dp)) {
                        if (income > 0) {
                            DonutChart(slices, "${((totalOut / income) * 100).toInt()}%", tr("من الدخل", "of income"),
                                size = 116.dp, showLegend = false)
                        } else {
                            DonutChart(slices, "", "", size = 116.dp, showLegend = false)
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(tr("دخل", "In"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Amount(income, 22.sp, p.mint)
                        Spacer(Modifier.height(8.dp))
                        Text(tr("صرف", "Out"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Amount(totalOut, 22.sp)
                    }
                }
                // Everything adds up to the real balance; whatever the SMS did not explain shows as its own line.
                if (al.balance != null) {
                    TextButton(onClick = { showMath = !showMath }, modifier = Modifier.padding(top = 4.dp)) {
                        Text(if (showMath) tr("إخفاء التفاصيل", "Hide details") else tr("كيف وصلنا إلى رصيدك؟", "How did we get your balance?"))
                    }
                }
                AnimatedVisibility(showMath && al.balance != null) { Column {
                if (al.balance != null) {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = p.line)
                    if (startBal != null) KV(if (ci.bySalary) tr("رصيدك قبل الراتب", "Balance before salary") else tr("رصيدك في بداية الشهر", "Balance at month start"), fmt(startBal))
                    if (noSalary) KV(tr("+ الدخل", "+ Income"), fmt(income))
                    else KV(if (ci.received) tr("+ الراتب", "+ Salary") else tr("+ الراتب (لم يصل بعد)", "+ Salary (not arrived yet)"), fmt(ci.salary))
                    if (otherIn > 0 && !noSalary) KV(tr("+ دخل آخر (كليك وتحويلات واردة)", "+ Other income (CliQ and incoming transfers)"), fmt(otherIn))
                    KV(tr("− صرفت", "− Spent"), fmt(totalOut))
                    val expected = (startBal ?: 0.0) + income - totalOut
                    val gap = al.balance - expected
                    if (startBal != null && kotlin.math.abs(gap) >= 0.5) {
                        KV(if (gap < 0) tr("− حركات لم تصل عنها رسالة", "− Movements with no SMS") else tr("+ حركات لم تصل عنها رسالة", "+ Movements with no SMS"),
                            fmt(kotlin.math.abs(gap)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = p.line)
                    KV(tr("= رصيدك الآن", "= Balance now"), fmt(al.balance), bold = true)
                } } }
            }
        }
        if (debts.isNotEmpty()) item {
            Section(tr("الديون", "Debts"), action = { TextButton(onClick = onGoDebts) { Text(tr("التفاصيل", "Details")) } }) {
                val iOwe = debts.filter { !it.owedToMe }.sumOf { it.remaining }
                val owedMe = debts.filter { it.owedToMe }.sumOf { it.remaining }
                if (iOwe > 0) KV(tr("عليّ للآخرين", "I owe"), fmt(iOwe), color = p.pomegranate, bold = true)
                if (owedMe > 0) KV(tr("لي عند الآخرين", "Owed to me"), fmt(owedMe), color = p.mint, bold = true)
            }
        }
    }

    if (showBalance) BalanceSheet(db) { showBalance = false; refresh() }
    if (showToday) TodaySheet(db, onOpen = { showToday = false; onOpen(it) }) { showToday = false; refresh() }
    if (showHelp) PermissionHelpSheet { showHelp = false; refresh() }
    catSheet?.let { id ->
        CategorySheet(db, cats.firstOrNull { it.id == id }, cycle, tr("هذا الشهر", "This month"),
            onOpen = { tid -> catSheet = null; onOpen(tid) }, onDone = { catSheet = null }, onChanged = refresh)
    }
}

/** One thing to do before the app can help: [hint] shows under it while it is not done. */
data class SetupStep(val icon: String, val title: String, val done: Boolean, val onClick: () -> Unit, val onHelp: (() -> Unit)? = null)

/** "Get started": the setup steps as a short checklist; the next one to do is highlighted. */
@Composable
fun SetupCard(steps: List<SetupStep>) {
    val p = Ui.p
    val doneCount = steps.count { it.done }
    val next = steps.indexOfFirst { !it.done }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, p.line, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("لنبدأ", "Let's get started"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(tr("$doneCount من ${steps.size}", "$doneCount of ${steps.size}"), fontFamily = Ui.numbers,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        LinearProgressIndicator(
            progress = { doneCount.toFloat() / steps.size },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = p.mint, trackColor = p.line, drawStopIndicator = {},
        )
        steps.forEachIndexed { i, s ->
            val isNext = i == next
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (isNext) p.pine.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable(enabled = !s.done, onClick = s.onClick)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (s.done) p.mint.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) { Text(if (s.done) "✓" else s.icon, fontSize = 18.sp, color = p.mint) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(s.title, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (s.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    if (isNext && s.onHelp != null) {
                        Text(tr("لا يعمل؟ اضغط لترى الطريقة", "Not working? Tap to see how"),
                            style = MaterialTheme.typography.bodySmall, color = p.pine,
                            modifier = Modifier.clickable(onClick = s.onHelp).padding(top = 2.dp))
                    }
                }
                if (!s.done) Icon(
                    if (Lang.ar) Icons.Filled.KeyboardArrowLeft else Icons.Filled.KeyboardArrowRight, null,
                    tint = if (isNext) p.pine else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A small rounded tile: emoji, label and one value. */
@Composable
fun MiniTile(
    icon: String, label: String, value: String, modifier: Modifier,
    onClick: (() -> Unit)? = null, valueColor: Color = Color.Unspecified,
) {
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, Ui.p.line, RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 22.sp)
        Column(Modifier.padding(start = 10.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontFamily = Ui.numbers, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = valueColor,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Ui.p.onPine.copy(alpha = 0.78f), fontSize = 12.sp)
        Text(value, color = Ui.p.onPine, fontFamily = Ui.numbers, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------- transactions ----------------

private fun dayHeader(dayStart: Long): String {
    val today = Period.day().from
    return when {
        dayStart == today -> tr("اليوم", "Today")
        dayStart == today - Period.DAY -> tr("أمس", "Yesterday")
        else -> Period.label(Period.MODE_DAY, Range(dayStart, dayStart + Period.DAY))
    }
}

private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
fun TxnsScreen(db: Db, tick: Int, onOpen: (Long) -> Unit, refresh: () -> Unit, onTriage: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var onlyPending by rememberSaveable { mutableStateOf(false) }
    var acc by rememberSaveable { mutableStateOf<Long?>(null) }
    val catList = remember(tick) { db.categories() }
    val cats = catList.associateBy { it.id }
    val quick = remember(tick) { quickCategories(db, catList) }
    val accounts = remember(tick) { db.accounts() }
    val accNames = if (accounts.size > 1) accounts.associate { it.id to it.name } else emptyMap()
    val all = remember(tick, onlyPending) { if (onlyPending) db.pending() else db.recent(2000) }
    val q = query.trim().lowercase()
    val list = all.filter { t ->
        (acc == null || t.accountId == acc) && (q.isEmpty() ||
            t.merchant.lowercase().contains(q) || t.note.lowercase().contains(q) || t.kind.contains(q) ||
            t.title.lowercase().contains(q) || t.kindText.lowercase().contains(q) ||
            (cats[t.categoryId]?.name?.contains(q) == true) ||
            String.format(java.util.Locale.US, "%.3f", t.amount).startsWith(q.replace(",", ".")) ||
            String.format(java.util.Locale.US, "%.2f", t.amount).startsWith(q.replace(",", ".")))
    }
    val days = list.groupBy { startOfDay(it.time) }.toList().sortedByDescending { it.first }

    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            androidx.compose.material3.OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text(tr("ابحث باسم المتجر أو المبلغ أو الملاحظة", "Search by shop, amount or note")) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, tr("مسح البحث", "Clear search")) }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
            Segmented(listOf(tr("كل الحركات", "All"), tr("بحاجة إلى تصنيف", "To sort")), if (onlyPending) 1 else 0) { onlyPending = it == 1 }
            AccountChips(accounts, acc) { acc = it }
            if (onlyPending && list.size > 1) {
                androidx.compose.material3.Button(
                    onClick = onTriage, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(48.dp),
                ) { Text(tr("صنّفها بسرعة، واحدة تلو الأخرى", "Sort them quickly, one by one")) }
            }
            if (q.isNotEmpty()) {
                Text(tr("${list.size} نتيجة، مجموعها ${fmt(list.filter { !it.income }.sumOf { it.amount })}",
                    "${list.size} results, totalling ${fmt(list.filter { !it.income }.sumOf { it.amount })}"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            }
        }
        if (list.isEmpty()) item {
            Text(
                if (onlyPending) tr("كل الحركات مصنّفة.", "Everything is sorted.")
                else tr("لا توجد حركات بعد. استورد رسائل هذا الشهر من الإعدادات، أو انتظر أول رسالة من البنك.",
                    "No transactions yet. Import this month's SMS from Settings, or wait for the first one from your bank."),
                Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for ((day, txns) in days) {
            item(key = "d$day") {
                val out = txns.filter { !it.income }.sumOf { it.amount }
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(dayHeader(day), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    if (out > 0) Text("−" + fmt(out), fontFamily = Ui.numbers, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onlyPending) {
                items(txns, key = { it.id }) { t ->
                    Box(Modifier.animateItem().padding(horizontal = 16.dp, vertical = 4.dp)) { PendingRow(db, t, quick, onOpen, refresh) }
                }
            } else {
                item(key = "g$day") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        txns.forEachIndexed { i, t ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 68.dp, end = 16.dp), color = Ui.p.line)
                            TxnRow(t, cats[t.categoryId], accNames[t.accountId]) { onOpen(t.id) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- reports ----------------

@Composable
fun ReportsScreen(db: Db, tick: Int, onOpen: (Long) -> Unit, refresh: () -> Unit = {}) {
    var mode by rememberSaveable { mutableIntStateOf(Period.MODE_MONTH) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var acc by rememberSaveable { mutableStateOf<Long?>(null) }
    // 0 = everything, 1 = money in, 2 = money out (the transaction list at the bottom).
    var flow by rememberSaveable { mutableIntStateOf(0) }
    var catSheet by remember { mutableStateOf<Long?>(null) }
    val monthInfo = remember(tick, mode, offset) { if (mode == Period.MODE_MONTH) Budget.cycle(db, offset) else null }
    // A picked range: day starts, the end day included.
    var customFrom by rememberSaveable { mutableStateOf(Period.day(-30).from) }
    var customTo by rememberSaveable { mutableStateOf(Period.day().to) }
    var picking by remember { mutableStateOf(false) }
    val range = remember(tick, mode, offset, customFrom, customTo) {
        monthInfo?.range ?: when (mode) {
            Period.MODE_ALL -> Period.all(db.firstTxnTime())
            Period.MODE_CUSTOM -> Range(customFrom, customTo)
            else -> Period.range(mode, offset, 1)
        }
    }
    val custom = mode == Period.MODE_CUSTOM
    // Long ranges are read by month rather than by day.
    val longView = mode == Period.MODE_YEAR || mode == Period.MODE_ALL || (custom && range.days > 62)
    val totalsView = longView || custom
    val accounts = remember(tick) { db.accounts() }
    val accNames = if (accounts.size > 1) accounts.associate { it.id to it.name } else emptyMap()
    val all = remember(tick, mode, offset) { db.txns(range.from, range.to) }
    val list = if (acc == null) all else all.filter { it.accountId == acc }
    val cats = remember(tick) { db.categories() }
    val catMap = cats.associateBy { it.id }

    val outs = list.filter { !it.income }
    val out = outs.sumOf { it.amount }
    val inc = list.filter { it.income }.sumOf { it.amount }
    val now = System.currentTimeMillis()
    val elapsed = if (now in range.from until range.to)
        min(range.days, ((now - range.from) / Period.DAY).toInt() + 1) else range.days
    val byCat = outs.groupBy { it.categoryId }.mapValues { (_, v) -> v.sumOf { it.amount } }
    val top = outs.filter { it.merchant.isNotBlank() }.groupBy { it.merchant }
        .mapValues { (_, v) -> v.sumOf { it.amount } to v.size }
        .entries.sortedByDescending { it.value.first }.take(5)

    val cycleDays = remember(tick) { Budget.cycle(db).range.days }
    val dailyIds = cats.filter { it.type == Db.DAILY }.map { it.id }.toSet()
    val dailyBudget = cats.filter { it.type == Db.DAILY }.sumOf { it.budget }
    val perDayLimit = dailyBudget / cycleDays
    val dailyTarget = perDayLimit * range.days
    val dailySpent = byCat.filterKeys { it != null && it in dailyIds }.values.sum()

    val bars = remember(list, range) {
        val arr = DoubleArray(range.days)
        for (t in outs) {
            val c = t.categoryId ?: continue
            if (c !in dailyIds) continue
            val i = ((t.time - range.from) / Period.DAY).toInt()
            if (i in arr.indices) arr[i] += t.amount
        }
        arr
    }

    // Year and all-time: totals per calendar month instead of per day.
    val monthRanges = remember(range, longView) { if (longView) Period.months(range) else emptyList() }
    val monthBars = remember(list, monthRanges) {
        DoubleArray(monthRanges.size) { i -> outs.filter { it.time >= monthRanges[i].from && it.time < monthRanges[i].to }.sumOf { it.amount } }
    }
    val monthLabels = remember(monthRanges) {
        val f = java.text.SimpleDateFormat(if (mode == Period.MODE_ALL && monthRanges.size > 12) "M/yy" else "M", java.util.Locale.US)
        monthRanges.map { f.format(java.util.Date(it.from)) }
    }
    val salaryTotal = remember(tick, range) { db.salaries().filter { it.time >= range.from && it.time < range.to }.sumOf { it.amount } }
    val prevYear = remember(tick, mode, offset, acc) {
        if (mode != Period.MODE_YEAR) null else {
            val r = Period.year(offset - 1)
            db.txns(r.from, r.to).filter { !it.income && (acc == null || it.accountId == acc) }.sumOf { it.amount }
        }
    }

    val prev = remember(tick, mode, offset, acc) {
        if (mode != Period.MODE_MONTH) null else {
            val r = Budget.cycle(db, offset - 1).range
            db.txns(r.from, r.to).filter { !it.income && (acc == null || it.accountId == acc) }
                .groupBy { it.categoryId }.mapValues { (_, v) -> v.sumOf { it.amount } }
        }
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column {
                Segmented(listOf(tr("يوم", "Day"), tr("أسبوع", "Week"), tr("شهر", "Month"), tr("سنة", "Year"), tr("الكل", "All")),
                    if (custom) -1 else mode) { mode = it; offset = 0 }
                // Any range: since payday in one tap, or two dates from the calendar.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val sinceSalary = remember(tick) { Budget.cycle(db).range.from }
                    val onSalary = custom && customFrom == sinceSalary && customTo == Period.day().to
                    FilterChip(onSalary, {
                        customFrom = sinceSalary; customTo = Period.day().to; mode = Period.MODE_CUSTOM
                    }, { Text(if (db.noSalary()) tr("💰 من بداية الشهر", "💰 Since month start") else tr("💰 من يوم الراتب", "💰 Since payday")) })
                    FilterChip(custom && !onSalary, { picking = true }, { Text(tr("📅 من تاريخ إلى تاريخ", "📅 Pick dates")) })
                }
                if (custom) {
                    Text(Period.label(mode, range) + "  ✎",
                        Modifier.fillMaxWidth().clickable { picking = true }.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = Ui.numbers, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                } else if (mode == Period.MODE_ALL) {
                    Text(tr("من أول حركة: ", "Since the first transaction: ") + Period.label(mode, range),
                        Modifier.fillMaxWidth().padding(vertical = 12.dp), style = MaterialTheme.typography.titleMedium,
                        fontFamily = Ui.numbers, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                } else Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { offset-- }) {
                        Icon(if (Lang.ar) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowLeft, tr("الفترة السابقة", "Previous"))
                    }
                    Text(Period.label(mode, range), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        fontFamily = Ui.numbers, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { offset++ }, enabled = offset < 0) {
                        Icon(if (Lang.ar) Icons.Filled.KeyboardArrowLeft else Icons.Filled.KeyboardArrowRight, tr("الفترة التالية", "Next"))
                    }
                }
                AccountChips(accounts, acc) { acc = it }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(tr("صرفت", "Spent"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Amount(out, 40.sp, weight = FontWeight.Bold)
                Row(Modifier.padding(top = 4.dp)) {
                    if (longView) {
                        val monthsSoFar = max(1, monthRanges.count { it.from <= now })
                        Text(tr("بمعدل ${fmt(out / monthsSoFar)} في الشهر", "${fmt(out / monthsSoFar)} a month on average"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp))
                    } else if (mode != Period.MODE_DAY) {
                        Text(tr("بمعدل ${fmt(out / max(1, elapsed))} في اليوم", "${fmt(out / max(1, elapsed))} a day on average"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp))
                    }
                    Text(txnsText(list.size), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            val rctx = LocalContext.current
            Section(tr("الملخص", "Summary"), action = {
                if (mode == Period.MODE_MONTH) {
                    TextButton(onClick = { MonthCard.share(rctx, db, offset) }) { Text(tr("مشاركة 📤", "Share 📤")) }
                }
            }) {
                if (inc > 0) KV(tr("إجمالي الدخل", "Total income"), "+" + fmt(inc), color = Ui.p.mint)
                if (totalsView) {
                    if (!db.noSalary() && salaryTotal > 0) KV(tr("الرواتب", "Salaries"), fmt(salaryTotal))
                    KV(tr("إجمالي الصرف", "Total spending"), fmt(out))
                    KV(tr("الدخل − الصرف", "Income − spending"), fmt(inc - out), bold = true,
                        color = if (inc - out < 0) Ui.p.pomegranate else Ui.p.mint)
                    if (prevYear != null && prevYear > 0) {
                        val d = out - prevYear
                        KV(tr("السنة الماضية", "Last year"), fmt(prevYear))
                        KV(if (d > 0) tr("صرفت أكثر بمقدار", "Spent more by") else tr("صرفت أقل بمقدار", "Spent less by"), fmt(abs(d)),
                            bold = true, color = if (d > 0) Ui.p.pomegranate else Ui.p.mint)
                    }
                } else if (monthInfo != null) {
                    if (!db.noSalary()) KV(tr("الراتب", "Salary"), if (monthInfo.received) fmt(monthInfo.salary) else tr("لم يصل في هذه الفترة", "Not in this period"))
                    KV(tr("الدخل − الصرف", "Income − spending"), fmt(inc - out), bold = true,
                        color = if (inc - out < 0) Ui.p.pomegranate else Ui.p.mint)
                } else {
                    KV(tr("حد المصروف اليومي", "Day-to-day limit"), fmt(dailyTarget))
                    KV(tr("صرفت منه", "Spent of it"), fmt(dailySpent), bold = true,
                        color = if (dailySpent > dailyTarget) Ui.p.pomegranate else Ui.p.mint)
                }
            }
        }
        if (longView && monthRanges.isNotEmpty()) item {
            Section(tr("الصرف حسب الشهر", "Spending by month")) {
                BarChart(monthBars, monthLabels, null, monthRanges.indexOfFirst { now >= it.from && now < it.to },
                    labelEvery = if (monthRanges.size > 12) (monthRanges.size + 11) / 12 else 1)
                val best = monthBars.withIndex().filter { monthRanges[it.index].from <= now }.maxByOrNull { it.value }
                if (best != null && best.value > 0) {
                    Text(tr("أعلى شهر: ${monthLabels[best.index]} بمبلغ ${fmt(best.value)}", "Highest month: ${monthLabels[best.index]} with ${fmt(best.value)}"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        if (mode != Period.MODE_DAY && !longView && range.days > 1) item {
            Section(tr("المصروف اليومي", "Day-to-day spending")) {
                BarChart(bars, dayLabels(range), perDayLimit, todayIndex(range), labelEvery = if (range.days > 7) 5 else 1)
                Text(tr("الخط المتقطع هو حدك اليومي (${fmt(perDayLimit)}). الأحمر يوم تجاوزته فيه.",
                    "The dashed line is your daily limit (${fmt(perDayLimit)}). Red marks a day you went over."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Section(tr("حسب البند", "By budget line")) {
                if (byCat.isEmpty()) Text(tr("لا يوجد صرف في هذه الفترة", "No spending in this period"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    val slices = cats.map { c -> Slice("${c.icon} ${c.name}", byCat[c.id] ?: 0.0, Color(c.color)) } +
                        listOf(Slice("❔ " + tr("بدون بند", "No line"), byCat[null] ?: 0.0, Color(0xFF9E9E9E)))
                    DonutChart(slices, fmt(out), Period.label(mode, range), size = 170.dp, showLegend = false)
                    Spacer(Modifier.height(8.dp))
                }
                cats.forEach { c ->
                    val s = byCat[c.id] ?: 0.0
                    val budget = when {
                        mode == Period.MODE_MONTH -> c.budget
                        longView -> 0.0
                        c.type == Db.DAILY -> c.budget / cycleDays * range.days
                        else -> 0.0
                    }
                    if (s > 0 || (mode == Period.MODE_MONTH && c.budget > 0)) CatBar(c, c.name, s, budget) { catSheet = c.id }
                }
                val unc = byCat[null] ?: 0.0
                if (unc > 0) CatBar(null, tr("بدون بند", "No line"), unc, 0.0) { catSheet = -1L }
            }
        }
        if (prev != null && (prev.isNotEmpty() || byCat.isNotEmpty())) item {
            Section(tr("مقارنة بالشهر الماضي", "Compared with last month")) {
                val prevTotal = prev.values.sum()
                val diff = out - prevTotal
                KV(tr("الشهر الماضي", "Last month"), fmt(prevTotal))
                KV(if (diff > 0) tr("صرفت أكثر بمقدار", "Spent more by") else tr("وفّرت", "Saved"), fmt(abs(diff)), bold = true,
                    color = if (diff > 0) Ui.p.pomegranate else Ui.p.mint)
                val changes = cats.map { c -> c to ((byCat[c.id] ?: 0.0) - (prev[c.id] ?: 0.0)) }
                    .filter { abs(it.second) >= 1 }.sortedByDescending { abs(it.second) }.take(4)
                changes.forEach { (c, d) ->
                    KV("${c.icon}  ${c.name}", (if (d > 0) "▲ " else "▼ ") + fmt(abs(d)),
                        color = if (d > 0) Ui.p.pomegranate else Ui.p.mint)
                }
                monthTip(cats, byCat, prev, offset == 0)?.let { tip ->
                    Text(tip, Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp))
                        .background(Ui.p.dinar.copy(alpha = 0.14f)).padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (top.isNotEmpty()) item {
            Section(tr("أين صرفت أكثر", "Where you spent most")) {
                top.forEach { e -> KV(merchantLabel(e.key) + tr(" (${e.value.second} مرة)", " (${e.value.second}×)"), fmt(e.value.first)) }
            }
        }
        if (list.isNotEmpty()) item {
            val ins = list.filter { it.income }
            val outsList = list.filter { !it.income }
            val picked = when (flow) { 1 -> ins; 2 -> outsList; else -> list }
            val shown = if (longView) picked.take(100) else picked
            val title = when (flow) {
                1 -> tr("الداخل إلى الحساب", "Money in")
                2 -> tr("الخارج من الحساب", "Money out")
                else -> tr("كل الحركات", "All transactions")
            }
            Section(if (shown.size < picked.size) "$title — " + tr("آخر ${shown.size} من ${picked.size}", "latest ${shown.size} of ${picked.size}") else title) {
                // Three equal boxes: the name on top, the count or total below on one line.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    FlowBox(tr("الكل", "All"), "${list.size}", flow == 0, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f)) { flow = 0 }
                    FlowBox("⬇ " + tr("الداخل", "In"), "+" + String.format(java.util.Locale.US, "%,.2f", inc), flow == 1, Ui.p.mint, Modifier.weight(1f)) { flow = 1 }
                    FlowBox("⬆ " + tr("الخارج", "Out"), "−" + String.format(java.util.Locale.US, "%,.2f", out), flow == 2, Ui.p.pomegranate, Modifier.weight(1f)) { flow = 2 }
                }
                if (flow != 0) {
                    Text(tr("${picked.size} حركة، بمجموع ${fmt(picked.sumOf { it.amount })}", "${picked.size} transactions, ${fmt(picked.sumOf { it.amount })} in total"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                if (shown.isEmpty()) {
                    Text(if (flow == 1) tr("لم يدخل شيء إلى الحساب في هذه الفترة.", "Nothing came in during this period.")
                        else tr("لم يخرج شيء من الحساب في هذه الفترة.", "Nothing went out during this period."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
                }
                Column {
                    shown.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(color = Ui.p.line)
                        TxnRow(t, catMap[t.categoryId], accNames[t.accountId]) { onOpen(t.id) }
                    }
                }
            }
        }
    }
    if (picking) {
        ImportRangeDialog(customFrom, customTo - Period.DAY, onDismiss = { picking = false },
            title = tr("فترة التقرير", "Report range")) { f, t ->
            customFrom = f; customTo = t + Period.DAY; mode = Period.MODE_CUSTOM; picking = false
        }
    }
    catSheet?.let { id ->
        CategorySheet(db, cats.firstOrNull { it.id == id }, range, Period.label(mode, range),
            onOpen = { tid -> catSheet = null; onOpen(tid) }, onDone = { catSheet = null },
            monthly = mode == Period.MODE_MONTH, onChanged = refresh)
    }
}

/** One plain-language tip from this month vs last month. */
fun monthTip(cats: List<Category>, now: Map<Long?, Double>, prev: Map<Long?, Double>, current: Boolean): String? {
    val over = cats.filter { it.budget > 0 }.map { it to (now[it.id] ?: 0.0) - it.budget }
        .filter { it.second > 0 }.maxByOrNull { it.second }
    if (over != null) {
        return tr("«${over.first.name}» تجاوز ميزانيته بمقدار ${fmt(over.second)}. ",
            "“${over.first.name}” went ${fmt(over.second)} over budget. ") +
            if (current) tr("حاول التقليل منه في ما تبقى من الشهر.", "Try to cut back on it for the rest of the month.")
            else tr("إما أن ترفع ميزانيته، أو تنتبه له الشهر القادم.", "Either raise its budget or watch it next month.")
    }
    val rise = cats.map { it to (now[it.id] ?: 0.0) - (prev[it.id] ?: 0.0) }.filter { it.second > 0 }.maxByOrNull { it.second }
    if (rise != null && rise.second >= 10) {
        return tr("أكثر بند زاد عن الشهر الماضي هو «${rise.first.name}» (+${fmt(rise.second)}).",
            "The line that grew most since last month is “${rise.first.name}” (+${fmt(rise.second)}).")
    }
    return if (now.isNotEmpty()) tr("أنت ضمن الميزانية. استمر على هذا النحو.", "You're within budget. Keep it up.") else null
}

/**
 * Everything booked under one budget line in [range]: date, amount and who it went to.
 * [cat] null = transactions without a budget line.
 */
@Composable
fun CategorySheet(
    db: Db, cat: Category?, range: Range, periodLabel: String,
    onOpen: (Long) -> Unit, onDone: () -> Unit, monthly: Boolean = true, onChanged: () -> Unit = {},
) {
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val list = remember(cat, range, version) { db.txnsInCategory(cat?.id, range.from, range.to) }
    var undoing by remember { mutableStateOf<Txn?>(null) }
    val total = list.sumOf { it.amount }
    val color = cat?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline
    SheetFrame(onDone) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(cat?.icon ?: "❔", color, 48.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(cat?.name ?: tr("بدون بند", "No line"), style = MaterialTheme.typography.titleLarge)
                Text(periodLabel + tr("، ", ", ") + txnsText(list.size), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        KV(tr("صرفت", "Spent"), fmt(total), bold = true)
        if (monthly && cat != null && cat.budget > 0) {
            val left = cat.budget - total
            KV(tr("الميزانية", "Budget"), fmt(cat.budget))
            KV(if (left >= 0) tr("المتبقي", "Left") else tr("الزيادة", "Over"), fmt(kotlin.math.abs(left)), bold = true,
                color = if (left >= 0) Ui.p.mint else Ui.p.pomegranate)
        }
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) {
            Text(tr("لا توجد حركات على هذا البند في هذه الفترة.", "No transactions on this line in this period."), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp))
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                list.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider(color = Ui.p.line)
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpen(t.id) }.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.width(54.dp)) {
                            Text(Period.dayMonth(t.time), fontFamily = Ui.numbers, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(Period.clock(t.time), fontFamily = Ui.numbers, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium)
                            if (t.note.isNotBlank()) {
                                Text(t.note, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Amount(t.amount, 15.sp)
                        // Take it off this line (back to "to sort").
                        if (cat != null) {
                            IconButton(onClick = { undoing = t }) {
                                Text("↩", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Text(if (cat != null) tr("اضغط على حركة لتغيير بندها، أو ↩ لإلغاء تصنيفها.", "Tap a transaction to change its line, or ↩ to undo it.")
                else tr("اضغط على أي حركة لتغيير بندها أو إضافة ملاحظة.", "Tap a transaction to change its line or add a note."), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            ReceiptButtons(tr("كشف البند (PDF)", "Line statement (PDF)"), "category.pdf") { Receipt.forCategory(it, cat, range, periodLabel) }
        }
    }
    undoing?.let { t ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { undoing = null },
            title = { Text(tr("إزالة الحركة من «${cat?.name}»؟", "Remove from “${cat?.name}”?")) },
            text = {
                Column {
                    Text(fmt(t.amount) + tr("، ", ", ") + t.title, fontWeight = FontWeight.SemiBold)
                    Text(tr("اختر ما تريد:", "Choose what to do:"), Modifier.padding(top = 8.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Not spending at all: it leaves every total and the daily allowance.
                    fun done(ignore: Boolean) {
                        val ruleGone = db.unbook(t.id)
                        if (ignore) { db.ignoreTxn(t.id); Notifier.cancel(ctx, t.id) }
                        undoing = null
                        version++
                        onChanged()
                        val msg = if (ignore) tr("أُزيلت من الميزانية", "Removed from your budget")
                            else tr("أُعيدت إلى «بحاجة إلى تصنيف»", "Moved back to “To sort”")
                        val rule = if (ruleGone) tr("، ولن تُصنَّف «${t.title}» هنا تلقائياً بعد الآن", "; “${t.title}” won't go here automatically any more") else ""
                        android.widget.Toast.makeText(ctx, msg + rule, android.widget.Toast.LENGTH_LONG).show()
                    }
                    androidx.compose.material3.Button(
                        onClick = { done(ignore = true) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text(tr("ليست مصروفاً، احذفها من الميزانية", "Not spending — remove it from the budget")) }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { done(ignore = false) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) { Text(tr("أعدها إلى «بحاجة إلى تصنيف»", "Move it back to “To sort”")) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { undoing = null }) { Text(tr("إلغاء", "Cancel")) } },
        )
    }
}

/** "You have N to sort": one big invitation instead of a long list on the home page. */
@Composable
fun TriageCard(pending: List<Txn>, cats: List<Category>, onTriage: () -> Unit) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val p = Ui.p
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).pressScale(src)
            .clip(RoundedCornerShape(22.dp)).background(p.dinar.copy(alpha = 0.16f))
            .border(1.dp, p.dinar.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .clickable(interactionSource = src, indication = null, onClick = onTriage)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tr("${txnsText(pending.size)} بحاجة إلى تصنيف", "${txnsText(pending.size)} to sort"), style = MaterialTheme.typography.titleMedium)
            Text(tr("لمسة واحدة لكل حركة", "One tap each"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                cats.filter { it.type == Db.DAILY }.take(4).forEach { c ->
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .border(1.dp, Color(c.color).copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(c.icon, fontSize = 14.sp) }
                }
            }
        }
        Box(
            Modifier.clip(RoundedCornerShape(16.dp)).background(p.hero).padding(horizontal = 16.dp, vertical = 12.dp)
        ) { Text(tr("صنّف بسرعة", "Quick sort"), color = p.onPine, fontWeight = FontWeight.SemiBold) }
    }
}


/** One of the all / in / out filters in the reports. */
@Composable
private fun FlowBox(label: String, value: String, on: Boolean, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(if (on) tint.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (on) tint.copy(alpha = 0.7f) else Ui.p.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
            color = if (on) tint else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = Ui.numbers, fontSize = 13.sp, maxLines = 1, softWrap = false,
            overflow = TextOverflow.Ellipsis, color = if (on) tint else MaterialTheme.colorScheme.onSurface)
    }
}
