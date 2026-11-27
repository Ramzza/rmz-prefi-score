package hu.rmz.prefiscore.model

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val id: String,
    val name: String,
    /** A játékos kezdő "fenti" pontja (általában -20). */
    val startUpper: Int,
    /** Kispadon ül-e (kimarad az osztásból és a játékból). */
    val benched: Boolean = false,
)

@Serializable
enum class RoundKind { GAME, KORPASSZ, ADJUSTMENT }

/** Pince-változás egy korrekcióban: [ownerId] pincéjében [targetId] szobája [amount]-tal változik. */
@Serializable
data class PinceDelta(val ownerId: String, val targetId: String, val amount: Int)

/** Közvetlen pontkorrekció (kép alapú import / frissítés vagy kézi javítás). */
@Serializable
data class Adjustment(
    val upper: Map<String, Int> = emptyMap(),
    val pince: List<PinceDelta> = emptyList(),
    val druzsWritten: Int = 0,
    val druzsCrossed: Int = 0,
    /** "image" vagy "manual". */
    val source: String = "manual",
    val note: String = "",
)

@Serializable
data class Round(
    val number: Int,
    val kind: RoundKind,
    val dealerId: String,
    /** Az ebben a körben játszó játékosok ülésrendben, az osztó utáni játékostól kezdve. */
    val playingIds: List<String>,
    val declarerId: String? = null,
    /** A lejátszott játék kódja (2..10). */
    val game: Int? = null,
    val talon: Boolean = true,
    val felkez: Boolean = false,
    /** Kontra szint: 0 = nincs, 1 = kontra, ... 5 = Gáll Anna. */
    val contra: Int = 0,
    val made: Boolean? = null,
    /** Vitelek: játéknál az ellenjátékosoké, körpassznál minden játszóé. */
    val tricks: Map<String, Int> = emptyMap(),
    val startedAt: Long,
    val endedAt: Long,
    val pausedMs: Long = 0,
    /** Csak [RoundKind.ADJUSTMENT] esetén. A [dealerId] ilyenkor a következő kör osztója. */
    val adjustment: Adjustment? = null,
) {
    val durationMs: Long get() = (endedAt - startedAt - pausedMs).coerceAtLeast(0)
    val isAdjustment: Boolean get() = kind == RoundKind.ADJUSTMENT
}

@Serializable
enum class DraftStep { WINNER, GAME, MODIFIERS, OUTCOME, TRICKS, KORPASSZ_TRICKS, CONFIRM }

@Serializable
data class RoundDraft(
    val step: DraftStep = DraftStep.WINNER,
    val korpassz: Boolean = false,
    val declarerId: String? = null,
    val game: Int? = null,
    val talon: Boolean = true,
    val felkez: Boolean = false,
    val contra: Int = 0,
    val made: Boolean? = null,
    val tricks: Map<String, Int> = emptyMap(),
    val startedAt: Long,
    val pausedMs: Long = 0,
    val pausedAt: Long? = null,
) {
    val pristine: Boolean
        get() = step == DraftStep.WINNER && !korpassz && declarerId == null && game == null
}

@Serializable
data class PlaySession(val start: Long, val end: Long? = null)

@Serializable
enum class GameStatus { ACTIVE, PAUSED, FINISHED }

@Serializable
data class Game(
    val id: String,
    val title: String,
    /** Ülésrend: az első kezd, az utolsó oszt az első körben. */
    val players: List<Player>,
    val startUpper: Int = -20,
    val rounds: List<Round> = emptyList(),
    val draft: RoundDraft? = null,
    val status: GameStatus = GameStatus.ACTIVE,
    val createdAt: Long,
    val updatedAt: Long,
    val finishedAt: Long? = null,
    val sessions: List<PlaySession> = emptyList(),
    val imported: Boolean = false,
    val importedAt: Long? = null,
) {
    fun player(id: String?): Player? = players.firstOrNull { it.id == id }

    /** A ténylegesen lejátszott körök (korrekciók nélkül). */
    val playedRounds: List<Round> get() = rounds.filter { !it.isAdjustment }
    fun name(id: String?): String = player(id)?.name ?: "?"

    /** Összes játékidő a munkamenetek alapján. */
    val playTimeMs: Long
        get() = sessions.sumOf { ((it.end ?: updatedAt) - it.start).coerceAtLeast(0) }
}

/** Export/import csomag (egy vagy több játék). */
@Serializable
data class ExportBundle(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: Long,
    val games: List<Game>,
) {
    companion object {
        const val FORMAT = "prefi-score"
    }
}
