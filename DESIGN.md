# Set — Design Document

**Version 0.2.1** · tag `v0.2.1` · package `com.example.set` · minSdk 26 / target 37

A single-player implementation of the card game **Set** for Android, written in
Kotlin with Jetpack Compose. This document is intended to be sufficient to
rebuild the app from scratch: it specifies the rules, the state machine, the
persistence format, every layout dimension, every colour, every animation and
its timing, and the traps that cost real debugging time while building it.

Where this document and the code disagree, the code is right and this document
is a bug. Numbers below were read from the source at tag `v0.2.1`.

---

## 1. Product summary

### 1.1 What the app is

One screen of twelve cards; the player finds a set of three, taps them, and the
cards are replaced from the deck. The deck is exhausted when 81 cards have been
placed. Two modes: **Timed** (clock, mistakes penalised) and **Relaxed** (no
clock, no penalty). Hints are capped at three per game. Progress survives
leaving the app; the best result per mode is kept.

No audio, no accounts, no network, no analytics. Single activity, three
surfaces (start, game, summary), no navigation library.

### 1.2 Non-goals (v1)

- Multiplayer, online leaderboards, accounts.
- Daily challenge (deferred to v1.1).
- Audio and haptics customisation beyond on/off.
- Tablet-specific layouts, foldable postures, landscape-optimised board.
- Instrumented UI tests (verification is scripted against a running emulator).
- Localisation beyond English.

### 1.3 Design principles actually applied

1. **The engine is pure Kotlin.** No Android imports, no clock, no I/O, no
   coroutines. Every rule is a pure function on an immutable state, so the
   rules are unit-testable and the same code runs in a JVM test.
2. **The table is a list of slots, not a list of cards.** A removed set leaves
   holes; replacements fill holes left-to-right so cards never move. Only a
   widened table (15/18 cards) compacts.
3. **Time is accumulated, never derived from timestamps.** Away time never
   counts, and process death cannot inflate a score.
4. **Every animation must be able to settle from any state.** State-driven
   animations snap to their resting value when their flag clears — see §11.1,
   the bug that produced persistent misalignment.
5. **Colour is never the only signal.** Selection, hints, "set found" and
   "invalid" all change shape/border as well as colour.

---

## 2. Rules

### 2.1 The deck

81 cards, the complete product of four independent attributes, three values
each (3⁴):

| Attribute | Values (enum order matters for the codec) |
|---|---|
| Number | `ONE`, `TWO`, `THREE` |
| Colour | `RED`, `GREEN`, `PURPLE` |
| Shape | `DIAMOND`, `SQUIGGLE`, `OVAL` |
| Shading | `SOLID`, `STRIPED`, `OPEN` |

The deck is generated, never authored: `fullDeck()` iterates number → colour →
shape → shading, then `shuffledDeck()` shuffles with a `Random` parameter so
tests can seed it.

### 2.2 What a set is

Three cards form a set **iff, for each of the four attributes independently**,
the three values are all the same or all different. Two-of-one-plus-one-of-another
never counts.

```
isSet(a,b,c) = ∀ attr ∈ {number,color,shape,shading}:
                 (a==b && b==c) || (a!=b && b!=c && a!=c)
```

Exhaustive property, asserted by `RulesTest`: over all C(81,3) = 85,320 triples
exactly **1,080** are sets.

### 2.3 The table and dealing

- `TABLE_SIZE = 12`. A new game shuffles, takes 12 for the table, keeps 69 in
  the deck, then calls `ensurePlayable()` (§2.4) in case the opening table is
  set-free.
- The table is addressed by **slot index**, `0..size-1`, row-major in a
  3-column grid. A slot holds `Card?`; `null` is a hole.
- When a set is found, its three slots become holes. Replacements are dealt
  **into those holes, left to right**, taking from the front of the deck, until
  the table is back to 12 or the deck runs out. No other card moves.
- If the table is currently **wider than 12** (15 or 18, from extra deals), a
  found set is removed by *compacting*: the holes are dropped instead of being
  refilled, taking the table back toward 12.
- If the deck is empty, found sets simply leave holes. The table shrinks as
  sets are found; a run can end with 0, 3, 6, 9 or 12 cards on the table.

### 2.4 Extra deal when the table has no set

After every resolution (`tap`) the app calls `ensurePlayable()`:

```
while (no set exists on the table) {
    if (deck is empty) -> GAME_OVER
    deal 3 cards onto the end of the table   // table may become 15, then 18
    count one extra-deal event
}
```

- Extra cards are **appended**, so the table grows 12 → 15 → 18. The board then
  has 4, 5 or 6 rows; the card size shrinks to fit (§8.3).
- Each event flashes the banner "No set here — dealing 3 more" for 2 s
  (`extraDeals` counter increments per event; the UI reacts to the change).
- The loop is guaranteed to terminate: either a set appears or the deck empties.

### 2.5 End of a game

The game ends when the deck is empty **and** the table holds no set. Because the
last cards can be undealable, a run finishes at **23–27 sets found**, never
necessarily 27. `perfect` is true iff `cardsOnTable == 0` at the end.

### 2.6 Modes, hints, mistakes, timing

| Concept | Rule |
|---|---|
| Timed | Clock runs while `started && phase == PLAYING`. |
| Relaxed | Clock runs too (it is shown nowhere), but **no penalty** is added. |
| Clock start | On the first tap or the first hint (`started = true`), not on the deal. |
| Clock tick | Coroutine, every 1000 ms, `elapsedSeconds + 1`. Accumulated — not wall time. |
| Mistake | A third tap that is not a set. `mistakes + 1`; Timed adds **+10 s** (`MISTAKE_PENALTY_SECONDS`). |
| Penalty | `penaltySeconds = if (TIMED) mistakes * 10 else 0`; `totalSeconds = elapsed + penalty`. |
| Hints | `MAX_HINTS = 3`, hard cap, no time penalty. A hint reveals one valid set by index. |
| Hint duration | Pulsing outline for 2500 ms, then cleared. |
| Pause | Freezes the clock; board stays visible behind a dialog. |

Formatting: `formatDuration` gives `"2:07"` — minutes unpadded, seconds
two-digit, negatives clamped to 0.

### 2.7 Best result

Stored per mode: `sets`, `seconds`, `perfect`. A result is better if it has more
sets; equal sets → **lower** seconds is better; equal both → perfect beats
imperfect. First result always counts. The start screen shows the best for the
currently selected mode; the summary shows a "New best" badge when beaten.

---

## 3. Vocabulary and invariants

