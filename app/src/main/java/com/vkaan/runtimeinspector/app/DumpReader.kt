package com.vkaan.runtimeinspector.app

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Turns whatever getLog wrote into lines, newest window only: the tail of the live buffer, dated
 * lines inside the window and whatever undated lines follow them.
 */
internal object DumpReader {

    private const val TAG = "RuntimeInspector"

    // The platform flushes the log in ~128KiB blocks, so a line can reach the file half an hour
    // after it happened. Shorter than that and we drop lines that only just arrived.
    const val WINDOW_MILLIS = 60 * 60 * 1000L

    // A 13-minute window measured 182 lines, so this is a wide margin over what we need.
    private const val TAIL_BYTES = 2 * 1024 * 1024L

    private val STAMP = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    // logcat's threadtime prefix "08-11 13:45:02.123", with an optional year in front and a comma
    // allowed for the millis — the dump's exact shape is still unconfirmed.
    private val TIMESTAMP =
        Regex("""^\[?(?:(\d{4})-)?(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})[.,](\d{3})""")

    /**
     * [windowMillis] is how far back a line may be stamped. A hand-triggered read passes
     * [Long.MAX_VALUE]: whatever is in the tail is what the user asked to see, however old.
     */
    fun lines(
        files: List<File>,
        nowMillis: Long,
        windowMillis: Long = WINDOW_MILLIS,
    ): Sequence<String> {
        var dated = 0
        var undated = 0
        var lastKept = false
        var newest = 0L

        // Runs while the file is still being read, so the old lines never reach the heap.
        fun keep(line: String): Boolean {
            val millis = timestampMillis(line, nowMillis)
            if (millis == null) {
                undated++
                // Either a continuation of the line above — a stack trace, a wrapped payload — or
                // one of the dump's section headers and the binary blocks between them. Follow what
                // the last dated line decided.
                return lastKept
            }
            dated++
            newest = maxOf(newest, millis)
            lastKept = nowMillis - millis <= windowMillis
            return lastKept
        }

        val kept = files.flatMap { file ->
            // The platform writes this folder too, so a file can be gone by the time we open it.
            // exists() would be the same race one line earlier.
            try {
                // The zips are the platform's rotated logs — applog_20260811-133246.txt and older.
                // Never in the window, 5MB to inflate.
                if (file.extension.equals("zip", ignoreCase = true)) {
                    Log.i(TAG, "Rotated log skipped: ${file.name}")
                    emptyList()
                } else {
                    tailLines(file, ::keep)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Dump unreadable: ${file.name} — ${e.message}")
                emptyList()
            }
        }
        // newest= is the tell when kept=0: the platform flushes late, so the dump's freshest line
        // can be older than the window even though the transaction already happened.
        val newestStamp =
            if (newest == 0L) "none" else STAMP.format(Date(newest))
        Log.i(
            TAG,
            "Dump lines: kept=${kept.size} dated=$dated undated=$undated newest=$newestStamp",
        )
        return kept.asSequence()
    }

    /**
     * Everything the buffer gained since [fromByte] — the slice a manual İncele runs the rules on.
     * No time window: whatever was appended after the last read (or the last Temizle) is new, however
     * it is stamped. [fromByte] is floored to the tail so a first read (fromByte 0) still can't drag
     * in days of history.
     */
    fun linesFrom(file: File, fromByte: Long): List<String> =
        file.inputStream().use { stream ->
            val start = maxOf(fromByte, file.length() - TAIL_BYTES).coerceIn(0, file.length())
            val reader = if (start == 0L) {
                stream.bufferedReader()
            } else {
                stream.channel.position(start)
                // The cut lands mid-line; drop the partial one.
                stream.bufferedReader().apply { readLine() }
            }
            reader.readLines()
        }

    /**
     * Only the tail. The platform's buffer holds days — it grew 8983288 -> 9114351 bytes between two
     * pulls, so it appends and the window can only be at the end. Reading all of it took 114s on the
     * terminal.
     */
    private fun tailLines(file: File, keep: (String) -> Boolean): List<String> =
        file.inputStream().use { stream ->
            val skip = (file.length() - TAIL_BYTES).coerceAtLeast(0)
            val reader = if (skip == 0L) {
                stream.bufferedReader()
            } else {
                stream.channel.position(skip)
                // The cut lands mid-line, and mid-character if the bytes are binary.
                stream.bufferedReader().apply { readLine() }
            }
            reader.lineSequence().filter(keep).toList()
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
