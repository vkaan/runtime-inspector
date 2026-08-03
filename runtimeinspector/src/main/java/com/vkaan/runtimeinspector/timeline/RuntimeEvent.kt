package com.vkaan.runtimeinspector.timeline

import android.content.ComponentCallbacks2
import java.util.Locale

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


        val isChangingConfigurations: Boolean? = null,

        /** Fragment events only: identity of the Activity hosting this fragment. */
        val hostActivityId: Int? = null,


        val backStackEntryCount: Int? = null,

    ) : RuntimeEvent {
        enum class SourceType { ACTIVITY, FRAGMENT }


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

        // Lifecycle
        override fun logLine(): String {
            val configNote = if (isChangingConfigurations == true) " (config change)" else ""
            return "${sourceType.name} $name#$instanceId -> ${stage.name}$configNote"
        }
    }


    data class BackStack(
        override val seq: Long,
        override val timestampMillis: Long,
        override val elapsedRealtimeNanos: Long,
        val hostActivityId: Int,
        val hostActivityName: String,
        /** All fragments in the transaction — a replace() lists both the old and the new one. */
        val fragmentNames: List<String>,
        val popped: Boolean,
    ) : RuntimeEvent {
        // BackStack
        override fun logLine(): String =
            "BACKSTACK ${if (popped) "POPPED" else "PUSHED"} ${fragmentNames.joinToString("|")} " +
                "in $hostActivityName#$hostActivityId"
    }

    data class Process(
        override val seq: Long,
        override val timestampMillis: Long,
        override val elapsedRealtimeNanos: Long,
        val state: State,
    ) : RuntimeEvent {
        enum class State { CREATED, FOREGROUNDED, BACKGROUNDED }
        // Process
        override fun logLine(): String = "PROCESS app -> ${state.name}"
    }


    data class Memory(
        override val seq: Long,
        override val timestampMillis: Long,
        override val elapsedRealtimeNanos: Long,
        val level: Int,
    ) : RuntimeEvent {


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
        // Memory
        override fun logLine(): String = "MEMORY onTrimMemory($levelName)"
    }


    data class ConfigChange(
        override val seq: Long,
        override val timestampMillis: Long,
        override val elapsedRealtimeNanos: Long,
        val changedFields: List<String>,
    ) : RuntimeEvent {
        // ConfigChange
        override fun logLine(): String =
            "CONFIG changed: ${changedFields.joinToString("|").ifEmpty { "NONE" }}"
    }


    data class Crash(
        override val seq: Long,
        override val timestampMillis: Long,
        override val elapsedRealtimeNanos: Long,
        val threadName: String,
        val exceptionClass: String,
        val exceptionMessage: String?,
        val topFrame: String?,
    ) : RuntimeEvent {
        // Crash
        override fun logLine(): String =
            "CRASH $exceptionClass${exceptionMessage?.let { ": $it" }.orEmpty()} " +
                "on thread=$threadName${topFrame?.let { " at $it" }.orEmpty()}"
    }


    data class Network(
        override val seq: Long,
        override val timestampMillis: Long,
        override val elapsedRealtimeNanos: Long,
        val state: State,
        val transports: List<String> = emptyList(),
    ) : RuntimeEvent {
        enum class State { AVAILABLE, LOST }

        // Network
        override fun logLine(): String =
            "NETWORK ${state.name}" +
                if (transports.isEmpty()) "" else " (${transports.joinToString("|")})"
    }


    /** Device-level signals delivered as system broadcasts (screen, battery, shutdown). */
    data class SystemSignal(
        override val seq: Long,
        override val timestampMillis: Long,
        override val elapsedRealtimeNanos: Long,
        val signal: Signal,
    ) : RuntimeEvent {
        enum class Signal { SCREEN_ON, SCREEN_OFF, BATTERY_LOW, BATTERY_OKAY, SHUTDOWN }

        // SystemSignal
        override fun logLine(): String = "SYSTEM ${signal.name}"
    }


    data class MemoryUsage(
        override val seq: Long,
        override val timestampMillis: Long,
        override val elapsedRealtimeNanos: Long,
        val usedBytes: Long,
        val maxBytes: Long,
        val trigger: Trigger,
    ) : RuntimeEvent {

        enum class Trigger {
            ACTIVITY_CREATED,
            ACTIVITY_DESTROYED,
            APP_FOREGROUNDED,
            APP_BACKGROUNDED,
            TRIM_MEMORY,
        }

        val usedPercent: Int
            get() = if (maxBytes <= 0L) 0 else ((usedBytes * 100L) / maxBytes).toInt()

        override fun logLine(): String =
            "HEAP used=${usedBytes.toMb()} max=${maxBytes.toMb()} ($usedPercent%) on ${trigger.name}"
    }
}

private fun Long.toMb(): String =
    String.format(Locale.US, "%.1fMB", this / 1024.0 / 1024.0)