package com.focus.guard

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.security.MessageDigest
import java.util.Calendar

val SOCIAL = setOf("com.instagram.android", "com.facebook.katana", "com.facebook.orca", "com.zhiliaoapp.musically",
    "com.ss.android.ugc.trill", "com.twitter.android", "com.snapchat.android", "org.telegram.messenger",
    "com.google.android.youtube", "com.reddit.frontpage", "com.pinterest")

const val DAY = 86_400_000L
const val DELAY_MS = 30 * 60_000L      // wait before a loosening change can be applied
const val WINDOW_MS = 10 * 60_000L     // how long a ready change stays applicable

/** Block schedule. days: bit0 = Sunday ... bit6 = Saturday. start/end: minutes since midnight. */
class Sched(val id: String, val days: Int, val start: Int, val end: Int) {
    fun activeAt(cal: Calendar): Boolean {
        val dow = cal.get(Calendar.DAY_OF_WEEK) - 1
        val prev = (dow + 6) % 7
        val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val today = ((days shr dow) and 1) == 1
        val yest = ((days shr prev) and 1) == 1
        return when {
            start < end -> today && m >= start && m < end
            start > end -> (today && m >= start) || (yest && m < end)
            else -> false
        }
    }
    fun endsAt(now: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        c.set(Calendar.HOUR_OF_DAY, end / 60); c.set(Calendar.MINUTE, end % 60)
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }
}

/** A change that loosens a restriction and must wait DELAY_MS before it can be applied. */
class Pend(val kind: String, val arg: String, val value: String, val readyAt: Long)

class Store(private val c: Context) {
    private val p = c.getSharedPreferences("s", 0)
    var limit: Int get() = p.getInt("limit", 60); set(v) = p.edit().putInt("limit", v.coerceIn(15, 90)).apply()
    var blockMin: Int get() = p.getInt("block", 60); set(v) = p.edit().putInt("block", v.coerceIn(15, 180)).apply()
    var nightOn: Boolean get() = p.getBoolean("night", true); set(v) = p.edit().putBoolean("night", v).apply()
    var nightEnd: Int get() = p.getInt("nend", 6); set(v) = p.edit().putInt("nend", v.coerceIn(1, 12)).apply()
    var lang: String get() = p.getString("lang", "auto")!!; set(v) = p.edit().putString("lang", v).apply()
    var blockedUntil: Long get() = p.getLong("until", 0); set(v) = p.edit().putLong("until", v).apply()
    var windowStart: Long get() = p.getLong("ws", 0); set(v) = p.edit().putLong("ws", v).apply()
    var monitored: Set<String> get() = p.getStringSet("mon", emptySet())!!.toSet(); set(v) = p.edit().putStringSet("mon", v).apply()
    var focusUntil: Long get() = p.getLong("fu", 0); set(v) = p.edit().putLong("fu", v).apply()
    var focusNote: Boolean get() = p.getBoolean("fn", false); set(v) = p.edit().putBoolean("fn", v).apply()
    var remind: Boolean get() = p.getBoolean("rem", false); set(v) = p.edit().putBoolean("rem", v).apply()
    var lastRemind: Long get() = p.getLong("lrem", 0); set(v) = p.edit().putLong("lrem", v).apply()

