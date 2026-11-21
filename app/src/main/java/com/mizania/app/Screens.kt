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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
    Row {
        Text(sign + String.format(Locale.US, "%,.2f", v), fontFamily = Ui.numbers, fontSize = size,
            fontWeight = weight, color = color, maxLines = 1, modifier = Modifier.alignByBaseline())
        Text(" " + currency(), fontSize = size * 0.42f, fontWeight = FontWeight.SemiBold,
            color = color.orElse(MaterialTheme.colorScheme.onSurfaceVariant), modifier = Modifier.alignByBaseline())
    }
}

private fun Color.orElse(other: Color) = if (this == Color.Unspecified) other else this

/** A titled group on the page; the rows sit on one raised sheet. */
@Composable
fun Section(
    title: String,
    action: (@Composable () -> Unit)? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        StickerHeader(title, Modifier.padding(bottom = 6.dp), icon, action)
        Column(
            Modifier.fillMaxWidth().sticker(lift = 0.dp).padding(horizontal = 16.dp, vertical = 12.dp),
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
fun IconBadge(icon: String, color: Color, size: Dp = 40.dp) = LineIcon(icon, color, size)

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
                val ratio = (spent / budget).toFloat()
                // Over is red; a bill or saving that used exactly its amount is done, not a warning.
                val color = when {
                    spent > budget + 0.005 -> Ui.p.pomegranate
                    ratio >= 0.999f && cat != null && cat.type != Db.DAILY -> Ui.p.mint
                    ratio >= 0.8f -> Ui.p.warning
                    else -> Ui.p.deep(base)
                }
                StickerBar(ratio * grow, color, Ui.p.pastel(base), Modifier.padding(top = 8.dp))
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
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(onClickLabel = tr("فتح الحركة", "Open transaction"), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(if (t.income) "💵" else cat?.icon ?: "❔", badgeColor, 42.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                fontWeight = if (!t.income && cat == null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (!t.income && cat == null) Ui.p.pomegranate else MaterialTheme.colorScheme.onSurfaceVariant)
            if (t.note.isNotBlank()) {
                Text(t.note, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Amount(t.amount, 16.sp, if (t.income) Ui.p.mint else MaterialTheme.colorScheme.onSurface,
                sign = if (t.income) "+" else "", weight = FontWeight.Bold)
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
                Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp))
                    .background(if (toIgnore) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = if (toIgnore) Alignment.CenterEnd else Alignment.CenterStart,
            ) { Text(if (toIgnore) tr("تجاهل", "Ignore") else tr("تفاصيل", "Details"), fontWeight = FontWeight.SemiBold) }
        },
    ) {
        Column(
            Modifier.fillMaxWidth().sticker(Ui.p.raised, 22.dp, 0.dp)
        ) {
            TxnRow(t, null) { onOpen(t.id) }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quick.forEach { c ->
                    PillButton(c.name, onClick = {
                        if (db.needsLink(c, t.income)) onOpen(t.id) else {
                            db.updateTxn(t.id, c.id, t.note)
                            Notifier.cancel(ctx, t.id)
                            Processor.afterCategorized(ctx, t.id, c.id, notifyRecorded = false)
                            refresh()
                        }
                    }, fill = Ui.p.pastel(Color(c.color)))
                }
                PillButton(tr("بند آخر", "Other line"), onClick = { onOpen(t.id) })
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
        PillButton(tr("كل الحسابات", "All accounts"), onClick = { onSelect(null) }, selected = selected == null)
        accounts.forEach { a -> PillButton(a.name, onClick = { onSelect(a.id) }, selected = selected == a.id) }
    }
}

/** Two or three choices as one pill control (day / week / month). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(50))
            .background(Ui.p.raised).border(2.dp, Ui.p.edge, RoundedCornerShape(50)).padding(4.dp)
            .selectableGroup(),
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(50))
                    .background(if (on) Ui.p.pine else Color.Transparent)
                    .selectable(selected = on, role = androidx.compose.ui.semantics.Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(o, color = if (on) Ui.p.onPine else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, style = MaterialTheme.typography.labelLarge)
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
    onTriage: () -> Unit, refresh: () -> Unit, onAddCash: () -> Unit = {}, onGoTxns: () -> Unit = {},
    onBalance: () -> Unit = {}, onToday: () -> Unit = {}, onGoPlan: () -> Unit = {},
) {
    val al = remember(tick) { Budget.allowance(db) }
    val recent = remember(tick) { db.recent(3) }
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
    val ctx = LocalContext.current
    val noSalary = remember(tick) { db.noSalary() }

    val steps = listOf(
        SetupStep("📩", tr("اسمح بقراءة الرسائل", "Allow reading SMS"), !missingPerms, onAskPerms, onHelp = { showHelp = true }),
        SetupStep("🏦", tr("اختر رسائل بنكك", "Pick your bank's messages"), senders.isNotEmpty(), onGoSettings),
        SetupStep("🎯", tr("حدّد ميزانيتك الشهرية", "Set your monthly budget"), cats.any { it.budget > 0 }, onGoSettings),
    )
    // Until the bank is connected there is nothing to show but zeros: show only the welcome and the steps.
    val welcome = !hasTxns && (missingPerms || senders.isEmpty())

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(p.sun).border(2.dp, p.edge, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.AccountBalanceWallet, null, tint = p.onSun, modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(Period.label(Period.MODE_DAY, Period.day()), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(greeting(), style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onGoSettings, modifier = Modifier.size(48.dp)) {
                    Icon(AppIcons.Settings, tr("الإعدادات", "Settings"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (welcome) item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(84.dp).sticker(p.sun, 28.dp, 4.dp), contentAlignment = Alignment.Center) {
                    Icon(AppIcons.Savings, null, tint = p.onSun, modifier = Modifier.size(40.dp))
                }
                Text(tr("أهلاً بك في ميزانيتي", "Welcome to My Budget"), style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 12.dp))
                Text(tr("التطبيق يقرأ رسائل البنك ويحسب لك كم يمكنك أن تصرف كل يوم.",
                    "The app reads your bank SMS and tells you how much you can spend each day."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (steps.any { !it.done }) item { SetupCard(steps) }
        if (welcome) return@LazyColumn

        // The one number that matters: what you can spend today.
        item { TodayHero(al, noSalary, onBalance, onToday) }
        // The week's free money, when one is set.
        if (Weekly.on(db)) item { WeeklyCard(db, tick, onOpen) }
        if (todayIndex(cycle) >= 0) item { WeekRow(perDay, perDayLimit, todayIndex(cycle), cycle) }
        // One card at a time: whatever matters most right now.
        item { TodayFocus(db, tick, al, pending, cats, onTriage, refresh) }
        val lines = cats.filter { it.budget > 0 }
            .sortedBy { when (it.type) { Db.DAILY -> 0; Db.FIXED -> 1; else -> 2 } }.take(4)
        if (lines.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                StickerHeader(tr("بنودك هالشهر", "Your lines this month"), Modifier.padding(bottom = 6.dp)) {
                    TextButton(onClick = onGoPlan, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("كلها", "All")) }
                }
                val fast = remember(tick) { lines.mapNotNull { c -> Watch.pace(db, c)?.takeIf { it.fast } }.associateBy { it.cat.id } }
                lines.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { c -> LineTile(c, spent[c.id] ?: 0.0, Modifier.weight(1f), fast = c.id in fast) { catSheet = c.id } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (recent.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                StickerHeader(tr("آخر الحركات", "Latest"), Modifier.padding(bottom = 6.dp)) {
                    TextButton(onClick = onGoTxns, modifier = Modifier.heightIn(min = 48.dp)) { Text(tr("الكل", "All")) }
                }
                StickerList {
                    recent.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = p.line)
                        TxnRow(t, cats.firstOrNull { it.id == t.categoryId }) { onOpen(t.id) }
                    }
                }
            }
        }
    }

    if (showBalance) BalanceSheet(db) { showBalance = false; refresh() }
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
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).sticker(p.raised)
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
                ) {
                    val vec = if (s.done) AppIcons.Check else AppIcons.forEmoji(s.icon)
                    if (vec != null) Icon(vec, null, tint = if (s.done) p.mint else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                    else Text(s.icon, fontSize = 18.sp)
                }
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
        modifier.sticker(Ui.p.raised, 20.dp, 3.dp)
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



private fun n2(v: Double) = String.format(Locale.US, "%,.2f", v)

/** The sunny card: a ring of today's allowance with what is left in the middle. */
@Composable
private fun TodayHero(al: Allowance, noSalary: Boolean, onBalance: () -> Unit, onToday: () -> Unit) {
    val p = Ui.p
    val over = al.leftToday < 0
    val ink = p.onSun
    val used = if (al.allowance > 0) (al.spentToday / al.allowance).toFloat().coerceIn(0f, 1f) else if (over) 1f else 0f
    val grow = appearProgress(200)
    val explain = tr("كيف انحسب هالرقم", "How this is worked out")
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).sticker(p.sun, 28.dp, 5.dp)
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (over) tr("تجاوزت حد اليوم", "Over today's limit") else tr("تقدر تصرف اليوم", "You can spend today"),
                Modifier.weight(1f).padding(start = 4.dp), color = ink, style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = onBalance, modifier = Modifier.size(48.dp)) { Icon(AppIcons.Info, explain, tint = ink) }
        }
        val ring = if (over) p.pomegranate else p.accent
        Box(
            Modifier.size(208.dp).clip(RoundedCornerShape(50))
                .clickable(onClickLabel = explain, onClick = onBalance),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                val w = 20.dp.toPx()
                val inset = w / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                drawCircle(Color.White.copy(alpha = 0.55f), radius = size.minDimension / 2 - inset)
                drawArc(ink.copy(alpha = 0.10f), 0f, 360f, false, tl, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                if (used > 0f) drawArc(ring, -90f, 360f * used * grow, false, tl, arcSize,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val v = if (over) -al.leftToday else al.leftToday
                val size = when { v >= 10_000 -> 26.sp; v >= 1_000 -> 32.sp; else -> 40.sp }
                AnimatedAmount(v, size, if (over) Color(0xFFB71C1C) else ink, weight = FontWeight.ExtraBold)
                Text(if (over) tr("فوق الحد", "over") else tr("لليوم", "for today"), color = ink.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(
            when {
                al.spendable != null && al.spendable < 0 -> tr("رصيدك لا يكفي فواتيرك هذا الشهر", "Your balance won't cover this month's bills")
                over -> tr("حاول ألا تصرف أكثر اليوم", "Try not to spend more today")
                noSalary -> tr("في اليوم، لمدة ${daysText(al.daysLeft)} حتى نهاية الشهر", "a day, for the ${daysText(al.daysLeft)} left this month")
                al.cycle.late -> tr("الراتب متأخر، فحسبنا ${daysText(al.daysLeft)} احتياط", "Payday is late, so this keeps ${daysText(al.daysLeft)} of cover")
                else -> tr("في اليوم، لمدة ${daysText(al.daysLeft)} حتى الراتب", "a day, for ${daysText(al.daysLeft)} until payday")
            },
            color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroStat(tr("صرفت اليوم", "Spent today"), n2(al.spentToday), null, Modifier.weight(1f), onToday)
            HeroStat(tr("بكرا بصير معك", "Tomorrow"), n2(al.tomorrow), if (p.dark) p.mint else p.mint, Modifier.weight(1f), onBalance)
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, valueColor: Color?, modifier: Modifier, onClick: () -> Unit) {
    val p = Ui.p
    // White chips on the sun in light mode, ink chips in dark mode.
    val fill = if (p.dark) Color(0xFF1E1B3A) else Color.White
    val fg = if (p.dark) Color.White else Color(0xFF1E1B3A)
    val sub = if (p.dark) Color(0xFFC9C5E6) else Color(0xFF5E5A7A)
    Column(
        modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(18.dp)).background(fill)
            .border(2.dp, if (p.dark) Color.Black else Color(0xFF1E1B3A), RoundedCornerShape(18.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, color = sub, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Row {
            Text(value, color = valueColor ?: fg, fontFamily = Ui.numbers, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.alignByBaseline(), maxLines = 1)
            Text(" " + currency(), color = sub, fontSize = 12.sp, modifier = Modifier.alignByBaseline())
        }
    }
}

/** A week of bubbles around today: check = stayed within the daily limit, ! = went over, dashed = still ahead. */
@Composable
private fun WeekRow(perDay: DoubleArray, limit: Double, today: Int, cycle: Range) {
    val p = Ui.p
    val n = perDay.size
    val start = (today - 3).coerceIn(0, max(0, n - 7))
    val end = min(n, start + 7)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        StickerHeader(tr("هالأسبوع", "This week"), Modifier.padding(bottom = 6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in start until end) {
                val c = Calendar.getInstance().apply { timeInMillis = cycle.from + i * Period.DAY + Period.HOUR }
                val isToday = i == today
                val ahead = i > today
                val overDay = !ahead && limit > 0 && perDay[i] > limit
                val label = if (isToday) tr("اليوم", "Today") else shortDays[c.get(Calendar.DAY_OF_WEEK)]
                val desc = when {
                    ahead -> tr("$label: لسا", "$label: ahead")
                    overDay -> tr("$label: تجاوزت الحد", "$label: over the limit")
                    else -> tr("$label: ضمن الحد", "$label: within the limit")
                }
                Column(
                    Modifier.semantics(mergeDescendants = true) { contentDescription = desc },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val shape = RoundedCornerShape(50)
                    val mod = Modifier.size(38.dp)
                    Box(
                        when {
                            isToday -> mod.sticker(p.sun, 19.dp, 3.dp)
                            ahead -> mod.clip(shape).border(2.dp, p.line.copy(alpha = 0.5f), shape)
                            overDay -> mod.clip(shape).background(p.pastel(p.pomegranate)).border(2.dp, p.pomegranate, shape)
                            else -> mod.clip(shape).background(p.pastel(p.mint)).border(2.dp, p.mint, shape)
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            isToday -> Text(c.get(Calendar.DAY_OF_MONTH).toString(), color = p.onSun, fontWeight = FontWeight.Bold,
                                fontFamily = Ui.numbers, fontSize = 13.sp)
                            ahead -> {}
                            overDay -> Text("!", color = p.pomegranate, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            else -> Icon(AppIcons.Check, null, tint = p.mint, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = if (isToday) 2.dp else 6.dp),
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A budget line as a colorful tile: what's left of it, or "paid" for a bill that's done. */
@Composable
fun LineTile(c: Category, spent: Double, modifier: Modifier, fast: Boolean = false, onClick: () -> Unit) {
    val p = Ui.p
    val base = Color(c.color)
    val left = c.budget - spent
    val paid = c.type != Db.DAILY && c.budget > 0 && left <= 0.005 && left > -0.005
    val overBy = -left
    val ratio = if (c.budget > 0) (spent / c.budget).toFloat() else 0f
    val grow = appearProgress(150)
    Column(
        modifier.sticker(p.pastel(base), 22.dp, 4.dp)
            .clickable(onClickLabel = tr("تفاصيل ${c.name}", "${c.name} details"), role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(p.raised), contentAlignment = Alignment.Center) {
            val vec = AppIcons.forEmoji(c.icon)
            if (vec != null) Icon(vec, null, tint = p.deep(base), modifier = Modifier.size(22.dp))
            else Text(c.icon, fontSize = 18.sp)
        }
        Text(c.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (fast && overBy <= 0.005) Tag(tr("ماشي بسرعة", "Running fast"), p.warning)
        when {
            paid -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Check, null, tint = p.mint, modifier = Modifier.size(16.dp))
                Text(tr(" مدفوع", " Paid"), color = p.mint, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            left >= 0 -> Text(tr("باقي ${n2(left)} من ${n2(c.budget)}", "${n2(left)} left of ${n2(c.budget)}"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                fontFamily = Ui.numbers)
            else -> Text(tr("زيادة ${n2(overBy)}", "${n2(overBy)} over"), style = MaterialTheme.typography.bodySmall,
                color = p.pomegranate, fontWeight = FontWeight.SemiBold, maxLines = 1, fontFamily = Ui.numbers)
        }
        StickerBar(
            if (paid) 1f else ratio * grow,
            when { paid -> p.mint; overBy > 0.005 -> p.pomegranate; else -> p.deep(base) },
            p.raised,
        )
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
                val inc = txns.filter { it.income }.sumOf { it.amount }
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(dayHeader(day), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    if (inc > 0) Text(tr("دخل ${n2(inc)}", "${n2(inc)} in"), fontFamily = Ui.numbers, fontSize = 13.sp,
                        color = Ui.p.mint, modifier = Modifier.padding(end = 10.dp))
                    if (out > 0) Text(tr("صرف ${n2(out)}", "${n2(out)} out"), fontFamily = Ui.numbers, fontSize = 13.sp,
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
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp).sticker(lift = 0.dp)
                    ) {
                        txns.forEachIndexed { i, t ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = Ui.p.line)
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
fun ReportsScreen(db: Db, tick: Int, onOpen: (Long) -> Unit, refresh: () -> Unit = {}, startSub: Int = 0) {
    var mode by rememberSaveable { mutableIntStateOf(Period.MODE_MONTH) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var acc by rememberSaveable { mutableStateOf<Long?>(null) }
    // 0 = everything, 1 = money in, 2 = money out (the transaction list at the bottom).
    var flow by rememberSaveable { mutableIntStateOf(0) }
    var rsub by rememberSaveable { mutableIntStateOf(startSub) }
    // Three inner tabs since the habits tab went away; an older saved choice falls back to the summary.
    if (rsub !in 0..2) rsub = 0
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
                    }, { Text(if (db.noSalary()) tr("من بداية الشهر", "Since month start") else tr("من يوم الراتب", "Since payday")) })
                    FilterChip(custom && !onSalary, { picking = true }, { Text(tr("من تاريخ إلى تاريخ", "Pick dates")) })
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
                // The report in four parts instead of one long page.
                Segmented(listOf(tr("ملخص", "Summary"), tr("البنود", "Lines"), tr("الاتجاه", "Trend")), rsub) { rsub = it }
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
        if (rsub == 0) item {
            Section(tr("الملخص", "Summary")) {
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
        if (rsub == 2 && (mode != Period.MODE_DAY && !longView && range.days > 1)) item {
            Section(tr("المصروف اليومي", "Day-to-day spending")) {
                BarChart(bars, dayLabels(range), perDayLimit, todayIndex(range), labelEvery = if (range.days > 7) 5 else 1)
                Text(tr("الخط المتقطع هو حدك اليومي (${fmt(perDayLimit)}). الأحمر يوم تجاوزته فيه.",
                    "The dashed line is your daily limit (${fmt(perDayLimit)}). Red marks a day you went over."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (rsub == 1) item {
            Section(tr("حسب البند", "By budget line")) {
                if (byCat.isEmpty()) Text(tr("لا يوجد صرف في هذه الفترة", "No spending in this period"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    val slices = cats.map { c -> Slice("${c.icon} ${c.name}", byCat[c.id] ?: 0.0, Color(c.color)) } +
                        listOf(Slice("❔ " + tr("بدون بند", "No line"), byCat[null] ?: 0.0, Color(0xFF9E9E9E)))
                    DonutChart(slices, fmt(out), Period.label(mode, range), size = 170.dp, showLegend = false)
                    Spacer(Modifier.height(8.dp))
                }
                val unc = byCat[null] ?: 0.0
                if (unc > 0) CatBar(null, tr("بدون بند", "No line"), unc, 0.0) { catSheet = -1L }
            }
        }
        // Monthly, daily and savings apart, each with its total and its lines.
        if (rsub == 1) LineGroups.forEach { g ->
            val lines = cats.filter { it.type == g.type && ((byCat[it.id] ?: 0.0) > 0 || (mode == Period.MODE_MONTH && it.budget > 0)) }
            if (lines.isNotEmpty()) item {
                GroupBlock(g, lines, byCat, { c ->
                    when {
                        mode == Period.MODE_MONTH -> c.budget
                        longView -> 0.0
                        c.type == Db.DAILY -> c.budget / cycleDays * range.days
                        else -> 0.0
                    }
                }) { catSheet = it }
            }
        }
        // The trend: forecast for the running month, the balance line, and how much of the income stayed.
        if (rsub == 2 && (acc == null && mode != Period.MODE_DAY)) item { BalanceLineSection(db, range, tick) }
        if (rsub == 0) item { SavingRateSection(db, range, inc, out, mode == Period.MODE_MONTH, offset, tick) }
        // Habits.
        if (rsub == 1) item { PeopleSection(list) }
        if (rsub == 1 && (top.isNotEmpty())) item {
            Section(tr("أين صرفت أكثر", "Where you spent most")) {
                top.forEach { e -> KV(merchantLabel(e.key) + tr(" (${e.value.second} مرة)", " (${e.value.second}×)"), fmt(e.value.first)) }
            }
        }
        if (rsub == 0 && (list.isNotEmpty())) item {
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
        if (rsub == 0 && (list.isNotEmpty())) item { ExportSection(db, range, Period.label(mode, range), acc) }
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
            // For the running month: where this line is heading by payday.
            val pace = remember(cat, range) {
                if (System.currentTimeMillis() in range.from until range.to) Watch.pace(db, cat) else null
            }
            if (pace != null) {
                KV(tr("التوقع آخر الشهر", "Likely by payday"), fmt(pace.forecast), bold = pace.fast,
                    color = if (pace.fast) Ui.p.pomegranate else Color.Unspecified)
                if (pace.fast) Text(
                    if (pace.perDay > 0.005) tr("عشان تضل ضمن الميزانية، خلّي صرفك عليه حوالي ${fmt(pace.perDay)} باليوم.",
                        "To stay within the budget, keep it to about ${fmt(pace.perDay)} a day.")
                    else tr("ميزانيته خلصت، وضايل ${daysText(pace.left)} للراتب.", "Its budget is used up, with ${daysText(pace.left)} to go."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
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
    val p = Ui.p
    val first = pending.first()
    StickerCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), onClick = onTriage,
        onClickLabel = tr("صنّف بسرعة", "Quick sort")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineIcon("❔", p.pomegranate, 44.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(if (pending.size == 1) tr("حركة وحدة بدها بند", "One spend needs a line")
                    else tr("${txnsText(pending.size)} بدها بند", "${txnsText(pending.size)} need a line"),
                    color = p.pomegranate, style = MaterialTheme.typography.labelLarge)
                Text(first.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(n2(first.amount), fontFamily = Ui.numbers, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("لمسة وحدة لكل حركة", "One tap each"), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(
                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(50)).background(p.pine).padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { Text(tr("صنّف بسرعة", "Quick sort"), color = p.onPine, style = MaterialTheme.typography.labelLarge) }
        }
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
