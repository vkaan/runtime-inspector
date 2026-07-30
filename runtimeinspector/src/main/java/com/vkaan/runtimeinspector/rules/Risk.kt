package com.vkaan.runtimeinspector.rules

data class Risk (
    val ruleId: String,
    val severity: Severity,
    val message: String,
    val subject: String?,
    val seq: Long,
    val timestampMillis: Long,
) {

    enum class Severity { INFO, WARNING, ERROR }

    internal val dedupKey: String get() = "$ruleId:${subject.orEmpty()}"

    fun logLine(): String =
        "RISK [${severity.name}] $ruleId${subject?.let { " $it" }.orEmpty()} — $message"
}