    /** Per-app daily limits in minutes (pkg -> minutes). Missing = no limit. */
    var perLimits: Map<String, Int>
        get() = (p.getString("pl", "") ?: "").split(";").mapNotNull {
            val a = it.split("=")
            if (a.size == 2) a[1].toIntOrNull()?.let { m -> a[0] to m } else null
        }.toMap()
        set(v) = p.edit().putString("pl", v.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()

    var schedules: List<Sched>
        get() = (p.getString("sch", "") ?: "").split(";").mapNotNull {
            val a = it.split(",")
            if (a.size == 4) Sched(a[0], a[1].toIntOrNull() ?: 0, a[2].toIntOrNull() ?: 0, a[3].toIntOrNull() ?: 0) else null
        }
        set(v) = p.edit().putString("sch", v.joinToString(";") { "${it.id},${it.days},${it.start},${it.end}" }).apply()

    var pending: List<Pend>
        get() = (p.getString("pend", "") ?: "").split("\n").mapNotNull {
            val a = it.split("|")
            if (a.size == 4) Pend(a[0], a[1], a[2], a[3].toLongOrNull() ?: 0L) else null
        }
        set(v) = p.edit().putString("pend", v.joinToString("\n") { "${it.kind}|${it.arg}|${it.value}|${it.readyAt}" }).apply()

    // ---- PIN ----
    private fun hash(x: String) = MessageDigest.getInstance("SHA-256").digest(("fg:$x").toByteArray()).joinToString("") { "%02x".format(it) }
    fun hasPin() = (p.getString("pin", "") ?: "").isNotEmpty()
    fun setPin(x: String) = p.edit().putString("pin", hash(x)).apply()
    fun checkPin(x: String) = hash(x) == p.getString("pin", "")
    fun clearPin() = p.edit().putString("pin", "").apply()

    // ---- Streak ----
    fun markBreach() {
        val key = Usage.midnight().toString()
        val set = p.getStringSet("br", emptySet())!!
        if (key !in set) p.edit().putStringSet("br", set + key).apply()
    }
    fun streak(): Int {
        val br = p.getStringSet("br", emptySet())!!
        val first = p.getLong("first", Usage.midnight())
        val cal = Calendar.getInstance()
        cal.timeInMillis = Usage.midnight()
        var n = 0
        while (cal.timeInMillis >= first && cal.timeInMillis.toString() !in br) { n++; cal.add(Calendar.DAY_OF_YEAR, -1) }
        return n
    }

    // ---- Delayed loosening ----
    private fun eff(m: Int) = if (m == 0) Int.MAX_VALUE else m

    fun isLoosening(kind: String, arg: String, value: String): Boolean = when (kind) {
        "limit" -> value.toInt() > limit
        "block" -> value.toInt() < blockMin
        "night" -> value != "1" && nightOn
        "nend" -> value.toInt() < nightEnd
        "app" -> value != "1" && arg in monitored
        "plimit" -> eff(value.toInt()) > eff(perLimits[arg] ?: 0)
        "sched" -> true
        else -> false
    }

    fun applyChange(kind: String, arg: String, value: String) {
        when (kind) {
            "limit" -> limit = value.toInt()
            "block" -> blockMin = value.toInt()
            "night" -> nightOn = value == "1"
            "nend" -> nightEnd = value.toInt()
            "app" -> monitored = if (value == "1") monitored + arg else monitored - arg
            "plimit" -> perLimits = if (value.toInt() == 0) perLimits - arg else perLimits + (arg to value.toInt())
            "sched" -> schedules = schedules.filter { it.id != arg }
        }
    }

    /** Tightening changes apply at once; loosening ones are queued behind a 30 minute wait. */
    fun request(kind: String, arg: String, value: String) {
        val rest = pending.filterNot { it.kind == kind && it.arg == arg }
        if (isLoosening(kind, arg, value)) pending = rest + Pend(kind, arg, value, System.currentTimeMillis() + DELAY_MS)
        else { pending = rest; applyChange(kind, arg, value) }
    }
    fun applyPending(x: Pend) { applyChange(x.kind, x.arg, x.value); dropPending(x) }
    fun dropPending(x: Pend) { pending = pending.filterNot { it.kind == x.kind && it.arg == x.arg } }
    fun expirePending(now: Long) {
        val all = pending
        val keep = all.filter { now <= it.readyAt + WINDOW_MS }
        if (keep.size != all.size) pending = keep
    }

    fun initDefaults() {
        if (!p.contains("first")) p.edit().putLong("first", Usage.midnight()).apply()
        if (p.contains("mon")) return
        val pm = c.packageManager
        monitored = SOCIAL.filter { pm.getLaunchIntentForPackage(it) != null }.toSet()
    }
}

class Scan(val total: Long, val per: Map<String, Long>, val cur: String?)
class Week(val days: List<Long>, val top: List<Pair<String, Long>>, val startMs: Long)

object Usage {
    fun midnight(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun nextMidnight(): Long {
        val c = Calendar.getInstance(); c.timeInMillis = midnight(); c.add(Calendar.DAY_OF_YEAR, 1); return c.timeInMillis
    }

    fun nightEnd(s: Store): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, s.nightEnd); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun isNight(s: Store) = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < s.nightEnd

    /** Walks foreground sessions since [from]; returns the package currently in front (or null). */
    fun walk(c: Context, from: Long, onSpan: (String, Long, Long) -> Unit): String? {
        val um = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val ev = um.queryEvents(from, now)
        val e = UsageEvents.Event()
        var cur: String? = null
        var t0 = 0L
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    val prev = cur
                    if (prev != null) onSpan(prev, t0, e.timeStamp)
                    cur = e.packageName; t0 = e.timeStamp
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val prev = cur
                    if (prev != null && e.packageName == prev) { onSpan(prev, t0, e.timeStamp); cur = null }
                }
            }
        }
        val last = cur
        if (last != null) onSpan(last, t0, now)
        return last
    }

    /** Usage of monitored apps: total since the current window, plus per-app usage today. */
    fun scan(c: Context, s: Store): Scan {
        val mon = s.monitored
        val mid = midnight()
        val ws = maxOf(s.windowStart, mid)
        var total = 0L
        val per = HashMap<String, Long>()
        val cur = walk(c, System.currentTimeMillis() - DAY) { pkg, a, b ->
            if (pkg in mon) {
                total += maxOf(0L, b - maxOf(a, ws))
                per[pkg] = (per[pkg] ?: 0L) + maxOf(0L, b - maxOf(a, mid))
            }
        }
        return Scan(total, per, cur)
    }

    /** Monitored usage for the last 7 days (oldest first, today last) and per-app totals. */
    fun week(c: Context, s: Store): Week {
        val mon = s.monitored
        val cal = Calendar.getInstance()
        cal.timeInMillis = midnight(); cal.add(Calendar.DAY_OF_YEAR, -6)
        val start = cal.timeInMillis
        val bounds = LongArray(8)
        for (i in 0..7) { bounds[i] = cal.timeInMillis; cal.add(Calendar.DAY_OF_YEAR, 1) }
        val days = LongArray(7)
        val per = HashMap<String, Long>()
        walk(c, start) { pkg, a, b ->
            if (pkg in mon) {
                for (i in 0..6) {
                    val ov = minOf(b, bounds[i + 1]) - maxOf(a, bounds[i])
                    if (ov > 0) { days[i] += ov; per[pkg] = (per[pkg] ?: 0L) + ov }
                }
            }
        }
        return Week(days.toList(), per.entries.sortedByDescending { it.value }.map { it.key to it.value }, start)
    }
}
