# RuntimeInspector

An Android library for detecting **potential runtime risks**. It observes an app through
Android's official lifecycle and callback APIs, records everything on a single ordered
timeline, and derives a live view of the app's runtime state from it.

No bytecode instrumentation, no reflection — only official callback mechanisms.

## Features

- **Activity & Fragment lifecycle** — every transition, with per-instance identity and a
  configuration-change flag that distinguishes a rotation from a genuine background.
- **Fragment back stack** — push/pop events, plus depth tracked per Activity and measured from
  the `FragmentManager` rather than counted, so it survives rotation and Activity teardown.
- **Process lifecycle** — whole-app foreground/background, debounced so rotations don't
  register as false backgrounding.
- **Memory pressure** — `onTrimMemory` / `onLowMemory` levels.
- **Configuration changes** — decoded into the fields that actually changed
  (`ORIENTATION`, `UI_MODE`, `LOCALE`, …) rather than a bare boolean.
- **Runtime timeline** — one ordered, bounded, thread-safe log of all of the above.
- **Runtime state** — a live summary derived from the timeline.

## Requirements

- `minSdk` 24
- Kotlin

## Installation

As a project module:

```kotlin
dependencies {
    implementation(project(":runtimeinspector"))
}
```

Or from a built AAR (`./gradlew :runtimeinspector:assembleRelease`):

```kotlin
dependencies {
    implementation(files("libs/runtimeinspector-release.aar"))
    implementation("androidx.lifecycle:lifecycle-process:2.9.0")
}
```

> A raw AAR does not carry transitive dependencies, so `lifecycle-process` must be declared
> by the host app. Without it, `ProcessLifecycleOwner` throws `NoClassDefFoundError` during
> `init()`.

## Usage

Initialize from `Application.onCreate()` — **not** from an Activity, or the first Activity's
`CREATED` event and all Fragment events will be missed:

```kotlin
class DemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RuntimeInspector.init(this)
    }
}
```

Collection starts automatically. Events are logged under the tag `RuntimeInspector`:

```
PROCESS app -> FOREGROUNDED
ACTIVITY MainActivity#154959438 -> RESUMED
BACKSTACK PUSHED PersonDetailFragment in MainActivity#154959438
FRAGMENT PersonDetailFragment#192886170 -> RESUMED
MEMORY onTrimMemory(UI_HIDDEN)
CONFIG changed: ORIENTATION|SCREEN_SIZE
```

### Runtime state

Every event is folded into a `RuntimeState` as it arrives: whether the app is in the
foreground, the current screen (the resumed Fragment when it belongs to the resumed Activity,
otherwise the Activity), back stack depth per Activity, the last memory trim level, and the
fields of the last configuration change.

`RuntimeState` is `internal` and has no accessor yet. The rules layer is its intended
consumer, and exposing a shape that is still moving would freeze it too early. Until then the
event log above is the observable surface.

### Configuration

```kotlin
RuntimeInspector.init(this, RuntimeInspector.Config(enabled = false))
```

## Architecture

```
Collectors ──emit──▶ Timeline ──reduce──▶ RuntimeState
```

- **Collectors** register Android callbacks and translate them into `RuntimeEvent`s.
- **Timeline** assigns a monotonic sequence number to each event under a single lock, stores
  the most recent 500 in a ring buffer, logs it, and folds it into the state.
- **RuntimeState** is produced only by a pure reducer, applied incrementally — the bounded
  buffer evicts old events, so the state cannot be recomputed from the buffer alone.

```
com/vkaan/runtimeinspector/
├── RuntimeInspector.kt          public entry point
├── collector/
│   ├── Collector.kt
│   ├── LifecycleCollector.kt
│   ├── ProcessLifecycleCollector.kt
│   └── ComponentCallbacksCollector.kt
└── timeline/
    ├── RuntimeEvent.kt
    ├── Timeline.kt
    └── RuntimeState.kt
```

`RuntimeInspector` is the intended public surface — `init()`, `isInitialized`, `Config`.
`Timeline`, `RuntimeState` and the collectors are `internal`. `RuntimeEvent` is currently
public but nothing public hands one out; whether it is exposed deliberately or tightened
depends on what the rules layer needs.

## Building

```bash
./gradlew :runtimeinspector:assembleRelease
```

Output: `runtimeinspector/build/outputs/aar/runtimeinspector-release.aar`

## Progress

- [x] Step 1 — Library module and initialization
- [x] Step 2 — Collector layer: Activity & Fragment lifecycle events (+ back-stack, per-event instanceId & config-change flag)
- [x] Step 3 — Runtime timeline & state
  - [x] FR-05 — Process lifecycle events
  - [x] FR-06 — Memory & configuration callbacks
  - [x] FR-07 — Runtime timeline built from collected events
  - [x] FR-08 — Runtime state derived from the timeline
- [ ] Step 4 — Rules / anomaly detection layer
