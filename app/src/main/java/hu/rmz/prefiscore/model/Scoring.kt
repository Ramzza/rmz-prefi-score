package hu.rmz.prefiscore.model

import kotlin.math.max

data class PinceEntry(val ownerId: String, val targetId: String, val amount: Int)

data class RoundResult(
    val round: Round,
    /** A játék végső értéke (szorzókkal együtt); körpassznál 0. */
    val value: Int,
    val druzsActive: Boolean,
    val upper: Map<String, Int>,
    val pince: List<PinceEntry>,
    /** Ellenjátékosok, akik -1-et kaptak fent, mert nem vittek legalább 2-t. */
    val penalized: List<String>,
    val druzsCrossed: Int,
    val druzsAdded: Int,
)

data class Standing(
    val playerIds: List<String>,
    val upper: Map<String, Int>,
    /** tulajdonos -> (célszemély -> pont): a tulajdonos pincéjében a célszemély szobája. */
    val pince: Map<String, Map<String, Int>>,
    val druzsWritten: Int,
    val druzsCrossed: Int,
    val results: List<RoundResult>,
    /** A fenti pontok alakulása játékosonként (kezdőérték + minden változás után). */
    val upperHistory: Map<String, List<Int>>,
) {
    val druzsActive: Int get() = druzsWritten - druzsCrossed
    val upperSum: Int get() = upper.values.sum()

    /** Ennyi pontot kell még összesen "fent" csinálni a játék végéig. */
    val remaining: Int get() = max(0, -upperSum)

    fun upperOf(id: String): Int = upper[id] ?: 0
    fun room(ownerId: String, targetId: String): Int = pince[ownerId]?.get(targetId) ?: 0
    fun ownPince(id: String): Int = pince[id]?.values?.sum() ?: 0
    fun writtenOn(id: String): Int = pince.filterKeys { it != id }.values.sumOf { it[id] ?: 0 }

    /** Végső pont: fent×10 + saját pince − másoknál levő szobáim. */
    fun final(id: String): Int = upperOf(id) * 10 + ownPince(id) - writtenOn(id)
}

object Table {
    fun active(game: Game): List<Player> = game.players.filter { !it.benched }

    /** A következő (aktuális) kör osztója. */
    fun currentDealer(game: Game): String? {
        val active = active(game)
        if (active.isEmpty()) return null
        val last = game.rounds.lastOrNull() ?: return active.last().id
        val seats = game.players
        val idx = seats.indexOfFirst { it.id == last.dealerId }
        if (idx < 0) return active.last().id
        for (k in 1..seats.size) {
            val p = seats[(idx + k) % seats.size]
            if (!p.benched) return p.id
        }
        return active.last().id
    }

    /** Aktív játékosok az osztó utántól kezdve; az osztó a lista végén. */
    fun orderAfterDealer(game: Game, dealerId: String): List<String> {
        val seats = game.players
        val idx = seats.indexOfFirst { it.id == dealerId }
        if (idx < 0) return active(game).map { it.id }
        return (1..seats.size).map { seats[(idx + it) % seats.size] }.filter { !it.benched }.map { it.id }
    }

    /** Akik játszanak a körben: 4 aktív játékosnál az osztó pihen, kivéve körpassznál. */
    fun playing(game: Game, dealerId: String, korpassz: Boolean): List<String> {
        val order = orderAfterDealer(game, dealerId)
        return if (order.size >= 4 && !korpassz) order.filter { it != dealerId } else order
    }

    /** Ki kezd: tromfos játéknál és körpassznál a soron következő, aláhívásnál a nyertes előtti. */
    fun leader(playing: List<String>, game: Int?, declarerId: String?, korpassz: Boolean): String? {
        if (playing.isEmpty()) return null
        if (korpassz || game == null || declarerId == null) return playing.first()
        val type = Rules.type(game) ?: return playing.first()
        if (type.trump) return playing.first()
        val i = playing.indexOf(declarerId)
        if (i < 0) return playing.first()
        return playing[(i - 1 + playing.size) % playing.size]
    }

