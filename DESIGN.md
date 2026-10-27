# Set — Design Document

A single-player, portrait-first Android implementation of the classic Set card game.

- **Working title:** Set
- **Package:** `com.example.set` (rename to `dev.<you>.set` before any public release)
- **Platform:** Android, `minSdk 26`, `targetSdk 37`, Kotlin + Jetpack Compose
- **Status:** design approved for implementation — no code written yet
- **Last updated:** 2026-10-26

---

## 1. Goals

1. **Rules-faithful.** The 81-card deck, the "all same or all different" rule for every
   attribute, and the official table/deal behaviour must be exactly right. This is the
   one area where "close enough" is a bug.
2. **Instant to play.** One screen, no tutorial wall. Open → cards are dealt → tap.
   A first-time player who knows the game never sees a dialog.
3. **Clean and calm.** Vertical, generous whitespace, no chrome competing with the cards.
   The board *is* the interface.
4. **Provable.** The engine is pure Kotlin with no Android dependencies and is covered by
   unit tests; every UI change is verified on a real emulator with a screenshot or logcat.

### Non-goals for v1

- Multiplayer, online play, or accounts.
- Campaigns, levels, power-ups, currencies, ads.
- Daily seeded challenge (deferred to v1.1).
- Card art beyond procedurally drawn geometry (no image assets).
- Landscape-optimised or tablet-specific layouts (it must not break, but portrait is the design).
- Process-death restoration of an in-progress game (rotation survival is required).

---

## 2. Game rules (authoritative spec)

### 2.1 The deck

81 unique cards. A card is the tuple of four attributes, each with three values:

| Attribute | Values |
|---|---|
| Number | 1, 2, 3 |
| Color | red, green, purple |
| Shape | diamond, squiggle, oval |
| Shading | solid, striped, open |

All 3⁴ = 81 combinations exist exactly once. The deck is generated programmatically, never
hand-authored.

### 2.2 What counts as a set

Three cards form a **set** iff, for **each** of the four attributes independently, the three
values are either **all the same** or **all different**.

```
for each attribute a in {number, color, shape, shading}:
    values = {c1.a, c2.a, c3.a}
    require values.size == 1 || values.size == 3
```

Two same + one different is *not* a set. A trio may be all-same on some attributes and
all-different on others simultaneously.

### 2.3 Play

1. Shuffle the deck. Deal **12 cards** face up into a 3-column grid.
2. The player taps three cards.
   - If they form a set: the cards leave the table. If the deck still has cards, deal
     **three replacements** so the table returns to its previous size.
   - If they do not: the selection clears and the player is charged a mistake.
3. If the table contains **no set** at all (this is a real state — 18 cards can be set-free),
   the game announces it and deals **three extra cards** without removing anything. The
   table grows to 15, then 18. 21 cards mathematically guarantee a set, so this terminates.
4. While the table is enlarged, a found set is removed **without replacement**. The table
   shrinks back toward 12. Only when the table is back to 12 does normal replacement resume.
5. The game ends when the deck is empty and the table holds no set.

   **A run does not necessarily use all 81 cards.** The deck empties in threes, so when it runs
   out the table holds 12 cards; from there each found set shrinks the table by three. Play stops
   at the first table with no set, leaving **0, 3, 6, 9 or 12** cards behind. Final sets found is
   therefore **23–27**, not always 27 — a 9-card table can be set-free (the largest set-free
   subset of the deck has 20 cards). This is the real game, so the UI never claims 27 is
   reachable: the header shows the set count alone, and a run that empties the table earns a
   **PERFECT** badge. Sets found is the first sort key for best results, time is the second.

### 2.4 Scoring (v1: "Clear the deck")

- **Primary metric:** sets found, then elapsed time. Sets found varies with the shuffle
  (23–27); a *perfect* run leaves the table empty.
- **Mistakes:** each non-set submission adds **+10 s** to the final time.
- **Hints:** at most **3 per game**. The footer shows how many remain and the button
  disables at zero. No time penalty — the cap is the cost.
- **Timed is the default mode.** Relaxed is one tap away on the start screen and is
  remembered as the last-used mode.
- The clock starts on the first tap, not on deal, so the player can look at the board first.
- The clock pauses when the app is paused or the pause dialog is open.
- Best time and best time-with-penalties are stored per mode and shown on the summary screen.

