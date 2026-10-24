package com.mizania.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What was done to one transaction, so it can be undone. [skipped] = left unsorted for later. */
private data class Done(val id: Long, val prevCat: Long?, val ignored: Boolean, val label: String, val skipped: Boolean = false)

/**
 * "صنّف بسرعة": the waiting transactions one at a time, with every budget line as a big tile.
 * One tap books it and slides in the next; "تراجع" puts the last one back.
 */
@Composable
fun TriageScreen(db: Db, onOpenDetails: (Long) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var cats by remember { mutableStateOf(db.categories()) }
    var addingLine by remember { mutableStateOf<Txn?>(null) }
    val queue = remember { mutableStateListOf<Txn>().apply { addAll(db.pending()) } }
    val total = remember { queue.size }
    val history = remember { mutableStateListOf<Done>() }
    var learn by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    var linking by remember { mutableStateOf<Pair<Txn, Category>?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(confirm, tick) {
        if (confirm != null) { delay(2600); confirm = null }
    }
    val p = Ui.p

    BackHandler(onBack = onClose)

    /**
     * Books [t] under [c]. For savings, debt and jamiya lines [link] says where the money went:
     * Link.None keeps it unlinked, otherwise the chosen goal / debt / jamiya.
     */
    fun book(t: Txn, c: Category, link: Link? = null, branches: Boolean = false) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        db.updateTxn(t.id, c.id, t.note)
        // Rules for savings/debt/jamiya lines would book future ones without asking where: skip those.
        if (learn && !db.needsLink(c, t.income) && t.merchant.isNotBlank() && db.validRuleKey(t.merchant)) {
            // The exact name, or every name starting with the shop's name when the user said they are branches.
            db.addRule(if (branches) Db.brandKey(t.merchant) else t.merchant, c.id)
            // Others waiting in the queue that the rule now covers get the same line straight away.
            if (db.applyRulesToPending() > 0) queue.removeAll { it.id != t.id && db.txn(it.id)?.categoryId != null }
        }
        Notifier.cancel(ctx, t.id)
        Processor.afterCategorized(ctx, t.id, c.id, notifyRecorded = false)
        when (link) {
            null -> {}
            Link.None -> db.unlinkAll(t.id)
            is Link.Goal -> db.syncLinks(t.id, goalId = link.id)
            is Link.Debt -> db.syncLinks(t.id, debtId = link.id)
            is Link.Jam -> db.syncLinks(t.id, jamiyaId = link.id)
        }
        confirm = db.linkSummary(t.id) ?: tr("احتُسبت على «${c.name}»", "Booked under “${c.name}”")
        history += Done(t.id, null, false, c.name)
        queue.remove(t)
        // The new rule may have booked others from the same shop: drop them from the queue too.
        if (learn) {
            val still = db.pending().map { it.id }.toSet()
            queue.removeAll { it.id !in still }
        }
        tick++
    }

    fun ignore(t: Txn) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        db.ignoreTxn(t.id)
        Notifier.cancel(ctx, t.id)
        history += Done(t.id, null, true, tr("تجاهل", "Ignored"))
        queue.remove(t)
        tick++
    }

    /** Leaves [t] unsorted: it drops out of this round and stays in "to sort" for next time. */
    fun skip(t: Txn) {
        history += Done(t.id, null, false, tr("لاحقاً", "Later"), skipped = true)
        queue.remove(t)
        tick++
    }

    fun undo() {
        val last = history.removeLastOrNull() ?: return
        if (last.skipped) {
            db.txn(last.id)?.let { queue.add(0, it) }
            tick++
            return
        }
        if (last.ignored) db.writableDatabase.execSQL("UPDATE txns SET ignored=0 WHERE id=?", arrayOf<Any>(last.id))
        db.updateTxn(last.id, last.prevCat, db.txn(last.id)?.note ?: "")
        db.syncLinks(last.id)
        val t = db.txn(last.id) ?: return
        queue.add(0, t)
        tick++
    }

    androidx.compose.material3.Surface(
        Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        // Top bar: close, progress.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, tr("إغلاق", "Close")) }
            Column(Modifier.weight(1f)) {
                Text(tr("صنّف بسرعة", "Quick sort"), style = MaterialTheme.typography.titleMedium)
                Text(tr("${total - queue.size} من $total", "${total - queue.size} of $total"), fontFamily = Ui.numbers, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(history.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                TextButton(onClick = { undo() }) { Text(tr("تراجع", "Undo")) }
            }
        }
        val done = if (total == 0) 1f else (total - queue.size).toFloat() / total
        val smooth by androidx.compose.animation.core.animateFloatAsState(done, tween(500), label = "progress")
        LinearProgressIndicator(
            progress = { smooth },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = p.mint, trackColor = p.line, drawStopIndicator = {},
        )

        val current = queue.firstOrNull()
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                (slideInHorizontally(tween(320)) { -it / 2 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(260)) { it / 2 } + fadeOut(tween(180)))
            },
            contentKey = { it?.id },
            modifier = Modifier.weight(1f),
            label = "card",
        ) { t ->
            if (t == null) {
                AllDone(history.count { it.skipped }, onClose)
            } else {
                val brand = remember(t.id) { Db.brandKey(t.merchant) }
                val hasBrand = brand != Db.ruleKey(t.merchant)
                val similar = remember(t.id) { db.similarRule(t.merchant) }
                val similarCat = similar?.let { r -> cats.firstOrNull { it.id == r.categoryId } }
                var branches by remember(t.id) { mutableStateOf(false) }
                var notSame by remember(t.id) { mutableStateOf(false) }
                Column(Modifier.fillMaxSize()) {
                    TxnCard(t, onDetails = { onOpenDetails(t.id) })
                    // Same start as a shop already sorted: offer it, never apply it alone.
                    if (similar != null && similarCat != null && !notSame) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                                .background(p.dinar.copy(alpha = 0.14f)).padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(tr("يشبه «${similar.keyword}» المصنّف في ${similarCat.icon} ${similarCat.name}. فرع من المحل نفسه؟",
                                "Looks like “${similar.keyword}”, sorted as ${similarCat.icon} ${similarCat.name}. A branch of the same shop?"),
                                style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { book(t, similarCat, branches = learn) }) {
                                    Text(tr("نعم، كل فروع «$brand»", "Yes, all “$brand” branches"), fontWeight = FontWeight.SemiBold)
                                }
                                TextButton(onClick = { notSame = true }) { Text(tr("لا، محل آخر", "No, another shop")) }
                            }
                        }
                    }
                    Text(tr("إلى أي بند؟", "Which budget line?"), style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(cats, key = { it.id }) { c ->
                            CategoryTile(c) { if (db.needsLink(c, t.income)) linking = t to c else book(t, c, branches = branches && hasBrand) }
                        }
                        item(key = "new-line") { NewLineTile { addingLine = t } }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (t.merchant.isNotBlank() && db.validRuleKey(t.merchant)) {
                            Checkbox(checked = learn, onCheckedChange = { learn = it })
                            Column(Modifier.weight(1f)) {
                                Text(tr("تذكّر «${t.merchant}» في المرات القادمة", "Remember “${t.merchant}” next time"), maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface)
                                if (learn && hasBrand) {
                                    Row(Modifier.clickable { branches = !branches }, verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = branches, onCheckedChange = { branches = it })
                                        Text(tr("وكل فروع «$brand»", "and every “$brand” branch"),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        TextButton(onClick = { skip(t) }) { Text(tr("تخطَّ الآن", "Skip for now")) }
                        TextButton(onClick = { ignore(t) }) { Text(tr("ليس مصروفاً", "Not spending"), color = p.pomegranate) }
                    }
                }
            }
        }
    }
    // Where the last one went, for a moment.
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 64.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(confirm != null, enter = fadeIn() + androidx.compose.animation.slideInVertically { it },
            exit = fadeOut()) {
            Text(
                confirm ?: "", color = p.onPine, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(14.dp)).background(p.hero)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
    }
    linking?.let { (t, c) ->
        LinkPicker(db, t, c, onPick = { link -> linking = null; book(t, c, link) }, onCancel = { linking = null })
    }
    addingLine?.let { t ->
        NewLineDialog(
            colorFor = CategoryColors[cats.size % CategoryColors.size],
            onCancel = { addingLine = null },
            onSave = { cat ->
                addingLine = null
                val id = db.saveCategory(cat)
                cats = db.categories()
                // The new line goes straight onto the transaction that asked for it.
                cats.firstOrNull { it.id == id }?.let { c -> book(t, c) }
            },
        )
    }
}

