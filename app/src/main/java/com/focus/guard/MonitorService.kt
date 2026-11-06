package com.focus.guard

import android.app.*
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class MonitorService : Service() {
    private val h = Handler(Looper.getMainLooper())
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

    private fun check() {
        val s = Store(this); val now = System.currentTimeMillis()
        if (s.filterOn && !FilterService.running && VpnService.prepare(this) == null) startService(Intent(this, FilterService::class.java))
        if (s.windowStart < Usage.midnight()) s.windowStart = Usage.midnight()
        val (used, cur) = Usage.scan(this, s)
        if (cur == null || cur == packageName) return
        if (s.nightOn && Usage.isNight(s) && !isSystem(cur)) return block("night")
        if (cur in s.monitored) {
            if (now < s.blockedUntil) return block("limit")
            if (used >= s.limit * 60_000L) {
                s.blockedUntil = now + s.blockMin * 60_000L
                s.windowStart = s.blockedUntil
                block("limit")
            }
        }
    }

    private fun block(reason: String) {
        startActivity(Intent(this, BlockActivity::class.java).putExtra("r", reason)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED && hasUsage(c))
            ContextCompat.startForegroundService(c, Intent(c, MonitorService::class.java))
    }
}

fun hasUsage(c: Context): Boolean {
    val a = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName) == AppOpsManager.MODE_ALLOWED
}
