
package com.leonidas.forexsession

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private class AssetStat(
    var count: Int = 0,
    var lots: Double = 0.0,
    var pnl: Double = 0.0,
    var wins: Int = 0,
    var losses: Int = 0
)

private class DonutView(
    ctx: Context,
    private val values: List<Double>,
    private val colors: List<Int>,
    private val centerTop: String,
    private val centerSub: String,
    private val topColor: Int,
    private val subColor: Int
) : View(ctx) {

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val d = resources.displayMetrics.density
        val size = min(width, height).toFloat()
        if (size <= 0f) return

        val stroke = size * 0.16f
        val cx = width / 2f
        val cy = height / 2f
        val r = size / 2f - stroke / 2f - 4f * d
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)

        ring.style = Paint.Style.STROKE
        ring.strokeWidth = stroke
        ring.color = Color.rgb(34, 48, 70)
        canvas.drawCircle(cx, cy, r, ring)

        val total = values.sum()

        if (total > 0.0) {
            var start = -90f

            values.forEachIndexed { i, value ->
                val sweep = (value / total * 360.0).toFloat()
                ring.color = colors[i % colors.size]
                canvas.drawArc(
                    rect,
                    start,
                    max(sweep - 1.2f, 0.5f),
                    false,
                    ring
                )
                start += sweep
            }
        }

        txt.textAlign = Paint.Align.CENTER
        txt.typeface = Typeface.DEFAULT_BOLD
        txt.color = topColor
        txt.textSize = 22f * d
        canvas.drawText(centerTop, cx, cy + 2f * d, txt)

        txt.typeface = Typeface.DEFAULT
        txt.color = subColor
        txt.textSize = 11f * d
        canvas.drawText(centerSub, cx, cy + 20f * d, txt)
    }
}

private class LineView(
    ctx: Context,
    private val pts: List<Double>,
    private val lineColor: Int,
    private val gridColor: Int,
    private val labelColor: Int
) : View(ctx) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (pts.isEmpty()) return

        val d = resources.displayMetrics.density
        val padL = 12f * d
        val padR = 12f * d
        val padT = 20f * d
        val padB = 12f * d

        var lo = 0.0
        var hi = 0.0

        pts.forEach {
            lo = min(lo, it)
            hi = max(hi, it)
        }

        if (hi - lo < 0.000001) hi = lo + 1.0

        val w = width - padL - padR
        val h = height - padT - padB

        fun px(i: Int): Float =
            if (pts.size == 1) padL + w / 2f
            else padL + w * i / (pts.size - 1f)

        fun py(v: Double): Float =
            (padT + h * (1.0 - (v - lo) / (hi - lo))).toFloat()

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f * d
        p.color = gridColor
        canvas.drawLine(padL, py(0.0), width - padR, py(0.0), p)

        val fill = Path()
        fill.moveTo(px(0), py(0.0))

        pts.forEachIndexed { i, value ->
            fill.lineTo(px(i), py(value))
        }

        fill.lineTo(px(pts.size - 1), py(0.0))
        fill.close()

        p.style = Paint.Style.FILL
        p.color = lineColor
        p.alpha = 40
        canvas.drawPath(fill, p)

        val line = Path()

        pts.forEachIndexed { i, value ->
            if (i == 0) line.moveTo(px(i), py(value))
            else line.lineTo(px(i), py(value))
        }

        p.style = Paint.Style.STROKE
        p.strokeWidth = 2.5f * d
        p.color = lineColor
        p.alpha = 255
        canvas.drawPath(line, p)

        p.style = Paint.Style.FILL
        canvas.drawCircle(
            px(pts.size - 1),
            py(pts.last()),
            4f * d,
            p
        )

        p.color = labelColor
        p.textSize = 10f * d

        canvas.drawText(
            String.format(Locale.US, "MAX %.2f", hi),
            padL,
            padT - 5f * d,
            p
        )
    }
}

class MainActivity : Activity() {

    private val prefsName = "leonidas_journal"
    private val tradesKey = "trades"

    private val gold = Color.rgb(220, 174, 72)
    private val navy = Color.rgb(10, 18, 34)
    private val panel = Color.rgb(19, 30, 49)
    private val panel2 = Color.rgb(25, 39, 61)
    private val white = Color.rgb(235, 241, 249)
    private val muted = Color.rgb(151, 168, 190)
    private val green = Color.rgb(39, 190, 129)
    private val red = Color.rgb(238, 91, 91)
    private val cyan = Color.rgb(56, 190, 220)
    private val orange = Color.rgb(242, 157, 62)
    private val violet = Color.rgb(160, 119, 235)

    private val palette = intArrayOf(
        cyan, gold, green, orange, violet, red,
        Color.rgb(90, 160, 255),
        Color.rgb(240, 120, 180),
        muted
    )

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout

    private var trades = JSONArray()

    private var firstName = "Spartan"
    private var currency = "USD"
    private var dailyRiskLimit = 0.0
    private var maxTradesPerDay = 4
    private var targetAmount = 1_000_000.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = navy
        window.navigationBarColor = navy

