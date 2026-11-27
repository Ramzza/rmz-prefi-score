package hu.rmz.prefiscore

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import hu.rmz.prefiscore.data.Sharing
import hu.rmz.prefiscore.ui.AppState
import hu.rmz.prefiscore.ui.GameScreen
import hu.rmz.prefiscore.ui.HistoryScreen
import hu.rmz.prefiscore.ui.HomeScreen
import hu.rmz.prefiscore.ui.NewGameScreen
import hu.rmz.prefiscore.ui.PrefiTheme
import hu.rmz.prefiscore.ui.RulesScreen
import hu.rmz.prefiscore.ui.Screen
import hu.rmz.prefiscore.ui.SettingsScreen
import hu.rmz.prefiscore.ui.SheetImportScreen
import hu.rmz.prefiscore.ui.StatsScreen
import hu.rmz.prefiscore.ui.toast

class MainActivity : ComponentActivity() {
    private val app: AppState get() = (application as PrefiApp).state

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            PrefiTheme {
                Surface { AppRoot(app) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Más alkalmazásból (pl. WhatsApp) megnyitott / megosztott JSON fájl importálása. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(intent.streamUri())
            Intent.ACTION_SEND_MULTIPLE -> intent.streamUris()
            else -> emptyList()
        }
        if (uris.isEmpty()) return
        val report = app.importTexts(uris.map { Sharing.readUri(this, it) })
        toast(this, report.message)
        app.goHome()
        app.navigate(Screen.History)
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun Intent.streamUris(): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)).orEmpty()
}

@Composable
private fun AppRoot(app: AppState) {
    val ctx = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val report = app.importTexts(uris.map { Sharing.readUri(ctx, it) })
            toast(ctx, report.message)
            if (app.screen != Screen.History) app.navigate(Screen.History)
        }
    }
    val onImport = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*")) }

    BackHandler(enabled = app.stack.size > 1) { app.back() }

    when (app.screen) {
        Screen.Home -> HomeScreen(app, onImport)
        Screen.NewGame -> NewGameScreen(app)
        Screen.Game -> {
            val s = app.session
            if (s != null) GameScreen(app, s) else HomeScreen(app, onImport)
        }
        Screen.History -> HistoryScreen(app, onImport)
        Screen.Stats -> StatsScreen(app)
        Screen.Rules -> RulesScreen(app)
        Screen.Settings -> SettingsScreen(app)
        Screen.SheetImport -> {
            val f = app.sheetFlow
            if (f != null) SheetImportScreen(app, f) else HomeScreen(app, onImport)
        }
    }
}
