package hu.rmz.prefiscore.model

import java.util.UUID

/** Tiszta állapotátmenetek a játékon. Minden függvény új [Game]-et ad vissza. */
object GameEngine {
    fun newId(): String = UUID.randomUUID().toString()

    fun newGame(names: List<String>, startUpper: Int, now: Long): Game {
        val players = names.map { Player(newId(), it.trim(), startUpper) }
        val title = players.joinToString(", ") { it.name }
        return Game(
            id = newId(),
            title = title,
            players = players,
            startUpper = startUpper,
            draft = RoundDraft(startedAt = now),
            createdAt = now,
            updatedAt = now,
            sessions = listOf(PlaySession(now)),
        )
    }

    fun draft(game: Game, now: Long): RoundDraft = game.draft ?: RoundDraft(startedAt = now)

    private fun Game.withDraft(d: RoundDraft, now: Long) = copy(draft = d, updatedAt = now)

    fun chooseWinner(game: Game, playerId: String, now: Long): Game {
        val d = draft(game, now)
        return game.withDraft(
            d.copy(step = DraftStep.GAME, korpassz = false, declarerId = playerId, made = null, tricks = emptyMap()),
            now,
        )
    }

    fun chooseKorpassz(game: Game, now: Long): Game {
        val d = draft(game, now)
        val dealer = Table.currentDealer(game) ?: return game
        val playing = Table.playing(game, dealer, korpassz = true)
        return game.withDraft(
            d.copy(
                step = DraftStep.KORPASSZ_TRICKS, korpassz = true, declarerId = null, game = null, made = null,
                tricks = playing.associateWith { 0 },
            ),
            now,
        )
    }

    fun chooseGame(game: Game, code: Int, now: Long): Game {
        val d = draft(game, now)
        return game.withDraft(d.copy(step = DraftStep.MODIFIERS, game = code), now)
    }

    fun setTalon(game: Game, talon: Boolean, now: Long): Game {
        val d = draft(game, now)
        return game.withDraft(d.copy(talon = talon, felkez = if (talon) false else d.felkez), now)
    }

    fun setFelkez(game: Game, felkez: Boolean, now: Long): Game {
        val d = draft(game, now)
        return game.withDraft(d.copy(felkez = felkez, talon = !felkez), now)
    }

    fun changeContra(game: Game, delta: Int, now: Long): Game {
        val d = draft(game, now)
        val c = (d.contra + delta).coerceIn(0, Rules.maxContra)
        if (c == d.contra) return game
        return game.withDraft(d.copy(contra = c), now)
    }

    fun toOutcome(game: Game, now: Long): Game {
        val d = draft(game, now)
        return game.withDraft(d.copy(step = DraftStep.OUTCOME), now)
    }

    fun setOutcome(game: Game, made: Boolean, now: Long): Game {
        val d = draft(game, now)
        val needsTricks = d.game in Rules.trickGames
        val defenders = defenders(game)
        return game.withDraft(
            d.copy(
                made = made,
                step = if (needsTricks) DraftStep.TRICKS else DraftStep.CONFIRM,
                tricks = if (needsTricks) defenders.associateWith { d.tricks[it] ?: 0 } else emptyMap(),
            ),
            now,
        )
    }

    fun setTricks(game: Game, playerId: String, tricks: Int, now: Long): Game {
        val d = draft(game, now)
        if (d.tricks[playerId] == tricks) return game
        return game.withDraft(d.copy(tricks = d.tricks + (playerId to tricks.coerceIn(0, Rules.TOTAL_TRICKS))), now)
    }

    fun toConfirm(game: Game, now: Long): Game {
        val d = draft(game, now)
        return game.withDraft(d.copy(step = DraftStep.CONFIRM), now)
    }

    fun previousStep(d: RoundDraft): DraftStep = when (d.step) {
        DraftStep.WINNER -> DraftStep.WINNER
        DraftStep.GAME -> DraftStep.WINNER
        DraftStep.MODIFIERS -> DraftStep.GAME
        DraftStep.OUTCOME -> DraftStep.MODIFIERS
        DraftStep.TRICKS -> DraftStep.OUTCOME
        DraftStep.KORPASSZ_TRICKS -> DraftStep.WINNER
        DraftStep.CONFIRM -> when {
            d.korpassz -> DraftStep.KORPASSZ_TRICKS
            d.game in Rules.trickGames -> DraftStep.TRICKS
            else -> DraftStep.OUTCOME
        }
    }

    fun back(game: Game, now: Long): Game {
        val d = draft(game, now)
        val prev = previousStep(d)
        val nd = when (prev) {
            DraftStep.WINNER -> RoundDraft(startedAt = d.startedAt, pausedMs = d.pausedMs, pausedAt = d.pausedAt)
            else -> d.copy(step = prev)
        }
        return game.withDraft(nd, now)
    }

