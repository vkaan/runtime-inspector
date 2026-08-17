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

    // logcat's threadtime prefix "08-11 13:45:02.123", with an optional year in front and a comma
    // allowed for the millis — the dump's exact shape is still unconfirmed.
    private val TIMESTAMP =
        Regex("""^\[?(?:(\d{4})-)?(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})[.,](\d{3})""")

    /** Newest line already analysed, so the next pull does not re-report the same window. */
    var lastProcessedMillis = 0L

    fun lines(files: List<File>, nowMillis: Long): Sequence<String> {
        var dated = 0
        var undated = 0
        var newest = lastProcessedMillis

        // Runs while the file is still being read, so the old lines never reach the heap.
        fun keep(line: String): Boolean {
            val millis = timestampMillis(line, nowMillis)
            if (millis == null) {
                undated++
                // A line we cannot date may still be one we need.
                return true
            }
            dated++
            newest = maxOf(newest, millis)
            return nowMillis - millis <= WINDOW_MILLIS && millis > lastProcessedMillis
        }

        val kept = files.flatMap { file ->
            if (file.extension.equals("zip", ignoreCase = true)) zipLines(file, ::keep)
            else file.useLines { lines -> lines.filter(::keep).toList() }
        }
        // Tells us whether the timestamp shapes above actually match the dump.
        Log.i(TAG, "Dump lines: kept=${kept.size} dated=$dated undated=$undated")
        lastProcessedMillis = newest
        return kept.asSequence()
    }

    // Filter and collect inside use() — a lazy sequence would outlive the closed file and slip
    // its reads past the catch.
    private fun zipLines(file: File, keep: (String) -> Boolean): List<String> = try {
        ZipFile(file).use { zip ->
            zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .flatMap { entry -> zip.getInputStream(entry).bufferedReader().lineSequence() }
                .filter(keep)
                .toList()
        }
    } catch (e: Exception) {
        Log.w(TAG, "Dump zip unreadable: ${file.name} — ${e.message}")
        emptyList()
    }

    private fun timestampMillis(line: String, nowMillis: Long): Long? {
        val m = TIMESTAMP.find(line) ?: return null
        val (year, month, day, hour, minute, second, milli) = m.destructured
        // Without a year in the line, take the current one and step back if that lands ahead.
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            if (year.isNotEmpty()) set(Calendar.YEAR, year.toInt())
            set(Calendar.MONTH, month.toInt() - 1)
            set(Calendar.DAY_OF_MONTH, day.toInt())
            set(Calendar.HOUR_OF_DAY, hour.toInt())
            set(Calendar.MINUTE, minute.toInt())
            set(Calendar.SECOND, second.toInt())
            set(Calendar.MILLISECOND, milli.toInt())
        }
        if (year.isEmpty() && calendar.timeInMillis > nowMillis) calendar.add(Calendar.YEAR, -1)
        return calendar.timeInMillis
    }
}
