@file:OptIn(ExperimentalLayoutApi::class)

package com.mizania.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToLong

object Setup {
    /** First launch: show the three questions. Anyone already set up skips them. */
    fun needed(db: Db): Boolean {
        if (db.setting("onboarded", "0") == "1") return false
        if (db.setting("onboarding_again", "0") == "1") return true
        if (db.senders().isNotEmpty() || db.recent(1).isNotEmpty()) {
            db.setSetting("onboarded", "1")
            return false
        }
        return true
    }

    /** Share of a monthly amount for each built-in line, found by its emoji. */
    private val shares = mapOf(
        "🏠" to 0.30, "💡" to 0.08, "🛒" to 0.15, "🚗" to 0.10, "☕" to 0.07,
        "💊" to 0.03, "📦" to 0.07, "🚑" to 0.05, "💰" to 0.10,
    )

    /** A starting budget from [monthly]: fills only lines that have no budget yet, rounded to 5. */
    fun fillBudgets(db: Db, monthly: Double) {
        if (monthly <= 0) return
        for (c in db.categories()) {
            if (c.budget > 0) continue
            val share = shares[c.icon] ?: continue
            val v = (monthly * share / 5.0).roundToLong() * 5.0
            if (v > 0) db.saveCategory(c.copy(budget = v))
        }
    }

    fun banks(): List<String> = listOf(
        tr("البنك العربي", "Arab Bank"), tr("بنك الإسكان", "Housing Bank"), tr("بنك الاتحاد", "Bank al Etihad"),
        tr("بنك القاهرة عمان", "Cairo Amman Bank"), tr("كابيتال بنك", "Capital Bank"),
        tr("البنك الأردني الكويتي", "Jordan Kuwait Bank"), tr("بنك صفوة الإسلامي", "Safwa Islamic Bank"),
        tr("البنك الإسلامي الأردني", "Jordan Islamic Bank"), tr("محفظة إلكترونية", "E-wallet"),
    )
}

/**
 * First launch, three questions: salary or not, roughly how much, which bank.
 * The answers set up the month, a starting budget and the bank's messages.
 */
@Composable
fun OnboardingScreen(db: Db, missingPerms: Boolean, onAskPerms: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var hasSalary by remember { mutableStateOf(true) }
    var amount by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var local by remember { mutableIntStateOf(0) }
    val account = remember(local) { db.accounts().firstOrNull() }
    val senders = remember(local) { db.senders() }
    val p = Ui.p

    fun finish() {
        db.setSetting("onboarded", "1")
        db.setSetting("onboarding_again", "0")
        if (db.senders().isNotEmpty()) {
            val n = Processor.importInbox(ctx, Period.day(-29).from)
            if (n > 0) Toast.makeText(ctx, tr("أُضيفت ${txnsText(n)} من آخر 30 يوماً", "Added ${txnsText(n)} from the last 30 days"),
                Toast.LENGTH_LONG).show()
        }
        onDone()
    }

    BackHandler(enabled = step > 0) { step-- }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            if (step > 0) {
                LinearProgressIndicator(
                    progress = { step / 3f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = p.mint, trackColor = p.line, drawStopIndicator = {},
                )
                Text(tr("السؤال $step من 3", "Question $step of 3"), fontFamily = Ui.numbers, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                modifier = Modifier.weight(1f),
                label = "onboarding",
            ) { s ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = if (s == 0) Arrangement.Center else Arrangement.Top,
                    horizontalAlignment = if (s == 0) Alignment.CenterHorizontally else Alignment.Start,
                ) {
                    when (s) {
                        0 -> {
                            Text("👋", fontSize = 72.sp)
                            Text(tr("أهلاً بك في ميزانيتي", "Welcome to My Budget"), style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
                            Text(tr("ثلاثة أسئلة سريعة ونجهّز لك كل شيء.", "Three quick questions and you're all set."),
                                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                        }
                        1 -> {
                            Question(tr("هل لديك راتب شهري؟", "Do you get a monthly salary?"))
                            BigChoice("💼", tr("نعم، لدي راتب", "Yes, I get a salary"), hasSalary) { hasSalary = true; step = 2 }
                            BigChoice("🙅", tr("لا، ليس لدي راتب", "No, I don't"), !hasSalary) { hasSalary = false; step = 2 }
                        }
                        2 -> {
                            Question(if (hasSalary) tr("كم راتبك تقريباً؟", "Roughly how much is your salary?")
                                else tr("كم تصرف في الشهر تقريباً؟", "Roughly how much do you spend a month?"))
                            OutlinedTextField(
                                value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                                singleLine = true, placeholder = { Text("500") }, suffix = { Text(currency()) },
                                textStyle = MaterialTheme.typography.headlineSmall,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                            )
                            Text(tr("سنقترح ميزانية لكل بند منه، ويمكنك تعديلها لاحقاً.",
                                "We'll suggest a budget for each line from it. You can change them any time."),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp))
                        }
                        else -> {
                            Question(tr("أي بنك تستخدم؟", "Which bank do you use?"))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Setup.banks().forEach { b -> FilterChip(bank == b, { bank = b }, { Text(b) }) }
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(tr("ثم اختر رسائله من صندوق الرسائل:", "Then pick its messages from your inbox:"),
                                style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            if (missingPerms) {
                                Button(onClick = onAskPerms, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                    Text(tr("اسمح بقراءة الرسائل", "Allow reading SMS"))
                                }
                                TextButton(onClick = { help = true }) { Text(tr("لا يعمل؟ اضغط هنا", "Not working? Tap here")) }
                            } else {
                                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                    Text(if (senders.isEmpty()) tr("اختيار رسائل البنك", "Pick bank messages")
                                        else tr("✓ تم اختيار ${senders.size}", "✓ ${senders.size} picked"))
                                }
                            }
                        }
                    }
                }
            }
            // Bottom buttons.
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { finish() }) {
                    Text(tr("تخطَّ", "Skip"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                when (step) {
                    0 -> Button(onClick = { step = 1 }, shape = RoundedCornerShape(16.dp)) { Text(tr("ابدأ", "Start")) }
                    1 -> {}
                    2 -> Button(onClick = {
                        val v = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
                        db.setSetting("no_salary", if (hasSalary) "0" else "1")
                        if (hasSalary && v > 0) db.setSetting("salary", v.toString())
                        Setup.fillBudgets(db, v)
                        step = 3
                    }, shape = RoundedCornerShape(16.dp)) { Text(tr("التالي", "Next")) }
                    else -> Button(onClick = {
                        if (bank.isNotEmpty()) account?.let { db.saveAccount(it.copy(bank = bank)) }
                        finish()
                    }, shape = RoundedCornerShape(16.dp)) { Text(tr("تم 🎉", "Done 🎉")) }
                }
            }
        }
    }
    if (picking && account != null) SenderPickerDialog(db, account) { picking = false; local++ }
    if (help) PermissionHelpSheet { help = false }
}

@Composable
private fun Question(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 24.dp, bottom = 20.dp))
}

