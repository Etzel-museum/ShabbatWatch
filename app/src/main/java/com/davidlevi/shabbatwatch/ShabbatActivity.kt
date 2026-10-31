package com.davidlevi.shabbatwatch

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * The Shabbat screen: a dim clock that stays on and reacts to nothing.
 * Every touch, swipe, rotary event and key is swallowed. The only way out is
 * three short presses of the Back button within [EXIT_WINDOW_MS].
 */
class ShabbatActivity : ComponentActivity() {

    companion object {
        /** Read by [ShabbatService] to bring the screen back if something hid it. */
        @Volatile
        var isVisible = false

        private const val EXIT_PRESSES = 3
        private const val EXIT_WINDOW_MS = 2500L
        // A press held longer than this (wrist bent against the button) is not counted.
        private const val MAX_PRESS_MS = 600L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val backPresses = ArrayDeque<Long>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)

    private lateinit var content: View
    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvParsha: TextView
    private lateinit var tvZman: TextView

    private val tick = object : Runnable {
        override fun run() {
            render()
            val now = System.currentTimeMillis()
            handler.postDelayed(this, 60_000 - now % 60_000 + 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            screenBrightness = Store.brightnessValue(this@ShabbatActivity)
        }
        setContentView(R.layout.activity_shabbat)
        content = findViewById(R.id.content)
        tvTime = findViewById(R.id.tvTime)
        tvDate = findViewById(R.id.tvDate)
        tvParsha = findViewById(R.id.tvParsha)
        tvZman = findViewById(R.id.tvZman)

        // Fallback in case Back ever arrives through the dispatcher instead of dispatchKeyEvent.
        onBackPressedDispatcher.addCallback(this) { onBackPress() }
    }

    override fun onStart() {
        super.onStart()
        if (!Store.isActive(this)) {
            finish()
            return
        }
        isVisible = true
        handler.removeCallbacks(tick)
        tick.run()
    }

    override fun onStop() {
        super.onStop()
        isVisible = false
        handler.removeCallbacks(tick)
    }

    private fun render() {
        val now = Date()
        val info = ShabbatInfo.compute(now, Cities.ALL[Store.cityIndex(this)], Store.candleMinutes(this))
        tvTime.text = timeFormat.format(now)
        tvDate.text = info.hebrewDate
        tvParsha.text = info.parsha ?: ""
        tvParsha.visibility = if (info.parsha == null) View.GONE else View.VISIBLE
        tvZman.text = info.zman ?: ""
        // Move the text a few pixels every minute so the OLED doesn't burn in over 25 hours.
        val shift = resources.displayMetrics.density * 6
        content.translationX = Random.nextFloat() * 2 * shift - shift
        content.translationY = Random.nextFloat() * 2 * shift - shift
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = true

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean = true

    override fun dispatchTrackballEvent(ev: MotionEvent): Boolean = true

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK &&
            event.action == KeyEvent.ACTION_UP &&
            !event.isCanceled &&
            event.eventTime - event.downTime <= MAX_PRESS_MS
        ) {
            onBackPress()
        }
        return true
    }

    private fun onBackPress() {
        val now = System.currentTimeMillis()
        while (backPresses.isNotEmpty() && now - backPresses.first() > EXIT_WINDOW_MS) backPresses.removeFirst()
        backPresses.addLast(now)
        if (backPresses.size >= EXIT_PRESSES) {
            backPresses.clear()
            ShabbatMode.stop(this)
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
    }
}
