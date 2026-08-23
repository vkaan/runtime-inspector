# RuntimeInspector

A tool for detecting **potential runtime risks** in an Android app. It observes the app
through Android's official lifecycle and callback APIs, records everything on a single
ordered timeline, derives a live view of the app's runtime state from it, and runs
deterministic risk rules over every event.

It ships as two pieces:

- **`runtimeinspector`** — the AAR that goes inside the app under test. It only collects.
  Activity, Fragment, heap and lifecycle signals exist only inside the observed
  process, so this half has to live there.
- **`app`** — an installable APK that runs a foreground service. It holds the timeline, the
  rules, the findings and the notifications, and it pulls the card service log from the
  platform.

The app under test writes two lines — `RuntimeInspector.init(this)` as it starts and
`RuntimeInspector.unbind()` as it closes — and the AAR binds to that service on its own.
Events cross the process boundary over AIDL. The unbind is what tells the service the session
is over, so the log gets pulled and the rules run without anyone reaching a screen.

No bytecode instrumentation, no reflection — only official callback mechanisms, plus Token's
platform API for the card service log.

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
- **Configuration changes** — decoded into the fields that actually changed
  (`ORIENTATION`, `UI_MODE`, `LOCALE`, …) rather than a bare boolean.
- **Card service state** — a separate app's transaction state, tracked by pulling the
  platform log dump and matching its lines against patterns, so an interruption in the host
  app can be correlated with an open transaction in another process.
- **Runtime timeline** — one ordered, bounded, thread-safe log of all of the above.
- **Runtime state** — a live summary derived from the timeline.
- **Risk Engine** — deterministic rules evaluated on every event, with severity levels,
  deduplication, and warnings via logcat and a public accessor.

## Requirements

