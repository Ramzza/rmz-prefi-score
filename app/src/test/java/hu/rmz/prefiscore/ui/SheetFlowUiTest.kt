package hu.rmz.prefiscore.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import hu.rmz.prefiscore.MainActivity
import hu.rmz.prefiscore.PrefiApp
import hu.rmz.prefiscore.data.ScanException
import hu.rmz.prefiscore.data.SheetScanner
import hu.rmz.prefiscore.model.RoundKind
import hu.rmz.prefiscore.model.Scoring
import hu.rmz.prefiscore.model.SheetFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SheetFlowUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = (rule.activity.application as PrefiApp).state
    private var scanCalls = 0

    @Before fun setUp() {
        rule.runOnUiThread {
            app.background = { it() }
            app.onMain = { it() }
            app.scannerFactory = { key ->
                SheetScanner {
                    scanCalls++
                    if (key == "rossz") throw ScanException("Érvénytelen API kulcs. Ellenőrizd a Beállításokban.")
                    SheetFixtures.photo
                }
            }
        }
    }

    private fun tap(text: String, index: Int = 0) {
        val n = rule.onAllNodes(hasText(text) and hasClickAction())[index]
        runCatching { n.performScrollTo() }
        n.performClick()
        rule.waitForIdle()
    }

    private fun setImage() {
        rule.runOnUiThread { app.sheetFlow!!.image = byteArrayOf(1, 2, 3) }
        rule.waitForIdle()
    }

    private fun setKeyInSettings(key: String) {
        tap("Beállítások")
        rule.onAllNodes(hasSetTextAction())[0].performTextInput(key)
        tap("Mentés")
        rule.onNodeWithContentDescription("Vissza").performClick()
        rule.waitForIdle()
    }

    @Test fun newGameFromPhoto() {
        setKeyInSettings("sk-ant-test")
        assertEquals("sk-ant-test", app.apiKey)
        tap("Új játék papírról (fotó alapján)")
        rule.onNodeWithText("Új játék képről").assertExists()
        setImage()
        tap("Kép feldolgozása")
        assertEquals(1, scanCalls)
        rule.onNodeWithText("Eredmény előnézet".uppercase()).assertExists()
        // következő osztó: Boti (chip, nem a névmező)
        rule.onNode(hasText("Boti") and hasClickAction() and hasSetTextAction().not()).apply { performScrollTo(); performClick() }
        rule.waitForIdle()
        tap("Játék létrehozása")

        val g = app.session!!.game
        val st = Scoring.standings(g)
        assertEquals(listOf("Szabó", "Boti", "Laci", "Zsomi"), g.players.map { it.name })
        assertEquals(listOf(-33, -30, -69, -35), g.players.map { st.upperOf(it.id) })
        assertEquals(9, st.druzsActive)
        assertEquals(RoundKind.ADJUSTMENT, g.rounds.single().kind)
        assertEquals(g.players[1].id, hu.rmz.prefiscore.model.Table.currentDealer(g))
        rule.onNodeWithText("1. kör").assertExists()
        assertNull(app.sheetFlow)
    }

    @Test fun scanErrorIsShown() {
        setKeyInSettings("rossz")
        tap("Új játék papírról (fotó alapján)")
        setImage()
        tap("Kép feldolgozása")
        rule.onNodeWithText("Érvénytelen API kulcs. Ellenőrizd a Beállításokban.").assertExists()
    }

    @Test fun updateExistingGameAndManualCorrection() {
        setKeyInSettings("sk-ant-test")
        tap("Új játék")
        tap("+ 4. játékos hozzáadása")
        listOf("Boti", "Szabo", "Zsomi", "Laci").forEachIndexed { i, n -> rule.onAllNodes(hasSetTextAction())[i].performTextInput(n) }
        tap("Játék indítása")

        rule.onNodeWithContentDescription("Menü").performClick()
        rule.waitForIdle()
        tap("Pontok frissítése képről")
        setImage()
        tap("Kép feldolgozása")
        tap("Pontok frissítése")
        var g = app.session!!.game
        var st = Scoring.standings(g)
        val id = { n: String -> g.players.first { it.name == n }.id }
        assertEquals(-33, st.upperOf(id("Szabo")))
        assertEquals(246, st.room(id("Boti"), id("Laci")))
        rule.onNodeWithText("1. kör").assertExists()

        // Visszavonás a játéknézetben
        rule.onNodeWithContentDescription("Visszavonás").performClick()
        rule.waitForIdle()
        assertEquals(0, app.session!!.game.rounds.size)
        rule.onNodeWithContentDescription("Újra").performClick()
        rule.waitForIdle()

        // Kézi javítás: Laci fenti pontja −69 → −60
        rule.onNodeWithContentDescription("Menü").performClick()
        rule.waitForIdle()
        tap("Pontok kézi javítása")
        val lacisUpper = rule.onAllNodes(hasSetTextAction())[3 * 4] // játékosonként 1 fent + 3 szoba; Laci a 4. kártya
        lacisUpper.performScrollTo()
        lacisUpper.performTextClearance()
        lacisUpper.performTextInput("-60")
        rule.waitForIdle()
        tap("Javítás mentése")
        g = app.session!!.game
        st = Scoring.standings(g)
        assertEquals(-60, st.upperOf(g.players.first { it.name == "Laci" }.id))
        assertEquals(2, g.rounds.count { it.kind == RoundKind.ADJUSTMENT })
    }
}
