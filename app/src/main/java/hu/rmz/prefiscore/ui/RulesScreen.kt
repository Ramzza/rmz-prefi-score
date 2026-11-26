package hu.rmz.prefiscore.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.rmz.prefiscore.model.Rules

private val gameplay = listOf(
    "A játékosok száma 3 vagy 4.",
    "4 játékosnál az osztó minden körben pihen, így ténylegesen 3-an játszanak — kivéve körpassznál, amikor az osztó is játszik a talonban fekvő 2 lappal.",
    "Minden kör licittel kezdődik, ez dönti el, milyen játékot játszanak.",
    "Ha mindenki passzol, körpassz lesz: a soron következő kezd, nincs tromf, és annyi druzst (üres karika) írunk fel, ahányan játszanak.",
    "Aktív druzs esetén minden kör után áthúzunk egy druzst; ha a játékot megbukják, annyi új druzs adódik hozzá, ahányan játszanak.",
    "A licit lezárása után lehet kontrázni, többször is; minden szint duplázza a játék értékét: kontra, rekontra, szubkontra, Fedák Sári, Gáll Anna.",
    "Osztáskor mindig két lap fekszik: ez a talon.",
    "Talon nélkül is lehet licitálni: ilyenkor a játék 1 ponttal többet ér.",
    "Először mindenki 5 lapot kap (félkéz). Ebből is lehet licitálni: a játék értéke 1-gyel nő (nincs talon), majd duplázódik.",
    "Általában mindenki −20 ponttal indul fent; ez közös megegyezéssel módosítható.",
    "A játék akkor ér véget, ha a fenti pontok összege 0.",
    "A következő osztó mindig az előző osztó bal oldalán ülő játékos. A kispadon ülőt kihagyjuk.",
    "A licit után a másik két játékos a licit nyertese ellen játszik, és megpróbálja megbuktatni.",
    "Alapszabály: színre színt; ha nincs, tromfot; ha az sincs, bármit.",
    "A lapok sorrendje növekvően: 7, 8, 9, 10, alsó (J), felső (Q), király (K), ász (A).",
)

private val scoring = listOf(
    "Minden játékosnak van fenti pontja. A licit nyertesénél a játék értéke hozzáadódik (megcsinálta) vagy levonódik (megbukta).",
    "A játék értéke alapból a licit száma. Ez nő talon nélküli (+1), félkéz (+1, majd ×2), kontrázott (×2 szintenként) és druzsos (×2) játéknál.",
    "Minden játékosnak van pincéje, benne egy-egy szoba a többi játékosnak. Ide írjuk a rájuk „ráírt” pontokat.",
    "2, 3, 4, 5, 8 és 10-es játéknál minden ellenjátékos a saját vitelei × a játék értéke pontot írja a licit nyertesének szobájába.",
    "6, 7 és 9-es játéknál bukás esetén minden ellenjátékos érték × 10 / 2 pontot ír a licit nyertesének szobájába.",
    "Körpassznál minden vitel −1 fent. Aki legalább 6-ot vitt, annak minden vitele −2.",
    "2, 3, 4, 5, 8 és 10-es játéknál az az ellenjátékos, aki 2-nél kevesebbet vitt, −1-et kap fent — kivéve, ha ketten együtt legalább 3-at vittek.",
    "Végső pont: fent × 10 + a saját pince − a saját szobák összege a többiek pincéjében.",
)

private val notes = listOf(
    "Az alkalmazás értelmezése szerint aktív druzs alatt a játék értéke duplázódik. Körpassznál a druzs nem duplázza a vitelek pontját.",
    "A 2, 3, 4, 5, 8 és 10-es játékoknál az ellenjátékosok viteleit mindig be kell írni, megcsinált játéknál is. Ezek kellenek a pincéhez és a −1 pontos szabályhoz.",
    "Körpassznál „6 viteltől duplázva” azt jelenti, hogy 6 vagy több vitelnél az összes vitel duplán számít.",
)

@Composable
fun RulesScreen(app: AppState) {
    Scaffold(topBar = { AppTopBar("Játékszabályok", onBack = { app.back() }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle("Licitek")
            InfoCard {
                Rules.bids.forEach { t ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text("${t.code}", Modifier.width(32.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(t.name, style = MaterialTheme.typography.titleSmall)
                            if (t.condition.isNotEmpty()) Text(t.condition, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "tr: tromf (az épp játszott szín) · sor: a soron következő kezd (az osztó bal oldalán ülő) · alá: aláhívás (a licit nyertese előtt, a jobb oldalán ülő kezd) · v: vitelek száma",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionTitle("Kontra szintek")
            InfoCard {
                Rules.contraNames.drop(1).forEachIndexed { i, n -> StatRow(n, "×${1 shl (i + 1)}") }
            }
            SectionTitle("Játék menete")
            InfoCard { Numbered(gameplay) }
            SectionTitle("Pontozás")
            InfoCard { Numbered(scoring) }
            SectionTitle("Az alkalmazás értelmezése")
            InfoCard { Numbered(notes) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Numbered(items: List<String>) {
    items.forEachIndexed { i, s ->
        Row(Modifier.padding(vertical = 4.dp)) {
            Text("${i + 1}.", Modifier.width(28.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text(s, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
