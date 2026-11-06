package com.focus.guard

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); enableEdgeToEdge()
        Store(this).initDefaults()
        setContent { App(this) }
    }
}

class BlockActivity : ComponentActivity() {
    private var reason by mutableStateOf("limit")
    private var until by mutableLongStateOf(0L)

    private fun read(i: Intent?) {
        reason = i?.getStringExtra("r") ?: "limit"
        until = i?.getLongExtra("u", 0L) ?: 0L
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); enableEdgeToEdge(); read(intent)
        val s = Store(this); val pick = (System.currentTimeMillis() % 1000).toInt()
        setContent {
            Themed(s.lang) {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
                val soft = reason == "night" || reason == "sched" || reason == "focus"
                val title = when (reason) {
                    "night" -> "bTitleN"; "app" -> "bTitleA"; "sched" -> "bTitleS"; "focus" -> "bTitleF"; else -> "bTitleL"
                }
                val left = (until - now).coerceAtLeast(0L) / 1000
                val endAt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(until))
                Column(Modifier.fillMaxSize().systemBarsPadding().widthIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.Center) {
                    Panel {
                        Banner(if (soft) Pr else Er, Icons.Filled.Lock, t(title), if (until > 0) "${t("bUntil")} $endAt" else "")
                        if (until > 0) {
                            Spacer(Modifier.height(16.dp))
                            Text(String.format("%d:%02d:%02d", left / 3600, left % 3600 / 60, left % 60), fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold, color = LC.current.tx, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                        Spacer(Modifier.height(16.dp)); Sub(t("q${pick % 3 + 1}"))
                        Spacer(Modifier.height(10.dp)); Sub("${t("bTip")}: ${t("a${pick % 5 + 1}")}")
                        Spacer(Modifier.height(20.dp)); PrimaryBtn(t("homeBtn")) { goHome() }
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); read(intent) }
    @Suppress("OVERRIDE_DEPRECATION") override fun onBackPressed() = goHome()
    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish()
    }
}

@Composable fun App(ctx: ComponentActivity) {
    val s = remember { Store(ctx) }
    val ver = Ver.n
    var tick by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var langPref by remember { mutableStateOf(s.lang) }
    val lo = LocalLifecycleOwner.current
    DisposableEffect(lo) {
        val o = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) tick++
            if (e == Lifecycle.Event.ON_STOP) Sess.unlocked = false
        }
        lo.lifecycle.addObserver(o); onDispose { lo.lifecycle.removeObserver(o) }
    }
    LaunchedEffect(Unit) { while (true) { delay(3000); tick++ } }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) }
    val usage = remember(tick) { hasUsage(ctx) }; val over = remember(tick) { Settings.canDrawOverlays(ctx) }
    val bat = remember(tick) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }
    val ok = usage && over
    LaunchedEffect(ok) {
        if (ok) ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))
    }
    BackHandler(tab != 0) { tab = 0 }
    val locked = ok && s.hasPin() && !Sess.unlocked

    Themed(langPref) {
        val c = LC.current
        Column(Modifier.fillMaxSize().systemBarsPadding().widthIn(max = 520.dp)) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(Grad, R13), Alignment.Center) { Icon(Icons.Filled.Lock, null, tint = Color.White) }
                Text("Focus Guard", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = c.tx)
            }
            Box(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                when {
                    !ok -> SetupTab(ctx, usage, over, bat)
                    locked -> PinScreen(s)
                    else -> when (tab) {
                        0 -> HomeTab(s, tick)
                        1 -> StatsTab(s)
                        2 -> AppsTab(s)
                        else -> SettingsTab(s, langPref, tick) { langPref = it; s.lang = it }
                    }
                }
            }
            if (ok && !locked) Row(Modifier.fillMaxWidth().background(c.card).padding(vertical = 6.dp)) {
                listOf(Icons.Filled.Home to "home", Icons.Filled.Info to "stats", Icons.Filled.List to "apps", Icons.Filled.Settings to "settings").forEachIndexed { i, (ic, k) ->
                    Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable { tab = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(ic, null, tint = if (tab == i) Pr else c.mu)
                        Text(t(k), fontSize = 12.sp, color = if (tab == i) Pr else c.mu)
                    }
                }
            }
        }
    }
}

