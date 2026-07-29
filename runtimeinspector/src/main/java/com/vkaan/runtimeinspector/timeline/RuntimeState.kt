package com.vkaan.runtimeinspector.timeline

import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.SourceType
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage

internal data class RuntimeState(
    val appInForeground: Boolean = false,
    val foregroundActivity: Screen? = null,
    val foregroundFragment: Screen? = null,
    val foregroundFragmentHostId: Int? = null,
    /** Back stack depth per Activity, keyed by Activity identity. Dropped when it is destroyed. */
    val backStackDepths: Map<Int, Int> = emptyMap(),
    val lastTrimMemory: String? = null,
    val lastConfigChange: List<String> = emptyList(),
    val lastSeq: Long = -1L,
    val lastHeapUsedBytes: Long? = null,
    val lastHeapMaxBytes: Long? = null,
) {

    data class Screen(val name: String, val instanceId: Int) {
        override fun toString(): String = "$name#$instanceId"
    }


    val foregroundScreen: String?
        get() = when {
            foregroundFragment != null &&
                foregroundFragmentHostId == foregroundActivity?.instanceId -> foregroundFragment.toString()

            else -> foregroundActivity?.toString()
        }


    val backStackDepth: Int
        get() = foregroundActivity?.let { backStackDepths[it.instanceId] } ?: 0

    fun reduce(event: RuntimeEvent): RuntimeState = when (event) {
        is RuntimeEvent.Process -> copy(
            appInForeground = when (event.state) {
                RuntimeEvent.Process.State.FOREGROUNDED -> true
                RuntimeEvent.Process.State.BACKGROUNDED -> false
                RuntimeEvent.Process.State.CREATED -> appInForeground
            }
        )

        is RuntimeEvent.Lifecycle -> reduceLifecycle(event)


        is RuntimeEvent.BackStack -> this

        is RuntimeEvent.Memory -> copy(lastTrimMemory = event.levelName)

        is RuntimeEvent.ConfigChange -> copy(lastConfigChange = event.changedFields)

        is RuntimeEvent.MemoryUsage -> copy(
            lastHeapUsedBytes = event.usedBytes,
            lastHeapMaxBytes = event.maxBytes,
        )

    }.copy(lastSeq = event.seq)

    private fun reduceLifecycle(event: RuntimeEvent.Lifecycle): RuntimeState {
        val screen = Screen(event.name, event.instanceId)
        val isActivity = event.sourceType == SourceType.ACTIVITY

        val next = when (event.stage) {
            Stage.RESUMED ->
                if (isActivity) copy(foregroundActivity = screen)
                else copy(foregroundFragment = screen, foregroundFragmentHostId = event.hostActivityId)

            // Only clear if this is still the screen we are pointing at. On a pop the incoming
            // fragment may resume before the outgoing one is destroyed, and this guard keeps that
            // late DESTROYED from wiping the fragment that is now on screen.
            Stage.DESTROYED -> when {
                isActivity -> copy(
                    foregroundActivity = foregroundActivity.takeIf { it != screen },
                    backStackDepths = backStackDepths - event.instanceId,
                )

                foregroundFragment == screen ->
                    copy(foregroundFragment = null, foregroundFragmentHostId = null)

                else -> this
            }

            Stage.CREATED,
            Stage.STARTED,
            Stage.PAUSED,
            Stage.STOPPED,
            Stage.ATTACHED,
            Stage.VIEW_CREATED,
            Stage.VIEW_DESTROYED,
            Stage.DETACHED -> this
        }

        return next.withSampledDepth(event)
    }


    private fun withSampledDepth(event: RuntimeEvent.Lifecycle): RuntimeState {
        val count = event.backStackEntryCount ?: return this

        return when (event.sourceType) {
            // Its entry was just dropped by DESTROYED above and must not be written back.
            SourceType.ACTIVITY ->
                if (event.stage == Stage.DESTROYED) this
                else copy(backStackDepths = backStackDepths + (event.instanceId to count))

            // A host is destroyed before its fragments are, so fragment events arrive after the
            // entry is gone. They may refresh a live host's count, never resurrect a dead one.
            SourceType.FRAGMENT -> {
                val hostId = event.hostActivityId
                if (hostId == null || hostId !in backStackDepths) this
                else copy(backStackDepths = backStackDepths + (hostId to count))
            }
        }
    }
}
