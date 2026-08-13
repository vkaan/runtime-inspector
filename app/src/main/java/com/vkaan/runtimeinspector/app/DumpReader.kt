package com.vkaan.runtimeinspector.app

import android.util.Log
import java.io.File
import java.util.Calendar
import java.util.zip.ZipFile

/**
 * Turns whatever getLog wrote into lines, newest window only. The dump format is unconfirmed, so
 * both a plain text file and a zip are handled, and a line with no parsable timestamp is kept.
 */
internal object DumpReader {

    private const val TAG = "RuntimeInspector"

    private const val WINDOW_MILLIS = 15 * 60 * 1000L

    // logcat's threadtime prefix: "08-11 13:45:02.123 ..."
    private val TIMESTAMP = Regex("""^(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})\.(\d{3})""")

    fun lines(files: List<File>, nowMillis: Long): Sequence<String> =
        files.asSequence().flatMap { file -> read(file) }.filter { keep(it, nowMillis) }

    private fun read(file: File): Sequence<String> =
        if (file.extension.equals("zip", ignoreCase = true)) readZip(file) else file.readLines().asSequence()

    private fun readZip(file: File): Sequence<String> = try {
        val zip = ZipFile(file)
        zip.entries().asSequence()
            .filterNot { it.isDirectory }
            .flatMap { entry ->
                zip.getInputStream(entry).bufferedReader().readLines().asSequence()
            }
    } catch (e: Exception) {
        Log.w(TAG, "Dump zip unreadable: ${file.name} — ${e.message}")
        emptySequence()
    }

    /** Lines older than the window are dropped; anything we cannot date is kept. */
    private fun keep(line: String, nowMillis: Long): Boolean {
        val millis = timestampMillis(line, nowMillis) ?: return true
        return nowMillis - millis <= WINDOW_MILLIS
    }

    private fun timestampMillis(line: String, nowMillis: Long): Long? {
        val m = TIMESTAMP.find(line) ?: return null
        val (month, day, hour, minute, second, milli) = m.destructured
        // The dump carries no year, so take the current one and step back if that lands ahead.
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.MONTH, month.toInt() - 1)
            set(Calendar.DAY_OF_MONTH, day.toInt())
            set(Calendar.HOUR_OF_DAY, hour.toInt())
            set(Calendar.MINUTE, minute.toInt())
            set(Calendar.SECOND, second.toInt())
            set(Calendar.MILLISECOND, milli.toInt())
        }
        if (calendar.timeInMillis > nowMillis) calendar.add(Calendar.YEAR, -1)
        return calendar.timeInMillis
    }
}
