# Set

Single-player Android implementation of the Set card game. Kotlin + Jetpack
Compose, one `:app` module. Read `DESIGN.md` before changing rules or layout.

## Build & run

```bash
./gradlew assembleDebug --console=plain
./gradlew testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.example.set/.MainActivity
adb exec-out screencap -p > /tmp/screen.png     # then read the image
adb logcat -c && adb logcat -d -v brief '*:E'
```

If `adb devices` is empty, run `~/.config/android-emulator.sh start`; never start
an adb daemon inside WSL.

## Card alignment check

`tools/gridcheck.py` decodes a screencap (stdlib only) and asserts every card
sits on the 3-column grid. Use it after any animation change:

```bash
adb exec-out screencap -p > /tmp/board.png
python3 tools/gridcheck.py /tmp/board.png      # PASS/FAIL + column/row edges
```

Animation state must always settle. Any `Animatable` driven by a flag that is
cleared on a timer (deal slide, gold pop, invalid shake) needs an `else` branch
that snaps to the resting value; otherwise the card stays parked mid-animation
for the rest of the run. Key per-card animatables on the card, not on a global
counter, or every deal restarts every slide.

## Architecture

- `engine/` is pure Kotlin: no Android imports, no clock, no I/O. Rules and
  scoring live here and must stay unit-testable. If a change needs
  `Context`, it does not belong in `engine/`.
- `GameState` is immutable; every transition returns a new state. Effects are
  one-shot UI concerns and never stored in the game state except `hintIndices`
  (cleared after 2.5 s) and `goldSlots` (the blink before a set is removed).
- `table` is a list of **slots**: a null is a hole. Replacement cards are dealt
  into holes left to right; other cards never move. Only an enlarged table
  (15/18) compacts when a set is removed. Do not reintroduce `filterNotNull`
  or list re-flows on the common path.
- A found set is held by the ViewModel for 640 ms (`goldSlots`) before
  `tap()` resolves it, so the UI can blink it. Taps are ignored while resolving.
- The ViewModel owns time and persistence only. It calls `tap()`, then
  `ensurePlayable()`. `tap()` resolves a trio and draws replacements;
  `ensurePlayable()` deals extra cards for a set-free table and ends the game.
- Settings use `SharedPreferences` synchronously. Do not add DataStore without a
  reason; see the deviations section of `DESIGN.md`.
- The in-progress game is saved after every action (`GameStore` + `GameCodec`,
  plain text, unit tested) and resumed on launch. The clock is accumulated, not
  derived from timestamps, so away time never counts.

## Rules invariants (tests enforce these)

- The deck is generated, never authored: 81 unique tuples, all 3⁴ combinations.
- Over all 85,320 triples, exactly **1,080** are sets.
- `cardsOnTable + deck.size + 3 * setsFound == 81` after every transition.
- A run ends when the deck is empty **and** the table has no set. That leaves
  0/3/6/9/12 cards behind, so sets found is **23–27**, not always 27. Never add
  UI or logic that assumes 27 is reachable.
- Replacement only tops the table back up to 12; a set found on a widened table
  (15/18) is removed without dealing.
- Hints: hard cap of 3 per game, no time penalty. Mistakes: +10 s, timed mode only.

## Theming

`SetTheme` provides `LocalContentColor` from `onBackground`. `MaterialTheme`
alone does not: uncoloured `Text`/`Icon` would fall back to black and vanish on
the dark background. Keep screen-level `Box`es on `background` and rely on that
provider rather than passing explicit colours.

## Versions

- compileSdk / targetSdk 37, minSdk 26. AGP 9.4.1, Gradle 9.8.2, Kotlin 2.2.10
  (AGP's built-in Kotlin — do not add the `kotlin-android` plugin).
- Compose BOM 2026.09.00; Compose compiler plugin version must equal the Kotlin
  version. All versions live in `gradle/libs.versions.toml`; never invent one.
- JDK 21 for Gradle (`$JAVA_HOME` from `~/.config/android-env.sh`).

## UI rules

- Cards are drawn on `Canvas` from `ui/ShapePaths.kt`; there are no image assets
  and none should be added. Shape geometry is normalised to a unit box.
- Shapes are wider than tall (physical Set cards), stacked **vertically** on a
  card, with **vertical** stripes. Aspect ratios live in `ShapePaths`; do not
  draw a tall shape or lay symbols out in a row.
- Symbol size scales with the count: 1.0, 0.9, 0.8 for one, two, three symbols.
  The base size is solved from the three-symbol case; keep that hierarchy when
  changing the layout maths.
- Animations: a found set turns gold, pops to 1.06 after 200 ms, and is removed
  at 520 ms; an invalid trio shakes only those cards (`invalidSlots`, cleared
  after 380 ms) — never the board; new cards slide from one shared point off the
  bottom-left corner (`ui/DealIn.kt`, 360 ms, keyed on `GameState.deals`).
- Theme: card faces use `surfaceContainerLowest`, borders `outlineVariant`,
  selection `onSurface`. Dark theme has its own palette and lifted shape colours;
  never hardcode `Color.White`/`Color.Black` for text or outlines.
- Selection and hints must be visible without colour (border/lift), and every
  card carries a `contentDescription` like "two solid red diamonds".
- Palette lives in `ui/theme/Theme.kt`. The colorblind option swaps to the
  Okabe–Ito triple; do not hardcode card colours in composables.
- Layout: 3 columns, card aspect 1:1.4, card size derived from available height
  so a 15/18-card table shrinks instead of scrolling.

## Known traps

- `sqrt(sin(PI * t))` is NaN at t = 1 in Float maths; that produced an invisible
  card. Clamp before `sqrt` when building paths.
- A rounded-rect corner radius must stay below half the width, or an "oval"
  collapses into an ellipse.
- Do not import `androidx.compose.animation.AnimatedVisibility` inside a
  `ColumnScope`/`BoxScope` mix; use a plain conditional instead.
- `var x by mutableStateOf(...)` plus `fun setX(...)` clashes on the JVM
  setter signature. Name mutators `updateX`.

## Agent loop

1. Read `DESIGN.md` and the engine tests before touching rules.
2. Change → `./gradlew testDebugUnitTest` → `assembleDebug` → install → verify.
3. Evidence only: Gradle output, a screenshot, a uiautomator dump, or logcat.
4. After UI work, solve one set through the accessibility tree to prove the tap
   path still works; the board's `content-desc` values make this scriptable.
