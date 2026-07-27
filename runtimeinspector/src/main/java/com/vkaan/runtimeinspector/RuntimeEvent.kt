package com.vkaan.runtimeinspector

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
}