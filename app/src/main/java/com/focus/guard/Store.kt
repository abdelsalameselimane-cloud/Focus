package com.focus.guard

import android.content.Context
import java.util.Calendar

val SOCIAL = setOf("com.instagram.android", "com.facebook.katana", "com.facebook.orca", "com.zhiliaoapp.musically",
    "com.ss.android.ugc.trill", "com.twitter.android", "com.snapchat.android", "org.telegram.messenger",
    "com.google.android.youtube", "com.reddit.frontpage", "com.pinterest")

class Store(private val c: Context) {
    private val p = c.getSharedPreferences("s", 0)
    var limit: Int get() = p.getInt("limit", 60); set(v) = p.edit().putInt("limit", v.coerceIn(15, 90)).apply()
    var blockMin: Int get() = p.getInt("block", 60); set(v) = p.edit().putInt("block", v.coerceIn(15, 180)).apply()
    var nightOn: Boolean get() = p.getBoolean("night", true); set(v) = p.edit().putBoolean("night", v).apply()
    var nightEnd: Int get() = p.getInt("nend", 6); set(v) = p.edit().putInt("nend", v.coerceIn(1, 12)).apply()
    var lang: String get() = p.getString("lang", "auto")!!; set(v) = p.edit().putString("lang", v).apply()
    var blockedUntil: Long get() = p.getLong("until", 0); set(v) = p.edit().putLong("until", v).apply()
    var windowStart: Long get() = p.getLong("ws", 0); set(v) = p.edit().putLong("ws", v).apply()
    var filterOn: Boolean get() = p.getBoolean("fon", true); set(v) = p.edit().putBoolean("fon", v).apply()
    var offReqAt: Long get() = p.getLong("oreq", 0); set(v) = p.edit().putLong("oreq", v).apply()
    var monitored: Set<String> get() = p.getStringSet("mon", emptySet())!!.toSet(); set(v) = p.edit().putStringSet("mon", v).apply()

    fun initDefaults() {
        if (p.contains("mon")) return
        val pm = c.packageManager
        monitored = SOCIAL.filter { pm.getLaunchIntentForPackage(it) != null }.toSet()
    }
}

object Usage {
    fun midnight(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun isNight(s: Store) = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < s.nightEnd

    /** Returns (monitored usage in ms since windowStart, current foreground package). */
    fun scan(c: Context, s: Store): Pair<Long, String?> {
        val um = c.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val now = System.currentTimeMillis()
        val ws = maxOf(s.windowStart, midnight())
        val ev = um.queryEvents(now - 24 * 3600_000L, now)
        val e = android.app.usage.UsageEvents.Event()
        val mon = s.monitored
        var total = 0L; var cur: String? = null; var t0 = 0L
        fun add(end: Long) { if (cur != null && cur in mon) total += maxOf(0L, end - maxOf(t0, ws)) }
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            when (e.eventType) {
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> { add(e.timeStamp); cur = e.packageName; t0 = e.timeStamp }
                android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> if (e.packageName == cur) { add(e.timeStamp); cur = null }
            }
        }
        add(now)
        return total to cur
    }
}
