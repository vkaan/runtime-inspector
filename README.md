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
- [ ] Step 1 — Library module and initialization
- [ ] Step 2 — TBD
