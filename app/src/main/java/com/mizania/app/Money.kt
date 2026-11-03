@file:OptIn(ExperimentalLayoutApi::class)

package com.mizania.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.max

private fun amountOf(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

private fun plain(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

/** Debts, savings goals and the jamiya under one tab. */
@Composable
fun MoneyScreen(db: Db, tick: Int, refresh: () -> Unit) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Segmented(listOf(tr("الادخار", "Savings"), tr("الديون", "Debts"), tr("الجمعية", "Jamiya"), tr("أدوات", "Tools")), sub) { sub = it }
            Box(Modifier.weight(1f)) {
                when (sub) {
                    0 -> GoalsScreen(db, tick, refresh)
                    1 -> DebtsScreen(db, tick, refresh)
                    2 -> JamiyaScreen(db, tick, refresh)
                    else -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)
                    ) { ToolsContent(db, tick) }
                }
            }
        }
        if (sub != 3) FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = Ui.p.pine, contentColor = Ui.p.onPine,
        ) { Icon(Icons.Filled.Add, tr("إضافة", "Add")) }
    }
    if (adding) {
        val done = { adding = false; refresh() }
        when (sub) {
            0 -> GoalFormDialog(db, null, onDone = done)
            1 -> DebtFormDialog(db, null, onDone = done)
            else -> JamiyaFormDialog(db, null, onDone = done)
        }
    }
}

// ---------------- savings goals ----------------

private fun monthsUntil(deadline: Long): Int =
    max(1, ceil((deadline - System.currentTimeMillis()).toDouble() / (30.44 * Period.DAY)).toInt())

@Composable
fun GoalsScreen(db: Db, tick: Int, refresh: () -> Unit) {
    var local by remember { mutableIntStateOf(0) }
    val goals = remember(tick, local) { db.goals() }
    val unlinked = remember(tick, local) { db.unlinkedSavings() }
    var open by remember { mutableStateOf<Goal?>(null) }
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)) {
        if (unlinked.isNotEmpty()) item {
            Section(tr("مدخرات غير مرتبطة بهدف", "Savings not in a goal")) {
                Text(tr("صنّفت ${txnsText(unlinked.size)} على الادخار بمجموع ${fmt(unlinked.sumOf { it.amount })}. ",
                    "You put ${txnsText(unlinked.size)} under savings, totalling ${fmt(unlinked.sumOf { it.amount })}. ") +
                    if (goals.isEmpty()) tr("أنشئ هدفاً لتضيفها إليه.", "Create a goal to put them in.") else tr("أضفها إلى هدف:", "Put them in a goal:"),
                    style = MaterialTheme.typography.bodyMedium)
                if (goals.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        goals.forEach { g ->
                            OutlinedButton(onClick = {
                                unlinked.forEach { t -> db.linkSaving(t.id, g.id) }
                                local++
                            }) { Text("${g.icon} ${g.name}") }
                        }
                    }
                }
            }
        }
        if (goals.isEmpty()) item {
            Text(tr("حدّد هدف ادخار (مثلاً «عيد الأضحى 500 د.أ») وتابع ما جمعته. اضغط ➕.",
                "Set a savings goal (e.g. “Eid 500 JOD”) and track how much you've saved. Tap ➕."), Modifier.padding(24.dp))
        } else item {
            Section(tr("المجموع", "Total")) {
                KV(tr("جمعت", "Saved"), fmt(goals.sumOf { it.saved }), bold = true, color = MaterialTheme.colorScheme.secondary)
                KV(tr("من أصل", "Out of"), fmt(goals.sumOf { it.target }))
            }
        }
        items(goals, key = { it.id }) { g ->
            val p = if (g.target > 0) (g.saved / g.target).toFloat() else 0f
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable { open = g },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(g.icon, MaterialTheme.colorScheme.secondary, 44.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(g.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(tr("${fmt(g.saved)} من ${fmt(g.target)}", "${fmt(g.saved)} of ${fmt(g.target)}"), style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${(p * 100).toInt()}%", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.secondary)
                    }
                    LinearProgressIndicator(
                        progress = { p.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.secondary,
                        drawStopIndicator = {},
                    )
                    val left = g.target - g.saved
                    if (left <= 0) {
                        Text(tr("وصلت إلى الهدف 🎉", "Goal reached 🎉"), Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.secondary)
                    } else if (g.deadline != null) {
                        val m = monthsUntil(g.deadline)
                        Text(tr("متبقٍ ${monthsText(m)} — تحتاج إلى ادخار ${fmt(left / m)} شهرياً", "${monthsText(m)} left — put aside ${fmt(left / m)} a month"),
                            Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall)
                    }
                    GoalPaceLine(db, g, tick + local)
                }
            }
        }
    }
    open?.let { g -> GoalDialog(db, g) { open = null; refresh() } }
}

