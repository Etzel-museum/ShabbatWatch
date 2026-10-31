package com.davidlevi.shabbatwatch

/** A city for zmanim. candleMinutes is the local custom for candle lighting before sunset. */
data class City(val name: String, val lat: Double, val lon: Double, val candleMinutes: Int = 20)

object Cities {

    val CANDLE_OPTIONS = listOf(18, 20, 22, 30, 40)

    // Jerusalem first: it is the default.
    val ALL = listOf(
        City("ירושלים", 31.7780, 35.2354, 40),
        City("תל אביב", 32.0853, 34.7818),
        City("חיפה", 32.7940, 34.9896, 30),
        City("באר שבע", 31.2520, 34.7915),
        City("בני ברק", 32.0807, 34.8338),
        City("פתח תקווה", 32.0871, 34.8875),
        City("ראשון לציון", 31.9730, 34.7925),
        City("רחובות", 31.8928, 34.8113),
        City("חולון", 32.0158, 34.7874),
        City("נתניה", 32.3215, 34.8532),
        City("הרצליה", 32.1663, 34.8433),
        City("רעננה", 32.1848, 34.8713),
        City("כפר סבא", 32.1750, 34.9069),
        City("חדרה", 32.4340, 34.9196),
        City("אשדוד", 31.8014, 34.6435),
        City("אשקלון", 31.6688, 34.5743),
        City("מודיעין", 31.8969, 35.0095),
        City("בית שמש", 31.7470, 34.9881),
        City("מעלה אדומים", 31.7775, 35.2981),
        City("אריאל", 32.1046, 35.1745),
        City("עפולה", 32.6078, 35.2897),
        City("טבריה", 32.7959, 35.5310),
        City("צפת", 32.9646, 35.4960),
        City("נהריה", 33.0059, 35.0941),
        City("אילת", 29.5577, 34.9519),
    )
}
