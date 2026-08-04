package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.report.FindingRecord
import com.vkaan.runtimeinspector.report.SessionContext
import com.vkaan.runtimeinspector.rules.Risk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FindingRecordTest {

    private val session = SessionContext(
        sessionId = "session-1",
        appPackage = "com.example.personlist",
        appVersionName = "1.2.3",
        appVersionCode = 42L,
        libraryVersion = "0.1.0",
        deviceModel = "Pixel 3",
        androidSdkInt = 28,
    )

    private fun risk(
        subject: String? = "MainActivity",
        instanceId: Int? = 154959438,
        message: String = "test finding",
    ) = Risk(
        ruleId = "RECREATION_MID_FLOW",
        severity = Risk.Severity.WARNING,
        message = message,
        subject = subject,
        instanceId = instanceId,
        seq = 3L,
        timestampMillis = 1000L,
        occurrences = 5,
        lastSeq = 90L,
        lastTimestampMillis = 5000L,
    )

    private fun record(risk: Risk = risk()) = FindingRecord.from(
        risk = risk,
        session = session,
        recordedAtMillis = 7000L,
        recordedElapsedRealtimeNanos = 123L,
    )

    @Test
    fun `carries the first and last occurrence separately`() {
        val record = record()

        assertEquals(5, record.occurrences)
        assertEquals(3L, record.firstSeq)
        assertEquals(90L, record.lastSeq)
        assertEquals(1000L, record.firstTimestampMillis)
        assertEquals(5000L, record.lastTimestampMillis)
    }

    @Test
    fun `renders every field on one line`() {
        val json = record().toJsonLine()

        assertEquals(
            """{"session_id":"session-1",""" +
                """"app_package":"com.example.personlist","app_version_name":"1.2.3",""" +
                """"app_version_code":42,"library_version":"0.1.0",""" +
                """"device_model":"Pixel 3","android_sdk":28,""" +
                """"rule_id":"RECREATION_MID_FLOW","severity":"WARNING",""" +
                """"subject":"MainActivity","instance_id":154959438,""" +
                """"message":"test finding","occurrences":5,""" +
                """"first_seq":3,"last_seq":90,""" +
                """"first_timestamp_millis":1000,"last_timestamp_millis":5000,""" +
                """"recorded_at_millis":7000,"recorded_elapsed_nanos":123}""",
            json,
        )
    }

    @Test
    fun `a rule with no subject writes JSON nulls, not the string "null"`() {
        val json = record(risk(subject = null, instanceId = null)).toJsonLine()

        assertTrue(json.contains(""""subject":null"""))
        assertTrue(json.contains(""""instance_id":null"""))
    }

    @Test
    fun `a message containing a newline still produces a single line`() {
        val json = record(risk(message = "line one\nline two")).toJsonLine()

        assertFalse(json.contains('\n'))
        assertTrue(json.contains("""line one\nline two"""))
    }
}
