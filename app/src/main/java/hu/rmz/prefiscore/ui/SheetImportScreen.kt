@file:OptIn(ExperimentalLayoutApi::class)

package hu.rmz.prefiscore.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import hu.rmz.prefiscore.data.ImageLoader
import hu.rmz.prefiscore.model.Format
import hu.rmz.prefiscore.model.JsonCodec
import hu.rmz.prefiscore.model.PaperConvention
import hu.rmz.prefiscore.model.Scoring
import hu.rmz.prefiscore.model.SheetImport
import hu.rmz.prefiscore.model.SheetScan
import java.io.File

@Composable
fun SheetImportScreen(app: AppState, flow: SheetFlow) {
    val ctx = LocalContext.current
    val game = app.session?.game?.takeIf { it.id == flow.gameId }
    val title = when (flow.mode) {
        SheetMode.NEW_GAME -> "Új játék képről"
        SheetMode.UPDATE_FROM_IMAGE -> "Pontok frissítése képről"
        SheetMode.MANUAL -> "Pontok kézi javítása"
    }

    val cameraFile = remember { File(ctx.cacheDir, "camera/scoresheet.jpg") }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) app.loadImage(flow) { ImageLoader.loadJpeg(ctx, Uri.fromFile(cameraFile)) }
    }
    fun launchCamera() {
        cameraFile.parentFile?.mkdirs()
        takePicture.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", cameraFile))
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) app.loadImage(flow) { ImageLoader.loadJpeg(ctx, uri) }
    }

    Scaffold(topBar = { AppTopBar(title, subtitle = game?.title, onBack = { app.back() }) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            if (flow.usesImage) {
                Text(
                    "Fotózd le a papíron vezetett pontozást. A képet a Claude AI (Anthropic API) olvassa ki JSON formátumba, amit mentés előtt ellenőrizhetsz és javíthatsz.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (app.apiKey.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                    InfoCard {
                        Text("Claude API kulcs szükséges", style = MaterialTheme.typography.titleMedium)
                        Text("A kép felismeréséhez add meg az Anthropic API kulcsodat.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { app.navigate(Screen.Settings) }) { Text("Beállítások megnyitása") }
                    }
                }
                SectionTitle("1. Kép")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { launchCamera() }, Modifier.weight(1f).height(52.dp), enabled = !flow.loading) {
                        Text("Fotó készítése")
                    }
                    FilledTonalButton(
                        onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        Modifier.weight(1f).height(52.dp), enabled = !flow.loading,
                    ) { Text("Galéria") }
                }
                flow.image?.let { bytes ->
                    val thumb = remember(bytes) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
                    }
                    if (thumb != null) {
                        Spacer(Modifier.height(8.dp))
                        Image(
                            thumb, contentDescription = "Kiválasztott kép",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { app.runScan(flow) },
                        enabled = !flow.loading && app.apiKey.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(if (flow.state == null) "Kép feldolgozása" else "Újrafeldolgozás") }
                }
                if (flow.loading) {
                    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (flow.image == null) "Kép betöltése…" else "A lap felismerése folyamatban (20–90 másodperc)…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            flow.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            val state = flow.state
            if (state != null) SheetReview(app, flow, ctx)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SheetReview(app: AppState, flow: SheetFlow, ctx: android.content.Context) {
    val state = flow.state ?: return
    val game = app.session?.game?.takeIf { it.id == flow.gameId }
    val scan = flow.scan

    SectionTitle(if (flow.usesImage) "2. Ellenőrzés" else "Aktuális állás")
    if (scan != null) {
        InfoCard {
            StatRow("Olvashatóság", when (scan.confidence) { "high" -> "jó"; "low" -> "gyenge"; else -> "közepes" })
            if (scan.notes.isNotBlank()) Text(scan.notes, style = MaterialTheme.typography.bodySmall)
            state.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            TextButton(onClick = {
                shareJson(ctx, "prefi_lap_${Format.fileStamp(System.currentTimeMillis())}.json", JsonCodec.json.encodeToString(SheetScan.serializer(), scan), whatsapp = false)
            }) { Text("Felismert JSON megosztása") }
        }
        Spacer(Modifier.height(8.dp))
    }
    Text(
        if (flow.usesImage) "Ellenőrizd és szükség esetén javítsd a felismert értékeket. Pincénél az utolsó (aktuális) értéket add meg."
        else "Írd át a hibás értékeket. A különbség korrekcióként kerül a játékba, és visszavonható.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (flow.usesImage) {
        Text("Fenti pont a papíron", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PaperConvention.entries.forEach { c ->
                FilterChip(selected = flow.convention == c, onClick = { app.setConvention(flow, c) }, label = { Text(c.label) })
            }
        }
        Text(flow.convention.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (flow.mode == SheetMode.NEW_GAME) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Kezdő pont fent:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { flow.startUpper -= 1 }) { Text("−") }
            Text("${flow.startUpper}", fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { flow.startUpper = (flow.startUpper + 1).coerceAtMost(-1) }) { Text("+") }
        }
    }

    for (i in 0 until state.size) {
        Spacer(Modifier.height(8.dp))
        InfoCard {
            when (flow.mode) {
                SheetMode.NEW_GAME -> OutlinedTextField(
                    value = state.names[i], onValueChange = { flow.state = state.withName(i, it.take(24)) },
                    label = { Text("${i + 1}. játékos neve") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                SheetMode.UPDATE_FROM_IMAGE -> {
                    Text("A lapon: ${state.names[i]}", style = MaterialTheme.typography.titleMedium)
                    Text("Melyik játékos?", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        game?.players?.forEach { p ->
                            FilterChip(
                                selected = flow.mapping.getOrNull(i) == p.id,
                                onClick = { flow.mapping = flow.mapping.toMutableList().also { it[i] = p.id } },
                                label = { Text(p.name) },
                            )
                        }
                        FilterChip(
                            selected = flow.mapping.getOrNull(i) == null,
                            onClick = { flow.mapping = flow.mapping.toMutableList().also { it[i] = null } },
                            label = { Text("Kihagy") },
                        )
                    }
                }
                SheetMode.MANUAL -> Text(state.names[i], style = MaterialTheme.typography.titleMedium)
            }
            val startUpper = when (flow.mode) {
                SheetMode.NEW_GAME -> flow.startUpper
                else -> game?.player(flow.mapping.getOrNull(i))?.startUpper ?: flow.startUpper
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IntField(
                    label = if (flow.convention == PaperConvention.ACTUAL) "Fent" else "Fent (papíron)",
                    value = state.paperUpper[i],
                    onChange = { flow.state = state.withUpper(i, it) },
                    modifier = Modifier.weight(1f),
                )
                if (flow.convention != PaperConvention.ACTUAL) {
                    Text(
                        "→ ${SheetImport.upperFromPaper(state.paperUpper[i], startUpper, flow.convention)}",
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            state.upperHistory.getOrNull(i)?.takeIf { it.size > 1 }?.let {
                Text("Előzmény: ${it.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Pince", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            for (j in 0 until state.size) {
                if (j == i) continue
                IntField(
                    label = "${state.names[j]} szobája",
                    value = state.rooms[i][j],
                    onChange = { flow.state = state.withRoom(i, j, it) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }

    SectionTitle("Druzs")
    InfoCard {
        Stepper("Felírt (összes)", state.druzsWritten) { flow.state = state.copy(druzsWritten = it.coerceAtLeast(0)) }
        Stepper("Ebből áthúzott", state.druzsCrossed) { flow.state = state.copy(druzsCrossed = it.coerceIn(0, state.druzsWritten)) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Text("Aktív: ", style = MaterialTheme.typography.bodyMedium)
            DruzsMarks(state.druzsWritten, state.druzsCrossed)
        }
    }

    SectionTitle("Következő osztó")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (flow.mode == SheetMode.NEW_GAME) {
            state.names.forEachIndexed { i, n ->
                FilterChip(selected = flow.dealerIndex == i, onClick = { flow.dealerIndex = i }, label = { Text(n.ifBlank { "${i + 1}." }) })
            }
        } else {
            game?.players?.filter { !it.benched }?.forEach { p ->
                FilterChip(selected = flow.dealerId == p.id, onClick = { flow.dealerId = p.id }, label = { Text(p.name) })
            }
        }
    }

    val error = app.sheetError(flow)
    val preview = remember(state, flow.mapping, flow.convention, flow.startUpper, flow.dealerIndex, flow.dealerId, game) { app.sheetPreview(flow) }
    if (preview != null) {
        SectionTitle("Eredmény előnézet")
        val st = Scoring.standings(preview)
        InfoCard {
            preview.players.sortedByDescending { st.final(it.id) }.forEach { p ->
                val f = st.final(p.id)
                StatRow("${p.name} (fent ${st.upperOf(p.id)})", Format.signed(f), ScoreColors.of(f))
            }
            Divider()
            StatRow("Hátra fent", "${st.remaining}")
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { app.applySheetFlow(flow) },
        enabled = error == null && preview != null,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Text(
            when (flow.mode) {
                SheetMode.NEW_GAME -> "Játék létrehozása"
                SheetMode.UPDATE_FROM_IMAGE -> "Pontok frissítése"
                SheetMode.MANUAL -> "Javítás mentése"
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }
    OutlinedButton(onClick = { app.back() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Mégse") }
}

/** Egész szám bevitele (előjelváltó gombbal, mert a numerikus billentyűzeten gyakran nincs mínusz). */
@Composable
fun IntField(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val cleaned = raw.filterIndexed { idx, c -> c.isDigit() || (c == '-' && idx == 0) }.take(6)
                text = cleaned
                cleaned.toIntOrNull()?.let(onChange)
            },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onChange(-value) }) { Text("±") }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange(value - 1) }) { Text("−") }
        Text("$value", fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { onChange(value + 1) }) { Text("+") }
    }
}
