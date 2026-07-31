package com.vkaan.runtimeinspector.rules

data class Risk (
    val ruleId: String,
    val severity: Severity,
    val message: String,
    val subject: String?,
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

    fun logLine(): String =
        "RISK [${severity.name}] $ruleId${subject?.let { " $it" }.orEmpty()} — $message"
}