| Term | Meaning |
|---|---|
| slot | Position in `GameState.table`; may be `null` (hole). |
| hole | A slot vacated by a removed set, awaiting a replacement. |
| dealing | Filling holes after a set is found (`dealtSlots`). |
| extra deal | Appending 3 cards because the table has no set (`extraDeals`). |
| resolving | The 520 ms window in which a found set is gold and still on the table. |
| gold blink | The 520 ms highlight before a found set is removed. |

**Invariants that must hold after every transition** (asserted in tests):

1. `cardsOnTable + deck.size + 3 * setsFound == 81`
2. All 81 cards appear at most once across table + deck (no duplicates).
3. `table.size == 12`, or `15`/`18` after extra deals; never anything else.
4. `setsFound ∈ 0..27`.
5. `selected.size <= 3`; `selected` contains no null slots.
6. `hintsUsed ∈ 0..3`.

---

## 4. Architecture

### 4.1 Module and file layout

One Gradle module, `:app`.

```
app/src/main/java/com/example/set/
├── MainActivity.kt          enableEdgeToEdge(), builds SettingsRepository + GameStore, sets SetTheme + SetApp
├── SetApp.kt                screen routing (START/GAME/SUMMARY), resume decision, gameKey
├── engine/                  PURE KOTLIN — no Android imports, no clock, no I/O
│   ├── Card.kt              Card data class + 4 attribute enums + description
│   ├── Deck.kt              fullDeck(), shuffledDeck(random)
│   ├── Rules.kt             isSet(), findSetIndices(), findSet()
│   ├── Scoring.kt           MAX_HINTS, MISTAKE_PENALTY_SECONDS, penaltySeconds, totalSeconds, formatDuration
│   └── GameState.kt         immutable state + every transition
├── data/
│   ├── GameCodec.kt         text codec for the in-progress game (pure Kotlin)
│   ├── GameStore.kt         SharedPreferences wrapper for one saved game
│   └── SettingsRepository.kt mode, haptics, colorblind, best results
├── game/
│   ├── GameViewModel.kt     state flow, clock, gold-blink/blink/clear jobs, result recording, save
│   └── GameScreen.kt        header, board, footer, dialogs, banner, haptics, lifecycle
├── start/StartScreen.kt     SET wordmark, mode choice, best, play/resume
├── summary/SummaryScreen.kt result, stats, play again / home
└── ui/
    ├── CardFace.kt          one card: geometry, shading, selection/hint/gold/shake
    ├── ShapePaths.kt        unit-box paths for diamond/oval/squiggle + aspect ratios
    ├── DealIn.kt            slide-in from the deck corner, z-lift
    └── theme/Theme.kt       light/dark schemes, card colours, SetTheme + LocalContentColor
```

Also: `app/src/main/res/values/strings.xml` (all user-visible text),
`values/themes.xml` and `values-night/themes.xml` (window background only),
`tools/gridcheck.py` (screenshot-based alignment check, not shipped in the APK).

### 4.2 Dependency rule

`engine` ← `data` ← `game/start/summary` ← `ui`. The engine must never import
`android.*`, `androidx.*` or `kotlinx.coroutines.*`. If a rule needs a `Context`,
it does not belong in the engine.

### 4.3 Data flow

```
tap(i) ──► GameViewModel.tap
             ├─ state.tap(i)  (pure; may report setsFound+1)
             ├─ if set found: publish goldSlots, wait 520 ms, then
             │     state.tap(i).ensurePlayable() → update() → clearDealtSlotsLater()
             └─ else: state.tap(i).ensurePlayable() → update() → clearDealtSlotsLater()
                                                 └─ if invalidSlots: clear after 380 ms

update(state) ──► StateFlow → GameScreen recomposes
             ├─ if GAME_OVER: store.clear(), record best once, publish GameResult
             └─ else: store.save(state)

clock coroutine (1 s) ──► withElapsed(+1) while started && PLAYING
```

### 4.4 Screen routing

`SetApp` holds no navigation library; three `when` branches:

- On cold start, `store.load()` decides: a valid saved game opens directly on
  **GAME**, otherwise **START**.
- `START` shows "Resume game" when a saved game exists, plus "New game" (which
  clears the save and starts fresh in the selected mode).
- `GAME` builds a `GameViewModel` keyed `"game-$gameKey"`; `gameKey` increments
  every time a new game starts, which forces a fresh ViewModel.
- `GAME_OVER` publishes a `GameResult` and moves to `SUMMARY`.
- `SUMMARY` → "Play again" (new game, same mode) or "Home".

`rememberSaveable` keeps `screen`, `activeMode`, `gameKey` across configuration
changes; the saved game itself lives in SharedPreferences, so a rotation or a
process death resumes from disk.

---

## 5. State model

### 5.1 `GameState` fields

| Field | Type | Meaning |
|---|---|---|
| `table` | `List<Card?>` | Slots; `null` = hole. |
| `deck` | `List<Card>` | Undealt cards, front = next. |
| `selected` | `List<Int>` | Currently tapped slots (0–3). |
| `setsFound` | `Int` | Completed sets. |
| `mistakes` | `Int` | Invalid trios submitted. |
| `hintsUsed` | `Int` | Hints spent. |
| `phase` | `PLAYING / PAUSED / GAME_OVER` | |
| `mode` | `TIMED / RELAXED` | |
| `elapsedSeconds` | `Long` | Accumulated play time. |
| `started` | `Boolean` | Clock started (first tap or hint). |
| `hintIndices` | `List<Int>?` | Slots currently pulsing as a hint. |
| `extraDeals` | `Int` | Cumulative extra-deal *events*; drives the banner. |
| `goldSlots` | `List<Int>` | Found set awaiting removal (gold/pop). |
| `invalidSlots` | `List<Int>` | Trio to shake. |
| `dealtSlots` | `List<Int>` | Slots filled by the latest deal (slide-in + z-lift). |
| `deals` | `Int` | Monotonic deal counter. Legacy: the animation no longer keys on it (§11.2); kept for the codec. |

Derived: `hintsRemaining = max(0, 3 - hintsUsed)`; `cardsOnTable = table.count { it != null }`;
`isResolving = goldSlots.isNotEmpty()`; `penaltySeconds`; `totalSeconds`.

### 5.2 `tap(index)` — exact algorithm