A "Relaxed" mode (same rules, clock hidden and no penalties) is included because it costs
almost nothing and is the right default for learning. Mode is chosen on the start screen and
persisted as the last-used mode.

---

## 3. User experience

### 3.1 Screen map

Three surfaces only:

1. **Start** — title, mode toggle (Clear the deck / Relaxed), best time, "Play".
2. **Game** — the board plus a thin header and footer. This is 95% of the app.
3. **Summary** — result, time, mistakes, hints, best-time badge, "Play again" / "Home".

Plus a **pause sheet** reachable from the game header (also triggered by leaving the app).

### 3.2 Game screen layout (portrait)

```
┌─────────────────────────────────────┐
│  ‹      SETS 12/27      02:41   ⋮   │  header 56dp, status bar inset above
├─────────────────────────────────────┤
│                                     │
│   ┌───────┐  ┌───────┐  ┌───────┐   │
│   │       │  │       │  │       │   │
│   │       │  │       │  │       │   │  board: 3 columns,
│   └───────┘  └───────┘  └───────┘   │  fixed 1 : 1.4 card aspect,
│   ┌───────┐  ┌───────┐  ┌───────┐   │  12dp gaps, 20dp side margins
│   │       │  │       │  │       │   │
│   │       │  │       │  │       │   │  cards shrink one step when the
│   └───────┘  └───────┘  └───────┘   │  table grows to 15 or 18
│   ┌───────┐  ┌───────┐  ┌───────┐   │
│   │       │  │       │  │       │   │
│   └───────┘  └───────┘  └───────┘   │
│   ┌───────┐  ┌───────┐  ┌───────┐   │
│   │       │  │       │  │       │   │
│   └───────┘  └───────┘  └───────┘   │
│                                     │
├─────────────────────────────────────┤
│        [ Hint ]      [ New game ]   │  footer 64dp, nav bar inset below
└─────────────────────────────────────┘
```

- **Header:** back, sets-found counter, timer, overflow (pause / how to play / sound).
- **Board:** always 3 columns. Card size is derived from available width and the current row
  count (4 rows normally, 5–6 when extra cards are dealt). One size step down for 15/18 so the
  board never scrolls. If the computed width would fall below 84 dp (very short screens),
  the board becomes vertically scrollable as a last resort.
- **Footer:** Hint and New game. New game asks for confirmation only if a game is in progress.
- Cards are the only tappable targets during play; the footer is deliberately low-contrast so
  it doesn't pull attention.

### 3.3 Interaction and feedback

| Event | Feedback |
|---|---|
| Card tapped | Card lifts ~2 dp, border thickens, subtle scale to 1.02 over 120 ms; haptic tick |
| Third card tapped | 220 ms validation beat before resolution (so the player sees all three) |
| Valid set | Cards flash the accent color, then collapse/fade out over 260 ms; replacements deal in with a 40 ms stagger; short ascending tone; haptic confirm |
| Invalid trio | Cards shake horizontally (±6 dp, 3 cycles, 300 ms), then deselect; low tone; haptic reject |
| Hint | One valid set's three cards pulse an outline for 2.5 s; timer penalty toast |
| No set on table | Inline banner "No set here — dealing 3 more" for 2 s, then the deal animates |
| Game over | Board fades, summary sheet slides up |
| Pause | Board blurs/dims behind a sheet; clock stops |

Additional rules:

- A fourth tap on an already-selected card deselects it. A tap on a fourth *different* card
  while three are selected is ignored until the trio resolves (prevents accidental clears).
- The clock is monospaced so digits don't jitter.
- Sound and haptics default on, toggleable in the overflow menu and persisted. Haptics degrade
  silently when the device has no vibrator.
- Leaving the app (ON_STOP) opens the pause state and stops the clock.

### 3.4 Accessibility

- Every card has a `contentDescription` enumerating its attributes, e.g. *"two solid red
  diamonds"*, and `stateDescription` *"selected"* when applicable. The board is a
  `selectableGroup` so TalkBack reads it as one unit.
- Selection is conveyed by border + lift, never by color alone.
- Touch targets ≥ 48 dp; the smallest legal card (~84 dp wide) still exceeds this.
- Header text uses `sp` and the header row is allowed to grow one line at font scale 2.0.
- **Colorblind assist** setting: swaps the red/green/purple palette for an Okabe–Ito-derived
  triple (vermillion `#D55E00`, bluish green `#009E73`, reddish purple `#CC79A7`), which stays
  separable under deuteranopia/protanopia. Off by default to preserve the classic look.
