package com.mizania.app

import android.widget.Toast
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Switch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(db: Db, tick: Int, refresh: () -> Unit) {
    val ctx = LocalContext.current
    val senders = remember(tick) { db.senders() }
    val cats = remember(tick) { db.categories() }
    val rules = remember(tick) { db.rules() }
    val ownNames = remember(tick) { db.ownNames() }
    var editingRule by remember { mutableStateOf<Rule?>(null) }
    var addingRule by remember { mutableStateOf(false) }
    val catNames = cats.associate { it.id to it.name }

    val accounts = remember(tick) { db.accounts() }
    val senderMap = remember(tick) { db.senderAccounts() }
    var pickFor by remember { mutableStateOf<Account?>(null) }
    var editAcc by remember { mutableStateOf<Account?>(null) }
    var addingAcc by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var addingCat by remember { mutableStateOf(false) }
    var salary by remember { mutableStateOf(db.setting("salary", "0")) }
    var cycleDay by remember { mutableStateOf(db.cycleDay().toString()) }
    var importFrom by remember { mutableStateOf(Period.day(-62).from) }
    var importTo by remember { mutableStateOf(Period.day().from) }
    var pickingRange by remember { mutableStateOf(false) }
    var bySalary by remember { mutableStateOf(db.setting("cycle_mode", "salary") == "salary") }
    var noSalary by remember { mutableStateOf(db.noSalary()) }
    var confirmReset by remember { mutableIntStateOf(0) } // 0 none, 1 transactions, 2 everything
    var dailySummary by remember { mutableStateOf(db.setting("daily_summary", "1") == "1") }
    var lockOn by remember { mutableStateOf(db.setting("lock", "0") == "1") }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var langChoice by remember { mutableStateOf(Lang.choice(ctx)) }
    var themeChoice by remember { mutableStateOf(Look.theme(ctx)) }
    var bigText by remember { mutableStateOf(Look.bigText(ctx)) }
    var showHelp by remember { mutableStateOf(false) }
    var ssec by remember { mutableIntStateOf(0) }
    val ignored = remember(tick) { db.ignoredTxns() }
    var showIgnored by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try { Backup.write(ctx, uri, Backup.exportJson(db)); toast(tr("حُفظت النسخة الاحتياطية", "Backup saved")) }
            catch (e: Exception) { toast(tr("تعذّر الحفظ", "Couldn't save")) }
        }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            try { Backup.write(ctx, uri, Backup.exportCsv(db)); toast(tr("حُفظ ملف Excel (CSV)", "Excel file (CSV) saved")) }
            catch (e: Exception) { toast(tr("تعذّر الحفظ", "Couldn't save")) }
        }
    }
    val openJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        // Settings in four parts: the bank, the budget lines, general, and your data.
        item {
            Segmented(listOf(tr("البنك", "Bank"), tr("البنود", "Lines"), tr("عام", "General"), tr("البيانات", "Data")), ssec) { ssec = it }
        }
        if (ssec == 2) item {
            Section(tr("اللغة", "Language")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Lang.SYSTEM to tr("لغة الهاتف", "Phone language"),
                        Lang.AR to "العربية",
                        Lang.EN to "English",
                    ).forEach { (code, name) ->
                        FilterChip(langChoice == code, {
                            if (langChoice != code) {
                                langChoice = code
                                Lang.set(ctx, code)
                                Notifier.createChannels(ctx)
                                AllowanceWidget.refreshAll(ctx)
                                (ctx as? android.app.Activity)?.recreate()
                            }
                        }, { Text(name) })
                    }
                }
            }
        }
        if (ssec == 2) item {
            Section(tr("المظهر", "Look")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "dark" to tr("🌙 داكن", "🌙 Dark"),
                        "light" to tr("☀️ فاتح", "☀️ Light"),
                        "" to tr("مثل الهاتف", "Like the phone"),
                    ).forEach { (code, name) ->
                        FilterChip(themeChoice == code, {
                            if (themeChoice != code) {
                                themeChoice = code
                                db.setSetting("theme", code)
                                (ctx as? android.app.Activity)?.recreate()
                            }
                        }, { Text(name) })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("خط أكبر", "Bigger text"))
                        Text(tr("يكبّر كل النصوص في التطبيق", "Makes all text in the app larger"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = bigText, onCheckedChange = {
                        bigText = it
                        db.setSetting("big_text", if (it) "1" else "0")
                        (ctx as? android.app.Activity)?.recreate()
                    })
                }
            }
        }
        if (ssec == 0) item {
            Section(tr("الحسابات", "Accounts")) {
                Text(tr("يقرأ كل حساب رسائل المرسلين الذين تختارهم له، ويتجاهل باقي الرسائل.",
                    "Each account reads SMS from the senders you pick for it and ignores the rest."),
                    style = MaterialTheme.typography.bodySmall)
                accounts.forEach { a ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a.label, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            TextButton(onClick = { editAcc = a }) { Text(tr("تعديل", "Edit")) }
                        }
                        val mine = senderMap.filterValues { it == a.id }.keys
                        if (mine.isEmpty()) Text(tr("لا يوجد مرسلون بعد", "No senders yet"), style = MaterialTheme.typography.bodySmall)
                        mine.forEach { s ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("• $s", Modifier.weight(1f))
                                IconButton(onClick = { db.removeSender(s); refresh() }) {
                                    Icon(Icons.Filled.Delete, tr("حذف", "Delete"))
                                }
                            }
                        }
                        OutlinedButton(onClick = { pickFor = a }) { Text(tr("اختيار المرسلين", "Pick senders")) }
                    }
                    HorizontalDivider()
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { addingAcc = true }) { Text(tr("إضافة حساب", "Add account")) }
            }
        }
        if (ssec == 0) item {
            Section(tr("استيراد الرسائل القديمة", "Import old SMS")) {
                Text(tr("اختر الفترة. يضيف التطبيق حركات المرسلين المختارين خلالها، دون تكرار ما أُضيف من قبل.",
                    "Pick a date range. Transactions from your chosen senders in that range are added, without duplicating ones already there."),
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                // Quick ranges, all ending today; 0 = every SMS in the inbox.
                val today = Period.day().from
                val dayOfMonth = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)
                val presets = listOf(
                    tr("آخر 30 يوماً", "Last 30 days") to Period.day(-29).from,
                    tr("هذا الشهر", "This month") to Period.day(-(dayOfMonth - 1)).from,
                    tr("من البداية", "From the beginning") to 0L,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { (label, from) ->
                        FilterChip(importFrom == from && importTo == today, {
                            importFrom = from; importTo = today
                        }, { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickingRange = true }) {
                        val fromText = if (importFrom == 0L) tr("من البداية", "From the beginning") else Period.dayMonthYear(importFrom)
                        Text("$fromText  ${if (Lang.ar) "←" else "→"}  ${Period.dayMonthYear(importTo)}", fontFamily = Ui.numbers)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Button(enabled = senders.isNotEmpty(), onClick = {
                    val n = Processor.importInbox(ctx, importFrom, importTo + Period.DAY)
                    Toast.makeText(ctx, if (n > 0) tr("أُضيفت ${txnsText(n)}", "Added ${txnsText(n)}") else tr("لا توجد حركات جديدة في هذه الفترة", "No new transactions in this range"), Toast.LENGTH_SHORT).show()
                    refresh()
                }) { Text(tr("استيراد", "Import")) }
            }
        }
        if (ssec == 1) item {
            Section(tr("البنود والميزانية", "Budget lines")) {
                val total = cats.sumOf { it.budget }
                cats.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = c }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.name, Modifier.weight(1f))
                        Text(
                            (when (c.type) { Db.FIXED -> tr("ثابت", "Fixed"); Db.DAILY -> tr("يومي", "Daily"); else -> tr("احتياط", "Reserve") }) + " · " + fmt(c.budget),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text(
                    if (noSalary) tr("مجموع الميزانية ${fmt(total)}", "Budget total ${fmt(total)}")
                    else tr("مجموع الميزانية ${fmt(total)}، والراتب المتوقع ${fmt(db.salary())}",
                        "Budget total ${fmt(total)}, expected salary ${fmt(db.salary())}") +
                        if (db.salary() - total != 0.0) tr(" (الفرق ${fmt(db.salary() - total)})", " (difference ${fmt(db.salary() - total)})") else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!noSalary && total > db.salary()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { addingCat = true }) { Text(tr("إضافة بند", "Add line")) }
            }
        }
        if (ssec == 0 && (ownNames.isNotEmpty())) item {
            Section(tr("أسماؤك في التحويلات", "Your names in transfers")) {
                Text(tr("التحويلات من هذه الأسماء وإليها تُحتسب بين حساباتك، لا صرفاً ولا دخلاً.",
                    "Transfers from and to these names count as moves between your own accounts, not spending or income."),
                    style = MaterialTheme.typography.bodySmall)
                ownNames.forEach { n ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(n, Modifier.weight(1f))
                        IconButton(onClick = { db.removeOwnName(n); refresh() }) { Icon(Icons.Filled.Delete, tr("حذف", "Delete")) }
                    }
                }
            }
        }
        if (ssec == 0 && (ignored.isNotEmpty())) item {
            Section(tr("حركات مستبعدة", "Left-out transactions")) {
                Text(tr("الحركات التي اخترت أنها «ليست مصروفاً». لا تُحتسب في أي مجموع، ويمكنك إرجاع أي منها.",
                    "Transactions you marked \"not spending\". They count in no total, and you can bring any of them back."),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showIgnored = true }) {
                    Text(tr("عرض الحركات المستبعدة (${ignored.size})", "Show left-out transactions (${ignored.size})"))
                }
            }
        }
        if (ssec == 1) item {
            Section(tr("التصنيف التلقائي", "Auto-sorting")) {
                Text(tr("أي حركة يحتوي اسم المتجر فيها على الكلمة تُصنَّف في البند تلقائياً. اضغط على قاعدة لتعديلها.",
                    "Any transaction whose shop name contains the word goes to that line by itself. Tap a rule to edit it."),
                    style = MaterialTheme.typography.bodySmall)
                rules.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editingRule = r },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(r.keyword, maxLines = 1)
                            Text(catNames[r.categoryId] ?: "?", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { db.deleteRule(r.id); refresh() }) {
                            Icon(Icons.Filled.Delete, tr("حذف", "Delete"))
                        }
                    }
                }
                OutlinedButton(onClick = { addingRule = true }, modifier = Modifier.padding(top = 6.dp)) { Text(tr("إضافة قاعدة", "Add rule")) }
            }
        }
        if (ssec == 2) item {
            Section(tr("عام", "General")) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("ليس لدي راتب", "I don't have a salary"))
                        Text(tr("يبدأ الشهر في يوم ثابت، ولا يظهر الراتب في التطبيق", "The month starts on a fixed day and salary is hidden"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = noSalary, onCheckedChange = {
                        noSalary = it; db.setSetting("no_salary", if (it) "1" else "0"); refresh()
                    })
                }
                Spacer(Modifier.height(8.dp))
                if (!noSalary) {
                OutlinedTextField(
                    value = salary, onValueChange = { salary = it }, label = { Text(tr("الراتب المتوقع (للتخطيط فقط)", "Expected salary (for planning only)")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text(tr("ما يظهر في الرئيسية هو الراتب الفعلي من رسالة «قيد راتب»، وليس هذا الرقم.",
                    "Home shows the real salary from the bank's salary SMS, not this number."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(tr("بداية الشهر", "The month starts"), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    FilterChip(bySalary, { bySalary = true; db.setSetting("cycle_mode", "salary"); refresh() },
                        { Text(tr("يوم وصول الراتب", "When salary arrives")) })
                    FilterChip(!bySalary, { bySalary = false; db.setSetting("cycle_mode", "day"); refresh() },
                        { Text(tr("في يوم ثابت", "On a fixed day")) })
                }
                }
                if (!bySalary || noSalary) {
                    OutlinedTextField(
                        value = cycleDay, onValueChange = { cycleDay = it },
                        label = { Text(tr("يوم بداية الشهر (1-28)", "Start day of the month (1-28)")) },
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                } else if (!noSalary) {
                    Text(tr("عند وصول رسالة الراتب يبدأ شهر جديد تلقائياً. وقبل أول راتب يُستخدم اليوم ${db.cycleDay()} من الشهر.",
                        "A new month starts by itself when the salary SMS arrives. Before the first salary, day ${db.cycleDay()} of the month is used."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    salary.replace(',', '.').toDoubleOrNull()?.let { db.setSetting("salary", it.toString()) }
                    cycleDay.toIntOrNull()?.let { db.setSetting("cycle_day", it.coerceIn(1, 28).toString()) }
                    cycleDay = db.cycleDay().toString()
                    Toast.makeText(ctx, tr("تم الحفظ", "Saved"), Toast.LENGTH_SHORT).show()
                    refresh()
                }) { Text(tr("حفظ", "Save")) }
            }
        }
        if (ssec == 2) item {
            Section(tr("التنبيهات", "Notifications")) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("ملخص يومي الساعة 10 مساءً", "Daily summary at 10 pm"))
                        Text(tr("كم صرفت، وكم يتاح لك غداً", "What you spent, and what you can spend tomorrow"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = dailySummary, onCheckedChange = {
                        dailySummary = it; db.setSetting("daily_summary", if (it) "1" else "0")
                    })
                }
                Text(tr("يعمل تذكير الدفعات لكل بند حدّدت له «يوم الدفع» (من البنود أعلاه)، ويصلك قبل الموعد بيوم.",
                    "Payment reminders work for every line with a “payment day” (set in Budget lines above) and arrive a day before."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (ssec == 2 && (Build.VERSION.SDK_INT >= 28)) item {
            Section(tr("الأمان", "Security")) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("قفل التطبيق بالبصمة", "Lock the app with fingerprint"))
                        Text(tr("أو برمز قفل الهاتف", "or the phone's screen lock"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = lockOn, onCheckedChange = {
                        lockOn = it; db.setSetting("lock", if (it) "1" else "0")
                    })
                }
            }
        }
        if (ssec == 2) item {
            Section(tr("مساعدة", "Help")) {
                OutlinedButton(onClick = { showHelp = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("صلاحية الرسائل والتثبيت", "SMS access & installing"))
                }
                OutlinedButton(onClick = {
                    db.setSetting("onboarded", "0")
                    // Setup only shows for a fresh start; this asks again even when data exists.
                    db.setSetting("onboarding_again", "1")
                    (ctx as? android.app.Activity)?.recreate()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("إعادة أسئلة البداية", "Run the setup questions again"))
                }
            }
        }
        if (ssec == 3) item {
            Section(tr("نسخة احتياطية", "Backup")) {
                Text(tr("احفظ كل بياناتك في ملف، ويمكنك استرجاعها على الهاتف نفسه أو على هاتف جديد.",
                    "Save all your data to a file; you can restore it on this phone or a new one."),
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { saveJson.launch(Backup.fileName("json")) }) { Text(tr("حفظ نسخة احتياطية", "Save a backup")) }
                OutlinedButton(onClick = { openJson.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }) {
                    Text(tr("الاسترجاع من نسخة", "Restore from a backup"))
                }
                OutlinedButton(onClick = { saveCsv.launch(Backup.fileName("csv")) }) { Text(tr("تصدير الحركات إلى Excel", "Export transactions to Excel")) }
            }
        }
        if (ssec == 3) item {
            Section(tr("مسح البيانات", "Erase data")) {
                Text(tr("الحركات المسجّلة فقط، أو كل شيء والعودة إلى حالة التطبيق عند تثبيته.",
                    "Just the recorded transactions, or everything, back to how the app was when installed."),
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { confirmReset = 1 }) { Text(tr("مسح كل الحركات", "Erase all transactions")) }
                Button(
                    onClick = { confirmReset = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(tr("إعادة ضبط التطبيق", "Reset the app")) }
            }
        }
    }

    if (showIgnored) IgnoredDialog(db) { showIgnored = false; refresh() }
    if (showHelp) PermissionHelpSheet { showHelp = false }
    editingRule?.let { r -> RuleDialog(db, r) { editingRule = null; refresh() } }
    if (addingRule) RuleDialog(db, null) { addingRule = false; refresh() }
    if (pickingRange) {
        ImportRangeDialog(importFrom, importTo, onDismiss = { pickingRange = false }) { f, t ->
            importFrom = f; importTo = t; pickingRange = false
        }
    }
    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text(tr("استرجاع النسخة؟", "Restore this backup?")) },
            text = { Text(tr("ستُحذف بياناتك الحالية وتحل محلها بيانات النسخة.", "Your current data will be replaced with the backup's data.")) },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        Backup.restoreJson(db, Backup.read(ctx, uri))
                        salary = db.setting("salary", "0"); cycleDay = db.cycleDay().toString()
                        toast(tr("استُرجعت البيانات ✓", "Data restored ✓"))
                    } catch (e: Exception) {
                        toast(tr("هذا الملف ليس نسخة احتياطية من ميزانيتي", "This file isn't a My Budget backup"))
                    }
                    restoreUri = null
                    refresh()
                }) { Text(tr("استرجاع", "Restore"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text(tr("إلغاء", "Cancel")) } },
        )
    }
    if (confirmReset != 0) {
        val all = confirmReset == 2
        AlertDialog(
            onDismissRequest = { confirmReset = 0 },
            title = { Text(if (all) tr("إعادة ضبط التطبيق؟", "Reset the app?") else tr("مسح كل الحركات؟", "Erase all transactions?")) },
            text = {
                Text(
                    if (all) tr("سيُحذف كل شيء: الحركات والبنود والحسابات والديون والتصنيف التلقائي والإعدادات، وتعود الميزانية الافتراضية.",
                        "Everything will be erased: transactions, budget lines, accounts, debts, auto-sorting rules and settings. The default budget comes back.")
                    else tr("ستُحذف كل الحركات المسجّلة، وتبقى البنود والحسابات والديون والتصنيف التلقائي. يمكنك بعدها استيراد رسائل هذا الشهر من جديد.",
                        "All recorded transactions will be erased. Budget lines, accounts, debts and auto-sorting rules stay. You can import this month's SMS again afterwards.")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (all) db.resetAll() else db.clearTransactions()
                    salary = db.setting("salary", "0")
                    cycleDay = db.cycleDay().toString()
                    confirmReset = 0
                    Toast.makeText(ctx, tr("مُسحت البيانات", "Data erased"), Toast.LENGTH_SHORT).show()
                    refresh()
                }) { Text(tr("مسح", "Erase"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = 0 }) { Text(tr("إلغاء", "Cancel")) } },
        )
    }
    pickFor?.let { a -> SenderPickerDialog(db, a) { pickFor = null; refresh() } }
    editAcc?.let { a -> AccountDialog(db, a, a.name, canDelete = accounts.size > 1) { editAcc = null; refresh() } }
    if (addingAcc) AccountDialog(db, null, tr("الحساب ${accounts.size + 1}", "Account ${accounts.size + 1}"), canDelete = false) { addingAcc = false; refresh() }
    editing?.let { c -> CategoryDialog(db, c) { editing = null; refresh() } }
    if (addingCat) CategoryDialog(db, null) { addingCat = false; refresh() }
}

