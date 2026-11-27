@file:OptIn(ExperimentalLayoutApi::class)

package hu.rmz.prefiscore.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import hu.rmz.prefiscore.model.Format
import hu.rmz.prefiscore.model.Game
import hu.rmz.prefiscore.model.Rules
import hu.rmz.prefiscore.model.Scoring
import hu.rmz.prefiscore.model.Standing
import hu.rmz.prefiscore.model.StatsCalculator

@Composable
fun RankingList(game: Game, standing: Standing) {
    val ranked = game.players.sortedByDescending { standing.final(it.id) }
    ranked.forEachIndexed { i, p ->
        val f = standing.final(p.id)
        InfoCard(Modifier.padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}.", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "fent ${standing.upperOf(p.id)} · pince ${standing.ownPince(p.id)} · rá írva ${standing.writtenOn(p.id)}" +
                            if (p.benched) " · kispad" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(Format.signed(f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ScoreColors.of(f))
            }
        }
    }
}

/** Pontok nézet: összesítés, részletes fent + pince, pincemátrix. */
@Composable
fun ScoresView(game: Game, standing: Standing) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("hátra fent", "${standing.remaining}", Modifier.weight(1f))
        StatTile("fenti összeg", "${standing.upperSum}", Modifier.weight(1f))
        StatTile("aktív druzs", "${standing.druzsActive}", Modifier.weight(1f))
    }

    SectionTitle("Összesítés")
    RankingList(game, standing)

    SectionTitle("Részletes pontok")
    game.players.forEach { p ->
        val up = standing.upperOf(p.id)
        val f = standing.final(p.id)
        InfoCard(Modifier.padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(Format.signed(f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ScoreColors.of(f))
            }
            Spacer(Modifier.height(6.dp))
            Text("FENT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            val hist = standing.upperHistory[p.id].orEmpty()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                hist.forEachIndexed { i, v ->
                    val last = i == hist.lastIndex
                    Text(
                        "$v",
                        style = if (last) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (last) FontWeight.Bold else FontWeight.Normal,
                        textDecoration = if (last) null else TextDecoration.LineThrough,
                        color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("PINCE (szobák)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            game.players.filter { it.id != p.id }.forEach { o ->
                StatRow("${o.name} szobája", "${standing.room(p.id, o.id)}")
            }
            Divider()
            StatRow("Fent × 10", "${up * 10}")
            StatRow("+ saját pince", "${standing.ownPince(p.id)}")
            StatRow("− mások pincéjében", "${standing.writtenOn(p.id)}")
            StatRow("= Összesen", Format.signed(f), ScoreColors.of(f))
        }
    }

    SectionTitle("Pince-mátrix")
    Text(
        "Sor: kinek a pincéje · oszlop: kinek a szobája",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    InfoCard {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row {
                Cell("", header = true, first = true)
                game.players.forEach { Cell(it.name, header = true) }
                Cell("Σ", header = true)
            }
            game.players.forEach { o ->
                Row {
                    Cell(o.name, header = true, first = true)
                    game.players.forEach { t -> Cell(if (o.id == t.id) "–" else "${standing.room(o.id, t.id)}") }
                    Cell("${standing.ownPince(o.id)}", header = true)
                }
            }
        }
    }
}

@Composable
private fun Cell(text: String, header: Boolean = false, first: Boolean = false) {
    Text(
        text,
        modifier = Modifier.width(if (first) 90.dp else 64.dp).padding(vertical = 6.dp, horizontal = 4.dp),
        textAlign = if (first) TextAlign.Start else TextAlign.End,
        maxLines = 1,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

/** Körök listája és a játék időstatisztikája. */
@Composable
fun RoundsView(game: Game, standing: Standing) {
    val gs = remember(game) { StatsCalculator.forGame(game) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("kör", "${gs.rounds}", Modifier.weight(1f))
        StatTile("játékidő", Format.duration(gs.playMs), Modifier.weight(1f))
        StatTile("átlagos kör", Format.duration(gs.avgRoundMs), Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    InfoCard {
        StatRow("Kezdés", Format.dateTime(game.createdAt))
        game.finishedAt?.let { StatRow("Vége", Format.dateTime(it)) }
        StatRow("Leghosszabb kör", Format.duration(gs.longestRoundMs))
        StatRow("Legrövidebb kör", Format.duration(gs.shortestRoundMs))
        StatRow("Körpasszok", "${gs.korpassz}")
        Divider()
        gs.perPlayer.forEach { s ->
            StatRow(s.name, "licit ${s.declared} · megcsinált ${s.made} (${Format.percent(s.successRate)})")
        }
    }

    SectionTitle("Körök")
    if (standing.results.isEmpty()) EmptyState("Még nincs rögzített kör.")
    standing.results.asReversed().forEach { res ->
        val r = res.round
        InfoCard(Modifier.padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (r.isAdjustment) "K" else "#${r.number}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
                Column(Modifier.weight(1f)) {
                    Text(Format.roundTitle(game, r), style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (r.isAdjustment) "Következő osztó: ${game.name(r.dealerId)} · ${Format.dateTime(r.endedAt)}" +
                            (if (res.druzsAdded != 0 || res.druzsCrossed != 0) " · druzs ${Format.signed(res.druzsAdded)}/${Format.signed(res.druzsCrossed)}" else "")
                        else "Osztó: ${game.name(r.dealerId)} · ${Format.duration(r.durationMs)}" +
                            (if (res.value > 0) " · érték ${res.value}" else "") +
                            (if (res.druzsActive) " · druzs" else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (r.tricks.isNotEmpty()) Text("Vitelek: ${Format.tricksText(game, r)}", style = MaterialTheme.typography.bodySmall)
            val parts = res.upper.map { "${game.name(it.key)} ${Format.signed(it.value)}" } +
                res.pince.map { "${game.name(it.ownerId)}→${game.name(it.targetId)} ${Format.signed(it.amount)}" }
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (r.kind == hu.rmz.prefiscore.model.RoundKind.GAME && r.game != null && res.value > 0) {
                val m = Scoring.multiplierText(r.talon, r.felkez, r.contra, res.druzsActive)
                if (m.isNotEmpty()) Text("${Rules.gameName(r.game)} ${r.game} · $m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
