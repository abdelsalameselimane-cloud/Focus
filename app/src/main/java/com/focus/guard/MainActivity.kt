package com.focus.guard

import android.Manifest
import android.content.*
import android.net.Uri
import android.net.VpnService
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
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); enableEdgeToEdge()
        Store(this).initDefaults()
        setContent { App(this) }
    }
}

class BlockActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b); enableEdgeToEdge()
        val night = intent.getStringExtra("r") == "night"; val s = Store(this)
        setContent {
            Themed(s.lang) {
                val m = ((s.blockedUntil - System.currentTimeMillis()) / 60000 + 1).coerceAtLeast(1)
                val until = if (night) String.format("%02d:00", s.nightEnd) else "$m ${t("min")}"
                Column(Modifier.fillMaxSize().systemBarsPadding().widthIn(max = 520.dp).padding(20.dp),
                    verticalArrangement = Arrangement.Center) {
                    Panel {
                        Banner(if (night) Pr else Er, Icons.Filled.Lock, t(if (night) "bTitleN" else "bTitleL"),
                            "${t(if (night) "bDescN" else "bDescL")} $until")
                        Spacer(Modifier.height(20.dp)); PrimaryBtn(t("homeBtn")) { goHome() }
                    }
                }
            }
        }
    }
    @Suppress("OVERRIDE_DEPRECATION") override fun onBackPressed() = goHome()
    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish()
    }
}

@Composable fun App(ctx: ComponentActivity) {
    val s = remember { Store(ctx) }
    var tick by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var langPref by remember { mutableStateOf(s.lang) }
    val lo = LocalLifecycleOwner.current
    DisposableEffect(lo) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lo.lifecycle.addObserver(o); onDispose { lo.lifecycle.removeObserver(o) }
    }
    LaunchedEffect(Unit) { while (true) { delay(3000); tick++ } }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) }
    val usage = remember(tick) { hasUsage(ctx) }; val over = remember(tick) { Settings.canDrawOverlays(ctx) }
    val bat = remember(tick) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }
    val ok = usage && over
    val vpn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    LaunchedEffect(ok) {
        if (ok) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, MonitorService::class.java))
            if (s.filterOn) VpnService.prepare(ctx)?.let { vpn.launch(it) }
        }
    }
    BackHandler(tab != 0) { tab = 0 }

    Themed(langPref) {
        val c = LC.current
        Column(Modifier.fillMaxSize().systemBarsPadding().widthIn(max = 520.dp)) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(Grad, R13), Alignment.Center) { Icon(Icons.Filled.Lock, null, tint = Color.White) }
                Text("Focus Guard", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = c.tx)
            }
            Box(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                if (!ok) SetupTab(ctx, usage, over, bat) else when (tab) {
                    0 -> HomeTab(s, tick)
                    1 -> AppsTab(s)
                    else -> SettingsTab(s, langPref) { langPref = it; s.lang = it }
                }
            }
            if (ok) Row(Modifier.fillMaxWidth().background(c.card).padding(vertical = 6.dp)) {
                listOf(Icons.Filled.Home to "home", Icons.Filled.List to "apps", Icons.Filled.Settings to "settings").forEachIndexed { i, (ic, k) ->
                    Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable { tab = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(ic, null, tint = if (tab == i) Pr else c.mu)
                        Text(t(k), fontSize = 12.sp, color = if (tab == i) Pr else c.mu)
                    }
                }
            }
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

@Composable fun HomeTab(s: Store, tick: Int) {
    val ctx = LocalContext.current; val now = System.currentTimeMillis()
    val used = remember(tick) { Usage.scan(ctx, s).first / 60000 }
    val night = s.nightOn && Usage.isNight(s); val paused = now < s.blockedUntil
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when {
            night -> Banner(Pr, Icons.Filled.Lock, t("nightNow"), "${t("bDescN")} ${String.format("%02d:00", s.nightEnd)}")
            paused -> Banner(Er, Icons.Filled.Warning, t("paused"), "${t("backIn")} ${(s.blockedUntil - now) / 60000 + 1} ${t("min")}")
            else -> Banner(Ok, Icons.Filled.Check, t("running"), "")
        }
        Panel {
            Title(t("used")); Spacer(Modifier.height(6.dp))
            Text("$used / ${s.limit} ${t("min")}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = LC.current.tx)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { (used.toFloat() / s.limit).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(8.dp), color = Pr, trackColor = Pr.copy(.12f))
        }
    }
}

