package com.vkaan.runtimeinspector.report

import com.vkaan.runtimeinspector.rules.Risk

/**
 * One finding, flattened together with its session context into a single self-contained line.
 *
 * The session fields repeat on every record instead of being written once as a header. That is
 * intentional: findings travel through logcat, which drops lines under load, so a record that
 * depends on an earlier line to be interpretable is a record that can arrive meaningless.
 *
 * `(sessionId, ruleId, subject)` is the natural primary key — the engine already keeps exactly
 * one [Risk] per rule + subject — so the same record may be written repeatedly as `occurrences`
 * grows and the backend can simply upsert, keeping the highest count.
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
        "boot_id" to session.bootId,
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