    /** Hány lapja van egy játékosnak körpassznál (4 játékosnál az osztó a 2 talonlappal játszik). */
    fun korpasszCards(playing: List<String>, dealerId: String, playerId: String): Int =
        if (playing.size >= 4 && playerId == dealerId) 2 else Rules.TOTAL_TRICKS
}

object Scoring {
    fun multiplierText(talon: Boolean, felkez: Boolean, contra: Int, druzs: Boolean): String {
        val parts = mutableListOf<String>()
        if (felkez) parts += "félkéz (+1, ×2)" else if (!talon) parts += "talon nélkül (+1)"
        if (contra > 0) parts += "${Rules.contraNames[contra]} (×${1 shl contra})"
        if (druzs) parts += "druzs (×2)"
        return parts.joinToString(", ")
    }

    fun value(game: Int, talon: Boolean, felkez: Boolean, contra: Int, druzs: Boolean): Int {
        var v = game
        if (!talon || felkez) v += 1
        if (felkez) v *= 2
        v *= 1 shl contra.coerceIn(0, Rules.maxContra)
        if (druzs) v *= 2
        return v
    }

    /** Körpassz: minden vitel -1 fent, 6 viteltől duplázva. */
    fun korpasszPenalty(tricks: Int): Int = if (tricks >= 6) -2 * tricks else -tricks

    fun evaluate(round: Round, druzsActive: Boolean): RoundResult {
        val upper = mutableMapOf<String, Int>()
        val pince = mutableListOf<PinceEntry>()
        val penalized = mutableListOf<String>()
        val crossed = if (druzsActive) 1 else 0
        val playingCount = round.playingIds.size

        if (round.kind == RoundKind.KORPASSZ) {
            for (id in round.playingIds) {
                val t = round.tricks[id] ?: 0
                val d = korpasszPenalty(t)
                if (d != 0) upper[id] = d
            }
            return RoundResult(round, 0, druzsActive, upper, pince, penalized, crossed, playingCount)
        }

        val game = round.game ?: 0
        val declarer = round.declarerId ?: return RoundResult(round, 0, druzsActive, upper, pince, penalized, crossed, 0)
        val made = round.made == true
        val v = value(game, round.talon, round.felkez, round.contra, druzsActive)
        upper[declarer] = if (made) v else -v
        val defenders = round.playingIds.filter { it != declarer }

        if (game in Rules.trickGames) {
            val total = defenders.sumOf { round.tricks[it] ?: 0 }
            for (d in defenders) {
                val t = round.tricks[d] ?: 0
                if (t > 0) pince += PinceEntry(d, declarer, t * v)
                if (t < 2 && total < 3) {
                    upper[d] = (upper[d] ?: 0) - 1
                    penalized += d
                }
            }
        } else if (game in Rules.penaltyGames && !made) {
            for (d in defenders) pince += PinceEntry(d, declarer, v * 10 / 2)
        }

        val added = if (druzsActive && !made) playingCount else 0
        return RoundResult(round, v, druzsActive, upper, pince, penalized, crossed, added)
    }

    fun standings(game: Game): Standing {
        val ids = game.players.map { it.id }
        val upper = game.players.associate { it.id to it.startUpper }.toMutableMap()
        val pince = mutableMapOf<String, MutableMap<String, Int>>()
        val history = game.players.associate { it.id to mutableListOf(it.startUpper) }
        var written = 0
        var crossed = 0
        val results = mutableListOf<RoundResult>()
        for (r in game.rounds) {
            val res = evaluate(r, written - crossed > 0)
            for ((id, d) in res.upper) {
                upper[id] = (upper[id] ?: 0) + d
                history[id]?.add(upper[id]!!)
            }
            for (e in res.pince) {
                val room = pince.getOrPut(e.ownerId) { mutableMapOf() }
                room[e.targetId] = (room[e.targetId] ?: 0) + e.amount
            }
            crossed += res.druzsCrossed
            written += res.druzsAdded
            results += res
        }
        return Standing(ids, upper, pince, written, crossed, results, history)
    }

    /** Előnézet a piszkozatból — ugyanaz a számítás, mint a rögzítéskor. */
    fun preview(game: Game, standing: Standing = standings(game)): RoundResult? {
        val round = GameEngine.draftToRound(game, 0L) ?: return null
        return evaluate(round, standing.druzsActive > 0)
    }
}
