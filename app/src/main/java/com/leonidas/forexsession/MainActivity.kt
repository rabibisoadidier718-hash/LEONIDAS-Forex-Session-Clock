package com.leonidas.forexsession

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max

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

    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private var trades = JSONArray()
    private var firstName = "Spartan"
    private var currency = "USD"
    private var dailyRiskLimit = 0.0
    private var maxTradesPerDay = 4

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
        dailyRiskLimit = p.getString("daily_risk_limit", "0")?.toDoubleOrNull() ?: 0.0
        maxTradesPerDay = p.getInt("max_trades_per_day", 4)
    }

    private fun loadTrades() {
        val raw = getSharedPreferences(prefsName, MODE_PRIVATE).getString(tradesKey, "[]") ?: "[]"
        trades = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
    }

    private fun saveTrades() {
        getSharedPreferences(prefsName, MODE_PRIVATE).edit().putString(tradesKey, trades.toString()).apply()
    }

    private fun saveSettings() {
        getSharedPreferences(prefsName, MODE_PRIVATE).edit()
            .putString("first_name", firstName)
            .putString("currency", currency)
            .putString("daily_risk_limit", dailyRiskLimit.toString())
            .putInt("max_trades_per_day", maxTradesPerDay)
            .apply()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, stroke: Int = Color.TRANSPARENT): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(14).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
    }

    private fun text(value: String, size: Float = 14f, color: Int = white, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(2), dp(3), dp(2), dp(3))
        }
    }

    private fun button(label: String, action: () -> Unit, color: Int = panel2): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(color, if (color == gold) gold else Color.rgb(47, 67, 94))
            if (color == gold) setTextColor(Color.rgb(18, 24, 34))
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

    private fun addLine(parent: LinearLayout, value: String, size: Float = 14f, color: Int = white, bold: Boolean = false) {
        parent.addView(text(value, size, color, bold))
    }

    private fun gap(parent: LinearLayout = content, height: Int = 8) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun baseScreen(title: String, subtitle: String = "") {
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
        val menu = TextView(this).apply {
            text = "☰"
            textSize = 25f
            setTextColor(gold)
            setPadding(dp(5), dp(2), dp(15), dp(2))
            setOnClickListener { showNavigation() }
        }
        header.addView(menu)
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        heading.addView(text(title, 17f, gold, true))
        if (subtitle.isNotBlank()) heading.addView(text(subtitle, 10f, muted))
        header.addView(heading)
        val settings = TextView(this).apply {
            text = "⚙"
            textSize = 23f
            setTextColor(white)
            setPadding(dp(10), dp(3), dp(4), dp(3))
            setOnClickListener { showSettings() }
        }
        header.addView(settings)
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        val scroll = ScrollView(this).apply { setFillViewport(true) }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(18))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(7), dp(5), dp(7))
            background = rounded(panel)
        }
        val items = listOf("⌂\nAccueil", "＋\nTrade", "▤\nJournal", "◈\nStats", "☷\nMenu")
        items.forEachIndexed { index, label ->
            val item = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 11f
                setTextColor(if (index == 1) gold else muted)
                setPadding(dp(3), dp(5), dp(3), dp(5))
                setOnClickListener {
                    when (index) {
                        0 -> showHome()
                        1 -> showAddTrade()
                        2 -> showHistory()
                        3 -> showStatistics()
                        else -> showNavigation()
                    }
                }
            }
            bottom.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(bottom, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun showNavigation() {
        val options = arrayOf("Accueil", "Dashboard", "Ajouter un trade", "Historique", "Journal & calendrier", "Statistiques", "Gestion du risque", "Calculateur de lots", "Objectifs", "Assistant Spartan", "Conseils", "Paramètres")
        AlertDialog.Builder(this).setTitle("LEONIDAS — Navigation").setItems(options) { _, which ->
            when (which) {
                0 -> showHome()
                1 -> showDashboard()
                2 -> showAddTrade()
                3, 4 -> showHistory()
                5 -> showStatistics()
                6 -> showRisk()
                7 -> showLotCalculator()
                8 -> showGoals()
                9, 10 -> showAdvice()
                11 -> showSettings()
            }
        }.show()
    }

    private fun localClock(zone: String): String {
        return try {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone(zone)
            sdf.format(Date())
        } catch (_: Exception) { "--:--:--" }
    }

    private fun localMinute(zone: String): Int {
        val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone(zone))
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    }

    private fun sessionOpen(zone: String, start: Int, end: Int): Boolean {
        val m = localMinute(zone)
        return if (start < end) m in start until end else m >= start || m < end
    }

    private fun countdown(zone: String, targetHour: Int): String {
        val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone(zone))
        val now = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        var diff = targetHour * 60 - now
        if (diff <= 0) diff += 1440
        return "${diff / 60} h ${diff % 60} min"
    }

    private fun showHome() {
        baseScreen("LEONIDAS SPARTAN GOLD", "FOREX SESSION CLOCK  •  100 % HORS CONNEXION")
        val hero = card()
        addLine(hero, "DISCIPLINE  •  PATIENCE  •  PRÉCISION", 12f, gold, true)
        addLine(hero, "Objectif patrimonial", 12f, muted)
        addLine(hero, "1 000 000 USD", 28f, gold, true)
        addLine(hero, "Heure Madagascar (UTC+3) : ${localClock("Indian/Antananarivo")}", 15f, white, true)
        val clocks = card()
        addLine(clocks, "HORLOGES MONDIALES", 13f, cyan, true)
        listOf(
            "Sydney" to "Australia/Sydney",
            "Tokyo" to "Asia/Tokyo",
            "Londres" to "Europe/London",
            "New York" to "America/New_York"
        ).forEach { (name, zone) -> addLine(clocks, "$name   ${localClock(zone)}", 14f) }
        val sessions = card()
        addLine(sessions, "SESSIONS DE MARCHÉ", 15f, gold, true)
        val data = listOf(
            Triple("SYDNEY", "Australia/Sydney", Color.rgb(160, 119, 235)),
            Triple("TOKYO", "Asia/Tokyo", cyan),
            Triple("LONDRES", "Europe/London", green),
            Triple("NEW YORK", "America/New_York", orange)
        )
        data.forEach { (name, zone, tint) ->
            val open = sessionOpen(zone, 8 * 60, 17 * 60)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val dot = TextView(this).apply { text = "●"; textSize = 17f; setTextColor(tint) }
            row.addView(dot)
            val label = TextView(this).apply {
                text = "  $name  •  ${if (open) "OUVERTE" else "FERMÉE"}"
                textSize = 13f
                setTextColor(if (open) green else muted)
                setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            row.addView(label)
            row.addView(text(if (open) "Clôture ${countdown(zone, 17)}" else "Ouverture ${countdown(zone, 8)}", 11f, muted))
            sessions.addView(row)
            gap(sessions, 5)
            val bar = View(this).apply { background = rounded(tint) }
            sessions.addView(bar, LinearLayout.LayoutParams(-1, dp(7)))
            gap(sessions, 5)
        }
        val map = card()
        addLine(map, "CARTE DES FUSEAUX", 13f, cyan, true)
        addLine(map, "Repère horaire mondial — les horaires s’adaptent automatiquement aux changements d’heure des fuseaux.", 12f, muted)
        addLine(map, "◉ Sydney     ◉ Tokyo     ◉ Londres     ◉ New York", 12f, gold)
        gap()
        val quick = card()
        addLine(quick, "ACCÈS RAPIDE", 14f, gold, true)
        quick.addView(button("＋  AJOUTER UN TRADE", { showAddTrade() }, gold))
        gap(quick, 7)
        quick.addView(button("TABLEAU DE BORD", { showDashboard() }))
        gap(quick, 7)
        quick.addView(button("JOURNAL & HISTORIQUE", { showHistory() }))
        gap(quick, 7)
        quick.addView(button("STATISTIQUES & RAPPORTS", { showStatistics() }))
        gap()
        addLine(content, "Version locale : aucune donnée de marché en direct. Les calculs utilisent uniquement tes trades enregistrés.", 11f, muted)
    }

    private fun pnlAt(i: Int): Double = trades.optJSONObject(i)?.optDouble("pnl", 0.0) ?: 0.0
    private fun countWins(): Int { var n = 0; for (i in 0 until trades.length()) if (pnlAt(i) > 0) n++; return n }
    private fun countLosses(): Int { var n = 0; for (i in 0 until trades.length()) if (pnlAt(i) < 0) n++; return n }
    private fun totalPnl(): Double { var s = 0.0; for (i in 0 until trades.length()) s += pnlAt(i); return s }
    private fun grossProfit(): Double { var s = 0.0; for (i in 0 until trades.length()) if (pnlAt(i) > 0) s += pnlAt(i); return s }
    private fun grossLoss(): Double { var s = 0.0; for (i in 0 until trades.length()) if (pnlAt(i) < 0) s += abs(pnlAt(i)); return s }
    private fun winRate(): Double = if (trades.length() == 0) 0.0 else countWins() * 100.0 / trades.length()
    private fun profitFactor(): Double = if (grossLoss() == 0.0) if (grossProfit() > 0) Double.POSITIVE_INFINITY else 0.0 else grossProfit() / grossLoss()
    private fun money(value: Double): String = String.format(Locale.US, "%,.2f %s", value, currency)
    private fun pfText(): String { val pf = profitFactor(); return if (pf.isInfinite()) "∞" else String.format(Locale.US, "%.2f", pf) }

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

    private fun showDashboard() {
        baseScreen("TABLEAU DE BORD", "SYNTHÈSE DE PERFORMANCE")
        val total = totalPnl()
        val hero = card()
        addLine(hero, "BONJOUR, $firstName", 13f, cyan, true)
        addLine(hero, "P&L NET", 12f, muted)
        addLine(hero, money(total), 29f, if (total >= 0) green else red, true)
        addLine(hero, "${trades.length()} trades   •   ${String.format(Locale.US, "%.1f", winRate())}% gagnants", 13f, white)
        val stats = card()
        addLine(stats, "INDICATEURS CLÉS", 14f, gold, true)
        addLine(stats, "Profit Factor : ${pfText()}", 15f)
        addLine(stats, "Drawdown maximum : ${money(drawdown())}", 14f)
        addLine(stats, "Trades gagnants : ${countWins()}   |   Perdants : ${countLosses()}", 13f)
        val best = bestTrade(true)
        val worst = bestTrade(false)
        addLine(stats, "Meilleur trade : ${if (best == null) "—" else money(best)}", 13f, green)
        addLine(stats, "Pire trade : ${if (worst == null) "—" else money(worst)}", 13f, red)
        val goal = card()
        val pct = (total / 1_000_000.0 * 100.0).coerceIn(0.0, 100.0)
        addLine(goal, "OBJECTIF : 1 000 000 USD", 14f, gold, true)
        addLine(goal, "${String.format(Locale.US, "%.4f", pct)} % de progression calculée sur le P&L cumulé", 12f, muted)
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = (pct * 10).toInt()
            progressTintList = android.content.res.ColorStateList.valueOf(gold)
        }
        goal.addView(progress, LinearLayout.LayoutParams(-1, dp(10)))
        val curve = card()
        addLine(curve, "COURBE DE CAPITAL — P&L CUMULÉ", 13f, cyan, true)
        var running = 0.0
        if (trades.length() == 0) addLine(curve, "Enregistre des trades pour afficher ta progression.", 12f, muted)
        for (i in 0 until trades.length()) {
            running += pnlAt(i)
            val t = trades.optJSONObject(i) ?: continue
            addLine(curve, "${i + 1}. ${t.optString("asset", "—")}  →  ${money(running)}", 11f, if (running >= 0) green else red)
        }
        gap()
        content.addView(button("＋  ENREGISTRER UN TRADE", { showAddTrade() }, gold))
    }

    private fun bestTrade(highest: Boolean): Double? {
        if (trades.length() == 0) return null
        var v = pnlAt(0)
        for (i in 1 until trades.length()) v = if (highest) max(v, pnlAt(i)) else kotlin.math.min(v, pnlAt(i))
        return v
    }

    private fun field(parent: LinearLayout, label: String, hint: String = "", initial: String = ""): EditText {
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

    private fun spinner(parent: LinearLayout, label: String, options: Array<String>): Spinner {
        parent.addView(text(label, 12f, muted, true))
        val sp = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sp.adapter = adapter
        parent.addView(sp, LinearLayout.LayoutParams(-1, dp(48)))
        return sp
    }

    private fun num(edit: EditText): Double = edit.text.toString().trim().replace(",", ".").toDoubleOrNull() ?: 0.0

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun showAddTrade() {
        baseScreen("NOUVEAU TRADE", "ENREGISTREMENT DU JOURNAL")
        val form = card()
        val asset = field(form, "ACTIF", "Ex. XAUUSD")
        val direction = spinner(form, "DIRECTION", arrayOf("BUY", "SELL"))
        val date = field(form, "DATE", "AAAA-MM-JJ", today())
        val entry = field(form, "PRIX D’ENTRÉE", "0.00")
        val stop = field(form, "STOP LOSS", "0.00")
        val target = field(form, "TAKE PROFIT", "0.00")
        val lot = field(form, "VOLUME / LOT", "0.01")
        val risk = field(form, "RISQUE INITIAL ($)", "Montant risqué")
        val exit = field(form, "PRIX DE SORTIE", "0.00")
        val pnl = field(form, "P&L RÉALISÉ ($)", "Saisis le résultat réel MT5")
        val mae = field(form, "MAE ($)", "0")
        val mfe = field(form, "MFE ($)", "0")
        val session = spinner(form, "SESSION", arrayOf("Sydney", "Tokyo", "Londres", "New York", "Hors session"))
        val emotion = spinner(form, "ÉMOTION", arrayOf("Calme", "Confiant", "Peur", "Stress", "Impatient", "Euphorique"))
        val plan = spinner(form, "PLAN RESPECTÉ ?", arrayOf("Oui", "Non"))
        val error = spinner(form, "ERREUR", arrayOf("Aucune", "FOMO", "Surtrading", "Revenge trading", "SL déplacé", "Sortie prématurée", "Autre"))
        val notes = field(form, "NOTES", "Analyse, contexte, leçon à retenir")
        val rrPreview = text("R multiple : —", 12f, cyan, true)
        form.addView(rrPreview)
        val updateR = {
            val r = num(pnl)
            val riskVal = num(risk)
            rrPreview.text = if (riskVal > 0) "R multiple estimé : ${String.format(Locale.US, "%.2f R", r / riskVal)}" else "R multiple : renseigne le risque initial"
        }
        pnl.setOnFocusChangeListener { _, _ -> updateR() }
        risk.setOnFocusChangeListener { _, _ -> updateR() }
        gap()
        content.addView(button("ENREGISTRER LE TRADE", {
            if (asset.text.toString().trim().isEmpty()) {
                Toast.makeText(this, "Renseigne l’actif.", Toast.LENGTH_SHORT).show()
                return@button
            }
            val dayCount = countTradesToday()
            if (dayCount >= maxTradesPerDay) {
                AlertDialog.Builder(this).setTitle("Discipline verrouillée")
                    .setMessage("Limite quotidienne atteinte ($dayCount/$maxTradesPerDay). Modifie la limite dans Paramètres si nécessaire.")
                    .setPositiveButton("OK", null).show()
                return@button
            }
            val p = num(pnl)
            if (dailyRiskLimit > 0 && p < -dailyRiskLimit) {
                AlertDialog.Builder(this).setTitle("Alerte de risque")
                    .setMessage("La perte saisie dépasse la limite quotidienne configurée. Confirme-tu l’enregistrement ?")
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("Enregistrer") { _, _ -> storeTrade(asset, direction, date, entry, stop, target, lot, risk, exit, pnl, mae, mfe, session, emotion, plan, error, notes) }
                    .show()
            } else {
                storeTrade(asset, direction, date, entry, stop, target, lot, risk, exit, pnl, mae, mfe, session, emotion, plan, error, notes)
            }
        }, gold))
        gap()
        addLine(content, "Note : le P&L est saisi depuis MT5 pour éviter une conversion erronée entre les contrats XAUUSD, Forex et autres actifs.", 11f, muted)
    }

    private fun countTradesToday(): Int {
        var count = 0
        for (i in 0 until trades.length()) if (trades.optJSONObject(i)?.optString("date") == today()) count++
        return count
    }

    private fun storeTrade(
        asset: EditText, direction: Spinner, date: EditText, entry: EditText, stop: EditText,
        target: EditText, lot: EditText, risk: EditText, exit: EditText, pnl: EditText,
        mae: EditText, mfe: EditText, session: Spinner, emotion: Spinner, plan: Spinner,
        error: Spinner, notes: EditText
    ) {
        val riskValue = num(risk)
        val realized = num(pnl)
        val obj = JSONObject().apply {
            put("asset", asset.text.toString().trim().uppercase(Locale.ROOT))
            put("direction", direction.selectedItem.toString())
            put("date", date.text.toString().ifBlank { today() })
            put("entry", num(entry)); put("stop", num(stop)); put("target", num(target))
            put("lot", num(lot)); put("risk", riskValue); put("exit", num(exit))
            put("pnl", realized); put("rMultiple", if (riskValue > 0) realized / riskValue else 0.0)
            put("mae", num(mae)); put("mfe", num(mfe))
            put("session", session.selectedItem.toString())
            put("emotion", emotion.selectedItem.toString())
            put("plan", plan.selectedItem.toString())
            put("error", error.selectedItem.toString())
            put("notes", notes.text.toString())
            put("created", System.currentTimeMillis())
        }
        trades.put(obj)
        saveTrades()
        Toast.makeText(this, "Trade enregistré et sauvegardé !", Toast.LENGTH_SHORT).show()
        showDashboard()
    }

    private fun showHistory() {
        baseScreen("JOURNAL & HISTORIQUE", "${trades.length()} TRADES ENREGISTRÉS")
        if (trades.length() == 0) {
            val empty = card()
            addLine(empty, "Ton journal est encore vide.", 15f, muted, true)
            addLine(empty, "Ajoute ton premier trade pour commencer à suivre tes performances.", 12f, muted)
            content.addView(button("AJOUTER UN TRADE", { showAddTrade() }, gold))
            return
        }
        val summary = card()
        addLine(summary, "P&L TOTAL : ${money(totalPnl())}", 18f, if (totalPnl() >= 0) green else red, true)
        addLine(summary, "Win rate : ${String.format(Locale.US, "%.1f", winRate())}%  •  PF : ${pfText()}", 12f, muted)
        for (i in trades.length() - 1 downTo 0) {
            val obj = trades.optJSONObject(i) ?: continue
            val item = card()
            val p = obj.optDouble("pnl", 0.0)
            addLine(item, "${obj.optString("asset", "—")}  •  ${obj.optString("direction", "—")}  •  ${obj.optString("date", "—")}", 14f, gold, true)
            addLine(item, money(p), 18f, if (p >= 0) green else red, true)
            addLine(item, "Lot ${obj.optString("lot", "—")}  |  ${obj.optString("session", "—")}  |  ${obj.optString("rMultiple", "0")} R", 11f, muted)
            addLine(item, "Émotion : ${obj.optString("emotion", "—")}  •  Erreur : ${obj.optString("error", "—")}", 11f, muted)
            addLine(item, obj.optString("notes", ""), 11f, white)
            item.addView(button("SUPPRIMER CE TRADE", { confirmDelete(i) }, Color.rgb(104, 43, 53)))
        }
        gap()
        content.addView(button("＋  AJOUTER UN TRADE", { showAddTrade() }, gold))
    }

    private fun confirmDelete(index: Int) {
        AlertDialog.Builder(this).setTitle("Supprimer ce trade ?")
            .setMessage("Cette action est définitive.")
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Supprimer") { _, _ ->
                val updated = JSONArray()
                for (i in 0 until trades.length()) if (i != index) updated.put(trades.get(i))
                trades = updated
                saveTrades()
                showHistory()
            }.show()
    }

    private fun showStatistics() {
        baseScreen("STATISTIQUES & RAPPORTS", "DONNÉES CALCULÉES SUR TES TRADES")
        val c = card()
        addLine(c, "RÉSUMÉ", 14f, gold, true)
        addLine(c, "Nombre total : ${trades.length()}")
        addLine(c, "Gagnants : ${countWins()}  |  Perdants : ${countLosses()}")
        addLine(c, "Win rate : ${String.format(Locale.US, "%.2f", winRate())}%")
        addLine(c, "P&L net : ${money(totalPnl())}", 18f, if (totalPnl() >= 0) green else red, true)
        val pl = card()
        addLine(pl, "PROFIT & LOSS", 14f, cyan, true)
        addLine(pl, "Profit brut : ${money(grossProfit())}", 13f, green)
        addLine(pl, "Perte brute : ${money(grossLoss())}", 13f, red)
        addLine(pl, "Profit Factor : ${pfText()}")
        addLine(pl, "Drawdown maximum : ${money(drawdown())}")
        val longShort = card()
        var buys = 0; var sells = 0; var buyPnl = 0.0; var sellPnl = 0.0
        for (i in 0 until trades.length()) {
            val t = trades.optJSONObject(i) ?: continue
            if (t.optString("direction") == "BUY") { buys++; buyPnl += t.optDouble("pnl") }
            else { sells++; sellPnl += t.optDouble("pnl") }
        }
        addLine(longShort, "LONG & SHORT", 14f, gold, true)
        addLine(longShort, "BUY : $buys trades  •  ${money(buyPnl)}")
        addLine(longShort, "SELL : $sells trades  •  ${money(sellPnl)}")
        val sessions = card()
        addLine(sessions, "PERFORMANCE PAR SESSION", 14f, cyan, true)
        listOf("Sydney", "Tokyo", "Londres", "New York", "Hors session").forEach { s ->
            var n = 0; var p = 0.0
            for (i in 0 until trades.length()) {
                val t = trades.optJSONObject(i) ?: continue
                if (t.optString("session") == s) { n++; p += t.optDouble("pnl") }
            }
            addLine(sessions, "$s : $n trades  •  ${money(p)}", 12f, if (p >= 0) green else red)
        }
        val discipline = card()
        var broken = 0
        for (i in 0 until trades.length()) if (trades.optJSONObject(i)?.optString("plan") == "Non") broken++
        addLine(discipline, "DISCIPLINE", 14f, gold, true)
        addLine(discipline, "Trades hors plan : $broken")
        addLine(discipline, "Respect du plan : ${if (trades.length() > 0) String.format(Locale.US, "%.1f", (trades.length() - broken) * 100.0 / trades.length()) else "0.0"} %")
        addLine(discipline, "MAE moyen : ${money(averageField("mae"))}  •  MFE moyen : ${money(averageField("mfe"))}")
    }

    private fun averageField(key: String): Double {
        if (trades.length() == 0) return 0.0
        var sum = 0.0
        for (i in 0 until trades.length()) sum += trades.optJSONObject(i)?.optDouble(key, 0.0) ?: 0.0
        return sum / trades.length()
    }

    private fun showRisk() {
        baseScreen("GESTION DU RISQUE", "PROTÈGE TON CAPITAL AVANT DE CHERCHER LE PROFIT")
        val c = card()
        val capital = field(c, "CAPITAL DISPONIBLE ($)", "Ex. 1000")
        val percent = field(c, "RISQUE PAR TRADE (%)", "Ex. 1")
        val sl = field(c, "STOP LOSS (POINTS / PIPS)", "Ex. 250")
        val value = field(c, "VALEUR PAR POINT POUR 1 LOT ($)", "À vérifier chez ton broker")
        val result = text("Taille de lot estimée : —", 16f, gold, true)
        c.addView(result)
        c.addView(button("CALCULER LA TAILLE", {
            val riskAmount = num(capital) * num(percent) / 100.0
            val denom = num(sl) * num(value)
            result.text = if (denom > 0) "Risque : ${money(riskAmount)}\nTaille estimée : ${String.format(Locale.US, "%.4f", riskAmount / denom)} lot" else "Renseigne un stop et une valeur par point valides."
        }, gold))
        val rules = card()
        addLine(rules, "RÈGLES DE DISCIPLINE", 14f, cyan, true)
        addLine(rules, "Trades maximum par jour : $maxTradesPerDay")
        addLine(rules, "Limite de perte quotidienne : ${if (dailyRiskLimit > 0) money(dailyRiskLimit) else "Non configurée"}")
        addLine(rules, "Trades pris aujourd’hui : ${countTradesToday()}")
        addLine(rules, "Ne risque jamais un montant que tu ne peux pas te permettre de perdre.", 12f, orange)
        content.addView(button("CONFIGURER MES LIMITES", { showSettings() }))
    }

    private fun showLotCalculator() {
        baseScreen("CALCULATEUR DE LOTS", "ESTIMATION — VÉRIFIE LA VALEUR DU POINT")
        val c = card()
        val capital = field(c, "CAPITAL ($)", "1000")
        val risk = field(c, "RISQUE (%)", "1")
        val stop = field(c, "DISTANCE DU STOP (POINTS)", "300")
        val point = field(c, "VALEUR DU POINT POUR 1 LOT ($)", "0.1")
        val result = text("Résultat : —", 17f, gold, true)
        c.addView(result)
        c.addView(button("CALCULER", {
            val amount = num(capital) * num(risk) / 100
            val d = num(stop) * num(point)
            result.text = if (d > 0) "Montant risqué : ${money(amount)}\nLot estimé : ${String.format(Locale.US, "%.4f", amount / d)}" else "Valeurs invalides"
        }, gold))
    }

    private fun showGoals() {
        baseScreen("OBJECTIFS SPARTAN", "CONSTRUIRE LA PERFORMANCE AVEC DISCIPLINE")
        val c = card()
        addLine(c, "OBJECTIF FINAL", 13f, gold, true)
        addLine(c, "1 000 000 USD", 28f, gold, true)
        addLine(c, "P&L journal enregistré : ${money(totalPnl())}", 14f)
        val remaining = 1_000_000.0 - totalPnl()
        addLine(c, "Écart théorique restant : ${money(max(0.0, remaining))}", 13f, muted)
        addLine(c, "Cette progression reflète le P&L de ton journal, pas la valeur réelle de ton compte broker.", 11f, orange)
    }

    private fun showAdvice() {
        baseScreen("ASSISTANT SPARTAN", "ANALYSE LOCALE DE TON JOURNAL")
        val c = card()
        addLine(c, "RAPPORT DE PERFORMANCE", 14f, gold, true)
        if (trades.length() == 0) {
            addLine(c, "Pas encore assez de données. Enregistre tes trades pour recevoir des conseils fondés sur tes résultats.", 13f, muted)
            return
        }
        addLine(c, "Ton P&L cumulé est de ${money(totalPnl())}.", 13f)
        addLine(c, "Ton taux de réussite est de ${String.format(Locale.US, "%.1f", winRate())} %.", 13f)
        addLine(c, "Ton Profit Factor est ${pfText()} et ton drawdown maximum est ${money(drawdown())}.", 13f)
        var errors = 0
        var noPlan = 0
        for (i in 0 until trades.length()) {
            val t = trades.optJSONObject(i) ?: continue
            if (t.optString("error") != "Aucune") errors++
            if (t.optString("plan") == "Non") noPlan++
        }
        addLine(c, "Trades avec erreur déclarée : $errors / ${trades.length()}.", 13f)
        addLine(c, "Trades où le plan n’a pas été respecté : $noPlan.", 13f)
        addLine(c, if (noPlan > 0) "Priorité : réduire les entrées hors plan et noter la raison avant chaque exécution." else "Continue à documenter tes décisions et à vérifier la stabilité de tes résultats.", 13f, cyan)
        addLine(c, "Les observations sont calculées à partir des données saisies. Ce n’est pas un signal d’achat ou de vente.", 11f, muted)
    }

    private fun showSettings() {
        baseScreen("PARAMÈTRES", "PERSONNALISATION & LIMITES")
        val c = card()
        val name = field(c, "PRÉNOM / PSEUDONYME", "Spartan", firstName)
        val cur = spinner(c, "DEVISE DU JOURNAL", arrayOf("USD", "EUR", "MGA"))
        cur.setSelection(listOf("USD", "EUR", "MGA").indexOf(currency).coerceAtLeast(0))
        val limit = field(c, "PERTE MAXIMALE PAR JOUR ($)", "0 = désactivée", dailyRiskLimit.toString())
        val maxDay = field(c, "NOMBRE MAXIMUM DE TRADES / JOUR", "4", maxTradesPerDay.toString())
        c.addView(button("SAUVEGARDER LES PARAMÈTRES", {
            firstName = name.text.toString().ifBlank { "Spartan" }
            currency = cur.selectedItem.toString()
            dailyRiskLimit = num(limit)
            maxTradesPerDay = num(maxDay).toInt().coerceIn(1, 100)
            saveSettings()
            Toast.makeText(this, "Paramètres sauvegardés.", Toast.LENGTH_SHORT).show()
            showHome()
        }, gold))
        val info = card()
        addLine(info, "STOCKAGE LOCAL", 14f, cyan, true)
        addLine(info, "Les trades restent enregistrés sur cet appareil dans le stockage privé de l’application. Désinstaller l’application peut supprimer ces données : exporte une sauvegarde avant toute désinstallation.", 12f, muted)
        info.addView(button("EXPORTER LE JOURNAL (JSON)", { exportJournal() }))
        gap(info, 7)
        info.addView(button("IMPORTER UN JOURNAL (JSON)", { importJournal() }))
    }

    private fun exportJournal() {
        val intent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE)
        intent.type = "application/json"
        intent.putExtra(android.content.Intent.EXTRA_TITLE, "leonidas-journal.json")
        try { startActivityForResult(intent, 701) } catch (_: Exception) {
            Toast.makeText(this, "Export indisponible sur cet appareil.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importJournal() {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE)
        intent.type = "application/json"
        try { startActivityForResult(intent, 702) } catch (_: Exception) {
            Toast.makeText(this, "Import indisponible sur cet appareil.", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Android API; kept for compatibility with the app's minSdk")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        try {
            val uri = data.data!!
            if (requestCode == 701) {
                contentResolver.openOutputStream(uri)?.use { it.write(trades.toString(2).toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, "Journal exporté.", Toast.LENGTH_SHORT).show()
            } else if (requestCode == 702) {
                val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: "[]"
                val imported = JSONArray(raw)
                AlertDialog.Builder(this).setTitle("Importer le journal ?")
                    .setMessage("Les ${trades.length()} trades actuels seront conservés et les ${imported.length()} trades importés seront ajoutés.")
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("Importer") { _, _ ->
                        for (i in 0 until imported.length()) trades.put(imported.get(i))
                        saveTrades()
                        showHistory()
                    }.show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Fichier invalide : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