```
if phase != PLAYING            -> return this
if index !in table.indices     -> return this
if table[index] == null        -> return this
if isResolving                 -> return this        // gold blink in progress
if index in selected           -> deselect it; reset hint/invalid; return
if selected.size == 3          -> return this        // unreachable in practice

append index;
if size < 3 -> copy(selected, started=true, hintIndices=null, invalidSlots=[])

trio = selected.map { table[it]!! }
if !isSet(trio) -> copy(selected=[], mistakes+1, started=true,
                        hintIndices=null, invalidSlots=selected)

// valid set
holes = table with the three indices set to null
dealt = []

if holes.count { != null } >= 12:            // table was 15 or 18
    newTable = holes.filterNotNull()         // compact instead of refilling
else if deck.isNotEmpty():
    open  = 12 - holes.count { != null }
    take  = min(open, deck.size)
    fill the holes left-to-right with deck[0..take)
    dealt = those slot indices
    newTable = filled holes; newDeck = deck.drop(take)

return copy(table, deck, selected=[], setsFound+1, started=true,
            hintIndices=null, goldSlots=[], invalidSlots=[],
            dealtSlots=dealt,
            deals = deals + (if dealt non-empty) 1 else 0)
```

Note `dealtSlots` is whatever `tap` produced — the ViewModel retires it.

### 5.3 `ensurePlayable()`

As in §2.4. On game over it returns a state with `phase = GAME_OVER`,
`dealtSlots = []`, `goldSlots = []`, `invalidSlots = []`, `selected = []`.
Otherwise, if it dealt anything, `extraDeals` increases by the number of events
and `dealtSlots` holds all appended slots; `deals` bumps by exactly 1.

### 5.4 Other transitions

| Function | Effect |
|---|---|
| `hint()` | No-op unless PLAYING, not resolving, hints remain, and a set exists. Adds 1 to `hintsUsed`, sets `hintIndices`, `started = true`. |
| `clearHint()` | `hintIndices = null`. |
| `pause()` / `resume()` | PLAYING ↔ PAUSED. |
| `withElapsed(s)` | Sets `elapsedSeconds` (no-op when unchanged). |
| `newGame(mode, random)` | Shuffle, table = first 12, deck = rest, then `ensurePlayable()`. |

---

## 6. Timing and orchestration (ViewModel)

### 6.1 Constants

| Constant | Value | Where |
|---|---|---|
| `TABLE_SIZE` | 12 | `GameState` |
| `MAX_HINTS` | 3 | `Scoring` |
| `MISTAKE_PENALTY_SECONDS` | 10 | `Scoring` |
| `GOLD_BLINK_MILLIS` | 520 | `GameViewModel` |
| `INVALID_SHAKE_MILLIS` | 380 | `GameViewModel` |
| `DEAL_ANIMATION_MILLIS` | 700 | `GameViewModel` |
| Slide duration | 360 ms | `DealIn` |
| Gold pop delay + duration | 200 ms + 140 ms | `CardFace` |
| Shake cycle | 3 × (50 ms out + 50 ms back) + 50 ms settle = 350 ms | `CardFace` |
| Hint pulse | 700 ms, reverse, infinite | `CardFace` |
| Hint clear | 2500 ms | `GameViewModel` |
| Extra-deal banner | 2000 ms | `GameScreen` |
| Clock tick | 1000 ms | `GameViewModel` |
| Selection scale animation | 120 ms | `CardFace` |

Ordering that matters:

- `GOLD_BLINK (520) > pop end (200+140 = 340)` — otherwise the pop is cut off
  mid-animation by removal. There is only 180 ms of slack; do not lengthen the
  pop without lengthening the blink.
- `INVALID_SHAKE (380) > shake end (350)` — only 30 ms of slack. That tiny
  margin is exactly why the snap-back in §11.1 is required; frame jank pushes
  the clear into the animation.
- `DEAL_ANIMATION (700) > slide (360)` — 340 ms of slack, but the slide only
  starts after `onGloballyPositioned`, so the flag can still retire first on a
  slow frame. Again, the snap in §11.2 is required.

### 6.2 Gold blink sequence

1. Third tap of a valid trio: `_state.value = current.copy(selected = trio,
   goldSlots = trio, started = true)` — assigned **directly**, not through
   `update()`, so the transient blink is never written to disk.
2. `blinkJob` (previous one cancelled): `delay(520)`; then
   `resolved = current.tap(index).ensurePlayable()`; `update(resolved)`;
   `clearDealtSlotsLater(resolved)`.
3. Taps are ignored during the blink because `isResolving` is true.

### 6.3 Invalid trio

`update(resolved)` publishes `invalidSlots`; a job waits 380 ms and clears them
(guarded by `if (_state.value.invalidSlots.isNotEmpty())`). The UI shakes the
three cards and snaps them back when the flag clears.

### 6.4 Deal flag retirement

`clearDealtSlotsLater(state)` launches a job: `delay(700)`, then clear
`dealtSlots` if still non-empty. **It must be called on every path that sets
`dealtSlots`** — that omission was a real bug (§11.3).

### 6.5 Clock

A `viewModelScope` coroutine loops: `delay(1000)`; if
`started && phase == PLAYING`, publish `withElapsed(elapsed + 1)`.

### 6.6 Save and game over

`update()`:
- `GAME_OVER` → `store.clear()`; record best exactly once (`recorded` guard);
  compute `seconds = totalSeconds`, `perfect = cardsOnTable == 0`; build
  `GameResult(mode, setsFound, seconds, mistakes, hintsUsed, perfect, isNewBest)`.
- otherwise → `store.save(next)` on every transition (cheap, plain text).

### 6.7 Lifecycle hooks

- `GameScreen`'s back arrow and system back call `exit()` = `viewModel.leave()`
  (pause + save via `update`) then `onExit()`.
- A `LifecycleEventObserver` calls `viewModel.leave()` on `ON_STOP`, so Home,
  recents, or another app freezes the clock and persists progress.
- On resume from disc, `GameViewModel` forcibly clears transient UI state:
  `phase = PLAYING`, `selected = []`, `hintIndices = null`, `goldSlots = []`,
  `dealtSlots = []`. A game saved mid-blink therefore resumes with the trio
  still on the table and unselected (you tap it again); `setsFound` has not
  advanced, so nothing is lost.

---

## 7. Persistence

### 7.1 Saved game format (`set.game` / key `in_progress`)

Plain text, `;`-separated `key=value`, version-tagged:

```
v1;mode=TIMED;sets=3;mistakes=1;hints=0;elapsed=127;started=1;extra=0;deals=4;
table=0121,1020,-,2202,...;deck=1111,0000,...
```

- Card token = four attribute **ordinals** in enum order: number, colour, shape,
  shading. `1020` = TWO, RED, OVAL, SOLID.
- `-` is a hole.
- `deck` may be empty (`deck=`).
- The codec is pure Kotlin and round-trip unit-tested.

`decode` returns `null` (treated as "no saved game") unless all of:

