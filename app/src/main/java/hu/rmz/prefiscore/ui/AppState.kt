package hu.rmz.prefiscore.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import hu.rmz.prefiscore.data.GameRepository
import hu.rmz.prefiscore.model.Game
import hu.rmz.prefiscore.model.GameEngine
import hu.rmz.prefiscore.model.GameStatus
import hu.rmz.prefiscore.model.JsonCodec

sealed interface Screen {
    data object Home : Screen
    data object NewGame : Screen
    data object Game : Screen
    data object History : Screen
    data object Stats : Screen
    data object Rules : Screen
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

class AppState(private val repo: GameRepository) {
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
        stack = stack.dropLast(1)
        return true
    }

    fun goHome() {
        if (screen == Screen.Game) leaveGame()
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