- Reduced-motion setting follows the system animator-duration-scale; shake and flash are
  replaced by an outline change when animations are disabled.

---

## 4. Visual design

### 4.1 Tokens

| Token | Value |
|---|---|
| Surface | `#FAF7F2` (warm paper) |
| Card face | `#FFFFFF` |
| Card border | `#D9D2C7`, selected `#1B1B1F` at 2 dp |
| Ink / text | `#1B1B1F` |
| Muted text | `#6B6459` |
| Accent (valid set, primary buttons) | `#5B4BD6` |
| Error (invalid trio) | `#B3261E` |
| Red / Green / Purple | `#D93A2B` / `#1F9D55` / `#7A4FD1` |
| Card radius | 12 dp |
| Card elevation | 1 dp rest, 4 dp selected |
| Spacing scale | 4, 8, 12, 16, 20, 24 dp |
| Type | Material 3 defaults; timer and counters in `Roboto Mono`-equivalent (`FontFamily.Monospace`) |

Material 3 theming only; colors are defined once in `ui/theme/Color.kt` and consumed through
`MaterialTheme.colorScheme` plus a small `SetColors` extension for the three card colors.
No hand-rolled per-composable color literals.

### 4.2 Card rendering

Cards are drawn with `Canvas`, not images. Each card is a `CardFace` composable that takes the
four attributes and draws into a box of `w × 1.4w`:

- **Shape geometry**, normalised to a unit box and scaled to fit:
  - *Diamond*: rhombus with vertices at (0.5, 0), (1, 0.5), (0.5, 1), (0, 0.5); drawn slightly
    narrower (×0.62) so it reads as a Set diamond rather than a square rotated 45°.
  - *Oval*: stadium shape — two semicircles of radius `h/2` joined by straight edges; width 0.78
    of the unit box.
  - *Squiggle*: an S-curve built from four cubic Béziers (two lobes), closed and symmetric about
    the centre line; width 0.82, amplitude 0.42 of the box. The path is cached per size.
- **Shading**:
  - *Solid*: fill the path with the card color.
  - *Striped*: stroke the outline at 2 dp and fill with 7 horizontal stripes of the card color,
    clipped to the path, each ~2 dp thick with equal gaps. Stripe count is fixed, not scaled, so
    striped cards stay recognisable at small sizes.
  - *Open*: stroke the outline only, 2.5 dp.
- **Number**: repeat the shape 1, 2, or 3 times in a horizontal row, centred, with 0.12w gaps;
  per-shape width divides the available row width by the count. One shape is larger than each
  shape in a trio.
- The card face keeps ≥ 8 dp inner padding so stripes never touch the border.

Rendering rules are unit-testable only at the geometry level (path bounds, repetition count), so
visual correctness is verified by screenshot on the emulator.

### 4.3 Motion

All motion uses `animateFloatAsState` / `AnimatedVisibility` with Material 3 easing. Durations:
120 ms (press), 220 ms (validation beat), 260 ms (set clear), 300 ms (invalid shake),
40 ms × n (deal stagger). Nothing exceeds 300 ms except the 2.5 s hint pulse. No decorative
motion on the start or summary screens beyond a 200 ms fade.

---

## 5. Architecture

### 5.1 Module and package layout

Single Gradle module (`:app`) — the app is small and an extra module would only add ceremony.

```
app/src/main/java/com/example/set/
├── MainActivity.kt              edge-to-edge, single activity, no fragments
├── SetApp.kt                    NavHost: start → game → summary
├── engine/                      pure Kotlin, zero Android imports
│   ├── Card.kt                  Card, Number/Color/Shape/Shading enums
│   ├── Deck.kt                  fullDeck(), shuffled(rng)
│   ├── Rules.kt                 isSet(a,b,c), findSet(cards)
│   ├── GameState.kt             immutable state + pure transitions
│   └── Scoring.kt               penalties, formatting
├── data/
│   └── SettingsRepository.kt    DataStore: mode, sound, haptics, colorblind, best times
├── game/
│   ├── GameViewModel.kt         StateFlow<GameState>, tick loop, events
│   └── GameScreen.kt            board, header, footer, dialogs
├── ui/
│   ├── CardFace.kt              Canvas rendering
│   ├── ShapePaths.kt            normalised shape geometry
│   └── theme/                   Color.kt, Type.kt, Theme.kt
└── summary/SummaryScreen.kt
```