| Check | Rule |
|---|---|
| version | first token is exactly `v1` |
| sets | `0..27` |
| table size | `12..18`, and if `> 12` then a multiple of 3 |
| invariant | `cardsOnTable + deck.size + 3*setsFound == 81` |
| uniqueness | no card appears twice across table + deck |
| required fields | `table`, `deck`, `sets`, `mistakes`, `hints`, `elapsed`, `started`, `extra`, `deals`, `mode` |

`phase` is **not** stored: a saved game always decodes as `PLAYING`, because
quitting always pauses and a paused game must be resumable.

### 7.2 Settings (`set.settings`)

| Key | Type | Default |
|---|---|---|
| `mode` | String enum name | `TIMED` |
| `haptics` | Boolean | `true` |
| `colorblind` | Boolean | `false` |
| `best_timed_sets` / `_seconds` / `_perfect` | Int/Long/Bool | absent |
| `best_relaxed_sets` / `_seconds` / `_perfect` | Int/Long/Bool | absent |

`SettingsRepository` exposes `mode`, `hapticsEnabled`, `colorblindPalette` as
Compose state and writes through synchronously (`apply()`).

---

## 8. UI specification

Density reference: the verification device is 1080 × 2400 px at 3× (360 × 800 dp).

### 8.1 Surfaces

```
START ──Play/Resume──► GAME ──game over──► SUMMARY
  ▲                     │                    │
  └────────Home─────────┴────Play again──────┘
```

`enableEdgeToEdge()` is on; each screen applies `systemBarsPadding()` and paints
`colorScheme.background` itself.

### 8.2 Start screen

Vertical `Column`, centred, `padding(horizontal = 24.dp, vertical = 32.dp)`.

1. **Wordmark**: `Row` of three `Text`s `S` `E` `T`, `displayLarge`, bold, each in
   `cardColor(RED/GREEN/PURPLE)` for the active palette and theme. Semantics
   merged with content description `@string/start_title` ("Set").
2. 12 dp spacer; tagline `bodyLarge`, `onSurfaceVariant`, centred:
   "Three cards. Every feature all the same, or all different."
3. 40 dp spacer.
4. **Mode options** (two): full-width `Row`, `RoundedCornerShape(16.dp)`,
   padding 16 × 14, border 2 dp `primary` when selected / 1 dp `outlineVariant`
   otherwise, background `primaryContainer` when selected /
   `surfaceVariant.copy(alpha = 0.45f)` otherwise. Left column: title
   `titleMedium` semibold + detail `bodySmall` `onSurfaceVariant`. Right:
   `RadioButton`. Semantics: `Role.RadioButton`, `selected`. Contents:
   - Timed — "Against the clock · mistakes cost 10 s"
   - Relaxed — "No timer, no penalty"
   12 dp between them.
5. 28 dp spacer; **best line** `bodyMedium` `onSurfaceVariant`:
   "No result yet" | "Best · N sets in m:ss" | "Best · perfect in m:ss".
6. 24 dp spacer; primary **Button** full width, 56 dp high: "Play", or
   "Resume game" when a save exists. Beneath it, when a save exists, a
   `TextButton` "New game".

### 8.3 Game screen

Root `Box` with background; `Column(systemBarsPadding)`:

**Header** (`Row`, min height 56 dp, horizontal padding 4 dp):
- `IconButton` back (`ArrowBack`, content description "Back").
- `weight(1f)` spacer.
- Centred `Column`: line 1 = "{sets} sets" (`titleMedium`, semibold) and, in
  Timed only, 16 dp spacer + `formatDuration(totalSeconds)` in monospace; line 2
  = "{deck.size} in deck" (`labelSmall`, `onSurfaceVariant`).
- `weight(1f)` spacer.
- `IconButton` overflow (`MoreVert`, "More") opening a `DropdownMenu` with:
  Pause/Resume · Haptics (with a check icon when on) · Colorblind palette ·
  How to play · Home.

**Board** — `BoxWithConstraints`, `fillMaxSize`, `padding(horizontal = 20.dp)`:

```
columns = 3
rows    = ceil(table.size / 3)
gap     = 12.dp
widthLimit  = (maxWidth  - gap*(columns-1)) / columns
heightLimit = (maxHeight - gap*(rows-1))  / rows / 1.4
cardWidth   = min(widthLimit, heightLimit).coerceAtLeast(64.dp)
cardHeight  = cardWidth * 1.4f          // portrait card
```

`Column` filled, `spacedBy(12.dp, Alignment.CenterVertically)`, horizontally
centred. For each row, a `Row(spacedBy(12.dp))` with exactly three children:
a `DealIn`+`CardFace` for a filled slot, or `Spacer(cardWidth, cardHeight)` for
a hole — **the spacer is mandatory**, otherwise a row with a hole shrinks and
its cards drift horizontally.

Z-order: `Row(zIndex(1f))` when the row contains any slot in `dealtSlots`, and
the `DealIn` box itself gets `zIndex(1f)` while `animate` is true, so a flying
card is drawn above every other card.

**Footer** (`Row`, padding 20 × 12, `spacedBy(12.dp)`, centred):
- `OutlinedButton` hint: "Hint · 3/2/1" or "No hints left"; enabled only when
  `hintsRemaining > 0 && phase == PLAYING`.
- `TextButton` "New game": asks for confirmation when `started`, otherwise
  starts immediately.

**Banner** over the board, top-centre, 8 dp from the top: pill
(`RoundedCornerShape(50)`), `primaryContainer`, `onPrimaryContainer`,
`labelLarge`, text "No set here — dealing 3 more", visible 2 s after each
extra-deal event.

**Dialogs**
- Paused (`phase == PAUSED`): title "Paused", body "The clock is stopped.",
  confirm "Resume", dismiss "Home". Not dismissible by tapping outside.
- New game confirmation: "Start a new game?" / "This game will be lost." /
  "Start over" / "Cancel".
- How to play: rules text (§16.2), single "Close".

Haptics (when enabled): `HapticFeedbackType.LongPress` on a found set and on a
mistake (each `LaunchedEffect` keyed on `setsFound` / `mistakes`, only when > 0
so the initial value does not buzz).

### 8.4 Summary screen

Centred `Column`, padding 24 × 32.
1. `titleMedium` `onSurfaceVariant`: "Perfect clear" if perfect, else "Deck cleared".
2. 8 dp; big number `displayLarge` bold + " sets" `titleMedium` bottom-aligned
   with 12 dp bottom padding.
3. If `isNewBest`: "New best" badge — `labelLarge` `onPrimaryContainer` on
   `primaryContainer`, pill radius, padding 12 × 6, 8 dp top.
4. 28 dp; stat rows (label `bodyLarge` `onSurfaceVariant`, value `bodyLarge`
   monospace medium, `SpaceBetween`, 10 dp apart):
   Time (Timed only) · Mistakes · Hints · Penalty `+Ns` (Timed **and**
   `mistakes > 0` only).
