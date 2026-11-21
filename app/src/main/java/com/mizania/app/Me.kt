package com.mizania.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private fun n2(v: Double) = String.format(Locale.US, "%,.2f", v)

/** "Me": comparing months, the reports, the balance and the settings, as one page of doors. */
@Composable
fun MeScreen(
    db: Db, tick: Int,
    onCompare: () -> Unit, onReports: (Int) -> Unit, onSettings: (Int) -> Unit,
    onBalance: () -> Unit,
) {
    val p = Ui.p
    val al = remember(tick) { Budget.allowance(db) }
    val cycle = al.cycle.range
    val list = remember(tick) { db.txns(cycle.from, cycle.to) }
    val inc = list.filter { it.income }.sumOf { it.amount }
    val out = list.filter { !it.income }.sumOf { it.amount }
    val cats = remember(tick) { db.categories() }
    val top = remember(tick) { db.spentByCategory(cycle.from, cycle.to) }
        .filterKeys { it != null }.maxByOrNull { it.value }?.let { e -> cats.firstOrNull { it.id == e.key } }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).sticker(p.pastel(p.dinar), 26.dp, 5.dp)
                    .clickable(role = Role.Button, onClick = onCompare).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.height(56.dp).clip(RoundedCornerShape(16.dp)).background(p.raised)
                        .border(2.dp, p.edge, RoundedCornerShape(16.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    listOf(0.6f, 0.45f, 0.9f, 0.75f).forEachIndexed { i, h ->
                        Box(Modifier.width(9.dp).fillMaxHeight(h).clip(RoundedCornerShape(4.dp))
                            .background(when (i) { 2 -> p.dinar; 3 -> p.accent; else -> p.line }))
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(tr("قارن الأشهر", "Compare months"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(tr("أي شهرين جنب بعض، بند ببند", "Any two months side by side, line by line"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(AppIcons.ChevronRight, null)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                StickerHeader(tr("تقارير", "Reports"), Modifier.padding(bottom = 6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReportTile(AppIcons.Category, p.accent, tr("وين راحت مصاريك", "Where it went"),
                        top?.let { tr("«${it.name}» أكبر بند", "“${it.name}” is the biggest") } ?: tr("حسب البنود", "By line"),
                        Modifier.weight(1f)) { onReports(1) }
                    ReportTile(null, p.mint, tr("معدل الادخار", "Saving rate"),
                        tr("من دخل هالشهر", "of this month's income"), Modifier.weight(1f),
                        big = if (inc > 0) "${((inc - out) / inc * 100).toInt()}%" else "—") { onReports(0) }
                }
                Spacer(Modifier.height(12.dp))
                ReportTile(AppIcons.TrendingUp, p.info, tr("الاتجاه", "Trend"),
                    tr("رصيدك عبر الشهر، وتوقّع آخره", "Your balance through the month, and where it ends"),
                    Modifier.fillMaxWidth()) { onReports(2) }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                StickerHeader(tr("فلوسي", "My money"), Modifier.padding(bottom = 6.dp))
                StickerList {
                    MeRow(AppIcons.AccountBalanceWallet, p.dinar, tr("رصيدك", "Balance"),
                        al.balance?.let { n2(it) } ?: tr("غير معروف", "Unknown"), onBalance)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                StickerHeader(tr("الإعدادات", "Settings"), Modifier.padding(bottom = 6.dp))
                StickerList {
                    MeRow(AppIcons.Category, p.accent, tr("البنود والميزانيات", "Lines and budgets"), null) { onSettings(1) }
                    HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = p.line)
                    MeRow(AppIcons.AccountBalance, p.info, tr("البنك والرسائل", "Bank and SMS"), null) { onSettings(0) }
                    HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = p.line)
                    MeRow(AppIcons.Settings, p.dinar, tr("عام: المظهر، اللغة، القفل", "General: look, language, lock"), null) { onSettings(2) }
                    HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = p.line)
                    MeRow(AppIcons.Upload, p.mint, tr("البيانات والنسخ الاحتياطي", "Data and backup"), null) { onSettings(3) }
                }
            }
        }
    }
}

@Composable
private fun ReportTile(
    icon: ImageVector?, color: Color, title: String, sub: String, modifier: Modifier,
    big: String? = null, onClick: () -> Unit,
) {
    val p = Ui.p
    Column(
        modifier.heightIn(min = 132.dp).sticker(p.pastel(color), 22.dp, 4.dp)
            .clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (big != null) Text(big, fontFamily = Ui.numbers, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = p.deep(color))
        else Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(p.raised), contentAlignment = Alignment.Center) {
            Icon(icon!!, null, tint = p.deep(color), modifier = Modifier.size(22.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MeRow(icon: ImageVector, color: Color, title: String, value: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VecTile(icon, color, 38.dp)
        Text(title, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge)
        if (value != null) Text(value, fontFamily = Ui.numbers, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            modifier = Modifier.padding(end = 6.dp))
        Icon(AppIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

// ---------------- compare months ----------------

// Month names stay in English in both languages: short and easy to tell apart.
private val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August",
    "September", "October", "November", "December")

private val shortMonths = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/** One budget month (salary to salary): its name is the calendar month most of it falls in. */
private data class MonthData(val offset: Int, val range: Range, val byCat: Map<Long?, Double>) {
    val total: Double get() = byCat.values.sum()
    private val mid: Int get() = Calendar.getInstance().apply { timeInMillis = (range.from + range.to) / 2 }.get(Calendar.MONTH)
    val name: String get() = monthNames[mid]
    val short: String get() = shortMonths[mid]
}

/** Two months side by side: the totals, six months of bars, and every line that moved. */
@Composable
fun CompareScreen(db: Db, tick: Int) {
    val p = Ui.p
    val months = remember(tick) {
        (11 downTo 0).map { i ->
            val r = Budget.cycle(db, -i).range
            MonthData(-i, r, db.spentByCategory(r.from, r.to))
        }
    }
    val cats = remember(tick) { db.categories() }
    // Months that have any spending; the comparison needs two.
    val usable = months.filter { it.total > 0 || it.offset == 0 }
    var aOff by rememberSaveable { mutableIntStateOf(-1) }
    var bOff by rememberSaveable { mutableIntStateOf(0) }
    val a = months.firstOrNull { it.offset == aOff } ?: months[months.size - 2]
    val b = months.firstOrNull { it.offset == bOff } ?: months.last()
    val older = if (a.offset <= b.offset) a else b
    val newer = if (a.offset <= b.offset) b else a
    val olderColor = p.dinar
    val newerColor = p.accent

    if (usable.size < 2) {
        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(84.dp).sticker(p.pastel(p.dinar), 28.dp, 4.dp), contentAlignment = Alignment.Center) {
                Icon(AppIcons.BarChart, null, tint = p.deep(p.dinar), modifier = Modifier.size(40.dp))
            }
            Text(tr("لسا ما في شهرين نقارنهم", "Not two months to compare yet"), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 14.dp), textAlign = TextAlign.Center)
            Text(tr("بعد ما يخلص أول شهر كامل مع التطبيق بتقدر تقارنه بالشهر اللي بعده.",
                "Once your first full month with the app is done, you can compare it with the next."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MonthPicker(older, olderColor, months, Modifier.weight(1f)) { if (older == a) aOff = it else bOff = it }
                Text(tr("مقابل", "vs"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MonthPicker(newer, newerColor, months, Modifier.weight(1f)) { if (newer == b) bOff = it else aOff = it }
            }
        }
        // The summary: which month cost less, and by how much.
        item {
            val diff = newer.total - older.total
            val less = diff < 0
            val tone = if (less) p.mint else p.pomegranate
            StickerCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fill = p.pastel(tone), lift = 5.dp, radius = 26.dp) {
                Text(
                    if (abs(diff) < 0.5) tr("صرفت تقريباً نفس المبلغ", "About the same spending")
                    else if (less) tr("بشهر ${newer.name} صرفت أقل بـ", "In ${newer.name} you spent less by")
                    else tr("بشهر ${newer.name} صرفت أكثر بـ", "In ${newer.name} you spent more by"),
                    style = MaterialTheme.typography.titleSmall, color = p.deep(tone),
                )
                if (abs(diff) >= 0.5) Amount(abs(diff), 38.sp, weight = FontWeight.ExtraBold)
                if (newer.offset == 0) Text(tr("الشهر الحالي لسا ما خلص", "The current month isn't over yet"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(older, newer).forEach { m ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(p.raised)
                                .border(2.dp, p.edge, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(m.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(n2(m.total), fontFamily = Ui.numbers, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
        // Six months of bars; tap one to put it in the comparison.
        item {
            val six = months.takeLast(6)
            val top = max(1.0, six.maxOf { it.total })
            val avg = six.filter { it.total > 0 }.map { it.total }.average().takeIf { !it.isNaN() } ?: 0.0
            StickerCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), lift = 0.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("آخر 6 أشهر", "Last 6 months"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(tr("المعدل ${n2(avg)}", "Average ${n2(avg)}"), fontFamily = Ui.numbers,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    Modifier.fillMaxWidth().height(170.dp).padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom,
                ) {
                    six.forEach { m ->
                        val on = m == older || m == newer
                        val fill = when (m) { older -> olderColor; newer -> newerColor; else -> p.line }
                        Column(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button, onClickLabel = tr("قارن ${m.name}", "Compare ${m.name}")) {
                                    if (m.offset < newer.offset) { if (older == a) aOff = m.offset else bOff = m.offset }
                                    else { if (newer == b) bOff = m.offset else aOff = m.offset }
                                }
                                .semantics(mergeDescendants = true) { contentDescription = "${m.name}: ${n2(m.total)}" },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom,
                        ) {
                            Text(String.format(Locale.US, "%,.0f", m.total), fontFamily = Ui.numbers, fontSize = 11.sp,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier.fillMaxWidth().fillMaxHeight((m.total / top).toFloat().coerceIn(0.03f, 1f) * 0.82f)
                                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                    .background(fill)
                                    .then(if (on) Modifier.border(2.dp, p.edge, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp)) else Modifier)
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    six.forEach { m ->
                        val on = m == older || m == newer
                        Text(m.short, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                            color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(tr("اضغط على أي شهر عشان تقارنه.", "Tap a month to compare it."), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
        // Line by line: the ones that moved most first; the steady ones in one line at the end.
        item {
            val keys = (older.byCat.keys + newer.byCat.keys).distinct()
            val rows = keys.map { k -> Triple(k, older.byCat[k] ?: 0.0, newer.byCat[k] ?: 0.0) }
            val moved = rows.filter { abs(it.third - it.second) >= 1.0 }.sortedByDescending { abs(it.third - it.second) }
            val steady = rows.filter { abs(it.third - it.second) < 1.0 && (it.second > 0 || it.third > 0) }
            val top = max(1.0, rows.maxOfOrNull { max(it.second, it.third) } ?: 1.0)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                StickerHeader(tr("بند ببند", "Line by line"), Modifier.padding(bottom = 6.dp))
                StickerList {
                    moved.forEachIndexed { i, (k, o, n) ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = p.line)
                        val c = cats.firstOrNull { it.id == k }
                        val d = n - o
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LineIcon(c?.icon ?: "❔", c?.let { Color(it.color) } ?: p.pomegranate, 36.dp)
                                Text(c?.name ?: tr("بدون بند", "No line"), Modifier.weight(1f).padding(horizontal = 10.dp),
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                Tag(if (d < 0) tr("أقل ${n2(-d)}", "${n2(-d)} less") else tr("أكثر ${n2(d)}", "${n2(d)} more"),
                                    if (d < 0) p.mint else p.pomegranate)
                            }
                            CompareBar(o, top, olderColor, older.short)
                            CompareBar(n, top, newerColor, newer.short)
                        }
                    }
                    if (steady.isNotEmpty()) {
                        if (moved.isNotEmpty()) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = p.line)
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(steady.joinToString(tr("، ", ", ")) { (k, _, _) -> cats.firstOrNull { it.id == k }?.name ?: tr("بدون بند", "No line") },
                                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(tr("تقريباً نفسها", "About the same"), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // One plain sentence: the biggest saving and the biggest rise.
                val save = moved.filter { it.third < it.second }.maxByOrNull { it.second - it.third }
                val rise = moved.filter { it.third > it.second }.maxByOrNull { it.third - it.second }
                fun nameOf(k: Long?) = cats.firstOrNull { it.id == k }?.name ?: tr("بدون بند", "No line")
                val tip = listOfNotNull(
                    save?.let { tr("أكبر توفير كان بـ«${nameOf(it.first)}».", "Your biggest saving was on “${nameOf(it.first)}”.") },
                    rise?.let { tr("وأكثر بند زاد «${nameOf(it.first)}».", "The line that grew most was “${nameOf(it.first)}”.") },
                ).joinToString(" ")
                if (tip.isNotBlank()) Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp).sticker(p.pastel(p.sun), 22.dp, 0.dp).padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(AppIcons.Lightbulb, null, tint = p.deep(p.sun), modifier = Modifier.size(22.dp))
                    Text(tip, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun CompareBar(v: Double, top: Double, color: Color, label: String) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(44.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1)
        Box(Modifier.weight(1f).height(10.dp)) {
            Box(Modifier.fillMaxWidth((v / top).toFloat().coerceIn(0.01f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        }
        Text(n2(v), Modifier.width(72.dp).padding(start = 8.dp), fontFamily = Ui.numbers, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun MonthPicker(m: MonthData, color: Color, months: List<MonthData>, modifier: Modifier, onPick: (Int) -> Unit) {
    val p = Ui.p
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(18.dp)).background(p.pastel(color))
                .border(2.dp, p.edge, RoundedCornerShape(18.dp)).clickable(role = Role.Button) { open = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(color))
            Text(m.name, Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(AppIcons.ExpandMore, tr("اختر شهراً", "Pick a month"), modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            months.reversed().forEach { o ->
                DropdownMenuItem(
                    text = { Text("${o.name}  ·  ${n2(o.total)}") },
                    onClick = { open = false; onPick(o.offset) },
                )
            }
        }
    }
}
