# Sonari — Accessible STEM Explorer

> *Turning equations, data, and molecules into sound and touch.*

Sonari is a fully **offline Android app** that converts mathematical graphs, CSV datasets, and molecular structures into **spatial audio + haptic feedback + speech**, so blind and visually impaired students can explore STEM concepts independently — no internet, no special hardware beyond a phone.

---

## What it does

| Input | How you explore it |
|---|---|
| **Formula** (`sin(x)`, `x^2 − 3x`, …) | Type an equation → the curve plays as stereo pitch: high = high, left/right = spatial pan |
| **CSV data** (numeric x,y or category,value) | Paste/upload a file → line chart, scatter plot, or bar chart rendered and sonified |
| **Molecule** (name lookup or offline preset) | Drag a finger — atoms buzz, bonds hum, element names are spoken |

### Sensory channels

| Channel | Encodes | Notes |
|---|---|---|
| **Pitch** (200–1000 Hz, log scale) | Y-value / height | Configurable range in Settings |
| **Stereo pan** (−1 to +1) | X-position | Disable in Settings for mono speaker use |
| **Haptic contact buzz** | On a curve / atom / bar | Fires every ~300 ms while touching a feature |
| **Haptic pulse** | Landmark crossed | Distinct from contact buzz |
| **Speech (TTS)** | Landmark labels, coordinates, axis crossings | Queued, device TTS engine |

### Interaction modes

| Mode | Gesture | What happens |
|---|---|---|
| **Overview** | Tap Play | Left→right sweep: pitch traces the full shape; landmark pulses mark key points |
| **Explore — drag** | One finger drag | Real-time pitch + pan + contact buzz |
| **Explore — nearest landmark** | Two-finger tap | Speaks the nearest landmark (peak, intercept, atom, bar top) |
| **Explore — coordinates** | Double-tap | Speaks exact x,y world coordinates at finger position |
| **Origin earcon** | Automatic while dragging | Haptic + "x equals zero" / "y equals zero" when crossing an axis |

---

## Architecture

```
app/src/main/java/com/sonari/app/
├── MainActivity.kt          # Single activity; NavHost; engine lifecycle
├── model/
│   └── Renderable.kt        # LineChart, BarChart, ScatterChart, MoleculeGraph + Landmark
├── engine/
│   └── MappingEngine.kt     # Pure Kotlin: normX,normY + Renderable → Cue (freq,pan,onFeature,landmark,quadrant,clamped)
├── audio/
│   ├── Sonifier.kt          # AudioTrack stereo sine; USAGE_ASSISTANCE_SONIFICATION; exp smoothing
│   └── SweepPlayer.kt       # Coroutine sweep: left→right, configurable duration, landmark earcons
├── haptic/
│   └── Haptics.kt           # Tier A (API 31 Composition), B (API 26 amplitude), C (basic)
├── a11y/
│   └── Announcer.kt         # TTS queue; landmark(), coordinates(), announce()
├── data/
│   ├── EquationLoader.kt    # exp4j; 600 samples; auto-detects extrema → x-intercepts → y-intercept
│   ├── MoleculeLoader.kt    # PubChem PUG REST + 3 offline fallbacks (water, caffeine, aspirin)
│   └── CsvLoader.kt         # Parses CSV; numeric x,y → LineChart or ScatterChart; category,value → BarChart
└── ui/
    ├── HomeScreen.kt        # Tabs: Equation | Molecule | CSV
    ├── ExplorerScreen.kt    # Overview + Explore; canvas drawing for all Renderable types
    ├── SettingsScreen.kt    # Pitch range, sweep duration, haptic intensity, stereo toggle
    └── TutorialScreen.kt    # 9-step gesture guide with TTS auto-announce and live practice canvases
```

### Mapping engine

`DefaultMappingEngine.cueAt(normX, normY, renderable)` returns a `Cue`:

```kotlin
data class Cue(
    val freqHz: Double,   // log pitch 200–1000 Hz (configurable)
    val pan: Double,      // −1 (left) … +1 (right); 0 when stereo disabled
    val onFeature: Boolean,
    val landmark: Landmark?,
    val quadrant: Int,    // 1=TR 2=TL 3=BL 4=BR
    val clamped: Boolean  // curve outside visible y range
)
```

Chart-specific logic:

| Chart type | Pitch source | onFeature condition |
|---|---|---|
| `LineChart` | Interpolated curve y | \|finger y − curve y\| < 0.04 |
| `BarChart` | Bar value at finger x | finger y ≥ bar top − tolerance |
| `ScatterChart` | Nearest point's y | distance to nearest point < 0.08 |
| `MoleculeGraph` | Nearest atom's y | within atom or bond radius |

### Audio pipeline