5. 16 dp; if not perfect: "{81 - 3*setsFound} cards left" (`bodySmall`,
   `onSurfaceVariant`). If Relaxed: "Relaxed game — no time recorded".
6. 32 dp; Button full width 52 dp "Play again"; `TextButton` "Home".

### 8.5 Card face

`Box` with `Modifier`:
```
graphicsLayer { scaleX = scaleY = scale; translationX = shake.dp.toPx() }
.shadow(if (isSelected || isGold) 4.dp else 1.dp, RoundedCornerShape(12.dp))
.clip(RoundedCornerShape(12.dp))
.background(colorScheme.surfaceContainerLowest)
.border(borderWidth, borderColor, RoundedCornerShape(12.dp))
.clickable(onClick)
.semantics(mergeDescendants = true) { contentDescription = card.description; selected; role = Button }
```

- `scale` = gold pop value when gold, else selection scale (1.02 when selected,
  1.0 otherwise, 120 ms tween).
- `borderColor`: gold → `Gold`; hinted → `Accent.copy(alpha = hintPulse)`;
  selected → `onSurface`; else `outlineVariant`.
- `borderWidth`: gold or hinted → 3 dp; selected → 2 dp; else 1 dp.
- Hint pulse alpha 0.35 → 1.0, 700 ms, reverse, infinite.
- Gold pop: after a 200 ms delay, `animateTo(1.06f)` over 140 ms; snaps to 1.0
  when `isGold` goes false.
- Shake: three cycles of +6 dp / −6 dp at 50 ms each way, then 50 ms to 0;
  snaps to 0 when `isInvalid` goes false.
- Both `Animatable`s are `remember(card)`, so a replacement card never inherits
  the outgoing card's pop or offset.

Inside, a `Canvas` with `padding(10.dp)`:

```
count        = card.number.value                     // 1..3
aspect       = 1.60 diamond | 1.75 oval | 1.55 squiggle
gapRatio     = 0.10
maxShapeWidth = canvasWidth * 0.94
heightBudget  = canvasHeight * 0.94
scaleFactor   = 1.0 | 0.9 | 0.8                      // by count
baseHeight    = min(maxShapeWidth / aspect,
                    heightBudget / (3*0.8 + 2*0.10*0.8))   // = /2.56
shapeHeight   = baseHeight * scaleFactor
shapeWidth    = shapeHeight * aspect
gap           = shapeHeight * 0.10
totalHeight   = count*shapeHeight + (count-1)*gap
top           = (canvasHeight - totalHeight)/2 ; left = (canvasWidth - shapeWidth)/2
stroke        = min(2.2.dp, shapeWidth*0.08)
```

Shapes are stacked **vertically**, as on the physical deck. Sizing hierarchy is
deliberate: one symbol is full size, two are 0.9, three are 0.8. The base size
is solved from the three-symbol case, which is what makes all three fit and
keeps the hierarchy consistent.

Shading rendering:
- `SOLID` — fill the path.
- `OPEN` — stroke the path at `stroke`.
- `STRIPED` — clip to the path, draw **9 vertical stripes**: slot = bounds.width/9,
  stripe width = 0.42 × slot, centred in each slot, overshooting top/bottom by
  2 px; then stroke the outline at `stroke`.

Shape geometry (`ShapePaths`, unit box 0..1, wider than tall):
- **Diamond**: rhombus (0,0.5) → (0.5,0) → (1,0.5) → (0.5,1), aspect 1.60.
- **Oval**: rounded rect with corner radius **0.45** (of the unit box), aspect
  1.75 — near-elliptical, keeping physical-card width.
- **Squiggle**: sampled band, 64 steps, amplitude 0.10, `halfHeight` 0.35,
  centre `0.5 + 0.10*sin(2πx)`, thickness taper
  `0.65 + 0.35*sqrt(max(0, sin(πx)))`, vertical extent `±(amplitude+halfHeight)`;
  aspect 1.55.

### 8.6 Theme

Light (`lightColorScheme`): primary `#5B4BD6`, onPrimary white, primaryContainer
`#E6E1FB`, onPrimaryContainer `#1D1647`, background/surface `#FAF7F2`,
onBackground/onSurface `#1B1B1F`, surfaceVariant `#F0EBE3`,
onSurfaceVariant `#6B6459`, outline `#D9D2C7`, outlineVariant `#D9D2C7`,
surfaceContainerLowest `#FFFFFF`, error `#B3261E`.

Dark (`darkColorScheme`): primary `#C4BBFF`, onPrimary `#1E1547`,
primaryContainer `#3B3070`, onPrimaryContainer `#E9E3FF`, background/surface
`#0E0D12`, onBackground/onSurface `#F2EFF7`, surfaceVariant `#2A2734`,
onSurfaceVariant `#D6D1DF`, surfaceContainerLowest `#211E2A`,
outline `#A9A2B8`, outlineVariant `#6B6478`, error `#FFB4AB`,
onError `#690005`.

Card colours:

| | Classic | Dark | Colourblind (Okabe–Ito derived) |
|---|---|---|---|
| Red | `#D93A2B` | `#FF7A68` | `#D55E00` (orange) |
| Green | `#1F9D55` | `#57D48C` | `#009E73` |
| Purple | `#7A4FD1` | `#B79CFF` | `#CC79A7` |

Gold accent `#C9A227`; hint accent `#5B4BD6`; dark vs light is decided by
`background.luminance() < 0.5f` (used both for card colours and the theme).

`SetTheme` applies `MaterialTheme(colorScheme)` **and**
`CompositionLocalProvider(LocalContentColor provides scheme.onBackground)`.
The provider is not optional — see §11.4.

Window background (`values/themes.xml`, `values-night/themes.xml`) is a
framework theme (`android:Theme.Material.Light.NoActionBar` /
`android:Theme.Material.NoActionBar`) with `windowBackground` =
`@color/surface` (`#FAF7F2` light, `#0E0D12` night), so the first frame does not
flash the wrong colour.

### 8.7 Deal animation

`DealIn(animate, dealKey, modifier, content)`:

- Origin: `corner = (-120f, windowHeight + 120f)` px, i.e. just off the
  bottom-left corner of the window.
- `progress` is an `Animatable` remembered on `dealKey` (the card itself), 0 when
  animating, 1 when not.
- `LaunchedEffect(dealKey, animate, positioned, windowSize)`: if `!animate` →
  snap to 1 and return; else, once `positioned`, `animateTo(1f, tween(360))`.
- `onGloballyPositioned` records `positionInWindow()`; the `graphicsLayer`
  translates by `(corner - topLeft) * (1 - progress)`, so every card converges
  from the same point regardless of screen size or layout changes.