        loadSettings()
        loadTrades()
        showHome()
    }

    private fun loadSettings() {
        val p = getSharedPreferences(prefsName, MODE_PRIVATE)

        firstName = p.getString("first_name", "Spartan") ?: "Spartan"
        currency = p.getString("currency", "USD") ?: "USD"
        dailyRiskLimit = p.getString("daily_risk_limit", "0")
            ?.toDoubleOrNull() ?: 0.0
        maxTradesPerDay = p.getInt("max_trades_per_day", 4)
        targetAmount = p.getString("target_amount", "1000000")
            ?.toDoubleOrNull() ?: 1_000_000.0
    }

    private fun saveSettings() {
        getSharedPreferences(prefsName, MODE_PRIVATE).edit()
            .putString("first_name", firstName)
            .putString("currency", currency)
            .putString("daily_risk_limit", dailyRiskLimit.toString())
            .putInt("max_trades_per_day", maxTradesPerDay)
            .putString("target_amount", targetAmount.toString())
            .apply()
    }

    private fun loadTrades() {
        val raw = getSharedPreferences(prefsName, MODE_PRIVATE)
            .getString(tradesKey, "[]") ?: "[]"

        trades = try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun saveTrades() {
        getSharedPreferences(prefsName, MODE_PRIVATE)
            .edit()
            .putString(tradesKey, trades.toString())
            .apply()
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun rounded(
        color: Int,
        stroke: Int = Color.TRANSPARENT
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(14).toFloat()

            if (stroke != Color.TRANSPARENT) {
                setStroke(dp(1), stroke)
            }
        }
    }

    private fun text(
        value: String,
        size: Float = 14f,
        color: Int = white,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)

            if (bold) {
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            }

            setPadding(dp(2), dp(3), dp(2), dp(3))
        }
    }

    private fun button(
        label: String,
        action: () -> Unit,
        color: Int = panel2
    ): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(
                color,
                if (color == gold) gold else Color.rgb(47, 67, 94)
            )

            setTextColor(
                if (color == gold) navy else white
            )

            setOnClickListener { action() }
        }
    }

    private fun card(parent: LinearLayout = content): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(panel, Color.rgb(43, 59, 82))
        }

        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, dp(5), 0, dp(7))
        parent.addView(box, lp)

        return box
    }

    private fun addLine(
        parent: LinearLayout,
        value: String,
        size: Float = 14f,
        color: Int = white,
        bold: Boolean = false
    ) {
        parent.addView(text(value, size, color, bold))
    }

    private fun gap(
        parent: LinearLayout = content,
        height: Int = 8
    ) {
        parent.addView(
            View(this),
            LinearLayout.LayoutParams(1, dp(height))
        )
    }

    private fun baseScreen(
        title: String,
        subtitle: String = ""
    ) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(panel)
        }

        val menu = text("☰", 25f, gold, true)
        menu.setPadding(dp(5), dp(2), dp(15), dp(2))
        menu.setOnClickListener { showNavigation() }
        header.addView(menu)

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        heading.addView(text(title, 17f, gold, true))

        if (subtitle.isNotBlank()) {
            heading.addView(text(subtitle, 10f, muted))
        }

        header.addView(heading)

        val settings = text("⚙", 23f, white)
        settings.setOnClickListener { showSettings() }
        header.addView(settings)

        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this).apply {
            setFillViewport(true)
        }

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(18))
        }

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(7), dp(5), dp(7))
            background = rounded(panel)
        }

        val items = listOf(
            "⌂\nAccueil",
            "＋\nTrade",
            "▤\nJournal",
            "◈\nStats",
            "☷\nMenu"
        )

        items.forEachIndexed { index, label ->
            val item = text(
                label,
                11f,
                if (index == 0) gold else muted
            )

            item.gravity = Gravity.CENTER
            item.setOnClickListener {
                when (index) {
                    0 -> showHome()
                    1 -> showAddTrade()
                    2 -> showHistory()
                    3 -> showStatistics()
                    else -> showNavigation()
                }
            }

            bottom.addView(
                item,
                LinearLayout.LayoutParams(0, -2, 1f)
            )
        }

        root.addView(bottom, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun showNavigation() {
        val options = arrayOf(
            "Accueil",
            "Dashboard",
            "Ajouter un trade",
            "Historique",
            "Statistiques",
            "Analyse graphique",
            "Gestion du risque",
            "Calculateur de lots",
            "Objectifs",
            "Assistant Spartan",
            "Paramètres"
        )

        AlertDialog.Builder(this)
            .setTitle("LEONIDAS — NAVIGATION")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showHome()
                    1 -> showDashboard()
                    2 -> showAddTrade()
                    3 -> showHistory()
                    4 -> showStatistics()
                    5 -> showAnalytics()
                    6 -> showRisk()
                    7 -> showLotCalculator()
                    8 -> showGoals()
                    9 -> showAdvice()
                    10 -> showSettings()
                }
            }
            .show()
    }

    private fun localClock(zone: String): String {
        return try {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone(zone)
            sdf.format(Date())
        } catch (_: Exception) {
            "--:--:--"
        }
    }

    private fun localMinute(zone: String): Int {
        val cal = Calendar.getInstance(TimeZone.getTimeZone(zone))

        return cal.get(Calendar.HOUR_OF_DAY) * 60 +
            cal.get(Calendar.MINUTE)
    }

    private fun sessionOpen(
        zone: String,
        start: Int,
        end: Int
    ): Boolean {
        val m = localMinute(zone)

        return if (start < end) {
            m in start until end
        } else {
            m >= start || m < end
        }
    }

    private fun countdown(
        zone: String,
        targetHour: Int
    ): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone(zone))

        val now =
            cal.get(Calendar.HOUR_OF_DAY) * 60 +
                cal.get(Calendar.MINUTE)

        var diff = targetHour * 60 - now

        if (diff <= 0) diff += 1440

        val hours = diff / 60
        val minutes = diff % 60

        return "${hours}h ${minutes}min"
    }

    private fun showHome() {
        baseScreen(
            "LEONIDAS SPARTAN GOLD",
            "FOREX SESSION CLOCK • 100 % HORS CONNEXION"
        )

        val hero = card()

        addLine(hero, "DISCIPLINE • PATIENCE • PRÉCISION", 12f, gold, true)
        addLine(hero, "OBJECTIF PATRIMONIAL", 12f, muted)
        addLine(hero, "1 000 000 USD", 28f, gold, true)

        addLine(
            hero,
            "Heure Madagascar : ${localClock("Indian/Antananarivo")}",
            15f,
            white,
            true
        )

        val clocks = card()
        addLine(clocks, "HORLOGES MONDIALES", 13f, cyan, true)

        val zones = listOf(
            "Sydney" to "Australia/Sydney",
            "Tokyo" to "Asia/Tokyo",
            "Londres" to "Europe/London",
            "New York" to "America/New_York"
        )

        zones.forEach { (name, zone) ->
            addLine(
                clocks,
                "$name : ${localClock(zone)}",
                14f
            )
        }

        val sessions = card()
        addLine(sessions, "SESSIONS DE MARCHÉ", 15f, gold, true)

        val data = listOf(
            Triple("SYDNEY", "Australia/Sydney", violet),
            Triple("TOKYO", "Asia/Tokyo", cyan),
            Triple("LONDRES", "Europe/London", green),
            Triple("NEW YORK", "America/New_York", orange)
        )

        data.forEach { (name, zone, tint) ->
            val open = sessionOpen(zone, 8 * 60, 17 * 60)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            row.addView(text("●", 17f, tint))

            val label = text(
                "$name • ${if (open) "OUVERTE" else "FERMÉE"}",
                13f,
                if (open) green else muted,
                true
            )

            row.addView(
                label,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            val remaining = if (open) {
                countdown(zone, 17)
            } else {
                countdown(zone, 8)
            }

            row.addView(
                text(
                    if (open) "Clôture $remaining"
                    else "Ouverture $remaining",
                    10f,
                    muted
                )
            )

            sessions.addView(row)
            gap(sessions, 5)

            val sessionDivider = View(this).apply {
    background = rounded(tint)
}

sessions.addView(
    sessionDivider,
    LinearLayout.LayoutParams(-1, dp(5)).apply {
        setMargins(0, dp(3), 0, dp(3))
    }
)
        }

        val map = card()
        addLine(map, "CARTE DES FUSEAUX", 13f, cyan, true)
        addLine(
            map,
            "Repère horaire mondial : Sydney • Tokyo • Londres • New York",
            12f,
            muted
        )
        addLine(
            map,
            "Les heures suivent automatiquement les fuseaux horaires.",
            11f,
            gold
        )

        val quick = card()
        addLine(quick, "ACCÈS RAPIDE", 14f, gold, true)

        quick.addView(
            button("＋ AJOUTER UN TRADE", { showAddTrade() }, gold)
        )

        gap(quick, 7)
        quick.addView(button("TABLEAU DE BORD", { showDashboard() }))
        gap(quick, 7)
        quick.addView(button("ANALYSE GRAPHIQUE", { showAnalytics() }))
        gap(quick, 7)
        quick.addView(button("JOURNAL & HISTORIQUE", { showHistory() }))
        gap(quick, 7)
        quick.addView(button("STATISTIQUES", { showStatistics() }))

        addLine(
            content,
            "Version locale : aucune donnée de marché en direct. Les calculs utilisent tes trades enregistrés.",
            11f,
            muted
        )
    }

    private fun pnlAt(i: Int): Double =
        trades.optJSONObject(i)?.optDouble("pnl", 0.0) ?: 0.0

    private fun countWins(): Int {
        var n = 0
        for (i in 0 until trades.length()) {
            if (pnlAt(i) > 0) n++
        }
        return n
    }

    private fun countLosses(): Int {
        var n = 0
        for (i in 0 until trades.length()) {
            if (pnlAt(i) < 0) n++
        }
        return n
    }

    private fun totalPnl(): Double {
        var sum = 0.0
        for (i in 0 until trades.length()) {
            sum += pnlAt(i)
        }
        return sum
    }

    private fun grossProfit(): Double {
        var sum = 0.0
        for (i in 0 until trades.length()) {
            if (pnlAt(i) > 0) sum += pnlAt(i)
        }
        return sum
    }

    private fun grossLoss(): Double {
        var sum = 0.0
        for (i in 0 until trades.length()) {
            if (pnlAt(i) < 0) sum += abs(pnlAt(i))
        }
        return sum
    }

    private fun winRate(): Double {
        return if (trades.length() == 0) 0.0
        else countWins() * 100.0 / trades.length()
    }

    private fun profitFactor(): Double {
        val loss = grossLoss()

        return if (loss == 0.0) {
            if (grossProfit() > 0) Double.POSITIVE_INFINITY else 0.0
        } else {
            grossProfit() / loss
        }
    }

    private fun pfText(): String {
        val pf = profitFactor()

        return if (pf.isInfinite()) "∞"
        else String.format(Locale.US, "%.2f", pf)
    }

    private fun money(value: Double): String {
        return String.format(Locale.US, "%,.2f %s", value, currency)
    }

    private fun drawdown(): Double {
        var equity = 0.0
        var peak = 0.0
        var maxDd = 0.0

        for (i in 0 until trades.length()) {
            equity += pnlAt(i)
            peak = max(peak, equity)
            maxDd = max(maxDd, peak - equity)
        }

        return maxDd
    }

    private fun bestTrade(highest: Boolean): Double? {
        if (trades.length() == 0) return null

        var value = pnlAt(0)

        for (i in 1 until trades.length()) {
            value = if (highest) {
                max(value, pnlAt(i))
            } else {
                min(value, pnlAt(i))
            }
        }

        return value
    }

    private fun groupBy(key: String): List<Pair<String, AssetStat>> {
        val map = LinkedHashMap<String, AssetStat>()

        for (i in 0 until trades.length()) {
            val t = trades.optJSONObject(i) ?: continue

            val name = t.optString(key, "—").ifBlank { "—" }
            val stat = map.getOrPut(name) { AssetStat() }

            val p = t.optDouble("pnl", 0.0)
            val lot = t.optDouble("lot", 0.0)

            stat.count++
            stat.lots += lot
            stat.pnl += p

            if (p > 0) stat.wins++
            if (p < 0) stat.losses++
        }

        return map.entries
            .map { it.key to it.value }
            .sortedByDescending { it.second.count }
    }

    private fun cumulativePoints(): List<Double> {
        val list = ArrayList<Double>()
        var running = 0.0

        list.add(0.0)

        for (i in 0 until trades.length()) {
            running += pnlAt(i)
            list.add(running)
        }

        return list
    }

    private fun equityCard() {
        val c = card()

        addLine(c, "COURBE DE CAPITAL — P&L CUMULÉ", 13f, cyan, true)

        if (trades.length() == 0) {
            addLine(c, "Enregistre des trades pour afficher ta progression.", 12f, muted)
            return
        }

        c.addView(
            LineView(
                this,
                cumulativePoints(),
                gold,
                Color.rgb(57, 75, 100),
                muted
            ),
            LinearLayout.LayoutParams(-1, dp(180))
        )
    }

    private fun donutCard(
        title: String,
        entries: List<Pair<String, Double>>,
        centerTop: String,
        centerSub: String
    ) {
        val c = card()
        addLine(c, title, 13f, cyan, true)

        val data = entries.filter { it.second > 0.0 }

        if (data.isEmpty()) {
            addLine(c, "Pas encore de données.", 12f, muted)
            return
        }

        val colors = data.indices.map {
            palette[it % palette.size]
        }

        c.addView(
            DonutView(
                this,
                data.map { it.second },
                colors,
                centerTop,
                centerSub,
                white,
                muted
            ),
            LinearLayout.LayoutParams(-1, dp(190))
        )

        val total = data.sumOf { it.second }

        data.forEachIndexed { i, entry ->
            val percent = entry.second * 100.0 / total

            addLine(
                c,
                "● ${entry.first} : ${String.format(Locale.US, "%.1f", percent)} %",
                12f,
                colors[i]
            )
        }
    }

    private fun showDashboard() {
        baseScreen("TABLEAU DE BORD", "SYNTHÈSE DE PERFORMANCE")

        val total = totalPnl()

        val hero = card()

        addLine(hero, "BONJOUR, $firstName", 13f, cyan, true)
        addLine(hero, "P&L NET", 12f, muted)
        addLine(hero, money(total), 29f, if (total >= 0) green else red, true)

        addLine(
            hero,
            "${trades.length()} trades • ${String.format(Locale.US, "%.1f", winRate())}% gagnants",
            13f
        )

        val stats = card()

        addLine(stats, "INDICATEURS CLÉS", 14f, gold, true)
        addLine(stats, "Profit Factor : ${pfText()}", 15f)
        addLine(stats, "Drawdown maximum : ${money(drawdown())}")
        addLine(stats, "Gagnants : ${countWins()} | Perdants : ${countLosses()}")

        val best = bestTrade(true)
        val worst = bestTrade(false)

        addLine(
            stats,
            "Meilleur trade : ${best?.let { money(it) } ?: "—"}",
            13f,
            green
        )

        addLine(
            stats,
            "Pire trade : ${worst?.let { money(it) } ?: "—"}",
            13f,
            red
        )

        val goal = card()

        val pct = if (targetAmount > 0) {
            (total / targetAmount * 100.0).coerceIn(0.0, 100.0)
        } else 0.0

        addLine(goal, "OBJECTIF : ${money(targetAmount)}", 14f, gold, true)

        addLine(
            goal,
            "${String.format(Locale.US, "%.4f", pct)} % de progression",
            12f,
            muted
        )

        val progress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 1000
            this.progress = (pct * 10).toInt()
            progressTintList = ColorStateList.valueOf(gold)
        }

        goal.addView(
            progress,
            LinearLayout.LayoutParams(-1, dp(10))
        )

        equityCard()

        donutCard(
            "RÉSULTATS DES TRADES",
            listOf(
                "Gagnants" to countWins().toDouble(),
                "Perdants" to countLosses().toDouble(),
                "Break-even" to
                    (trades.length() - countWins() - countLosses()).toDouble()
            ),
            "${String.format(Locale.US, "%.0f", winRate())}%",
            "Win rate"
        )

        gap()

        content.addView(
            button("ANALYSE GRAPHIQUE", { showAnalytics() })
        )

        gap(content, 7)

        content.addView(
            button("＋ ENREGISTRER UN TRADE", { showAddTrade() }, gold)
        )
    }

    private fun field(
        parent: LinearLayout,
        label: String,
        hint: String = "",
        initial: String = ""
    ): EditText {
        parent.addView(text(label, 12f, muted, true))

        val edit = EditText(this).apply {
            setSingleLine(true)
            textSize = 14f
            setTextColor(white)
            setHintTextColor(muted)
            setHint(hint)
            setText(initial)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(panel2, Color.rgb(57, 75, 100))
        }

        val lp = LinearLayout.LayoutParams(-1, dp(48))
        lp.setMargins(0, dp(3), 0, dp(9))

        parent.addView(edit, lp)

        return edit
    }

    private fun spinner(
        parent: LinearLayout,
        label: String,
        options: Array<String>
    ): Spinner {
        parent.addView(text(label, 12f, muted, true))

        val sp = Spinner(this)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            options
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        sp.adapter = adapter

        parent.addView(sp, LinearLayout.LayoutParams(-1, dp(48)))

        return sp
    }

    private fun num(edit: EditText): Double {
        return edit.text.toString()
            .trim()
            .replace(",", ".")
            .toDoubleOrNull() ?: 0.0
    }

    private fun today(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun countTradesOnDate(dateValue: String): Int {
    var count = 0

    for (i in 0 until trades.length()) {
        if (trades.optJSONObject(i)?.optString("date") == dateValue) {
            count++
        }
    }

    return count
}

private fun countTradesToday(): Int {
    return countTradesOnDate(today())
}

private fun dailyPnlOnDate(dateValue: String): Double {
    var total = 0.0

    for (i in 0 until trades.length()) {
        val trade = trades.optJSONObject(i) ?: continue

        if (trade.optString("date") == dateValue) {
            total += trade.optDouble("pnl", 0.0)
        }
    }

    return total
}

    private fun showAddTrade() {
        baseScreen("NOUVEAU TRADE", "ENREGISTREMENT DU JOURNAL")

        val form = card()

        val asset = field(form, "ACTIF", "Ex. XAUUSD")
        val direction = spinner(form, "DIRECTION", arrayOf("BUY", "SELL"))
        val date = field(form, "DATE", "AAAA-MM-JJ", today())
        val entry = field(form, "PRIX D'ENTRÉE", "0.00")
        val stop = field(form, "STOP LOSS", "0.00")
        val target = field(form, "TAKE PROFIT", "0.00")
        val lot = field(form, "VOLUME / LOT", "0.01")
        val risk = field(form, "RISQUE INITIAL ($)", "Montant risqué")
        val exit = field(form, "PRIX DE SORTIE", "0.00")
        val pnl = field(form, "P&L RÉEL ($)", "Résultat MT5")
        val mae = field(form, "MAE ($)", "0")
        val mfe = field(form, "MFE ($)", "0")

        val session = spinner(
            form,
            "SESSION",
            arrayOf("Sydney", "Tokyo", "Londres", "New York", "Hors session")
        )

        val emotion = spinner(
            form,
            "ÉMOTION",
            arrayOf("Calme", "Confiant", "Peur", "Stress", "Impatient", "Euphorique")
        )

        val plan = spinner(
            form,
            "PLAN RESPECTÉ ?",
            arrayOf("Oui", "Non")
        )

        val error = spinner(
            form,
            "ERREUR",
            arrayOf(
                "Aucune",
                "FOMO",
                "Surtrading",
                "Revenge trading",
                "SL déplacé",
                "Sortie prématurée",
                "Autre"
            )
        )

        val notes = field(form, "NOTES", "Analyse et leçon à retenir")

        val rrPreview = text("R MULTIPLE : —", 13f, cyan, true)
        form.addView(rrPreview)

        val updateR = {
            val riskValue = num(risk)
            val profit = num(pnl)

            rrPreview.text = if (riskValue > 0) {
                "R MULTIPLE : ${String.format(Locale.US, "%.2f", profit / riskValue)} R"
            } else {
                "R MULTIPLE : renseigne le risque initial"
            }
        }

        pnl.setOnFocusChangeListener { _, _ -> updateR() }
        risk.setOnFocusChangeListener { _, _ -> updateR() }

        gap()

        content.addView(
            button(
                "ENREGISTRER LE TRADE",
                {
                    if (asset.text.toString().trim().isEmpty()) {
                        Toast.makeText(
                            this,
                            "Renseigne l'actif.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@button
                    }

                    if (num(risk) < 0 || num(lot) < 0) {
                        Toast.makeText(
                            this,
                            "Valeurs de risque ou de lot invalides.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@button
                    }

                    if (countTradesToday() >= maxTradesPerDay) {
                        AlertDialog.Builder(this)
                            .setTitle("DISCIPLINE VERROUILLÉE")
                            .setMessage(
                                "Limite quotidienne atteinte : $maxTradesPerDay trades."
                            )
                            .setPositiveButton("OK", null)
                            .show()

                        return@button
                    }

                    if (dailyRiskLimit > 0 && num(pnl) < -dailyRiskLimit) {
                        AlertDialog.Builder(this)
                            .setTitle("ALERTE DE RISQUE")
                            .setMessage(
                                "La perte dépasse la limite quotidienne configurée. Confirmer ?"
                            )
                            .setNegativeButton("Annuler", null)
                            .setPositiveButton("Enregistrer") { _, _ ->
                                storeTrade(
                                    asset, direction, date, entry, stop,
                                    target, lot, risk, exit, pnl, mae,
                                    mfe, session, emotion, plan, error, notes
                                )
                            }
                            .show()
                    } else {
                        storeTrade(
                            asset, direction, date, entry, stop,
                            target, lot, risk, exit, pnl, mae,
                            mfe, session, emotion, plan, error, notes
                        )
                    }
                },
                gold
            )
        )

        addLine(
            content,
            "Le P&L doit être renseigné à partir du résultat réel de MT5. Les contrats et valeurs de point varient selon les brokers.",
            11f,
            muted
        )
    }

    private fun storeTrade(
        asset: EditText,
        direction: Spinner,
        date: EditText,
        entry: EditText,
        stop: EditText,
        target: EditText,
        lot: EditText,
        risk: EditText,
        exit: EditText,
        pnl: EditText,
        mae: EditText,
        mfe: EditText,
        session: Spinner,
        emotion: Spinner,
        plan: Spinner,
        error: Spinner,
        notes: EditText
    ) {
        val riskValue = num(risk)
        val realized = num(pnl)

        val obj = JSONObject().apply {
            put("asset", asset.text.toString().trim().uppercase(Locale.ROOT))
            put("direction", direction.selectedItem.toString())
            put("date", date.text.toString().ifBlank { today() })
            put("entry", num(entry))
            put("stop", num(stop))
            put("target", num(target))
            put("lot", num(lot))
            put("risk", riskValue)
            put("exit", num(exit))
            put("pnl", realized)
            put(
                "rMultiple",
                if (riskValue > 0) realized / riskValue else 0.0
            )
            put("mae", num(mae))
            put("mfe", num(mfe))
            put("session", session.selectedItem.toString())
            put("emotion", emotion.selectedItem.toString())
            put("plan", plan.selectedItem.toString())
            put("error", error.selectedItem.toString())
            put("notes", notes.text.toString())
            put("created", System.currentTimeMillis())
        }

        trades.put(obj)
        saveTrades()

        Toast.makeText(
            this,
            "Trade enregistré et sauvegardé !",
            Toast.LENGTH_SHORT
        ).show()

        showDashboard()
    }

    private fun showHistory() {
        baseScreen(
            "JOURNAL & HISTORIQUE",
            "${trades.length()} TRADES ENREGISTRÉS"
        )

        if (trades.length() == 0) {
            val empty = card()

            addLine(empty, "JOURNAL VIDE", 15f, muted, true)
            addLine(empty, "Ajoute ton premier trade.", 12f, muted)

            content.addView(
                button("AJOUTER UN TRADE", { showAddTrade() }, gold)
            )

            return
        }

        val summary = card()

        addLine(
            summary,
            "P&L TOTAL : ${money(totalPnl())}",
            18f,
            if (totalPnl() >= 0) green else red,
            true
        )

        addLine(
            summary,
            "Win rate : ${String.format(Locale.US, "%.1f", winRate())}% • PF : ${pfText()}",
            12f,
            muted
        )

        summary.addView(
            button("ANALYSE GRAPHIQUE", { showAnalytics() })
        )

        for (i in trades.length() - 1 downTo 0) {
            val obj = trades.optJSONObject(i) ?: continue
            val item = card()
            val p = obj.optDouble("pnl", 0.0)

            addLine(
                item,
                "${obj.optString("asset", "—")} • ${obj.optString("direction", "—")} • ${obj.optString("date", "—")}",
                14f,
                gold,
                true
            )

            addLine(
                item,
                money(p),
                18f,
                if (p >= 0) green else red,
                true
            )

            addLine(
                item,
                "Lot ${obj.optString("lot", "—")} | ${obj.optString("session", "—")} | ${String.format(Locale.US, "%.2f", obj.optDouble("rMultiple", 0.0))} R",
                11f,
                muted
            )

            addLine(
                item,
                "Émotion : ${obj.optString("emotion", "—")} • Erreur : ${obj.optString("error", "—")}",
                11f,
                muted
            )

            addLine(item, obj.optString("notes", ""), 11f)

            item.addView(
                button(
                    "SUPPRIMER CE TRADE",
                    { confirmDelete(i) },
                    Color.rgb(104, 43, 53)
                )
            )
        }

        content.addView(
            button("＋ AJOUTER UN TRADE", { showAddTrade() }, gold)
        )
    }

    private fun confirmDelete(index: Int) {
        AlertDialog.Builder(this)
            .setTitle("SUPPRIMER CE TRADE ?")
            .setMessage("Cette action est définitive.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer") { _, _ ->
                val updated = JSONArray()

                for (i in 0 until trades.length()) {
                    if (i != index) {
                        updated.put(trades.get(i))
                    }
                }

                trades = updated
                saveTrades()
                showHistory()
            }
            .show()
    }

    private fun averageField(key: String): Double {
        if (trades.length() == 0) return 0.0

        var sum = 0.0

        for (i in 0 until trades.length()) {
            sum += trades.optJSONObject(i)?.optDouble(key, 0.0) ?: 0.0
        }

        return sum / trades.length()
    }

    private fun showStatistics() {
        baseScreen(
            "STATISTIQUES & RAPPORTS",
            "DONNÉES CALCULÉES SUR TES TRADES"
        )

        val c = card()

        addLine(c, "RÉSUMÉ", 14f, gold, true)
        addLine(c, "Nombre total : ${trades.length()}")
        addLine(c, "Gagnants : ${countWins()} | Perdants : ${countLosses()}")

        addLine(
            c,
            "Win rate : ${String.format(Locale.US, "%.2f", winRate())}%"
        )

        addLine(
            c,
            "P&L net : ${money(totalPnl())}",
            18f,
            if (totalPnl() >= 0) green else red,
            true
        )

        val pl = card()

        addLine(pl, "PROFIT & LOSS", 14f, cyan, true)
        addLine(pl, "Profit brut : ${money(grossProfit())}", 13f, green)
        addLine(pl, "Perte brute : ${money(grossLoss())}", 13f, red)
        addLine(pl, "Profit Factor : ${pfText()}")
        addLine(pl, "Drawdown maximum : ${money(drawdown())}")

        val longShort = card()

        var buys = 0
        var sells = 0
        var buyPnl = 0.0
        var sellPnl = 0.0

        for (i in 0 until trades.length()) {
            val t = trades.optJSONObject(i) ?: continue

            if (t.optString("direction") == "BUY") {
                buys++
                buyPnl += t.optDouble("pnl")
            } else {
                sells++
                sellPnl += t.optDouble("pnl")
            }
        }

        addLine(longShort, "LONG & SHORT", 14f, gold, true)
        addLine(longShort, "BUY : $buys trades • ${money(buyPnl)}")
        addLine(longShort, "SELL : $sells trades • ${money(sellPnl)}")

        val discipline = card()
        var broken = 0

        for (i in 0 until trades.length()) {
            if (trades.optJSONObject(i)?.optString("plan") == "Non") {
                broken++
            }
        }

        addLine(discipline, "DISCIPLINE", 14f, gold, true)
        addLine(discipline, "Trades hors plan : $broken")

        val respect = if (trades.length() > 0) {
            (trades.length() - broken) * 100.0 / trades.length()
        } else 0.0

        addLine(
            discipline,
            "Respect du plan : ${String.format(Locale.US, "%.1f", respect)} %"
        )

        addLine(
            discipline,
            "MAE moyen : ${money(averageField("mae"))}"
        )

        addLine(
            discipline,
            "MFE moyen : ${money(averageField("mfe"))}"
        )

        content.addView(
            button("ANALYSE GRAPHIQUE", { showAnalytics() }, gold)
        )
    }

    private fun showAnalytics() {
        baseScreen(
            "ANALYSE GRAPHIQUE",
            "RÉPARTITIONS • VOLUMES • PERFORMANCE"
        )

        if (trades.length() == 0) {
            val c = card()

            addLine(c, "AUCUNE DONNÉE À ANALYSER", 14f, muted, true)
            addLine(c, "Enregistre des trades pour voir tes graphiques.", 12f)

            content.addView(
                button("AJOUTER UN TRADE", { showAddTrade() }, gold)
            )

            return
        }

        val assets = groupBy("asset")
        val sessions = groupBy("session")
        val directions = groupBy("direction")

        val best = assets.maxByOrNull { it.second.pnl }
        val worst = assets.minByOrNull { it.second.pnl }

        val insights = card()

        addLine(insights, "INSIGHTS ACTIONNABLES", 14f, gold, true)

        best?.let {
            addLine(
                insights,
                "Meilleur actif : ${it.first} • ${money(it.second.pnl)}",
                12f,
                green
            )
        }

        worst?.let {
            addLine(
                insights,
                "Actif le plus faible : ${it.first} • ${money(it.second.pnl)}",
                12f,
                red
            )
        }

        val totalLots = assets.sumOf { it.second.lots }

        donutCard(
            "VOLUME PAR ACTIF",
            assets.map { it.first to it.second.lots },
            String.format(Locale.US, "%.2f", totalLots),
            "Lots"
        )

        donutCard(
            "ACTIFS LES PLUS TRADÉS",
            assets.map { it.first to it.second.count.toDouble() },
            "${trades.length()}",
            "Trades"
        )

        donutCard(
            "RÉSULTATS DES TRADES",
            listOf(
                "Gagnants" to countWins().toDouble(),
                "Perdants" to countLosses().toDouble(),
                "Break-even" to
                    (trades.length() - countWins() - countLosses()).toDouble()
            ),
            "${String.format(Locale.US, "%.0f", winRate())}%",
            "Win rate"
        )

        donutCard(
            "RÉPARTITION PAR SESSION",
            sessions.map { it.first to it.second.count.toDouble() },
            "${sessions.size}",
            "Sessions"
        )

        donutCard(
            "BUY / SELL — VOLUME",
            directions.map { it.first to it.second.lots },
            String.format(Locale.US, "%.2f", totalLots),
            "Lots"
        )

        val byAsset = card()

        addLine(byAsset, "P&L PAR ACTIF", 14f, cyan, true)

        assets.sortedByDescending { it.second.pnl }.forEach {
            addLine(
                byAsset,
                "${it.first} : ${money(it.second.pnl)}",
                12f,
                if (it.second.pnl >= 0) green else red
            )
        }

        val bySession = card()

        addLine(bySession, "P&L PAR SESSION", 14f, cyan, true)

        sessions.forEach {
            addLine(
                bySession,
                "${it.first} : ${money(it.second.pnl)}",
                12f,
                if (it.second.pnl >= 0) green else red
            )
        }

        equityCard()
    }

    private fun showRisk() {
        baseScreen(
            "GESTION DU RISQUE",
            "PROTÈGE TON CAPITAL"
        )

        val c = card()

        val capital = field(c, "CAPITAL DISPONIBLE ($)", "Ex. 1000")
        val percent = field(c, "RISQUE PAR TRADE (%)", "Ex. 1")
        val sl = field(c, "STOP LOSS (POINTS / PIPS)", "Ex. 250")
        val value = field(c, "VALEUR PAR POINT POUR 1 LOT ($)", "Ex. 1")

        val result = text(
            "TAILLE DE LOT ESTIMÉE : —",
            16f,
            gold,
            true
        )

        c.addView(result)

        c.addView(
            button(
                "CALCULER LA TAILLE",
                {
                    val cap = num(capital)
                    val riskPct = num(percent)
                    val stop = num(sl)
                    val point = num(value)

                    if (
                        cap <= 0.0 ||
                        riskPct <= 0.0 ||
                        riskPct > 100.0 ||
                        stop <= 0.0 ||
                        point <= 0.0
                    ) {
                        result.text = "VÉRIFIE LES VALEURS SAISIES"
                    } else {
                        val riskAmount = cap * riskPct / 100.0
                        val denominator = stop * point
                        val lots = riskAmount / denominator

                        result.text =
                            "Risque : ${money(riskAmount)}\n" +
                            "Taille estimée : ${String.format(Locale.US, "%.4f", lots)} lot"
                    }
                },
                gold
            )
        )

        val rules = card()

        addLine(rules, "RÈGLES DE DISCIPLINE", 14f, cyan, true)
        addLine(rules, "Trades maximum par jour : $maxTradesPerDay")

        addLine(
            rules,
            "Limite de perte quotidienne : " +
                if (dailyRiskLimit > 0) money(dailyRiskLimit)
                else "Non configurée"
        )

        addLine(rules, "Trades pris aujourd'hui : ${countTradesToday()}")
        addLine(
            rules,
            "Vérifie la valeur du point chez ton broker.",
            12f,
            orange
        )

        content.addView(
            button("CONFIGURER MES LIMITES", { showSettings() })
        )
    }

    private fun showLotCalculator() {
        baseScreen(
            "CALCULATEUR DE LOTS",
            "ESTIMATION — VÉRIFIE LA VALEUR DU POINT"
        )

        val c = card()

        addLine(c, "PARAMÈTRES DE CALCUL", 14f, gold, true)

        val cap = field(c, "CAPITAL DISPONIBLE ($)", "Ex. 1000")
        val risk = field(c, "RISQUE PAR TRADE (%)", "Ex. 1")
        val sl = field(c, "STOP LOSS (POINTS / PIPS)", "Ex. 250")

        val pointValue = field(
            c,
            "VALEUR DU POINT POUR 1 LOT ($)",
            "Ex. 1"
        )

        val result = text(
            "TAILLE DU LOT : —",
            18f,
            gold,
            true
        )

        c.addView(result)

        val details = text(
            "Montant risqué : —\nPerte estimée au SL : —",
            13f,
            white
        )

        c.addView(details)

        c.addView(
            button(
                "CALCULER",
                {
                    val capital = num(cap)
                    val riskPercent = num(risk)
                    val stopLoss = num(sl)
                    val valuePerPoint = num(pointValue)

                    if (
                        capital <= 0.0 ||
                        riskPercent <= 0.0 ||
                        riskPercent > 100.0 ||
                        stopLoss <= 0.0 ||
                        valuePerPoint <= 0.0
                    ) {
                        result.text = "VÉRIFIE LES VALEURS SAISIES"
                        details.text =
                            "Tous les paramètres doivent être valides."
                    } else {
                        val riskAmount =
                            capital * riskPercent / 100.0

                        val lossPerLot =
                            stopLoss * valuePerPoint

                        val lotSize =
                            riskAmount / lossPerLot

                        if (!lotSize.isFinite() || lotSize <= 0.0) {
                            result.text = "ERREUR DE CALCUL"
                            details.text =
                                "Vérifie les paramètres et la valeur du point."
                        } else {
                            result.text =
                                "TAILLE DU LOT : ${
                                    String.format(
                                        Locale.US,
                                        "%.4f",
                                        lotSize
                                    )
                                }"

                            details.text =
                                "Capital : ${money(capital)}\n" +
                                "Risque : ${money(riskAmount)}\n" +
                                "Perte estimée au SL : ${money(riskAmount)}\n" +
                                "Perte pour 1 lot : ${money(lossPerLot)}"
                        }
                    }
                },
                gold
            )
        )

        val warning = card()

        addLine(warning, "AVERTISSEMENT", 14f, orange, true)

        addLine(
            warning,
            "La valeur du point dépend de l'actif, du contrat et du broker. Vérifie-la dans MT5.",
            12f,
            orange
        )

        addLine(
            warning,
            "Le résultat est une estimation et ne tient pas compte des commissions, spreads et slippage.",
            12f
        )
    }

    private fun showGoals() {
        baseScreen("OBJECTIFS", "DISCIPLINE ET PROGRESSION")

        val c = card()

        addLine(c, "OBJECTIF PATRIMONIAL", 14f, gold, true)
        addLine(c, money(targetAmount), 25f, gold, true)

        val current = totalPnl()

        val pct = if (targetAmount > 0) {
            (current / targetAmount * 100.0).coerceIn(0.0, 100.0)
        } else 0.0

        addLine(
            c,
            "Progression : ${String.format(Locale.US, "%.4f", pct)} %",
            13f,
            cyan
        )

        val progress = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 1000
            this.progress = (pct * 10).toInt()
            progressTintList = ColorStateList.valueOf(gold)
        }

        c.addView(progress, LinearLayout.LayoutParams(-1, dp(10)))

        val edit = field(
            c,
            "MODIFIER L'OBJECTIF ($currency)",
            "1000000",
            targetAmount.toString()
        )

        c.addView(
            button(
                "SAUVEGARDER L'OBJECTIF",
                {
                    val value = num(edit)

                    if (value > 0) {
                        targetAmount = value
                        saveSettings()
                        showGoals()
                    } else {
                        Toast.makeText(
                            this,
                            "Objectif invalide.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                gold
            )
        )

        val note = card()

        addLine(note, "RÈGLE SPARTAN", 14f, cyan, true)
        addLine(
            note,
            "L'objectif ne justifie jamais de prendre un risque excessif. La protection du capital reste prioritaire.",
            12f,
            white
        )
    }

    private fun showAdvice() {
        baseScreen(
            "ASSISTANT SPARTAN",
            "DISCIPLINE • MÉTHODE • PROGRESSION"
        )

        val c = card()

        addLine(c, "LES 5 RÈGLES SPARTAN", 15f, gold, true)

        val rules = listOf(
            "01 — Protège ton capital avant de chercher le profit.",
            "02 — Ne risque jamais plus que ton plan ne l'autorise.",
            "03 — Ne déplace pas ton stop loss par émotion.",
            "04 — Enregistre chaque trade dans ton journal.",
            "05 — Analyse tes erreurs avant de chercher de nouvelles stratégies."
        )

        rules.forEach {
            addLine(c, it, 13f, white)
            gap(c, 5)
        }

        val discipline = card()

        addLine(discipline, "TON BILAN", 14f, cyan, true)
        addLine(discipline, "Trades enregistrés : ${trades.length()}")
        addLine(discipline, "Win rate : ${String.format(Locale.US, "%.1f", winRate())} %")
        addLine(discipline, "Profit Factor : ${pfText()}")
        addLine(discipline, "Drawdown : ${money(drawdown())}")

        addLine(
            discipline,
            "Ces indicateurs décrivent les résultats enregistrés, pas les performances futures.",
            11f,
            orange
        )
    }

    private fun showSettings() {
        baseScreen("PARAMÈTRES", "CONFIGURATION LEONIDAS")

        val c = card()

        val name = field(
            c,
            "NOM DU TRADER",
            "Spartan",
            firstName
        )

        val curr = spinner(
            c,
            "DEVISE DU JOURNAL",
            arrayOf("USD", "EUR", "GBP", "JPY", "MGA")
        )

        val currencyIndex = when (currency) {
            "EUR" -> 1
            "GBP" -> 2
            "JPY" -> 3
            "MGA" -> 4
            else -> 0
        }

        curr.setSelection(currencyIndex)

        val limit = field(
            c,
            "LIMITE DE PERTE QUOTIDIENNE ($)",
            "0 = désactivée",
            dailyRiskLimit.toString()
        )

        val maxTrades = field(
            c,
            "NOMBRE MAXIMUM DE TRADES PAR JOUR",
            "4",
            maxTradesPerDay.toString()
        )

        c.addView(
            button(
                "SAUVEGARDER LES PARAMÈTRES",
                {
                    val maxValue = maxTrades.text.toString().toIntOrNull()

                    if (maxValue == null || maxValue < 1) {
                        Toast.makeText(
                            this,
                            "Nombre maximum de trades invalide.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@button
                    }

                    if (num(limit) < 0) {
                        Toast.makeText(
                            this,
                            "Limite de risque invalide.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@button
                    }

                    firstName = name.text.toString().ifBlank { "Spartan" }
                    currency = curr.selectedItem.toString()
                    dailyRiskLimit = num(limit)
                    maxTradesPerDay = maxValue

                    saveSettings()

                    Toast.makeText(
                        this,
                        "Paramètres sauvegardés.",
                        Toast.LENGTH_SHORT
                    ).show()

                    showHome()
                },
                gold
            )
        )

        val info = card()

        addLine(info, "INFORMATIONS", 14f, cyan, true)
        addLine(info, "LEONIDAS SPARTAN GOLD")
        addLine(info, "Journal local hors connexion")
        addLine(info, "Stockage : mémoire locale Android")
        addLine(info, "Données de marché en direct : non connectées")
    }
}
