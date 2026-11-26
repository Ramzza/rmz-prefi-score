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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import hu.rmz.prefiscore.model.Format
import hu.rmz.prefiscore.model.Game
import hu.rmz.prefiscore.model.GameStatus
import hu.rmz.prefiscore.model.Rules
import hu.rmz.prefiscore.model.Scoring

@Composable
fun HomeScreen(app: AppState, onImport: () -> Unit) {
    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Cards, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Prefi pontozó", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Licit, kontra, druzs, pince — a pontozást a gép vezeti.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = { app.navigate(Screen.NewGame) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Új játék", style = MaterialTheme.typography.titleMedium)
            }

            val open = app.sortedGames.filter { it.status != GameStatus.FINISHED }
            if (open.isNotEmpty()) {
                SectionTitle("Folytatható játékok")
                open.take(4).forEach { g ->
                    GameSummaryCard(g, onClick = { app.openGame(g.id) })
                    Spacer(Modifier.height(8.dp))
                }
            }

            SectionTitle("Menü")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { app.navigate(Screen.History) }, Modifier.weight(1f).height(52.dp)) { Text("Előzmények") }
                FilledTonalButton(onClick = { app.navigate(Screen.Stats) }, Modifier.weight(1f).height(52.dp)) { Text("Statisztika") }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { app.navigate(Screen.Rules) }, Modifier.weight(1f).height(52.dp)) { Text("Szabályok") }
                FilledTonalButton(onClick = onImport, Modifier.weight(1f).height(52.dp)) { Text("Importálás") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun GameSummaryCard(
    g: Game,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val st = remember(g) { Scoring.standings(g) }
    ChoiceButton(
        title = g.players.joinToString(" · ") { it.name },
        subtitle = buildString {
            append(Format.statusText(g.status))
            append(" · ${g.rounds.size} kör · ")
            if (g.status == GameStatus.FINISHED) {
                val winner = g.players.maxByOrNull { st.final(it.id) }
                append("győztes: ${winner?.name}")
            } else append("hátra: ${st.remaining}")
            append("\n").append(Format.dateTime(g.updatedAt))
            if (g.imported) append(" · importált")
        },
        selected = selected,
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    )
}

@Composable
fun NewGameScreen(app: AppState) {
    val names = remember { mutableStateListOf("", "", "") }
    var start by rememberSaveable { mutableIntStateOf(Rules.DEFAULT_START_UPPER) }
    val known = remember(app.games.size) {
        app.games.flatMap { g -> g.players.map { it.name } }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }.take(12)
    }
    val trimmed = names.map { it.trim() }
    val duplicate = trimmed.filter { it.isNotEmpty() }.groupBy { it.lowercase() }.any { it.value.size > 1 }
    val valid = trimmed.all { it.isNotEmpty() } && !duplicate && names.size in 3..4

    Scaffold(topBar = { AppTopBar("Új játék", onBack = { app.back() }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Text(
                "Add meg a játékosokat ülésrendben (balra haladva). Az első játékos kezd, az utolsó oszt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionTitle("Játékosok")
            names.forEachIndexed { i, n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = n,
                        onValueChange = { names[i] = it.take(24) },
                        label = { Text(if (i == names.lastIndex) "${i + 1}. játékos (oszt)" else if (i == 0) "1. játékos (kezd)" else "${i + 1}. játékos") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { if (i > 0) names.add(i - 1, names.removeAt(i)) }, enabled = i > 0) {
                        Icon(AppIcons.Up, "Feljebb")
                    }
                    IconButton(onClick = { if (i < names.lastIndex) names.add(i + 1, names.removeAt(i)) }, enabled = i < names.lastIndex) {
                        Icon(AppIcons.Down, "Lejjebb")
                    }
                    if (names.size > 3) IconButton(onClick = { names.removeAt(i) }) { Icon(AppIcons.Close, "Törlés") }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (names.size < 4) {
                TextButton(onClick = { names.add("") }) { Text("+ 4. játékos hozzáadása") }
            }
            if (duplicate) Text("Két játékosnak nem lehet azonos neve.", color = MaterialTheme.colorScheme.error)

            val unused = known.filter { k -> trimmed.none { it.equals(k, ignoreCase = true) } }
            if (unused.isNotEmpty()) {
                Text("Korábbi játékosok:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    unused.forEach { k ->
                        AssistChip(onClick = {
                            val idx = names.indexOfFirst { it.isBlank() }
                            if (idx >= 0) names[idx] = k else if (names.size < 4) names.add(k)
                        }, label = { Text(k) })
                    }
                }
            }

            SectionTitle("Kezdő pont fent")
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { start = (start - 5).coerceAtLeast(-500) }) { Text("−5") }
                Text(
                    "$start", style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 20.dp), fontWeight = FontWeight.SemiBold,
                )
                FilledTonalIconButton(onClick = { start = (start + 5).coerceAtMost(-5) }) { Text("+5") }
                Spacer(Modifier.width(12.dp))
                FilledTonalIconButton(onClick = { start = (start - 1).coerceAtLeast(-500) }) { Text("−1") }
                FilledTonalIconButton(onClick = { start = (start + 1).coerceAtMost(-1) }) { Text("+1") }
            }
            Text(
                "Általában mindenki −20-ról indul. A játék akkor ér véget, ha a fenti pontok összege 0.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { app.startNewGame(trimmed, start) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Játék indítása", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ConfirmRow(onCancel: () -> Unit, onConfirm: () -> Unit, confirmText: String, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onCancel, Modifier.weight(1f).height(52.dp)) { Text("Vissza") }
        Button(onClick = onConfirm, enabled = enabled, modifier = Modifier.weight(2f).height(52.dp)) { Text(confirmText) }
    }
}

@Composable
fun Divider() = HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
