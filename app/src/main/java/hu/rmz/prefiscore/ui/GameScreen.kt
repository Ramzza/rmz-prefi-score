@file:OptIn(ExperimentalLayoutApi::class)

package hu.rmz.prefiscore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import hu.rmz.prefiscore.model.Game
import hu.rmz.prefiscore.model.GameEngine
import hu.rmz.prefiscore.model.GameStatus
import hu.rmz.prefiscore.model.JsonCodec
import hu.rmz.prefiscore.model.Scoring
import hu.rmz.prefiscore.model.Table

@Composable
fun GameScreen(app: AppState, session: GameSession) {
    val game = session.game
    val standing = remember(game) { Scoring.standings(game) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var showBench by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val saveJson = rememberJsonSaver()
    val finished = game.status == GameStatus.FINISHED

    fun exportText() = JsonCodec.encodeBundle(listOf(session.game), System.currentTimeMillis())

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (finished) "Játék vége" else "${game.playedRounds.size + 1}. kör",
                subtitle = "Hátra fent: ${standing.remaining} pont",
                onBack = { app.back() },
            ) {
                IconButton(onClick = session::undo, enabled = session.canUndo) { Icon(AppIcons.Undo, "Visszavonás") }
                IconButton(onClick = session::redo, enabled = session.canRedo) { Icon(AppIcons.Redo, "Újra") }
                IconButton(onClick = { menu = true }) { Icon(AppIcons.More, "Menü") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Kispad / játékosok") }, enabled = !finished, onClick = { menu = false; showBench = true })
                    DropdownMenuItem(text = { Text("Pontok frissítése képről") }, onClick = { menu = false; app.startSheetFlow(SheetMode.UPDATE_FROM_IMAGE) })
                    DropdownMenuItem(text = { Text("Pontok kézi javítása") }, onClick = { menu = false; app.startSheetFlow(SheetMode.MANUAL) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Mentés JSON fájlba") }, onClick = {
                        menu = false; saveJson("${gameFileBase(game)}.json", exportText())
                    })
                    DropdownMenuItem(text = { Text("Küldés WhatsAppon (JSON)") }, onClick = {
                        menu = false; shareJson(ctx, "${gameFileBase(game)}.json", exportText(), whatsapp = true)
                    })
                    DropdownMenuItem(text = { Text("Megosztás (JSON)…") }, onClick = {
                        menu = false; shareJson(ctx, "${gameFileBase(game)}.json", exportText(), whatsapp = false)
                    })
                    DropdownMenuItem(text = { Text("Eredmény megosztása (HTML)") }, onClick = { menu = false; shareHtml(ctx, game) })
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Utolsó bejegyzés törlése") }, enabled = game.rounds.isNotEmpty(),
                        onClick = { menu = false; confirmRemove = true },
                    )
                    DropdownMenuItem(
                        text = { Text(if (finished) "Bezárás" else "Játék szüneteltetése") },
                        onClick = { menu = false; if (finished) app.back() else app.pauseCurrent() },
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(AppIcons.Cards, null) }, label = { Text("Kör") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(AppIcons.Scores, null) }, label = { Text("Pontok") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(AppIcons.List, null) }, label = { Text("Körök") })
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            if (showBench) BenchPanel(session, onDone = { showBench = false })
            else when (tab) {
                0 -> if (finished) FinishedPanel(game, standing, onScores = { tab = 1 }, onHome = { app.goHome() }, onShare = { shareHtml(ctx, game) })
                else RoundFlow(session, standing)
                1 -> ScoresView(game, standing)
                else -> RoundsView(game, standing)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text("Utolsó bejegyzés törlése?") },
        text = {
            val last = game.rounds.lastOrNull()
            Text(
                (if (last?.isAdjustment == true) "Az utolsó pontkorrekció" else "A(z) ${game.playedRounds.size}. kör") +
                    " törlődik (visszavonással visszahozható).",
            )
        },
        confirmButton = {
            TextButton(onClick = { confirmRemove = false; session.update(GameEngine::removeLastRound) }) { Text("Törlés") }
        },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Mégse") } },
    )
}

@Composable
private fun FinishedPanel(
    game: Game,
    standing: hu.rmz.prefiscore.model.Standing,
    onScores: () -> Unit,
    onHome: () -> Unit,
    onShare: () -> Unit,
) {
    InfoCard {
        Text("Vége a játéknak!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "A fenti pontok összege elérte a 0-t. Ha tévedés történt, a visszavonás gombbal vagy az utolsó kör törlésével folytatható.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SectionTitle("Végeredmény")
    RankingList(game, standing)
    Spacer(Modifier.height(16.dp))
    Button(onClick = onShare, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Eredmény megosztása (HTML)") }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onScores, Modifier.weight(1f).height(52.dp)) { Text("Részletes pontok") }
        OutlinedButton(onClick = onHome, Modifier.weight(1f).height(52.dp)) { Text("Kezdőlap") }
    }
}

@Composable
internal fun BenchPanel(session: GameSession, onDone: () -> Unit) {
    val game = session.game
    var newName by remember { mutableStateOf("") }
    var after by remember { mutableStateOf(game.players.lastOrNull()?.id) }
    var startUpper by remember { mutableIntStateOf(game.startUpper) }
    val activeCount = Table.active(game).size
    val nameTaken = game.players.any { it.name.equals(newName.trim(), ignoreCase = true) }

    Text("Kispad", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    Text(
        "Aktív játékosok: $activeCount. Egyszerre 3 vagy 4 játékos lehet aktív. A módosítás törli a folyamatban lévő kör beírását (visszavonható).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    InfoCard {
        game.players.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (p.benched) "kispadon" else "játszik", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (p.benched) TextButton(
                    onClick = { session.update { g, now -> GameEngine.setBenched(g, p.id, false, now) } },
                    enabled = GameEngine.canActivate(game, p.id),
                ) { Text("Visszahív") }
                else TextButton(
                    onClick = { session.update { g, now -> GameEngine.setBenched(g, p.id, true, now) } },
                    enabled = GameEngine.canBench(game, p.id),
                ) { Text("Pihentet") }
            }
        }
    }
    if (activeCount == 3) {
        SectionTitle("Új játékos (3 → 4)")
        InfoCard {
            OutlinedTextField(
                value = newName, onValueChange = { newName = it.take(24) }, singleLine = true,
                label = { Text("Név") }, isError = nameTaken,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Ki után ül?", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                game.players.forEach { p ->
                    FilterChip(selected = after == p.id, onClick = { after = p.id }, label = { Text(p.name) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Kezdő pont fent:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { startUpper -= 1 }) { Text("−") }
                Text("$startUpper", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { startUpper = (startUpper + 1).coerceAtMost(0) }) { Text("+") }
            }
            Button(
                onClick = {
                    val n = newName
                    session.update { g, now -> GameEngine.addPlayer(g, n, startUpper, after, now) }
                    newName = ""
                },
                enabled = newName.isNotBlank() && !nameTaken,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Hozzáadás") }
        }
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Kész") }
}
