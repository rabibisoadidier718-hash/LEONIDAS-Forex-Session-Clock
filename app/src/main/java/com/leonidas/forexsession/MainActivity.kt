package com.leonidas.forexsession

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
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
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Statistiques agrégées (par actif, session, direction, émotion, erreur…). */
private class AssetStat(
var count: Int = 0,
var lots: Double = 0.0,
var pnl: Double = 0.0,
var wins: Int = 0,
var losses: Int = 0
)

/** Graphique en anneau (donut) dessiné sans bibliothèque externe — 100 % hors connexion. */
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
val total = values.sum()
val size = min(width, height).toFloat()
val stroke = size * 0.16f
val cx = width / 2f
val cy = height / 2f
val r = size / 2f - stroke / 2f - 4f * d
val rect = RectF(cx - r, cy - r, cx + r, cy + r)
ring.style = Paint.Style.STROKE
ring.strokeWidth = stroke
ring.color = Color.rgb(34, 48, 70)
canvas.drawCircle(cx, cy, r, ring)
if (total > 0) {
var start = -90f
val gap = if (values.size > 1) 1.2f else 0f
values.forEachIndexed { i, v ->
val sweep = (v / total * 360.0).toFloat()
ring.color = colors[i % colors.size]
canvas.drawArc(rect, start, max(sweep - gap, 0.5f), false, ring)
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

/** Courbe de capital (P&L cumulé). */
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
val padL = 8f * d
val padR = 8f * d
val padT = 18f * d
val padB = 10f * d
var lo = 0.0
var hi = 0.0
for (v in pts) { if (v < lo) lo = v; if (v > hi) hi = v }
if (hi - lo < 1e-9) hi = lo + 1.0
val w = width - padL - padR
val h = height - padT - padB
fun px(i: Int): Float = if (pts.size == 1) padL + w / 2f else padL + w * i / (pts.size - 1f)
fun py(v: Double): Float = (padT + h * (1.0 - (v - lo) / (hi - lo))).toFloat()

p.style = Paint.Style.STROKE
p.strokeWidth = 1f * d
p.color = gridColor
canvas.drawLine(padL, py(0.0), width - padR, py(0.0), p)

val fill = Path()
fill.moveTo(px(0), py(0.0))
for (i in pts.indices) fill.lineTo(px(i), py(pts[i]))
fill.lineTo(px(pts.size - 1), py(0.0))
fill.close()
p.style = Paint.Style.FILL
p.color = lineColor
p.alpha = 45
canvas.drawPath(fill, p)

val line = Path()
for (i in pts.indices) { if (i == 0) line.moveTo(px(i), py(pts[i])) else line.lineTo(px(i), py(pts[i])) }
p.style = Paint.Style.STROKE
p.strokeWidth = 2.2f * d
p.color = lineColor
p.alpha = 255
canvas.drawPath(line, p)

p.style = Paint.Style.FILL
canvas.drawCircle(px(pts.size - 1), py(pts[pts.size - 1]), 3.6f * d, p)

p.color = labelColor
p.textSize = 10f * d
canvas.drawText(String.format(Locale.US, "max %.2f", hi), padL, padT - 5f * d, p)
if (lo < 0) canvas.drawText(String.format(Locale.US, "min %.2f", lo), padL, height - 2f * d, p)
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
Color.rgb(56, 190, 220), Color.rgb(220, 174, 72), Color.rgb(39, 190, 129),
Color.rgb(242, 157, 62), Color.rgb(160, 119, 235), Color.rgb(238, 91, 91),
Color.rgb(90, 160, 255), Color.rgb(240, 120, 180), Color.rgb(151, 168, 190)
)

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
val options = arrayOf("Accueil", "Dashboard", "Ajouter un trade", "Historique", "Journal & calendrier", "Statistiques", "Analyse graphique (volumes %)", "Gestion du risque", "Calculateur de lots", "Objectifs", "Assistant Spartan", "Conseils", "Paramètres")
AlertDialog.Builder(this).setTitle("LEONIDAS — Navigation").setItems(options) { _, which ->
when (which) {
0 -> showHome()
1 -> showDashboard()
2 -> showAddTrade()
3, 4 -> showHistory()
5 -> showStatistics()
6 -> showAnalytics()
7 -> showRisk()
8 -> showLotCalculator()
9 -> showGoals()
10, 11 -> showAdvice()
12 -> showSettings()
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
return " {diff % 60} min"
}

private fun showHome() {
baseScreen("LEONIDAS SPARTAN GOLD", "FOREX SESSION CLOCK • 100 % HORS CONNEXION")
val hero = card()
addLine(hero, "DISCIPLINE • PATIENCE • PRÉCISION", 12f, gold, true)
addLine(hero, "Objectif patrimonial", 12f, muted)
addLine(hero, "1 000 000 USD", 28f, gold, true)
addLine(hero, "Heure Madagascar (UTC+3) : latex
{localClock("Indian/Antananarivo")}", 15f, white, true) val clocks = card() addLine(clocks, "HORLOGES MONDIALES", 13f, cyan, true) listOf( "Sydney" to "Australia/Sydney", "Tokyo" to "Asia/Tokyo", "Londres" to "Europe/London", "New York" to "America/New_York" ).forEach { (name, zone) -&gt; addLine(clocks, "

name latex
{localClock(zone)}", 14f) } val sessions = card() addLine(sessions, "SESSIONS DE MARCHÉ", 15f, gold, true) val data = listOf( Triple("SYDNEY", "Australia/Sydney", Color.rgb(160, 119, 235)), Triple("TOKYO", "Asia/Tokyo", cyan), Triple("LONDRES", "Europe/London", green), Triple("NEW YORK", "America/New_York", orange) ) data.forEach { (name, zone, tint) -&gt; val open = sessionOpen(zone, 8 * 60, 17 * 60) val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL } val dot = TextView(this).apply { text = "●"; textSize = 17f; setTextColor(tint) } row.addView(dot) val label = TextView(this).apply { text = " 

name • latex
{if (open) "OUVERTE" else "FERMÉE"}" textSize = 13f setTextColor(if (open) green else muted) setTypeface(Typeface.DEFAULT, Typeface.BOLD) layoutParams = LinearLayout.LayoutParams(0, -2, 1f) } row.addView(label) row.addView(text(if (open) "Clôture 

{countdown(zone, 17)}" else "Ouverture $`{countdown(zone, 8)}", 11f, muted))
sessions.addView(row)
gap(sessions, 5)
val bar = View(this).apply { background = rounded(tint) }
sessions.addView(bar, LinearLayout.LayoutParams(-1, dp(7)))
gap(sessions, 5)
}
val map = card()
addLine(map, "CARTE DES FUSEAUX", 13f, cyan, true)
addLine(map, "Repère horaire mondial — les horaires s’adaptent automatiquement aux changements d’heure des fuseaux.", 12f, muted)
addLine(map, "◉ Sydney ◉ Tokyo ◉ Londres ◉ New York", 12f, gold)
gap()
val quick = card()
addLine(quick, "ACCÈS RAPIDE", 14f, gold, true)
quick.addView(button("＋ AJOUTER UN TRADE", { showAddTrade() }, gold))
gap(quick, 7)
quick.addView(button("TABLEAU DE BORD", { showDashboard() }))
gap(quick, 7)
quick.addView(button("ANALYSE GRAPHIQUE (VOLUMES %)", { showAnalytics() }))
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

// ───────────────────────── GRAPHIQUES & RÉPARTITIONS ─────────────────────────

/** Regroupe les trades selon un champ (asset, session, direction, emotion, error…). */
private fun groupBy(key: String): List<Pair<String, AssetStat>> {
val map = LinkedHashMap<String, AssetStat>()
for (i in 0 until trades.length()) {
val t = trades.optJSONObject(i) ?: continue
val k = t.optString(key, "—").ifBlank { "—" }
val s = map.getOrPut(k) { AssetStat() }
val p = t.optDouble("pnl", 0.0)
val lot = t.optDouble("lot", 0.0)
s.count++
s.lots += if (lot.isNaN()) 0.0 else lot
s.pnl += p
if (p > 0) s.wins++ else if (p < 0) s.losses++
}
return map.entries.map { it.key to it.value }.sortedByDescending { it.second.count }
}

private fun legendRow(parent: LinearLayout, color: Int, label: String, valueText: String) {
val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
row.addView(TextView(this).apply { text = "●"; textSize = 14f; setTextColor(color); setPadding(dp(2), 0, dp(6), 0) })
row.addView(text(label, 12f, white), LinearLayout.LayoutParams(0, -2, 1f))
row.addView(text(valueText, 12f, muted, true))
parent.addView(row)
}

/** Carte avec un anneau + légende en pourcentages. */
private fun donutCard(
title: String,
note: String,
entries: List<Pair<String, Double>>,
centerTop: String,
centerSub: String,
detail: (Double) -> String = { "" }
) {
val c = card()
addLine(c, title, 13f, cyan, true)
if (note.isNotBlank()) addLine(c, note, 11f, muted)
val data = entries.filter { it.second > 0 }
val total = data.sumOf { it.second }
if (total <= 0) {
addLine(c, "Pas encore de données pour ce graphique.", 12f, muted)
return
}
val colors = data.indices.map { palette[it % palette.size] }
c.addView(DonutView(this, data.map { it.second }, colors, centerTop, centerSub, white, muted), LinearLayout.LayoutParams(-1, dp(200)))
data.forEachIndexed { i, e ->
val pct = e.second * 100.0 / total
val extra = detail(e.second)
legendRow(c, colors[i], e.first, String.format(Locale.US, "%.1f %%", pct) + if (extra.isNotEmpty()) " • `$extra" else "")
}
}

/** Barre horizontale (0..1) avec libellé et valeur. */
private fun hBar(parent: LinearLayout, label: String, fraction: Double, valueText: String, color: Int) {
val f = fraction.coerceIn(0.0, 1.0).toFloat()
val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
row.addView(text(label, 12f, white), LinearLayout.LayoutParams(dp(78), -2))
val track = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = rounded(Color.rgb(34, 48, 70)) }
track.addView(View(this).apply { background = rounded(color) }, LinearLayout.LayoutParams(0, dp(10), f))
track.addView(View(this), LinearLayout.LayoutParams(0, dp(10), 1f - f))
row.addView(track, LinearLayout.LayoutParams(0, dp(10), 1f))
val v = text(valueText, 11f, muted, true)
v.gravity = Gravity.END
row.addView(v, LinearLayout.LayoutParams(dp(96), -2))
parent.addView(row)
gap(parent, 4)
}

private fun cumulativePoints(): List<Double> {
val list = ArrayList<Double>()
var run = 0.0
list.add(0.0)
for (i in 0 until trades.length()) { run += pnlAt(i); list.add(run) }
return list
}

private fun equityCard(title: String = "COURBE DE CAPITAL — P&L CUMULÉ") {
val c = card()
addLine(c, title, 13f, cyan, true)
if (trades.length() == 0) {
addLine(c, "Enregistre des trades pour afficher ta progression.", 12f, muted)
return
}
c.addView(LineView(this, cumulativePoints(), gold, Color.rgb(57, 75, 100), muted), LinearLayout.LayoutParams(-1, dp(170)))
}

/** Barres de répartition du volume (lots) par actif, en %. */
private fun volumeBarsCard(limit: Int) {
val c = card()
addLine(c, "VOLUME PAR ACTIF (% DES LOTS)", 13f, cyan, true)
val groups = groupBy("asset")
val totalLots = groups.sumOf { it.second.lots }
val byLots = totalLots > 0
val sorted = if (byLots) groups.sortedByDescending { it.second.lots } else groups
val total = if (byLots) totalLots else trades.length().toDouble()
if (trades.length() == 0) { addLine(c, "Aucun trade enregistré.", 12f, muted); return }
if (!byLots) addLine(c, "Volume non renseigné : répartition calculée sur le nombre de trades.", 11f, orange)
sorted.take(limit).forEachIndexed { i, g ->
val v = if (byLots) g.second.lots else g.second.count.toDouble()
val frac = if (total > 0) v / total else 0.0
hBar(c, g.first, frac, String.format(Locale.US, "%.1f %%", frac * 100), palette[i % palette.size])
}
}

/** Écran + bloc complet "Analyse graphique". */
private fun showAnalytics() {
baseScreen("ANALYSE GRAPHIQUE", "RÉPARTITIONS EN % • VOLUMES • PERFORMANCE")
if (trades.length() == 0) {
val empty = card()
addLine(empty, "Aucune donnée à analyser.", 15f, muted, true)
addLine(empty, "Enregistre des trades pour voir les répartitions de volume, win rate et P&L par actif.", 12f, muted)
content.addView(button("AJOUTER UN TRADE", { showAddTrade() }, gold))
return
}
addAnalytics()
}

private fun addAnalytics() {
val byAsset = groupBy("asset")
val bySession = groupBy("session")
val byDir = groupBy("direction")
val totalLots = byAsset.sumOf { it.second.lots }

// 0) Insights rapides
val insights = card()
addLine(insights, "INSIGHTS ACTIONNABLES", 14f, gold, true)
val bestA = byAsset.maxByOrNull { it.second.pnl }
val worstA = byAsset.minByOrNull { it.second.pnl }
val bestS = bySession.maxByOrNull { it.second.pnl }
val topVol = byAsset.maxByOrNull { it.second.lots }
if (bestA != null) addLine(insights, "★ Meilleur actif :  {money(bestA.second.pnl)})", 12f, green)
if (worstA != null && worstA.first != bestA?.first) addLine(insights, "▼ Actif le plus faible :  {money(worstA.second.pnl)})", 12f, red)
if (bestS != null) addLine(insights, "◔ Meilleure session :  {money(bestS.second.pnl)})", 12f, cyan)
if (topVol != null && totalLots > 0) addLine(insights, "◆ Actif le plus tradé en volume :  {String.format(Locale.US, "%.1f", topVol.second.lots * 100 / totalLots)} % des lots)", 12f, white)

// 1) Répartition du volume (lots) par actif — donut
donutCard(
"RÉPARTITION DU VOLUME PAR ACTIF",
"Part de chaque actif dans le volume total (lots).",
byAsset.map { it.first to it.second.lots },
String.format(Locale.US, "%.2f", totalLots), "lots au total"
) { String.format(Locale.US, "%.2f lot", it) }

// 2) Répartition du nombre de trades par actif — donut
donutCard(
"ACTIFS LES PLUS TRADÉS",
"Part du nombre de trades par actif.",
byAsset.map { it.first to it.second.count.toDouble() },
"latex
{trades.length()}", "trades" ) { "

{it.toInt()} trade(s)" }

// 3) Win rate par actif
val wr = card()
addLine(wr, "WIN RATE PAR ACTIF", 13f, cyan, true)
addLine(wr, "Taux de réussite de chaque instrument.", 11f, muted)
byAsset.forEach { (name, s) ->
val rate = if (s.count > 0) s.wins.toDouble() / s.count else 0.0
hBar(wr, name, rate, String.format(Locale.US, "%.0f %% (%d/%d)", rate * 100, s.wins, s.count), if (rate >= 0.5) green else orange)
}

// 4) P&L par actif
val pl = card()
addLine(pl, "P&L PAR ACTIF", 13f, cyan, true)
addLine(pl, "Profit & loss cumulé par instrument.", 11f, muted)
var maxAbs = 0.0
byAsset.forEach { maxAbs = max(maxAbs, abs(it.second.pnl)) }
byAsset.sortedByDescending { it.second.pnl }.forEach { (name, s) ->
val frac = if (maxAbs > 0) abs(s.pnl) / maxAbs else 0.0
hBar(pl, name, frac, money(s.pnl), if (s.pnl >= 0) green else red)
}

// 5) Résultats des trades
var be = 0
for (i in 0 until trades.length()) if (pnlAt(i) == 0.0) be++
donutCard(
"RÉSULTATS DES TRADES",
"Ratio gagnants / perdants / break-even.",
listOf("Gagnants" to countWins().toDouble(), "Perdants" to countLosses().toDouble(), "Break-even" to be.toDouble()),
String.format(Locale.US, "%.0f %%", winRate()), "win rate"
) { "$`{it.toInt()} trade(s)" }

// 6) Sessions
donutCard(
"RÉPARTITION PAR SESSION",
"Part des trades pris dans chaque session de marché.",
bySession.map { it.first to it.second.count.toDouble() },
"${bySession.size}", "session(s)" ) { "${it.toInt()} trade(s)" }
val sp = card()
addLine(sp, "P&L PAR SESSION", 13f, cyan, true)
bySession.forEach { (name, s) -> addLine(sp, "$name : ${s.count} trades • `${money(s.pnl)}", 12f, if (s.pnl >= 0) green else red) }

// 7) BUY vs SELL en volume
donutCard(
"BUY / SELL — VOLUME",
"Part du volume (lots) par direction.",
byDir.map { it.first to it.second.lots },
String.format(Locale.US, "%.2f", totalLots), "lots"
) { String.format(Locale.US, "%.2f lot", it) }

// 8) Performance horaire
val hourMap = java.util.TreeMap<Int, AssetStat>()
for (i in 0 until trades.length()) {
val t = trades.optJSONObject(i) ?: continue
val created = t.optLong("created", 0L)
if (created <= 0L) continue
val cal = java.util.Calendar.getInstance()
cal.timeInMillis = created
val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
val s = hourMap.getOrPut(h) { AssetStat() }
s.count++
s.pnl += t.optDouble("pnl", 0.0)
}
if (hourMap.isNotEmpty()) {
val hc = card()
addLine(hc, "PERFORMANCE HORAIRE", 13f, cyan, true)
addLine(hc, "Heure d’enregistrement du trade (heure locale de l’appareil).", 11f, muted)
hourMap.entries.map { it.key to it.value }.chunked(3).forEach { rowItems ->
val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
rowItems.forEach { (h, s) ->
val cell = LinearLayout(this).apply {
orientation = LinearLayout.VERTICAL
gravity = Gravity.CENTER_HORIZONTAL
setPadding(dp(6), dp(8), dp(6), dp(8))
background = rounded(panel2, if (s.pnl >= 0) Color.rgb(30, 90, 70) else Color.rgb(100, 45, 55))
}
cell.addView(text(String.format(Locale.US, "%02dh", h), 14f, white, true))
cell.addView(text("$`{s.count} trade(s)", 10f, muted))
cell.addView(text(String.format(Locale.US, "%+.2f", s.pnl), 12f, if (s.pnl >= 0) green else red, true))
val lp = LinearLayout.LayoutParams(0, -2, 1f)
lp.setMargins(dp(3), dp(3), dp(3), dp(3))
row.addView(cell, lp)
}
for (k in rowItems.size until 3) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
hc.addView(row)
}
}

// 9) Émotions & erreurs (en % des trades)
val total = trades.length().toDouble()
val em = card()
addLine(em, "ÉMOTIONS (% DES TRADES)", 13f, cyan, true)
groupBy("emotion").forEachIndexed { i, (name, s) ->
hBar(em, name, s.count / total, String.format(Locale.US, "%.0f %% • %s", s.count * 100 / total, money(s.pnl)), palette[i % palette.size])
}
val er = card()
addLine(er, "ERREURS (% DES TRADES)", 13f, cyan, true)
groupBy("error").forEach { (name, s) ->
hBar(er, name, s.count / total, String.format(Locale.US, "%.0f %% • %d", s.count * 100 / total, s.count), if (name == "Aucune") green else orange)
}

// 10) Courbe de capital
equityCard()
}

// ───────────────────────── ÉCRANS EXISTANTS ─────────────────────────

private fun showDashboard() {
baseScreen("TABLEAU DE BORD", "SYNTHÈSE DE PERFORMANCE")
val total = totalPnl()
val hero = card()
addLine(hero, "BONJOUR, $firstName", 13f, cyan, true) addLine(hero, "P&L NET", 12f, muted) addLine(hero, money(total), 29f, if (total &gt;= 0) green else red, true) addLine(hero, "${trades.length()} trades • ${String.format(Locale.US, "%.1f", winRate())}% gagnants", 13f, white) val stats = card() addLine(stats, "INDICATEURS CLÉS", 14f, gold, true) addLine(stats, "Profit Factor : ${pfText()}", 15f)
addLine(stats, "Drawdown maximum : ${money(drawdown())}", 14f) addLine(stats, "Trades gagnants : ${countWins()} | Perdants : ${countLosses()}", 13f) val best = bestTrade(true) val worst = bestTrade(false) addLine(stats, "Meilleur trade : ${if (best == null) "—" else money(best)}", 13f, green)
addLine(stats, "Pire trade : ${if (worst == null) "—" else money(worst)}", 13f, red) val goal = card() val pct = (total / 1_000_000.0 * 100.0).coerceIn(0.0, 100.0) addLine(goal, "OBJECTIF : 1 000 000 USD", 14f, gold, true) addLine(goal, "${String.format(Locale.US, "%.4f", pct)} % de progression calculée sur le P&L cumulé", 12f, muted)
val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
max = 1000
progress = (pct * 10).toInt()
progressTintList = android.content.res.ColorStateList.valueOf(gold)
}
goal.addView(progress, LinearLayout.LayoutParams(-1, dp(10)))

equityCard()
if (trades.length() > 0) {
// Mini dashboard : répartition des résultats + volume par actif
donutCard(
"RÉSULTATS DES TRADES", "",
listOf("Gagnants" to countWins().toDouble(), "Perdants" to countLosses().toDouble(), "Break-even" to (trades.length() - countWins() - countLosses()).toDouble()),
String.format(Locale.US, "%.0f %%", winRate()), "win rate"
) { "`${it.toInt()} trade(s)" }
volumeBarsCard(5)
}
gap()
content.addView(button("ANALYSE GRAPHIQUE COMPLÈTE", { showAnalytics() }))
gap(content, 7)
content.addView(button("＋ ENREGISTRER UN TRADE", { showAddTrade() }, gold))
}

private fun bestTrade(highest: Boolean): Double? {
if (trades.length() == 0) return null
var v = pnlAt(0)
for (i in 1 until trades.length()) v = if (highest) max(v, pnlAt(i)) else min(v, pnlAt(i))
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
val risk = field(form, "RISQUE INITIAL ( )", "Saisis le résultat réel MT5")
val mae = field(form, "MAE ( )", "0")
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
rrPreview.text = if (riskVal > 0) "R multiple estimé : latex
{String.format(Locale.US, "%.2f R", r / riskVal)}" else "R multiple : renseigne le risque initial" } pnl.setOnFocusChangeListener { _, _ -&gt; updateR() } risk.setOnFocusChangeListener { _, _ -&gt; updateR() } gap() content.addView(button("ENREGISTRER LE TRADE", { if (asset.text.toString().trim().isEmpty()) { Toast.makeText(this, "Renseigne l’actif.", Toast.LENGTH_SHORT).show() return@button } val dayCount = countTradesToday() if (dayCount &gt;= maxTradesPerDay) { AlertDialog.Builder(this).setTitle("Discipline verrouillée") .setMessage("Limite quotidienne atteinte (

dayCount/$`maxTradesPerDay). Modifie la limite dans Paramètres si nécessaire.")
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
baseScreen("JOURNAL & HISTORIQUE", "${trades.length()} TRADES ENREGISTRÉS") if (trades.length() == 0) { val empty = card() addLine(empty, "Ton journal est encore vide.", 15f, muted, true) addLine(empty, "Ajoute ton premier trade pour commencer à suivre tes performances.", 12f, muted) content.addView(button("AJOUTER UN TRADE", { showAddTrade() }, gold)) return } val summary = card() addLine(summary, "P&L TOTAL : ${money(totalPnl())}", 18f, if (totalPnl() >= 0) green else red, true)
addLine(summary, "Win rate : ${String.format(Locale.US, "%.1f", winRate())}% • PF : ${pfText()}", 12f, muted)
summary.addView(button("ANALYSE GRAPHIQUE (VOLUMES %)", { showAnalytics() }))
for (i in trades.length() - 1 downTo 0) {
val obj = trades.optJSONObject(i) ?: continue
val item = card()
val p = obj.optDouble("pnl", 0.0)
addLine(item, "${obj.optString("asset", "—")} • ${obj.optString("direction", "—")} • ${obj.optString("date", "—")}", 14f, gold, true) addLine(item, money(p), 18f, if (p &gt;= 0) green else red, true) addLine(item, "Lot ${obj.optString("lot", "—")} | ${obj.optString("session", "—")} | ${String.format(Locale.US, "%.2f", obj.optDouble("rMultiple", 0.0))} R", 11f, muted)
addLine(item, "Émotion : ${obj.optString("emotion", "—")} • Erreur : ${obj.optString("error", "—")}", 11f, muted)
addLine(item, obj.optString("notes", ""), 11f, white)
item.addView(button("SUPPRIMER CE TRADE", { confirmDelete(i) }, Color.rgb(104, 43, 53)))
}
gap()
content.addView(button("＋ AJOUTER UN TRADE", { showAddTrade() }, gold))
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
addLine(c, "Nombre total : ${trades.length()}") addLine(c, "Gagnants : ${countWins()} | Perdants : ${countLosses()}") addLine(c, "Win rate : ${String.format(Locale.US, "%.2f", winRate())}%")
addLine(c, "P&L net : ${money(totalPnl())}", 18f, if (totalPnl() &gt;= 0) green else red, true) val pl = card() addLine(pl, "PROFIT & LOSS", 14f, cyan, true) addLine(pl, "Profit brut : ${money(grossProfit())}", 13f, green)
addLine(pl, "Perte brute : ${money(grossLoss())}", 13f, red) addLine(pl, "Profit Factor : ${pfText()}")
addLine(pl, "Drawdown maximum : ${money(drawdown())}") val longShort = card() var buys = 0; var sells = 0; var buyPnl = 0.0; var sellPnl = 0.0 for (i in 0 until trades.length()) { val t = trades.optJSONObject(i) ?: continue if (t.optString("direction") == "BUY") { buys++; buyPnl += t.optDouble("pnl") } else { sells++; sellPnl += t.optDouble("pnl") } } addLine(longShort, "LONG & SHORT", 14f, gold, true) addLine(longShort, "BUY : $buys trades • ${money(buyPnl)}") addLine(longShort, "SELL : $sells trades • ${money(sellPnl)}") val sessions = card() addLine(sessions, "PERFORMANCE PAR SESSION", 14f, cyan, true) listOf("Sydney", "Tokyo", "Londres", "New York", "Hors session").forEach { s -&gt; var n = 0; var p = 0.0 for (i in 0 until trades.length()) { val t = trades.optJSONObject(i) ?: continue if (t.optString("session") == s) { n++; p += t.optDouble("pnl") } } addLine(sessions, "$s : $n trades • ${money(p)}", 12f, if (p >= 0) green else red)
}
val discipline = card()
var broken = 0
for (i in 0 until trades.length()) if (trades.optJSONObject(i)?.optString("plan") == "Non") broken++
addLine(discipline, "DISCIPLINE", 14f, gold, true)
addLine(discipline, "Trades hors plan : $broken") addLine(discipline, "Respect du plan : ${if (trades.length() > 0) String.format(Locale.US, "%.1f", (trades.length() - broken) * 100.0 / trades.length()) else "0.0"} %")
addLine(discipline, "MAE moyen : ${money(averageField("mae"))} • MFE moyen : ${money(averageField("mfe"))}")
gap()
content.addView(button("ANALYSE GRAPHIQUE (VOLUMES %)", { showAnalytics() }, gold))
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
val capital = field(c, "CAPITAL DISPONIBLE ($)", "Ex. 1000") val percent = field(c, "RISQUE PAR TRADE (%)", "Ex. 1") val sl = field(c, "STOP LOSS (POINTS / PIPS)", "Ex. 250") val value = field(c, "VALEUR PAR POINT POUR 1 LOT ($)", "À vérifier chez ton broker")
val result = text("Taille de lot estimée : —", 16f, gold, true)
c.addView(result)
c.addView(button("CALCULER LA TAILLE", {
val riskAmount = num(capital) * num(percent) / 100.0
val denom = num(sl) * num(value)
result.text = if (denom > 0) "Risque : ${money(riskAmount)}\nTaille estimée : ${String.format(Locale.US, "%.4f", riskAmount / denom)} lot" else "Renseigne un stop et une valeur par point valides."
}, gold))
val rules = card()
addLine(rules, "RÈGLES DE DISCIPLINE", 14f, cyan, true)
addLine(rules, "Trades maximum par jour : $maxTradesPerDay") addLine(rules, "Limite de perte quotidienne : ${if (dailyRiskLimit > 0) money(dailyRiskLimit) else "Non configurée"}")
addLine(rules, "Trades pris aujourd’hui : `${countTradesToday()}")
addLine(rules, "Ne risque jamais un montant que tu ne peux pas te permettre de perdre.", 12f, orange)
content.addView(button("CONFIGURER MES LIMITES", { showSettings() }))
}

private fun showLotCalculator() {
baseScreen("CALCULATEUR DE LOTS", "ESTIMATION — VÉRIFIE LA VALEUR DU POINT")
val c = card()
val cap
