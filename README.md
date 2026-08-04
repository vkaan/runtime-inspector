# RuntimeInspector

An Android library for detecting **potential runtime risks**. It observes an app through
Android's official lifecycle and callback APIs, records everything on a single ordered
timeline, derives a live view of the app's runtime state from it, and runs deterministic
risk rules over every event.

No bytecode instrumentation, no reflection — only official callback mechanisms.

## Features

- **Activity & Fragment lifecycle** — every transition, with per-instance identity and a
  configuration-change flag that distinguishes a rotation from a genuine background.
- **Fragment back stack** — push/pop events, plus depth tracked per Activity and measured from
  the `FragmentManager` rather than counted, so it survives rotation and Activity teardown.
- **Process lifecycle** — whole-app foreground/background, debounced so rotations don't
  register as false backgrounding.
- **Memory pressure** — `onTrimMemory` / `onLowMemory` levels.
- **Heap sampling** — Java heap usage measured at fixed lifecycle points (Activity
  created/destroyed, app foregrounded/backgrounded, memory trim), so samples are comparable.
- **Live instance tracking** — live Activity/Fragment counts and peaks, keyed by identity.
- **Network connectivity** — default-network availability and transport (Wi-Fi, cellular, …)
  via `registerDefaultNetworkCallback`.
- **Crash capture** — uncaught exceptions recorded on the timeline before the process dies,
  chaining (never replacing) the existing exception handler.
- **Configuration changes** — decoded into the fields that actually changed
  (`ORIENTATION`, `UI_MODE`, `LOCALE`, …) rather than a bare boolean.
- **Runtime timeline** — one ordered, bounded, thread-safe log of all of the above.
- **Runtime state** — a live summary derived from the timeline.
- **Risk Engine** — deterministic rules evaluated on every event, with severity levels,
  deduplication, and warnings via logcat and a public accessor.

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

