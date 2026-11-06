package com.focus.guard

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Weekly commitment: the person sets their rules, locks them for 7 days (they can only get stricter),
 * and when the week ends a report shows what was gained.
 */
object Commit {
    private const val SNAP_EVERY = 10 * 60_000L

    /**
     * Called regularly by the service (and by the app when it opens after the week). Keeps a running snapshot of
     * the week's usage and, once the week is over, finalizes it. Returns true exactly once, when the report becomes ready.
     */
    fun tick(c: Context, s: Store, now: Long): Boolean {
        if (s.cStart == 0L) return false
        if (!s.ended(now)) {
            if (now - s.cSnap >= SNAP_EVERY) snapshot(c, s, now)
            return false
        }
        if (s.cDone) return false
        snapshot(c, s, now)
        s.cDone = true
        s.weeksDone += 1
        return true
    }

    /** Recomputes the week's per-day usage; keeps the larger of old/new so data that aged out of the system log isn't lost. */
    private fun snapshot(c: Context, s: Store, now: Long) {
        if (!hasUsage(c)) return
        s.cSnap = now
        val w = try { Usage.buckets(c, s.cMon, s.cStart, 7) } catch (_: Exception) { return }
        val old = s.cDays
        s.cDays = w.days.mapIndexed { i, v -> maxOf(v, old.getOrElse(i) { 0L }) }
        val oldTop = s.cTop.toMap(); val newTop = w.top.toMap()
        s.cTop = (oldTop.keys + newTop.keys).map { it to maxOf(newTop[it] ?: 0L, oldTop[it] ?: 0L) }.sortedByDescending { it.second }
    }

    /** Time saved compared with the person's own average before the commitment (ms, never negative). */
    fun saved(s: Store): Long = if (s.cBase > 0) maxOf(0L, s.cBase * 7 - s.cDays.sum()) else 0L

    /** (number of prayer pauses in the week, total minutes of undisturbed prayer) */
    fun prayerStats(s: Store): Pair<Int, Int> {
        if (!s.cPrayer) return 0 to 0
        var n = 0; var m = 0
        val cal = Calendar.getInstance()
        for (i in 0..7) {
            cal.timeInMillis = s.cStart; cal.add(Calendar.DAY_OF_YEAR, i)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            Prayer.slots(s, cal.timeInMillis).forEach {
                if (it.at >= s.cStart && it.at < s.cEnd) { n++; m += Prayer.DUR[it.key] ?: 0 }
            }
        }
        return n to m
    }
}

// ---------------------------------------------------------------- UI

@Composable private fun KV(k: String, v: String) =
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = LC.current.mu, modifier = Modifier.weight(1f))
        Text(v, color = LC.current.tx, fontWeight = FontWeight.ExtraBold)
    }

@Composable private fun StatBox(mod: Modifier, label: String, value: String, sub: String = "") =
    Column(mod.background(LC.current.card, R20).border(1.dp, LC.current.bd, R20).padding(14.dp)) {
        Sub(label); Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = LC.current.tx)
        if (sub.isNotEmpty()) Sub(sub)
    }

