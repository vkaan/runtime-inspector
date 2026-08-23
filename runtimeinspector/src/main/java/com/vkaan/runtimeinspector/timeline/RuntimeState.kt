package com.vkaan.runtimeinspector.timeline

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
import com.vkaan.runtimeinspector.cardservice.CardServiceState
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.SourceType
import com.vkaan.runtimeinspector.timeline.RuntimeEvent.Lifecycle.Stage

private const val MAX_DESTROY_SAMPLES = 3

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
    val liveActivityIds: Map<Int, String> = emptyMap(),
    val liveFragmentIds: Map<Int, String> = emptyMap(),
    val peakLiveActivities: Int = 0,
    val peakLiveFragments: Int = 0,
    val activityStages: Map<Int, Stage> = emptyMap(),
    val fragmentHostIds: Map<Int, Int> = emptyMap(),
    /** Fragments whose host Activity died, keyed to the host's death time (elapsedRealtimeNanos). */
    val orphanCandidates: Map<Int, Long> = emptyMap(),
    val destroyHeapSamples: List<DestroyHeapSample> = emptyList(),
    val cardServiceState: CardServiceState = CardServiceState.IDLE,
    val cardServiceSinceNanos: Long = 0L,
    val cardServiceBound: Boolean = false,
    val emvConfigured: Boolean = false,
) {

    data class Screen(val name: String, val instanceId: Int) {
        override fun toString(): String = "$name#$instanceId"
    }

    data class DestroyHeapSample(val usedBytes: Long, val liveActivities: Int)


    val foregroundScreen: Screen?
        get() = when {
            foregroundFragment != null &&
                foregroundFragmentHostId == foregroundActivity?.instanceId -> foregroundFragment

            else -> foregroundActivity
        }


    val backStackDepth: Int
        get() = foregroundActivity?.let { backStackDepths[it.instanceId] } ?: 0

    val liveActivities: Int get() = liveActivityIds.size
    val liveFragments: Int get() = liveFragmentIds.size

    val cardTransactionOpen: Boolean
        get() = cardServiceState.requiresCompletion

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

        is RuntimeEvent.CardService -> copy(
            cardServiceState = event.to,
            cardServiceSinceNanos =
                if (event.to != event.from) event.elapsedRealtimeNanos else cardServiceSinceNanos,
            cardServiceBound = when {
                CardServiceApi.UNBIND in event.apis -> false
                CardServiceApi.BIND in event.apis -> true
                else -> cardServiceBound
            },
            emvConfigured = emvConfigured || CardServiceApi.SET_EMV_CONFIG in event.apis,
        )

        is RuntimeEvent.MemoryUsage -> {
            val sampled = copy(
                lastHeapUsedBytes = event.usedBytes,
                lastHeapMaxBytes = event.maxBytes,
            )
            if (event.trigger == RuntimeEvent.MemoryUsage.Trigger.ACTIVITY_DESTROYED) {
                sampled.copy(
                    destroyHeapSamples = (destroyHeapSamples +
                        DestroyHeapSample(event.usedBytes, liveActivities)).takeLast(MAX_DESTROY_SAMPLES)
                )
            } else {
                sampled
            }
        }

    }.copy(lastSeq = event.seq)

    private fun reduceLifecycle(event: RuntimeEvent.Lifecycle): RuntimeState {
        val screen = Screen(event.name, event.instanceId)
        val isActivity = event.sourceType == SourceType.ACTIVITY

        val next = when (event.stage) {
            Stage.RESUMED ->
                if (isActivity) copy(foregroundActivity = screen)
                else copy(foregroundFragment = screen, foregroundFragmentHostId = event.hostActivityId)


            Stage.CREATED ->
                if (isActivity) copy(liveActivityIds = liveActivityIds + (event.instanceId to event.name))
                else copy(
                    liveFragmentIds = liveFragmentIds + (event.instanceId to event.name),
                    fragmentHostIds = event.hostActivityId
                        ?.let { fragmentHostIds + (event.instanceId to it) }
                        ?: fragmentHostIds,
                )

            Stage.DESTROYED -> when {
                isActivity -> {
                    val hosted = fragmentHostIds.filterValues { it == event.instanceId }.keys
                    copy(
                        foregroundActivity = foregroundActivity.takeIf { it != screen },
                        backStackDepths = backStackDepths - event.instanceId,
                        liveActivityIds = liveActivityIds - event.instanceId,
                        fragmentHostIds = fragmentHostIds - hosted,
                        orphanCandidates = orphanCandidates +
                            hosted.associateWith { event.elapsedRealtimeNanos },
                    )
                }

                foregroundFragment == screen -> copy(
                    foregroundFragment = null,
                    foregroundFragmentHostId = null,
                    liveFragmentIds = liveFragmentIds - event.instanceId,
                    fragmentHostIds = fragmentHostIds - event.instanceId,
                    orphanCandidates = orphanCandidates - event.instanceId,
                )

                else -> copy(
                    liveFragmentIds = liveFragmentIds - event.instanceId,
                    fragmentHostIds = fragmentHostIds - event.instanceId,
                    orphanCandidates = orphanCandidates - event.instanceId,
                )
            }

            Stage.STARTED,
            Stage.PAUSED,
            Stage.STOPPED,
            Stage.ATTACHED,
            Stage.VIEW_CREATED,
            Stage.VIEW_DESTROYED,
            Stage.DETACHED -> this
        }

        return next.withActivityStage(event).withPeaks().withSampledDepth(event)
    }

    private fun withActivityStage(event: RuntimeEvent.Lifecycle): RuntimeState {
        if (event.sourceType != SourceType.ACTIVITY) return this
        return when (event.stage) {
            Stage.DESTROYED -> copy(activityStages = activityStages - event.instanceId)
            Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED ->
                copy(activityStages = activityStages + (event.instanceId to event.stage))
            else -> this
        }
    }

    private fun withPeaks(): RuntimeState = copy(
        peakLiveActivities = maxOf(peakLiveActivities, liveActivities),
        peakLiveFragments = maxOf(peakLiveFragments, liveFragments),
    )


    private fun withSampledDepth(event: RuntimeEvent.Lifecycle): RuntimeState {
        val count = event.backStackEntryCount ?: return this

        return when (event.sourceType) {

            SourceType.ACTIVITY ->
                if (event.stage == Stage.DESTROYED) this
                else copy(backStackDepths = backStackDepths + (event.instanceId to count))


            SourceType.FRAGMENT -> {
                val hostId = event.hostActivityId
                if (hostId == null || hostId !in backStackDepths) this
                else copy(backStackDepths = backStackDepths + (hostId to count))
            }
        }
    }
}
