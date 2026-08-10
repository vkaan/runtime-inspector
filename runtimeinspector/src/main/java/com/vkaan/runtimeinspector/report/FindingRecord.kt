package com.vkaan.runtimeinspector.report

import com.vkaan.runtimeinspector.rules.Risk

/**
 * One finding flattened with its session context into a self-contained line.
 *
 * The session fields repeat on every record rather than being written once as a header:
 * logcat drops lines under load, so a record that needs an earlier line is unreadable.
 *
 * `(sessionId, ruleId, subject)` is the primary key, so a record can be rewritten as
 * `occurrences` grows and the backend upserts on the highest count.
 */
internal data class FindingRecord(
    val session: SessionContext,
    val ruleId: String,
    val severity: String,
    val subject: String?,
    val instanceId: Int?,
    val message: String,
    val occurrences: Int,
    val firstSeq: Long,
    val lastSeq: Long,
    val firstTimestampMillis: Long,
    val lastTimestampMillis: Long,
    val recordedAtMillis: Long,
    val recordedElapsedRealtimeNanos: Long,
) {

    fun toJsonLine(): String = Json.obj(
        "session_id" to session.sessionId,
        "app_package" to session.appPackage,
        "app_version_name" to session.appVersionName,
        "app_version_code" to session.appVersionCode,
        "library_version" to session.libraryVersion,
        "device_model" to session.deviceModel,
        "android_sdk" to session.androidSdkInt,
        "rule_id" to ruleId,
        "severity" to severity,
        "subject" to subject,
        "instance_id" to instanceId,
        "message" to message,
        "occurrences" to occurrences,
        "first_seq" to firstSeq,
        "last_seq" to lastSeq,
        "first_timestamp_millis" to firstTimestampMillis,
        "last_timestamp_millis" to lastTimestampMillis,
        "recorded_at_millis" to recordedAtMillis,
        "recorded_elapsed_nanos" to recordedElapsedRealtimeNanos,
    )

    companion object {

        fun from(
            risk: Risk,
            session: SessionContext,
            recordedAtMillis: Long,
            recordedElapsedRealtimeNanos: Long,
        ): FindingRecord = FindingRecord(
            session = session,
            ruleId = risk.ruleId,
            severity = risk.severity.name,
            subject = risk.subject,
            instanceId = risk.instanceId,
            message = risk.message,
            occurrences = risk.occurrences,
            firstSeq = risk.seq,
            lastSeq = risk.lastSeq,
            firstTimestampMillis = risk.timestampMillis,
            lastTimestampMillis = risk.lastTimestampMillis,
            recordedAtMillis = recordedAtMillis,
            recordedElapsedRealtimeNanos = recordedElapsedRealtimeNanos,
        )
    }
}
