package hu.rmz.prefiscore.model

data class GameType(
    val code: Int,
    val name: String,
    /** Tromfos (soron következő kezd) vagy aláhívós játék. */
    val trump: Boolean,
    val condition: String,
)

object Rules {
    val bids: List<GameType> = listOf(
        GameType(0, "Passz", false, ""),
        GameType(1, "Játék", false, ""),
        GameType(2, "Zöld / Lapi", true, "tromf, sor, min. 6 vitel"),
        GameType(3, "Tök / Káró", true, "tromf, sor, min. 6 vitel"),
        GameType(4, "Szív", true, "tromf, sor, min. 6 vitel"),
        GameType(5, "Makk / Tref", true, "tromf, sor, min. 6 vitel"),
        GameType(6, "Betli", false, "aláhívás, 0 vitel"),
        GameType(7, "Mizer", false, "aláhívás, 1 vitel"),
        GameType(8, "Szanadu", false, "aláhívás, min. 6 vitel"),
        GameType(9, "Piccolo", false, "aláhívás, min. 2 vitel, nem egymás után"),
        GameType(10, "Preferance", false, "aláhívás, min. 6 vitel szívvel"),
    )

    /** Licit után választható (lejátszható) játékok. */
    val playable: List<GameType> = bids.filter { it.code >= 2 }

    /** Játékok, ahol az ellenjátékosok vitelei számítanak a pincébe. */
    val trickGames: Set<Int> = setOf(2, 3, 4, 5, 8, 10)

    /** Játékok, ahol bukás esetén érték×10/2 kerül a pincébe. */
    val penaltyGames: Set<Int> = setOf(6, 7, 9)

    val contraNames: List<String> = listOf("Nincs", "Kontra", "Rekontra", "Szubkontra", "Fedák Sári", "Gáll Anna")
    val maxContra: Int get() = contraNames.lastIndex

    const val TOTAL_TRICKS = 10
    const val DEFAULT_START_UPPER = -20

    fun type(code: Int?): GameType? = bids.firstOrNull { it.code == code }
    fun gameName(code: Int?): String = type(code)?.name ?: "?"
}