    fun resetDraft(game: Game, now: Long): Game {
        val d = draft(game, now)
        return game.withDraft(RoundDraft(startedAt = d.startedAt, pausedMs = d.pausedMs, pausedAt = d.pausedAt), now)
    }

    fun defenders(game: Game): List<String> {
        val d = game.draft ?: return emptyList()
        val dealer = Table.currentDealer(game) ?: return emptyList()
        return Table.playing(game, dealer, korpassz = false).filter { it != d.declarerId }
    }

    /** Hibaüzenet, ha a vitelek nem érvényesek (null, ha rendben). */
    fun tricksError(game: Game): String? {
        val d = game.draft ?: return "Nincs folyamatban lévő kör."
        val dealer = Table.currentDealer(game) ?: return "Nincs osztó."
        if (d.korpassz) {
            val playing = Table.playing(game, dealer, true)
            val sum = playing.sumOf { d.tricks[it] ?: 0 }
            for (p in playing) {
                val max = Table.korpasszCards(playing, dealer, p)
                if ((d.tricks[p] ?: 0) > max) return "${game.name(p)} legfeljebb $max vitelt vihet."
            }
            if (sum != Rules.TOTAL_TRICKS) return "A vitelek összege ${Rules.TOTAL_TRICKS} kell legyen (most: $sum)."
            return null
        }
        val sum = defenders(game).sumOf { d.tricks[it] ?: 0 }
        if (sum > Rules.TOTAL_TRICKS) return "Az ellenjátékosok összesen legfeljebb ${Rules.TOTAL_TRICKS} vitelt vihetnek."
        return null
    }

    /** Figyelmeztetés (nem blokkoló), ha a vitelek ellentmondanak a kimenetelnek. */
    fun tricksWarning(game: Game): String? {
        val d = game.draft ?: return null
        if (d.korpassz || d.game !in Rules.trickGames) return null
        val declarerTricks = Rules.TOTAL_TRICKS - defenders(game).sumOf { d.tricks[it] ?: 0 }
        return when {
            d.made == true && declarerTricks < 6 ->
                "A licit nyertese így csak $declarerTricks vitelt vitt, mégis \"megcsinálta\"."
            d.made == false && declarerTricks >= 6 && d.game in setOf(2, 3, 4, 5) ->
                "A licit nyertese így $declarerTricks vitelt vitt, mégis \"megbukta\"."
            else -> null
        }
    }

    fun draftToRound(game: Game, now: Long): Round? {
        val d = game.draft ?: return null
        val dealer = Table.currentDealer(game) ?: return null
        val number = game.playedRounds.size + 1
        return if (d.korpassz) {
            Round(
                number = number, kind = RoundKind.KORPASSZ, dealerId = dealer,
                playingIds = Table.playing(game, dealer, true), tricks = d.tricks,
                startedAt = d.startedAt, endedAt = now, pausedMs = d.pausedMs,
            )
        } else {
            if (d.declarerId == null || d.game == null || d.made == null) return null
            Round(
                number = number, kind = RoundKind.GAME, dealerId = dealer,
                playingIds = Table.playing(game, dealer, false), declarerId = d.declarerId, game = d.game,
                talon = d.talon, felkez = d.felkez, contra = d.contra, made = d.made,
                tricks = if (d.game in Rules.trickGames) d.tricks else emptyMap(),
                startedAt = d.startedAt, endedAt = now, pausedMs = d.pausedMs,
            )
        }
    }

    fun commit(game: Game, now: Long): Game {
        val round = draftToRound(game, now) ?: return game
        val g = game.copy(rounds = game.rounds + round, draft = RoundDraft(startedAt = now), updatedAt = now)
        return updateStatus(g, now)
    }

    /**
     * Pontkorrekció hozzáadása: a fenti pontok, pince-szobák és druzsok közvetlen módosítása.
     * A [nextDealerId] lesz a következő kör osztója.
     */
    fun addAdjustment(game: Game, adjustment: Adjustment, nextDealerId: String, now: Long): Game {
        val empty = adjustment.upper.values.all { it == 0 } && adjustment.pince.all { it.amount == 0 } &&
            adjustment.druzsWritten == 0 && adjustment.druzsCrossed == 0
        val dealerUnchanged = Table.currentDealer(game) == nextDealerId
        if (empty && dealerUnchanged) return game
        val round = Round(
            number = game.playedRounds.size,
            kind = RoundKind.ADJUSTMENT,
            dealerId = nextDealerId,
            playingIds = Table.active(game).map { it.id },
            startedAt = now,
            endedAt = now,
            adjustment = adjustment.copy(
                upper = adjustment.upper.filterValues { it != 0 },
                pince = adjustment.pince.filter { it.amount != 0 },
            ),
        )
        val d = game.draft
        val g = game.copy(
            rounds = game.rounds + round,
            draft = RoundDraft(startedAt = d?.startedAt ?: now, pausedMs = d?.pausedMs ?: 0, pausedAt = d?.pausedAt),
            updatedAt = now,
        )
        return updateStatus(g, now)
    }

