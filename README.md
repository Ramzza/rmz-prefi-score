# Prefi pontozó

Android alkalmazás a Prefi kártyajáték pontozásához: licit, kontra, talon/félkéz, druzs, körpassz, fent + pince, kispad, statisztikák, JSON import/export, HTML eredménymegosztás.

## Build

Követelmények: JDK 17+ (25-tel tesztelve), Android SDK `platforms;android-37.2`, `build-tools;37.0.0`.

```bash
./gradlew :app:testDebugUnitTest   # pontozási + Robolectric UI tesztek
./gradlew :app:assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

A release APK a debug kulccsal van aláírva, így közvetlenül telepíthető (Play Store-hoz saját kulcs kell).

## Papíron vezetett pontozás importálása (kép → JSON)

- **Új játék papírról:** Kezdőlap → „Új játék papírról (fotó alapján)”.
- **Meglévő játék frissítése:** Játék → menü → „Pontok frissítése képről”. A lap játékosait név szerint automatikusan párosítja; a párosítás és minden érték mentés előtt javítható.
- **Kézi javítás:** Játék → menü → „Pontok kézi javítása”.

A fotót a Claude API (`claude-opus-5-5`, strukturált JSON kimenet) olvassa ki a `SheetScan` formátumba (`model/SheetImport.kt`). Ehhez saját Anthropic API kulcs kell: Beállítások menüpont. A változások korrekciós bejegyzésként kerülnek a játékba, ezért visszavonhatók.

A papíron a fenti pontot gyakran 0-ról vezetik, és a bukás növeli. Ilyenkor fent = kezdő pont − papíron levő érték. Ez a konvenció az ellenőrző képernyőn átállítható.

## Felépítés

- `model/` – tiszta Kotlin logika: `Scoring` (pontszámítás), `GameEngine` (állapotátmenetek), `Table` (osztó, játszók, kezdő), `SheetImport` (papír → játékállás), `StatsCalculator`, `HtmlReport`, `JsonCodec`
- `data/` – fájlalapú tárolás (játékonként egy JSON), megosztás (FileProvider, WhatsApp), `ClaudeSheetScanner` (kép → JSON a Claude API-val)
- `ui/` – Jetpack Compose képernyők

A szabályok alkalmazásbeli értelmezése a Szabályok nézet végén olvasható.
