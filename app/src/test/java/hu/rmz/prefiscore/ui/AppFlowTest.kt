package hu.rmz.prefiscore.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import hu.rmz.prefiscore.MainActivity
import hu.rmz.prefiscore.PrefiApp
import hu.rmz.prefiscore.model.GameStatus
import hu.rmz.prefiscore.model.Scoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A valódi képernyők végigjárása Robolectricen: új játék, körök, pontok, előzmények, statisztika. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class AppFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = (rule.activity.application as PrefiApp).state

    private fun button(text: String): SemanticsNodeInteraction =
        rule.onNode(hasText(text) and hasClickAction())

    private fun tap(text: String) {
        val n = button(text)
        runCatching { n.performScrollTo() }
        n.performClick()
        rule.waitForIdle()
    }

    private fun tapNth(text: String, index: Int) {
        val n = rule.onAllNodes(hasText(text) and hasClickAction())[index]
        runCatching { n.performScrollTo() }
        n.performClick()
        rule.waitForIdle()
    }

    private fun startGame(vararg names: String) {
        tap("Új játék")
        if (names.size == 4) tap("+ 4. játékos hozzáadása")
        names.forEachIndexed { i, n -> rule.onAllNodes(hasSetTextAction())[i].performTextInput(n) }
        tap("Játék indítása")
    }

    @Test
    fun fullRoundFlowAndScores() {
        startGame("Anna", "Béla", "Cili")
        rule.onNodeWithText("1. kör").assertExists()

        tap("Anna")
        tap("4 · Szív")
        tap("+") // kontra
        rule.onNodeWithText("Kontra (×2)").assertExists()
        tap("Tovább: kimenetel")
        tap("Megcsinálta")
        tapNth("2", 0) // Béla 2
        tapNth("1", 1) // Cili 1
        tap("Tovább: összesítés")
        rule.onNodeWithText("Összesítés").assertExists()
        tap("Kör rögzítése")
        rule.onNodeWithText("2. kör").assertExists()

        val g = app.session!!.game
        val st = Scoring.standings(g)
        val id = { n: String -> g.players.first { it.name == n }.id }
        assertEquals(-12, st.upperOf(id("Anna")))
        assertEquals(16, st.room(id("Béla"), id("Anna")))
        assertEquals(8, st.room(id("Cili"), id("Anna")))
        assertEquals(-20, st.upperOf(id("Cili")))

        // Undo visszaviszi a rögzítés előtti állapotba, redo újra rögzíti
        rule.onNodeWithContentDescription("Visszavonás").performClick()
        rule.waitForIdle()
        assertEquals(0, app.session!!.game.rounds.size)
        rule.onNodeWithContentDescription("Újra").performClick()
        rule.waitForIdle()
        assertEquals(1, app.session!!.game.rounds.size)

        // Körpassz a 2. körben (osztó: Anna)
        tap("Körpassz")
        tapNth("5", 0)
        tapNth("3", 1)
        tapNth("2", 2)
        tap("Tovább: összesítés")
        tap("Kör rögzítése")
        assertEquals(3, Scoring.standings(app.session!!.game).druzsActive)

        // Pontok és körök fül
        button("Pontok").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Pince-mátrix", substring = true, ignoreCase = true).assertExists()
        button("Körök").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Körpassz").assertExists()

        // HTML megosztás menüből
        rule.onNodeWithContentDescription("Menü").performClick()
        rule.waitForIdle()
        tap("Eredmény megosztása (HTML)")

        // Szüneteltetés → előzmények
        rule.onNodeWithContentDescription("Menü").performClick()
        rule.waitForIdle()
        tap("Játék szüneteltetése")
        assertEquals(GameStatus.PAUSED, app.games.single().status)
        tap("Előzmények")
        rule.onAllNodesWithText("Szüneteltetve", substring = true)[0].assertExists()
        rule.onNodeWithContentDescription("Vissza").performClick()
        rule.waitForIdle()
        tap("Statisztika")
        rule.onNodeWithText("Játékosok".uppercase()).assertExists()
        rule.onNodeWithContentDescription("Vissza").performClick()
        rule.waitForIdle()
        tap("Szabályok")
        rule.onNodeWithText("Gáll Anna").assertExists()
    }

    @Test
    fun fourPlayersBenchAndBetli() {
        startGame("Anna", "Béla", "Cili", "Dani")
        // Dani oszt, így csak hárman választhatók licitnyertesnek
        rule.onAllNodes(hasText("Dani") and hasClickAction()).fetchSemanticsNodes().let { assertEquals(0, it.size) }
        tap("Béla")
        tap("6 · Betli")
        tap("Tovább: kimenetel")
        tap("Megbukta")
        tap("Kör rögzítése")
        val g = app.session!!.game
        val st = Scoring.standings(g)
        val id = { n: String -> g.players.first { it.name == n }.id }
        assertEquals(30, st.room(id("Anna"), id("Béla")))
        assertEquals(0, st.room(id("Dani"), id("Béla")))

        rule.onNodeWithContentDescription("Menü").performClick()
        rule.waitForIdle()
        tap("Kispad / játékosok")
        tapNth("Pihentet", 0)
        assertEquals(3, hu.rmz.prefiscore.model.Table.active(app.session!!.game).size)
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Ede")
        tap("Hozzáadás")
        assertEquals(4, hu.rmz.prefiscore.model.Table.active(app.session!!.game).size)
        assertEquals(5, app.session!!.game.players.size)
        tap("Kész")
        rule.onNodeWithText("Ki nyerte a licitet?").assertExists()
        assertNotNull(app.session)
    }

    @Test
    fun importRoundTrip() {
        startGame("Anna", "Béla", "Cili")
        val json = app.exportJson(listOf(app.session!!.game.id))
        rule.onNodeWithContentDescription("Vissza").performClick()
        rule.waitForIdle()
        app.delete(app.games.map { it.id }.toSet())
        val report = app.importTexts(listOf(json, "nem json"))
        assertEquals(1, report.added)
        assertEquals(1, report.failed)
        assertEquals(true, app.games.single().imported)
    }
}