- `zIndex(1f)` while animating.

### 8.8 Accessibility (current state)

- Every card exposes `contentDescription = card.description`, e.g.
  "two solid red diamonds" (shape pluralised only when count > 1), plus
  `selected` and `role = Button`.
- Mode options: `Role.RadioButton` + `selected`; the wordmark merges into "Set".
- Header text, hint label and buttons are real text nodes.
- Known gaps: no TalkBack pass, no reduced-motion handling (all animations run
  regardless of `Settings.Global.ANIMATOR_DURATION_SCALE`), no custom actions,
  no landscape/tablet verification.

---

## 9. Build and toolchain

| Item | Value |
|---|---|
| Gradle | 9.8.2 |
| AGP | 9.4.1 (bundled Kotlin 2.2.10 — do **not** add `kotlin-android`) |
| Compose compiler plugin | `org.jetbrains.kotlin.plugin.compose`, version = Kotlin 2.2.10 |
| Compose BOM | 2026.09.00 |
| activity-compose | 1.13.0 |
| lifecycle | 2.11.0 (`-viewmodel-compose`, `-runtime-compose`) |
| core-ktx | 1.19.1 |
| JUnit | 4.13.2 · coroutines-test 1.10.2 |
| Java | 17 source/target |
| SDK | compile 37, target 37, min 26 |

```
./gradlew testDebugUnitTest assembleDebug --console=plain
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.example.set/.MainActivity
```

Distribution: debug-signed APK attached to a GitHub Release per version
(`v0.2.1` → `Set-0.2.1.apk`). No release keystore yet; a build from a different
machine cannot update an install from this one.

---

## 10. Testing and verification

### 10.1 JVM tests (32, all green at v0.2.1)

| File | Tests | Covers |
|---|---|---|
| `engine/DeckTest.kt` | 4 | 81 unique cards, every 3⁴ combination present exactly once, seeded shuffle is a permutation, deterministic order. |
| `engine/RulesTest.kt` | 8 | `isSet` truth table per attribute, all-same/all-different, the 85,320-triple enumeration yielding exactly 1,080 sets, `findSetIndices` skipping holes. |
| `engine/GameStateTest.kt` | 16 | The 81-invariant across tap/ensurePlayable/newGame, holes filled left-to-right, other cards static, compaction of widened tables, extra-deal growth 12→15→18, game over when deck empty and set-free, hint cap and no-penalty, mistake accounting, pause/resume, elapsed, selection limits. |
| `data/GameCodecTest.kt` | 4 | Round trip including holes and an empty deck, and rejection of inconsistent payloads (duplicate card, wrong total). |

Run: `./gradlew testDebugUnitTest`.

### 10.2 Scripted emulator verification

The emulator cannot run in WSL (no `/dev/kvm`); it runs on Windows and adb is
reached over TCP. In WSL, source the environment and use the helper:

```bash
. ~/.config/android-env.sh
~/.config/android-emulator.sh start|stop|restart|status
adb devices                       # emulator-5554
```

Card-level automation reads the accessibility tree:

```bash
adb shell uiautomator dump /sdcard/d.xml
adb shell cat /sdcard/d.xml       # content-desc="two solid red diamonds" bounds="[x1,y1][x2,y2]"
```

Parsing `content-desc` into (number, shading, colour, shape) lets a script find
a real set on screen, tap the three centres, and assert the HUD/counter. A
`uiautomator dump` costs roughly a second, which sets the pace of scripted play.

### 10.3 Alignment check (`tools/gridcheck.py`)

Decodes a screencap with the Python standard library only (zlib + PNG
unfiltering), builds a mask of pixels differing from the background colour by
more than 24 in summed RGB (background sampled at the top-left corner, so it
works in both themes), extracts connected components, keeps components that are
card-sized (area > 1500 scaled px, width/height > 120 px, aspect between 0.45
and 2.2), and clusters their left/top/right edges.

```
PASS/FAIL  +  card count, column count, row count, edge clusters
```

A settled board must yield exactly 3 columns and ≤ 4 rows (4–6 rows when the
table is 15/18). This is measured, not eyeballed: it caught the 14 px shake
drift which is easy to miss in a screenshot.

### 10.4 Evidence for v0.2.1

- Invalid trio, 4 rounds before the fix: 3 cards 14 px left of their column
  (6 distinct x-clusters). After the fix: 4/4 PASS with exactly
  `lefts=[54,390,726]`, `tops=[282,738,1196,1652]`.
- Ten deals + two invalid trios after the fix: 10/10 and 2/2 PASS.
- Deal-timer control: with `DEAL_ANIMATION_MILLIS = 120` (below the 360 ms
  slide) the pre-fix build fails with 9 cards on the grid plus a component at
  x = 0; the fixed build passes. The timer was restored to 700 afterwards.
- Mid-flight frame still fails the grid check (cards in motion) and shows the
  flying card drawn above the board, proving the animation and z-lift are intact.
- Dark theme: header ("N sets", timer, back, overflow) and summary figures render
  in `onBackground`; screenshots checked in both themes.
- 32 JVM tests green.

---

## 11. Known pitfalls — bugs to watch out for

These are the traps that actually occurred or are structurally likely. Each has
a symptom, a cause, and the rule that prevents it.

### 11.1 Timer-cleared flags freeze `Animatable`s

**Symptom:** after a few moves some cards sit a few dp off their slot, in
different directions, permanently. Measured: three cards 14 px left.

**Cause:** an animation is started when a flag becomes true, but the flag is
cleared by a timer (380 ms for `invalidSlots`). The `LaunchedEffect` keyed on
`(shakeKey, isInvalid)` re-runs with `isInvalid == false` and does **nothing**,
so the `Animatable` keeps its mid-animation value forever. The shake takes
350 ms; the clear lands at 380 ms while `invalidSlots` is set mid-composition, so
frame jank loses the race often enough to hit 4/4 scripted rounds.

**Rule:** every state-driven `Animatable` needs an `else` branch that snaps to
its resting value (`shake.snapTo(0f)`, `progress.snapTo(1f)`,
`goldPop.snapTo(1f)`). Also `remember(card)` the animatables so a replacement
card cannot inherit the previous card's value at the same composition slot.

### 11.2 Global counters must not key per-item animations

**Symptom:** every deal restarts every slide; cards appear to re-fly.

**Cause:** `DealIn(dealKey = state.deals)` — one counter for the whole board, so
a new deal recreates every card's `Animatable` from 0.

**Rule:** key per-card animations on the card identity
(`state.table[index].hashCode()`; cards are unique within a game).

