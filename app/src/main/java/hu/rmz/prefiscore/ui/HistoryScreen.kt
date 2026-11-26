@file:OptIn(ExperimentalLayoutApi::class)

package hu.rmz.prefiscore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import hu.rmz.prefiscore.model.Format
import hu.rmz.prefiscore.model.GameStatus

private enum class HistoryFilter(val label: String) { ALL("Mind"), OPEN("Folyamatban"), PAUSED("Szünetel"), FINISHED("Befejezett"), IMPORTED("Importált") }

@Composable
fun HistoryScreen(app: AppState, onImport: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val saveJson = rememberJsonSaver()
    val all = app.sortedGames
    val games = all.filter {
        when (filter) {
            HistoryFilter.ALL -> true
            HistoryFilter.OPEN -> it.status == GameStatus.ACTIVE
            HistoryFilter.PAUSED -> it.status == GameStatus.PAUSED
            HistoryFilter.FINISHED -> it.status == GameStatus.FINISHED
            HistoryFilter.IMPORTED -> it.imported
        }
    }
    val now = System.currentTimeMillis()
    fun bundleName(n: Int) = "prefi_export_${Format.fileStamp(now)}_${n}jatek.json"

    Scaffold(
        topBar = {
            AppTopBar("Előzmények", subtitle = "${all.size} játék", onBack = { app.back() }) {
                TextButton(onClick = onImport) { Text("Import") }
                IconButton(onClick = { menu = true }) { Icon(AppIcons.More, "Menü") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Összes kijelölése") }, onClick = { menu = false; selected = games.map { it.id }.toSet() })
                    DropdownMenuItem(text = { Text("Összes mentése fájlba") }, enabled = all.isNotEmpty(), onClick = {
                        menu = false; saveJson(bundleName(all.size), app.exportJson(all.map { it.id }))
                    })
                    DropdownMenuItem(text = { Text("Összes küldése WhatsAppon") }, enabled = all.isNotEmpty(), onClick = {
                        menu = false; shareJson(ctx, bundleName(all.size), app.exportJson(all.map { it.id }), whatsapp = true)
                    })
                }
            }
        },
        bottomBar = {
            if (selected.isNotEmpty()) BottomAppBar {
                Text("${selected.size} kijelölve", Modifier.padding(start = 16.dp).weight(1f))
                TextButton(onClick = { saveJson(bundleName(selected.size), app.exportJson(selected)) }) { Text("Mentés") }
                TextButton(onClick = { shareJson(ctx, bundleName(selected.size), app.exportJson(selected), whatsapp = true) }) { Text("WhatsApp") }
                TextButton(onClick = { shareJson(ctx, bundleName(selected.size), app.exportJson(selected), whatsapp = false) }) { Text("Küldés") }
                TextButton(onClick = { confirmDelete = true }) { Text("Törlés", color = MaterialTheme.colorScheme.error) }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HistoryFilter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
            if (games.isEmpty()) EmptyState("Nincs megjeleníthető játék.\nIndíts újat, vagy importálj JSON fájlt.")
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                items(games, key = { it.id }) { g ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Checkbox(checked = g.id in selected, onCheckedChange = { c -> selected = if (c) selected + g.id else selected - g.id })
                        GameSummaryCard(g, Modifier.weight(1f), selected = g.id in selected) {
                            if (selected.isNotEmpty()) selected = if (g.id in selected) selected - g.id else selected + g.id
                            else app.openGame(g.id)
                        }
                    }
                }
                item { Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {} }
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Törlés") },
        text = { Text("${selected.size} játék végleg törlődik. Biztos?") },
        confirmButton = {
            TextButton(onClick = { app.delete(selected); selected = emptySet(); confirmDelete = false }) { Text("Törlés") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Mégse") } },
    )
}
