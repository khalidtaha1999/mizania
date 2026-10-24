@file:OptIn(ExperimentalLayoutApi::class)

package com.mizania.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.max

fun directionLabel(owedToMe: Boolean) = if (owedToMe) tr("لي", "Owed to me") else tr("عليّ", "I owe")

fun entryLabel(d: Debt, kind: Int) = when {
    kind == Db.PAY && d.owedToMe -> tr("استرددت", "Paid back to me")
    kind == Db.PAY -> tr("سدّدت", "I paid")
    d.owedToMe -> tr("أقرضته المزيد", "Lent more")
    else -> tr("اقترضت المزيد", "Borrowed more")
}

private fun parseAmount(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

@Composable
fun DebtsScreen(db: Db, tick: Int, refresh: () -> Unit) {
    val debts = remember(tick) { db.debts() }
    var open by remember { mutableStateOf<Long?>(null) }
    val iOwe = debts.filter { !it.owedToMe }.sumOf { max(0.0, it.remaining) }
    val owedMe = debts.filter { it.owedToMe }.sumOf { max(0.0, it.remaining) }
    val active = debts.filter { !it.closed }
    val done = debts.filter { it.closed }

    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)) {
        item {
            Section(tr("الملخص", "Summary")) {
                KV(tr("عليّ للآخرين", "I owe"), fmt(iOwe), color = MaterialTheme.colorScheme.error, bold = true)
                KV(tr("لي عند الآخرين", "Owed to me"), fmt(owedMe), color = MaterialTheme.colorScheme.secondary, bold = true)
                KV(tr("الصافي", "Net"), fmt(owedMe - iOwe))
                // Planned installments this month, and whether the debt line's budget covers them.
                val cycle = remember(tick) { Budget.cycle(db).range }
                val planned = active.filter { !it.owedToMe }.mapNotNull { d -> db.debtPlan(d.id)?.let { d to minOf(it, d.remaining) } }
                if (planned.isNotEmpty()) {
                    val due = planned.sumOf { it.second }
                    val paid = planned.sumOf { (d, _) -> db.debtPaidIn(d.id, cycle.from, cycle.to) }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    KV(tr("أقساط هذا الشهر", "This month's installments"), tr("دفعت ${fmt(paid)} من ${fmt(due)}", "${fmt(paid)} of ${fmt(due)} paid"),
                        bold = true, color = if (paid + 0.001 >= due) MaterialTheme.colorScheme.secondary else androidx.compose.ui.graphics.Color.Unspecified)
                    val lineBudget = remember(tick) { db.categories().filter { db.isDebtLine(it) }.sumOf { it.budget } }
                    if (lineBudget + 0.001 < due) {
                        Text(tr("ميزانية بند الأقساط (${fmt(lineBudget)}) أقل من مجموع أقساطك (${fmt(due)}). ارفعها من الإعدادات ← البنود حتى تُحجز من الراتب.",
                            "Your debt line's budget (${fmt(lineBudget)}) is less than your installments (${fmt(due)}). Raise it in Settings → Budget lines so it's set aside from your salary."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (debts.isEmpty()) item {
            Text(tr("لا توجد ديون مسجّلة. اضغط ➕ لإضافة دين لك أو عليك.", "No debts recorded. Tap ➕ to add money you owe or are owed."), Modifier.padding(24.dp))
        }
        items(active, key = { it.id }) { d -> DebtCard(d, db.debtPlan(d.id)) { open = d.id } }
        if (done.isNotEmpty()) item {
            Text(tr("المُسدَّدة", "Settled"), Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        items(done, key = { it.id }) { d -> DebtCard(d) { open = d.id } }
    }

    open?.let { id -> DebtDialog(db, id) { open = null; refresh() } }
}

@Composable
fun DebtCard(d: Debt, plan: Double? = null, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.person, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (d.closed) tr("سُدِّد ✓", "Settled ✓") else directionLabel(d.owedToMe),
                    fontWeight = FontWeight.Bold,
                    color = when {
                        d.closed -> MaterialTheme.colorScheme.onSurfaceVariant
                        d.owedToMe -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    },
                )
            }
            KV(tr("المبلغ الكلي", "Total"), fmt(d.total))
            KV(if (d.owedToMe) tr("استرددت", "Paid back") else tr("سدّدت", "Paid"), fmt(d.paid))
            KV(tr("المتبقي", "Remaining"), fmt(max(0.0, d.remaining)), bold = true)
            if (d.total > 0) {
                LinearProgressIndicator(
                    progress = { (d.paid / d.total).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp),
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (plan != null && !d.closed) {
                val months = kotlin.math.ceil(d.remaining / plan).toInt()
                Text(
                    tr("قسط ${fmt(plan)} شهرياً: تنتهي بعد ${monthsText(months)} (${finishMonth(months)})",
                        "${fmt(plan)} a month: done in ${monthsText(months)} (${finishMonth(months)})"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (d.note.isNotBlank()) {
                Text(d.note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** New debt, or edit one ([debt] not null). */
@Composable
fun DebtFormDialog(db: Db, debt: Debt?, onDone: () -> Unit) {
    var person by remember { mutableStateOf(debt?.person ?: "") }
    var owedToMe by remember { mutableStateOf(debt?.owedToMe ?: false) }
    var amount by remember {
        mutableStateOf(debt?.amount?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
    }
    var note by remember { mutableStateOf(debt?.note ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val value = parseAmount(amount)
    var plan by remember { mutableStateOf(debt?.let { db.debtPlan(it.id) }?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    val planValue = parseAmount(plan)

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (debt == null) tr("دين جديد", "New debt") else tr("تعديل الدين", "Edit debt")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = person, onValueChange = { person = it }, label = { Text(tr("اسم الشخص", "Person")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(tr("الدين", "Debt"), fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(!owedToMe, { owedToMe = false }, { Text(tr("عليّ (أنا المقترض)", "I owe (I borrowed)")) })
                    FilterChip(owedToMe, { owedToMe = true }, { Text(tr("لي (أنا المُقرض)", "Owed to me (I lent)")) })
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, label = { Text(tr("المبلغ الأصلي", "Original amount")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(tr("ملاحظة (اختياري)", "Note (optional)")) },
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = plan, onValueChange = { plan = it },
                    label = { Text(if (owedToMe) tr("القسط الشهري المتوقع (اختياري)", "Expected monthly installment (optional)")
                        else tr("كم تسدّد كل شهر؟ (اختياري)", "How much do you pay each month? (optional)")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                val left = (value ?: 0.0) + (debt?.added ?: 0.0) - (debt?.paid ?: 0.0)
                if (planValue != null && planValue > 0 && left > 0) {
                    val months = kotlin.math.ceil(left / planValue).toInt()
                    Text(tr("تنتهي بعد ${monthsText(months)}، في ${finishMonth(months)}", "Done in ${monthsText(months)}, in ${finishMonth(months)}"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp))
                }
                if (confirmDelete) {
                    Text(tr("هل أنت متأكد؟ سيُحذف الدين وكل دفعاته.", "Are you sure? The debt and all its payments will be deleted."), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = person.isNotBlank() && value != null && value > 0,
                onClick = {
                    val id = db.saveDebt(debt?.id ?: 0, person.trim(), owedToMe, value ?: 0.0, note.trim())
                    db.setDebtPlan(id, planValue)
                    onDone()
                },
            ) { Text(tr("حفظ", "Save")) }
        },
        dismissButton = {
            Row {
                if (debt != null) {
                    TextButton(onClick = {
                        if (confirmDelete) { db.deleteDebt(debt.id); onDone() } else confirmDelete = true
                    }) { Text(if (confirmDelete) tr("نعم، احذف", "Yes, delete") else tr("حذف", "Delete"), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDone) { Text(tr("إلغاء", "Cancel")) }
            }
        },
    )
}

/** A debt's history, plus adding a repayment or an increase. */
@Composable
fun DebtDialog(db: Db, id: Long, onDone: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var local by remember { mutableIntStateOf(0) }
    val d = remember(local) { db.debt(id) }
    if (d == null) {
        LaunchedEffect(id) { onDone() }
        return
    }
    val entries = remember(local) { db.debtEntries(id) }
    var editing by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("") }
    var kind by remember { mutableIntStateOf(Db.PAY) }
    var note by remember { mutableStateOf("") }
    var fixing by remember { mutableStateOf(false) }
    var fixValue by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val value = parseAmount(amount)

    // Saves whatever is typed but not yet saved (new payment, corrected total), so "تم" never drops it.
    fun savePending(): Boolean {
        var saved = false
        if (fixing) parseAmount(fixValue)?.let { db.setDebtPaid(d.id, it); fixing = false; saved = true }
        val v = parseAmount(amount)
        if (v != null && v > 0) {
            db.addDebtEntry(d.id, kind, v, System.currentTimeMillis(), note.trim(), null)
            android.widget.Toast.makeText(ctx, tr("سُجّلت: ", "Recorded: ") + "${entryLabel(d, kind)} ${fmt(v)}", android.widget.Toast.LENGTH_SHORT).show()
            amount = ""; note = ""; saved = true
        }
        return saved
    }

    if (editing) {
        DebtFormDialog(db, d) { editing = false; local++ }
        return
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("${d.person} — ${directionLabel(d.owedToMe)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                KV(tr("المبلغ الأصلي", "Original amount"), fmt(d.amount))
                if (d.added > 0) KV(tr("زيادات", "Added"), fmt(d.added))
                KV(if (d.owedToMe) tr("استرددت", "Paid back") else tr("سدّدت", "Paid"), fmt(d.paid))
                KV(tr("المتبقي", "Remaining"), fmt(max(0.0, d.remaining)), bold = true,
                    color = if (d.closed) MaterialTheme.colorScheme.secondary else Color0)
                if (fixing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = fixValue, onValueChange = { fixValue = it }, singleLine = true,
                            label = { Text(if (d.owedToMe) tr("ما استرددته حتى الآن", "Paid back so far") else tr("ما سدّدته حتى الآن", "Paid so far")) }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        TextButton(enabled = parseAmount(fixValue) != null, onClick = {
                            db.setDebtPaid(d.id, parseAmount(fixValue) ?: 0.0); fixing = false; local++
                        }) { Text(tr("حفظ", "Save")) }
                    }
                    Text(tr("يُسجَّل كـ«تعديل»، لا كدفعة جديدة.", "Recorded as a “correction”, not a new payment."), style = MaterialTheme.typography.bodySmall)
                } else {
                    TextButton(onClick = {
                        fixing = true
                        fixValue = if (d.paid % 1.0 == 0.0) d.paid.toLong().toString() else d.paid.toString()
                    }) { Text(if (d.owedToMe) tr("تعديل المبلغ المسترد", "Fix amount paid back") else tr("تعديل المبلغ المسدَّد", "Fix amount paid")) }
                }
                if (d.note.isNotBlank()) Text(d.note, style = MaterialTheme.typography.bodySmall)
                ReceiptButtons(tr("كشف الدين (PDF)", "Debt statement (PDF)"), "debt-${d.id}.pdf") { Receipt.forDebt(it, d.id) }

                Spacer(Modifier.height(12.dp))
                Text(tr("دفعة جديدة", "New payment"), fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(kind == Db.PAY, { kind = Db.PAY }, { Text(entryLabel(d, Db.PAY)) })
                    FilterChip(kind == Db.ADD, { kind = Db.ADD }, { Text(entryLabel(d, Db.ADD)) })
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it }, label = { Text(tr("المبلغ", "Amount")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(tr("ملاحظة (اختياري)", "Note (optional)")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    enabled = value != null && value > 0,
                    onClick = { savePending(); local++ },
                    modifier = Modifier.padding(top = 6.dp),
                ) { Text(tr("تسجيل الدفعة", "Record payment")) }

                if (entries.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(tr("السجل", "History"), fontWeight = FontWeight.SemiBold)
                    entries.forEach { e ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (e.note == Db.CORRECTION) tr("تعديل ", "Correction ") + (if (e.amount >= 0) "+" else "−") + fmt(kotlin.math.abs(e.amount))
                                    else "${entryLabel(d, e.kind)} ${fmt(e.amount)}"
                                )
                                Text(
                                    Period.stamp(e.time) + (if (e.txnId != null) tr(" · من البنك", " · from bank") else "") +
                                        (if (e.note.isNotBlank() && e.note != Db.CORRECTION) " · ${e.note}" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton(onClick = { db.deleteDebtEntry(e.id); local++ }) {
                                Icon(Icons.Filled.Delete, tr("حذف", "Delete"))
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            val pending = (value != null && value > 0) || (fixing && parseAmount(fixValue) != null)
            TextButton(onClick = { savePending(); onDone() }) { Text(if (pending) tr("حفظ وإغلاق", "Save and close") else tr("تم", "Done")) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    if (confirmDelete) { db.deleteDebt(d.id); onDone() } else confirmDelete = true
                }) { Text(if (confirmDelete) tr("نعم، احذف", "Yes, delete") else tr("حذف الدين", "Delete debt"), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { editing = true }) { Text(tr("تعديل", "Edit")) }
            }
        },
    )
}

private val Color0 = androidx.compose.ui.graphics.Color.Unspecified


/** The month a plan ends, counting this month as the first. */
private fun finishMonth(months: Int): String {
    val c = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, maxOf(0, months - 1)) }
    val s = java.text.SimpleDateFormat("MMMM yyyy", if (Lang.ar) java.util.Locale("ar") else java.util.Locale.ENGLISH).format(c.time)
    return s.map { ch -> if (ch in '\u0660'..'\u0669') '0' + (ch - '\u0660') else ch }.joinToString("")
}