@Composable fun PinScreen(s: Store) {
    var txt by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Panel {
            Title(t("pin")); Spacer(Modifier.height(10.dp))
            OutlinedTextField(txt, { if (it.length <= 8 && it.all { ch -> ch.isDigit() }) txt = it; err = false },
                singleLine = true, label = { Text(t("pinEnter")) }, isError = err,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
            if (err) Sub(t("pinWrong"))
            Spacer(Modifier.height(12.dp))
            PrimaryBtn(t("unlock")) { if (s.checkPin(txt)) { Sess.unlocked = true; txt = "" } else err = true }
        }
    }
}

@Composable fun SetupTab(ctx: Context, usage: Boolean, over: Boolean, bat: Boolean) {
    val pkg = Uri.parse("package:" + ctx.packageName)
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Panel { Title(t("setup")); Sub(t("setupD")) }
        @Composable fun step(ch: String, k: String, on: Boolean, intents: List<Intent>) = Panel {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Tile(ch); Column(Modifier.weight(1f)) { Title(t(k)); Sub(t(k + "D")) }
            }
            Spacer(Modifier.height(14.dp))
            if (on) Banner(Ok, Icons.Filled.Check, t("done"), "") else PrimaryBtn(t("grant")) { openFirst(ctx, intents) }
        }
        step("1", "pUsage", usage, listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkg),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)))
        step("2", "pOver", over, listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)))
        step("3", "pBat", bat, listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)))
    }
}

// ---------------------------------------------------------------- Home