### 11.3 Retire the deal flag on every path

**Symptom:** the lifted z-order never returns to normal; `dealtSlots` is
permanently non-empty, so a shortened retire timer never fires and the freeze in
§11.1 looks unreproducible.

**Cause:** `tap()`'s valid-set path resolved inside `blinkJob` and called
`update()` but not `clearDealtSlotsLater()`. Only the invalid path did.

**Rule:** whatever sets `dealtSlots` must schedule its retirement, and the timer
must be longer than the slide plus its start-up (700 > 360 ms).

### 11.4 `LocalContentColor` defaults to black

**Symptom:** in dark theme the header ("N sets", timer, back arrow, overflow
menu) and the summary figures (`25`, `Mistakes`, `Hints`) are invisible.

**Cause:** `MaterialTheme` does not provide `LocalContentColor`; only `Surface`
does. Uncoloured `Text`/`Icon` therefore fall back to black — fine on the cream
light background, invisible on `#0E0D12`.

**Rule:** provide `LocalContentColor = onBackground` in `SetTheme`. Do not paper
over it by colouring every `Text`; components that need their own ink (buttons,
dialog text) already resolve it through their own `Surface`/content colour.

### 11.5 `graphicsLayer` translation is invisible to layout APIs

**Symptom:** `uiautomator` bounds, `onGloballyPositioned` and Compose test
assertions all report cards as perfectly aligned while the screen shows them
displaced.

**Cause:** `graphicsLayer` transforms drawing, not layout; accessibility bounds
come from layout. The deal slide and the shake are both `graphicsLayer`.

**Rule:** verify visual position from pixels (`tools/gridcheck.py`), not from
node bounds. Conversely, do not use bounds to prove an animation happened.

### 11.6 Rows with holes collapse without spacers

**Symptom:** cards in a row shift horizontally whenever a hole exists.

**Cause:** a `Row` wrapping its content is narrower with two children than with
three, and the parent `Column` centres it.

**Rule:** always emit a `Spacer(Modifier.size(cardWidth, cardHeight))` for a
null slot. The board keeps a fixed 3-column geometry at all times.

### 11.7 `sqrt(sin(PI))` is NaN in Float

**Symptom:** an entire card renders blank (no shapes at all).

**Cause:** `sin(Float.PI)` is a hair negative; `sqrt` of that is `NaN`, and a
`NaN` in a path coordinate poisons the whole path.

**Rule:** `sin(PI * x).coerceAtLeast(0f)` before `sqrt`, as in `ShapePaths`.

### 11.8 Rounded-rect corner radius versus size

**Symptom:** the oval degenerates into a pill or a rectangle at some sizes.

**Cause:** `RoundRect` with a corner radius ≥ half the smaller dimension is
clamped/degenerate; the oval uses 0.45 of the unit box, which is correct only
because the shape is wider than tall and the same radius applies to both axes.

**Rule:** keep the radius below half the *smaller* dimension, and verify at the
three-symbol size (the smallest a shape ever gets).

### 11.9 Compose API traps hit during development

- `AnimatedVisibility` cannot be called from a composable that receives both
  `ColumnScope` and `BoxScope`; keep each on one scope or use a plain `if`.
- `var x by mutableStateOf(...)` plus `fun setX(...)` generates a clashing
  JVM signature; name mutators `updateX`.
- `LaunchedEffect` keys decide cancellation. Adding `animate` to the key list is
  what allows the snap branch to run; omitting it leaves the slide running
  against a stale flag.
- `remember` inside a reused composition slot survives a data change: key
  per-item state on the item (`remember(card)`), not on position.

### 11.10 Persistence traps

- Do **not** persist the gold-blink state. `goldSlots` is assigned directly to
  `_state.value`, bypassing `update()`/`store.save()`. Routing it through
  `update()` would store a table where a found set is still present with
  `setsFound` already incremented, breaking the 81 invariant on the next decode.
- `phase` is deliberately not saved; a saved game is always resumable.
- Quitting during the 520 ms blink loses only the blink: the trio is still on the
  table, unselected, and must be tapped again. That is intentional and
  consistent, not a bug to "fix" by persisting `goldSlots`.
- `GameCodec.decode` must validate the invariant and uniqueness; a partial or
  hand-edited save otherwise crashes the board by drawing duplicate cards.
- `ensurePlayable()` is called after every resolution — forgetting it can leave
  a set-free table, which the rules forbid.

### 11.11 Rule and accounting traps

- A run ends at 23–27 sets. Do not assert 27 anywhere in UI or tests.
- The header shows **deck size**, not cards on table (changed in 0.2.0); the
  summary's "N cards left" is `81 - 3*setsFound`, which counts table + deck.
- A found set on a widened table compacts instead of refilling — otherwise the
  table would stay at 15/18 with holes and the card size would keep shrinking.
- The clock is accumulated. Deriving it from wall time counts time spent away.
- Mistakes are penalised in Timed only; the summary hides Time/Penalty in Relaxed.

### 11.12 Environment and tooling traps (this machine)

- **Never start an adb daemon in WSL.** WSL relays listening ports into Windows
  localhost, so a Linux adb on 5037 shadows the real server and the emulator
  registers with the wrong one (symptom: both sides show no devices while
  `emulator-5554` is clearly running). `ADB_SERVER_SOCKET` is pinned to
  `tcp:127.0.0.1:1` when the Windows server is down so adb fails in 0.5 s.
- The emulator cannot run in WSL (no `/dev/kvm`); it runs on Windows via
  `~/.config/android-emulator.sh` (AVD `pi_phone`, API 36, 1080 × 2400).
- Two SDKs exist: Linux (`~/Android/Sdk`, for Gradle) and Windows
  (`C:\Users\Li\AppData\Local\Android\Sdk`, for the emulator). Never point
  Gradle at the Windows one — those binaries are `.exe`.
- Keep sources, Gradle caches and build output on ext4 under `~`; `/mnt/c` is
  slow and file watching is unreliable there.
- A `uiautomator dump` is ~1 s; scripted "fast" play is still limited to roughly
  one move per 1.5 s. Timing-sensitive bugs need the constants shortened (and
  then restored) rather than faster tapping.

---

## 12. Version history

