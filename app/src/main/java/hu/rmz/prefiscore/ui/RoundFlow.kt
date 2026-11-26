package hu.rmz.prefiscore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.rmz.prefiscore.model.DraftStep
import hu.rmz.prefiscore.model.Format
import hu.rmz.prefiscore.model.Game
import hu.rmz.prefiscore.model.GameEngine
import hu.rmz.prefiscore.model.RoundDraft
import hu.rmz.prefiscore.model.Rules
import hu.rmz.prefiscore.model.Scoring
import hu.rmz.prefiscore.model.Standing
import hu.rmz.prefiscore.model.Table

/** A kör beírásának lépésenkénti, gombnyomásos folyamata. */
@Composable
fun RoundFlow(session: GameSession, standing: Standing) {
    val game = session.game
    val dealer = Table.currentDealer(game) ?: return
    val d = game.draft ?: RoundDraft(startedAt = System.currentTimeMillis())
    val playing = Table.playing(game, dealer, d.korpassz)
    val druzs = standing.druzsActive > 0

    RoundHeader(game, standing, dealer, playing, d)
    Spacer(Modifier.height(12.dp))

    when (d.step) {
        DraftStep.WINNER -> {
            StepTitle("Ki nyerte a licitet?")
            playing.forEach { id ->
                ChoiceButton(
                    title = game.name(id),
                    selected = d.declarerId == id,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) { session.update { g, now -> GameEngine.chooseWinner(g, id, now) } }
            }
            ChoiceButton(
                title = "Körpassz",
                subtitle = "Mindenki passzolt — nincs tromf, a vitelek számítanak" +
                    if (Table.playing(game, dealer, true).size > playing.size) " (az osztó is játszik a talonnal)" else "",
                accent = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth(),
            ) { session.update(GameEngine::chooseKorpassz) }
        }

        DraftStep.GAME -> {
            StepTitle("${game.name(d.declarerId)} játéka:")
            Rules.playable.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    row.forEach { t ->
                        ChoiceButton(
                            title = "${t.code} · ${t.name}",
                            subtitle = t.condition,
                            selected = d.game == t.code,
                            accent = if (t.code == 4 || t.code == 10) MaterialTheme.colorScheme.secondary else null,
                            modifier = Modifier.weight(1f),
                        ) { session.update { g, now -> GameEngine.chooseGame(g, t.code, now) } }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            BackRow(session)
        }

        DraftStep.MODIFIERS -> {
            StepTitle("${game.name(d.declarerId)} – ${Rules.gameName(d.game)}")
            InfoCard {
                ToggleRow("Talonnal", "Talon nélkül +1 érték", d.talon, enabled = !d.felkez) { v ->
                    session.update { g, now -> GameEngine.setTalon(g, v, now) }
                }
                ToggleRow("Félkéz", "5 lapból licitált: +1, majd ×2", d.felkez) { v ->
                    session.update { g, now -> GameEngine.setFelkez(g, v, now) }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Kontra", style = MaterialTheme.typography.titleMedium)
                        Text(
                            Rules.contraNames[d.contra] + if (d.contra > 0) " (×${1 shl d.contra})" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalIconButton(onClick = { session.update { g, now -> GameEngine.changeContra(g, -1, now) } }, enabled = d.contra > 0) { Text("−") }
                    Spacer(Modifier.width(4.dp))
                    FilledTonalIconButton(onClick = { session.update { g, now -> GameEngine.changeContra(g, 1, now) } }, enabled = d.contra < Rules.maxContra) { Text("+") }
                }
            }
            Spacer(Modifier.height(12.dp))
            ValueCard(d, druzs)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { session.update(GameEngine::toOutcome) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Tovább: kimenetel")
            }
            BackRow(session)
        }

        DraftStep.OUTCOME -> {
            StepTitle("Hogy sikerült ${game.name(d.declarerId)} játéka?")
            Text(
                "${Rules.gameName(d.game)}: ${Rules.type(d.game)?.condition}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { session.update { g, now -> GameEngine.setOutcome(g, true, now) } },
                    modifier = Modifier.weight(1f).height(72.dp),
                ) { Text("Megcsinálta", style = MaterialTheme.typography.titleMedium) }
                Button(
                    onClick = { session.update { g, now -> GameEngine.setOutcome(g, false, now) } },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.weight(1f).height(72.dp),
                ) { Text("Megbukta", style = MaterialTheme.typography.titleMedium) }
            }
            BackRow(session)
        }

        DraftStep.TRICKS -> {
            val defenders = GameEngine.defenders(game)
            StepTitle("Ellenjátékosok vitelei")
            Text(
                "A vitelek × a játék értéke kerül a pincébe. Aki 2-nél kevesebbet vitt, −1-et kap fent, hacsak ketten együtt nem vittek legalább 3-at.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            defenders.forEach { id ->
                InfoCard(Modifier.padding(bottom = 8.dp)) {
                    Text(game.name(id), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    NumberPicker(d.tricks[id] ?: 0, Rules.TOTAL_TRICKS) { n -> session.update { g, now -> GameEngine.setTricks(g, id, n, now) } }
                }
            }
            val sum = defenders.sumOf { d.tricks[it] ?: 0 }
            Text(
                "${game.name(d.declarerId)} vitelei: ${(Rules.TOTAL_TRICKS - sum).coerceAtLeast(0)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            TricksMessages(game)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { session.update(GameEngine::toConfirm) },
                enabled = GameEngine.tricksError(game) == null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Tovább: összesítés") }
            BackRow(session)
        }

        DraftStep.KORPASSZ_TRICKS -> {
            StepTitle("Körpassz – vitelek")
            Text(
                "Minden vitel −1 fent; 6 vagy több vitelnél duplán számít. Utána ${playing.size} druzs kerül fel.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            playing.forEach { id ->
                val max = Table.korpasszCards(playing, dealer, id)
                InfoCard(Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(game.name(id), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (max < Rules.TOTAL_TRICKS) Text("osztó, $max lap", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    NumberPicker(d.tricks[id] ?: 0, max) { n -> session.update { g, now -> GameEngine.setTricks(g, id, n, now) } }
                }
            }
            val sum = playing.sumOf { d.tricks[it] ?: 0 }
            Text("Összesen: $sum / ${Rules.TOTAL_TRICKS}", style = MaterialTheme.typography.bodyLarge)
            TricksMessages(game)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { session.update(GameEngine::toConfirm) },
                enabled = GameEngine.tricksError(game) == null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Tovább: összesítés") }
            BackRow(session)
        }

        DraftStep.CONFIRM -> {
            StepTitle("Összesítés")
            val res = Scoring.preview(game, standing)
            InfoCard {
                if (res == null) Text("Hiányos adatok.")
                else {
                    val r = res.round
                    Text(Format.roundTitle(game, r), style = MaterialTheme.typography.titleMedium)
                    if (res.value > 0) Text(
                        "Érték: ${res.value}" + Scoring.multiplierText(r.talon, r.felkez, r.contra, res.druzsActive).let { if (it.isEmpty()) "" else " ($it)" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (r.tricks.isNotEmpty()) Text("Vitelek: ${Format.tricksText(game, r)}", style = MaterialTheme.typography.bodyMedium)
                    Divider()
                    Text("Fent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (res.upper.isEmpty()) Text("nincs változás", style = MaterialTheme.typography.bodyMedium)
                    res.upper.forEach { (id, v) ->
                        StatRow(
                            game.name(id) + if (id in res.penalized && id != r.declarerId) " (kevés vitel)" else "",
                            "${Format.signed(v)}  →  ${standing.upperOf(id) + v}",
                            ScoreColors.of(v),
                        )
                    }
                    if (res.pince.isNotEmpty()) {
                        Text("Pince", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                        res.pince.forEach { e ->
                            StatRow("${game.name(e.ownerId)} pincéjében ${game.name(e.targetId)} szobája", "+${e.amount}")
                        }
                    }
                    if (res.druzsCrossed > 0 || res.druzsAdded > 0) {
                        Text("Druzs", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                        if (res.druzsCrossed > 0) StatRow("Áthúzva", "${res.druzsCrossed}")
                        if (res.druzsAdded > 0) StatRow("Új druzs", "+${res.druzsAdded}")
                    }
                    val newRemaining = (standing.remaining - res.upper.values.sum()).coerceAtLeast(0)
                    Divider()
                    StatRow("Hátra fent a kör után", "$newRemaining")
                    if (newRemaining == 0) Text("Ezzel a körrel vége a játéknak!", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { session.update(GameEngine::commit) },
                enabled = res != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Kör rögzítése", style = MaterialTheme.typography.titleMedium) }
            BackRow(session)
        }
    }
}

@Composable
private fun RoundHeader(game: Game, standing: Standing, dealer: String, playing: List<String>, d: RoundDraft) {
    InfoCard {
        Row {
            Column(Modifier.weight(1f)) {
                Text("Osztó", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(game.name(dealer), style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f)) {
                Text("Kezd", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(game.name(Table.leader(playing, d.game, d.declarerId, d.korpassz)), style = MaterialTheme.typography.titleMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Hátra fent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${standing.remaining}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Játszanak: " + playing.joinToString(", ") { game.name(it) } +
                game.players.filter { it.benched }.let { b -> if (b.isEmpty()) "" else " · Kispad: " + b.joinToString { it.name } },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Druzs: ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DruzsMarks(standing.druzsWritten, standing.druzsCrossed)
        }
        if (standing.druzsActive > 0) Text(
            "Aktív druzs: a játék értéke duplázódik.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun ValueCard(d: RoundDraft, druzs: Boolean) {
    val code = d.game ?: return
    val v = Scoring.value(code, d.talon, d.felkez, d.contra, druzs)
    val mult = Scoring.multiplierText(d.talon, d.felkez, d.contra, druzs)
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("A játék értéke", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (mult.isEmpty()) "alapérték" else "alap $code · $mult", style = MaterialTheme.typography.bodySmall)
            }
            Text("$v", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TricksMessages(game: Game) {
    GameEngine.tricksError(game)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    GameEngine.tricksWarning(game)?.let { Text("Figyelem: $it", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun BackRow(session: GameSession) {
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        androidx.compose.material3.TextButton(onClick = { session.update(GameEngine::back) }) { Text("‹ Előző lépés") }
        androidx.compose.material3.TextButton(onClick = { session.update(GameEngine::resetDraft) }) { Text("Kör újrakezdése") }
    }
}
