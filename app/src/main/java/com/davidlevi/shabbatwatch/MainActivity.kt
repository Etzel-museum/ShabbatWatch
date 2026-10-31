package com.davidlevi.shabbatwatch

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.util.Date

/** Setup screen: one big button, plus city / candle-lighting / brightness and a readiness check. */
class MainActivity : ComponentActivity() {

    private lateinit var btnCity: Button
    private lateinit var btnCandle: Button
    private lateinit var btnBright: Button
    private lateinit var tvPreview: TextView
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Store.isActive(this)) {
            startActivity(Intent(this, ShabbatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        setContentView(R.layout.activity_main)
        btnCity = findViewById(R.id.btnCity)
        btnCandle = findViewById(R.id.btnCandle)
        btnBright = findViewById(R.id.btnBright)
        tvPreview = findViewById(R.id.tvPreview)
        tvStatus = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            ShabbatMode.start(this)
            finish()
        }

        btnCity.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("בחירת עיר")
                .setItems(Cities.ALL.map { it.name }.toTypedArray()) { _, i ->
                    Store.setCity(this, i)
                    refresh()
                }
                .show()
        }

        btnCandle.setOnClickListener {
            val options = Cities.CANDLE_OPTIONS
            val next = options[(options.indexOf(Store.candleMinutes(this)) + 1) % options.size]
            Store.setCandleMinutes(this, next)
            refresh()
        }

        btnBright.setOnClickListener {
            Store.setBrightness(this, (Store.brightness(this) + 1) % Store.BRIGHTNESS_NAMES.size)
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::tvStatus.isInitialized) refresh()
    }

    private fun refresh() {
        val city = Cities.ALL[Store.cityIndex(this)]
        btnCity.text = "עיר: ${city.name}"
        btnCandle.text = "הדלקת נרות: ${Store.candleMinutes(this)} דק' לפני השקיעה"
        btnBright.text = "בהירות: ${Store.BRIGHTNESS_NAMES[Store.brightness(this)]}"

        val info = ShabbatInfo.compute(Date(), city, Store.candleMinutes(this))
        tvPreview.text = listOfNotNull(info.hebrewDate, info.parsha, info.zman).joinToString("\n")

        tvStatus.text = SystemTweaks.status(this).joinToString("\n")
    }
}
