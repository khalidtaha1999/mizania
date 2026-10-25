@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.mizania.app

import androidx.compose.material3.IconButton

import androidx.compose.material3.HorizontalDivider

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun CategoryChips(cats: List<Category>, selected: Long?, onSelect: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cats.forEach { c ->
            FilterChip(
                selected = selected == c.id, onClick = { onSelect(c.id) },
                label = { Text("${c.icon}  ${c.name}") },
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(c.color).copy(alpha = 0.22f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

/** Shared frame for the bottom sheets: drag handle, padding, scrolling, and an action row at the end. */
@Composable
fun SheetFrame(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            content = content,
        )
    }
}

@Composable
fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

/** Classify a transaction: pick the budget, add a note, optionally remember the merchant. */
@Composable
fun TxnDialog(db: Db, id: Long, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val t = remember(id) { db.txn(id) }
    if (t == null) {
        LaunchedEffect(id) { onDone() }
        return
    }
    val cats = remember { db.categories() }
    var cat by remember(id) { mutableStateOf(t.categoryId) }
    var note by remember(id) { mutableStateOf(t.note) }
    var learn by remember(id) { mutableStateOf(t.categoryId == null && t.merchant.isNotBlank()) }
    var branches by remember(id) { mutableStateOf(false) }
    var showSms by remember(id) { mutableStateOf(false) }
    var splitting by remember(id) { mutableStateOf(false) }
    var splitTick by remember(id) { mutableIntStateOf(0) }
    val linkedDebt = remember(id) { db.debtForTxn(id) }
    var debtSel by remember(id) { mutableStateOf(linkedDebt) }
    val debts = remember(id) { db.debts().filter { !it.closed || it.id == linkedDebt } }
    val accountName = remember(id) { db.accounts().firstOrNull { it.id == t.accountId }?.label }
    val selected = cats.firstOrNull { it.id == cat }
    val goals = remember { db.goals() }
    var goalSel by remember(id) { mutableStateOf(db.goalForTxn(id) ?: goals.singleOrNull()?.id) }
    val jamiyas = remember { db.jamiyas() }
    var jamSel by remember(id) { mutableStateOf(db.jamiyaForTxn(id) ?: jamiyas.singleOrNull()?.id) }
    // Picking the debt line preselects your only open debt.
    LaunchedEffect(cat) {
        if (!t.income && db.isDebtLine(selected) && debtSel == null) debtSel = db.openDebtsIOwe().singleOrNull()?.id
    }

    if (splitting) {
        SplitDialog(db, t, cats, cat) { mainCat ->
            splitting = false
            // Saved: the parts are their own rows now, so close and let the lists show them.
            if (mainCat != null) onDone()
        }
    }
    SheetFrame(onDone) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(if (t.income) "💵" else selected?.icon ?: "❔",
                if (t.income) Ui.p.mint else selected?.let { Color(it.color) } ?: Ui.p.pomegranate, 48.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(t.kindText + tr("، ", ", ") + Period.stamp(t.time), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (accountName != null) Text(accountName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Amount(t.amount, 24.sp, if (t.income) Ui.p.mint else MaterialTheme.colorScheme.onSurface,
                sign = if (t.income) "+" else "")
        }
        // What it cost in work, from the salary.
        if (!t.income) remember(id) { Plan.hourly(db) }?.let { h ->
            Text("≈ " + Plan.hoursText(t.amount / h), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        t.balance?.let {
            Text(tr("الرصيد بعدها ${fmt(it)}", "Balance after: ${fmt(it)}"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }

        if (!t.income) {
            SheetLabel(tr("إلى أي بند تتبع؟", "Which budget line?"))
            CategoryChips(cats, cat) { cat = if (cat == it) null else it }
            // One payment for several things (100 for the household, 33 for furniture): split it.
            if (t.parentId == null) {
                val parts = remember(id, splitTick) { db.splitParts(id) }
                if (parts.isNotEmpty()) {
                    Text(tr("مقسّمة: ", "Split: ") + parts.joinToString("، ") { p -> "${cats.firstOrNull { it.id == p.categoryId }?.name ?: "؟"} ${fmt(p.amount)}" } +
                        tr(" — والباقي ${fmt(t.amount)} على البند المختار هنا", " — the rest, ${fmt(t.amount)}, on the line chosen here"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp))
                }
                TextButton(onClick = { splitting = true }) {
                    Text(if (parts.isEmpty()) tr("✂️ تقسيم المبلغ على أكثر من بند", "✂️ Split over several lines")
                        else tr("✂️ تعديل التقسيم", "✂️ Edit the split"))
                }
            } else {
                Text(tr("هذا جزء من حركة مقسّمة.", "This is a part of a split transaction."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
                TextButton(onClick = { db.unsplit(t.parentId); Notifier.cancel(ctx, t.id); onDone() }) {
                    Text(tr("دمج الأجزاء في حركة واحدة", "Merge the parts back"))
                }
            }
            if (db.isSavings(selected)) {
                val lineGoals = remember(cat) { db.goalsFor(selected) }
                LaunchedEffect(cat) { if (lineGoals.none { it.id == goalSel }) goalSel = lineGoals.singleOrNull()?.id }
                if (lineGoals.isEmpty()) {
                    Text(tr("لا توجد لديك أهداف ادخار بعد. أنشئ هدفاً من «أموالي ← الادخار» ليُحتسب فيه.",
                        "You have no savings goals yet. Create one in “My money → Savings” and it will count toward it."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp))
                } else {
                    SheetLabel(tr("لأي هدف ادخار؟", "Which savings goal?"))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        lineGoals.forEach { g ->
                            FilterChip(selected = goalSel == g.id, onClick = { goalSel = g.id },
                                label = { Text("${g.icon}  ${g.name}") }, shape = RoundedCornerShape(50))
                        }
                    }
                }
            }
            if (t.merchant.isNotBlank()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).clickable { learn = !learn },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = learn, onCheckedChange = { learn = it })
                    Text(tr("صنّف كل حركات «${t.merchant}» في البند نفسه تلقائياً", "Always put “${t.merchant}” in this line"), style = MaterialTheme.typography.bodyMedium)
                }
                val brand = Db.brandKey(t.merchant)
                if (learn && brand != Db.ruleKey(t.merchant)) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp).clip(RoundedCornerShape(12.dp)).clickable { branches = !branches },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = branches, onCheckedChange = { branches = it })
                        Text(tr("وكل فروع «$brand» (كل اسم يبدأ به)", "and every “$brand” branch (any name starting with it)"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        val debtLine = !t.income && db.isDebtLine(selected)
        val shownDebts = if (debtLine) debts.filter { !it.owedToMe } else debts
        if (debtLine && shownDebts.isEmpty()) {
            Text(tr("لا يوجد دين مسجّل عليك. أضفه من «أموالي ← الديون» لتُحتسب الدفعة سداداً منه.",
                "You have no debts recorded. Add one in “My money → Debts” and the payment will count as a repayment."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
        if (shownDebts.isNotEmpty()) {
            SheetLabel(when {
                debtLine -> tr("لأي دين؟", "Which debt?")
                t.income -> tr("هل هي مرتبطة بدين؟", "Is it for a debt?")
                else -> tr("أو سجّلها على دين", "Or record it on a debt")
            })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shownDebts.forEach { d ->
                    FilterChip(
                        selected = debtSel == d.id,
                        onClick = { debtSel = if (debtSel == d.id) null else d.id },
                        label = { Text("${d.person} (${directionLabel(d.owedToMe)})") },
                    )
                }
            }
            debts.firstOrNull { it.id == debtSel }?.let { d ->
                val kind = if (t.income == d.owedToMe) Db.PAY else Db.ADD
                Text(tr("ستُسجَّل «${entryLabel(d, kind)}» على دين ${d.person}", "Will be recorded as “${entryLabel(d, kind)}” on ${d.person}'s debt"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (!t.income && db.isJamiyaLine(selected)) {
            val lineJams = remember(cat) { db.jamiyasFor(selected) }
            LaunchedEffect(cat) { if (lineJams.none { it.id == jamSel }) jamSel = lineJams.singleOrNull()?.id }
            if (lineJams.isEmpty()) {
                Text(tr("لا توجد جمعية مسجّلة. أضفها من «أموالي ← الجمعية» ليُسجَّل الشهر مدفوعاً تلقائياً.",
                    "No jamiya recorded. Add one in “My money → Jamiya” and the month will be marked paid by itself."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
            } else {
                SheetLabel(tr("لأي جمعية؟", "Which jamiya?"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    lineJams.forEach { j ->
                        FilterChip(selected = jamSel == j.id, onClick = { jamSel = j.id },
                            label = { Text("🤝  ${j.name}") }, shape = RoundedCornerShape(50))
                    }
                }
                lineJams.firstOrNull { it.id == jamSel }?.let { j ->
                    val i = JamiyaMonths.indexAt(j, t.time)
                    Text(
                        if (i in 0 until j.months) tr("سيُسجَّل شهر ${JamiyaMonths.label(j, i)} مدفوعاً", "${JamiyaMonths.label(j, i)} will be marked paid")
                        else tr("تاريخ الحركة خارج أشهر هذه الجمعية", "This date is outside this jamiya's months"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        if ((t.kind.contains("كليك") || t.kind.contains("حوالة")) && t.merchant.isNotBlank() && !db.isOwnName(t.merchant)) {
            TextButton(
                onClick = {
                    db.addOwnName(t.merchant)
                    Notifier.cancel(ctx, t.id)
                    onDone()
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(tr("«${t.merchant}» هو أنا، هذا تحويل بين حساباتي", "“${t.merchant}” is me — this is a transfer between my accounts")) }
        }
        SheetLabel(tr("ملاحظة", "Note"))
        OutlinedTextField(
            value = note, onValueChange = { note = it }, placeholder = { Text(tr("مثلاً: غداء مع الأصدقاء", "e.g. lunch with friends")) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        )
        if (t.body.isNotBlank()) {
            TextButton(onClick = { showSms = !showSms }, modifier = Modifier.padding(top = 4.dp)) {
                Text(if (showSms) tr("إخفاء الرسالة", "Hide SMS") else tr("عرض الرسالة الأصلية", "Show the original SMS"))
            }
            if (showSms) {
                Text(t.body, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            enabled = t.income || cat != null || debtSel != null,
            onClick = {
                val c = if (t.income) null else cat
                db.updateTxn(t.id, c, note.trim())
                if (debtSel != linkedDebt) db.linkTxnToDebt(t, debtSel)
                Notifier.cancel(ctx, t.id)
                if (c != null) {
                    if (learn) {
                        db.addRule(if (branches) Db.brandKey(t.merchant) else t.merchant, c)
                        db.applyRulesToPending()
                    }
                    if (c != t.categoryId) Processor.afterCategorized(ctx, t.id, c, notifyRecorded = false)
                }
                // In a goal when booked under savings, out of it otherwise.
                db.syncLinks(t.id, goalSel, debtSel, jamSel)
                onDone()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text(tr("حفظ", "Save"), style = MaterialTheme.typography.titleMedium) }
        ReceiptButtons(tr("إيصال الحركة (PDF)", "Transaction receipt (PDF)"), "receipt-${t.id}.pdf") { Receipt.forTxn(it, t.id) }
        TextButton(
            onClick = { db.ignoreTxn(t.id); Notifier.cancel(ctx, t.id); onDone() },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) { Text(tr("تجاهل الحركة (ليست مصروفاً)", "Ignore (not spending)"), color = MaterialTheme.colorScheme.error) }
    }
}

/** A cash spend that never reaches the bank. */
@Composable
fun AddDialog(db: Db, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val cats = remember { db.categories() }
    var amount by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    val value = amount.replace(',', '.').toDoubleOrNull()

    SheetFrame(onDone) {
        Text(tr("مصروف نقدي", "Cash expense"), style = MaterialTheme.typography.titleLarge)
        Text(tr("لما دفعته نقداً ولم تصلك عنه رسالة.", "For what you paid in cash with no SMS."), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = amount, onValueChange = { amount = it },
            placeholder = { Text("0.00", fontFamily = Ui.numbers, fontSize = 28.sp) },
            textStyle = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp),
            suffix = { Text(currency()) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        )
        SheetLabel(tr("البند", "Budget line"))
        CategoryChips(cats, cat) { cat = it }
        SheetLabel(tr("ملاحظة", "Note"))
        OutlinedTextField(
            value = note, onValueChange = { note = it }, placeholder = { Text(tr("مثلاً: فلافل", "e.g. falafel")) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), singleLine = true,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            enabled = value != null && value > 0 && cat != null,
            onClick = {
                val c = cat ?: return@Button
                val v = value ?: return@Button
                val id = db.insertTxn(
                    time = System.currentTimeMillis(), amount = v, income = false,
                    merchant = note.trim().ifEmpty { "كاش" }, kind = "كاش", categoryId = c,
                    note = note.trim(), balance = null, sender = "يدوي", body = "",
                    hash = null, manual = true, accountId = null,
                )
                if (id > 0) Processor.afterCategorized(ctx, id, c, notifyRecorded = false)
                onDone()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text(tr("إضافة المصروف", "Add expense"), style = MaterialTheme.typography.titleMedium) }
    }
}

val CategoryIcons = listOf(
    "🏠", "🤝", "💝", "💡", "💳", "🛒", "🚗", "☕", "🚑", "💰", "🍔", "👕",
    "💊", "🎓", "📱", "🎁", "👶", "✈️", "🏋️", "🐾", "🧾", "🔧", "🎮", "📦",
)
val CategoryColors = listOf(
    0xFF5B6CFF, 0xFF8E59D9, 0xFFE0559B, 0xFFF2A516, 0xFFE5533D, 0xFF2EA36A,
    0xFF1E88E5, 0xFF8D6E63, 0xFFD84343, 0xFF26A69A, 0xFF78909C, 0xFF00ACC1,
)

/** Add or edit a budget line. [cat] null = new one. */
@Composable
fun CategoryDialog(db: Db, cat: Category?, onDone: () -> Unit) {
    var name by remember { mutableStateOf(cat?.name ?: "") }
    var budget by remember { mutableStateOf(cat?.budget?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var type by remember { mutableStateOf(cat?.type ?: Db.DAILY) }
    var icon by remember { mutableStateOf(cat?.icon ?: "📦") }
    var color by remember { mutableStateOf(cat?.color ?: 0xFF78909C) }
    var dueDay by remember { mutableStateOf(cat?.dueDay?.takeIf { it > 0 }?.toString() ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (cat == null) tr("بند جديد", "New budget line") else tr("تعديل البند", "Edit budget line")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr("الاسم", "Name")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = budget, onValueChange = { budget = it }, label = { Text(tr("الميزانية الشهرية", "Monthly budget")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(8.dp))
                Text(tr("النوع", "Type"), fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(type == Db.FIXED, { type = Db.FIXED }, { Text(tr("ثابت", "Fixed")) })
                    FilterChip(type == Db.DAILY, { type = Db.DAILY }, { Text(tr("يومي", "Daily")) })
                    FilterChip(type == Db.RESERVE, { type = Db.RESERVE }, { Text(tr("احتياط/ادخار", "Reserve/savings")) })
                }
                Text(tr("بنود «يومي» هي التي يُحسب منها المتاح للصرف كل يوم.", "“Daily” lines are what your daily allowance is worked out from."), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text(tr("الأيقونة", "Icon"), fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CategoryIcons.forEach { e ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (icon == e) Color(color).copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { icon = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 20.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(tr("اللون", "Colour"), fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryColors.forEach { c ->
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                                .border(if (color == c) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dueDay, onValueChange = { dueDay = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text(tr("يوم الدفع في الشهر (اختياري، للتذكير)", "Payment day of the month (optional, for reminders)")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (confirmDelete) {
                    Text(tr("هل أنت متأكد؟ ستعود حركات هذا البند إلى «بحاجة إلى تصنيف».", "Are you sure? This line's transactions will go back to “to sort”."),
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (budget.isBlank() || budget.replace(',', '.').toDoubleOrNull() != null),
                onClick = {
                    db.saveCategory(Category(
                        cat?.id ?: 0, name.trim(), budget.replace(',', '.').toDoubleOrNull() ?: 0.0, type,
                        icon = icon, color = color, dueDay = (dueDay.toIntOrNull() ?: 0).coerceIn(0, 28),
                    ))
                    onDone()
                },
            ) { Text(tr("حفظ", "Save")) }
        },
        dismissButton = {
            Row {
                if (cat != null) {
                    TextButton(onClick = {
                        if (confirmDelete) { db.deleteCategory(cat.id); onDone() } else confirmDelete = true
                    }) { Text(if (confirmDelete) tr("نعم، احذف", "Yes, delete") else tr("حذف", "Delete"), color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDone) { Text(tr("إلغاء", "Cancel")) }
            }
        },
    )
}

/** Tick which inbox senders belong to [account]. A sender belongs to one account at a time. */
@Composable
fun SenderPickerDialog(db: Db, account: Account, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val list = remember { Processor.inboxSenders(ctx) }
    val accNames = remember { db.accounts().associate { it.id to it.name } }
    var owners by remember { mutableStateOf(db.senderAccounts()) }
    var manual by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(tr("مرسلو ${account.label}", "Senders for ${account.label}")) },
        text = {
            Column {
                if (list.isEmpty()) {
                    Text(tr("تعذّرت قراءة الرسائل. تأكد من منح التطبيق صلاحية قراءة الرسائل.", "Couldn't read your SMS. Make sure the app has permission to read messages."))
                } else {
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(list, key = { it.address }) { s ->
                            val key = Db.norm(s.address)
                            val owned = owners.containsKey(key)
                            val owner = owners[key]
                            val on = owned && owner == account.id
                            val toggle = {
                                if (on) db.removeSender(s.address) else db.addSender(s.address, account.id)
                                owners = db.senderAccounts()
                            }
                            Row(
                                Modifier.fillMaxWidth().clickable { toggle() }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = on, onCheckedChange = { toggle() })
                                Column(Modifier.weight(1f)) {
                                    Text("${s.address}  (${s.count})", fontWeight = FontWeight.SemiBold)
                                    if (owned && !on) {
                                        Text(tr("تابع لـ ${accNames[owner] ?: "حساب آخر"} — اختياره ينقله إلى هنا",
                                            "Belongs to ${accNames[owner] ?: "another account"} — picking it moves it here"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error)
                                    }
                                    Text(s.sample, style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manual, onValueChange = { manual = it },
                        label = { Text(tr("أو اكتب اسم المرسل", "Or type the sender name")) }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = manual.isNotBlank(), onClick = {
                        db.addSender(manual, account.id); manual = ""; owners = db.senderAccounts()
                    }) { Text(tr("إضافة", "Add")) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(tr("تم", "Done")) } },
    )
}

/** Add or edit an account (a bank, a card, a wallet). */
@Composable
fun AccountDialog(db: Db, account: Account?, defaultName: String, canDelete: Boolean, onDone: () -> Unit) {
    var name by remember { mutableStateOf(account?.name ?: defaultName) }
    var bank by remember { mutableStateOf(account?.bank ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    // 0 = not asked, 1 = choosing what to delete, 2 = confirming "everything".
    var deleteStep by remember { mutableIntStateOf(0) }
    val txnCount = remember(account?.id) { account?.let { db.accountTxnCount(it.id) } ?: 0 }
    val ctx = LocalContext.current
    val banks = listOf(
        tr("البنك العربي", "Arab Bank"), tr("بنك الاتحاد", "Bank al Etihad"), tr("بنك الإسكان", "Housing Bank"),
        tr("بنك القاهرة عمان", "Cairo Amman Bank"), tr("كابيتال بنك", "Capital Bank"), tr("محفظة إلكترونية", "E-wallet"),
    )

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (account == null) tr("حساب جديد", "New account") else tr("تعديل الحساب", "Edit account")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(tr("اسم الحساب", "Account name")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = bank, onValueChange = { bank = it }, label = { Text(tr("البنك", "Bank")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    banks.forEach { b -> FilterChip(bank == b, { bank = b }, { Text(b) }) }
                }
                Text(tr("بعد الحفظ، اختر مرسلي رسائل هذا البنك من «اختيار المرسلين».", "After saving, pick this bank's SMS senders with “Pick senders”."),
                    style = MaterialTheme.typography.bodySmall)
                if (account != null && deleteStep == 1) {
                    Text(tr("ماذا تريد أن تحذف؟", "What do you want to delete?"), fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp))
                    OutlinedButton(onClick = { db.deleteAccount(account.id); AllowanceWidget.refreshAll(ctx); onDone() },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text(tr("الحساب فقط، وتبقى حركاته", "Only the account, keep its transactions"))
                    }
                    Button(onClick = { deleteStep = 2 }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text(tr("الحساب وكل ما يخصه ($txnCount حركة)", "The account and everything in it ($txnCount transactions)"))
                    }
                }
                if (account != null && deleteStep == 2) {
                    Text(tr("سيُحذف «${account.name}» مع $txnCount حركة ورصيده وكل ما سُجّل منها على الديون والادخار والجمعية. لا يمكن التراجع، إلا من نسخة احتياطية.",
                        "“${account.name}” will be deleted with $txnCount transactions, its balance and whatever they booked into debts, savings and the jamiya. This can't be undone, except from a backup."),
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                    Button(onClick = {
                        db.deleteAccountWithData(account.id); AllowanceWidget.refreshAll(ctx)
                        android.widget.Toast.makeText(ctx, tr("حُذف الحساب وكل ما يخصه", "Account and its data deleted"), android.widget.Toast.LENGTH_SHORT).show()
                        onDone()
                    }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text(tr("نعم، احذف كل شيء", "Yes, delete everything"))
                    }
                    TextButton(onClick = { deleteStep = 0 }, modifier = Modifier.fillMaxWidth()) { Text(tr("تراجع", "Go back")) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                db.saveAccount(Account(account?.id ?: 0, name.trim(), bank.trim()))
                onDone()
            }) { Text(tr("حفظ", "Save")) }
        },
        dismissButton = {
            Row {
                if (account != null && canDelete) {
                    TextButton(onClick = { deleteStep = if (deleteStep == 0) 1 else 0 }) {
                        Text(tr("حذف الحساب", "Delete account"), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDone) { Text(tr("إلغاء", "Cancel")) }
            }
        },
    )
}


/**
 * Splits one spend over several budget lines. Each part gets an amount and a line; what is left stays on
 * the transaction under [mainCat]. [onDone] gets the main line after saving, or null when cancelled.
 */
@Composable
fun SplitDialog(db: Db, t: Txn, cats: List<Category>, mainCat: Long?, onDone: (Long?) -> Unit) {
    val ctx = LocalContext.current
    val old = remember { db.splitParts(t.id) }
    val total = t.amount + old.sumOf { it.amount }
    var main by remember { mutableStateOf(mainCat) }
    val parts = remember {
        androidx.compose.runtime.mutableStateListOf<Pair<Long?, String>>().apply {
            if (old.isEmpty()) add(null to "") else old.forEach { add(it.categoryId to subPlainAmount(it.amount)) }
        }
    }
    var pickFor by remember { mutableStateOf(-1) } // -1 none, -2 main line, else part index
    val sum = parts.sumOf { subAmountOf(it.second) ?: 0.0 }
    val rest = total - sum
    val valid = main != null && parts.all { it.first != null && (subAmountOf(it.second) ?: 0.0) > 0 } && rest > 0.0005
    fun name(id: Long?) = cats.firstOrNull { it.id == id }?.let { "${it.icon} ${it.name}" } ?: tr("اختر البند", "Pick a line")

    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(tr("تقسيم ${fmt(total)}", "Split ${fmt(total)}")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(tr("اكتب كل جزء وبنده. الباقي يبقى على البند الأساسي.", "Enter each part and its line. The rest stays on the main line."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                parts.forEachIndexed { i, (c, a) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                        OutlinedTextField(
                            value = a, onValueChange = { v -> parts[i] = c to v }, singleLine = true,
                            label = { Text(tr("المبلغ", "Amount")) }, modifier = Modifier.width(110.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        OutlinedButton(onClick = { pickFor = i }, modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(name(c), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { parts.removeAt(i) }) { Text("✕") }
                    }
                }
                TextButton(onClick = { parts.add(null to "") }) { Text(tr("+ جزء آخر", "+ Another part")) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("الباقي", "The rest"), Modifier.width(60.dp))
                    Text(fmt(rest), fontFamily = Ui.numbers, fontWeight = FontWeight.SemiBold,
                        color = if (rest <= 0.0005) MaterialTheme.colorScheme.error else Color.Unspecified,
                        modifier = Modifier.width(110.dp))
                    OutlinedButton(onClick = { pickFor = -2 }, modifier = Modifier.weight(1f)) {
                        Text(name(main), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (rest <= 0.0005) {
                    Text(tr("مجموع الأجزاء يجب أن يكون أقل من ${fmt(total)}.", "The parts must add up to less than ${fmt(total)}."),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                db.splitTxn(t.id, main, parts.map { (c, a) -> (c ?: 0L) to (subAmountOf(a) ?: 0.0) })
                Notifier.cancel(ctx, t.id)
                AllowanceWidget.refreshAll(ctx)
                onDone(main)
            }) { Text(tr("حفظ", "Save")) }
        },
        dismissButton = {
            Row {
                if (old.isNotEmpty()) TextButton(onClick = { db.unsplit(t.id); onDone(main) }) {
                    Text(tr("إلغاء التقسيم", "Remove split"), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { onDone(null) }) { Text(tr("رجوع", "Back")) }
            }
        },
    )

    if (pickFor != -1) {
        AlertDialog(
            onDismissRequest = { pickFor = -1 },
            title = { Text(tr("إلى أي بند؟", "Which line?")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    cats.forEach { c ->
                        Text("${c.icon}  ${c.name}", Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                            if (pickFor == -2) main = c.id else parts[pickFor] = c.id to parts[pickFor].second
                            pickFor = -1
                        }.padding(vertical = 12.dp, horizontal = 8.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickFor = -1 }) { Text(tr("إلغاء", "Cancel")) } },
        )
    }
}

private fun subAmountOf(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

private fun subPlainAmount(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else "%.3f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')
