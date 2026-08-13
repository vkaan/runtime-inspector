package com.vkaan.runtimeinspector.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class DumpReaderTest {

    private val now = System.currentTimeMillis()

    private fun stamp(minutesAgo: Long): String =
        SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            .format(now - minutesAgo * 60_000L)

    private fun dumpOf(vararg lines: String): File =
        File.createTempFile("dump", ".txt").apply { writeText(lines.joinToString("\n")) }

    @Test
    fun `lines older than the window are dropped`() {
        val file = dumpOf(
            "${stamp(2)} I cardservice: completeEmvTxn",
            "${stamp(40)} I cardservice: getOnlinePIN",
        )

        val kept = DumpReader.lines(listOf(file), now).toList()

        assertEquals(listOf("${stamp(2)} I cardservice: completeEmvTxn"), kept)
    }

    @Test
    fun `a line with no timestamp is kept`() {
        val file = dumpOf("getCard called with config: {}")

        assertEquals(1, DumpReader.lines(listOf(file), now).count())
    }
}