    /** Az utolsó rögzített kör törlése (pl. betöltött, befejezett játéknál). */
    fun removeLastRound(game: Game, now: Long): Game {
        if (game.rounds.isEmpty()) return game
        val g = game.copy(rounds = game.rounds.dropLast(1), draft = RoundDraft(startedAt = now), updatedAt = now)
        return updateStatus(g, now)
    }

    fun updateStatus(game: Game, now: Long): Game {
        val remaining = Scoring.standings(game).remaining
        return when {
            remaining <= 0 && game.rounds.isNotEmpty() && game.status != GameStatus.FINISHED -> {
                val sessions = game.sessions.mapIndexed { i, s ->
                    if (i == game.sessions.lastIndex && s.end == null) s.copy(end = now) else s
                }
                game.copy(status = GameStatus.FINISHED, finishedAt = now, sessions = sessions)
            }
            remaining > 0 && game.status == GameStatus.FINISHED ->
                game.copy(status = GameStatus.ACTIVE, finishedAt = null, sessions = game.sessions + PlaySession(now))
            else -> game
        }
    }

    // ---- Kispad ----

    fun canBench(game: Game, playerId: String): Boolean =
        Table.active(game).size == 4 && game.player(playerId)?.benched == false

    fun canActivate(game: Game, playerId: String): Boolean =
        Table.active(game).size == 3 && game.player(playerId)?.benched == true

    fun setBenched(game: Game, playerId: String, benched: Boolean, now: Long): Game {
        val ok = if (benched) canBench(game, playerId) else canActivate(game, playerId)
        if (!ok) return game
        val players = game.players.map { if (it.id == playerId) it.copy(benched = benched) else it }
        return updateStatus(game.copy(players = players, updatedAt = now).let { resetDraft(it, now) }, now)
    }

    /** Új játékos hozzáadása (3 → 4 aktív). [afterId] után ül; null esetén a sor végére. */
    fun addPlayer(game: Game, name: String, startUpper: Int, afterId: String?, now: Long): Game {
        if (Table.active(game).size != 3) return game
        val p = Player(newId(), name.trim(), startUpper)
        val idx = game.players.indexOfFirst { it.id == afterId }
        val players = if (idx < 0) game.players + p else
            game.players.toMutableList().apply { add(idx + 1, p) }
        val g = game.copy(players = players, updatedAt = now)
        return updateStatus(resetDraft(g, now), now)
    }

    fun renamePlayer(game: Game, playerId: String, name: String, now: Long): Game {
        val n = name.trim()
        if (n.isEmpty()) return game
        return game.copy(players = game.players.map { if (it.id == playerId) it.copy(name = n) else it }, updatedAt = now)
    }

    // ---- Szüneteltetés / folytatás ----

    fun pause(game: Game, now: Long): Game {
        if (game.status == GameStatus.FINISHED) return closeSession(game, now)
        val d = game.draft
        return closeSession(game, now).copy(
            status = GameStatus.PAUSED,
            draft = d?.copy(pausedAt = d.pausedAt ?: now),
            updatedAt = now,
        )
    }

    /** Megnyitás / folytatás: új munkamenetet nyit, a szünet idejét levonja a kör hosszából. */
    fun resume(game: Game, now: Long): Game {
        val closed = closeSession(game, game.updatedAt)
        val d = closed.draft ?: RoundDraft(startedAt = now)
        val nd = when {
            d.pristine -> d.copy(startedAt = now, pausedAt = null, pausedMs = 0)
            d.pausedAt != null -> d.copy(pausedMs = d.pausedMs + (now - d.pausedAt).coerceAtLeast(0), pausedAt = null)
            else -> d
        }
        val status = if (game.status == GameStatus.FINISHED) GameStatus.FINISHED else GameStatus.ACTIVE
        val sessions = if (status == GameStatus.FINISHED) closed.sessions else closed.sessions + PlaySession(now)
        return closed.copy(status = status, draft = nd, sessions = sessions, updatedAt = now)
    }

    private fun closeSession(game: Game, now: Long): Game {
        val last = game.sessions.lastOrNull() ?: return game
        if (last.end != null) return game
        return game.copy(sessions = game.sessions.dropLast(1) + last.copy(end = maxOf(now, last.start)))
    }
}
