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
        val stage: String,
        val instanceId: Int,

        val isChangingConfigurations: Boolean?= null,


    ) : RuntimeEvent {
        enum class SourceType{ ACTIVITY, FRAGMENT}

        override fun logLine(): String {
            val configNote = if (isChangingConfigurations == true) " (config change)" else ""
            return "${sourceType.name} $name#$instanceId -> $stage$configNote"
        }
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