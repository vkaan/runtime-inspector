package com.vkaan.runtimeinspector.timeline

import android.content.ComponentCallbacks2

sealed interface RuntimeEvent {
    val seq: Long
    val timestampMillis: Long
    val elapsedRealtimeNanos: Long

    fun logLine(): String

    data class Lifecycle(
        override val seq: Long,
        override val timestampMillis: Long,
        override val elapsedRealtimeNanos: Long,
        val sourceType: SourceType,
        val name: String,
        val stage: Stage,
        val instanceId: Int,

        /** Activity STOPPED/DESTROYED only: true when the teardown is a config change. */
        val isChangingConfigurations: Boolean? = null,

        /** Fragment events only: identity of the Activity hosting this fragment. */
        val hostActivityId: Int? = null,

        /**
         * The host FragmentManager's entry count, read at a moment when no transaction is in
         * flight. Set on FragmentActivity events and on the events of its fragments; null when
         * the Activity is not a FragmentActivity.
         */
        val backStackEntryCount: Int? = null,

    ) : RuntimeEvent {
        enum class SourceType { ACTIVITY, FRAGMENT }

        /**
         * Every lifecycle stage this library records. Closed on purpose: the reducer's `when`
         * is exhaustive over it, so adding a stage here fails the build until it is handled.
         * Back stack pushes/pops are NOT stages — see [BackStack].
         */
        enum class Stage {
            CREATED,
            STARTED,
            RESUMED,
            PAUSED,
            STOPPED,
            DESTROYED,
            ATTACHED,
            VIEW_CREATED,
            VIEW_DESTROYED,
            DETACHED,
        }

        override fun logLine(): String {
            val configNote = if (isChangingConfigurations == true) " (config change)" else ""
            return "${sourceType.name} $name#$instanceId -> ${stage.name}$configNote"
        }
    }

    /**
     * A fragment back stack transaction. Separate from [Lifecycle] because it is a navigation
     * event, not a lifecycle stage.
     *
     * Deliberately carries no depth. FragmentManager dispatches this from two different places,
     * one of which runs before the transaction is applied, so a count read here is sometimes the
     * pre-transaction value. Depth is sampled from lifecycle callbacks instead, which always run
     * after the transaction has settled.
     */
    data class BackStack(
        override val seq: Long,
        override val timestampMillis: Long,
        override val elapsedRealtimeNanos: Long,
        val hostActivityId: Int,
        val hostActivityName: String,
        val fragmentName: String,
        val popped: Boolean,
    ) : RuntimeEvent {
        override fun logLine(): String =
            "BACKSTACK ${if (popped) "POPPED" else "PUSHED"} $fragmentName " +
                "in $hostActivityName#$hostActivityId"
    }

    data class Process(
        override val seq: Long,
        override val timestampMillis: Long,
        override val elapsedRealtimeNanos: Long,
        val state: State,
    ) : RuntimeEvent {
        enum class State { CREATED, FOREGROUNDED, BACKGROUNDED }

        override fun logLine(): String = "PROCESS app -> ${state.name}"
    }

    /** Memory-pressure signals from ComponentCallbacks2.onTrimMemory (FR-06). */
    data class Memory(
        override val seq: Long,
        override val timestampMillis: Long,
        override val elapsedRealtimeNanos: Long,
        val level: Int,
    ) : RuntimeEvent {

        /** Human-readable name for the raw TRIM_MEMORY_* constant. */
        val levelName: String
            get() = when (level) {
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
                ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
                ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
                ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE"
                else -> "UNKNOWN($level)"
            }

        override fun logLine(): String = "MEMORY onTrimMemory($levelName)"
    }

    /** Device/app configuration changes from onConfigurationChanged (FR-06). */
    data class ConfigChange(
        override val seq: Long,
        override val timestampMillis: Long,
        override val elapsedRealtimeNanos: Long,
        val changedFields: List<String>,
    ) : RuntimeEvent {
        override fun logLine(): String =
            "CONFIG changed: ${changedFields.joinToString("|").ifEmpty { "NONE" }}"
    }
}