The library's manifest declares `ACCESS_NETWORK_STATE` (a normal-level permission); manifest
merging adds it to the host automatically.

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
NETWORK AVAILABLE (WIFI)
ACTIVITY MainActivity#154959438 -> RESUMED
BACKSTACK PUSHED PersonDetailFragment in MainActivity#154959438
FRAGMENT PersonDetailFragment#192886170 -> RESUMED
HEAP used=14.2MB max=192.0MB (7%) on ACTIVITY_DESTROYED
MEMORY onTrimMemory(UI_HIDDEN)
CONFIG changed: ORIENTATION|SCREEN_SIZE
```

When a rule fires, a `RISK` line appears in the same stream (`Log.w`, or `Log.e` for
`ERROR` severity):

```
RISK [WARNING] RECREATION_MID_FLOW MainActivity#154959438 — Activity destroyed for a config
change while its back stack held 1 entries — in-flight callbacks and results can be lost;
verify state restoration.
```

Findings are also available programmatically:

```kotlin
val findings: List<Risk> = RuntimeInspector.risks()
```

## Risk rules

Every rule is a pure function of `(event, state before, state after)` — no clocks, no
randomness, no rule-local state. The same event sequence always produces the same findings,
so every rule is unit-testable without a device. Findings are deduplicated per rule + subject.

| Rule | Severity | Fires when |
|---|---|---|
| `STATE_LOSS` | ERROR | a Fragment is created while its host Activity is `STOPPED` — the signature of a commit after `onSaveInstanceState` |
| `MID_FLOW_CRASH` | ERROR / WARNING | an uncaught exception kills the app; ERROR if a flow was open |
| `MEMORY_PRESSURE` | ERROR / WARNING | `onTrimMemory(RUNNING_CRITICAL)` while foregrounded / heap over the configured ceiling (default 85%) |
| `NETWORK_LOSS` | WARNING / INFO | the default network is lost while foregrounded; WARNING if a flow was open |
| `NETWORK_FLAPPING` | WARNING | the network is lost 3 times (configurable) within 60s (configurable) — the link itself is unstable |
| `RECREATION_MID_FLOW` | WARNING | an Activity is destroyed by a config change while its back stack is non-empty |
| `ORPHAN_FRAGMENT` | WARNING | a Fragment is still alive 1s after its host Activity was destroyed |
| `ACTIVITY_LEAK` | WARNING | across 3 destroy-time heap samples: live Activity count flat, heap climbing ≥ 1MB per step |
| `DUPLICATE_SCREEN` | WARNING | two or more live instances of the same Activity class exist at once |
| `BACKSTACK_GROWTH` | WARNING | back stack depth reaches the configured ceiling (default 10) |
| `INTERRUPTED_FLOW` | INFO | the app is backgrounded while a back stack is non-empty |
| `SCREEN_OFF_MID_FLOW` | WARNING | the screen turns off while foregrounded with a flow open — idle timeout mid-interaction |
| `POWER_LOSS_MID_FLOW` | ERROR / WARNING | the device shuts down (ERROR) or reports low battery (WARNING) while a flow is open |

### Runtime state

Every event is folded into a `RuntimeState` as it arrives: foreground status, the current
screen, back stack depth per Activity, per-Activity lifecycle stages, live instance maps and
peaks, last heap sample plus a short destroy-time history, network availability, the last
memory trim level, and the fields of the last configuration change.

`RuntimeState` itself stays `internal`; the rules are its consumer, and `risks()` is the
public window over what they conclude.

### Configuration

```kotlin
RuntimeInspector.init(
    this,
    RuntimeInspector.Config(
        enabled = true,
        notifyOnRisk = true,      // status-bar notification per finding
        backStackCeiling = 10,    // BACKSTACK_GROWTH threshold
        heapPercentCeiling = 85,  // MEMORY_PRESSURE heap threshold (%)
        networkFlapCount = 3,     // NETWORK_FLAPPING: losses within the window (max 10)
        networkFlapWindowSeconds = 60,
    ),
)
```

Rule thresholds are per-host settings — different apps have legitimately different
navigation depths and memory profiles.

### Notifications

With `notifyOnRisk`, each finding is posted as a heads-up notification (`IMPORTANCE_HIGH`)
so it appears over the screen as it fires. Repeats **update the existing notification**
with a `×N` count rather than stacking, because the notification ID is derived from the
same rule + subject key used for deduplication. Logcat still receives every finding
regardless of this setting.

Android freezes a channel's settings once it has been created on a device, so `CHANNEL_ID`
carries a version suffix — changing importance requires shipping a new channel id, not
editing the existing one.

The library declares **no notification permission**, so nothing is added to the host's
merged manifest. On API 33+ that means notifications only appear if the host app already
holds `POST_NOTIFICATIONS`; on the Android 9–10 target fleet no permission is required.

## Architecture

```
Collectors ──emit──▶ Timeline ──reduce──▶ RuntimeState
                        │
                        └─(event, before, after)─▶ RiskEngine ──▶ RISK log lines + risks()
```

- **Collectors** register Android callbacks and translate them into `RuntimeEvent`s.
- **Timeline** assigns a monotonic sequence number to each event under a single lock, stores
  the most recent 500 in a ring buffer, logs it, and folds it into the state. Before/after
  state snapshots are captured inside the lock; rules run outside it, so a slow or broken
  rule can never block a collector.
- **RuntimeState** is produced only by a pure reducer, applied incrementally.
- **RiskEngine** runs every registered rule on every event, deduplicates findings, keeps the
  most recent 100, and logs each new one. A rule that throws is logged and skipped — a rule
  bug must never crash the host.

```
com/vkaan/runtimeinspector/
├── RuntimeInspector.kt          public entry point
├── collector/
│   ├── Collector.kt
│   ├── LifecycleCollector.kt
│   ├── ProcessLifecycleCollector.kt
│   ├── ComponentCallbacksCollector.kt
│   ├── ConnectivityCollector.kt
│   ├── CrashCollector.kt
│   └── HeapSampler.kt
├── rules/
│   ├── Risk.kt                  public finding type
│   ├── RiskRule.kt
│   ├── RiskEngine.kt
│   └── <one file per rule>
└── timeline/
    ├── RuntimeEvent.kt
    ├── Timeline.kt
    └── RuntimeState.kt
```

`RuntimeInspector` is the intended public surface — `init()`, `isInitialized`, `risks()`,
`Config` — plus `Risk`, the finding type it returns. `Timeline`, `RuntimeState`, the rules
and the collectors are `internal`.

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
- [x] Step 4 — Rules / anomaly detection layer
  - [x] FR-09 — Deterministic rules run through a Risk Engine
  - [x] FR-10 — At least 5 risk rules (10 shipped)
  - [x] FR-11 — Warnings produced on risk (logcat + `risks()`)
