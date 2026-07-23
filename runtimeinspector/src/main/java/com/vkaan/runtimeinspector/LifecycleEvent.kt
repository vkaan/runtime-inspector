package com.vkaan.runtimeinspector

/**
 * One captured lifecycle transition.
 *
 * Holds only plain data (names, a stage label, a timestamp) — it deliberately
 * does NOT keep a reference to the Activity/Fragment, so the long-lived
 * collector can never leak a short-lived screen.
 */
data class LifecycleEvent(
    val sourceType: SourceType,
    val name: String,
    val stage: String,
    val instanceId: Int,                            // identifies the specific instance
    val isChangingConfigurations: Boolean? = null,  // set on Activity STOPPED/DESTROYED only
    val timestampMillis: Long = System.currentTimeMillis(),
) {
    enum class SourceType { ACTIVITY, FRAGMENT }
}
