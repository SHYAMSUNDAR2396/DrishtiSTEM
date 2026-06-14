# DrishtiSTEM (Sonari)

An Android accessibility app that turns STEM visuals into sound, vibration, and speech so users can explore equations and molecules without relying on vision.

> Current app/package naming in code is **Sonari** (`com.sonari.app`).

## Project analysis (current state)

This repository is a **single-module Android app** (`:app`) built with:

- Kotlin + Jetpack Compose
- Android SDK 35 / minSdk 26
- exp4j (equation parsing)
- OkHttp (PubChem fetch)
- MediaPipe GenAI (`tasks-genai`) for optional on-device Gemma voice command parsing

### What the app currently does

1. **Equation mode**
   - Users enter expressions like `x^2` or `sin(x)` with a custom domain.
   - The app samples the curve and auto-detects landmarks (intercepts, local extrema).
   - In Explore mode, finger position maps to:
     - pitch (vertical position)
     - stereo pan (horizontal position)
     - vibration (contact/feature feedback)

2. **Molecule mode**
   - Queries PubChem for molecule graph data (atoms + bonds).
   - Includes offline fallbacks: `water`, `caffeine`, `aspirin`.
   - Different haptic patterns are used for atom types and bond orders.

3. **Accessibility and guidance**
   - Text-to-speech announces landmarks, coordinates, molecule summaries, atoms, and bonds.
   - Tutorial flow explains gestures and interaction model.
   - Voice button supports speech recognition and command parsing.

4. **Voice command parsing**
   - Baseline regex parser handles commands such as:
     - load molecule
     - load equation
     - navigate home/back
     - open tutorial/settings
   - Optional Gemma 3n local model support (`GemmaVoiceBrain`) for smarter parsing if the model file is available.

5. **Deterministic CV pipeline (in repo)**
   - `core/vision/cv` contains image preprocessing + curve extraction + curve normalization classes.
   - Instrumentation tests exist for this pipeline.
   - This is present in codebase but separate from the main Home → Explore UI flow.

## Architecture overview

- `MainActivity` hosts a Compose `NavHost` (`tutorial`, `home`, `explore`, `settings`) and shared engines.
- Core runtime components:
  - `Sonifier` (continuous stereo audio synthesis)
  - `Haptics` (tiered vibration behavior by device capability)
  - `Announcer` (TTS output)
  - `DefaultMappingEngine` (maps touch position + renderable data into cues)

### Navigation flow

- First launch: Tutorial (unless completed flag is set in SharedPreferences)
- Then Home:
  - Equation tab → generates a `LineChart`
  - Molecule tab → loads a `MoleculeGraph`
- Explore screen supports:
  - Overview mode (auto sweep for charts)
  - Explore mode (touch-driven exploration)

## Repository structure

```text
app/src/main/java/com/sonari/app/
├── MainActivity.kt
├── a11y/Announcer.kt
├── audio/
│   ├── Sonifier.kt
│   └── SweepPlayer.kt
├── data/
│   ├── EquationLoader.kt
│   └── MoleculeLoader.kt
├── engine/MappingEngine.kt
├── haptic/Haptics.kt
├── model/Renderable.kt
├── ui/
│   ├── HomeScreen.kt
│   ├── ExplorerScreen.kt
│   ├── TutorialScreen.kt
│   └── SettingsScreen.kt
└── voice/
    ├── VoiceButton.kt
    ├── VoiceCommand.kt
    └── GemmaVoiceBrain.kt

app/src/main/java/com/technoblaze/drishtistem/core/vision/cv/
├── ImagePreprocessor.kt
├── CurveExtractor.kt
└── CurveNormaliser.kt

Tests:
- app/src/test/java/com/sonari/app/engine/
- app/src/androidTest/java/com/technoblaze/drishtistem/
```

## Permissions used

From `AndroidManifest.xml`:

- `INTERNET` (PubChem access)
- `VIBRATE`
- `RECORD_AUDIO` (voice command input)

## Build and test

### Requirements

- JDK 17
- Android SDK / Android Studio

### Commands

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

## Current known issue in this environment

During task validation, Gradle failed before build/test execution because the Android Gradle Plugin could not be resolved from remote repositories in the sandbox environment.