@Composable fun HomeTab(s: Store, tick: Int) {
    val ver = Ver.n
    val ctx = LocalContext.current; val now = System.currentTimeMillis()
    val used = remember(tick) { Usage.scan(ctx, s).total / 60000 }
    val night = s.nightOn && Usage.isNight(s); val paused = now < s.blockedUntil
    val focus = now < s.focusUntil
    val sch = s.schedules.firstOrNull { it.activeAt(Calendar.getInstance()) }
    var fm by remember { mutableIntStateOf(25) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when {
            night -> Banner(Pr, Icons.Filled.Lock, t("nightNow"), "${t("bDescN")} ${String.format("%02d:00", s.nightEnd)}")
            focus -> Banner(Pr, Icons.Filled.Lock, t("focusNow"), "${t("left")} ${(s.focusUntil - now) / 60000 + 1} ${t("min")}")
            sch != null -> Banner(Pr, Icons.Filled.Lock, t("scheduleNow"), "${t("bUntil")} ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sch.endsAt(now)))}")
            paused -> Banner(Er, Icons.Filled.Warning, t("paused"), "${t("backIn")} ${(s.blockedUntil - now) / 60000 + 1} ${t("min")}")
            else -> Banner(Ok, Icons.Filled.Check, t("running"), "")
        }
        Panel {
            Title(t("used")); Spacer(Modifier.height(6.dp))
            Text("$used / ${s.limit} ${t("min")}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = LC.current.tx)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { (used.toFloat() / s.limit).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(8.dp), color = Pr, trackColor = Pr.copy(.12f))
        }
        Panel {
            Title(t("streak")); Spacer(Modifier.height(6.dp))
            Text("${s.streak()} ${t("days")}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ok)
        }
        Panel {
            Title(t("focus")); Sub(t("focusD")); Spacer(Modifier.height(12.dp))
            if (focus) Banner(Pr, Icons.Filled.Lock, t("focusNow"), "${t("left")} ${(s.focusUntil - now) / 60000 + 1} ${t("min")}")
            else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(15, 25, 45, 60).forEach { m -> Chip("$m ${t("min")}", fm == m, Modifier.weight(1f)) { fm = m } }
                }
                Spacer(Modifier.height(12.dp))
                PrimaryBtn(t("start")) {
                    s.focusUntil = System.currentTimeMillis() + fm * 60_000L; s.focusNote = true; Ver.n++
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Stats

fun appLabel(ctx: Context, pkg: String): String = try {
    ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
} catch (_: Exception) { pkg }

fun hhmm(m: Int) = String.format("%02d:%02d", m / 60, m % 60)

@Composable fun StatsTab(s: Store) {
    val ctx = LocalContext.current; val lang = LL.current
    var week by remember { mutableStateOf<Week?>(null) }
    LaunchedEffect(Unit) { week = withContext(Dispatchers.Default) { try { Usage.week(ctx, s) } catch (_: Exception) { null } } }
    val w = week
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (w == null) { Sub(t("loading")); return@Column }
        val mins = w.days.map { it / 60000 }
        val total = mins.sum(); val max = maxOf(mins.maxOrNull() ?: 0L, 1L)
        val fmt = SimpleDateFormat("EEE", Locale(lang))
        val cal = Calendar.getInstance()
        val labels = (0..6).map { i -> cal.timeInMillis = w.startMs; cal.add(Calendar.DAY_OF_YEAR, i); fmt.format(cal.time) }
        Panel {
            Title(t("statsT")); Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                mins.forEachIndexed { i, m ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text("$m", fontSize = 10.sp, color = LC.current.mu)
                        Box(Modifier.fillMaxWidth().height((100f * m / max).coerceAtLeast(3f).dp).background(if (i == 6) Pr else Pr.copy(.45f), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
                        Text(labels[i], fontSize = 10.sp, maxLines = 1, color = LC.current.mu)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Sub("${t("total")}: $total ${t("min")}  ·  ${t("avg")}: ${total / 7} ${t("min")}")
        }
        Panel {
            Title(t("topApps")); Spacer(Modifier.height(8.dp))
            if (w.top.isEmpty()) Sub(t("noData"))
            w.top.take(6).forEach { (pkg, ms) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(appLabel(ctx, pkg), color = LC.current.tx, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${ms / 60000} ${t("min")}", color = Pr, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        val title = t("report"); val minTxt = t("min"); val totalTxt = t("total"); val avgTxt = t("avg"); val streakTxt = t("streak"); val daysTxt = t("days")
        PrimaryBtn(t("share")) {
            val df = SimpleDateFormat("EEE dd/MM", Locale(lang)); val c2 = Calendar.getInstance()
            val body = buildString {
                append("$title\n")
                mins.forEachIndexed { i, m -> c2.timeInMillis = w.startMs; c2.add(Calendar.DAY_OF_YEAR, i); append("${df.format(c2.time)}: $m $minTxt\n") }
                append("$totalTxt: $total $minTxt · $avgTxt: ${total / 7} $minTxt\n$streakTxt: ${s.streak()} $daysTxt\n")
                w.top.take(5).forEach { (pkg, ms) -> append("• ${appLabel(ctx, pkg)}: ${ms / 60000} $minTxt\n") }
            }
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body)
            ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

// ---------------------------------------------------------------- Apps

val LIMITS = listOf(0, 15, 30, 45, 60, 90, 120, 180)
@Composable fun limText(m: Int) = if (m == 0) t("noLimit") else "$m ${t("min")}"

@Composable fun AppsTab(s: Store) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) {
            val pm = ctx.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .filter { it.first != ctx.packageName }.distinctBy { it.first }
                .sortedWith(compareBy({ it.first !in SOCIAL }, { it.second.lowercase() }))
        }
    }
    val list = apps
    when {
        list == null -> Sub(t("loading"))
        list.isEmpty() -> Panel { Title(t("empty")) }
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Sub(t("appsD")) }
            items(list, key = { it.first }) { (pkg, label) -> AppRow(s, pkg, label) }
        }
    }
}

@Composable fun AppRow(s: Store, pkg: String, label: String) {
    val ver = Ver.n
    val on = pkg in s.monitored
    val pend = s.pending
    val offPending = pend.any { it.kind == "app" && it.arg == pkg }
    val cur = s.perLimits[pkg] ?: 0
    val shown = pend.firstOrNull { it.kind == "plimit" && it.arg == pkg }?.value?.toIntOrNull() ?: cur
    fun step(d: Int) {
        val i = LIMITS.indexOf(shown).coerceAtLeast(0)
        s.request("plimit", pkg, LIMITS[(i + d).coerceIn(0, LIMITS.size - 1)].toString()); Ver.n++
    }
    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Tile(label.take(1).uppercase())
            Column(Modifier.weight(1f)) {
                Title(label)
                if (offPending) Sub(t("pendShort")) else if (pkg in SOCIAL) Sub(t("suggested"))
            }
            Switch(on, { want -> s.request("app", pkg, if (want) "1" else "0"); Ver.n++ })
        }
        if (on) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Sub(t("appLimit")); if (shown != cur) Sub(t("pendShort")) }
                Stepper(limText(shown), true, { step(-1) }, { step(1) })
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable fun SettingStepper(s: Store, kind: String, cur: Int, min: Int, max: Int, by: Int, enabled: Boolean = true, fmt: @Composable (Int) -> String) {
    val ver = Ver.n
    val shown = s.pending.firstOrNull { it.kind == kind }?.value?.toIntOrNull() ?: cur
    fun go(d: Int) { s.request(kind, "", (shown + d * by).coerceIn(min, max).toString()); Ver.n++ }
    Column {
        Stepper(fmt(shown), enabled, { go(-1) }, { go(1) })
        if (shown != cur) Sub(t("pendShort"))
    }
}

@Composable fun SettingsTab(s: Store, lang: String, tick: Int, onLang: (String) -> Unit) {
    val ver = Ver.n
    val ctx = LocalContext.current
    val locked = Usage.isNight(s) && s.nightOn
    val now = System.currentTimeMillis()
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PendingPanel(ctx, s, now)
        Panel {
            Title(t("limit")); Sub(t("limitD")); Spacer(Modifier.height(10.dp))
            SettingStepper(s, "limit", s.limit, 15, 90, 5) { "$it ${t("min")}" }
        }
        Panel {
            Title(t("blockFor")); Sub(t("blockForD")); Spacer(Modifier.height(10.dp))
            SettingStepper(s, "block", s.blockMin, 15, 180, 15) { "$it ${t("min")}" }
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Title(t("night")); Sub(if (locked) t("locked") else t("nightD")) }
                Switch(s.nightOn, { s.request("night", "", if (it) "1" else "0"); Ver.n++ }, enabled = !locked)
            }
            if (s.nightOn) {
                Spacer(Modifier.height(10.dp)); Sub(t("nightEnd")); Spacer(Modifier.height(6.dp))
                SettingStepper(s, "nend", s.nightEnd, 1, 12, 1, !locked) { String.format("%02d:00", it) }
            }
        }
        SchedPanel(s)
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Title(t("remind")); Sub(t("remindD")) }
                Switch(s.remind, { s.remind = it; if (it) s.lastRemind = System.currentTimeMillis(); Ver.n++ })
            }
        }
        PinPanel(s)
        AdminPanel(ctx, tick)
        Panel {
            Title(t("lang")); Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("auto" to t("auto"), "ar" to "العربية", "en" to "English", "fr" to "Français").forEach { (k, n) ->
                    Chip(n, lang == k) { onLang(k) }
                }
            }
        }
        Text("v2.0", fontSize = 12.sp, color = LC.current.mu, modifier = Modifier.align(Alignment.CenterHorizontally).padding(12.dp))
    }
}