- `minSdk` 24 for the library, 25 for the inspector app (Token's wrapper requires it)
- Kotlin
- Token's `TSystemWrapper` AAR in `app/libs/` — it is theirs to distribute, so it is not in
  this repo. Without it the `:app` module will not build.

## Setup

**1. Build both artifacts.**

```bash
./gradlew :runtimeinspector:assembleRelease :app:assembleDebug
```

The AAR lands in `runtimeinspector/build/outputs/aar/`, the APK in
`app/build/outputs/apk/debug/`. The APK is a debug build because a release APK is unsigned
and will not install without a keystore.

**2. Install the inspector app and open it once.**

```bash
adb install -r app-debug.apk
```

Opening it matters: an app that has never been launched sits in Android's *stopped state*,
where it receives no `BOOT_COMPLETED` and cannot be bound by another app. After the first
launch the service starts on every boot on its own.

**3. Add the AAR to the app under test.**

```kotlin
dependencies {
    implementation(files("libs/runtimeinspector-release.aar"))
    implementation("androidx.lifecycle:lifecycle-process:2.9.0")
    implementation("androidx.fragment:fragment:1.7.1")
}
```

> A raw AAR does not carry transitive dependencies, so these must be declared by the host
> app. Without `lifecycle-process`, `ProcessLifecycleOwner` throws `NoClassDefFoundError`
> during `init()`.

**4. Initialize from `Application.onCreate()`** — not from an Activity, or the first
Activity's `CREATED` event and all Fragment events will be missed:

```kotlin
class HostApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RuntimeInspector.init(this)
    }
}
```

That one call binds to the inspector service and starts collecting once connected. The host
declares no service, no permission and no manifest entry — the library's manifest carries
the `<queries>` entry that lets the host see the inspector app, and manifest merging adds it.

**5. Unbind when the app closes** — from the same process that called `init()`, at the app's
own shutdown point:

```kotlin
RuntimeInspector.unbind()
```

This is the trigger for the whole analysis: the service sees the binding drop, pulls the
platform log dump and runs the card service rules on it. Do not put it in an Activity's
`onDestroy()` — that fires on every rotation. If the host process dies without calling it,
the service is told anyway and pulls all the same, just later.

**6. Watch the output.**

```bash
adb logcat -s RuntimeInspector
```

In Android Studio's Logcat tab, the same filter is `tag:RuntimeInspector`. Both processes
log under this one tag.

`Bound to the inspector service.` means the binding worked. `Inspector service not found`
means the inspector app is not installed, or has never been opened.

Events look like this:

```
PROCESS app -> FOREGROUNDED
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
RISK [WARNING] ORPHAN_FRAGMENT PersonDetailFragment#192886170 — PersonDetailFragment still alive
over 1s after its host Activity was destroyed — something is holding a reference to it
(likely leaked).
```

Findings are also available programmatically — **in the inspector app's process**, which is
where the rules run:

```kotlin
val findings: List<Risk> = RuntimeInspector.risks()
```

Calling this in the app under test returns an empty list. Its collectors write straight to
the binder, so its own timeline never sees an event.

### Pulling the card service log

```kotlin
RuntimeInspector.inspect()
```

Sends the request across the binder; the service calls Token's `getLog`, reads the dump and
runs the card service rules over it. `unbind()` does the same at the end of a session, which
is the normal path — `inspect()` is for a check partway through. The inspector app's own
screen also has an **İncele** button (a force read of the whole tail) and a **Yenile** button
that just re-renders the current findings, so the platform call can be tried with no host app
at all, and `am startservice … -a com.vkaan.runtimeinspector.INSPECT` does the same from adb.
A **Temizle** button (with an inline confirmation) drops the shown findings and baselines the
log buffer at that point, so the next İncele reports only the lines that arrive afterwards.

The screen itself lists the findings as tap-to-expand cards — first/last seen, sequence and
occurrence count — each with a **Daha fazla bilgi** button that opens a per-rule Turkish
explanation (`RuleHelp.kt`).

## Risk rules

Every rule is a pure function of `(event, state before, state after)` — no clocks, no
randomness, no rule-local state. The same event sequence always produces the same findings,
so every rule is unit-testable without a device. Findings are counted per rule + subject, where
the subject is a stable name (an Activity or Fragment class) rather than a particular instance —
so twenty rotations of the same screen are one finding with `occurrences = 20`, not twenty
findings. The instance the finding first happened to is kept alongside it in `instanceId` and
still shows up in the log line as `Subject#instanceId`.

| Rule | Severity | Fires when |
|---|---|---|
| `FRAGMENT_ADDED_WHILE_STOPPED` | ERROR | a Fragment is created while its host Activity is `STOPPED` — the signature of a commit after `onSaveInstanceState` |
| `LOW_MEMORY_WHILE_FOREGROUND` | ERROR / WARNING | `onTrimMemory(RUNNING_CRITICAL)` while foregrounded / heap over the configured ceiling (default 85%) |
| `ORPHAN_FRAGMENT` | WARNING | a Fragment is still alive 1s after its host Activity was destroyed |
| `ACTIVITY_LEAK` | WARNING | across 3 destroy-time heap samples: live Activity count flat, heap climbing ≥ 1MB per step |
| `DUPLICATE_SCREEN` | WARNING | two or more live instances of the same Activity class exist at once |
| `BACK_STACK_TOO_DEEP` | WARNING | back stack depth reaches the configured ceiling (default 10) |
| `APP_BACKGROUNDED_MID_FLOW` | INFO | the app is backgrounded while a back stack is non-empty |
| `CARD_SERVICE_CALLED_BEFORE_BIND` | ERROR | a card service API is called before the service reports a bound client |
| `CARD_SERVICE_BOUND_TWICE` | WARNING | the card service is bound again while a client is still bound, with no unbind in between — the previous connection leaks |
| `CONTACTLESS_CONFIG_BEFORE_CONTACT_CONFIG` | ERROR | `setEMVCLConfiguration` is called before `setEMVConfiguration` |
| `ONLINE_PIN_AFTER_TRANSACTION_COMPLETE` | ERROR | an online PIN is requested after `completeEmvTxn` |
| `CARD_REMOVED_DURING_TRANSACTION` | ERROR | `takeOutICC` is called while an EMV transaction still needs completing |
| `PREVIOUS_TRANSACTION_NOT_FINISHED` | ERROR | a new card read starts while the previous transaction still needs completing |

### Runtime state

Every event is folded into a `RuntimeState` as it arrives: foreground status, the current
screen, back stack depth per Activity, per-Activity lifecycle stages, live instance maps and
peaks, last heap sample plus a short destroy-time history, the last memory trim level, the
fields of the last configuration change, and the card service's current state with the time
it was entered.

`RuntimeState` itself stays `internal`; the rules are its consumer, and `risks()` is the
public window over what they conclude.

### Configuration

`Config` is read by whichever side owns the timeline — in practice the inspector app, in
`InspectorService.onCreate`. The app under test can pass one too, but its collectors send
everything across the binder, so only `enabled` has any effect there.

```kotlin
RuntimeInspector.init(
    this,
    RuntimeInspector.Config(
        enabled = true,           // false in the service: it collects nothing itself
        notifyOnRisk = true,      // status-bar notification per finding
        backStackCeiling = 10,    // BACK_STACK_TOO_DEEP threshold
        heapPercentCeiling = 85,  // LOW_MEMORY_WHILE_FOREGROUND heap threshold (%)
        cardServicePatterns = CARD_SERVICE_PATTERNS,  // line pattern -> API + state
        cardServiceEnabled = false,   // legacy logcat reader; see below
        cardServiceTags = emptyList(),
        cardServiceLogUnmatched = false,
    ),
)
```

Rule thresholds are per-deployment settings — different apps have legitimately different
navigation depths and memory profiles.

### Card service tracking

The card service is a separate app, so its transaction state can only be learned from its
log. The inspector app asks Token's platform for that log rather than reading logcat itself.

Capture is turned on when the service starts, not when the dump is asked for — `onCreate`
calls `enableAppLog(true)` and `setAppLogList(listOf("com.tokeninc.cardservice"))`. The
platform captures app logs **by package name**, which is why no logcat tag is configured
anywhere any more. Doing this at pull time would be too late: the transaction has already
happened.

Then, on the host unbinding (or `inspect()`, the **İncele** button, or the adb action) the
service:

1. binds TSystem through `TSystemServiceBinder`
2. calls `getLog(0, destDir, listener)`, where 0 means app logs — the only ones we listed
3. reads only the last 2 MB tail of each plain dump file (the rotated `.zip` archives are
   skipped outright), keeps the last hour, and runs the rest through `CardServiceLogState`
   one line at a time

`getLog` takes no time range, so the file itself still goes as far back as the platform kept
it; the tail read and the hour window are ours. A line whose timestamp we cannot parse follows
the verdict of the last dated line before it, so every pull logs
`Dump lines: kept=… dated=… undated=… newest=…` — `newest=` is the timestamp of the freshest
dated line, which is what explains a `kept=0`: the platform flushes late, so the dump's newest
line can already be older than the window even though the transaction just happened.

**Growth check and retry.** `getLog` copies the whole ~14 MB dump every time, so an automatic
pull first compares the live buffer's size against the last read; if it has not grown, nothing
new was flushed and the read is skipped. Rather than give up, it then retries on a growing
backoff — 15s, 30s, 60s, 2min, 5min — so a card test run and walked away from still lands its
`RISK` lines once the platform flushes, without rewriting 14 MB every minute on an idle
terminal. A fresh pull resets the ladder.

**Force reads take the whole tail.** A hand-triggered read — the **İncele** button or the adb
action — passes `force = true`, which skips both the growth check and the hour window: it
analyses the entire tail regardless of age, because the platform flushes late enough that the
window would otherwise throw away the transaction the user is standing there waiting for.

A pattern names the `CardServiceApi` the line stands for and, when the line also moves the
transaction on, the `CardServiceState` it moves to. That transaction state is what the
call-ordering and sequencing rules read — `PREVIOUS_TRANSACTION_NOT_FINISHED`,
`ONLINE_PIN_AFTER_TRANSACTION_COMPLETE`, `CARD_REMOVED_DURING_TRANSACTION`.

The ten patterns live in the inspector app, in `CardServicePatterns.kt` — including the
`bound` / `Unbound` pair that `CARD_SERVICE_BOUND_TWICE` needs to tell a leaked re-bind from
a normal reconnect.

Matching is a substring search and every matching pattern counts, so one line can stand for
more than one API. The transaction state comes from the first matching pattern that carries
one, so order those from most specific to least.

`destDir` is `/sdcard/Download/runtimeinspector`, chosen because Token's own `getSysLog`
writes to `/sdcard/Download/DeviceLog.txt`. If a run produces no files there, the log says
so and the path is one constant in `PlatformLog.kt`.

> **Nothing here needs `READ_LOGS`.** The platform produces the dump, so the app under test
> needs no log permission and no consent dialog. The old path — the library running `logcat`
> itself, configured with `cardServiceEnabled` and `cardServiceTags` — is still in the code
> but is not used; it required `READ_LOGS` in the host app plus a system consent dialog on
> every app start from Android 11 on.

### Notifications

With `notifyOnRisk`, each finding is posted as a heads-up notification (`IMPORTANCE_HIGH`)
so it appears over the screen as it fires. Repeats **update the existing notification**
with a `×N` count rather than stacking, because the notification ID is derived from the
same rule + subject key used for deduplication. Logcat still receives every finding
regardless of this setting.

The library declares **no notification permission**, so nothing is added to the host's
merged manifest. Findings are now posted by the inspector app, which declares
`POST_NOTIFICATIONS` itself. Tapping a finding notification opens the inspector app's screen
with that finding's card already expanded — the `PendingIntent` resolves the launcher Activity
through the package manager, so the library needs no reference to it.

Separately from findings, the inspector app shows one permanent notification for its
foreground service. That one sits on its own channel at `IMPORTANCE_LOW`, so it stays silent
in the shade instead of appearing over a payment screen.

## Architecture

```
     app under test (AAR)                    inspector app (APK)

  Collectors ──▶ RemoteSink ──AIDL──▶ InspectorService ──▶ Timeline ──▶ RuntimeState
                                             │                 │
  TSystem ◀── getLog ── PlatformLog ◀── unbind()/inspect()      └──▶ RiskEngine ──▶ RISK lines
                  │                                                              + notifications
                  └──▶ DumpReader ──▶ readCardServiceLines ──▶ CardServiceLogState
```

Both halves are the same `RuntimeInspector` object, initialized differently: the host with
`enabled = true` so it collects, the service with `enabled = false` so it only records what
arrives. Sequence numbers are assigned in the host — it is the only place events originate —
and the service keeps them.

- **Collectors** register Android callbacks and translate them into `RuntimeEvent`s.
- **Timeline** assigns a monotonic sequence number to each event under a single lock, stores
  the most recent 500 in a ring buffer, logs it, and folds it into the state. Before/after
  state snapshots are captured inside the lock; rules run outside it, so a slow or broken
  rule can never block a collector.
- **RuntimeState** is produced only by a pure reducer, applied incrementally.
- **RiskEngine** runs every registered rule on every event, deduplicates findings, keeps 100
  of them, and logs each new one. A rule that throws is logged and skipped — a rule bug must
  never crash the host. Eviction is by first-seen order, not recency: a finding that keeps
  repeating can be dropped while a newer one-off survives.

```
runtimeinspector/                the AAR
└── com/vkaan/runtimeinspector/
    ├── RuntimeInspector.kt      public entry point: init, inspect, risks, record
    ├── IInspector.aidl          the interface both processes compile against
    ├── collector/               one per signal: lifecycle, process, memory, heap,
    │                            and the unused logcat reader
    ├── cardservice/
    │   ├── CardServiceState.kt  public state enum
    │   ├── CardServiceApi.kt    public API-name enum
    │   ├── CardServiceLogState.kt  public pattern type + internal tracker
    │   └── rules/               one file per card service rule
    ├── rules/                   Risk, RiskRule, RiskEngine + one file per rule
    ├── report/                  SessionContext, RiskNotifier, FindingRecord, Json
    └── timeline/
        ├── RuntimeEvent.kt      parcelable events
        ├── EventSink.kt         where a collector writes
        ├── RemoteSink.kt        the binder implementation of it
        ├── Timeline.kt          the in-process implementation of it
        └── RuntimeState.kt

app/                             the APK
└── com/vkaan/runtimeinspector/app/
    ├── InspectorService.kt      foreground service, holds the binder
    ├── BootReceiver.kt          starts it at power-on
    ├── MainActivity.kt          the View: findings list, tap-to-expand cards, help view, buttons
    ├── MainViewModel.kt         screen state + actions (MVVM), survives rotation
    ├── RuleHelp.kt              per-rule Turkish cause/fix text for the detail screen
    ├── PlatformLog.kt           TSystem bind + getLog, growth check + retry backoff
    ├── DumpReader.kt            dump -> lines, 2 MB tail, last hour
    └── CardServicePatterns.kt   the ten patterns
```

`RuntimeInspector` is the intended public surface — `init()`, `unbind()`, `isInitialized`,
`risks()`, `inspect()`, `readCardServiceLines()`, `record()` and `Config` — plus `Risk`, the finding
type it returns, and `CardServiceState`, `CardServiceApi` and `CardServiceLogPattern`,
needed to declare patterns. `Timeline`, `RuntimeState`, `EventSink`, `RemoteSink`, the rules,
the collectors and the log tracker are `internal`.

## Building

```bash
./gradlew :runtimeinspector:assembleRelease :app:assembleDebug
```

Outputs:

- `runtimeinspector/build/outputs/aar/runtimeinspector-release.aar`
- `app/build/outputs/apk/debug/app-debug.apk`

`:app` needs Token's wrapper AAR in `app/libs/` first — it is gitignored.

Tests:

```bash
./gradlew :runtimeinspector:testDebugUnitTest :app:testDebugUnitTest
```
