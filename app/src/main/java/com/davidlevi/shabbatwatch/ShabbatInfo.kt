package com.davidlevi.shabbatwatch

import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The text lines under the clock. Any line that can't be computed is null and simply not shown. */
data class Lines(val hebrewDate: String, val parsha: String?, val zman: String?)

/**
 * Hebrew date (advances at sunset), this week's parsha, and the next relevant time:
 * candle lighting on Friday before it, tzeit Shabbat (8.5°, like Hebcal) from then
 * until the end of Saturday, and plain sunset on other days.
 */
object ShabbatInfo {

    private val israel: TimeZone = TimeZone.getTimeZone("Asia/Jerusalem")

    fun compute(now: Date, city: City, candleMinutes: Int): Lines {
        val loc = GeoLocation(city.name, city.lat, city.lon, 0.0, israel)
        val today = Calendar.getInstance(israel).apply { time = now }
        val sunset = zmanimFor(loc, today).seaLevelSunset

        val fmt = HebrewDateFormatter().apply { isHebrewFormat = true }

        val hebrewDay = today.clone() as Calendar
        if (sunset != null && now.after(sunset)) hebrewDay.add(Calendar.DAY_OF_MONTH, 1)
        val hebrewDate = fmt.format(jewish(hebrewDay))

        val saturday = hebrewDay.clone() as Calendar
        while (saturday.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) saturday.add(Calendar.DAY_OF_MONTH, 1)
        val shabbat = jewish(saturday)
        val parsha = if (shabbat.parshah != JewishCalendar.Parsha.NONE) {
            "פרשת " + fmt.formatParsha(shabbat)
        } else {
            fmt.formatYomTov(shabbat).ifBlank { null }
        }

        val dow = today.get(Calendar.DAY_OF_WEEK)
        val candles = sunset?.let { Date(it.time - candleMinutes * 60_000L) }
        val zman = when {
            dow == Calendar.FRIDAY && candles != null && now.before(candles) ->
                "הדלקת נרות " + hm(candles)
            dow == Calendar.FRIDAY || dow == Calendar.SATURDAY -> {
                val sat = today.clone() as Calendar
                if (dow == Calendar.FRIDAY) sat.add(Calendar.DAY_OF_MONTH, 1)
                zmanimFor(loc, sat).tzaisGeonim8Point5Degrees?.let { "צאת שבת " + hm(it) }
            }
            else -> sunset?.let { "שקיעה " + hm(it) }
        }

        return Lines(hebrewDate, parsha, zman)
    }

    private fun zmanimFor(loc: GeoLocation, day: Calendar) =
        ComplexZmanimCalendar(loc).apply { calendar = day.clone() as Calendar }

    private fun jewish(day: Calendar) = JewishCalendar(day).apply { inIsrael = true }

    private fun hm(d: Date): String =
        SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = israel }.format(d)
}
