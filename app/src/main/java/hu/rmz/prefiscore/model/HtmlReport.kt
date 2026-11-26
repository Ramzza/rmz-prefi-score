package hu.rmz.prefiscore.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    private val dateTime = SimpleDateFormat("yyyy.MM.dd. HH:mm", Locale.forLanguageTag("hu"))
    private val fileDate = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT)

    fun dateTime(ms: Long): String = synchronized(dateTime) { dateTime.format(Date(ms)) }
    fun fileStamp(ms: Long): String = synchronized(fileDate) { fileDate.format(Date(ms)) }

    fun duration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun signed(v: Int): String = if (v > 0) "+$v" else v.toString()
    fun percent(d: Double): String = "%.0f%%".format(d * 100)

    fun statusText(s: GameStatus): String = when (s) {
        GameStatus.ACTIVE -> "Folyamatban"
        GameStatus.PAUSED -> "Szüneteltetve"
        GameStatus.FINISHED -> "Befejezve"
    }

    fun safeFileName(s: String): String =
        s.replace(Regex("[^A-Za-z0-9áéíóöőúüűÁÉÍÓÖŐÚÜŰ_-]+"), "_").trim('_').take(40).ifEmpty { "jatek" }

    /** Egy kör rövid leírása. */
    fun roundTitle(game: Game, r: Round): String = if (r.kind == RoundKind.KORPASSZ) "Körpassz" else buildString {
        append(game.name(r.declarerId)).append(" – ").append(Rules.gameName(r.game))
        if (r.felkez) append(", félkéz") else if (!r.talon) append(", talon nélkül")
        if (r.contra > 0) append(", ").append(Rules.contraNames[r.contra])
        append(if (r.made == true) " · megcsinálta" else " · megbukta")
    }

    fun tricksText(game: Game, r: Round): String =
        r.tricks.entries.joinToString(", ") { "${game.name(it.key)}: ${it.value}" }
}

object HtmlReport {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun render(game: Game, now: Long): String {
        val st = Scoring.standings(game)
        val players = game.players
        val ranked = players.sortedByDescending { st.final(it.id) }
        val gs = StatsCalculator.forGame(game)
        val sb = StringBuilder()
        sb.append(
            """<!doctype html><html lang="hu"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Prefi – ${esc(game.title)}</title>
<style>
:root{--bg:#f6f4ee;--card:#fff;--ink:#1d2421;--muted:#66706b;--line:#dcd8cc;--accent:#1f6f4a;--neg:#b3261e;--pos:#1f6f4a}
@media (prefers-color-scheme:dark){:root{--bg:#121714;--card:#1b221e;--ink:#e6ebe8;--muted:#9aa59f;--line:#2e3832;--accent:#7fd3a6;--neg:#ff8a80;--pos:#7fd3a6}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.45 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
main{max-width:860px;margin:0 auto;padding:20px 16px 40px}
h1{font-size:22px;margin:0 0 4px}h2{font-size:16px;margin:28px 0 10px;color:var(--accent);text-transform:uppercase;letter-spacing:.06em}
.meta{color:var(--muted);font-size:13px}
.card{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:12px 14px;margin:8px 0}
table{width:100%;border-collapse:collapse;font-variant-numeric:tabular-nums}
th,td{padding:7px 8px;border-bottom:1px solid var(--line);text-align:right}
th:first-child,td:first-child{text-align:left}th{color:var(--muted);font-weight:600;font-size:12px}
.neg{color:var(--neg)}.pos{color:var(--pos)}.big{font-size:20px;font-weight:700}
.rank{display:flex;justify-content:space-between;align-items:baseline}
.small{font-size:12px;color:var(--muted)}
.wrap{overflow-x:auto}
</style></head><body><main>
"""
        )
        sb.append("<h1>Prefi pontozás</h1>")
        sb.append("<div class=\"meta\">${esc(players.joinToString(", ") { it.name })}<br>")
        sb.append("Kezdés: ${Format.dateTime(game.createdAt)} · Állapot: ${Format.statusText(game.status)}")
        game.finishedAt?.let { sb.append(" · Vége: ${Format.dateTime(it)}") }
        sb.append("<br>Körök: ${game.rounds.size} · Játékidő: ${Format.duration(gs.playMs)} · Átlagos kör: ${Format.duration(gs.avgRoundMs)}")
        sb.append("<br>Hátralévő pont fent: ${st.remaining} · Aktív druzs: ${st.druzsActive}</div>")

