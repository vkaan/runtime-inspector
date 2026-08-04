package com.vkaan.runtimeinspector.rules

data class Risk (
    val ruleId: String,
    val severity: Severity,
    val message: String,
    /** What the finding is about, e.g. an Activity class name. Stable across recreation. */
    val subject: String?,
    /**
     * Which instance the finding happened to, when that is known. Read but never compared:
     * recreation mints a new id, so including it in [dedupKey] would defeat [occurrences].
     */
    val instanceId: Int? = null,
    /** Sequence number of the FIRST occurrence; `lastSeq` tracks the most recent one. */
    val seq: Long,
    /** Timestamp of the FIRST occurrence; `lastTimestampMillis` tracks the most recent one. */
    val timestampMillis: Long,
    /** How many times this (rule + subject) has fired. Maintained by the engine; rules emit 1. */
    val occurrences: Int = 1,
    val lastSeq: Long = seq,
    val lastTimestampMillis: Long = timestampMillis,
) {

    enum class Severity { INFO, WARNING, ERROR }

    internal val dedupKey: String get() = "$ruleId:${subject.orEmpty()}"

    /** Human-readable identity: `subject#instanceId` when both are known. */
    fun label(): String? = subject?.let { it + instanceId?.let { id -> "#$id" }.orEmpty() }

    fun logLine(): String =
        "RISK [${severity.name}] $ruleId${label()?.let { " $it" }.orEmpty()} — $message"
}