@Composable
fun GoalDialog(db: Db, goal: Goal, onDone: () -> Unit) {
    var local by remember { mutableIntStateOf(0) }
    val g = remember(local) { db.goals().firstOrNull { it.id == goal.id } } ?: goal
    val entries = remember(local) { db.goalEntries(goal.id) }
    var amount by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var fixing by remember { mutableStateOf(false) }
    var fixValue by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val v = amountOf(amount)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Typed but not saved yet: "تم" saves it (an amount counts as حطيت).
    val lineCat = remember(local) { db.goalLineOrDefault(g.id)?.let { db.category(it) } }
    var onLine by remember { mutableStateOf(true) }
    fun savePending() {
        if (fixing) amountOf(fixValue)?.let { db.setGoalSaved(g.id, it); fixing = false }
        val x = amountOf(amount)
        if (x != null && x > 0) {
            if (onLine && lineCat != null) db.addToGoalOnLine(g.id, x, lineCat.id) else db.addToGoal(g.id, x)
            android.widget.Toast.makeText(ctx, tr("أُضيف ${fmt(x)} إلى ${g.name}", "Added ${fmt(x)} to ${g.name}"), android.widget.Toast.LENGTH_SHORT).show()
            amount = ""
        }
    }
    if (editing) {
        GoalFormDialog(db, g, onDone = onDone)
        return
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("${g.icon} ${g.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                KV(tr("جمعت", "Saved"), fmt(g.saved), bold = true)
                KV(tr("الهدف", "Goal"), fmt(g.target))
                KV(tr("المتبقي", "Left"), fmt(max(0.0, g.target - g.saved)))
                if (fixing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = fixValue, onValueChange = { fixValue = it }, singleLine = true,
                            label = { Text(tr("المبلغ الصحيح", "Correct amount")) }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        TextButton(enabled = amountOf(fixValue) != null, onClick = {
                            db.setGoalSaved(g.id, amountOf(fixValue) ?: 0.0); fixing = false; local++
                        }) { Text(tr("حفظ", "Save")) }
                    }
                    Text(tr("يصبح «جمعت» هذا الرقم، ولا يُحتسب إضافةً ولا سحباً.", "“Saved” becomes this number; it doesn't count as money in or out."), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    TextButton(onClick = { fixing = true; fixValue = plain(g.saved) }) { Text(tr("تعديل المبلغ المجمّع", "Fix the saved amount")) }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, label = { Text(tr("المبلغ", "Amount")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (lineCat != null) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onLine = !onLine },
                        verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = onLine, onCheckedChange = { onLine = it })
                        Text(tr("احسب ما أضيفه على بند ${lineCat.icon} ${lineCat.name}", "Book what I put in on ${lineCat.icon} ${lineCat.name}"),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedButton(enabled = v != null && v > 0, onClick = {
                        if (onLine && lineCat != null) db.addToGoalOnLine(g.id, v ?: 0.0, lineCat.id) else db.addToGoal(g.id, v ?: 0.0)
                        amount = ""; local++
                    }) {
                        Text(tr("أضفت", "Put in"))
                    }
                    OutlinedButton(enabled = v != null && v > 0, onClick = { db.addToGoal(g.id, -(v ?: 0.0)); amount = ""; local++ }) {
                        Text(tr("سحبت", "Took out"))
                    }
                }

                if (entries.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(tr("السجل", "History"), fontWeight = FontWeight.SemiBold)
                    entries.forEach { e ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when {
                                        e.note == Db.CORRECTION -> tr("تعديل", "Correction")
                                        e.txnId != null && e.merchant == g.name -> tr("أضفت ✓ على البند", "Put in ✓ booked")
                                        e.txnId != null -> tr("من حركة: ", "From transaction: ") + merchantLabel(e.merchant).ifEmpty { tr("بنك", "bank") }
                                        e.amount >= 0 -> tr("أضفت", "Put in")
                                        else -> tr("سحبت", "Took out")
                                    } + "  " + (if (e.amount >= 0) "+" else "−") + fmt(kotlin.math.abs(e.amount))
                                )
                                Text(Period.stamp(e.time), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                // Put in by hand earlier: book it on the line now.
                                if (lineCat != null && e.txnId == null && e.amount > 0 && e.note != Db.CORRECTION) {
                                    Text(tr("احسبها على ${lineCat.name}", "Book it on ${lineCat.name}"),
                                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.clickable { db.bookGoalEntry(e.id, lineCat.id); local++ }.padding(vertical = 4.dp))
                                }
                            }
                            IconButton(onClick = { db.deleteGoalEntry(e.id); local++ }) {
                                Icon(Icons.Filled.Delete, tr("حذف", "Delete"))
                            }
                        }
                        HorizontalDivider()
                    }
                }
                if (confirmDelete) {
                    Text(tr("هل أنت متأكد؟ سيُحذف الهدف وكل سجله.", "Are you sure? The goal and its history will be deleted."), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            val pending = (v != null && v > 0) || (fixing && amountOf(fixValue) != null)
            TextButton(onClick = { savePending(); onDone() }) { Text(if (pending) tr("حفظ وإغلاق", "Save and close") else tr("تم", "Done")) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    if (confirmDelete) { db.deleteGoal(g.id); onDone() } else confirmDelete = true
                }) { Text(if (confirmDelete) tr("نعم، احذف", "Yes, delete") else tr("حذف الهدف", "Delete goal"), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { editing = true }) { Text(tr("تعديل", "Edit")) }
            }
        },
    )
}

