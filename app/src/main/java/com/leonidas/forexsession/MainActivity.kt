
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
import kotlin.math.abs
import kotlin.math.max

class MainActivity : Activity() {

    private val bg = Color.rgb(12, 12, 14)
    private val gold = Color.rgb(218, 170, 65)
    private val white = Color.WHITE
    private val prefs by lazy {
        getSharedPreferences("leonidas_journal", MODE_PRIVATE)
    }
    private val trades = JSONArray()
    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadTrades()
        showDashboard()
    }

    private fun loadTrades() {
        val saved = prefs.getString("trades", "[]") ?: "[]"
        try {
            val array = JSONArray(saved)
            for (i in 0 until array.length()) {
                trades.put(array.getJSONObject(i))
            }
        } catch (_: Exception) {
        }
    }

    private fun saveTrades() {
        prefs.edit().putString("trades", trades.toString()).apply()
    }

    private fun baseScreen(title: String): LinearLayout {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 30)
            setBackgroundColor(bg)
        }
        scroll.addView(root)
        setContentView(scroll)

        addText(root, "LEONIDAS SPARTAN GOLD", 23, gold, true)
        addText(root, title, 19, white, true)
        addText(root, "━━━━━━━━━━━━━━━━━━━━", 14, gold)
        return root
    }

    private fun addText(
        parent: LinearLayout,
        value: String,
        size: Float = 16f,
        color: Int = white,
        bold: Boolean = false
    ): TextView {
        val view = TextView(this).apply {
            text = value
            textSize = size
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
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 8, 0, 8)
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

    private fun number(value: String): Double? =
        value.trim().replace(",", ".").toDoubleOrNull()

    private fun money(value: Double): String =
        String.format(Locale.FRANCE, "%.2f", value)

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
        }

        val total = trades.length()
        val result = JSONObject()
        result.put("total", total)
        result.put("wins", wins)
        result.put("losses", losses)
        result.put("winRate", if (total > 0) wins * 100.0 / total else 0.0)
        result.put("net", net)
        result.put("profitFactor", when {
            grossLoss > 0 -> grossProfit / grossLoss
            grossProfit > 0 -> Double.POSITIVE_INFINITY
            else -> 0.0
        })
        result.put("drawdown", maxDrawdown)
        result.put("mae", if (maeCount > 0) maeTotal / maeCount else 0.0)
        result.put("mfe", if (mfeCount > 0) mfeTotal / mfeCount else 0.0)
        return result
    }

    private fun showDashboard() {
        val screen = baseScreen("TABLEAU DE BORD")
        val stats = allStats()

        addText(screen, "JOURNAL DE TRADING PRO", 20, gold, true)
        addText(screen, "Trades enregistrés : ${stats.optInt("total")}")
        addText(screen, "Taux de réussite : ${money(stats.optDouble("winRate"))} %")
        addText(screen, "PNL net : ${money(stats.optDouble("net"))}")
        val pf = stats.optDouble("profitFactor")
        addText(
            screen,
            "Profit Factor : " +
                if (pf.isInfinite()) "∞" else money(pf)
        )
        addText(screen, "Drawdown maximum : ${money(stats.optDouble("drawdown"))}")
        addText(screen, "MAE moyen : ${money(stats.optDouble("mae"))}")
        addText(screen, "MFE moyen : ${money(stats.optDouble("mfe"))}")

        addText(screen, "OBJECTIF CAPITAL : 1 000 000 Ar", 17, gold, true)
        addText(screen, "Les résultats sont calculés à partir des trades saisis.")

        addButton(screen, "＋ AJOUTER UN TRADE") { showAddTrade() }
        addButton(screen, "HISTORIQUE DES TRADES") { showHistory() }
        addButton(screen, "STATISTIQUES") { showStatistics() }
        addButton(screen, "CONSEILS DE TRADING") { showAdvice() }
        addButton(screen, "RETOUR AU TABLEAU") { showDashboard() }
    }

    private fun showAddTrade() {
        val screen = baseScreen("NOUVEAU TRADE")

        val asset = addField(screen, "ACTIF", "Ex : XAUUSD")
        val side = addSpinner(screen, "DIRECTION", listOf("BUY", "SELL"))
        val date = addField(
            screen,
            "DATE",
            "AAAA-MM-JJ",
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        )
        val entry = addField(screen, "PRIX D'ENTRÉE", "Ex : 2650.50")
        val stop = addField(screen, "STOP LOSS", "Ex : 2645.00")
        val target = addField(screen, "TAKE PROFIT", "Ex : 2665.00")
        val lot = addField(screen, "LOT", "Ex : 0.01")
        val pnl = addField(screen, "RÉSULTAT RÉALISÉ (Ar)", "Ex : 15000")
        val mae = addField(screen, "MAE (en valeur)", "Ex : 5.50")
        val mfe = addField(screen, "MFE (en valeur)", "Ex : 12.00")
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
        val notes = addField(screen, "NOTES", "Analyse et leçons du trade")

        addButton(screen, "ENREGISTRER LE TRADE") {
            val a = asset.text.toString().trim()
            val e = number(entry.text.toString())
            val s = number(stop.text.toString())
            val tp = number(target.text.toString())
            val l = number(lot.text.toString())
            val result = number(pnl.text.toString())
            val mA = number(mae.text.toString())
            val mF = number(mfe.text.toString())

            if (a.isEmpty() || e == null || s == null ||
                tp == null || l == null || result == null
            ) {
                Toast.makeText(
                    this,
                    "Remplis les champs obligatoires avec des nombres valides.",
                    Toast.LENGTH_LONG
                ).show()
                return@addButton
            }

            if (e == s || l <= 0) {
                Toast.makeText(
                    this,
                    "Vérifie le prix d'entrée, le stop et le lot.",
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
            Toast.makeText(this, "Trade enregistré !", Toast.LENGTH_SHORT).show()
            showDashboard()
        }

        addButton(screen, "ANNULER") { showDashboard() }
    }

    private fun showHistory() {
        val screen = baseScreen("HISTORIQUE DES TRADES")

        if (trades.length() == 0) {
            addText(screen, "Aucun trade enregistré pour le moment.")
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
                                if (j != index) updated.put(trades.getJSONObject(j))
                            }
                            trades.clear()
                            for (j in 0 until updated.length()) {
                                trades.put(updated.getJSONObject(j))
                            }
                            saveTrades()
                            showHistory()
                        }
                        .show()
                }
                addText(screen, "────────────────────", 12, gold)
            }
        }

        addButton(screen, "RETOUR") { showDashboard() }
    }

    private fun showStatistics() {
        val screen = baseScreen("STATISTIQUES & PERFORMANCE")
        val s = allStats()

        addText(screen, "Nombre de trades : ${s.optInt("total")}")
        addText(screen, "Trades gagnants : ${s.optInt("wins")}")
        addText(screen, "Trades perdants : ${s.optInt("losses")}")
        addText(screen, "Win rate : ${money(s.optDouble("winRate"))} %")
        addText(screen, "PNL net : ${money(s.optDouble("net"))} Ar")

        val pf = s.optDouble("profitFactor")
        addText(
            screen,
            "Profit Factor : " + if (pf.isInfinite()) "∞" else money(pf)
        )

        addText(screen, "Drawdown maximum : ${money(s.optDouble("drawdown"))} Ar")
        addText(screen, "MAE moyen : ${money(s.optDouble("mae"))}")
        addText(screen, "MFE moyen : ${money(s.optDouble("mfe"))}")

        addText(
            screen,
            "Le drawdown est calculé sur la série des PNL enregistrés, " +
                "en supposant un capital initial de zéro."
        )
        addButton(screen, "RETOUR") { showDashboard() }
    }

    private fun showAdvice() {
        val screen = baseScreen("CONSEILS DE TRADING")

        addText(screen, "1. Respecte ton risque avant de penser au profit.", 16, gold, true)
        addText(screen, "2. Ne déplace pas ton Stop Loss sous l'effet de la peur.")
        addText(screen, "3. Note tes émotions après chaque trade.")
        addText(screen, "4. Analyse les erreurs répétées chaque semaine.")
        addText(screen, "5. Ne confonds pas un bon résultat avec une bonne décision.")
        addText(screen, "6. Respecte ton plan et évite le surtrading.")
        addText(screen, "7. Un trade perdant peut être un trade bien exécuté.")

        addButton(screen, "RETOUR") { showDashboard() }
    }
}
