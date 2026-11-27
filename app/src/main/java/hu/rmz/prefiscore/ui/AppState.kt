package hu.rmz.prefiscore.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import hu.rmz.prefiscore.data.ClaudeSheetScanner
import hu.rmz.prefiscore.data.GameRepository
import hu.rmz.prefiscore.data.ScanException
import hu.rmz.prefiscore.data.Settings
import hu.rmz.prefiscore.data.SheetScanner
import hu.rmz.prefiscore.model.Game
import hu.rmz.prefiscore.model.GameEngine
import hu.rmz.prefiscore.model.GameStatus
import hu.rmz.prefiscore.model.JsonCodec
import hu.rmz.prefiscore.model.PaperConvention
import hu.rmz.prefiscore.model.Rules
import hu.rmz.prefiscore.model.SheetImport
import hu.rmz.prefiscore.model.SheetScan
import hu.rmz.prefiscore.model.SheetState
import hu.rmz.prefiscore.model.Table
import java.util.concurrent.Executors

sealed interface Screen {
    data object Home : Screen
    data object NewGame : Screen
    data object Game : Screen
    data object History : Screen
    data object Stats : Screen
    data object Rules : Screen
    data object SheetImport : Screen
    data object Settings : Screen
}

enum class SheetMode { NEW_GAME, UPDATE_FROM_IMAGE, MANUAL }

/** Kép alapú import / pontfrissítés / kézi javítás folyamatának állapota. */
class SheetFlow(val mode: SheetMode, val gameId: String?) {
    var image by mutableStateOf<ByteArray?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var scan by mutableStateOf<SheetScan?>(null)
    var state by mutableStateOf<SheetState?>(null)
    /** Frissítésnél: a lap i. játékosa melyik játékoshoz tartozik (null = kihagyva). */
    var mapping by mutableStateOf<List<String?>>(emptyList())
    var convention by mutableStateOf(PaperConvention.FROM_ZERO)
    var startUpper by mutableIntStateOf(Rules.DEFAULT_START_UPPER)
    /** Új játéknál a lap játékosának indexe, frissítésnél játékos azonosító. */
    var dealerIndex by mutableIntStateOf(0)
    var dealerId by mutableStateOf<String?>(null)

    val usesImage: Boolean get() = mode != SheetMode.MANUAL
}

/** Egy megnyitott játék szerkesztése visszavonás/újra támogatással. */
class GameSession(initial: Game, private val persist: (Game) -> Unit) {
    var game by mutableStateOf(initial)
        private set
    private val undoStack = ArrayDeque<Game>()
    private val redoStack = ArrayDeque<Game>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private fun set(g: Game) {
        game = g
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
        persist(g)
    }

    /** Visszavonható módosítás. */
    fun update(f: (Game, Long) -> Game) {
        val next = f(game, System.currentTimeMillis())
        if (next == game) return
        undoStack.addLast(game)
        if (undoStack.size > 300) undoStack.removeFirst()
        redoStack.clear()
        set(next)
    }

    /** Nem visszavonható (meta) módosítás, pl. szünet. */
    fun meta(f: (Game, Long) -> Game) {
        val next = f(game, System.currentTimeMillis())
        if (next != game) set(next)
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(game)
        set(prev.copy(updatedAt = System.currentTimeMillis(), sessions = game.sessions))
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(game)
        set(next.copy(updatedAt = System.currentTimeMillis(), sessions = game.sessions))
    }
}

data class ImportReport(val added: Int, val updated: Int, val skipped: Int, val failed: Int) {
    val message: String
        get() = buildList {
            add("$added új")
            if (updated > 0) add("$updated frissítve")
            if (skipped > 0) add("$skipped már megvolt")
            if (failed > 0) add("$failed hibás fájl")
        }.joinToString(", ", prefix = "Import: ")
}

class AppState(private val repo: GameRepository, val settings: Settings? = null) {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** Tesztekben lecserélhető: háttérmunka, főszálra visszatérés és a kép-felismerő. */
    var background: (() -> Unit) -> Unit = { executor.execute(it) }
    var onMain: (() -> Unit) -> Unit = { mainHandler.post(it) }
    var scannerFactory: (String) -> SheetScanner = { key -> ClaudeSheetScanner(key) }