@Composable fun pendLabel(ctx: Context, s: Store, p: Pend): String = when (p.kind) {
    "limit" -> "${t("limit")}: ${p.value} ${t("min")}"
    "block" -> "${t("blockFor")}: ${p.value} ${t("min")}"
    "night" -> "${t("night")}: ${t("off")}"
    "nend" -> "${t("nightEnd")} ${String.format("%02d:00", p.value.toInt())}"
    "app" -> "${appLabel(ctx, p.arg)}: ${t("off")}"
    "plimit" -> "${appLabel(ctx, p.arg)}: ${limText(p.value.toInt())}"
    "sched" -> "${t("sched")}: " + (s.schedules.firstOrNull { it.id == p.arg }?.let { hhmm(it.start) + "–" + hhmm(it.end) } ?: "")
    else -> p.kind
}

@Composable fun PendingPanel(ctx: Context, s: Store, now: Long) {
    val ver = Ver.n
    val list = s.pending
    if (list.isEmpty()) return
    Panel {
        Title(t("pending")); Sub(t("pendD"))
        list.forEach { p ->
            Spacer(Modifier.height(12.dp))
            Text(pendLabel(ctx, s, p), color = LC.current.tx, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            if (now >= p.readyAt) PrimaryBtn(t("applyNow")) { s.applyPending(p); Ver.n++ }
            else Sub("${t("wait")} ${(p.readyAt - now) / 60000 + 1} ${t("min")}")
            Spacer(Modifier.height(6.dp))
            Chip(t("cancel"), false) { s.dropPending(p); Ver.n++ }
        }
    }
}

fun dayShort(lang: String, i: Int): String {
    val c = Calendar.getInstance(); c.set(Calendar.DAY_OF_WEEK, i + 1)
    return SimpleDateFormat("EEE", Locale(lang)).format(c.time)
}

@Composable fun SchedPanel(s: Store) {
    val ver = Ver.n
    val lang = LL.current
    var st by remember { mutableIntStateOf(8 * 60) }
    var en by remember { mutableIntStateOf(12 * 60) }
    var days by remember { mutableIntStateOf(127) }
    val list = s.schedules
    Panel {
        Title(t("sched")); Sub(t("schedD"))
        if (list.isEmpty()) { Spacer(Modifier.height(8.dp)); Sub(t("noSched")) }
        list.forEach { sc ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(hhmm(sc.start) + " – " + hhmm(sc.end), color = LC.current.tx, fontWeight = FontWeight.ExtraBold)
                    Sub(if (sc.days == 127) t("everyDay") else (0..6).filter { ((sc.days shr it) and 1) == 1 }.joinToString(" ") { dayShort(lang, it) })
                }
                SmallBtn("✕") { s.request("sched", sc.id, "del"); Ver.n++ }
            }
        }
        Spacer(Modifier.height(16.dp)); Title(t("addSched")); Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Sub(t("from")); Spacer(Modifier.weight(1f))
            Stepper(hhmm(st), true, { st = (st - 15 + 1440) % 1440 }, { st = (st + 15) % 1440 })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Sub(t("to")); Spacer(Modifier.weight(1f))
            Stepper(hhmm(en), true, { en = (en - 15 + 1440) % 1440 }, { en = (en + 15) % 1440 })
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0..6).forEach { i -> Chip(dayShort(lang, i), ((days shr i) and 1) == 1, Modifier.weight(1f), 11) { days = days xor (1 shl i) } }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryBtn(t("addSched")) {
            if (st != en && days != 0) {
                s.schedules = s.schedules + Sched(System.currentTimeMillis().toString(), days, st, en); Ver.n++
            }
        }
    }
}

