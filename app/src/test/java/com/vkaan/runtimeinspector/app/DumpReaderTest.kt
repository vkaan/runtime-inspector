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
    fun `a zipped dump is skipped — it is a rotated log, never in the window`() {
        val zip = File.createTempFile("dump", ".zip").apply {
            ZipOutputStream(outputStream()).use { out ->
                out.putNextEntry(ZipEntry("applog_20260811-133246.txt"))
                out.write("${stamp(2)} I cardservice: completeEmvTxn".toByteArray())
                out.closeEntry()
            }
        }

        assertEquals(0, DumpReader.lines(listOf(zip), now).count())
    }

    @Test
    fun `an undated line follows the dated line above it`() {
        val file = dumpOf(
            "${stamp(90)} I cardservice: getOnlinePIN",
            "=================== [pid=6746, packagename=com.tokeninc.cardservice] ===================",
            "${stamp(2)} I cardservice: getCard called with config: {",
            "  \"emvProcessType\": 1",
            "}",
        )

        val kept = DumpReader.lines(listOf(file), now).toList()

        assertEquals(
            listOf(
                "${stamp(2)} I cardservice: getCard called with config: {",
                "  \"emvProcessType\": 1",
                "}",
            ),
            kept,
        )
    }

    @Test
    fun `undated lines before any dated line are dropped`() {
        val file = dumpOf("=================== start-applog [log_index=9] ===================")

        assertEquals(0, DumpReader.lines(listOf(file), now).count())
    }

    @Test
    fun `only the tail of a large dump is read`() {
        val recent = "${stamp(2)} I cardservice: completeEmvTxn"
        val filler = "${stamp(90)} I cardservice: getOnlinePIN"
        val file = File.createTempFile("dump", ".txt").apply {
            bufferedWriter().use { out ->
                // In the window, so only its position past the 2MB tail can keep it out.
                out.write("${stamp(3)} I cardservice: takeOutICC\n")
                repeat(2 * 1024 * 1024 / filler.length + 1) { out.write("$filler\n") }
                out.write("$recent\n")
            }
        }

        val kept = DumpReader.lines(listOf(file), now).toList()

        assertEquals(listOf(recent), kept)
    }
}
