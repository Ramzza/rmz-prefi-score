package hu.rmz.prefiscore.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetImportTest {
    private var t = 10_000L
    private fun now() = (t + 1000).also { t = it }
    private fun id(g: Game, n: String) = g.players.first { it.name == n }.id

    @Test fun nameMatching() {
        val names = listOf("Szabó", "Boti", "Laci", "Zsomi")
        assertEquals(0, SheetImport.bestMatch("szabo", names))
        assertEquals(0, SheetImport.bestMatch("Szab", names))
        assertEquals(3, SheetImport.bestMatch("Zsomii", names))
        assertEquals(1, SheetImport.bestMatch("BOTI", names))
        assertNull(SheetImport.bestMatch("Ede", names))
    }

    @Test fun scanToState() {
        val s = SheetImport.fromScan(SheetFixtures.photo)
        assertEquals(listOf("Szabó", "Boti", "Laci", "Zsomi"), s.names)
        assertEquals(listOf(13, 10, 49, 15), s.paperUpper)
        assertEquals(listOf(0, 98, 4, 0), s.rooms[0])
        assertEquals(listOf(140, 0, 246, 90), s.rooms[1])
        assertEquals(listOf(182, 134, 0, 90), s.rooms[2])
        assertEquals(listOf(0, 46, 230, 0), s.rooms[3])
        assertEquals(12, s.druzsWritten)
        assertEquals(3, s.druzsCrossed)
        assertTrue(s.warnings.isEmpty())
    }

    @Test fun createGameFromPhoto() {
        val state = SheetImport.fromScan(SheetFixtures.photo)
        assertNull(SheetImport.validationError(state))
        val g = SheetImport.createGame(state, -20, PaperConvention.FROM_ZERO, nextDealerIndex = 1, source = "image", now = now())
        val st = Scoring.standings(g)
        assertEquals(-33, st.upperOf(id(g, "Szabó")))
        assertEquals(-30, st.upperOf(id(g, "Boti")))
        assertEquals(-69, st.upperOf(id(g, "Laci")))
        assertEquals(-35, st.upperOf(id(g, "Zsomi")))
        assertEquals(98, st.room(id(g, "Szabó"), id(g, "Boti")))
        assertEquals(246, st.room(id(g, "Boti"), id(g, "Laci")))
        assertEquals(9, st.druzsActive)
        assertEquals(33 + 30 + 69 + 35, st.remaining)
        // A pince zéró összegű: amit valaki ír, az másnál levonódik.
        assertEquals(0, g.players.sumOf { st.ownPince(it.id) - st.writtenOn(it.id) })
        assertEquals(id(g, "Boti"), Table.currentDealer(g))
        assertEquals(0, g.playedRounds.size)
        assertEquals(GameStatus.ACTIVE, g.status)

        // A korrekció utáni első kör duplázott (druzs), és a következő osztó Boti utáni játékos.
        var x = GameEngine.chooseWinner(g, id(g, "Laci"), now())
        x = GameEngine.chooseGame(x, 2, now())
        x = GameEngine.toOutcome(x, now())
        x = GameEngine.setOutcome(x, true, now())
        x = GameEngine.commit(GameEngine.toConfirm(x, now()), now())
        val st2 = Scoring.standings(x)
        assertEquals(-69 + 4, st2.upperOf(id(x, "Laci")))
        assertEquals(8, st2.druzsActive)
        assertEquals(id(x, "Laci"), Table.currentDealer(x))
        assertEquals(1, x.playedRounds.single().number)
        assertEquals(1, StatsCalculator.forGame(x).rounds)
    }

    @Test fun actualConventionUsesPaperValues() {
        val state = SheetImport.fromScan(SheetFixtures.photo).copy(paperUpper = listOf(-5, -10, 0, -3))
        val g = SheetImport.createGame(state, -20, PaperConvention.ACTUAL, 3, "image", now())
        val st = Scoring.standings(g)
        assertEquals(listOf(-5, -10, 0, -3), g.players.map { st.upperOf(it.id) })
    }

    @Test fun updateExistingGameFromPhoto() {
        // Az appban vezetett játék nevei kicsit eltérnek, sorrendje más; egy kör már le van játszva.
        var g = GameEngine.newGame(listOf("Boti", "Szabo", "Zsomi", "Laci"), -20, now())
        var x = GameEngine.chooseWinner(g, id(g, "Boti"), now())
        x = GameEngine.chooseGame(x, 3, now())
        x = GameEngine.toOutcome(x, now())
        x = GameEngine.setOutcome(x, true, now())
        x = GameEngine.setTricks(x, id(x, "Szabo"), 2, now())
        x = GameEngine.setTricks(x, id(x, "Zsomi"), 2, now())
        g = GameEngine.commit(GameEngine.toConfirm(x, now()), now())

        val state = SheetImport.fromScan(SheetFixtures.photo)
        val mapping = SheetImport.autoMapping(state, g)
        assertEquals(listOf(id(g, "Szabo"), id(g, "Boti"), id(g, "Laci"), id(g, "Zsomi")), mapping)
        assertNull(SheetImport.validationError(state, mapping))
        val u = SheetImport.updateGame(g, state, mapping, PaperConvention.FROM_ZERO, id(g, "Zsomi"), "image", now())
        val st = Scoring.standings(u)
        assertEquals(-33, st.upperOf(id(u, "Szabo")))
        assertEquals(-69, st.upperOf(id(u, "Laci")))
        assertEquals(140, st.room(id(u, "Boti"), id(u, "Szabo")))
        assertEquals(0, st.room(id(u, "Szabo"), id(u, "Zsomi")))
        assertEquals(12, st.druzsWritten)
        assertEquals(3, st.druzsCrossed)
        assertEquals(id(u, "Zsomi"), Table.currentDealer(u))
        assertEquals(RoundKind.ADJUSTMENT, u.rounds.last().kind)
        // Visszavonható: az utolsó bejegyzés törlése visszaadja az eredeti állást.
        assertEquals(Scoring.standings(g).upper, Scoring.standings(GameEngine.removeLastRound(u, now())).upper)
    }

    @Test fun partialMappingLeavesOthersUntouched() {
        val g = GameEngine.newGame(listOf("Anna", "Boti", "Cili"), -20, now())
        val state = SheetImport.fromScan(SheetFixtures.photo)
        val mapping = SheetImport.autoMapping(state, g)
        assertEquals(listOf(null, id(g, "Boti"), null, null), mapping)
        val u = SheetImport.updateGame(g, state, mapping, PaperConvention.FROM_ZERO, id(g, "Anna"), "image", now())
        val st = Scoring.standings(u)
        assertEquals(-30, st.upperOf(id(u, "Boti")))
        assertEquals(-20, st.upperOf(id(u, "Anna")))
        assertEquals(0, st.ownPince(id(u, "Boti")))
    }

    @Test fun manualCorrectionRoundTrip() {
        val g = SheetImport.createGame(SheetImport.fromScan(SheetFixtures.photo), -20, PaperConvention.FROM_ZERO, 0, "image", now())
        val state = SheetImport.fromGame(g)
        assertEquals(listOf(-33, -30, -69, -35), state.paperUpper)
        val fixed = state.withUpper(2, -60).withRoom(0, 1, 100)
        val u = SheetImport.updateGame(g, fixed, g.players.map { it.id }, PaperConvention.ACTUAL, Table.currentDealer(g)!!, "manual", now())
        val st = Scoring.standings(u)
        assertEquals(-60, st.upperOf(id(u, "Laci")))
        assertEquals(100, st.room(id(u, "Szabó"), id(u, "Boti")))
        val adj = u.rounds.last().adjustment!!
        assertEquals(mapOf(id(u, "Laci") to 9), adj.upper)
        assertEquals(1, adj.pince.size)
        assertEquals("manual", adj.source)
        // Változtatás nélkül nincs új bejegyzés.
        assertEquals(g, SheetImport.updateGame(g, state, g.players.map { it.id }, PaperConvention.ACTUAL, Table.currentDealer(g)!!, "manual", now()))
    }

    @Test fun validation() {
        val state = SheetImport.fromScan(SheetFixtures.photo)
        assertNotNull(SheetImport.validationError(state.withName(1, "szabó")))
        assertNotNull(SheetImport.validationError(state.copy(druzsCrossed = 13)))
        assertNotNull(SheetImport.validationError(state, listOf(null, null, null, null)))
        assertNotNull(SheetImport.validationError(state, listOf("a", "a", null, null)))
    }

    @Test fun scanJsonRoundTripAndHtml() {
        val text = JsonCodec.json.encodeToString(SheetScan.serializer(), SheetFixtures.photo)
        assertEquals(SheetFixtures.photo, JsonCodec.json.decodeFromString(SheetScan.serializer(), text))
        val g = SheetImport.createGame(SheetImport.fromScan(SheetFixtures.photo), -20, PaperConvention.FROM_ZERO, 0, "image", now())
        assertEquals(g, JsonCodec.decodeAny(JsonCodec.encodeGame(g)).single())
        assertTrue(HtmlReport.render(g, now()).contains("Korrekció képről"))
    }
}
