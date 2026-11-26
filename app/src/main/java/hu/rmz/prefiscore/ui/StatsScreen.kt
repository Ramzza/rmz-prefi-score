@file:OptIn(ExperimentalLayoutApi::class)

package hu.rmz.prefiscore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.rmz.prefiscore.model.Format
import hu.rmz.prefiscore.model.Rules
import hu.rmz.prefiscore.model.StatsCalculator
import hu.rmz.prefiscore.model.TypeCount

@Composable
fun StatsScreen(app: AppState) {
    val stats = remember(app.games.toList()) { StatsCalculator.overall(app.games.toList()) }
    Scaffold(topBar = { AppTopBar("Statisztika", onBack = { app.back() }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (stats.games == 0) {
                EmptyState("Még nincs lejátszott játék.")
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("játék", "${stats.games}", Modifier.weight(1f))
                StatTile("befejezett", "${stats.finished}", Modifier.weight(1f))
                StatTile("kör", "${stats.rounds}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("össz. játékidő", Format.duration(stats.totalPlayMs), Modifier.weight(1f))
                StatTile("átlagos kör", Format.duration(stats.avgRoundMs), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            InfoCard {
                StatRow("Folyamatban / szünetel", "${stats.active} / ${stats.paused}")
                StatRow("Átlagos játékidő (befejezett)", Format.duration(stats.avgGameMs))
                StatRow("Átlagos körszám (befejezett)", "%.1f".format(stats.avgRoundsPerFinishedGame))
                StatRow("Leghosszabb kör", Format.duration(stats.longestRoundMs))
                StatRow("Körpasszok", "${stats.korpassz} (${Format.percent(if (stats.rounds == 0) 0.0 else stats.korpassz.toDouble() / stats.rounds)})")
                StatRow("Megcsinált játékok", "${stats.madeRounds} / ${stats.gameRounds} (${Format.percent(if (stats.gameRounds == 0) 0.0 else stats.madeRounds.toDouble() / stats.gameRounds)})")
            }

            if (stats.byType.isNotEmpty()) {
                SectionTitle("Játéktípusok")
                InfoCard { TypeBars(stats.byType) }
            }

            SectionTitle("Játékosok")
            stats.players.forEach { p ->
                InfoCard(Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Text("${p.wins} győzelem", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    StatRow("Játékok (befejezett)", "${p.games} (${p.finishedGames})")
                    StatRow("Győzelmi arány", Format.percent(p.winRate))
                    StatRow("Átlagos végeredmény", "%.0f".format(p.avgFinal), ScoreColors.of(p.avgFinal.toInt()))
                    StatRow("Játszott körök", "${p.roundsPlayed}")
                    StatRow("Megnyert licit", "${p.declared} (${Format.percent(p.declareRate)} a körökből)")
                    StatRow("Megcsinált / megbukott", "${p.made} / ${p.declared - p.made}")
                    StatRow("Sikerességi arány", Format.percent(p.successRate))
                    StatRow("Ellenjátékban buktatott", "${p.defendedWon} / ${p.defended}")
                    StatRow("Kevés vitel miatti −1", "${p.penalties}")
                    StatRow("Körpassz vitelek (átlag)", if (p.korpassz == 0) "–" else "%.1f".format(p.korpasszTricks.toDouble() / p.korpassz))
                    StatRow("Kontrázott / félkéz játék", "${p.contraGames} / ${p.felkezGames}")
                    if (p.byType.isNotEmpty()) {
                        Divider()
                        Text("Licitált játékok", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        p.byType.forEach { (code, tc) -> StatRow(Rules.gameName(code), "${tc.made}/${tc.declared}") }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TypeBars(byType: Map<Int, TypeCount>) {
    val max = byType.values.maxOf { it.declared }.coerceAtLeast(1)
    byType.forEach { (code, tc) ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
            Text(Rules.gameName(code), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp), maxLines = 1)
            Box(Modifier.weight(1f).height(16.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth(tc.declared.toFloat() / max).fillMaxHeight(),
                ) {}
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth(tc.made.toFloat() / max).fillMaxHeight(),
                ) {}
            }
            Text("${tc.made}/${tc.declared}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(52.dp).padding(start = 8.dp), fontWeight = FontWeight.Medium)
        }
    }
    Text("sötét: megcsinált · világos: összes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
