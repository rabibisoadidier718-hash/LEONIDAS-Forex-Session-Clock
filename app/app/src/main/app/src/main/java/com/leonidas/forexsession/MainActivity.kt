
package com.leonidas.forexsession

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private lateinit var sessions: LinearLayout

    private val markets = listOf(
        Session("Sydney", 22, 7),
        Session("Tokyo", 0, 9),
        Session("Londres", 8, 17),
        Session("New York", 13, 22)
    )

    data class Session(val name: String, val open: Int, val close: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 35, 28, 25)
            setBackgroundColor(Color.rgb(12, 16, 27))
        }

        fun text(value: String, size: Float): TextView {
            return TextView(this).apply {
                text = value
                textSize = size
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(8, 14, 8, 14)
            }
        }

        root.addView(text("LEONIDAS", 30f))
        root.addView(text("FOREX SESSION CLOCK", 15f))

        clock = text("--:--:--", 36f)
        root.addView(clock)
        root.addView(text("HEURE DE MADAGASCAR • UTC+3", 13f))

        sessions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(sessions)

        setContentView(root)
        updateClock()
    }

    private fun updateClock() {
        val zone = TimeZone.getTimeZone("Indian/Antananarivo")
        val now = Date()

        val formatter = SimpleDateFormat(
            "HH:mm:ss", Locale.FRANCE
        )
        formatter.timeZone = zone

        clock.text = formatter.format(now)

        val calendar = java.util.Calendar.getInstance(zone)
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)

        sessions.removeAllViews()

        for (market in markets) {
            val isOpen = if (market.open < market.close) {
                hour >= market.open && hour < market.close
            } else {
                hour >= market.open || hour < market.close
            }

            val status = if (isOpen) "OUVERTE" else "FERMÉE"

            val item = TextView(this).apply {
                text = "${market.name}  •  $status\n" +
                    "%02d:00 - %02d:00".format(
                        market.open, market.close
                    )
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(
                    if (isOpen) Color.GREEN else Color.LTGRAY
                )
                setPadding(12, 20, 12, 20)
            }

            sessions.addView(item)
        }

        handler.postDelayed({ updateClock() }, 1000)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
