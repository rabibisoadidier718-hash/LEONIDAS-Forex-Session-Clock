
package com.leonidas.forexsession

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.text.InputType
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private val navy = Color.rgb(5, 9, 20)
    private val panel = Color.rgb(15, 24, 42)
    private val panelLight = Color.rgb(25, 39, 61)
    private val gold = Color.rgb(230, 180, 75)
    private val cyan = Color.rgb(30, 205, 230)
    private val white = Color.WHITE
    private val green = Color.rgb(50, 210, 140)
    private val red = Color.rgb(245, 85, 100)
    private val purple = Color.rgb(175, 125, 255)
    private val muted = Color.rgb(155, 170, 190)

    private val madagascarTimeZone =
        TimeZone.getTimeZone("Indian/Antananarivo")

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var prefs: android.content.SharedPreferences

    private var trades = JSONArray()
    private var currentPage = "Accueil"

    private var firstName = "TRADER"
    private var currency = "USD"
    private var initialCapital = 0.0
    private var dailyRiskLimit = 3.0
    private var maxTradesPerDay = 5
    private var targetAmount = 1000000.0

    private var selectedTradeDate = today()

    private fun today(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = madagascarTimeZone
        }.format(Date())
    }

    private fun madagascarDate(): String {
        return SimpleDateFormat(
            "EEEE dd MMMM yyyy",
            Locale.FRENCH
        ).apply {
            timeZone = madagascarTimeZone
        }.format(Date()).uppercase(Locale.FRENCH)
    }

    private fun newMadagascarCalendar(): Calendar {
        return Calendar.getInstance(madagascarTimeZone, Locale.US)
    }

    private fun parseDate(value: String): Calendar {
        val cal = newMadagascarCalendar()
        try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
                timeZone = madagascarTimeZone
            }
            val parsed = format.parse(value)
            if (parsed != null) cal.time = parsed
        } catch (_: Exception) {
            cal.time = Date()
        }
        return cal
    }

    private fun formatDate(cal: Calendar): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = madagascarTimeZone
        }.format(cal.time)
    }

    private fun money(value: Double): String =
        String.format(Locale.US, "%,.2f %s", value, currency)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = navy
        window.navigationBarColor = navy
        window.decorView.systemUiVisibility = 0

        prefs = getSharedPreferences("LEONIDAS_DATA", MODE_PRIVATE)

        loadSettings()
        loadTrades()
        buildApplication()
        showHome()
    }

    private fun loadSettings() {
        firstName = prefs.getString("first_name", "TRADER") ?: "TRADER"
        currency = prefs.getString("currency", "USD") ?: "USD"

        initialCapital = prefs.getString("initial_capital", "0")
            ?.toDoubleOrNull() ?: 0.0

        dailyRiskLimit = prefs.getString("daily_risk_limit", "3")
            ?.toDoubleOrNull() ?: 3.0

        maxTradesPerDay = prefs.getString("max_trades_per_day", "5")
            ?.toIntOrNull() ?: 5

        targetAmount = prefs.getString("target_amount", "1000000")
            ?.toDoubleOrNull() ?: 1000000.0
    }

    private fun saveSettings() {
        prefs.edit()
            .putString("first_name", firstName)
            .putString("currency", currency)
            .putString("initial_capital", initialCapital.toString())
            .putString("daily_risk_limit", dailyRiskLimit.toString())
            .putString("max_trades_per_day", maxTradesPerDay.toString())
            .putString("target_amount", targetAmount.toString())
            .apply()
    }

    private fun loadTrades() {
        trades = try {
            JSONArray(prefs.getString("trades", "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun saveTrades() {
        prefs.edit().putString("trades", trades.toString()).apply()
    }

    private fun buildApplication() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
            fitsSystemWindows = true
        }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(18))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(10, 18, 32))
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }

        val pages = listOf(
            "Accueil" to "⌂",
            "Trade" to "＋",
            "Journal" to "▤",
            "Calendrier" to "▦",
            "Menu" to "☰"
        )

        pages.forEach { item ->
            val page = item.first
            val symbol = item.second

            val b = Button(this).apply {
                text = "$symbol\n$page"
                textSize = 11f
                isAllCaps = false
                setTextColor(if (page == currentPage) cyan else white)
                setBackgroundColor(Color.TRANSPARENT)
                minHeight = dp(62)
                minimumHeight = dp(62)
                setPadding(dp(1), dp(3), dp(1), dp(3))
                setOnClickListener { navigate(page) }
            }

            nav.addView(
                b,
                LinearLayout.LayoutParams(0, dp(72), 1f)
            )
        }

        root.addView(
            nav,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(78)
            )
        )

        setContentView(root)
    }

    private fun navigate(page: String) {
        currentPage = page
        when (page) {
            "Accueil" -> showHome()
            "Trade" -> showTradeForm()
            "Journal" -> showJournal()
            "Calendrier" -> showCalendar()
            "Menu" -> showMenu()
        }
    }

    private fun refresh(title: String) {
        currentPage = title
        content.removeAllViews()
        header(title)
    }

    private fun header(title: String) {
        addText("LEONIDAS SPARTAN GOLD", 21f, cyan, true)
        addText("NEXUS // TRADING TERMINAL", 10f, gold, true)
        addSpace(5)
        addText(title.uppercase(Locale.getDefault()), 14f, white, true)
        addSpace(12)
    }

    private fun addText(
        value: String,
        size: Float = 14f,
        color: Int = white,
        bold: Boolean = false
    ): TextView {
        val t = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }
        content.addView(t)
        return t
    }

    private fun addSpace(height: Int) {
        content.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(height))
        )
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(panel)
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), Color.rgb(35, 65, 90))
            }
        }

        val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        p.bottomMargin = dp(12)
        content.addView(c, p)
        return c
    }

    private fun line(
        parent: LinearLayout,
        label: String,
        value: String,
        valueColor: Int = white
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(5))
        }

        val left = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(muted)
        }

        val right = TextView(this).apply {
            text = value
            textSize = 13f
            setTextColor(valueColor)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.END
        }

        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(right)
        parent.addView(row)
    }

    private fun makeButton(
        parent: LinearLayout,
        label: String,
        action: () -> Unit
    ) {
        val b = Button(this).apply {
            text = label
            textSize = 13f
            isAllCaps = false
            setTextColor(navy)
            setBackgroundColor(gold)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { action() }
        }

        val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(50)
        )
        p.bottomMargin = dp(8)
        parent.addView(b, p)
    }

    private fun input(
        parent: LinearLayout,
        label: String,
        value: String = "",
        numeric: Boolean = false
    ): EditText {
        val title = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(cyan)
            setPadding(0, dp(5), 0, dp(3))
        }
        parent.addView(title)

        val edit = EditText(this).apply {
            setText(value)
            textSize = 15f
            setTextColor(white)
            setHintTextColor(muted)
            hint = "Entrer une valeur"
            setSingleLine(true)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setBackgroundColor(panelLight)

            if (numeric) {
                inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    InputType.TYPE_NUMBER_FLAG_SIGNED
            }
        }

        val p = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(48)
        )
        p.bottomMargin = dp(7)
        parent.addView(edit, p)
        return edit
    }

    private fun totalPnl(): Double {
        var total = 0.0
        for (i in 0 until trades.length()) {
            total += trades.optJSONObject(i)?.optDouble("pnl", 0.0) ?: 0.0
        }
        return total
    }

    private fun currentBalance(): Double = initialCapital + totalPnl()

    private fun tradesOnDate(date: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        for (i in 0 until trades.length()) {
            val obj = trades.optJSONObject(i) ?: continue
            if (obj.optString("date") == date) result.add(obj)
        }
        return result
    }

    private fun dailyPnlOnDate(date: String): Double =
        tradesOnDate(date).sumOf { it.optDouble("pnl", 0.0) }

    private fun averageField(field: String): Double {
        if (trades.length() == 0) return 0.0
        var total = 0.0
        for (i in 0 until trades.length()) {
            total += trades.optJSONObject(i)?.optDouble(field, 0.0) ?: 0.0
        }
        return total / trades.length()
    }

    private fun winCount(): Int {
        var count = 0
        for (i in 0 until trades.length()) {
            if ((trades.optJSONObject(i)?.optDouble("pnl", 0.0) ?: 0.0) > 0) {
                count++
            }
        }
        return count
    }

    private fun lossCount(): Int {
        var count = 0
        for (i in 0 until trades.length()) {
            if ((trades.optJSONObject(i)?.optDouble("pnl", 0.0) ?: 0.0) < 0) {
                count++
            }
        }
        return count
    }

    private fun winRate(): Double =
        if (trades.length() > 0) winCount() * 100.0 / trades.length() else 0.0

    private fun grossProfit(): Double {
        var total = 0.0
        for (i in 0 until trades.length()) {
            val pnl = trades.optJSONObject(i)?.optDouble("pnl", 0.0) ?: 0.0
            if (pnl > 0) total += pnl
        }
        return total
    }

    private fun grossLoss(): Double {
        var total = 0.0
        for (i in 0 until trades.length()) {
            val pnl = trades.optJSONObject(i)?.optDouble("pnl", 0.0) ?: 0.0
            if (pnl < 0) total += abs(pnl)
        }
        return total
    }

    private fun profitFactor(): Double {
        val loss = grossLoss()
        return if (loss > 0) grossProfit() / loss else 0.0
    }

    private fun maxDrawdown(): Double {
        var balance = initialCapital
        var peak = initialCapital
        var worst = 0.0

        for (i in 0 until trades.length()) {
            balance += trades.optJSONObject(i)?.optDouble("pnl", 0.0) ?: 0.0
            peak = max(peak, balance)

            if (peak > 0) {
                val dd = (peak - balance) / peak * 100.0
                worst = max(worst, dd)
            }
        }
        return worst
    }

    private fun averageWin(): Double {
        var total = 0.0
        var count = 0
        for (i in 0 until trades.length()) {
            val pnl = trades.optJSONObject(i)?.optDouble("pnl", 0.0) ?: 0.0
            if (pnl > 0) {
                total += pnl
                count++
            }
        }
        return if (count > 0) total / count else 0.0
    }

    private fun averageLoss(): Double {
        var total = 0.0
        var count = 0
        for (i in 0 until trades.length()) {
            val pnl = trades.optJSONObject(i)?.optDouble("pnl", 0.0) ?: 0.0
            if (pnl < 0) {
                total += abs(pnl)
                count++
            }
        }
        return if (count > 0) total / count else 0.0
    }

    private fun showHome() {
        refresh("Accueil")

        addText("BIENVENUE, $firstName", 17f, gold, true)
        addText("Votre discipline construit votre capital.", 12f, muted)
        addText(madagascarDate(), 10f, cyan)
        addText("HEURE LOCALE : ${clockTime("Indian/Antananarivo")}", 12f, gold, true)
        addSpace(8)

        val c = card()
        addText("NEXUS // PERFORMANCE DASHBOARD", 14f, cyan, true)

        line(c, "Capital initial", money(initialCapital), gold)
        line(
            c,
            "Solde actuel",
            money(currentBalance()),
            if (currentBalance() >= initialCapital) green else red
        )
        line(
            c,
            "P&L net total",
            money(totalPnl()),
            if (totalPnl() >= 0) green else red
        )
        line(c, "Nombre de trades", trades.length().toString())
        line(c, "Taux de réussite", "%.1f %%".format(winRate()), cyan)
        line(c, "Profit factor", "%.2f".format(profitFactor()), purple)
        line(c, "Drawdown maximum", "%.2f %%".format(maxDrawdown()), red)

        val progress = if (targetAmount > 0) {
            ((totalPnl() / targetAmount) * 100.0).coerceIn(0.0, 100.0)
        } else 0.0

        line(c, "Objectif financier", money(targetAmount))
        line(c, "Progression de l'objectif", "%.4f %%".format(progress), gold)

        val chartCard = card()
        addText("COURBE D'ÉQUITÉ", 14f, cyan, true)
        addText("Évolution du capital après chaque trade", 11f, muted)
        chartCard.addView(
            EquityChartView(this),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(190)
            )
        )

        val stats = card()
        addText("INDICATEURS DE PERFORMANCE", 14f, cyan, true)
        line(stats, "Trades gagnants", winCount().toString(), green)
        line(stats, "Trades perdants", lossCount().toString(), red)
        line(stats, "Gain moyen", money(averageWin()), green)
        line(stats, "Perte moyenne", money(averageLoss()), red)
        line(stats, "MAE moyen", money(averageField("mae")), muted)
        line(stats, "MFE moyen", money(averageField("mfe")), muted)

        val sessions = card()
        addText("HORLOGE DES SESSIONS FOREX", 14f, cyan, true)
        line(sessions, "Madagascar", clockTime("Indian/Antananarivo"))
        line(sessions, "Sydney", clockTime("Australia/Sydney"))
        line(sessions, "Tokyo", clockTime("Asia/Tokyo"))
        line(sessions, "Londres", clockTime("Europe/London"))
        line(sessions, "New York", clockTime("America/New_York"))

        addText(
            "Les horaires des sessions changent selon l'heure d'été des différents pays.",
            11f,
            muted
        )

        val quick = card()
        addText("ACCÈS RAPIDE", 14f, cyan, true)
        makeButton(quick, "NOUVEAU TRADE") { showTradeForm() }
        makeButton(quick, "OUVRIR LE CALENDRIER") { showCalendar() }
        makeButton(quick, "MES STATISTIQUES") { showStats() }
    }

    private inner class EquityChartView(context: Context) : View(context) {

        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cyan
            strokeWidth = dp(2).toFloat()
            style = Paint.Style.STROKE
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(45, 30, 205, 230)
            style = Paint.Style.FILL
        }

        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(43, 58, 78)
            strokeWidth = dp(1).toFloat()
        }

        private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gold
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val left = dp(12).toFloat()
            val right = width - dp(12).toFloat()
            val top = dp(12).toFloat()
            val bottom = height - dp(18).toFloat()

            if (right <= left || bottom <= top) return

            for (i in 0..4) {
                val y = top + (bottom - top) * i / 4f
                canvas.drawLine(left, y, right, y, gridPaint)
            }

            val values = mutableListOf<Double>()
            var balance = initialCapital
            values.add(balance)

            for (i in 0 until trades.length()) {
                balance += trades.optJSONObject(i)?.optDouble("pnl", 0.0) ?: 0.0
                values.add(balance)
            }

            if (values.size < 2) {
                val y = (top + bottom) / 2f
                canvas.drawLine(left, y, right, y, linePaint)
                return
            }

            var minValue = values.minOrNull() ?: 0.0
            var maxValue = values.maxOrNull() ?: 1.0

            if (minValue == maxValue) {
                minValue -= 1.0
                maxValue += 1.0
            }

            val range = maxValue - minValue
            val path = Path()
            val fill = Path()

            values.forEachIndexed { index, value ->
                val x = left + (right - left) * index / (values.size - 1).toFloat()
                val y = bottom - ((value - minValue) / range).toFloat() *
                    (bottom - top)

                if (index == 0) {
                    path.moveTo(x, y)
                    fill.moveTo(x, bottom)
                    fill.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fill.lineTo(x, y)
                }

                canvas.drawCircle(x, y, dp(3).toFloat(), pointPaint)
            }

            fill.lineTo(right, bottom)
            fill.close()

            canvas.drawPath(fill, fillPaint)
            canvas.drawPath(path, linePaint)
        }
    }

    private fun clockTime(zone: String): String {
        return try {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .apply {
                    timeZone = TimeZone.getTimeZone(zone)
                }
                .format(Date())
        } catch (_: Exception) {
            "--:--"
        }
    }

    // ---------------------------------------------------------
    // CALENDRIER MENSUEL
    // ---------------------------------------------------------

    private fun showCalendar(
        selectedMonth: Calendar = newMadagascarCalendar(),
        selectedDate: String = today()
    ) {
        val month = selectedMonth.clone() as Calendar
        month.timeZone = madagascarTimeZone
        month.set(Calendar.DAY_OF_MONTH, 1)

        refresh("Calendrier")

        val monthName = SimpleDateFormat(
            "MMMM yyyy",
            Locale.FRENCH
        ).apply {
            timeZone = madagascarTimeZone
        }.format(month.time).uppercase(Locale.FRENCH)

        val topCard = card()
        addText("CALENDRIER DE TRADING", 15f, cyan, true)

        val monthRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val previous = Button(this).apply {
            text = "‹"
            textSize = 22f
            setTextColor(gold)
            setBackgroundColor(panelLight)
            setOnClickListener {
                val next = month.clone() as Calendar
                next.add(Calendar.MONTH, -1)
                showCalendar(next, selectedDate)
            }
        }

        val monthTitle = TextView(this).apply {
            text = monthName
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(white)
            setTypeface(null, Typeface.BOLD)
        }

        val next = Button(this).apply {
            text = "›"
            textSize = 22f
            setTextColor(gold)
            setBackgroundColor(panelLight)
            setOnClickListener {
                val nextMonth = month.clone() as Calendar
                nextMonth.add(Calendar.MONTH, 1)
                showCalendar(nextMonth, selectedDate)
            }
        }

        monthRow.addView(previous, LinearLayout.LayoutParams(dp(52), dp(48)))
        monthRow.addView(monthTitle, LinearLayout.LayoutParams(0, dp(48), 1f))
        monthRow.addView(next, LinearLayout.LayoutParams(dp(52), dp(48)))
        topCard.addView(monthRow)

        val monthTrades = mutableListOf<JSONObject>()
        for (i in 0 until trades.length()) {
            val trade = trades.optJSONObject(i) ?: continue
            val tradeDate = trade.optString("date")
            if (tradeDate.startsWith(
                    SimpleDateFormat("yyyy-MM", Locale.US)
                        .apply { timeZone = madagascarTimeZone }
                        .format(month.time)
                )
            ) {
                monthTrades.add(trade)
            }
        }

        val monthPnl = monthTrades.sumOf { it.optDouble("pnl", 0.0) }
        line(topCard, "Trades du mois", monthTrades.size.toString())
        line(
            topCard,
            "P&L du mois",
            money(monthPnl),
            if (monthPnl > 0) green else if (monthPnl < 0) red else muted
        )

        val calendarCard = card()
        addText("RÉSULTATS PAR JOUR", 14f, cyan, true)

        val grid = GridLayout(this).apply {
            columnCount = 8
            rowCount = 7
            useDefaultMargins = false
        }

        val headers = listOf("SEM", "LUN", "MAR", "MER", "JEU", "VEN", "SAM", "DIM")

        headers.forEachIndexed { index, label ->
            val tv = TextView(this).apply {
                text = label
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(if (index == 0) gold else cyan)
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(5), 0, dp(5))
            }
            val params = GridLayout.LayoutParams(
                GridLayout.spec(0),
                GridLayout.spec(index, 1f)
            ).apply {
                width = 0
                height = dp(32)
                setMargins(dp(1), dp(1), dp(1), dp(1))
            }
            grid.addView(tv, params)
        }

        val firstDay = month.clone() as Calendar
        val dayOfWeek = firstDay.get(Calendar.DAY_OF_WEEK)
        val offset = (dayOfWeek + 5) % 7
        firstDay.add(Calendar.DAY_OF_MONTH, -offset)

        val monthKey = SimpleDateFormat("yyyy-MM", Locale.US)
            .apply { timeZone = madagascarTimeZone }
            .format(month.time)

        for (week in 0 until 6) {
            val weekStart = firstDay.clone() as Calendar
            weekStart.add(Calendar.DAY_OF_MONTH, week * 7)

            val weekEnd = weekStart.clone() as Calendar
            weekEnd.add(Calendar.DAY_OF_MONTH, 6)

            val weekPnl = tradesInRange(
                formatDate(weekStart),
                formatDate(weekEnd)
            ).sumOf { it.optDouble("pnl", 0.0) }

            val weekCount = tradesInRange(
                formatDate(weekStart),
                formatDate(weekEnd)
            ).size

            val weekLabel = TextView(this).apply {
                text = "S${weekStart.get(Calendar.WEEK_OF_YEAR)}"
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(gold)
                setTypeface(null, Typeface.BOLD)
            }

            val weekParams = GridLayout.LayoutParams(
                GridLayout.spec(week + 1),
                GridLayout.spec(0, 1f)
            ).apply {
                width = 0
                height = dp(66)
                setMargins(dp(1), dp(1), dp(1), dp(1))
            }
            grid.addView(weekLabel, weekParams)

            for (dayIndex in 0 until 7) {
                val dayCal = weekStart.clone() as Calendar
                dayCal.add(Calendar.DAY_OF_MONTH, dayIndex)

                val dateText = formatDate(dayCal)
                val dayNumber = dayCal.get(Calendar.DAY_OF_MONTH)
                val sameMonth = dateText.startsWith(monthKey)
                val dayPnl = dailyPnlOnDate(dateText)
                val dayCount = tradesOnDate(dateText).size
                val isToday = dateText == today()
                val isSelected = dateText == selectedDate

                val dayButton = TextView(this).apply {
                    text = buildString {
                        append(dayNumber)
                        if (dayCount > 0) {
                            append("\n")
                            append(if (dayPnl > 0) "+" else "")
                            append(String.format(Locale.US, "%.0f", dayPnl))
                            append("\n")
                            append(dayCount)
                            append(" T")
                        }
                    }
                    textSize = if (dayCount > 0) 9f else 12f
                    gravity = Gravity.CENTER
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(
                        when {
                            !sameMonth -> Color.rgb(85, 96, 112)
                            dayPnl > 0 -> green
                            dayPnl < 0 -> red
                            else -> white
                        }
                    )

                    background = GradientDrawable().apply {
                        setColor(
                            when {
                                dayPnl > 0 -> Color.rgb(13, 48, 43)
                                dayPnl < 0 -> Color.rgb(55, 25, 37)
                                else -> panelLight
                            }
                        )
                        cornerRadius = dp(7).toFloat()
                        setStroke(
                            dp(if (isToday || isSelected) 2 else 1),
                            when {
                                isSelected -> cyan
                                isToday -> gold
                                else -> Color.rgb(42, 58, 78)
                            }
                        )
                    }

                    setOnClickListener {
                        showDayDetails(dateText)
                    }
                }

                val dayParams = GridLayout.LayoutParams(
                    GridLayout.spec(week + 1),
                    GridLayout.spec(dayIndex + 1, 1f)
                ).apply {
                    width = 0
                    height = dp(66)
                    setMargins(dp(1), dp(1), dp(1), dp(1))
                }

                grid.addView(dayButton, dayParams)
            }
        }

        calendarCard.addView(
            grid,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val legend = card()
        addText("LÉGENDE", 13f, cyan, true)
        line(legend, "Journée gagnante", "VERT", green)
        line(legend, "Journée perdante", "ROUGE", red)
        line(legend, "Aujourd'hui", "CONTOUR OR", gold)
        line(legend, "Jour sélectionné", "CONTOUR BLEU", cyan)
        line(legend, "T = nombre de trades", "P&L = résultat", white)

        val actions = card()
        addText("ACTIONS RAPIDES", 14f, cyan, true)
        makeButton(actions, "AJOUTER UN TRADE AUJOURD'HUI") {
            showTradeForm(today(), true)
        }
        makeButton(actions, "RETOUR À L'ACCUEIL") {
            showHome()
        }
    }

    private fun tradesInRange(
        startDate: String,
        endDate: String
    ): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        for (i in 0 until trades.length()) {
            val trade = trades.optJSONObject(i) ?: continue
            val date = trade.optString("date")
            if (date >= startDate && date <= endDate) {
                result.add(trade)
            }
        }
        return result
    }

    // ---------------------------------------------------------
    // FORMULAIRE DE TRADE
    // ---------------------------------------------------------

    private fun showTradeForm(
        dateValue: String = today(),
        returnToCalendar: Boolean = false
    ) {
        refresh("Trade")
        selectedTradeDate = dateValue

        val c = card()
        addText("ENREGISTRER UN TRADE", 15f, cyan, true)

        val asset = input(c, "ACTIF / PAIRE", "XAUUSD")

        val direction = Spinner(this)
        direction.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("BUY", "SELL")
        )
        c.addView(direction)

        val date = input(c, "DATE (AAAA-MM-JJ)", selectedTradeDate)
        val entry = input(c, "PRIX D'ENTRÉE", "", true)
        val stop = input(c, "STOP LOSS", "", true)
        val target = input(c, "TAKE PROFIT", "", true)
        val lot = input(c, "TAILLE DU LOT", "", true)
        val risk = input(c, "RISQUE EN %", "1", true)
        val pnl = input(c, "RÉSULTAT P&L", "0", true)
        val mae = input(c, "MAE", "0", true)
        val mfe = input(c, "MFE", "0", true)
        val session = input(c, "SESSION", "Londres")
        val emotion = input(c, "ÉMOTION", "Calme")
        val plan = input(c, "PLAN RESPECTÉ ?", "Oui")
        val error = input(c, "ERREUR ÉVENTUELLE", "")
        val notes = input(c, "NOTES", "")

        makeButton(c, "ENREGISTRER LE TRADE") {
            val pnlValue = pnl.text.toString().toDoubleOrNull()
            val entryValue = entry.text.toString().toDoubleOrNull()
            val stopValue = stop.text.toString().toDoubleOrNull()
            val targetValue = target.text.toString().toDoubleOrNull()
            val dateText = date.text.toString().trim()

            val validDate = try {
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    isLenient = false
                    timeZone = madagascarTimeZone
                }
                format.parse(dateText) != null
            } catch (_: Exception) {
                false
            }

            if (asset.text.isBlank() || pnlValue == null ||
                entryValue == null || stopValue == null ||
                targetValue == null || !validDate
            ) {
                message(
                    "Informations incorrectes",
                    "Vérifie l'actif, la date, l'entrée, le stop, l'objectif et le P&L."
                )
                return@makeButton
            }

            val obj = JSONObject().apply {
                put("id", System.currentTimeMillis())
                put("asset", asset.text.toString().uppercase(Locale.US))
                put("direction", direction.selectedItem.toString())
                put("date", dateText)
                put("entry", entryValue)
                put("stop", stopValue)
                put("target", targetValue)
                put("lot", lot.text.toString().toDoubleOrNull() ?: 0.0)
                put("risk", risk.text.toString().toDoubleOrNull() ?: 0.0)
                put("exit", 0.0)
                put("pnl", pnlValue)
                put("rMultiple", 0.0)
                put("mae", mae.text.toString().toDoubleOrNull() ?: 0.0)
                put("mfe", mfe.text.toString().toDoubleOrNull() ?: 0.0)
                put("session", session.text.toString())
                put("emotion", emotion.text.toString())
                put("plan", plan.text.toString())
                put("error", error.text.toString())
                put("notes", notes.text.toString())
                put("created", System.currentTimeMillis())
            }

            trades.put(obj)
            saveTrades()

            message("Trade enregistré", "Ton trade a été sauvegardé.")

            if (returnToCalendar) {
                showDayDetails(dateText)
            } else {
                showJournal()
            }
        }
    }

    // ---------------------------------------------------------
    // JOURNAL
    // ---------------------------------------------------------

    private fun showJournal() {
        refresh("Journal")

        val c = card()
        addText("HISTORIQUE DES TRADES", 15f, cyan, true)
        line(c, "Trades enregistrés", trades.length().toString())
        line(
            c,
            "P&L total",
            money(totalPnl()),
            if (totalPnl() >= 0) green else red
        )

        if (trades.length() == 0) {
            addText("Aucun trade enregistré pour le moment.", 13f, muted)
            makeButton(c, "AJOUTER UN TRADE") { showTradeForm() }
            return
        }

        for (i in trades.length() - 1 downTo 0) {
            val obj = trades.optJSONObject(i) ?: continue
            val tradeCard = card()
            val pnl = obj.optDouble("pnl", 0.0)

            addText(
                "${obj.optString("asset")} — ${obj.optString("direction")}",
                15f,
                cyan,
                true
            )

            line(tradeCard, "Date", obj.optString("date"))
            line(tradeCard, "P&L", money(pnl), if (pnl >= 0) green else red)
            line(tradeCard, "Session", obj.optString("session"))
            line(tradeCard, "Prix d'entrée", obj.optDouble("entry", 0.0).toString())
            line(tradeCard, "Stop Loss", obj.optDouble("stop", 0.0).toString())
            line(tradeCard, "Take Profit", obj.optDouble("target", 0.0).toString())
            line(tradeCard, "Lot", obj.optDouble("lot", 0.0).toString())
            line(tradeCard, "Risque", "${obj.optDouble("risk", 0.0)} %")
            line(tradeCard, "MAE", obj.optDouble("mae", 0.0).toString())
            line(tradeCard, "MFE", obj.optDouble("mfe", 0.0).toString())
            line(tradeCard, "Émotion", obj.optString("emotion"))
            line(tradeCard, "Plan respecté", obj.optString("plan"))

            val notes = obj.optString("notes")
            if (notes.isNotBlank()) {
                addText("Notes : $notes", 12f, muted)
            }

            makeButton(tradeCard, "SUPPRIMER CE TRADE") {
                AlertDialog.Builder(this)
                    .setTitle("Supprimer le trade ?")
                    .setMessage("Cette action est définitive.")
                    .setNegativeButton("ANNULER", null)
                    .setPositiveButton("SUPPRIMER") { _, _ ->
                        val newArray = JSONArray()
                        for (j in 0 until trades.length()) {
                            if (j != i) {
                                newArray.put(trades.getJSONObject(j))
                            }
                        }
                        trades = newArray
                        saveTrades()
                        showJournal()
                    }
                    .show()
            }

            makeButton(tradeCard, "VOIR DANS LE CALENDRIER") {
                showDayDetails(obj.optString("date"))
            }
        }
    }

    // ---------------------------------------------------------
    // DÉTAIL D'UNE JOURNÉE
    // ---------------------------------------------------------

    private fun showDayDetails(date: String) {
        refresh("Détail du jour")

        val dayTrades = tradesOnDate(date)
        val dailyPnl = dailyPnlOnDate(date)

        val summary = card()
        addText("RÉSULTATS DE LA JOURNÉE", 15f, cyan, true)

        line(summary, "Date", date)
        line(summary, "Nombre de trades", dayTrades.size.toString())
        line(
            summary,
            "P&L net",
            money(dailyPnl),
            if (dailyPnl > 0) green else if (dailyPnl < 0) red else muted
        )

        if (dayTrades.isEmpty()) {
            addText("Aucun trade enregistré pour cette journée.", 13f, muted)
        }

        dayTrades.forEach { trade ->
            val tradeCard = card()
            val pnl = trade.optDouble("pnl", 0.0)

            val status = when {
                pnl > 0 -> "TP"
                pnl < 0 -> "SL"
                else -> "BE"
            }

            val statusColor = when {
                pnl > 0 -> green
                pnl < 0 -> red
                else -> muted
            }

            addText(
                "${trade.optString("asset")} — ${trade.optString("direction")}",
                15f,
                cyan,
                true
            )

            line(tradeCard, "Résultat indicatif", status, statusColor)
            line(tradeCard, "P&L", money(pnl), statusColor)
            line(tradeCard, "Prix d'entrée", trade.optDouble("entry", 0.0).toString())
            line(tradeCard, "Stop Loss", trade.optDouble("stop", 0.0).toString())
            line(tradeCard, "Take Profit", trade.optDouble("target", 0.0).toString())
            line(tradeCard, "Lot", trade.optDouble("lot", 0.0).toString())
            line(tradeCard, "Session", trade.optString("session"))

            makeButton(tradeCard, "SUPPRIMER CE TRADE") {
                AlertDialog.Builder(this)
                    .setTitle("Supprimer ce trade ?")
                    .setMessage("Cette action est définitive.")
                    .setNegativeButton("ANNULER", null)
                    .setPositiveButton("SUPPRIMER") { _, _ ->
                        val newArray = JSONArray()

                        for (i in 0 until trades.length()) {
                            val item = trades.optJSONObject(i) ?: continue
                            if (item.optLong("id") != trade.optLong("id")) {
                                newArray.put(item)
                            }
                        }

                        trades = newArray
                        saveTrades()
                        showDayDetails(date)
                    }
                    .show()
            }
        }

        makeButton(summary, "+ AJOUTER UN TRADE À CETTE DATE") {
            showTradeForm(date, true)
        }

        makeButton(summary, "RETOUR AU CALENDRIER") {
            showCalendar(parseDate(date), date)
        }
    }

    // ---------------------------------------------------------
    // STATISTIQUES
    // ---------------------------------------------------------

    private fun showStats() {
        refresh("Statistiques")

        val c = card()
        addText("PERFORMANCE GLOBALE", 15f, cyan, true)

        line(c, "Total trades", trades.length().toString())
        line(c, "Trades gagnants", winCount().toString(), green)
        line(c, "Trades perdants", lossCount().toString(), red)
        line(c, "Taux de réussite", "%.2f %%".format(winRate()))
        line(c, "Profit brut", money(grossProfit()), green)
        line(c, "Perte brute", money(grossLoss()), red)
        line(
            c,
            "P&L net",
            money(totalPnl()),
            if (totalPnl() >= 0) green else red
        )
        line(c, "Profit factor", "%.2f".format(profitFactor()), purple)
        line(c, "Gain moyen", money(averageWin()), green)
        line(c, "Perte moyenne", money(averageLoss()), red)
        line(
            c,
            "P&L moyen / trade",
            money(if (trades.length() > 0) totalPnl() / trades.length() else 0.0)
        )
        line(c, "Drawdown maximum", "%.2f %%".format(maxDrawdown()), red)
        line(c, "MAE moyen", money(averageField("mae")))
        line(c, "MFE moyen", money(averageField("mfe")))

        val advice = card()
        addText("CONSEIL DE DISCIPLINE", 15f, cyan, true)

        addText(
            when {
                trades.length() == 0 ->
                    "Commence par enregistrer tes trades. La discipline commence par le suivi."
                winRate() >= 60 ->
                    "Bon taux de réussite. Vérifie aussi ton ratio gain/perte et la stabilité de tes résultats."
                winRate() >= 45 ->
                    "Analyse les trades perdants et identifie les erreurs répétées."
                else ->
                    "Réduis le risque, respecte ton plan et évite les trades impulsifs."
            },
            13f,
            white
        )
    }

    // ---------------------------------------------------------
    // MENU
    // ---------------------------------------------------------

    private fun showMenu() {
        refresh("Menu")

        val c = card()
        addText("ESPACE DE TRAVAIL", 15f, cyan, true)

        makeButton(c, "CALCULATEUR DE RISQUE") { showRiskCalculator() }
        makeButton(c, "CALCULATEUR DE LOTS") { showLotCalculator() }
        makeButton(c, "STATISTIQUES") { showStats() }
        makeButton(c, "OBJECTIFS FINANCIERS") { showObjectives() }
        makeButton(c, "PARAMÈTRES") { showSettings() }
        makeButton(c, "ACCUEIL") { showHome() }

        val info = card()
        addText("NEXUS TERMINAL", 15f, cyan, true)
        addText("LEONIDAS SPARTAN GOLD", 13f, gold, true)
        addText("Trading journal professionnel", 12f, muted)
        addText("Mode de stockage : local hors ligne", 12f, muted)
        addText("Fuseau horaire : Madagascar UTC+3", 12f, muted)
    }

    // ---------------------------------------------------------
    // CALCULATEUR DE RISQUE
    // ---------------------------------------------------------

    private fun showRiskCalculator() {
        refresh("Calculateur de risque")

        val c = card()
        addText("CALCUL DU RISQUE", 15f, cyan, true)

        val capital = input(
            c,
            "CAPITAL DISPONIBLE",
            currentBalance().toString(),
            true
        )
        val risk = input(c, "RISQUE EN %", "1", true)
        val entry = input(c, "PRIX D'ENTRÉE", "", true)
        val stop = input(c, "STOP LOSS", "", true)

        val result = addText("Résultat : en attente", 14f, gold, true)

        makeButton(c, "CALCULER") {
            val cap = capital.text.toString().toDoubleOrNull()
            val r = risk.text.toString().toDoubleOrNull()
            val e = entry.text.toString().toDoubleOrNull()
            val s = stop.text.toString().toDoubleOrNull()

            if (cap == null || r == null || e == null || s == null ||
                cap <= 0 || r <= 0 || abs(e - s) <= 0
            ) {
                message("Erreur", "Vérifie les valeurs saisies.")
                return@makeButton
            }

            val riskAmount = cap * r / 100.0

            result.text =
                "Montant risqué : ${money(riskAmount)}\n" +
                "Distance du stop : ${abs(e - s)}\n" +
                "Risque : $r %\n\n" +
                "Attention : ce calcul ne détermine pas la taille exacte du lot."
        }
    }

    // ---------------------------------------------------------
    // CALCULATEUR DE LOTS
    // ---------------------------------------------------------

    private fun showLotCalculator() {
        refresh("Calculateur de lots")

        val c = card()
        addText("CALCULATEUR DE LOTS", 15f, cyan, true)
        addText("ESTIMATION — VÉRIFIE LA VALEUR DU POINT", 11f, muted)

        val capital = input(
            c,
            "CAPITAL DISPONIBLE",
            currentBalance().toString(),
            true
        )
        val risk = input(c, "RISQUE EN %", "1", true)
        val stop = input(c, "DISTANCE DU STOP EN POINTS", "", true)
        val pointValue = input(
            c,
            "VALEUR D'UN POINT POUR 1 LOT",
            "1",
            true
        )

        val result = addText("Résultat : en attente", 14f, gold, true)

        makeButton(c, "CALCULER LE LOT") {
            val cap = capital.text.toString().toDoubleOrNull()
            val r = risk.text.toString().toDoubleOrNull()
            val distance = stop.text.toString().toDoubleOrNull()
            val value = pointValue.text.toString().toDoubleOrNull()

            if (cap == null || r == null || distance == null ||
                value == null || cap <= 0 || r <= 0 ||
                distance <= 0 || value <= 0
            ) {
                message("Erreur", "Vérifie les valeurs.")
                return@makeButton
            }

            val riskAmount = cap * r / 100.0
            val lots = riskAmount / (distance * value)

            result.text =
                "Capital : ${money(cap)}\n" +
                "Risque : ${money(riskAmount)}\n" +
                "Distance stop : $distance points\n" +
                "Lot estimé : %.4f".format(lots) +
                "\n\nVérifie la valeur du point et les spécifications du contrat auprès de ton broker."
        }
    }

    // ---------------------------------------------------------
    // OBJECTIFS FINANCIERS
    // ---------------------------------------------------------

    private fun showObjectives() {
        refresh("Objectifs")

        val c = card()
        addText("OBJECTIF FINANCIER", 15f, cyan, true)

        line(c, "Objectif", money(targetAmount))
        line(c, "Capital initial", money(initialCapital))
        line(c, "Solde actuel", money(currentBalance()))
        line(c, "P&L accumulé", money(totalPnl()))

        val remaining = targetAmount - totalPnl()

        line(
            c,
            "Montant restant",
            money(remaining),
            if (remaining > 0) gold else green
        )

        val progress = if (targetAmount > 0) {
            (totalPnl() / targetAmount * 100).coerceIn(0.0, 100.0)
        } else 0.0

        line(c, "Progression", "%.4f %%".format(progress))

        addText(
            "Objectif de référence : 1 000 000 USD. Les résultats affichés dépendent des trades que tu enregistres.",
            12f,
            muted
        )
    }

    // ---------------------------------------------------------
    // PARAMÈTRES ET CAPITAL INITIAL
    // ---------------------------------------------------------

    private fun showSettings() {
        refresh("Paramètres")

        val c = card()
        addText("CONFIGURATION DU COMPTE", 15f, cyan, true)

        val name = input(c, "NOM DU TRADER", firstName)
        val capital = input(
            c,
            "CAPITAL INITIAL",
            initialCapital.toString(),
            true
        )
        val target = input(
            c,
            "OBJECTIF FINANCIER",
            targetAmount.toString(),
            true
        )
        val dailyRisk = input(
            c,
            "LIMITE DE RISQUE JOURNALIÈRE (%)",
            dailyRiskLimit.toString(),
            true
        )
        val maxTrades = input(
            c,
            "NOMBRE MAXIMUM DE TRADES / JOUR",
            maxTradesPerDay.toString(),
            true
        )

        val currencySpinner = Spinner(this)
        currencySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("USD", "EUR", "MGA")
        )

        currencySpinner.setSelection(
            listOf("USD", "EUR", "MGA").indexOf(currency).coerceAtLeast(0)
        )

        c.addView(currencySpinner)

        makeButton(c, "SAUVEGARDER LES PARAMÈTRES") {
            val cap = capital.text.toString().toDoubleOrNull()
            val obj = target.text.toString().toDoubleOrNull()
            val risk = dailyRisk.text.toString().toDoubleOrNull()
            val maxTradesValue = maxTrades.text.toString().toIntOrNull()

            if (cap == null || obj == null || risk == null ||
                maxTradesValue == null || cap < 0 || obj <= 0 ||
                risk <= 0 || maxTradesValue <= 0
            ) {
                message(
                    "Valeurs incorrectes",
                    "Vérifie les paramètres saisis."
                )
                return@makeButton
            }

            firstName = name.text.toString().ifBlank { "TRADER" }
            initialCapital = cap
            targetAmount = obj
            dailyRiskLimit = risk
            maxTradesPerDay = maxTradesValue
            currency = currencySpinner.selectedItem.toString()

            saveSettings()

            message(
                "Paramètres sauvegardés",
                "Ton capital initial et tes paramètres ont été enregistrés."
            )

            showHome()
        }

        val info = card()
        addText("INFORMATIONS", 15f, cyan, true)
        addText("LEONIDAS SPARTAN GOLD", 14f, white, true)
        addText("NEXUS Trading Journal", 12f, muted)
        addText("Les données sont sauvegardées sur cet appareil.", 12f, muted)
        addText("Fuseau horaire : Madagascar UTC+3.", 12f, muted)
        addText("Objectif de référence : 1 000 000 USD.", 12f, gold)
        addText(
            "Attention : changer la devise modifie l'affichage, mais ne convertit pas automatiquement les montants.",
            11f,
            muted
        )
    }

    // ---------------------------------------------------------
    // MESSAGE
    // ---------------------------------------------------------

    private fun message(title: String, text: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(text)
            .setPositiveButton("OK", null)
            .show()
    }
}
