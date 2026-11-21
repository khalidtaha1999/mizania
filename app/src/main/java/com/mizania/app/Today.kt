package com.mizania.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * What "spent today" is made of: the spending that counts against today's limit, what was spread over the
 * month, and what was paid today but never counts against a day (fixed lines, reserves).
 */
@Composable
fun TodaySheet(db: Db, onOpen: (Long) -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var local by remember { mutableIntStateOf(0) }
    val cats = remember { db.categories() }
    val byId = cats.associateBy { it.id }
    val type = cats.associate { it.id to it.type }
    val today = Period.day()
    val all = remember(local) { db.txns(today.from, today.to).filter { !it.income } }
    val spread = remember(local) { db.spreadIds() }
    val al = remember(local) { Budget.allowance(db) }
    val counted = all.filter { Budget.countsToday(it, type) && it.id !in spread }
    val spreadOnes = all.filter { it.id in spread }
    val other = all.filter { !Budget.countsToday(it, type) && it.id !in spread }

    fun lineName(t: Txn) = t.categoryId?.let { byId[it] }?.let { "${it.icon} ${it.name}" } ?: tr("❔ بدون بند", "❔ No line")

    @Composable
    fun TxnLine(t: Txn, action: String? = null, onAction: () -> Unit = {}) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(t.id) }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(lineName(t) + "  ·  " + Period.clock(t.time), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Ui.numbers)
            }
            Amount(t.amount, 15.sp)
            if (action != null) TextButton(onClick = onAction) { Text(action, fontSize = 13.sp) }
        }
    }

    SheetFrame(onDone) {
        Text(tr("صرف اليوم", "Today's spending"), style = MaterialTheme.typography.titleLarge)
        Text(tr("يُحسب على حدّك اليومي الصرف على بنود «يومي» والحركات التي لم تُصنَّف بعد. الثابت والادخار محجوزان من أول الشهر فلا يُحسبان على يوم.",
            "Today's limit counts spending on “daily” lines and transactions not sorted yet. Fixed lines and savings are set aside from the start of the month, so they never count against a day."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        KV(tr("حدّك اليوم", "Today's limit"), fmt(al.allowance))
        KV(tr("انحسب عليه", "Counted against it"), fmt(al.spentToday), bold = true,
            color = if (al.leftToday < 0) Ui.p.pomegranate else Ui.p.mint)

        Text(tr("على حدّ اليوم", "Against today's limit"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
        if (counted.isEmpty()) Text(tr("لا شيء بعد.", "Nothing yet."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        counted.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(color = Ui.p.line)
            // Bank spends can be spread; cash never reached the balance, so it stays on its day.
            if (t.manual) TxnLine(t) else TxnLine(t, tr("وزّعها", "Spread")) {
                db.setSpread(t.id, true); AllowanceWidget.refreshAll(ctx); local++
            }
        }
        if (counted.any { it.categoryId == null }) {
            Text(tr("الحركات «بدون بند» تُحسب يومية حتى تصنّفها. اضغط عليها لتختار بندها.",
                "“No line” transactions count as daily until you sort them. Tap one to pick its line."),
                style = MaterialTheme.typography.bodySmall, color = Ui.p.pomegranate, modifier = Modifier.padding(top = 4.dp))
        }

        if (spreadOnes.isNotEmpty()) {
            Text(tr("موزّعة على باقي الشهر", "Spread over the month"), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 14.dp))
            spreadOnes.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider(color = Ui.p.line)
                TxnLine(t, tr("أعِدها لليوم", "Back to today")) {
                    db.setSpread(t.id, false); AllowanceWidget.refreshAll(ctx); local++
                }
            }
        }

        if (other.isNotEmpty()) {
            Text(tr("دُفعت اليوم ولا تُحسب على حدّك", "Paid today, not counted against the limit"),
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
            other.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider(color = Ui.p.line)
                TxnLine(t)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(tr("اضغط على أي حركة لتغيير بندها.", "Tap any transaction to change its line."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
