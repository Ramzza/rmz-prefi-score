package hu.rmz.prefiscore.model

import kotlinx.serialization.Serializable
import java.text.Normalizer

// ---- A kép → JSON átalakító kimenete (ez a JSON séma, amit a modell kitölt) ----

@Serializable
data class ScannedRoom(
    /** Annak a játékosnak a neve, akinek a szobája (ahogy a lapon szerepel). */
    val target: String,
    /** A szobába írt halmozott értékek időrendben; az utolsó az aktuális. */
    val values: List<Int>,
)

@Serializable
data class ScannedPlayer(
    val name: String,
    /** A "fenti" sor értékei időrendben; az utolsó az aktuális. */
    val upperValues: List<Int>,
    val rooms: List<ScannedRoom>,
)

@Serializable
data class SheetScan(
    val players: List<ScannedPlayer>,
    /** Felírt druzs karikák száma összesen (áthúzottakkal együtt). */
    val druzsTotal: Int,
    /** Áthúzott druzs karikák száma. */
    val druzsCrossed: Int,
    /** "high", "medium" vagy "low". */
    val confidence: String = "medium",
    /** Magyar nyelvű megjegyzés a bizonytalan részekről. */
    val notes: String = "",
)

/** Hogyan vezették a fenti pontot a papíron. */
enum class PaperConvention(val label: String, val description: String) {
    FROM_ZERO("0-ról indul, a bukás növeli", "fent = kezdő pont − papíron levő érték"),
    ACTUAL("A tényleges fenti pont", "fent = papíron levő érték"),
}

/** Szerkeszthető állapot egy lap (vagy egy meglévő játék) alapján. Az indexek a lap játékosaira vonatkoznak. */
data class SheetState(
    val names: List<String>,
    /** A papíron levő aktuális fenti érték (a konvenció szerint értelmezve). */
    val paperUpper: List<Int>,
    /** rooms[i][j]: i pincéjében j szobája. */
    val rooms: List<List<Int>>,
    val druzsWritten: Int,
    val druzsCrossed: Int,
    val upperHistory: List<List<Int>> = names.map { emptyList() },
    val warnings: List<String> = emptyList(),
) {
    val size: Int get() = names.size

    fun withName(i: Int, v: String) = copy(names = names.toMutableList().also { it[i] = v })
    fun withUpper(i: Int, v: Int) = copy(paperUpper = paperUpper.toMutableList().also { it[i] = v })
    fun withRoom(i: Int, j: Int, v: Int) = copy(rooms = rooms.mapIndexed { a, row -> if (a == i) row.toMutableList().also { it[j] = v } else row })
}

object SheetImport {
    fun normalize(name: String): String =
        Normalizer.normalize(name.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]"), "")

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    /** A [name]-hez legjobban illő jelölt indexe (pontos, előtag vagy kis elírás egyezés), vagy null. */
    fun bestMatch(name: String, candidates: List<String>): Int? {
        val n = normalize(name)
        if (n.isEmpty()) return null
        val norm = candidates.map(::normalize)
        norm.indexOfFirst { it == n }.takeIf { it >= 0 }?.let { return it }
        norm.indexOfFirst { it.isNotEmpty() && (it.startsWith(n) || n.startsWith(it)) }.takeIf { it >= 0 }?.let { return it }
        val scored = norm.mapIndexed { i, c -> i to levenshtein(n, c) }.filter { (_, d) -> d <= maxOf(1, n.length / 3) }
        return scored.minByOrNull { it.second }?.first
    }

    fun fromScan(scan: SheetScan): SheetState {
        val names = scan.players.map { it.name.trim() }
        val warnings = mutableListOf<String>()
        val rooms = scan.players.mapIndexed { i, p ->
            val row = MutableList(names.size) { 0 }
            for (room in p.rooms) {
                val others = names.indices.filter { it != i }
                val m = bestMatch(room.target, others.map { names[it] })?.let { others[it] }
                if (m == null) {
                    if (room.values.isNotEmpty()) warnings += "${p.name} pincéjében ismeretlen szoba: \"${room.target}\""
                } else row[m] = room.values.lastOrNull() ?: 0
            }
            row
        }
        return SheetState(
            names = names,
            paperUpper = scan.players.map { it.upperValues.lastOrNull() ?: 0 },
            rooms = rooms,
            druzsWritten = scan.druzsTotal.coerceAtLeast(0),
            druzsCrossed = scan.druzsCrossed.coerceIn(0, scan.druzsTotal.coerceAtLeast(0)),
            upperHistory = scan.players.map { it.upperValues },
            warnings = warnings,
        )
    }

    fun upperFromPaper(paper: Int, startUpper: Int, conv: PaperConvention): Int =
        if (conv == PaperConvention.FROM_ZERO) startUpper - paper else paper

    fun paperFromUpper(upper: Int, startUpper: Int, conv: PaperConvention): Int =
        if (conv == PaperConvention.FROM_ZERO) startUpper - upper else upper

