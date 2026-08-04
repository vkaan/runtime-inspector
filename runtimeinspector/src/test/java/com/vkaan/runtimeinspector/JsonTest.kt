package com.vkaan.runtimeinspector

import com.vkaan.runtimeinspector.report.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JsonTest {

    @Test
    fun `writes numbers bare and strings quoted`() {
        assertEquals(
            """{"a":"text","b":7,"c":9000000000,"d":true}""",
            Json.obj("a" to "text", "b" to 7, "c" to 9_000_000_000L, "d" to true),
        )
    }

    @Test
    fun `writes null unquoted so it stays a JSON null`() {
        assertEquals("""{"a":null}""", Json.obj("a" to null))
    }

    @Test
    fun `escapes quotes and backslashes`() {
        assertEquals("""{"a":"say \"hi\" C:\\tmp"}""", Json.obj("a" to """say "hi" C:\tmp"""))
    }

    @Test
    fun `escapes newlines so a record cannot split across lines`() {
        val json = Json.obj("a" to "first\r\nsecond")

        assertEquals("""{"a":"first\r\nsecond"}""", json)
        assertFalse(json.contains('\n'))
    }

    @Test
    fun `escapes control characters that have no short form`() {
        assertEquals("""{"a":"\u0001\u001f"}""", Json.obj("a" to "\u0001\u001F"))
    }
}
