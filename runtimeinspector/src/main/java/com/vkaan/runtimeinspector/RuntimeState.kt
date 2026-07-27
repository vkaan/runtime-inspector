package com.vkaan.runtimeinspector

internal data class RuntimeState (
    val appInForeground: Boolean = false,
    val foregroundScreen: String? = null,
    val backStackDepth: Int = 0,
    val lastTrimMemory: String? = null,
    val lastConfigChange: List<String> = emptyList(),
    val lastSeq: Long = -1L,
) {

    fun reduce(event: RuntimeEvent): RuntimeState = when (event) {
        is RuntimeEvent.Process -> copy(
            appInForeground = when (event.state) {
                RuntimeEvent.Process.State.FOREGROUNDED -> true
                RuntimeEvent.Process.State.BACKGROUNDED -> false
                RuntimeEvent.Process.State.CREATED -> appInForeground
            }
        )

        is RuntimeEvent.Lifecycle -> when (event.stage) {
            "RESUMED" -> copy(foregroundScreen = "${event.name}#${event.instanceId}")
            "BACKSTACK_PUSHED" -> copy(backStackDepth = backStackDepth + 1)
            "BACKSTACK_POPPED" -> copy(backStackDepth = (backStackDepth - 1).coerceAtLeast(0))
            else -> this
        }

        is RuntimeEvent.Memory -> copy(lastTrimMemory = event.levelName)

        is RuntimeEvent.ConfigChange -> copy(lastConfigChange = event.changedFields)

    }.copy(lastSeq = event.seq)
}