/** Picks a from/to date range; returns local start-of-day times for both ends. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportRangeDialog(from: Long, to: Long, onDismiss: () -> Unit, title: String = tr("فترة الاستيراد", "Import range"), onPick: (Long, Long) -> Unit) {
    // The picker works in UTC days; convert the local days in and out.
    fun toUtc(local: Long): Long {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = local }
        return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear(); set(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
    fun toLocal(utc: Long): Long {
        val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
        return java.util.Calendar.getInstance().apply {
            clear(); set(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = toUtc(from),
        initialSelectedEndDateMillis = toUtc(to),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val s = state.selectedStartDateMillis ?: return@TextButton
                    val e = state.selectedEndDateMillis ?: s
                    onPick(toLocal(s), toLocal(e))
                },
            ) { Text(tr("تم", "Done")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("إلغاء", "Cancel")) } },
    ) {
        DateRangePicker(
            state = state,
            title = { Text(title, Modifier.padding(start = 24.dp, top = 16.dp)) },
            modifier = Modifier.weight(1f),
        )
    }
}

/** Add or edit an automatic rule: a shop or provider name and the budget line it goes to. */
@Composable
fun RuleDialog(db: Db, rule: Rule?, onDone: () -> Unit) {
    val cats = remember { db.categories() }
    var keyword by remember { mutableStateOf(rule?.keyword ?: "") }
    var cat by remember { mutableStateOf(rule?.categoryId) }
    val ok = db.validRuleKey(keyword) && cat != null
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (rule == null) tr("قاعدة جديدة", "New rule") else tr("تعديل القاعدة", "Edit rule")) },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword, onValueChange = { keyword = it }, singleLine = true,
                    label = { Text(tr("اسم المتجر أو المزوّد", "Shop or provider name")) }, placeholder = { Text(tr("مثلاً ZAIN أو CARREFOUR", "e.g. ZAIN or CARREFOUR")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (keyword.isNotBlank() && !db.validRuleKey(keyword)) {
                    Text(tr("اكتب اسم المتجر فقط، لا نص الرسالة.", "Type just the shop name, not the SMS text."), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                Text(tr("البند", "Budget line"), style = MaterialTheme.typography.titleSmall)
                CategoryChips(cats, cat) { cat = it }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                val c = cat ?: return@TextButton
                if (rule == null) db.addRule(keyword, c) else db.updateRule(rule.id, keyword, c)
                db.applyRulesToPending()
                onDone()
            }) { Text(tr("حفظ", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(tr("إلغاء", "Cancel")) } },
    )
}


/** The "not spending" transactions, each with a button to count it again. */
@Composable
private fun IgnoredDialog(db: Db, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var local by remember { mutableIntStateOf(0) }
    val list = remember(local) { db.ignoredTxns() }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(tr("حركات مستبعدة", "Left-out transactions")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (list.isEmpty()) Text(tr("لا توجد حركات مستبعدة.", "Nothing left out."))
                list.forEachIndexed { i, t ->
                    if (i > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.merchant.ifBlank { t.kind }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (t.income) "+" else "−") + fmt(t.amount) + "  ·  " + Period.stamp(t.time),
                                style = MaterialTheme.typography.bodySmall, fontFamily = Ui.numbers,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = {
                            db.unignoreTxn(t.id); AllowanceWidget.refreshAll(ctx); local++
                            Toast.makeText(ctx, tr("رجعت الحركة للحساب", "Counted again"), Toast.LENGTH_SHORT).show()
                        }) { Text(tr("إرجاع", "Bring back")) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(tr("تم", "Done")) } },
    )
}