### 5.2 Engine API

The engine is pure and synchronous; the ViewModel owns time and I/O.

```kotlin
enum class Number { ONE, TWO, THREE }
enum class Color { RED, GREEN, PURPLE }
enum class Shape { DIAMOND, SQUIGGLE, OVAL }
enum class Shading { SOLID, STRIPED, OPEN }

data class Card(
    val number: Number,
    val color: Color,
    val shape: Shape,
    val shading: Shading,
)

fun fullDeck(): List<Card>                       // 81 cards, deterministic order
fun shuffledDeck(random: Random): List<Card>

fun isSet(a: Card, b: Card, c: Card): Boolean
fun findSet(cards: List<Card>): Triple<Card, Card, Card>?   // null when none

data class GameState(
    val table: List<Card>,          // 12, 15 or 18
    val deck: List<Card>,           // remaining draw pile
    val selected: List<Int>,        // indices into table, 0..2
    val setsFound: Int,             // 0..27
    val mistakes: Int,
    val hintsUsed: Int,
    val phase: Phase,               // Playing, Paused, GameOver
    val lastEvent: GameEvent?,      // SetFound, InvalidTrio, ExtraDeal, NoSetAndDeckEmpty
    val elapsedSeconds: Long,
    val mode: Mode,                 // TIMED, RELAXED
)
```

Transitions are pure functions returning a new `GameState`:
`GameState.tap(index)`, `GameState.hint()`, `GameState.startTimer()`, `GameState.pause()`,
`GameState.resume()`, `GameState.newGame(random)`.

Key invariants, asserted in tests and in `check()` during debug builds:
`table.size + deck.size + 3 * setsFound == 81` at all times, `table.distinct().size == table.size`,
and `selected.size <= 3`.

### 5.3 Timing and events

- The ViewModel runs a coroutine ticker (`delay(1000)` in a `while (isActive)`) that only emits
  while `phase == Playing`; `elapsedSeconds` is derived, never accumulated by hand.
- One-shot effects (sound, haptic, shake trigger) go through a `SharedFlow<UiEffect>` so they
  are not replayed on recomposition or rotation. Board content stays in the `StateFlow`.
- Config changes: the ViewModel survives rotation. Process death mid-game restarts a fresh game
  in v1; `SavedStateHandle` restoration is a stretch goal.

### 5.4 Dependencies

Reuse the pinned versions from the sibling project; do not invent new ones.

| Artifact | Version |
|---|---|
| AGP | 9.4.1 |
| Kotlin | 2.2.10 (Compose compiler plugin must match) |
| Compose BOM | 2026.09.00 |
| activity-compose | 1.13.0 |
| lifecycle (+ viewmodel-compose) | 2.11.0 |
| core-ktx | 1.19.1 |
| DataStore Preferences | resolved from the version catalog at scaffold time |
| JUnit 4 | 4.13.2 |

No navigation library in v1: three screens with a sealed route in a single `when` is less code
than a NavHost and has no back-stack surprises. Revisit if a fourth screen appears.

---

## 6. Testing and verification

### 6.1 Engine tests (JVM, fast, must all pass before UI work)

1. `fullDeck` has 81 cards, all distinct, and covers all 81 attribute tuples.
2. `isSet` truth table: for a single attribute, same/same/same → true; same/same/different →
   false; all three different → true. Verified for all four attributes.
3. **Exhaustive set count:** over all C(81,3) = 85,320 triples, exactly **1,080** are sets.
   This single test catches almost every rule bug.
4. `findSet` returns a valid set when one exists and null when it does not; verified against a
   brute-force oracle on random 12-card tables.
5. Deal invariants hold after every transition in a random 27-set playthrough that ends with an
   empty deck, empty table, and `setsFound == 27`.
6. Extra-deal behaviour: a set-free 12-card table triggers an extra deal to 15; a set found at 15
   removes without replacement (table → 12); replacement resumes at 12.
7. Penalty maths: 3 mistakes + 2 hints add exactly 60 s in timed mode and 0 in relaxed mode.

### 6.2 ViewModel tests

