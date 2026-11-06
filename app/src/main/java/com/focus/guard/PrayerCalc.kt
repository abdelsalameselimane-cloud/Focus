package com.focus.guard

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Offline prayer-time calculator (no network, no external library).
 * Based on the well-known solar-position method used by PrayTimes.org. Asr uses the standard (Shafi'i/Maliki) shadow ratio.
 */
object PrayerCalc {
    /** fajr/isha: sun angle below the horizon. If [ishaMin] > 0, Isha = Maghrib + ishaMin minutes (Umm al-Qura). */
    class Method(val fajr: Double, val isha: Double, val ishaMin: Int = 0)

    /** 0 = Muslim World League (also what Algeria's ministry uses), 1 = Egyptian, 2 = Umm al-Qura, 3 = Karachi, 4 = ISNA. */
    val METHODS = listOf(Method(18.0, 17.0), Method(19.5, 17.5), Method(18.5, 0.0, 90), Method(18.0, 18.0), Method(15.0, 15.0))

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun fix(a: Double, b: Double) = a - b * floor(a / b)

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year; var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** Returns (declination in degrees, equation of time in hours). */
    private fun sun(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * sin(rad(g)) + 0.020 * sin(rad(2 * g)), 360.0)
        val e = 23.439 - 0.00000036 * d
        val ra = fix(deg(atan2(cos(rad(e)) * sin(rad(l)), cos(rad(l)))) / 15.0, 24.0)
        return deg(asin(sin(rad(e)) * sin(rad(l)))) to (q / 15.0 - ra)
    }

    /**
     * Prayer instants (epoch millis) for the local day starting at [dayMidnight], in order fajr, dhuhr, asr, maghrib, isha.
     * Prayers that cannot be computed (extreme latitudes) are left out.
     */
    fun compute(lat: Double, lng: Double, methodIdx: Int, offsetMin: Int, dayMidnight: Long): List<Pair<String, Long>> {
        val m = METHODS[methodIdx.coerceIn(0, METHODS.size - 1)]
        val cal = Calendar.getInstance().apply { timeInMillis = dayMidnight }
        val tz = TimeZone.getDefault().getOffset(dayMidnight + 12 * 3_600_000L) / 3_600_000.0
        val jd = julian(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)) - lng / (15.0 * 24.0)

        fun midDay(t: Double) = fix(12.0 - sun(jd + t).second, 24.0)
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sun(jd + t).first
            val x = (-sin(rad(angle)) - sin(rad(decl)) * sin(rad(lat))) / (cos(rad(decl)) * cos(rad(lat)))
            if (x < -1.0 || x > 1.0) return Double.NaN
            val th = deg(acos(x)) / 15.0
            return midDay(t) + if (ccw) -th else th
        }
        fun asrTime(t: Double): Double {
            val decl = sun(jd + t).first
            val angle = -deg(atan(1.0 / (1.0 + tan(rad(abs(lat - decl))))))
            return angleTime(angle, t, false)
        }

        val adj = tz - lng / 15.0
        val fajr = angleTime(m.fajr, 5.0 / 24, true) + adj
        val dhuhr = midDay(12.0 / 24) + adj
        val asr = asrTime(13.0 / 24) + adj
        val maghrib = angleTime(0.833, 18.0 / 24, false) + adj
        val isha = (if (m.ishaMin > 0) maghrib + m.ishaMin / 60.0 else angleTime(m.isha, 18.0 / 24, false) + adj)

        fun ms(h: Double) = dayMidnight + (h * 3_600_000.0).toLong() + offsetMin * 60_000L
        return listOf("fajr" to fajr, "dhuhr" to dhuhr, "asr" to asr, "maghrib" to maghrib, "isha" to isha)
            .filter { !it.second.isNaN() }.map { it.first to ms(it.second) }
    }
}