| Tag | Changes |
|---|---|
| `v0.1.0` | First playable: engine + 81-invariant, canvas cards, tap/validate/replace, extra deal, hints, timer, pause, best result, start/summary. |
| `v0.1.1` | Auto-save after every action and on `ON_STOP`, resume on launch; slot-stable replacement dealing (no reflow); gold blink before removal; deal slide-in; deck counter in the header; physical-card shapes (wide diamond/oval/squiggle, vertical stacking, 9 stripes); 1.0/0.9/0.8 symbol hierarchy; near-elliptical oval. |
| `v0.2.0` | Gold outline + 6 % pop; per-card invalid shake (board no longer moves); deal from a bottom-left deck origin; high-contrast dark theme; "Timed" naming; SET wordmark in card colours. |
| `v0.2.1` | Fix: shake/deal/row-z-order animations froze when their flags retired (cards drifted 6 dp – full slide, permanently); the valid-set path never retired `dealtSlots`; dark theme rendered uncoloured text and icons in black. Adds `tools/gridcheck.py`. |

---

## 13. Deliberate deviations

- **SharedPreferences, not DataStore.** Five primitives and one best-result
  triple do not justify an async dependency; reads are instant at startup,
  writes are `apply()`. Revisit only if settings grow.
- **No navigation library.** Three surfaces and a `when`; `rememberSaveable`
  holds route and `gameKey`. Back is handled explicitly (`BackHandler` + the
  header arrow), and both route through the same `exit()` that pauses and saves.
- **`GameState` carries `deals`, a counter the UI no longer uses** for animation
  keys (0.2.1). It remains for codec compatibility and as an event counter.
- **Timed is the default mode**, persisted; the saved game's mode wins over the
  setting when resuming.
- **The clock ticks in the ViewModel, not in `GameState`**, because the engine
  takes no clock; the tick is an explicit `withElapsed` transition.

---

## 14. Open work

| Item | Notes |
|---|---|
| Audio | Not started. Placeholder for deal, set found, mistake. |
| TalkBack pass | Content descriptions exist; grouping, focus order, and live-region announcements for "set found"/"deck cleared" are unverified. |
| Reduced motion | Animations ignore the system animation scale. Gate the slide, pop, shake and pulse on `Settings.Global.ANIMATOR_DURATION_SCALE`. |
| Daily challenge | Deferred to v1.1: seeded deal by date, one attempt, compare against a target. |
| Release signing | Debug-signed APKs only. A release keystore plus `signingConfigs` and a CI build would make updates portable. |
| Instrumented tests | Verification is scripted against a running emulator; Compose UI tests would cover taps/semantics in CI. |
| Tablet/landscape | Board math is resolution-independent, but only 1080 × 2400 portrait is verified. |

---

## 15. Rebuild checklist

A from-scratch implementation that follows this document can be checked in this
order. Each step should be verifiable before moving on.

1. **Engine**: generate 81 cards; `isSet`; `findSetIndices`. Assert exactly 1,080
   sets over 85,320 triples.
2. **State**: `newGame` (12 + 69), `tap` with holes filled left-to-right,
   compaction on widened tables, `ensurePlayable` loop, game over, the 81
   invariant after every transition. 16 tests' worth of behaviour.
3. **Codec**: round-trip and reject inconsistent payloads.
4. **Board UI**: fixed 3-column grid with spacers for holes, size math from
   `min(widthLimit, heightLimit)`, portrait 1.4 ratio, 64 dp floor. Verify by
   screenshot that holes do not shift cards.
5. **Cards**: three shape paths, three shadings, 1/0.9/0.8 sizing, selection +
   hint + gold + shake states, `contentDescription` per card.
6. **Loop**: gold blink 520 ms → resolve → slide 360 ms → retire at 700 ms;
   invalid shake 350 ms → clear at 380 ms with snap.
7. **Persistence**: save on every update, pause on `ON_STOP`, resume on launch,
   clear on game over.
8. **Screens**: start (mode, best, resume), summary (stats, new best), pause and
   confirm dialogs.
9. **Theme**: light/dark tokens, card colour variants, `LocalContentColor`.
10. **Verify**: scripted play with `uiautomator`, `tools/gridcheck.py` after 10
    deals and after each invalid trio, dark and light screenshots, JVM tests.

Acceptance for a release: 32 tests green; every settled board passes the grid
check; no card left displaced after shake, deal, hint, pause/resume or rotation;
header and summary legible in both themes; a game survives Home and process
death; best result persists.

---

## 16. Appendix

### 16.1 Card description format

`"{number} {shading} {colour} {shape}"`, shape pluralised when number ≠ one:
`one solid red diamond`, `two striped green ovals`, `three open purple
squiggles`. Reading order matters for scripted testing: number, shading, colour,
shape.

### 16.2 Strings (complete)

```
app_name            Set
start_title         Set
start_tagline       Three cards. Every feature all the same, or all different.
mode_timed          Timed
mode_timed_detail   Against the clock · mistakes cost 10 s
mode_relaxed        Relaxed
mode_relaxed_detail No timer, no penalty
start_play          Play
action_resume_game  Resume game
action_new_game_start New game
start_best          Best · %1$d sets in %2$s
start_best_perfect  Best · perfect in %1$s
start_best_none     No result yet
header_sets         %1$d sets
header_in_deck      %1$d in deck
action_back         Back
action_more         More
action_hint_left    Hint · %1$d
action_hint         Hint
action_new_game     New game
action_pause        Pause
action_resume       Resume
action_home         Home
action_play_again   Play again
pause_title         Paused
pause_body          The clock is stopped.
new_game_title      Start a new game?
new_game_body       This game will be lost.
new_game_confirm    Start over
cancel              Cancel
no_set_banner       No set here — dealing 3 more
hint_none           No hints left
summary_perfect     Perfect clear
summary_cleared     Deck cleared
summary_sets        sets
summary_time        Time
summary_mistakes    Mistakes
summary_hints       Hints
summary_penalty     Penalty
summary_leftover    %1$d cards left
summary_best_badge  New best
summary_relaxed_note Relaxed game — no time recorded
settings_haptics    Haptics
settings_colorblind Colorblind palette
how_to_play         How to play
close               Close
```

How-to-play body:

> A set is three cards where each of the four features — number, colour, shape
> and shading — is either the same on all three cards or different on all three.
>
> Two of one and one of another is never a set. Tap three cards to submit.
>
> If the table has no set at all, three extra cards are dealt automatically.
> Hints are limited to three per game.

### 16.3 Saved-game example with a hole

```
v1;mode=TIMED;sets=1;mistakes=0;hints=1;elapsed=64;started=1;extra=0;deals=1;table=0000,-,0122,1021,2000,1111,0222,2010,1201,2211,0022,2100;deck=0110,1012,...
```

The `-` at index 1 marks the hole left by the removed set (replacement pending
only if the deck were empty; here it is illustrative).

### 16.4 Colour cheat-sheet for the card renderer

`cardColor(colour, colorblind, dark)` — colourblind wins over dark; otherwise
dark uses the lifted variants. Never hardcode a card colour in `CardFace`;
always call this function so the palette setting and theme agree.