    /** Egy meglévő játék aktuális állása lapként (kézi javításhoz). */
    fun fromGame(game: Game, conv: PaperConvention = PaperConvention.ACTUAL): SheetState {
        val st = Scoring.standings(game)
        val ids = game.players.map { it.id }
        return SheetState(
            names = game.players.map { it.name },
            paperUpper = game.players.map { paperFromUpper(st.upperOf(it.id), it.startUpper, conv) },
            rooms = ids.map { o -> ids.map { t -> if (o == t) 0 else st.room(o, t) } },
            druzsWritten = st.druzsWritten,
            druzsCrossed = st.druzsCrossed,
            upperHistory = ids.map { st.upperHistory[it].orEmpty() },
        )
    }

    /** A lap játékosainak automatikus párosítása a játék játékosaihoz (név alapján). */
    fun autoMapping(state: SheetState, game: Game): List<String?> {
        val used = mutableSetOf<String>()
        return state.names.map { n ->
            val idx = bestMatch(n, game.players.map { it.name })
            val id = idx?.let { game.players[it].id }
            if (id != null && used.add(id)) id else null
        }
    }

    fun validationError(state: SheetState, mapping: List<String?>? = null): String? {
        if (state.size !in 3..6) return "A lapon 3–6 játékosnak kell szerepelnie (most: ${state.size})."
        if (mapping == null) {
            val n = state.names.map { it.trim() }
            if (n.any { it.isEmpty() }) return "Minden játékosnak adj nevet."
            if (n.map { it.lowercase() }.toSet().size != n.size) return "Két játékosnak nem lehet azonos neve."
            if (n.size > 4) return "Új játék legfeljebb 4 játékossal indítható."
        } else {
            val mapped = mapping.filterNotNull()
            if (mapped.isEmpty()) return "Legalább egy játékost párosíts."
            if (mapped.toSet().size != mapped.size) return "Egy játékost csak egyszer lehet párosítani."
        }
        if (state.druzsCrossed > state.druzsWritten) return "Több áthúzott druzs nem lehet, mint felírt."
        return null
    }

    /** Új játék létrehozása a lap állása alapján (egy korrekcióval). */
    fun createGame(
        state: SheetState,
        startUpper: Int,
        conv: PaperConvention,
        nextDealerIndex: Int,
        source: String,
        now: Long,
    ): Game {
        val base = GameEngine.newGame(state.names, startUpper, now)
        val ids = base.players.map { it.id }
        val adj = Adjustment(
            upper = ids.indices.associate { i -> ids[i] to upperFromPaper(state.paperUpper[i], startUpper, conv) - startUpper },
            pince = ids.indices.flatMap { i -> ids.indices.filter { it != i }.map { j -> PinceDelta(ids[i], ids[j], state.rooms[i][j]) } },
            druzsWritten = state.druzsWritten,
            druzsCrossed = state.druzsCrossed,
            source = source,
            note = "kezdőállás",
        )
        val dealer = ids[nextDealerIndex.coerceIn(0, ids.lastIndex)]
        // A kezdőállás-korrekció mindig bekerül (akkor is, ha minden érték 0), hogy az osztó rögzüljön.
        val round = Round(
            number = 0, kind = RoundKind.ADJUSTMENT, dealerId = dealer, playingIds = ids,
            startedAt = now, endedAt = now,
            adjustment = adj.copy(upper = adj.upper.filterValues { it != 0 }, pince = adj.pince.filter { it.amount != 0 }),
        )
        return GameEngine.updateStatus(base.copy(rounds = listOf(round), title = base.title), now)
    }

    /**
     * Meglévő játék pontjainak frissítése/kiegészítése: a párosított játékosok fenti pontja,
     * pince-szobái és a druzsok a lap szerinti értékre állnak (különbség-korrekcióval).
     */
    fun updateGame(
        game: Game,
        state: SheetState,
        mapping: List<String?>,
        conv: PaperConvention,
        nextDealerId: String,
        source: String,
        now: Long,
    ): Game {
        val st = Scoring.standings(game)
        val upper = mutableMapOf<String, Int>()
        val pince = mutableListOf<PinceDelta>()
        for (i in mapping.indices) {
            val id = mapping[i] ?: continue
            val p = game.player(id) ?: continue
            upper[id] = upperFromPaper(state.paperUpper[i], p.startUpper, conv) - st.upperOf(id)
            for (j in mapping.indices) {
                val t = mapping[j] ?: continue
                if (i == j || t == id) continue
                pince += PinceDelta(id, t, state.rooms[i][j] - st.room(id, t))
            }
        }
        val adj = Adjustment(
            upper = upper,
            pince = pince,
            druzsWritten = state.druzsWritten - st.druzsWritten,
            druzsCrossed = state.druzsCrossed - st.druzsCrossed,
            source = source,
        )
        return GameEngine.addAdjustment(game, adj, nextDealerId, now)
    }
}
