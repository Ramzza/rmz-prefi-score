package hu.rmz.prefiscore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import hu.rmz.prefiscore.data.ClaudeSheetScanner

@Composable
fun SettingsScreen(app: AppState) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf(app.apiKey) }
    var visible by remember { mutableStateOf(false) }
    Scaffold(topBar = { AppTopBar("Beállítások", onBack = { app.back() }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle("Kép alapú import")
            InfoCard {
                Text(
                    "A papíron vezetett pontozás fotójának felismeréséhez a Claude AI-t (Anthropic API, ${ClaudeSheetScanner.MODEL} modell) használja az app. " +
                        "Ehhez saját API kulcs kell (console.anthropic.com). Felismeréskor a kép az Anthropic szervereire kerül; a használat díjköteles, egy fotó általában néhány cent.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    label = { Text("Anthropic API kulcs") },
                    placeholder = { Text("sk-ant-…") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { visible = !visible }) { Text(if (visible) "Elrejtés" else "Megjelenítés") }
                Text(
                    "A kulcsot csak ez az alkalmazás tárolja a telefonon, a saját privát tárhelyén.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { key = ""; app.saveApiKey(""); toast(ctx, "API kulcs törölve") }, Modifier.weight(1f)) { Text("Törlés") }
                    Button(onClick = { app.saveApiKey(key); toast(ctx, "API kulcs elmentve") }, Modifier.weight(1f), enabled = key.isNotBlank()) { Text("Mentés") }
                }
            }
        }
    }
}
