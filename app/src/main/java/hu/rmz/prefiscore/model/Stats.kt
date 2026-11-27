package hu.rmz.prefiscore.model

data class TypeCount(var declared: Int = 0, var made: Int = 0)

data class PlayerStats(
    val name: String,
    var games: Int = 0,
    var finishedGames: Int = 0,
    var wins: Int = 0,
    var totalFinal: Int = 0,
    var roundsPlayed: Int = 0,
    var declared: Int = 0,
    var made: Int = 0,
    var defended: Int = 0,
    var defendedWon: Int = 0,
    var penalties: Int = 0,
    var korpassz: Int = 0,
    var korpasszTricks: Int = 0,
    var contraGames: Int = 0,
    var felkezGames: Int = 0,
    val byType: MutableMap<Int, TypeCount> = sortedMapOf(),
) {
    val successRate: Double get() = if (declared == 0) 0.0 else made.toDouble() / declared
    val declareRate: Double get() = if (roundsPlayed == 0) 0.0 else declared.toDouble() / roundsPlayed
    val winRate: Double get() = if (finishedGames == 0) 0.0 else wins.toDouble() / finishedGames
    val avgFinal: Double get() = if (finishedGames == 0) 0.0 else totalFinal.toDouble() / finishedGames
}

data class OverallStats(
    val games: Int,
    val finished: Int,
    val active: Int,
    val paused: Int,
    val rounds: Int,
    val korpassz: Int,
    val gameRounds: Int,
    val madeRounds: Int,
    val avgRoundMs: Long,
    val longestRoundMs: Long,
    val totalPlayMs: Long,
    val avgGameMs: Long,
    val avgRoundsPerFinishedGame: Double,
    val byType: Map<Int, TypeCount>,
    val players: List<PlayerStats>,
)

data class GameStats(
    val rounds: Int,
    val avgRoundMs: Long,
    val longestRoundMs: Long,
    val shortestRoundMs: Long,
    val playMs: Long,
    val korpassz: Int,
    val perPlayer: List<PlayerStats>,
)

object StatsCalculator {
    private fun key(name: String) = name.trim().lowercase()

    private fun accumulate(game: Game, map: MutableMap<String, PlayerStats>) {
        val standing = Scoring.standings(game)
        fun stats(id: String): PlayerStats {
            val name = game.name(id)
            return map.getOrPut(key(name)) { PlayerStats(name) }
        }
        val finished = game.status == GameStatus.FINISHED
        val finals = game.players.associate { it.id to standing.final(it.id) }
        val best = finals.values.maxOrNull()
        for (p in game.players) {
            val s = stats(p.id)
            s.games++
            if (finished) {
                s.finishedGames++
                s.totalFinal += finals[p.id] ?: 0
                if (finals[p.id] == best) s.wins++
            }
        }
        for (res in standing.results) {
            val r = res.round
            if (r.isAdjustment) continue
            for (id in r.playingIds) stats(id).roundsPlayed++
            if (r.kind == RoundKind.KORPASSZ) {
                for (id in r.playingIds) {
                    val s = stats(id)
                    s.korpassz++
                    s.korpasszTricks += r.tricks[id] ?: 0
                }
                continue
            }
            val decl = r.declarerId ?: continue
            val s = stats(decl)
            s.declared++
            if (r.made == true) s.made++
            if (r.contra > 0) s.contraGames++
            if (r.felkez) s.felkezGames++
            val tc = s.byType.getOrPut(r.game ?: 0) { TypeCount() }
            tc.declared++
            if (r.made == true) tc.made++
            for (d in r.playingIds.filter { it != decl }) {
                val ds = stats(d)
                ds.defended++
                if (r.made == false) ds.defendedWon++
            }
            for (p in res.penalized) stats(p).penalties++
        }
    }

    fun overall(games: List<Game>): OverallStats {
        val map = linkedMapOf<String, PlayerStats>()
        games.forEach { accumulate(it, map) }
        val rounds = games.flatMap { it.playedRounds }
        val durations = rounds.map { it.durationMs }.filter { it > 0 }
        val byType = sortedMapOf<Int, TypeCount>()
        for (r in rounds) if (r.kind == RoundKind.GAME) {
            val tc = byType.getOrPut(r.game ?: 0) { TypeCount() }
            tc.declared++
            if (r.made == true) tc.made++
        }
        val finishedGames = games.filter { it.status == GameStatus.FINISHED }
        return OverallStats(
            games = games.size,
            finished = finishedGames.size,
            active = games.count { it.status == GameStatus.ACTIVE },
            paused = games.count { it.status == GameStatus.PAUSED },
            rounds = rounds.size,
            korpassz = rounds.count { it.kind == RoundKind.KORPASSZ },
            gameRounds = rounds.count { it.kind == RoundKind.GAME },
            madeRounds = rounds.count { it.kind == RoundKind.GAME && it.made == true },
            avgRoundMs = if (durations.isEmpty()) 0 else durations.sum() / durations.size,
            longestRoundMs = durations.maxOrNull() ?: 0,
            totalPlayMs = games.sumOf { it.playTimeMs },
            avgGameMs = if (finishedGames.isEmpty()) 0 else finishedGames.sumOf { it.playTimeMs } / finishedGames.size,
            avgRoundsPerFinishedGame = if (finishedGames.isEmpty()) 0.0 else finishedGames.sumOf { it.playedRounds.size }.toDouble() / finishedGames.size,
            byType = byType,
            players = map.values.sortedWith(compareByDescending<PlayerStats> { it.games }.thenBy { it.name }),
        )
    }

    fun forGame(game: Game): GameStats {
        val map = linkedMapOf<String, PlayerStats>()
        accumulate(game, map)
        val played = game.playedRounds
        val durations = played.map { it.durationMs }.filter { it > 0 }
        return GameStats(
            rounds = played.size,
            avgRoundMs = if (durations.isEmpty()) 0 else durations.sum() / durations.size,
            longestRoundMs = durations.maxOrNull() ?: 0,
            shortestRoundMs = durations.minOrNull() ?: 0,
            playMs = game.playTimeMs,
            korpassz = played.count { it.kind == RoundKind.KORPASSZ },
            perPlayer = map.values.toList(),
        )
    }
}