        sb.append("<h2>Végeredmény</h2>")
        ranked.forEachIndexed { i, p ->
            val f = st.final(p.id)
            sb.append("<div class=\"card rank\"><span>${i + 1}. ${esc(p.name)}${if (p.benched) " <span class=small>(kispad)</span>" else ""}</span>")
            sb.append("<span class=\"big ${cls(f)}\">${Format.signed(f)}</span></div>")
        }

        sb.append("<h2>Részletek (fent + pince)</h2><div class=\"card wrap\"><table><tr><th>Játékos</th><th>Fent</th><th>Fent ×10</th><th>Saját pince</th><th>Rá írva</th><th>Összesen</th></tr>")
        for (p in players) {
            val f = st.final(p.id)
            sb.append("<tr><td>${esc(p.name)}</td><td>${st.upperOf(p.id)}</td><td>${st.upperOf(p.id) * 10}</td><td>${st.ownPince(p.id)}</td><td>−${st.writtenOn(p.id)}</td><td class=\"${cls(f)}\"><b>${Format.signed(f)}</b></td></tr>")
        }
        sb.append("</table></div>")

        sb.append("<h2>Pince (sor: kinek a pincéje, oszlop: kinek a szobája)</h2><div class=\"card wrap\"><table><tr><th></th>")
        players.forEach { sb.append("<th>${esc(it.name)}</th>") }
        sb.append("</tr>")
        for (o in players) {
            sb.append("<tr><td>${esc(o.name)}</td>")
            for (t in players) sb.append(if (o.id == t.id) "<td>–</td>" else "<td>${st.room(o.id, t.id)}</td>")
            sb.append("</tr>")
        }
        sb.append("</table></div>")

        sb.append("<h2>Körök</h2><div class=\"card wrap\"><table><tr><th>#</th><th>Osztó</th><th>Játék</th><th>Érték</th><th>Fent</th><th>Pince</th><th>Idő</th></tr>")
        for (res in st.results) {
            val r = res.round
            val upper = res.upper.entries.joinToString("<br>") { "${esc(game.name(it.key))} ${Format.signed(it.value)}" }
            val pince = res.pince.joinToString("<br>") { "${esc(game.name(it.ownerId))} → ${esc(game.name(it.targetId))}: ${it.amount}" }
            var desc = esc(Format.roundTitle(game, r))
            if (r.tricks.isNotEmpty()) desc += "<br><span class=small>Vitelek: ${esc(Format.tricksText(game, r))}</span>"
            if (res.druzsActive) desc += "<br><span class=small>druzs</span>"
            sb.append("<tr><td>${r.number}</td><td>${esc(game.name(r.dealerId))}</td><td style=\"text-align:left\">$desc</td><td>${if (res.value > 0) res.value else ""}</td><td>$upper</td><td>$pince</td><td>${Format.duration(r.durationMs)}</td></tr>")
        }
        sb.append("</table></div>")
        sb.append("<p class=\"small\">Végső pont = fent × 10 + saját pince − a többiek pincéjében levő saját szoba. Készült: ${Format.dateTime(now)} · Prefi pontozó</p>")
        sb.append("</main></body></html>")
        return sb.toString()
    }

    private fun cls(v: Int) = if (v < 0) "neg" else if (v > 0) "pos" else ""
}
