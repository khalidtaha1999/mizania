package com.mizania.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Calendar

/** This month's bills by date: what is paid, what is next, and yearly expenses coming up. */
@Composable
fun BillsCard(db: Db, tick: Int) {
    val bills = remember(tick) { Watch.upcomingBills(db) }
    if (bills.isEmpty()) return
    val today = Period.day(0).from
    val p = Ui.p
    Section(tr("الفواتير الجاية", "Upcoming bills"), icon = AppIcons.Event) {
        bills.forEachIndexed { i, b ->
            if (i > 0) HorizontalDivider(color = p.line)
            val days = ((b.due - today) / Period.DAY).toInt()
            val whenText = when {
                b.paid -> tr("مدفوع ✓", "Paid ✓")
                days < 0 -> tr("متأخر ${daysText(-days)}", "${daysText(-days)} late")
                days == 0 -> tr("اليوم", "Today")
                days == 1 -> tr("بكرا", "Tomorrow")
                else -> tr("بعد ${daysText(days)}", "In ${daysText(days)}")
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${b.cat.icon}  ${b.cat.name}" + if (b.yearly) tr(" · سنوي", " · yearly") else "",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("${Period.dayMonth(b.due)} · $whenText", style = MaterialTheme.typography.bodySmall,
                        color = when {
                            b.paid -> p.mint
                            days < 0 -> p.pomegranate
                            days <= 2 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        })
                }
                Text(if (b.paid) fmt(b.amount) else fmt(b.left), fontFamily = Ui.numbers,
                    color = if (b.paid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
            }
        }
        Text(tr("بيوصلك تذكير قبل كل فاتورة بيومين وقبلها بيوم. حدد «يوم الدفع» لأي بند ثابت من إعدادات البنود.",
            "You get a reminder two days and one day before each bill. Set a payment day on any fixed line in the line settings."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Yearly expenses: what is already put aside for each and what is still to come. */
@Composable
fun AnnualCard(db: Db, tick: Int, refresh: () -> Unit) {
    var local by remember { mutableIntStateOf(0) }
    val states = remember(tick, local) { Annual.items(db).map { Annual.state(db, it) } }
    var editing by remember { mutableStateOf<Annual.Item?>(null) }
    var adding by remember { mutableStateOf(false) }
    val p = Ui.p
    Section(tr("مصاريف سنوية", "Yearly expenses"), icon = AppIcons.CalendarMonth, action = {
        TextButton(onClick = { adding = true }) { Text(tr("+ إضافة", "+ Add")) }
    }) {
        if (states.isEmpty()) Text(
            tr("ترخيص السيارة، التأمين، المدارس… حطها مرة وحدة، والتطبيق بيحجزلها شوي كل يوم لحد موعدها، فما بتضرب شهر لحاله.",
                "Car licence, insurance, school fees… add them once and a little is held back every day until they're due."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        states.forEachIndexed { i, st ->
            if (i > 0) HorizontalDivider(color = p.line)
            val it = st.item
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { editing = it }.padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${it.cat.icon}  ${it.cat.name}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(fmt(it.amount), fontFamily = Ui.numbers)
                }
                LinearProgressIndicator(
                    progress = { ((st.held + st.paid) / it.amount).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (st.overdue) p.pomegranate else p.mint, trackColor = p.line, drawStopIndicator = {},
                )
                Text(
                    if (st.overdue) tr("موعدها ${Period.dayMonth(it.due)} فات. إذا دفعتها، صنّف الدفعة على هالبند أو اضغط وعلّمها مدفوعة.",
                        "Due ${Period.dayMonth(it.due)} has passed. If paid, sort the payment onto this line or tap to mark it paid.")
                    else tr("محجوز ${fmt(st.held + st.paid)} لحد الآن · موعدها ${Period.dayMonth(it.due)} (بعد ${daysText(st.daysToDue)}) · حوالي ${fmt(it.monthly)} بالشهر",
                        "${fmt(st.held + st.paid)} put aside so far · due ${Period.dayMonth(it.due)} (in ${daysText(st.daysToDue)}) · about ${fmt(it.monthly)} a month"),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (st.overdue) p.pomegranate else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (adding || editing != null) AnnualDialog(db, editing) { adding = false; editing = null; local++; refresh() }
}

@Composable
private fun AnnualDialog(db: Db, item: Annual.Item?, onDone: () -> Unit) {
    val dueCal = remember { Calendar.getInstance().apply { timeInMillis = item?.due ?: System.currentTimeMillis() } }
    var name by remember { mutableStateOf(item?.cat?.name ?: "") }
    var icon by remember { mutableStateOf(item?.cat?.icon ?: "🗓") }
    var amount by remember { mutableStateOf(item?.amount?.let { fmtPlain(it) } ?: "") }
    var day by remember { mutableStateOf(if (item != null) dueCal.get(Calendar.DAY_OF_MONTH).toString() else "") }
    var month by remember { mutableStateOf(if (item != null) (dueCal.get(Calendar.MONTH) + 1).toString() else "") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ideas = listOf("🚗" to tr("ترخيص السيارة", "Car licence"), "🛡" to tr("تأمين السيارة", "Car insurance"),
        "🎒" to tr("المدارس", "School fees"), "🏠" to tr("ضريبة المسقفات", "Property tax"))
    SheetFrame(onDone) {
        Text(if (item == null) tr("مصروف سنوي جديد", "New yearly expense") else tr("تعديل المصروف السنوي", "Edit yearly expense"),
            style = MaterialTheme.typography.titleLarge)
        if (item == null) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ideas.forEach { (ic, n) ->
                TextButton(onClick = { name = n; icon = ic }) { Text("$ic $n", style = MaterialTheme.typography.bodySmall) }
            }
        }
        OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(tr("الاسم", "Name")) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(14.dp))
        OutlinedTextField(amount, { amount = it }, singleLine = true, label = { Text(tr("المبلغ بالسنة", "Amount per year")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(14.dp))
        SheetLabel(tr("موعدها (يوم / شهر)", "Due date (day / month)"))
        Row {
            OutlinedTextField(day, { day = it.filter { c -> c.isDigit() }.take(2) }, singleLine = true,
                label = { Text(tr("اليوم", "Day")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(month, { month = it.filter { c -> c.isDigit() }.take(2) }, singleLine = true,
                label = { Text(tr("الشهر (1-12)", "Month (1-12)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
        }
        error?.let { Text(it, color = Ui.p.pomegranate, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(14.dp))
        Button(onClick = {
            val a = amount.trim().replace('٫', '.').replace(',', '.').toDoubleOrNull()
            val d = day.toIntOrNull(); val m = month.toIntOrNull()
            error = when {
                name.isBlank() -> tr("اكتب اسم", "Enter a name")
                a == null || a <= 0 -> tr("اكتب المبلغ", "Enter the amount")
                d == null || d !in 1..31 || m == null || m !in 1..12 -> tr("اكتب يوم وشهر صحيحين", "Enter a valid day and month")
                else -> null
            }
            if (error == null) {
                Annual.add(db, name.trim(), icon, a!!, m!!, d!!, item?.cat?.id)
                onDone()
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(tr("حفظ", "Save")) }
        if (item != null) {
            TextButton(onClick = { Annual.markPaid(db, item); onDone() }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("✓ دفعتها هالسنة (من مكان ما بيشوفه التطبيق)", "✓ Paid this year (from somewhere the app doesn't see)"))
            }
            TextButton(onClick = { if (confirmDelete) { Annual.remove(db, item.cat.id); onDone() } else confirmDelete = true },
                modifier = Modifier.fillMaxWidth()) {
                Text(if (confirmDelete) tr("متأكد؟ اضغط مرة ثانية", "Sure? Tap again") else tr("حذف", "Delete"),
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** For money in: book it as a refund of a recent purchase, or undo that. */
@Composable
fun RefundRow(db: Db, t: Txn, onChanged: () -> Unit) {
    val of = remember(t.id) { db.refundOf(t.id)?.let { db.txn(it) } }
    var picking by remember { mutableStateOf(false) }
    if (of != null) {
        Text(tr("↩️ مرتجع لشراء ${merchantLabel(of.merchant)} (${fmt(of.amount)}، ${Period.dayMonth(of.time)}) — بينقص من بنده ومش محسوب دخل.",
            "↩️ Refund of ${merchantLabel(of.merchant)} (${fmt(of.amount)}, ${Period.dayMonth(of.time)}) — it lowers that line and is not income."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        TextButton(onClick = { db.unmarkRefund(t.id); onChanged() }) { Text(tr("مش مرتجع", "Not a refund")) }
        return
    }
    TextButton(onClick = { picking = !picking }) { Text(tr("↩️ هذا مرتجع لشراء", "↩️ This is a refund of a purchase")) }
    if (!picking) return
    val list = remember(t.id) { db.refundCandidates(t).take(8) }
    if (list.isEmpty()) Text(tr("ما في مشتريات بآخر 60 يوم بمبلغ يكفي.", "No purchase in the last 60 days is big enough."),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    list.forEach { b ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { db.markRefund(t.id, b.id); onChanged() }
            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${merchantLabel(b.merchant)} · ${Period.dayMonth(b.time)}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(fmt(b.amount), fontFamily = Ui.numbers)
        }
    }
}
