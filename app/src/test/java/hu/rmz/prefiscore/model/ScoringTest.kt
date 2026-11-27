package hu.rmz.prefiscore.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringTest {
    private var t = 1_000L
    private fun now() = (t + 1000).also { t = it }

    private fun game3() = GameEngine.newGame(listOf("Anna", "Béla", "Cili"), -20, now())
    private fun game4() = GameEngine.newGame(listOf("Anna", "Béla", "Cili", "Dani"), -20, now())
    private fun id(g: Game, name: String) = g.players.first { it.name == name }.id

    private fun play(
        g: Game, declarer: String, game: Int, made: Boolean,
        tricks: Map<String, Int> = emptyMap(), talon: Boolean = true, felkez: Boolean = false, contra: Int = 0,
    ): Game {
        var x = GameEngine.chooseWinner(g, id(g, declarer), now())
        x = GameEngine.chooseGame(x, game, now())
        if (felkez) x = GameEngine.setFelkez(x, true, now()) else if (!talon) x = GameEngine.setTalon(x, false, now())
        repeat(contra) { x = GameEngine.changeContra(x, 1, now()) }
        x = GameEngine.toOutcome(x, now())
        x = GameEngine.setOutcome(x, made, now())
        for ((n, v) in tricks) x = GameEngine.setTricks(x, id(x, n), v, now())
        x = GameEngine.toConfirm(x, now())
        return GameEngine.commit(x, now())
    }

    private fun korpassz(g: Game, tricks: Map<String, Int>): Game {
        var x = GameEngine.chooseKorpassz(g, now())
        for ((n, v) in tricks) x = GameEngine.setTricks(x, id(x, n), v, now())
        assertNull(GameEngine.tricksError(x))
        return GameEngine.commit(x, now())
    }

    @Test fun valueModifiers() {
        assertEquals(4, Scoring.value(4, talon = true, felkez = false, contra = 0, druzs = false))
        assertEquals(5, Scoring.value(4, talon = false, felkez = false, contra = 0, druzs = false))
        assertEquals(10, Scoring.value(4, talon = false, felkez = true, contra = 0, druzs = false))
        assertEquals(16, Scoring.value(4, talon = true, felkez = false, contra = 2, druzs = false))
        assertEquals(8, Scoring.value(4, talon = true, felkez = false, contra = 0, druzs = true))
        assertEquals(4 * 32, Scoring.value(4, talon = true, felkez = false, contra = 5, druzs = false))
    }

    @Test fun dealerRotationAndSeating() {
        val g = game3()
        assertEquals(id(g, "Cili"), Table.currentDealer(g))
        assertEquals(listOf("Anna", "Béla", "Cili"), Table.playing(g, id(g, "Cili"), false).map { g.name(it) })
        val g2 = play(g, "Anna", 2, true, mapOf("Béla" to 2, "Cili" to 2))
        assertEquals(id(g, "Anna"), Table.currentDealer(g2))
    }

    @Test fun fourPlayersDealerRests() {
        val g = game4()
        val dealer = Table.currentDealer(g)!!
        assertEquals("Dani", g.name(dealer))
        assertEquals(listOf("Anna", "Béla", "Cili"), Table.playing(g, dealer, false).map { g.name(it) })
        assertEquals(4, Table.playing(g, dealer, true).size)
        assertEquals(2, Table.korpasszCards(Table.playing(g, dealer, true), dealer, dealer))
    }

    @Test fun madeTrumpGameWithPince() {
        val g = play(game3(), "Anna", 4, true, mapOf("Béla" to 2, "Cili" to 1))
        val st = Scoring.standings(g)
        assertEquals(-16, st.upperOf(id(g, "Anna")))
        assertEquals(-20, st.upperOf(id(g, "Béla")))
        assertEquals(-20, st.upperOf(id(g, "Cili")))
        assertEquals(8, st.room(id(g, "Béla"), id(g, "Anna")))
        assertEquals(4, st.room(id(g, "Cili"), id(g, "Anna")))
        // Anna: -160 - 12, Béla: -200 + 8, Cili: -200 + 4 ; zéró összegű pince
        assertEquals(-172, st.final(id(g, "Anna")))
        assertEquals(-192, st.final(id(g, "Béla")))
        assertEquals(-196, st.final(id(g, "Cili")))
        assertEquals(56, st.remaining)
    }

    @Test fun defenderPenaltyWhenFewTricks() {
        val g = play(game3(), "Anna", 2, true, mapOf("Béla" to 1, "Cili" to 1))
        val st = Scoring.standings(g)
        assertEquals(-21, st.upperOf(id(g, "Béla")))
        assertEquals(-21, st.upperOf(id(g, "Cili")))
        val g2 = play(game3(), "Anna", 2, true, mapOf("Béla" to 3, "Cili" to 0))
        val st2 = Scoring.standings(g2)
        assertEquals(-20, st2.upperOf(id(g2, "Cili"))) // összesen 3 → nincs büntetés
        val g3 = play(game3(), "Anna", 2, true, mapOf("Béla" to 2, "Cili" to 0))
        val st3 = Scoring.standings(g3)
        assertEquals(-20, st3.upperOf(id(g3, "Béla")))
        assertEquals(-21, st3.upperOf(id(g3, "Cili")))
    }

    @Test fun failedBetliGoesToPince() {
        val g = play(game3(), "Béla", 6, false)
        val st = Scoring.standings(g)
        assertEquals(-26, st.upperOf(id(g, "Béla")))
        assertEquals(30, st.room(id(g, "Anna"), id(g, "Béla")))
        assertEquals(30, st.room(id(g, "Cili"), id(g, "Béla")))
        val ok = Scoring.standings(play(game3(), "Béla", 7, true))
        assertTrue(ok.pince.isEmpty())
    }

    @Test fun korpasszAndDruzs() {
        var g = korpassz(game3(), mapOf("Anna" to 6, "Béla" to 3, "Cili" to 1))
        var st = Scoring.standings(g)
        assertEquals(-32, st.upperOf(id(g, "Anna")))
        assertEquals(-23, st.upperOf(id(g, "Béla")))
        assertEquals(-21, st.upperOf(id(g, "Cili")))
        assertEquals(3, st.druzsActive)

        // druzs alatt dupla érték, kör után egy áthúzva
        g = play(g, "Anna", 2, true, mapOf("Béla" to 2, "Cili" to 2))
        st = Scoring.standings(g)
        assertEquals(-28, st.upperOf(id(g, "Anna")))
        assertEquals(8, st.room(id(g, "Béla"), id(g, "Anna")))
        assertEquals(2, st.druzsActive)

        // bukás druzs alatt: +3 új druzs
        g = play(g, "Béla", 3, false, mapOf("Anna" to 4, "Cili" to 2))
        st = Scoring.standings(g)
        assertEquals(-23 - 6, st.upperOf(id(g, "Béla")))
        assertEquals(1 + 3, st.druzsActive)
        assertEquals(6, st.druzsWritten)
        assertEquals(2, st.druzsCrossed)
    }

    @Test fun korpasszFourPlayersValidation() {
        val g = game4()
        var x = GameEngine.chooseKorpassz(g, now())
        x = GameEngine.setTricks(x, id(x, "Dani"), 3, now())
        x = GameEngine.setTricks(x, id(x, "Anna"), 7, now())
        assertNotNull(GameEngine.tricksError(x))
        x = GameEngine.setTricks(x, id(x, "Dani"), 2, now())
        x = GameEngine.setTricks(x, id(x, "Anna"), 8, now())
        assertNull(GameEngine.tricksError(x))
        val st = Scoring.standings(GameEngine.commit(x, now()))
        assertEquals(4, st.druzsActive)
        assertEquals(-36, st.upperOf(id(x, "Anna")))
    }

    @Test fun gameEndsWhenUpperSumZero() {
        var g = GameEngine.newGame(listOf("A", "B", "C"), -2, now())
        g = play(g, "A", 2, true, mapOf("B" to 2, "C" to 2))
        assertEquals(GameStatus.ACTIVE, g.status)
        g = play(g, "B", 2, true, mapOf("A" to 2, "C" to 2))
        g = play(g, "C", 2, true, mapOf("A" to 2, "B" to 2))
        assertEquals(GameStatus.FINISHED, g.status)
        val st = Scoring.standings(g)
        assertEquals(0, st.remaining)
        assertEquals(0, g.players.sumOf { st.final(it.id) })
        val undone = GameEngine.removeLastRound(g, now())
        assertEquals(GameStatus.ACTIVE, undone.status)
    }

    @Test fun benchAndAddPlayer() {
        var g = game4()
        g = GameEngine.setBenched(g, id(g, "Béla"), true, now())
        assertEquals(3, Table.active(g).size)
        assertFalse(GameEngine.canBench(g, id(g, "Anna")))
        g = GameEngine.addPlayer(g, "Ede", -20, id(g, "Anna"), now())
        assertEquals(listOf("Anna", "Ede", "Béla", "Cili", "Dani"), g.players.map { it.name })
        assertEquals(4, Table.active(g).size)
    }

    @Test fun leaderRules() {
        val playing = listOf("a", "b", "c")
        assertEquals("a", Table.leader(playing, 4, "c", false))
        assertEquals("b", Table.leader(playing, 6, "c", false))
        assertEquals("c", Table.leader(playing, 9, "a", false))
    }

    @Test fun jsonRoundTrip() {
        val g = play(game3(), "Anna", 4, true, mapOf("Béla" to 2, "Cili" to 2))
        val bundle = JsonCodec.encodeBundle(listOf(g, game4()), now())
        val back = JsonCodec.decodeAny(bundle)
        assertEquals(2, back.size)
        assertEquals(g, back[0])
        assertEquals(listOf(g), JsonCodec.decodeAny(JsonCodec.encodeGame(g)))
        assertTrue(HtmlReport.render(g, now()).contains("Anna"))
    }
}
