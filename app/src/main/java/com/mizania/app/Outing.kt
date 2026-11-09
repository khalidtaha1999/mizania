package com.mizania.app

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs

private fun num(s: String): Double? = s.trim().replace('٫', '.').replace(',', '.').toDoubleOrNull()

private fun plain(v: Double): String =
    if (abs(v - Math.round(v)) < 0.0005) Math.round(v).toString() else String.format(Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')

/**
 * "دفعت عن أصحابي": you paid the whole bill, each friend owes you their share. Only your share counts as your
 * spending (on the line you pick — never a default), and each share becomes a debt owed to you that closes by
 * itself when that friend's transfer arrives.
 */
@Composable
fun OutingSheet(db: Db, t: Txn, onDone: (saved: Boolean) -> Unit) {
    val cats = remember { db.categories() }
    val old = remember(t.id) { db.outing(t.id) }
    val full = remember(t.id) { t.amount + db.splitTotal(t.id) }
    var title by remember { mutableStateOf(old?.title?.ifBlank { null } ?: t.merchant.ifBlank { tr("طلعة", "Outing") }) }
    var cat by remember { mutableStateOf(if (old != null) t.categoryId else null) }
    val names = remember { mutableStateListOf<String>().apply { old?.debts?.forEach { add(it.person) } ?: run { add(""); add("") } } }
    val shares = remember { mutableStateListOf<String>().apply { old?.debts?.forEach { add(plain(it.amount)) } ?: run { add(""); add("") } } }
    var error by remember { mutableStateOf<String?>(null) }

    val friendsTotal = shares.sumOf { num(it) ?: 0.0 }
    val mine = full - friendsTotal
    val paidBack = old?.back ?: 0.0

    fun equal() {
        val n = names.size + 1
        val each = Math.floor(full / n * 1000) / 1000
        for (i in shares.indices) shares[i] = plain(each)
    }

    SheetFrame({ onDone(false) }) {
        Text(tr("👥 دفعت عن أصحابي", "👥 I paid for friends"), style = MaterialTheme.typography.titleLarge)
        Text(tr("المبلغ كله ${fmt(full)}. حصتك بس بتنحسب عليك، وحصة كل واحد بتصير دين إلك بيتسكّر لحاله لما يحوّلك.",
            "The whole bill is ${fmt(full)}. Only your share counts as yours; each friend's share becomes a debt owed to you that closes when they transfer."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp))

        OutlinedTextField(
            value = title, onValueChange = { title = it }, singleLine = true,
            label = { Text(tr("اسم الطلعة", "Name of the outing")) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(14.dp),
        )

        SheetLabel(tr("مين دفعتي عنهم؟", "Who did you pay for?"))
        names.indices.forEach { i ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = names[i], onValueChange = { names[i] = it }, singleLine = true,
                    placeholder = { Text(tr("الاسم", "Name")) }, modifier = Modifier.weight(1.4f), shape = RoundedCornerShape(12.dp),
                )
                Spacer(Modifier.width(6.dp))
                OutlinedTextField(
                    value = shares[i], onValueChange = { shares[i] = it }, singleLine = true,
                    placeholder = { Text(tr("حصته", "Share")) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (names.size > 1) TextButton(onClick = { names.removeAt(i); shares.removeAt(i) }) { Text("✕") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { names.add(""); shares.add("") }) { Text(tr("+ شخص", "+ Person")) }
            TextButton(onClick = { equal() }) { Text(tr("⚖️ بالتساوي (${names.size + 1} مع حصتك)", "⚖️ Equally (${names.size + 1} with you)")) }
        }

        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("حصتك", "Your share"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(fmt(mine), fontWeight = FontWeight.SemiBold, fontFamily = Ui.numbers,
                color = if (mine < -0.0005) Ui.p.pomegranate else MaterialTheme.colorScheme.onSurface)
        }
        Text(tr("لك على أصحابك ${fmt(friendsTotal)}", "Your friends owe you ${fmt(friendsTotal)}"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SheetLabel(tr("حصتك لأي بند تتبع؟", "Which line is your share for?"))
        CategoryChips(cats, cat) { cat = if (cat == it) null else it }

        if (paidBack > 0) Text(tr("تنبيه: سجّلنا ${fmt(paidBack)} رجعوا من أصحابك. التعديل بيمسح هالربط وبتضل التحويلات بدون دين.",
            "Note: ${fmt(paidBack)} already came back. Changing this removes those links; the transfers stay without a debt."),
            style = MaterialTheme.typography.bodySmall, color = Ui.p.pomegranate, modifier = Modifier.padding(top = 8.dp))
        error?.let { Text(it, color = Ui.p.pomegranate, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                val friends = names.indices.mapNotNull { i -> num(shares[i])?.let { names[i].trim() to it } }
                    .filter { it.first.isNotEmpty() && it.second > 0 }
                error = when {
                    friends.isEmpty() -> tr("اكتبي اسم واحد على الأقل وحصته", "Enter at least one name and share")
                    mine < -0.0005 -> tr("حصص أصحابك أكثر من المبلغ كله", "Friends' shares are more than the whole bill")
                    cat == null -> tr("اختاري لأي بند تتبع حصتك", "Pick the line for your share")
                    else -> null
                }
                if (error == null) {
                    db.payForOthers(t.id, cat, title.trim().ifEmpty { tr("طلعة", "Outing") }, friends, t.note)
                    onDone(true)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(tr("حفظ", "Save")) }
        if (old != null) TextButton(onClick = { db.clearOuting(t.id); onDone(true) }, modifier = Modifier.fillMaxWidth()) {
            Text(tr("إلغاء «دفعت عن أصحابي» (المبلغ كله عليّ)", "Undo — the whole amount is mine"), color = MaterialTheme.colorScheme.error)
        }
    }
}

/** A ready message reminding a friend of their share, sent with any chat app. */
fun remindFriend(ctx: android.content.Context, d: Debt) {
    val what = d.note.removePrefix("👥 ").ifBlank { tr("الطلعة", "the outing") }
    val text = tr("مرحبا ${d.person} 👋 تذكير بحصتك من «$what»: ${fmt(d.remaining)} 🙏",
        "Hi ${d.person} 👋 a reminder of your share of “$what”: ${fmt(d.remaining)} 🙏")
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    ctx.startActivity(Intent.createChooser(send, tr("إرسال تذكير", "Send a reminder")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
