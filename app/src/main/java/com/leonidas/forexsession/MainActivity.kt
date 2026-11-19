
package com.leonidas.forexsession

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
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

    private val bg = Color.rgb(12, 12, 14)
    private val gold = Color.rgb(218, 170, 65)
    private val white = Color.WHITE

    private val prefs by lazy {
        getSharedPreferences("leonidas_journal", MODE_PRIVATE)
    }

    private lateinit var root: LinearLayout
    private var trades = JSONArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadTrades()
        showHome()
    }

    // CHARGEMENT ET SAUVEGARDE

    private fun loadTrades() {
        trades = JSONArray()
        val saved = prefs.getString("trades", "[]") ?: "[]"

        try {
            val array = JSONArray(saved)
            for (i in 0 until array.length()) {
                trades.put(array.getJSONObject(i))
            }
        } catch (_: Exception) {
            trades = JSONArray()
        }
    }

    private fun saveTrades() {
        prefs.edit().putString("trades", trades.toString()).apply()
    }

    // BASE DES ÉCRANS

    private fun baseScreen(title: String): LinearLayout {
        val scroll = ScrollView(this)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 30)
            setBackgroundColor(bg)
        }

        scroll.addView(root)
        setContentView(scroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val heading = TextView(this).apply {
            text = "LEONIDAS SPARTAN GOLD"
            textSize = 19f
            setTextColor(gold)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        header.addView(
            heading,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val menuButton = Button(this).apply {
            text = "⋮"
            textSize = 24f
            setTextColor(gold)
            setBackgroundColor(bg)
            setOnClickListener { showMenu(this) }
        }

        header.addView(menuButton)
        root.addView(header)

        addText(root, title, 19, white, true)
        addText(root, "━━━━━━━━━━━━━━━━━━━━", 14, gold)

        return root
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)

        popup.menu.add("Accueil")
        popup.menu.add("Tableau de bord")
        popup.menu.add("Ajouter un trade")
        popup.menu.add("Historique")
        popup.menu.add("Statistiques")
        popup.menu.add("Conseils")

        popup.setOnMenuItemClickListener {
            when (it.title.toString()) {
                "Accueil" -> showHome()
                "Tableau de bord" -> showDashboard()
                "Ajouter un trade" -> showAddTrade()
                "Historique" -> showHistory()
                "Statistiques" -> showStatistics()
                "Conseils" -> showAdvice()
            }
            true
        }

        popup.show()
    }

    private fun addText(
        parent: LinearLayout,
        value: String,
        size: Number = 16,
        color: Int = white,
        bold: Boolean = false
    ): TextView {
        val view = TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(4, 9, 4, 9)
        }

        parent.addView(view)
        return view
    }

    private fun addButton(
        parent: LinearLayout,
        label: String,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            setTextColor(Color.BLACK)
            setBackgroundColor(gold)
            textSize = 14f
            setOnClickListener { action() }
        }

        val params = LinearLayout.LayoutParams(-1, -2)
        params.setMargins(0, 7, 0, 7)
        parent.addView(button, params)
    }

    private fun addField(
        parent: LinearLayout,
        label: String,
        hint: String,
        default: String = ""
    ): EditText {
        addText(parent, label, 14, gold, true)

        val field = EditText(this).apply {
            this.hint = hint
            setText(default)
            setTextColor(white)
            setHintTextColor(Color.GRAY)
            textSize = 16f
            setSingleLine(true)
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.rgb(35, 35, 38))
        }

        parent.addView(field)
        return field
    }

    private fun addSpinner(
        parent: LinearLayout,
        label: String,
        choices: List<String>
    ): Spinner {
        addText(parent, label, 14, gold, true)

        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            choices
        )

        parent.addView(spinner)
        return spinner
    }

    private fun number(value: String): Double? {
        return value.trim().replace(",", ".").toDoubleOrNull()
    }

    private fun money(value: Double): String {
        return String.format(Locale.FRANCE, "%.2f", value)
    }

    // ACCUEIL ET SESSIONS FOREX

    private fun worldTime(zone: String): String {
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        format.timeZone = TimeZone.getTimeZone(zone)
        return format.format(Date())
    }

    private fun sessionStatus(
        zone: String,
        start: Int,
        end: Int
    ): String {
        val calendar = java.util.Calendar.getInstance(
            TimeZone.getTimeZone(zone)
        )
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)

        return if (hour >= start && hour < end) {
            "OUVERTE"
        } else {
            "FERMÉE"
        }
    }

    private fun showHome() {
        val screen = baseScreen("ACCUEIL")

        addText(screen, "🌍", 54, gold, true)
        addText(screen, "CARTE MONDIALE DES MARCHÉS", 18, gold, true)

        val dateFormat = SimpleDateFormat(
            "EEEE dd MMMM yyyy",
            Locale.FRANCE
        )

        addText(
            screen,
            "Madagascar : ${worldTime("Indian/Antananarivo")}",
            22,
            white,
            true
        )

        addText(
            screen,
            dateFormat.format(Date()),
            14,
            Color.LTGRAY
        )

        addText(screen, "SESSIONS INTERNATIONALES", 17, gold, true)

        addText(
            screen,
            "Sydney  | ${worldTime("Australia/Sydney")} | " +
                sessionStatus("Australia/Sydney", 8, 17)
        )

        addText(
            screen,
            "Tokyo  | ${worldTime("Asia/Tokyo")} | " +
                sessionStatus("Asia/Tokyo", 9, 18)
        )

        addText(
            screen,
            "Londres | ${worldTime("Europe/London")} | " +
                sessionStatus("Europe/London", 8, 17)
        )

        addText(
            screen,
            "New York | ${worldTime("America/New_York")} | " +
                sessionStatus("America/New_York", 8, 17)
        )

        addText(screen, "━━━━━━━━━━━━━━━━━━━━", 14, gold)
        addText(screen, "DISCIPLINE • STRATÉGIE • PERFORMANCE", 15, gold, true)

        addButton(screen, "OUVRIR LE TABLEAU DE BORD") {
            showDashboard()
        }

        addButton(screen, "AJOUTER UN TRADE") {
            showAddTrade()
        }

        addButton(screen, "HISTORIQUE") {
            showHistory()
        }
    }

    // CALCUL DES STATISTIQUES

    private fun allStats(): JSONObject {
        var wins = 0
        var losses = 0
        var grossProfit = 0.0
        var grossLoss = 0.0
        var net = 0.0
        var peak = 0.0
        var equity = 0.0
        var maxDrawdown = 0.0
        var maeTotal = 0.0
        var mfeTotal = 0.0
        var maeCount = 0
        var mfeCount = 0
        var rrTotal = 0.0
        var rrCount = 0

        for (i in 0 until trades.length()) {
            val t = trades.getJSONObject(i)
            val pnl = t.optDouble("pnl", 0.0)

            net += pnl
            equity += pnl
            peak = max(peak, equity)
            maxDrawdown = max(maxDrawdown, peak - equity)

            if (pnl > 0) {
                wins++
                grossProfit += pnl
            } else if (pnl < 0) {
                losses++
                grossLoss += abs(pnl)
            }

            if (t.has("mae")) {
                maeTotal += t.optDouble("mae")
                maeCount++
            }

            if (t.has("mfe")) {
                mfeTotal += t.optDouble("mfe")
                mfeCount++
            }

            if (t.has("rr")) {
                rrTotal += t.optDouble("rr")
                rrCount++
            }
        }

        val total = trades.length()
        val result = JSONObject()

        result.put("total", total)
        result.put("wins", wins)
        result.put("losses", losses)
        result.put(
            "winRate",
            if (total > 0) wins * 100.0 / total else 0.0
        )
        result.put("net", net)

        result.put(
            "profitFactor",
            when {
                grossLoss > 0 -> grossProfit / grossLoss
                grossProfit > 0 -> Double.POSITIVE_INFINITY
                else -> 0.0
            }
        )

        result.put("drawdown", maxDrawdown)
        result.put("mae", if (maeCount > 0) maeTotal / maeCount else 0.0)
        result.put("mfe", if (mfeCount > 0) mfeTotal / mfeCount else 0.0)
        result.put("rr", if (rrCount > 0) rrTotal / rrCount else 0.0)

        return result
    }

    // TABLEAU DE BORD

    private fun showDashboard() {
        val screen = baseScreen("TABLEAU DE BORD")
        val stats = allStats()

        addText(screen, "JOURNAL DE TRADING PROFESSIONNEL", 18, gold, true)

        addText(screen, "TRADES : ${stats.optInt("total")}")
        addText(screen, "GAGNANTS : ${stats.optInt("wins")}")
        addText(screen, "PERDANTS : ${stats.optInt("losses")}")

        addText(
            screen,
            "WIN RATE : ${money(stats.optDouble("winRate"))} %"
        )

        addText(
            screen,
            "PNL NET : ${money(stats.optDouble("net"))} Ar",
            19,
            gold,
            true
        )

        val pf = stats.optDouble("profitFactor")
        val pfText = if (pf.isInfinite()) "∞" else money(pf)

        addText(screen, "PROFIT FACTOR : $pfText")
        addText(screen, "RR MOYEN : 1:${money(stats.optDouble("rr"))}")
        addText(
            screen,
            "DRAWDOWN MAX : ${money(stats.optDouble("drawdown"))} Ar"
        )
        addText(screen, "MAE MOYEN : ${money(stats.optDouble("mae"))}")
        addText(screen, "MFE MOYEN : ${money(stats.optDouble("mfe"))}")

        addText(screen, "━━━━━━━━━━━━━━━━━━━━", 14, gold)

        addText(
            screen,
            "OBJECTIF : 1 000 000 USD",
            20,
            gold,
            true
        )

        addText(
            screen,
            "Les statistiques sont calculées à partir des trades enregistrés."
        )

        addButton(screen, "＋ AJOUTER UN TRADE") {
            showAddTrade()
        }

        addButton(screen, "HISTORIQUE DES TRADES") {
            showHistory()
        }

        addButton(screen, "STATISTIQUES DÉTAILLÉES") {
            showStatistics()
        }

        addButton(screen, "ACCUEIL") {
            showHome()
        }
    }

    // AJOUT D'UN TRADE

    private fun showAddTrade() {
        val screen = baseScreen("NOUVEAU TRADE")

        val asset = addField(screen, "ACTIF", "Ex : XAUUSD")

        val side = addSpinner(
            screen,
            "DIRECTION",
            listOf("BUY", "SELL")
        )

        val date = addField(
            screen,
            "DATE",
            "AAAA-MM-JJ",
            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.getDefault()
            ).format(Date())
        )

        val entry = addField(screen, "PRIX D'ENTRÉE", "Ex : 2650.50")
        val stop = addField(screen, "STOP LOSS", "Ex : 2645.00")
        val target = addField(screen, "TAKE PROFIT", "Ex : 2665.00")
        val lot = addField(screen, "LOT", "Ex : 0.01")
        val pnl = addField(screen, "PNL RÉALISÉ (Ar)", "Ex : 15000")
        val mae = addField(screen, "MAE", "Ex : 5.50")
        val mfe = addField(screen, "MFE", "Ex : 12.00")

        val emotion = addSpinner(
            screen,
            "ÉMOTION",
            listOf(
                "Calme",
                "Confiant",
                "Peur",
                "Stress",
                "Impatient",
                "Euphorique",
                "Frustré"
            )
        )

        val discipline = addSpinner(
            screen,
            "DISCIPLINE",
            listOf(
                "Plan respecté",
                "Plan partiellement respecté",
                "Plan non respecté"
            )
        )

        val error = addSpinner(
            screen,
            "ERREUR ÉVENTUELLE",
            listOf(
                "Aucune",
                "Entrée trop tôt",
                "Entrée trop tard",
                "Stop déplacé",
                "Surtrading",
                "Risque excessif",
                "Sortie prématurée",
                "Autre"
            )
        )

        val notes = addField(
            screen,
            "NOTES",
            "Analyse et leçons du trade"
        )

        addButton(screen, "ENREGISTRER LE TRADE") {
            val a = asset.text.toString().trim()
            val e = number(entry.text.toString())
            val s = number(stop.text.toString())
            val tp = number(target.text.toString())
            val l = number(lot.text.toString())
            val result = number(pnl.text.toString())
            val mA = number(mae.text.toString())
            val mF = number(mfe.text.toString())

            if (
                a.isEmpty() || e == null || s == null ||
                tp == null || l == null || result == null
            ) {
                Toast.makeText(
                    this,
                    "Remplis les champs obligatoires correctement.",
                    Toast.LENGTH_LONG
                ).show()
                return@addButton
            }

            if (e == s || l <= 0 || e <= 0 || s < 0 || tp < 0) {
                Toast.makeText(
                    this,
                    "Vérifie les prix et le lot.",
                    Toast.LENGTH_LONG
                ).show()
                return@addButton
            }

            val risk = abs(e - s)
            val reward = abs(tp - e)
            val rr = reward / risk

            val trade = JSONObject().apply {
                put("asset", a)
                put("side", side.selectedItem.toString())
                put("date", date.text.toString())
                put("entry", e)
                put("stop", s)
                put("target", tp)
                put("lot", l)
                put("pnl", result)
                put("rr", rr)

                if (mA != null) put("mae", mA)
                if (mF != null) put("mfe", mF)

                put("emotion", emotion.selectedItem.toString())
                put("discipline", discipline.selectedItem.toString())
                put("error", error.selectedItem.toString())
                put("notes", notes.text.toString())
            }

            trades.put(trade)
            saveTrades()

            Toast.makeText(
                this,
                "Trade enregistré !",
                Toast.LENGTH_SHORT
            ).show()

            showDashboard()
        }

        addButton(screen, "ANNULER") {
            showDashboard()
        }
    }

    // HISTORIQUE

    private fun showHistory() {
        val screen = baseScreen("HISTORIQUE DES TRADES")

        if (trades.length() == 0) {
            addText(screen, "Aucun trade enregistré.")
        } else {
            for (i in trades.length() - 1 downTo 0) {
                val t = trades.getJSONObject(i)

                addText(
                    screen,
                    "${t.optString("date")} | ${t.optString("asset")}",
                    17,
                    gold,
                    true
                )

                addText(screen, "Direction : ${t.optString("side")}")
                addText(screen, "PNL : ${money(t.optDouble("pnl"))} Ar")
                addText(screen, "RR : 1:${money(t.optDouble("rr"))}")
                addText(screen, "Émotion : ${t.optString("emotion")}")
                addText(screen, "Discipline : ${t.optString("discipline")}")
                addText(screen, "Erreur : ${t.optString("error")}")
                addText(screen, "MAE : ${t.optString("mae", "Non renseigné")}")
                addText(screen, "MFE : ${t.optString("mfe", "Non renseigné")}")
                addText(screen, "Notes : ${t.optString("notes")}")

                val index = i

                addButton(screen, "SUPPRIMER CE TRADE") {
                    AlertDialog.Builder(this)
                        .setTitle("Supprimer ce trade ?")
                        .setMessage("Cette action est définitive.")
                        .setNegativeButton("ANNULER", null)
                        .setPositiveButton("SUPPRIMER") { _, _ ->

                            val updated = JSONArray()

                            for (j in 0 until trades.length()) {
                                if (j != index) {
                                    updated.put(trades.getJSONObject(j))
                                }
                            }

                            trades = updated
                            saveTrades()
                            showHistory()
                        }
                        .show()
                }

                addText(screen, "────────────────────", 12, gold)
            }
        }

        addButton(screen, "RETOUR AU TABLEAU") {
            showDashboard()
        }
    }

    // STATISTIQUES DÉTAILLÉES

    private fun showStatistics() {
        val screen = baseScreen("STATISTIQUES & PERFORMANCE")
        val s = allStats()

        addText(screen, "Nombre de trades : ${s.optInt("total")}")
        addText(screen, "Trades gagnants : ${s.optInt("wins")}")
        addText(screen, "Trades perdants : ${s.optInt("losses")}")

        addText(
            screen,
            "Win rate : ${money(s.optDouble("winRate"))} %"
        )

        addText(
            screen,
            "PNL net : ${money(s.optDouble("net"))} Ar"
        )

        val pf = s.optDouble("profitFactor")
        val pfText = if (pf.isInfinite()) "∞" else money(pf)

        addText(screen, "Profit Factor : $pfText")
        addText(screen, "RR moyen : 1:${money(s.optDouble("rr"))}")

        addText(
            screen,
            "Drawdown maximum : ${money(s.optDouble("drawdown"))} Ar"
        )

        addText(screen, "MAE moyen : ${money(s.optDouble("mae"))}")
        addText(screen, "MFE moyen : ${money(s.optDouble("mfe"))}")

        addText(
            screen,
            "Attention : le drawdown est calculé sur les PNL saisis, " +
                "avec un capital initial de zéro."
        )

        addButton(screen, "RETOUR") {
            showDashboard()
        }
    }

    // CONSEILS

    private fun showAdvice() {
        val screen = baseScreen("CONSEILS DE TRADING")

        addText(screen, "1. Protège ton capital avant de chercher le profit.", 16, gold, true)
        addText(screen, "2. Respecte toujours ton Stop Loss.")
        addText(screen, "3. Note tes émotions après chaque trade.")
        addText(screen, "4. Analyse tes erreurs chaque semaine.")
        addText(screen, "5. Ne confonds pas un bon résultat avec une bonne décision.")
        addText(screen, "6. Évite le surtrading.")
        addText(screen, "7. Un trade perdant peut être un trade bien exécuté.")
        addText(screen, "8. Respecte ton plan de trading.")

        addButton(screen, "RETOUR") {
            showDashboard()
        }
    }
}