`kotlinx-coroutines-test` with a virtual clock: tap sequences produce the expected state and
effects; pause freezes the clock; hint marks a valid trio and charges the penalty; a completed
game emits `GameOver` exactly once.

### 6.3 UI verification (on the emulator, evidence required)

- `./gradlew testDebugUnitTest` green.
- `./gradlew assembleDebug` green.
- Install and drive with adb; capture a screenshot of the dealt board and of a completed set.
- Scripted device check (implemented): parse `uiautomator dump` content descriptions, solve a
  set from the accessibility tree, tap its three cards, and assert the set counter increments
  and the table refills to 12. This exercises the same tap path a player uses and doubles as an
  accessibility check, because it only works if every card exposes its attributes.
- A Compose UI test asserting card semantics is still planned for CI, not required locally.
- Check logcat for exceptions after each run; a screenshot alone is not proof the app is healthy.

### 6.4 Manual check on the emulator

Cold start → deal looks correct → find a set → replacements appear → deliberately tap a bad trio
→ shake + counter unchanged → hint pulses a real set → pause stops the clock → resume continues →
finish a game and confirm 27 sets and a summary with a stored best time.

---

## 7. Milestones

| # | Deliverable | Acceptance |
|---|---|---|
| M1 | Engine + unit tests | All §6.1 tests pass; no Android imports in `engine/` |
| M2 | Card rendering + static board | Screenshot of all 81 cards in a grid looks correct; striped/open/solid and all shapes legible at real size |
| M3 | Playable loop | Tap/validate/replace/extra-deal/end-game all work; effects fire once; §6.2 tests pass |
| M4 | Start, summary, pause, persistence | Best times survive app restart; settings persist; rotation keeps the game |
| M5 | Polish | Accessibility pass with TalkBack, font scale 2.0 layout, reduced motion, colorblind palette; final screenshot set |

---

## 8. Risks and open questions

| Risk | Mitigation |
|---|---|
| Squiggle path looks wrong or blobby | Tune control points against a screenshot; keep geometry in one file so it's cheap to iterate |
| 15/18-card boards too cramped on short screens | Two size tiers + scroll fallback; verify on a 360×640 dp emulator profile |
| AGP 9.4.1 / compileSdk 37 toolchain quirks | Reuse the already-proven versions and config from the sibling app |
| Timer/effects duplicating on rotation | Effects over `SharedFlow`; clock derived from a start timestamp |
| "Set" is a trademarked name | Personal use is fine; rename the store listing before publishing |

Resolved decisions (2026-10-26):

1. **Timed "clear the deck" is the default**; Relaxed is available and remembered.
2. **Hints are hard-capped at 3 per game**, with no time penalty.
3. **No daily challenge in v1**; deferred to v1.1.

Implementation deviations from this document:

- Settings persist via `SharedPreferences` rather than DataStore. Five primitives and two
  best-time longs do not justify an async dependency and a cached warm-up path;
  SharedPreferences reads synchronously at startup and writes with `apply()`. Revisit only
  if settings grow into structured data.
- Audio is deferred past M4. Haptics ship in M3/M4; a tone generator is not worth the
  lifecycle complexity for a first version.


---

## 9. Implementation status (2026-10-26)

Shipped and verified on the `pi_phone` emulator (Android 16 / API 36):

| Area | State |
|---|---|
| M1 engine + tests | Done. 26 JVM tests, including the exhaustive 1,080-set check, invariants, extra-deal and hint-cap behaviour. |
| M2 card rendering | Done. Canvas-only diamonds, ovals, squiggles in solid/striped/open; no image assets. |
| M3 playable loop | Done. Tap/validate/replace/extra-deal/end-game, hint pulse, invalid-trio shake, haptics, timer. |
| M4 start/summary/pause/persistence | Done. Mode choice, best result survives relaunch, pause freezes the clock. |
| M5 polish | Partial. Launcher icon, colorblind palette, selection without colour, font-scale-safe header. TalkBack pass and reduced-motion still pending. |

Verified by script during implementation: a set was solved from `content-desc` and tapped
(counter 0 → 1, table back to 12), an invalid trio left the counter unchanged and charged the
penalty, hints capped at three (`No hints left`, button disabled), pause froze the clock, and a
full game finished at 25 sets with 6 cards left and a stored best time.

Deliberately not done: audio, daily challenge, process-death restore, instrumented UI tests,
multiplayer.