private val goalIcons = listOf("🎯", "🐑", "🌙", "✈️", "🚗", "🏠", "💍", "🎓", "📱", "👶", "🕋", "🛋️")

@Composable
fun GoalFormDialog(db: Db, goal: Goal?, onDone: () -> Unit) {
    var name by remember { mutableStateOf(goal?.name ?: "") }
    var target by remember { mutableStateOf(goal?.target?.let(::plain) ?: "") }
    var icon by remember { mutableStateOf(goal?.icon ?: "🎯") }
    var months by remember { mutableIntStateOf(goal?.deadline?.let { monthsUntil(it) } ?: 0) }
    // An occasion picked from the list sets its own date instead of a number of months.
    var exact by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val t = amountOf(target)
    var line by remember { mutableStateOf(goal?.let { db.goalLine(it.id) }) }
    val lines = remember { db.categories().filter { it.type != Db.DAILY || it.id == line } }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (goal == null) tr("هدف جديد", "New goal") else tr("تعديل الهدف", "Edit goal")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr("الهدف", "Goal")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = target, onValueChange = { target = it }, label = { Text(tr("المبلغ المطلوب", "Target amount")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(8.dp))
                if (goal == null) {
                    Text(tr("صندوق مناسبة", "Save for an occasion"), fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Occasions.upcoming().forEach { o ->
                            FilterChip(exact == o.date, {
                                name = o.name; icon = o.icon; exact = o.date; months = -1
                            }, { Text("${o.icon} ${o.name}") })
                        }
                    }
                }
                Text(tr("خلال", "Within"), fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to tr("بدون موعد", "No deadline"), 3 to tr("3 أشهر", "3 months"), 6 to tr("6 أشهر", "6 months"),
                        12 to tr("سنة", "1 year"), 24 to tr("سنتان", "2 years")).forEach { (m, l) ->
                        FilterChip(months == m && exact == null, { months = m; exact = null }, { Text(l) })
                    }
                }
                exact?.let { e ->
                    val mm = monthsUntil(e)
                    Text(tr("الموعد تقريباً ${Period.dayMonthYear(e)}، بعد ${monthsText(mm)}", "Around ${Period.dayMonthYear(e)}, in ${monthsText(mm)}") +
                        (t?.let { tr(" — ادّخر ${fmt(it / mm)} شهرياً", " — put aside ${fmt(it / mm)} a month") } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                LinePicker(
                    tr("من أي بند تُموَّل؟", "Which budget line feeds it?"),
                    tr("كل حركة على هذا البند ستسألك هل هي لهذا الهدف، وتُضاف إليه.", "Every transaction on this line will ask whether it is for this goal, and add to it."),
                    tr("تلقائي (بند الادخار)", "Automatic (the savings line)"), lines, line,
                ) { line = it }
                Text(tr("الأيقونة", "Icon"), fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    goalIcons.forEach { e ->
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (icon == e) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .clickable { icon = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 20.sp) }
                    }
                }
                if (confirmDelete) Text(tr("هل أنت متأكد؟", "Are you sure?"), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && t != null && t > 0, onClick = {
                val deadline = exact ?: if (months > 0) Calendar.getInstance().apply { add(Calendar.MONTH, months) }.timeInMillis else null
                val id = db.saveGoal(goal?.id ?: 0, name.trim(), t ?: 0.0, deadline, icon)
                db.setGoalLine(id, line)
                onDone()
            }) { Text(tr("حفظ", "Save")) }
        },
        dismissButton = {
            Row {
                if (goal != null) TextButton(onClick = {
                    if (confirmDelete) { db.deleteGoal(goal.id); onDone() } else confirmDelete = true
                }) { Text(if (confirmDelete) tr("نعم، احذف", "Yes, delete") else tr("حذف", "Delete"), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDone) { Text(tr("إلغاء", "Cancel")) }
            }
        },
    )
}

// ---------------- jamiya ----------------

object JamiyaMonths {
    private fun ym(s: String): Int? {
        val p = s.trim().split("-")
        if (p.size != 2) return null
        val y = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        return if (m in 1..12) y * 12 + (m - 1) else null
    }

    fun thisMonth(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    fun valid(s: String) = ym(s) != null

    /** 0-based index of the current month in the circle (can be <0 or >= months). */
    fun currentIndex(j: Jamiya): Int = indexAt(j, System.currentTimeMillis())

    /** 0-based index of the month containing [time] in the circle. */
    fun indexAt(j: Jamiya, time: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = time }
        val m = c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
        return m - (ym(j.startMonth) ?: m)
    }

    fun label(j: Jamiya, index: Int): String {
        val v = (ym(j.startMonth) ?: 0) + index
        return "${v % 12 + 1}/${(v / 12) % 100}"
    }
}

@Composable
fun JamiyaScreen(db: Db, tick: Int, refresh: () -> Unit) {
    var local by remember { mutableIntStateOf(0) }
    val list = remember(tick, local) { db.jamiyas() }
    var editing by remember { mutableStateOf<Jamiya?>(null) }
    var deleting by remember { mutableStateOf<Long?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)) {
        if (list.isEmpty()) item {
            Text(tr("سجّل جمعيتك: المبلغ الشهري وعدد الأشهر ودورك، ثم علّم كل شهر تدفعه. اضغط ➕.",
                "Record your jamiya: the monthly amount, the number of months and your turn. Then tick each month you pay. Tap ➕."),
                Modifier.padding(24.dp))
        }
        items(list, key = { it.id }) { j ->
            val cur = JamiyaMonths.currentIndex(j)
            val paid = j.paidMonths.count { it in 0 until j.months }
            val turnIdx = j.myTurn - 1
            Section("🤝 ${j.name}") {
                KV(tr("القسط الشهري", "Monthly payment"), fmt(j.monthly))
                KV(tr("دفعت", "Paid"), tr("$paid من ${monthsText(j.months)}", "$paid of ${monthsText(j.months)}") + " (${fmt(paid * j.monthly)})")
                KV(tr("ستقبض", "You'll receive"), fmt(j.monthly * j.months), bold = true, color = MaterialTheme.colorScheme.secondary)
                val turnText = when {
                    j.received -> tr("قبضت ✓", "Received ✓")
                    turnIdx == cur -> tr("هذا الشهر! 🎉", "This month! 🎉")
                    turnIdx > cur -> tr("بعد ${monthsText(turnIdx - cur)}", "In ${monthsText(turnIdx - cur)}") + " (${JamiyaMonths.label(j, turnIdx)})"
                    else -> tr("كان في ${JamiyaMonths.label(j, turnIdx)} — علّمه إذا قبضت", "Was ${JamiyaMonths.label(j, turnIdx)} — tick it if you received it")
                }
                KV(tr("دورك", "Your turn"), turnText, bold = true)
                Spacer(Modifier.height(8.dp))
                Text(tr("اضغط على الشهر لتعليمه مدفوعاً:", "Tap a month to mark it paid:"), style = MaterialTheme.typography.bodySmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    for (i in 0 until j.months) {
                        val isPaid = i in j.paidMonths
                        val bg = when {
                            isPaid -> MaterialTheme.colorScheme.secondaryContainer
                            i < cur -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        Box(
                            Modifier.size(width = 54.dp, height = 46.dp).clip(RoundedCornerShape(12.dp)).background(bg)
                                .border(
                                    if (i == turnIdx) 2.dp else if (i == cur) 1.dp else 0.dp,
                                    if (i == turnIdx) Color(0xFF8E59D9) else MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { db.setJamiyaPaid(j.id, i, !isPaid); local++ },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(JamiyaMonths.label(j, i), fontSize = 11.sp)
                                Text(if (isPaid) "✓" else if (i == turnIdx) "🎉" else "", fontSize = 13.sp)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = j.received, onCheckedChange = { db.setJamiyaReceived(j.id, it); local++ })
                    Text(tr("قبضت الجمعية", "I received the jamiya"), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { editing = j }) { Text(tr("تعديل", "Edit")) }
                    TextButton(onClick = {
                        if (deleting == j.id) { db.deleteJamiya(j.id); deleting = null; local++ } else deleting = j.id
                    }) {
                        Text(if (deleting == j.id) tr("نعم، احذف", "Yes, delete") else tr("حذف", "Delete"), color = MaterialTheme.colorScheme.error)
                    }
                }
                ReceiptButtons(tr("كشف الجمعية (PDF)", "Jamiya statement (PDF)"), "jamiya-${j.id}.pdf") { Receipt.forJamiya(it, j.id) }
            }
        }
    }
    editing?.let { j -> JamiyaFormDialog(db, j) { editing = null; refresh() } }
}

@Composable
fun JamiyaFormDialog(db: Db, j: Jamiya?, onDone: () -> Unit) {
    var name by remember { mutableStateOf(j?.name ?: tr("جمعية", "Jamiya")) }
    var monthly by remember { mutableStateOf(j?.monthly?.let(::plain) ?: "") }
    var months by remember { mutableStateOf(j?.months?.toString() ?: "10") }
    var start by remember { mutableStateOf(j?.startMonth ?: JamiyaMonths.thisMonth()) }
    var turn by remember { mutableStateOf(j?.myTurn?.toString() ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var line by remember { mutableStateOf(j?.let { db.jamiyaLine(it.id) }) }
    val lines = remember { db.categories().filter { it.type != Db.DAILY || it.id == line } }
    val m = amountOf(monthly)
    val n = months.toIntOrNull()
    val tr = turn.toIntOrNull()
    val ok = name.isNotBlank() && m != null && m > 0 && n != null && n in 1..60 &&
        tr != null && tr in 1..n && JamiyaMonths.valid(start)
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (j == null) tr("جمعية جديدة", "New jamiya") else tr("تعديل الجمعية", "Edit jamiya")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr("الاسم", "Name")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = monthly, onValueChange = { monthly = it }, label = { Text(tr("القسط الشهري", "Monthly payment")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = months, onValueChange = { months = it.filter(Char::isDigit) },
                    label = { Text(tr("عدد الأشهر (= عدد المشتركين)", "Number of months (= members)")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text(tr("الشهر الأول (سنة-شهر مثل 2026-10)", "First month (year-month, e.g. 2026-10)")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = turn, onValueChange = { turn = it.filter(Char::isDigit) },
                    label = { Text(tr("دورك (رقم الشهر الذي تقبض فيه)", "Your turn (the month number you receive in)")) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                LinePicker(
                    tr("من أي بند تُدفع؟", "Which budget line pays it?"),
                    tr("الدفعة على هذا البند تُعلِّم شهر الجمعية مدفوعاً.", "A payment on this line marks the jamiya month as paid."),
                    tr("تلقائي (بند «جمعية»)", "Automatic (the “jamiya” line)"), lines, line,
                ) { line = it }
                if (confirmDelete) Text(tr("هل أنت متأكد؟", "Are you sure?"), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                val id = db.saveJamiya(Jamiya(j?.id ?: 0, name.trim(), m ?: 0.0, n ?: 1, start.trim(), tr ?: 1,
                    j?.paidMonths ?: emptySet(), j?.received ?: false))
                db.setJamiyaLine(id, line)
                onDone()
            }) { Text(tr("حفظ", "Save")) }
        },
        dismissButton = {
            Row {
                if (j != null) TextButton(onClick = {
                    if (confirmDelete) { db.deleteJamiya(j.id); onDone() } else confirmDelete = true
                }) { Text(if (confirmDelete) tr("نعم، احذف", "Yes, delete") else tr("حذف", "Delete"), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDone) { Text(tr("إلغاء", "Cancel")) }
            }
        },
    )
}


/** Pick the budget line a goal or jamiya is tied to; [none] is the automatic choice. */
@Composable
private fun LinePicker(title: String, hint: String, none: String, lines: List<Category>, selected: Long?, onPick: (Long?) -> Unit) {
    Spacer(Modifier.height(8.dp))
    Text(title, fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FilterChip(selected == null, { onPick(null) }, { Text(none) })
        lines.forEach { c -> FilterChip(selected == c.id, { onPick(c.id) }, { Text("${c.icon} ${c.name}") }) }
    }
    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
