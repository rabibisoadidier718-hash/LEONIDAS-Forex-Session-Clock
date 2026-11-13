
package com.leonidas.forexsession

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.widget.ScrollView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : Activity() {

    private val gold = Color.rgb(255, 193, 7)
    private val dark = Color.rgb(16, 24, 32)
    private val zone = TimeZone.getTimeZone("Indian/Antananarivo")
    private lateinit var clock: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(dark)
            setPadding(24, 30, 24, 30)
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "⚔ LEONIDAS"
            textSize = 34f
            setTextColor(gold)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "SPARTAN GOLD"
            textSize = 19f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        clock = TextView(this).apply {
            textSize = 25f
            setTextColor(gold)
            gravity = Gravity.CENTER
            setPadding(0, 25, 0, 10)
        }

        val location = TextView(this).apply {
            text = "MADAGASCAR • GMT+3"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }

        content.addView(title)
        content.addView(subtitle)
        content.addView(clock)
        content.addView(location)

        addSection(content, "CENTRE DE TRADING")

        val modules = listOf(
            "HORLOGE & SESSIONS FOREX",
            "ALARMES DES SESSIONS",
            "CALCULATEUR DE LOTS",
            "CALENDRIER ECONOMIQUE",
            "GESTION DU RISQUE",
            "JOURNAL DE TRADING PRO",
            "STATISTIQUES & PSYCHOLOGIE",
            "OBJECTIF CAPITAL : 1 000 000 $"
        )

        modules.forEachIndexed { index, name ->
            val button = Button(this).apply {
                text = name
                textSize = 14f
                setTextColor(gold)
                setBackgroundColor(Color.rgb(35, 43, 52))
                isAllCaps = false

                setOnClickListener {
                    Toast.makeText(
                        this@MainActivity,
                        "$name : module en construction",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                65
            )
            params.setMargins(0, 7, 0, 7)
            content.addView(button, params)
        }

        addSection(content, "LE CODE DU SPARTAN")

        val footer = TextView(this).apply {
            text = "DISCIPLINE • PATIENCE • PRECISION\nAPPLICATION HORS LIGNE"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 25, 0, 20)
        }

        content.addView(footer)
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)

        updateClock()
    }

    private fun addSection(
        layout: LinearLayout,
        name: String
    ) {
        val section = TextView(this).apply {
            text = name
            textSize = 16f
            setTextColor(gold)
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 15)
        }
        layout.addView(section)
    }

    private fun updateClock() {
        val format = SimpleDateFormat(
            "HH:mm:ss",
            Locale.FRANCE
        )
        format.timeZone = zone

        clock.text = format.format(Date())

        clock.postDelayed({
            updateClock()
        }, 1000)
    }
}
