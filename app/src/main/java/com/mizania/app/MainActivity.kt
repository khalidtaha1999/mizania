@file:OptIn(ExperimentalMaterial3Api::class)

package com.mizania.app

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.filled.Info

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TXN = "txn"
        /** Opens straight on "add a cash spend" (from the quick settings tile). */
        const val EXTRA_ADD_CASH = "add_cash"
    }

    private val openTxn = mutableStateOf<Long?>(null)
    private val addCash = mutableStateOf(false)
    private val tick = mutableIntStateOf(0)
    private val locked = mutableStateOf(false)
    private val crash = mutableStateOf<String?>(null)
    private var unlockedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If the app crashes, keep what happened so it can be shown and copied on the next start.
        val prefs = getSharedPreferences("crash", MODE_PRIVATE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { th, e ->
            try { prefs.edit().putString("last", android.util.Log.getStackTraceString(e).take(4000)).commit() } catch (_: Exception) {}
            previous?.uncaughtException(th, e)
        }
        crash.value = prefs.getString("last", null)
        Lang.apply(Lang.choice(this))
        Notifier.createChannels(this)
        Daily.schedule(this)
        // Re-reading stored messages after an update can take a while: never on the screen's thread.
        val app = applicationContext
        Background.run {
            Processor.repairIfNeeded(app)
            Db.get(app).reconcileDirections()
            Bus.changed()
        }
        readIntent(intent)
        Bus.listener = { tick.intValue++ }
        val dark = Look.isDark(this)
        val bigText = Look.bigText(this)
        // System bars in the page color, with dark icons on the light theme.
        val bar = if (dark) 0xFF12101F.toInt() else 0xFFF6F4FF.toInt()
        @Suppress("DEPRECATION")
        run { window.statusBarColor = bar; window.navigationBarColor = bar }
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (Lang.ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                MizaniaTheme(dark = dark, bigText = bigText) {
                  androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                  ) {
                    // The app stays composed under the lock, so unlocking returns to the same screen.
                    App(
                        tick = tick.intValue,
                        refresh = { tick.intValue++ },
                        openTxn = openTxn.value,
                        setOpenTxn = { openTxn.value = it },
                        addCash = addCash.value,
                        onAddCashShown = { addCash.value = false },
                    )
                    if (locked.value) LockOverlay(onUnlock = ::authenticate)
                    crash.value?.let { text ->
                        CrashDialog(text) {
                            getSharedPreferences("crash", MODE_PRIVATE).edit().remove("last").apply()
                            crash.value = null
                        }
                    }
                  }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        val lockOn = Build.VERSION.SDK_INT >= 28 && Db.get(this).setting("lock", "0") == "1"
        if (lockOn && System.currentTimeMillis() - unlockedAt > 30_000) {
            locked.value = true
            authenticate()
        } else if (!lockOn) {
            locked.value = false
        }
    }

    override fun onResume() {
        super.onResume()
        tick.intValue++
    }

    override fun onPause() {
        super.onPause()
        AllowanceWidget.refreshAll(this)
    }

    override fun onStop() {
        super.onStop()
        if (!locked.value) unlockedAt = System.currentTimeMillis()
    }

    /** Fingerprint/face, or the phone's PIN on Android 10+. Turns the lock off if the phone has neither. */
    private fun authenticate() {
        if (Build.VERSION.SDK_INT < 28) { locked.value = false; return }
        val b = BiometricPrompt.Builder(this).setTitle(tr("ميزانيتي", "My Budget")).setSubtitle(tr("افتح التطبيق", "Unlock the app"))
        allowCredential(b)
        val prompt = b.build()
        prompt.authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                locked.value = false
                unlockedAt = System.currentTimeMillis()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                val noLock = errorCode == BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT ||
                    errorCode == BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE ||
                    errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS ||
                    errorCode == 14 // BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL
                if (noLock) {
                    Db.get(this@MainActivity).setSetting("lock", "0")
                    locked.value = false
                    Toast.makeText(this@MainActivity, tr("لا توجد بصمة أو رمز قفل على الهاتف، لذا أُوقف القفل", "This phone has no fingerprint or screen lock, so the app lock was turned off"), Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    @Suppress("DEPRECATION")
    private fun allowCredential(b: BiometricPrompt.Builder) {
        when {
            Build.VERSION.SDK_INT >= 30 -> b.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            Build.VERSION.SDK_INT >= 29 -> b.setDeviceCredentialAllowed(true)
            else -> b.setNegativeButton(tr("إلغاء", "Cancel"), mainExecutor) { _, _ -> }
        }
    }

    override fun onDestroy() {
        Bus.listener = null
        super.onDestroy()
    }

    private fun readIntent(i: Intent?) {
        val id = i?.getLongExtra(EXTRA_TXN, -1L) ?: -1L
        if (id > 0) openTxn.value = id
        i?.removeExtra(EXTRA_TXN)
        if (i?.getBooleanExtra(EXTRA_ADD_CASH, false) == true) addCash.value = true
        i?.removeExtra(EXTRA_ADD_CASH)
    }
}

@Composable
fun App(
    tick: Int, refresh: () -> Unit, openTxn: Long?, setOpenTxn: (Long?) -> Unit,
    addCash: Boolean = false, onAddCashShown: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val db = remember { Db.get(ctx) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    if (addCash) { adding = true; onAddCashShown() }
    // Sheets opened from the Today card live here, at the top, like the transaction sheet.
    var homeSheet by remember { mutableIntStateOf(0) }

    val perms = remember {
        buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Background.run { Processor.repairIfNeeded(ctx.applicationContext); Bus.changed() }
        refresh()
    }
    LaunchedEffect(Unit) {
        if (perms.any { ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) launcher.launch(perms)
    }
    val missingPerms = perms.any { ctx.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    var triage by rememberSaveable { mutableStateOf(false) }
    var onboarding by remember { mutableStateOf(Setup.needed(db)) }
    // Main places: 0 Today, 2 Activity, 6 Plan, 7 Me. Pages inside them: 3 Reports, 8 Compare (from Me), 5 Settings.
    var reportSub by rememberSaveable { mutableIntStateOf(0) }
    var settingsSec by rememberSaveable { mutableIntStateOf(0) }
    var settingsFrom by rememberSaveable { mutableIntStateOf(0) }
    var planSub by rememberSaveable { mutableIntStateOf(0) }
    var moneySub by rememberSaveable { mutableIntStateOf(0) }
    val titles = mapOf(2 to tr("الحركات", "Activity"), 6 to tr("الخطة", "Plan"), 7 to tr("أنا", "Me"),
        3 to tr("التقارير", "Reports"), 8 to tr("قارن الأشهر", "Compare months"), 5 to tr("الإعدادات", "Settings"))
    val subPage = tab == 3 || tab == 5 || tab == 8
    fun back() { tab = when (tab) { 3, 8 -> 7; 5 -> settingsFrom; else -> 0 } }
    // Back from an inner page goes to where it was opened from; from a main place it goes to Today.
    androidx.activity.compose.BackHandler(enabled = tab != 0 && !triage) { back() }
    val goSettings = { sec: Int -> settingsSec = sec; settingsFrom = if (tab == 7) 7 else 0; tab = 5 }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                // Today carries its own header; the other places get a big plain title on the page color.
                if (tab != 0) TopAppBar(
                    title = { Text(titles[tab] ?: "", style = if (subPage) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium) },
                    navigationIcon = {
                        if (subPage) IconButton(onClick = { back() }) { Icon(AppIcons.ArrowBack, tr("رجوع", "Back")) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            bottomBar = { if (!subPage) BottomTabs(tab, onAdd = { adding = true }) { tab = it } },
        ) { pad ->
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(260)) { it / 24 }) togetherWith
                        fadeOut(tween(120))
                },
                modifier = Modifier.padding(pad),
                label = "tab",
            ) { t ->
                when (t) {
                    0 -> HomeScreen(db, tick, missingPerms,
                        onAskPerms = { launcher.launch(perms) },
                        onOpen = setOpenTxn,
                        onGoSettings = { goSettings(0) },
                        onGoDebts = { planSub = 1; moneySub = 1; tab = 6 },
                        onTriage = { triage = true },
                        refresh = refresh,
                        onAddCash = { adding = true },
                        onGoTxns = { tab = 2 },
                        onBalance = { homeSheet = 1 },
                        onToday = { homeSheet = 2 },
                        onGoPlan = { planSub = 0; tab = 6 })
                    2 -> TxnsScreen(db, tick, onOpen = setOpenTxn, refresh = refresh, onTriage = { triage = true })
                    6 -> Column(Modifier.fillMaxSize()) {
                        Segmented(listOf(tr("الشهر", "Month"), tr("فلوسي", "My money")), planSub) { planSub = it }
                        Box(Modifier.weight(1f)) {
                            if (planSub == 0) MonthScreen(db, tick, onOpen = setOpenTxn, refresh = refresh)
                            else MoneyScreen(db, tick, refresh, startSub = moneySub)
                        }
                    }
                    7 -> MeScreen(db, tick,
                        onCompare = { tab = 8 },
                        onReports = { reportSub = it; tab = 3 },
                        onSettings = { goSettings(it) },
                        onBalance = { homeSheet = 1 })
                    3 -> ReportsScreen(db, tick, onOpen = setOpenTxn, refresh = refresh, startSub = reportSub)
                    8 -> CompareScreen(db, tick)
                    else -> SettingsScreen(db, tick, refresh, startSection = settingsSec)
                }
            }
        }

        AnimatedVisibility(
            triage,
            enter = slideInVertically(tween(320)) { it } + fadeIn(),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(),
        ) {
            TriageScreen(db, onOpenDetails = setOpenTxn, onClose = { triage = false; refresh() })
        }

        if (onboarding) {
            OnboardingScreen(db, missingPerms, onAskPerms = { launcher.launch(perms) },
                onDone = { onboarding = false; tab = 0; refresh() })
        }
    }

    if (openTxn != null) {
        TxnDialog(db, openTxn, onDone = { setOpenTxn(null); refresh() })
    }
    when (homeSheet) {
        1 -> BalanceSheet(db) { homeSheet = 0; refresh() }
        2 -> TodaySheet(db, onOpen = { homeSheet = 0; setOpenTxn(it) }) { homeSheet = 0; refresh() }
    }
    if (adding) {
        AddDialog(db, onDone = { adding = false; refresh() })
    }
}

/** Today, Activity, the add button, Plan and Me; the current place sits in a sunny pill. */
@Composable
fun BottomTabs(selected: Int, onAdd: () -> Unit, onSelect: (Int) -> Unit) {
    val p = Ui.p
    val items = listOf(
        Triple(tr("اليوم", "Today"), AppIcons.Home, 0),
        Triple(tr("الحركات", "Activity"), AppIcons.FormatListBulleted, 2),
        null,
        Triple(tr("الخطة", "Plan"), AppIcons.CalendarMonth, 6),
        Triple(tr("أنا", "Me"), AppIcons.Person, 7),
    )
    Column(Modifier.fillMaxWidth().background(p.raised)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(p.edge))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp).height(72.dp).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                if (item == null) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.offset(y = (-14).dp).size(60.dp).sticker(p.accent, 22.dp, 4.dp)
                                .clickable(onClickLabel = tr("إضافة مصروف", "Add an expense"), role = Role.Button, onClick = onAdd),
                            contentAlignment = Alignment.Center,
                        ) { Icon(AppIcons.Add, tr("إضافة مصروف", "Add an expense"), tint = p.onAccent, modifier = Modifier.size(30.dp)) }
                    }
                    return@forEach
                }
                val (label, icon, i) = item
                val on = selected == i
                val pill by animateColorAsState(if (on) p.sun else Color.Transparent, tween(250), label = "tabBg")
                Column(
                    Modifier.weight(1f).fillMaxHeight().selectable(selected = on, role = Role.Tab) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.width(52.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(pill)
                            .then(if (on) Modifier.border(2.dp, p.edge, RoundedCornerShape(16.dp)) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, tint = if (on) p.onSun else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) }
                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.padding(top = 4.dp),
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * The lock as its own full-screen window: it covers the app and any open sheet or dialog
 * (those are windows too), and back can't dismiss it.
 */
@Composable
fun LockOverlay(onUnlock: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        LockScreen(onUnlock)
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(84.dp).sticker(Ui.p.sun, 28.dp, 4.dp), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Lock, null, tint = Ui.p.onSun, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(tr("ميزانيتي مقفلة", "My Budget is locked"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onUnlock) { Text(tr("فتح", "Unlock")) }
        }
    }
}


/** After a crash: what went wrong, to copy and send, so it can be fixed. */
@Composable
fun CrashDialog(text: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        title = { Text(tr("توقف التطبيق في المرة الماضية", "The app stopped last time")) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Text(tr("انسخ التفاصيل وأرسلها لتصليح المشكلة.", "Copy the details and send them so it can be fixed."))
                Text(text.take(1500), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("mizania", text))
                android.widget.Toast.makeText(ctx, tr("نُسخت", "Copied"), android.widget.Toast.LENGTH_SHORT).show()
            }) { Text(tr("نسخ", "Copy")) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDone) { Text(tr("إغلاق", "Close")) } },
    )
}