/** The last tile in the grid: make a new budget line for this transaction. */
@Composable
private fun NewLineTile(onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    Column(
        Modifier.height(92.dp).pressScale(src).clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("＋", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(tr("بند جديد", "New line"), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/** The last character as the user sees it (one emoji, even one made of several code points). */
private fun lastGrapheme(s: String): String {
    val t = s.trim()
    if (t.isEmpty()) return ""
    val bi = android.icu.text.BreakIterator.getCharacterInstance()
    bi.setText(t)
    val end = bi.last()
    val start = bi.previous()
    return if (start == android.icu.text.BreakIterator.DONE) t else t.substring(start, end)
}

/** Name, emoji and kind for a new budget line, made from inside quick sort. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewLineDialog(colorFor: Long, onCancel: () -> Unit, onSave: (Category) -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(Db.DAILY) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(tr("بند جديد", "New budget line")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(tr("الاسم", "Name")) }, placeholder = { Text(tr("مثلاً: هدايا", "e.g. Gifts")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = icon, onValueChange = { icon = lastGrapheme(it) }, singleLine = true,
                        label = { Text(tr("الإيموجي", "Emoji")) },
                        textStyle = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(tr("اكتبه من لوحة المفاتيح أو اختر:", "Type one from the keyboard, or pick:"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(start = 10.dp))
                }
                FlowRow(Modifier.padding(top = 6.dp)) {
                    CategoryIcons.forEach { e ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (icon == e) Color(colorFor).copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { icon = e },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 20.sp) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(tr("النوع", "Type"), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(type == Db.DAILY, { type = Db.DAILY }, { Text(tr("يومي", "Daily")) })
                    FilterChip(type == Db.FIXED, { type = Db.FIXED }, { Text(tr("ثابت", "Fixed")) })
                    FilterChip(type == Db.RESERVE, { type = Db.RESERVE }, { Text(tr("احتياط/ادخار", "Reserve/savings")) })
                }
                Text(tr("يمكنك تحديد ميزانيته لاحقاً من الإعدادات ← البنود.", "You can set its budget later in Settings → Budget lines."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val n = name.trim()
                val look = defaultLook(n)
                onSave(Category(0, n, 0.0, type, icon = icon.ifEmpty { look.first }, color = colorFor))
            }) { Text(tr("إضافة وتصنيف", "Add and book")) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(tr("إلغاء", "Cancel")) } },
    )
}

/** Where money booked under a savings, debt or jamiya line goes. */
sealed class Link {
    object None : Link()
    data class Goal(val id: Long) : Link()
    data class Debt(val id: Long) : Link()
    data class Jam(val id: Long) : Link()
}

/** Asks which goal, debt or jamiya — every time, so it is always clear where the money went. */
@Composable
fun LinkPicker(db: Db, t: Txn, c: Category, onPick: (Link) -> Unit, onCancel: () -> Unit) {
    data class Opt(val link: Link, val title: String, val sub: String)
    val (title, empty, opts) = remember(t.id, c.id) {
        when {
            db.isSavings(c) -> Triple(tr("لأي هدف ادخار؟", "Which savings goal?"),
                tr("لا توجد لديك أهداف ادخار. أنشئ هدفاً من «أموالي ← الادخار».", "You have no savings goals. Create one in “My money → Savings”."),
                db.goals().map { Opt(Link.Goal(it.id), "${it.icon}  ${it.name}",
                    tr("جمعت ${fmt(it.saved)} من ${fmt(it.target)}", "Saved ${fmt(it.saved)} of ${fmt(it.target)}")) })
            db.isDebtLine(c) -> Triple(tr("لأي دين؟", "Which debt?"),
                tr("لا يوجد دين مسجّل عليك. أضفه من «أموالي ← الديون».", "You have no debts recorded. Add one in “My money → Debts”."),
                db.openDebtsIOwe().map { Opt(Link.Debt(it.id), it.person, tr("المتبقي عليك ${fmt(it.remaining)}", "${fmt(it.remaining)} left to pay")) })
            else -> Triple(tr("أي جمعية؟", "Which jamiya?"),
                tr("لا توجد جمعية مسجّلة. أضفها من «أموالي ← الجمعية».", "No jamiya recorded. Add one in “My money → Jamiya”."),
                db.jamiyas().map { j ->
                    val i = JamiyaMonths.indexAt(j, t.time)
                    Opt(Link.Jam(j.id), "🤝  ${j.name}",
                        if (i in 0 until j.months) tr("سيُسجَّل شهر ${JamiyaMonths.label(j, i)} مدفوعاً", "${JamiyaMonths.label(j, i)} will be marked paid")
                        else tr("تاريخ الحركة خارج أشهرها", "This date is outside its months"))
                })
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column {
                Text(fmt(t.amount) + tr("، ", ", ") + t.title, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                if (opts.isEmpty()) Text(empty, style = MaterialTheme.typography.bodyMedium)
                opts.forEach { o ->
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { onPick(o.link) }.padding(14.dp)
                    ) {
                        Text(o.title, style = MaterialTheme.typography.titleSmall)
                        Text(o.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(Link.None) }) { Text(tr("صنّفها دون ربط", "Book it without linking")) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(tr("إلغاء", "Cancel")) } },
    )
}

@Composable
private fun TxnCard(t: Txn, onDetails: () -> Unit) {
    val p = Ui.p
    Column(
        Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(30.dp)).background(p.hero)
            .clickable(onClick = onDetails).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(t.kindText + tr("، ", ", ") + Period.stamp(t.time), color = p.onPine.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodySmall, fontFamily = Ui.numbers)
        Spacer(Modifier.height(6.dp))
        Amount(t.amount, 44.sp, p.dinar, weight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(t.title, color = p.onPine, style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(tr("اضغط للتفاصيل والملاحظة", "Tap for details and a note"), color = p.onPine.copy(alpha = 0.78f), fontSize = 12.sp,
            modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun CategoryTile(c: Category, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val color = Color(c.color)
    Column(
        Modifier.height(92.dp).pressScale(src).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.5.dp, color.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(c.icon, fontSize = 26.sp)
        Spacer(Modifier.height(4.dp))
        Text(c.name, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp)
    }
}

@Composable
private fun AllDone(skipped: Int, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedVisibility(true, enter = scaleIn(tween(400)) + fadeIn()) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(48.dp)).background(Ui.p.mint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Text("✓", fontSize = 44.sp, color = Ui.p.mint) }
        }
        Spacer(Modifier.height(16.dp))
        Text(if (skipped > 0) tr("انتهت هذه الجولة", "Done for now") else tr("كل شيء مصنّف", "All sorted"),
            style = MaterialTheme.typography.headlineSmall)
        Text(if (skipped > 0) tr("تخطّيت ${txnsText(skipped)}، وستبقى في «بحاجة إلى تصنيف» للمرة القادمة.",
                "You skipped ${txnsText(skipped)}; they stay in “To sort” for next time.")
            else tr("أصبحت الميزانية والمتاح اليوم محسوبَين بدقة.", "Your budget and today's allowance are now accurate."), color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(24.dp))
        androidx.compose.material3.Button(onClick = onClose, shape = RoundedCornerShape(16.dp)) { Text(tr("العودة إلى الرئيسية", "Back to home")) }
    }
}