@Composable fun AppsTab(s: Store) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var mon by remember { mutableStateOf(s.monitored) }
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
            items(list, key = { it.first }) { (pkg, label) ->
                Panel {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Tile(label.take(1).uppercase())
                        Column(Modifier.weight(1f)) { Title(label); if (pkg in SOCIAL) Sub(t("suggested")) }
                        Switch(pkg in mon, { on -> mon = if (on) mon + pkg else mon - pkg; s.monitored = mon })
                    }
                }
            }
        }
    }
}

@Composable fun SettingsTab(s: Store, lang: String, onLang: (String) -> Unit) {
    var limit by remember { mutableFloatStateOf(s.limit.toFloat()) }
    var block by remember { mutableFloatStateOf(s.blockMin.toFloat()) }
    var nOn by remember { mutableStateOf(s.nightOn) }
    var nEnd by remember { mutableFloatStateOf(s.nightEnd.toFloat()) }
    val locked = Usage.isNight(s) && s.nightOn
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Panel {
            Title(t("limit")); Sub(t("limitD")); Text("${limit.toInt()} ${t("min")}", color = Pr, fontWeight = FontWeight.ExtraBold)
            Slider(limit, { limit = it; s.limit = it.toInt() }, valueRange = 15f..90f, steps = 14)
        }
        Panel {
            Title(t("blockFor")); Sub(t("blockForD")); Text("${block.toInt()} ${t("min")}", color = Pr, fontWeight = FontWeight.ExtraBold)
            Slider(block, { block = it; s.blockMin = it.toInt() }, valueRange = 15f..180f, steps = 10)
        }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Title(t("night")); Sub(if (locked) t("locked") else t("nightD")) }
                Switch(nOn, { nOn = it; s.nightOn = it }, enabled = !locked)
            }
            if (nOn) {
                Spacer(Modifier.height(10.dp)); Sub("${t("nightEnd")} ${String.format("%02d:00", nEnd.toInt())}")
                Slider(nEnd, { nEnd = it; s.nightEnd = it.toInt() }, valueRange = 1f..12f, steps = 10, enabled = !locked)
            }
        }
        Panel {
            Title(t("lang")); Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("auto" to t("auto"), "ar" to "العربية", "en" to "English", "fr" to "Français").forEach { (k, n) ->
                    val on = lang == k
                    Box(Modifier.heightIn(min = 40.dp).background(if (on) Pr.copy(.12f) else Color.Transparent, R14)
                        .border(1.dp, if (on) Pr else LC.current.bd, R14).clickable { onLang(k) }.padding(horizontal = 12.dp), Alignment.Center) {
                        Text(n, fontSize = 13.sp, color = if (on) Pr else LC.current.tx)
                    }
                }
            }
        }
        var taps by remember { mutableIntStateOf(0) }
        Text("v1.0", fontSize = 12.sp, color = LC.current.mu, modifier = Modifier.align(Alignment.CenterHorizontally).clickable { taps++ }.padding(12.dp))
        if (taps >= 7) AdvPanel(s)
    }
}

@Composable fun AdvPanel(s: Store) {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var on by remember { mutableStateOf(s.filterOn) }
    var req by remember { mutableLongStateOf(s.offReqAt) }
    val wait = 30 * 60_000L; val win = 10 * 60_000L
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            if (req != 0L && now > req + wait + win) { req = 0; s.offReqAt = 0 }
            delay(1000)
        }
    }
    Panel {
        Title(t("adv")); Sub(t(if (on) "protOn" else "protOff")); Spacer(Modifier.height(10.dp))
        when {
            !on -> PrimaryBtn(t("turnOn")) { s.filterOn = true; on = true }
            req == 0L -> PrimaryBtn(t("reqOff")) { req = now; s.offReqAt = now }
            now < req + wait -> {
                Sub("${t("wait")} ${(req + wait - now) / 60000 + 1} ${t("min")}"); Spacer(Modifier.height(10.dp))
                PrimaryBtn(t("cancel")) { req = 0; s.offReqAt = 0 }
            }
            else -> PrimaryBtn(t("confirmOff")) {
                s.filterOn = false; on = false; req = 0; s.offReqAt = 0
                ctx.stopService(Intent(ctx, FilterService::class.java))
            }
        }
    }
}

fun openFirst(ctx: Context, intents: List<Intent>) {
    for (i in intents) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) {}
    }
}