    var apiKey by mutableStateOf(settings?.apiKey.orEmpty())
        private set

    fun saveApiKey(key: String) {
        settings?.apiKey = key
        apiKey = key.trim()
    }

    var sheetFlow by mutableStateOf<SheetFlow?>(null)
        private set

    val games = mutableStateListOf<Game>().apply { addAll(repo.loadAll()) }
    var stack by mutableStateOf(listOf<Screen>(Screen.Home))
        private set
    var session by mutableStateOf<GameSession?>(null)
        private set

    val screen: Screen get() = stack.last()

    fun navigate(s: Screen) {
        stack = stack + s
    }

    /** Vissza navigálás; false, ha már a kezdőképernyőn vagyunk. */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        if (screen == Screen.Game) leaveGame()
        if (screen == Screen.SheetImport) sheetFlow = null
        stack = stack.dropLast(1)
        return true
    }

    fun goHome() {
        if (screen == Screen.Game) leaveGame()
        sheetFlow = null
        stack = listOf(Screen.Home)
    }

    val sortedGames: List<Game> get() = games.sortedByDescending { it.updatedAt }

    private fun persist(g: Game) {
        val i = games.indexOfFirst { it.id == g.id }
        if (i >= 0) games[i] = g else games.add(g)
        repo.save(g)
    }

    fun startNewGame(names: List<String>, startUpper: Int) {
        val g = GameEngine.newGame(names, startUpper, System.currentTimeMillis())
        persist(g)
        session = GameSession(g, ::persist)
        stack = listOf(Screen.Home, Screen.Game)
    }

    fun openGame(id: String) {
        val g = games.firstOrNull { it.id == id } ?: return
        val resumed = GameEngine.resume(g, System.currentTimeMillis())
        persist(resumed)
        session = GameSession(resumed, ::persist)
        if (screen != Screen.Game) navigate(Screen.Game)
    }

    /** Kilépés a játék nézetből szüneteltetés nélkül: a játékidő mérése megáll, az állapot marad. */
    private fun leaveGame() {
        val s = session ?: return
        val wasActive = s.game.status == GameStatus.ACTIVE
        s.meta { g, now -> GameEngine.pause(g, now).let { if (wasActive) it.copy(status = GameStatus.ACTIVE) else it } }
        session = null
    }

    fun pauseCurrent() {
        session?.meta { g, now -> GameEngine.pause(g, now) }
        session = null
        stack = listOf(Screen.Home)
    }

    fun delete(ids: Set<String>) {
        games.removeAll { it.id in ids }
        ids.forEach(repo::delete)
    }

    fun exportJson(ids: Collection<String>): String {
        val selected = sortedGames.filter { it.id in ids }
        return JsonCodec.encodeBundle(selected, System.currentTimeMillis())
    }

    // ---- Kép alapú import / frissítés / kézi javítás ----

    fun startSheetFlow(mode: SheetMode) {
        val game = session?.game
        val flow = SheetFlow(mode, if (mode == SheetMode.NEW_GAME) null else game?.id)
        flow.convention = settings?.paperConvention ?: PaperConvention.FROM_ZERO
        if (mode != SheetMode.NEW_GAME) {
            game ?: return
            flow.dealerId = Table.currentDealer(game)
            flow.startUpper = game.startUpper
        }
        if (mode == SheetMode.MANUAL && game != null) {
            flow.convention = PaperConvention.ACTUAL
            flow.state = SheetImport.fromGame(game, PaperConvention.ACTUAL)
            flow.mapping = game.players.map { it.id }
        }
        sheetFlow = flow
        navigate(Screen.SheetImport)
    }

    fun setConvention(flow: SheetFlow, conv: PaperConvention) {
        flow.convention = conv
        if (flow.usesImage) settings?.paperConvention = conv
    }

    /** A kiválasztott kép betöltése a háttérben (kicsinyítés, tömörítés). */
    fun loadImage(flow: SheetFlow, loader: () -> ByteArray) {
        flow.error = null
        flow.loading = true
        background {
            val result = runCatching(loader)
            onMain {
                flow.loading = false
                result.onSuccess { flow.image = it }.onFailure { flow.error = "A kép nem tölthető be: ${it.message}" }
            }
        }
    }

    fun runScan(flow: SheetFlow) {
        val image = flow.image ?: return
        val key = apiKey
        if (key.isBlank()) {
            flow.error = "Előbb add meg a Claude API kulcsot a Beállításokban."
            return
        }
        flow.error = null
        flow.loading = true
        background {
            val result = runCatching { scannerFactory(key).scan(image) }
            onMain {
                flow.loading = false
                result.onSuccess { applyScan(flow, it) }.onFailure {
                    flow.error = (it as? ScanException)?.message ?: "Váratlan hiba: ${it.message ?: it::class.simpleName}"
                }
            }
        }
    }

    fun applyScan(flow: SheetFlow, scan: SheetScan) {
        val state = SheetImport.fromScan(scan)
        flow.scan = scan
        flow.state = state
        flow.dealerIndex = state.size - 1
        val game = session?.game?.takeIf { it.id == flow.gameId }
        if (flow.mode == SheetMode.UPDATE_FROM_IMAGE && game != null) flow.mapping = SheetImport.autoMapping(state, game)
    }

    fun sheetError(flow: SheetFlow): String? {
        val state = flow.state ?: return "Nincs beolvasott lap."
        return SheetImport.validationError(state, if (flow.mode == SheetMode.NEW_GAME) null else flow.mapping)
            ?: if (flow.mode != SheetMode.NEW_GAME && flow.dealerId == null) "Válaszd ki a következő osztót." else null
    }

    /** A folyamat eredményének előnézete (a még nem mentett játékállás). */
    fun sheetPreview(flow: SheetFlow): Game? {
        val state = flow.state ?: return null
        if (sheetError(flow) != null) return null
        val now = System.currentTimeMillis()
        val source = if (flow.usesImage) "image" else "manual"
        return if (flow.mode == SheetMode.NEW_GAME) {
            SheetImport.createGame(state, flow.startUpper, flow.convention, flow.dealerIndex, source, now)
        } else {
            val game = session?.game?.takeIf { it.id == flow.gameId } ?: return null
            SheetImport.updateGame(game, state, flow.mapping, flow.convention, flow.dealerId!!, source, now)
        }
    }

    fun applySheetFlow(flow: SheetFlow) {
        val preview = sheetPreview(flow) ?: return
        if (flow.mode == SheetMode.NEW_GAME) {
            val g = preview.copy(sessions = listOf(hu.rmz.prefiscore.model.PlaySession(preview.createdAt)))
            persist(g)
            session = GameSession(g, ::persist)
            sheetFlow = null
            stack = listOf(Screen.Home, Screen.Game)
        } else {
            val s = session ?: return
            val state = flow.state!!
            s.update { g, now ->
                SheetImport.updateGame(g, state, flow.mapping, flow.convention, flow.dealerId!!, if (flow.usesImage) "image" else "manual", now)
            }
            back()
        }
    }

    fun importTexts(texts: List<String?>): ImportReport {
        var added = 0
        var updated = 0
        var skipped = 0
        var failed = 0
        val now = System.currentTimeMillis()
        for (t in texts) {
            val parsed = t?.let { runCatching { JsonCodec.decodeAny(it) }.getOrNull() }
            if (parsed == null) {
                failed++
                continue
            }
            for (g in parsed) {
                val incoming = g.copy(imported = true, importedAt = now)
                val local = games.firstOrNull { it.id == g.id }
                when {
                    local == null -> { persist(incoming); added++ }
                    g.updatedAt > local.updatedAt && session?.game?.id != g.id -> { persist(incoming); updated++ }
                    else -> skipped++
                }
            }
        }
        return ImportReport(added, updated, skipped, failed)
    }
}
