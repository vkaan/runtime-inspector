package com.vkaan.runtimeinspector.app

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DumpReaderTest {

    private val now = System.currentTimeMillis()

    @Before
    fun forgetPreviousPulls() {
        DumpReader.lastProcessedMillis = 0L
    }

    private fun stamp(minutesAgo: Long, pattern: String = "MM-dd HH:mm:ss.SSS"): String =
        SimpleDateFormat(pattern, Locale.US).format(now - minutesAgo * 60_000L)

    private fun dumpOf(vararg lines: String): File =
        File.createTempFile("dump", ".txt").apply { writeText(lines.joinToString("\n")) }

    @Test
    fun `lines older than the window are dropped`() {
        val file = dumpOf(
            "${stamp(2)} I cardservice: completeEmvTxn",
            "${stamp(90)} I cardservice: getOnlinePIN",
        )

        val kept = DumpReader.lines(listOf(file), now).toList()

        assertEquals(listOf("${stamp(2)} I cardservice: completeEmvTxn"), kept)
    }

    @Test
    fun `a line with a year in the timestamp is dated too`() {
        val file = dumpOf(
            "${stamp(2, "yyyy-MM-dd HH:mm:ss.SSS")} I cardservice: completeEmvTxn",
            "${stamp(90, "yyyy-MM-dd HH:mm:ss.SSS")} I cardservice: getOnlinePIN",
        )

        val kept = DumpReader.lines(listOf(file), now).toList()

        assertEquals(1, kept.size)
    }

    @Test
    fun `a zipped dump is read and windowed like a plain one`() {
        val zip = File.createTempFile("dump", ".zip").apply {
            ZipOutputStream(outputStream()).use { out ->
                out.putNextEntry(ZipEntry("DeviceLog.txt"))
                out.write(
                    (
                        "${stamp(2)} I cardservice: completeEmvTxn\n" +
                            "${stamp(90)} I cardservice: getOnlinePIN"
                        ).toByteArray()
                )
                out.closeEntry()
            }
        }

        val kept = DumpReader.lines(listOf(zip), now).toList()

        assertEquals(listOf("${stamp(2)} I cardservice: completeEmvTxn"), kept)
    }

    @Test
    fun `a line with no timestamp is kept`() {
        val file = dumpOf("getCard called with config: {}")

        assertEquals(1, DumpReader.lines(listOf(file), now).count())
    }

    @Test
    fun `a second pull skips what the first one already read`() {
        val file = dumpOf("${stamp(2)} I cardservice: completeEmvTxn")

        assertEquals(1, DumpReader.lines(listOf(file), now).count())
        assertEquals(0, DumpReader.lines(listOf(file), now).count())
    }
}
