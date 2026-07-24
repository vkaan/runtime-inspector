# RuntimeInspector

An Android debugging library for inspecting application state at runtime.

## Installation

```kotlin
dependencies {
    implementation(project(":runtimeinspector"))
}
```

## Usage

```kotlin
class DemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RuntimeInspector.init(this)
    }
}
```

## Progress
- [x] Step 1 — Library module and initialization
- [x] Step 2 — Collector layer: Activity & Fragment lifecycle events (+ back-stack, per-event instanceId & config-change flag)
