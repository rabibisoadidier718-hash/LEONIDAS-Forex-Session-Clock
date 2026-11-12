
package com.leonidas.forexsession

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : Activity() {

    private lateinit var clock: TextView
    private lateinit var sessions: TextView
    private lateinit var status: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val zone = TimeZone.getTimeZone("Indian/Antananarivo")

    private val updateClock = object : Runnable {
        override fun run() {
            val format = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)
            format.timeZone = zone

            clock.text = "MADAGASCAR\n${format.format(Date())}"

            val calendar = Calendar.getInstance(zone)
            val hour = calendar.get(Calendar.HOUR_OF_DAY)

            sessions.text = getSession(hour)
            status.text = "Fuseau horaire : GMT+3\nApplication 100% hors ligne"

            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(30, 40, 30, 40)
            setBackgroundColor(0xFF101820.toInt())
        }

        val title = TextView(this).apply {
            text = "LEONIDAS"
            textSize = 34f
            setTextColor(0xFFFFC107.toInt())
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "FOREX SESSION CLOCK"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }

        clock = TextView(this).apply {
            textSize = 30f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 40, 0, 40)
        }

        sessions = TextView(this).apply {
            textSize = 22f
            setTextColor(0xFF00E676.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        status = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFB0BEC5.toInt())
            gravity = Gravity.CENTER
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(clock)
        layout.addView(sessions)
        layout.addView(status)

        setContentView(layout)
        handler.post(updateClock)
    }

    private fun getSession(hour: Int): String {
        return when (hour) {
            in 0..6 -> "SESSION : TOKYO"
            in 7..11 -> "SESSION : LONDRES"
            in 12..16 -> "SESSION : NEW YORK"
            else -> "SESSION : SYDNEY"
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateClock)
        super.onDestroy()
    }
}