```
Touch event (120 fps)
  → MappingEngine.cueAt()
  → Sonifier.setCue(freqHz, pan, active)
        ↓  (512-frame chunks, background daemon thread)
  AudioTrack stereo PCM float 44.1 kHz
  USAGE_ASSISTANCE_SONIFICATION
  Per-sample exp smoothing → no clicks
```

### Haptic tiers

| Tier | API | Method |
|---|---|---|
| A | 31+ | `VibrationEffect.Composition` — `PRIMITIVE_CLICK`, `PRIMITIVE_THUD` |
| B | 26–30 | `VibrationEffect.createOneShot` with amplitude |
| C | < 26 | `Vibrator.vibrate(ms)` |

### CSV auto-detection

`CsvLoader.load(text)`:
- ≥20 rows, numeric x,y, uniform x-spacing → `LineChart`
- Numeric x,y but sparse/non-uniform → `ScatterChart`
- First column non-numeric, second column numeric → `BarChart`

---

## Data loaders

### EquationLoader

```
EquationLoader.load("sin(x)", xMin=-π, xMax=π)
```

- Parses with **exp4j** (offline, no internet)
- 600 samples over `[xMin, xMax]`
- Auto-detects landmarks in order: extrema first, then x-intercepts, then y-intercept
- Blocks degenerate domain (`xMin ≥ xMax`)

### MoleculeLoader

```
MoleculeLoader.load("caffeine")   // tries PubChem, falls back to hardcoded
```

Offline fallbacks (always available, no network): **water**, **caffeine**, **aspirin**

### CsvLoader

```
CsvLoader.load("x,y\n0,0\n1,1\n...")
```

- Skips comment lines (`#`)
- Handles quoted commas (`"Math, Science"`)
- Returns `Result<Renderable>` — explicit error messages on failure

---

## Settings (all applied live)

| Setting | Range | Effect |
|---|---|---|
| Pitch low | 100–500 Hz | Maps to y = yMin |
| Pitch high | 500–2000 Hz | Maps to y = yMax |
| Sweep duration | 2–15 s | Overview play speed |
| Haptic intensity | 0–100% | Scales all haptic amplitude |
| Stereo pan | on/off | Off → mono (phone speaker friendly) |

---

## Tutorial

A 9-step interactive tutorial is always reachable from **Home → "How to use"** and **Settings → "Open tutorial"**.

Steps: Welcome → What Sonari does → Overview mode → Drag to explore (live practice) → Landmarks → Two-finger tap landmark (live practice) → Double-tap coordinates (live practice) → Axis earcons → Done.

Each step auto-announces via TTS on entry; "Repeat" re-reads; swipe or buttons navigate.

---

## Build

### Requirements

- JDK 17
- Android SDK compileSdk 35
- Physical Android phone (API 26+) — emulator cannot vibrate

### Build & install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Run unit tests

```bash
./gradlew :app:testDebugUnitTest
```

Targets: **MappingEngineTest** (26 tests), **EquationLoaderTest** (10 tests), **CsvLoaderTest** (17 tests).

---

## Demo script (30 seconds, eyes closed)

1. **Airplane mode on** — app still works fully.
2. Home → **Equation tab** → type `sin(x)`, domain `−6.28` to `6.28` → Explore.
3. Tap **Play** in Overview — pitch rises and falls with the sine wave; landmark pulses mark zero crossings.
4. Switch to **Explore** — drag a finger: left ear for negative x, right ear for positive x; contact buzz on the curve.
5. Slowly drag across x=0 — haptic + "x equals zero" speaks.
6. Two-finger tap anywhere — nearest landmark is announced.
7. Double-tap — exact coordinates spoken.
8. Home → **Molecule tab** → "water" → Explore — oxygen buzzes, hydrogens tick.

---

## Accepted limitations

- Sonification has a learning curve; the tutorial addresses this.
- Haptics confirm contact and mark landmarks; they do not render texture.
- Molecules are 2D connectivity graphs, not 3D geometry or stereochemistry.
- Stereo pan requires headphones; the phone speaker plays in mono (disable pan in Settings).
- Bar charts and scatter plots support Overview Play only for bar charts (line interpolation not meaningful for scatter).
- PubChem lookup requires network; the three offline fallbacks always work.

---

## Roadmap

| Phase | Milestone | Status |
|---|---|---|
| 1 | Formula → graph: equation input, EquationLoader, tactile explorer | ✅ |
| 2 | CSV data → chart: CsvLoader, BarChart, ScatterChart | ✅ |
| 3 | Molecule explorer: PubChem + offline fallbacks | ✅ |
| 4 | Tutorial & onboarding | ✅ |
| 5 | Settings: pitch range, sweep, haptics, stereo | ✅ |
| 6 | Accessible e-books & diagram import | Planned |
| 7 | Universal Accessibility SDK for any Android app | Planned |
