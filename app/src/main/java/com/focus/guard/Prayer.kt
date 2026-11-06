package com.focus.guard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Prayer mode: for a few minutes after each adhan every non-system app is paused. */
object Prayer {
    val ORDER = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
    /** Minutes the apps stay paused after each adhan. */
    val DUR = mapOf("fajr" to 5, "dhuhr" to 10, "asr" to 10, "maghrib" to 5, "isha" to 15)

    class City(val ar: String, val en: String, val fr: String, val lat: Double, val lng: Double) {
        fun name(lang: String) = when (lang) { "ar" -> ar; "fr" -> fr; else -> en }
    }
    val CITIES = listOf(
        City("الجزائر", "Algiers", "Alger", 36.7538, 3.0588), City("وهران", "Oran", "Oran", 35.6971, -0.6308),
        City("قسنطينة", "Constantine", "Constantine", 36.365, 6.6147), City("عنابة", "Annaba", "Annaba", 36.9, 7.7667),
        City("سطيف", "Setif", "Sétif", 36.19, 5.41), City("ورقلة", "Ouargla", "Ouargla", 31.95, 5.3333),
        City("مكة", "Makkah", "La Mecque", 21.4225, 39.8262), City("المدينة", "Madinah", "Médine", 24.4686, 39.6142),
        City("القاهرة", "Cairo", "Le Caire", 30.0444, 31.2357), City("الدار البيضاء", "Casablanca", "Casablanca", 33.5731, -7.5898),
        City("تونس", "Tunis", "Tunis", 36.8065, 10.1815), City("باريس", "Paris", "Paris", 48.8566, 2.3522)
    )

    class Slot(val key: String, val at: Long) {
        val end: Long get() = at + (DUR[key] ?: 10) * 60_000L
    }

    private var ck = ""
    private var cv: List<Slot> = emptyList()

    /** The five prayers of the local day that starts at [dayMid]. */
    fun slots(s: Store, dayMid: Long): List<Slot> {
        val k = "$dayMid|${s.lat}|${s.lng}|${s.pMethod}|${s.pOffset}"
        if (k != ck) {
            cv = try { PrayerCalc.compute(s.lat, s.lng, s.pMethod, s.pOffset, dayMid).map { Slot(it.first, it.second) } } catch (_: Exception) { emptyList() }
            ck = k
        }
        return cv
    }

    /** The prayer whose pause window contains [now], if any. */
    fun active(s: Store, now: Long): Slot? = slots(s, Usage.midnight()).firstOrNull { now >= it.at && now < it.end }

    fun next(s: Store, now: Long): Slot? =
        slots(s, Usage.midnight()).firstOrNull { it.at > now } ?: slots(s, Usage.nextMidnight()).firstOrNull()

    /** Last position the device knows (needs only the coarse location permission). */
    fun lastKnown(ctx: Context): Pair<Double, Double>? {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        var best: android.location.Location? = null
        for (p in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)) {
            try { lm.getLastKnownLocation(p)?.let { if (best == null || it.time > best!!.time) best = it } } catch (_: Exception) {}
        }
        return best?.let { it.latitude to it.longitude }
    }
}

private fun fmtClock(ms: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

/** "1 h 20 min" / "35 min" */
fun fmtMin(lang: String, m: Long): String =
    if (m >= 60) "${m / 60} ${tl(lang, "hr")} ${m % 60} ${tl(lang, "min")}" else "$m ${tl(lang, "min")}"

/** Home card: next prayer and how long until its pause. */
@Composable fun NextPrayerCard(s: Store) {
    val lang = LL.current; val now = System.currentTimeMillis()
    val nx = Prayer.next(s, now) ?: return
    Panel {
        Title(t("nextPrayer")); Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("p_" + nx.key), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Pr, modifier = Modifier.weight(1f))
            Text(fmtClock(nx.at), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = LC.current.tx)
        }
        Sub("${t("inTime")} ${fmtMin(lang, ((nx.at - now) / 60_000L).coerceAtLeast(0))} · ${Prayer.DUR[nx.key]} ${t("min")}")
    }
}

@Composable fun PrayerPanel(s: Store) {
    val ver = Ver.n
    val ctx = LocalContext.current; val lang = LL.current
    val now = System.currentTimeMillis()
    val offPending = s.pending.any { it.kind == "prayer" }
    fun locate() {
        val ll = Prayer.lastKnown(ctx)
        if (ll != null) { s.lat = ll.first; s.lng = ll.second; s.pCity = -1; Ver.n++ }
        else Toast.makeText(ctx, tl(lang, "locFail"), Toast.LENGTH_LONG).show()
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) locate() else Toast.makeText(ctx, tl(lang, "locFail"), Toast.LENGTH_LONG).show()
    }
    val slots = Prayer.slots(s, Usage.midnight())
    val nextKey = Prayer.next(s, now)?.key
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Title(t("prayer")); Sub(t("prayerD")) }
            Switch(s.prayerOn, { s.request("prayer", "", if (it) "1" else "0"); Ver.n++ })
        }
        if (offPending) Sub(t("pendShort"))
        Spacer(Modifier.height(14.dp))
        Title(t("prayerTimes")); Spacer(Modifier.height(6.dp))
        if (slots.isEmpty()) Sub(t("noData"))
        slots.forEach { sl ->
            val hot = sl.key == nextKey
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("p_" + sl.key), color = if (hot) Pr else LC.current.tx, fontWeight = if (hot) FontWeight.ExtraBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                Text("${Prayer.DUR[sl.key]} ${t("min")}", color = LC.current.mu, modifier = Modifier.padding(end = 14.dp))
                Text(fmtClock(sl.at), color = if (hot) Pr else LC.current.tx, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(14.dp))
        Title(t("location"))
        Sub(if (s.pCity in Prayer.CITIES.indices) Prayer.CITIES[s.pCity].name(lang) + (if (s.pCity == 0) " (${t("locDefault")})" else "") else t("locGps"))
        Spacer(Modifier.height(8.dp))
        PrimaryBtn(t("useLoc")) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
            else perm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        Spacer(Modifier.height(8.dp))
        Prayer.CITIES.chunked(3).forEachIndexed { r, row ->
            Row(Modifier.padding(top = if (r == 0) 0.dp else 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { j, city ->
                    val idx = r * 3 + j
                    Chip(city.name(lang), s.pCity == idx, Modifier.weight(1f), 12) { s.lat = city.lat; s.lng = city.lng; s.pCity = idx; Ver.n++ }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Title(t("method")); Spacer(Modifier.height(8.dp))
        PrayerCalc.METHODS.indices.chunked(3).forEachIndexed { r, row ->
            Row(Modifier.padding(top = if (r == 0) 0.dp else 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { i -> Chip(t("m$i"), s.pMethod == i, Modifier.weight(1f), 12) { s.pMethod = i; Ver.n++ } }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Title(t("pOffset")); Sub(t("pOffsetD")) }
            Stepper((if (s.pOffset > 0) "+" else "") + "${s.pOffset} ${t("min")}", true,
                { s.pOffset -= 1; Ver.n++ }, { s.pOffset += 1; Ver.n++ })
        }
    }
}