@Composable fun CommitTab(s: Store, tick: Int) {
    val ver = Ver.n
    val ctx = LocalContext.current
    val now = System.currentTimeMillis()
    LaunchedEffect(tick, s.cStart) {
        if (s.ended(now) && !s.cDone) {
            val fin = withContext(Dispatchers.Default) { Commit.tick(ctx, s, System.currentTimeMillis()) }
            if (fin) Ver.n++
        }
    }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when {
            s.cStart == 0L -> CommitSetup(s)
            s.locked(now) -> CommitActive(s, now)
            else -> CommitReport(s)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable private fun CommitSetup(s: Store) {
    val ctx = LocalContext.current; val lang = LL.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    Panel { Title(t("commitT")); Spacer(Modifier.height(4.dp)); Sub(t("commitD")) }
    if (s.weeksDone > 0) Banner(Ok, Icons.Filled.Star, t("weeksDone"), "${s.weeksDone}")
    Panel {
        Title(t("willLock")); Spacer(Modifier.height(6.dp))
        KV(t("limit"), fmtMin(lang, s.limit.toLong()))
        KV(t("blockFor"), fmtMin(lang, s.blockMin.toLong()))
        KV(t("night"), if (s.nightOn) "${t("on")} · ${String.format("%02d:00", s.nightEnd)}" else t("off"))
        KV(t("monApps"), "${s.monitored.size}")
        KV(t("sched"), "${s.schedules.size}")
        KV(t("prayer"), if (s.prayerOn) t("on") else t("off"))
        Spacer(Modifier.height(8.dp)); Sub(t("rulesNote"))
    }
    if (s.monitored.isEmpty()) { Banner(Er, Icons.Filled.Warning, t("commitNeedApps"), ""); return }
    if (!confirm) PrimaryBtn(t("commitStart")) { confirm = true }
    else Panel {
        Banner(Er, Icons.Filled.Warning, t("commitSure"), t("commitSureD"))
        Spacer(Modifier.height(12.dp))
        PrimaryBtn(t("confirmYes")) {
            if (!busy) {
                busy = true
                scope.launch {
                    val base = withContext(Dispatchers.Default) { try { Usage.week(ctx, s).days.take(6).sum() / 6 } catch (_: Exception) { 0L } }
                    s.startCommit(base); busy = false; confirm = false; Ver.n++
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Chip(t("cancel"), false, Modifier.fillMaxWidth()) { confirm = false }
    }
}

@Composable private fun CommitActive(s: Store, now: Long) {
    val lang = LL.current
    val elapsed = now - s.cStart; val remain = s.cEnd - now
    val dayNo = (elapsed / DAY + 1).toInt().coerceIn(1, 7)
    val used = s.cDays.sum()
    val savedSoFar = if (s.cBase > 0) maxOf(0L, (s.cBase * elapsed / DAY) - used) else 0L
    Banner(Pr, Icons.Filled.Lock, t("commitOn"), "${t("left")} ${remain / DAY} ${t("days")} ${remain % DAY / 3_600_000L} ${t("hr")}")
    Panel {
        Title("${t("dayOf")} $dayNo / 7"); Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(progress = { (elapsed.toFloat() / (7 * DAY)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(8.dp), color = Pr, trackColor = Pr.copy(.12f))
        Spacer(Modifier.height(10.dp))
        Sub("${t("lockedUntil")} ${SimpleDateFormat("EEE dd/MM HH:mm", Locale(lang)).format(Date(s.cEnd))}")
    }
    if (savedSoFar > 0) Panel {
        Title(t("savedSoFar")); Spacer(Modifier.height(6.dp))
        Text(fmtMin(lang, savedSoFar / 60_000L), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ok)
        Sub(t("keepGoing"))
    }
    Panel {
        Title(t("willLock")); Spacer(Modifier.height(6.dp))
        KV(t("limit"), fmtMin(lang, s.limit.toLong()))
        KV(t("blockFor"), fmtMin(lang, s.blockMin.toLong()))
        KV(t("night"), if (s.nightOn) "${t("on")} · ${String.format("%02d:00", s.nightEnd)}" else t("off"))
        KV(t("monApps"), "${s.monitored.size}")
        KV(t("sched"), "${s.schedules.size}")
        KV(t("prayer"), if (s.prayerOn) t("on") else t("off"))
        KV(t("focusDone"), "${s.cFocusN}")
        Spacer(Modifier.height(8.dp)); Sub(t("rulesNote"))
    }
}

@Composable private fun CommitReport(s: Store) {
    val ctx = LocalContext.current; val lang = LL.current
    val days = s.cDays.let { d -> List(7) { d.getOrElse(it) { 0L } } }
    val mins = days.map { it / 60_000L }
    val total = mins.sum()
    val baseDay = s.cBase / 60_000L
    val saved = Commit.saved(s) / 60_000L
    val pct = if (s.cBase > 0) ((days.sum() - s.cBase * 7) * 100 / (s.cBase * 7)).toInt() else null
    val clean = 7 - s.commitBreachDays()
    val (prN, prMin) = Commit.prayerStats(s)
    val bestIdx = mins.indices.minByOrNull { mins[it] } ?: 0
    val yearDays = saved * 52 / 60.0 / 24.0
    val pages = saved / 2
    val dayFmt = SimpleDateFormat("EEE", Locale(lang))
    val labels = (0..6).map { dayFmt.format(Date(s.cStart + it * DAY)) }
    val tier = when { pct != null && pct <= -40 -> "rep1"; pct != null && pct <= -10 -> "rep2"; else -> "rep3" }

    Banner(Ok, Icons.Filled.Check, t("repDone"), t("repT"))
    Box(Modifier.fillMaxWidth().background(Grad, R20).padding(20.dp)) {
        Column {
            Text(t("timeSaved"), color = Color.White.copy(.85f), fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            if (saved > 0) {
                Text(fmtMin(lang, saved), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text(t("vsBefore"), color = Color.White.copy(.85f), fontSize = 13.sp)
            } else {
                Text(t(if (s.cBase > 0) "noSaved" else "noBase"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Panel { Sub(t(tier)) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatBox(Modifier.weight(1f), t("avg"), fmtMin(lang, total / 7), if (baseDay > 0) "${t("before")}: ${fmtMin(lang, baseDay)}" else "")
        StatBox(Modifier.weight(1f), t("cleanDays"), "$clean / 7")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatBox(Modifier.weight(1f), t("focusDone"), "${s.cFocusN}", if (s.cFocusMin > 0) fmtMin(lang, s.cFocusMin.toLong()) else "")
        StatBox(Modifier.weight(1f), t("prayersKept"), "$prN", if (prMin > 0) fmtMin(lang, prMin.toLong()) else "")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatBox(Modifier.weight(1f), t("bestDay"), fmtMin(lang, mins[bestIdx]), labels[bestIdx])
        StatBox(Modifier.weight(1f), t("change"), if (pct != null) "${if (pct > 0) "+" else ""}$pct%" else "—")
    }
    Panel {
        Title(t("statsT")); Spacer(Modifier.height(14.dp))
        val max = maxOf(mins.maxOrNull() ?: 0L, 1L)
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            mins.forEachIndexed { i, m ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Text(fmtShort(m), fontSize = 10.sp, color = LC.current.mu)
                    Box(Modifier.fillMaxWidth().height((100f * m / max).coerceAtLeast(3f).dp)
                        .background(if (i == bestIdx) Ok else Pr.copy(.55f), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
                    Text(labels[i], fontSize = 10.sp, maxLines = 1, color = LC.current.mu)
                }
            }
        }
    }
    if (saved > 0) Panel {
        Title(t("yearProj")); Spacer(Modifier.height(6.dp))
        Sub(t("yearProjB").replace("%s", String.format(Locale.US, "%.1f", yearDays)))
        if (pages > 0) Sub(t("pagesEq").replace("%s", pages.toString()))
    }
    val top = s.cTop.take(5)
    if (top.isNotEmpty()) Panel {
        Title(t("topApps")); Spacer(Modifier.height(8.dp))
        top.forEach { (pkg, ms) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(appLabel(ctx, pkg), color = LC.current.tx, modifier = Modifier.weight(1f), maxLines = 1)
                Text(fmtMin(lang, ms / 60_000L), color = Pr, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
    if (s.cBroken) Banner(Er, Icons.Filled.Warning, t("protBroken"), "")

    val title = t("commitRepT"); val minT = t("min")
    PrimaryBtn(t("share")) {
        val body = buildString {
            append("$title\n")
            if (saved > 0) append("${tl(lang, "timeSaved")}: ${fmtMin(lang, saved)}\n")
            append("${tl(lang, "avg")}: ${fmtMin(lang, total / 7)}")
            if (baseDay > 0) append(" (${tl(lang, "before")}: ${fmtMin(lang, baseDay)})")
            append("\n${tl(lang, "cleanDays")}: $clean / 7\n${tl(lang, "focusDone")}: ${s.cFocusN}\n")
            if (prN > 0) append("${tl(lang, "prayersKept")}: $prN\n")
        }
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body), title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    Chip(t("newWeek"), true, Modifier.fillMaxWidth()) { s.clearCommit(); Ver.n++ }
}
