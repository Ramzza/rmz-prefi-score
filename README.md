# Prefi pontozó

Android alkalmazás a Prefi kártyajáték pontozásához: licit, kontra, talon/félkéz, druzs, körpassz, fent + pince, kispad, statisztikák, JSON import/export, HTML eredménymegosztás.

## Build

Követelmények: JDK 17+ (25-tel tesztelve), Android SDK `platforms;android-37.2`, `build-tools;37.0.0`.

```bash
./gradlew :app:testDebugUnitTest   # pontozási + Robolectric UI tesztek
./gradlew :app:assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

A release APK a debug kulccsal van aláírva, így közvetlenül telepíthető (Play Store-hoz saját kulcs kell).

## Felépítés

- `model/` – tiszta Kotlin logika: `Scoring` (pontszámítás), `GameEngine` (állapotátmenetek), `Table` (osztó, játszók, kezdő), `StatsCalculator`, `HtmlReport`, `JsonCodec`
- `data/` – fájlalapú tárolás (játékonként egy JSON) és megosztás (FileProvider, WhatsApp)
- `ui/` – Jetpack Compose képernyők

A szabályok alkalmazásbeli értelmezése a Szabályok nézet végén olvasható.