@Composable
private fun BigChoice(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val p = Ui.p
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(20.dp))
            .background(if (selected) p.pine.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.5.dp, if (selected) p.pine else p.line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 30.sp)
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp))
    }
}

/**
 * How to let the app read SMS when Android blocks it (apps installed outside the Play Store),
 * and how to get past Play Protect when installing an update.
 */
@Composable
fun PermissionHelpSheet(onDone: () -> Unit) {
    val ctx = LocalContext.current
    SheetFrame(onDone) {
        Text(tr("السماح بقراءة الرسائل", "Allowing SMS access"), style = MaterialTheme.typography.titleLarge)
        Text(tr("أندرويد 13 وما بعده يمنع هذه الصلاحية عن التطبيقات المثبتة من خارج متجر Play. فعّلها مرة واحدة هكذا:",
            "Android 13 and newer block this for apps installed outside the Play Store. Turn it on once like this:"),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
        HelpStep(1, tr("اضغط «فتح إعدادات التطبيق» في الأسفل.", "Tap “Open app settings” below."))
        HelpStep(2, tr("اضغط ⋮ في أعلى الشاشة، ثم «السماح بالإعدادات المقيدة»، وأكّد برمز القفل.",
            "Tap ⋮ at the top, then “Allow restricted settings”, and confirm with your screen lock."))
        HelpStep(3, tr("ارجع وافتح «الأذونات» ← «الرسائل القصيرة» ← «السماح».", "Go back, open Permissions → SMS → Allow."))
        HelpStep(4, tr("ومن «الإشعارات» فعّل الإشعارات.", "And under Notifications, turn notifications on."))
        Button(
            onClick = {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(50.dp), shape = RoundedCornerShape(16.dp),
        ) { Text(tr("فتح إعدادات التطبيق", "Open app settings")) }

        Text(tr("إذا منع Play Protect التثبيت", "If Play Protect blocks installing"), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp, bottom = 6.dp))
        HelpStep(1, tr("في نافذة التحذير اضغط «مزيد من التفاصيل» ثم «التثبيت على أي حال».",
            "On the warning, tap “More details”, then “Install anyway”."))
        HelpStep(2, tr("إذا لم يظهر الخيار: متجر Play ← صورة حسابك ← Play Protect ← ⚙️ ← أوقف الفحص مؤقتاً، ثبّت، ثم أعد تشغيله.",
            "If that option isn't there: Play Store → your profile picture → Play Protect → ⚙️ → turn scanning off, install, then turn it back on."))
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(tr("تم", "Done")) }
    }
}

@Composable
private fun HelpStep(n: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(Ui.p.pine.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Text("$n", fontFamily = Ui.numbers, fontWeight = FontWeight.SemiBold, color = Ui.p.pine) }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
}
