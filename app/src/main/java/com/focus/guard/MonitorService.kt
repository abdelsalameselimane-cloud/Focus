package com.focus.guard

import android.app.*
import android.app.admin.DeviceAdminReceiver
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class MonitorService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var warned = ""
    private var lastWidget = 0L
    private val tick = object : Runnable { override fun run() { try { check() } catch (_: Exception) {}; h.postDelayed(this, 2000) } }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("m", "Focus Guard", NotificationManager.IMPORTANCE_MIN))
        val n = NotificationCompat.Builder(this, "m").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Focus Guard").setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
        h.removeCallbacks(tick); h.post(tick)
        return START_STICKY
    }

    override fun onBind(i: Intent?): IBinder? = null
    override fun onDestroy() { h.removeCallbacks(tick) }

    private fun isSystem(pkg: String) = try {
        packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
    } catch (_: Exception) { false }

    private fun alert(id: Int, title: String, text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("w", "Focus Guard alerts", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(id, NotificationCompat.Builder(this, "w").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(text).setContentIntent(pi).setAutoCancel(true).build())
    }

    private fun housekeeping(s: Store, now: Long, lang: String) {
        if (s.focusNote && s.focusUntil > 0 && now >= s.focusUntil) {
            s.focusNote = false
            alert(2, tl(lang, "focusEndT"), tl(lang, "focusEndB"))
        }
        if (s.remind && now - s.lastRemind > 7 * DAY) {
            s.lastRemind = now
            alert(3, tl(lang, "reportT"), tl(lang, "reportB"))
        }
        if (s.prayerOn) Prayer.active(s, now)?.let {
            val id = "${it.key}${it.at}"
            if (s.lastPrayerNote != id) {
                s.lastPrayerNote = id
                alert(6, "${tl(lang, "prayerNow")} ${tl(lang, "p_" + it.key)}", tl(lang, "prayerAlertB"))
            }
        }
        if (Commit.tick(this, s, now))
            alert(5, tl(lang, "commitRepT"), tl(lang, "timeSaved") + ": " + (Commit.saved(s) / 60_000L).let { m -> if (m > 0) fmtMin(lang, m) else "—" })
        if (now - lastWidget > 60_000L) { lastWidget = now; refreshWidgets(this) }
        s.expirePending(now)
    }

    private fun check() {
        val s = Store(this); val now = System.currentTimeMillis(); val lang = resolveLang(s.lang)
        val mid = Usage.midnight()
        if (s.windowStart < mid) s.windowStart = mid
        housekeeping(s, now, lang)
        val sc = Usage.scan(this, s)
        val cur = sc.cur ?: return
        if (cur == packageName) return

        if (s.prayerOn && !isSystem(cur)) {
            val pr = Prayer.active(s, now)
            if (pr != null) return block("prayer", pr.end, pr.key)
        }
        if (s.nightOn && Usage.isNight(s) && !isSystem(cur)) return block("night", Usage.nightEnd(s))
        if (cur !in s.monitored) return

        if (now < s.focusUntil) return block("focus", s.focusUntil)
        val cal = Calendar.getInstance()
        val sch = s.schedules.firstOrNull { it.activeAt(cal) }
        if (sch != null) return block("sched", sch.endsAt(now))

        val lim = s.perLimits[cur] ?: 0
        val usedApp = sc.per[cur] ?: 0L
        if (lim > 0 && usedApp >= lim * 60_000L) { s.markBreach(); return block("app", Usage.nextMidnight()) }
        if (now < s.blockedUntil) return block("limit", s.blockedUntil)
        if (sc.total >= s.limit * 60_000L) {
            s.blockedUntil = now + s.blockMin * 60_000L
            s.windowStart = s.blockedUntil
            s.markBreach()
            return block("limit", s.blockedUntil)
        }

        // Heads-up when about 5 minutes remain (global or this app's own limit)
        val leftG = s.limit * 60_000L - sc.total
        val leftA = if (lim > 0) lim * 60_000L - usedApp else Long.MAX_VALUE
        val left = minOf(leftG, leftA)
        val key = (if (leftA < leftG) "a$cur" else "g${s.windowStart}") + mid
        if (left > 0 && left <= 300_000L && warned != key) {
            warned = key
            alert(4, tl(lang, "warnT"), tl(lang, "warnB"))
        }
    }

    private fun block(reason: String, until: Long, name: String = "") {
        startActivity(Intent(this, BlockActivity::class.java).putExtra("r", reason).putExtra("u", until).putExtra("n", name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED && hasUsage(c))
            ContextCompat.startForegroundService(c, Intent(c, MonitorService::class.java))
    }
}

/** Active device admin makes the system refuse to uninstall the app until it is deactivated. */
class AdminReceiver : DeviceAdminReceiver() {
    /** While a weekly commitment is running, warn that switching this protection off will be recorded. */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        val s = Store(context)
        return if (s.locked()) tl(resolveLang(s.lang), "adminWarn") else null
    }
    override fun onDisabled(context: Context, intent: Intent) {
        val s = Store(context)
        if (s.locked()) s.cBroken = true
    }
}

fun hasUsage(c: Context): Boolean {
    val a = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
}