@Composable fun PinPanel(s: Store) {
    val ver = Ver.n
    var txt by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    val has = s.hasPin()
    Panel {
        Title(t("pin")); Sub(t("pinD")); Spacer(Modifier.height(10.dp))
        OutlinedTextField(txt, { if (it.length <= 8 && it.all { ch -> ch.isDigit() }) txt = it; err = false },
            singleLine = true, label = { Text(t(if (has) "pinEnter" else "pinHint")) }, isError = err,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
        if (err) Sub(t("pinWrong"))
        Spacer(Modifier.height(10.dp))
        if (!has) PrimaryBtn(t("pinSet")) {
            if (txt.length >= 4) { s.setPin(txt); Sess.unlocked = true; txt = ""; Ver.n++ } else err = true
        } else PrimaryBtn(t("pinRemove")) {
            if (s.checkPin(txt)) { s.clearPin(); txt = ""; Ver.n++ } else err = true
        }
    }
}

@Composable fun AdminPanel(ctx: Context, tick: Int) {
    val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val cn = ComponentName(ctx, AdminReceiver::class.java)
    val active = remember(tick) { dpm.isAdminActive(cn) }
    val expl = t("adminD")
    Panel {
        Title(t("admin")); Sub(t("adminD")); Spacer(Modifier.height(10.dp))
        if (active) Banner(Ok, Icons.Filled.Check, t("adminOn"), "")
        else PrimaryBtn(t("enable")) {
            openFirst(ctx, listOf(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, expl)))
        }
    }
}

fun openFirst(ctx: Context, intents: List<Intent>) {
    for (i in intents) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) {}
    }
}
