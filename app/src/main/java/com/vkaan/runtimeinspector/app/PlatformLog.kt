package com.vkaan.runtimeinspector.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.vkaan.runtimeinspector.RuntimeInspector
import com.tokeninc.tsystemwrapper.TSystemServiceBinder
import com.tokeninc.tsystemwrapper.interfaces.IGetLogListener
import com.tokeninc.tsystemwrapper.interfaces.IOnTSystemBound
import java.io.File

/** Token's platform log dump: turn capture on at startup, pull it on demand. */
internal object PlatformLog {

    private const val TAG = "RuntimeInspector"

    // 0 app, 1 system, 2 both. We only list the card service package, so app is enough.
    private const val LOG_TYPE_APP = 0

    // Token writes its own platform output here — getSysLog hardcodes /sdcard/Download/DeviceLog.txt.
    private const val DEST_DIR = "/sdcard/Download/runtimeinspector"

    private const val CARD_SERVICE_PACKAGE = "com.tokeninc.cardservice"

    // What we leave behind in destDir: the window we read, nothing older.
    private const val TRIMMED_NAME = "cardservice-recent.log"

    // The platform's live buffer — the only file in the dump that can hold anything recent.
    private const val BUFFER_NAME = "applog_logbuffer.log"

    /** Size of the buffer at the last read, so an unflushed pull can be skipped. */
    @Volatile
    private var lastBufferBytes = 0L

    /** How far into the buffer İncele has already run the rules — the baseline Temizle moves. */
    @Volatile
    private var fedBytes = 0L

    // The block lands minutes after the transaction, so a pull that finds nothing waits and tries
    // again on a growing gap: the user runs a card test, walks away, and the RISK lines arrive on
    // their own once SUNMI flushes. The gaps grow because every attempt is a full getLog — ~14 MB
    // copied to /sdcard — so an idle terminal isn't rewriting that every minute.
    private val RETRY_DELAYS_MILLIS = longArrayOf(15_000, 30_000, 60_000, 120_000, 300_000)

    private val retryHandler = Handler(Looper.getMainLooper())

    // Where we are on the ladder above. Reset by a fresh pull, walked by the no-growth branch.
    @Volatile
    private var retryIndex = 0

    // A retry fires long after pull() returned, when the calling Service may be gone, so it binds
    // through the application context instead.
    @Volatile
    private var appContext: Context? = null

    /**
     * Capture has to be on before the transaction happens, not when the dump is asked for, so this
     * runs from the service's onCreate.
     */
    fun enableCapture(context: Context) {
        val bound = TSystemServiceBinder.bind(context, object : IOnTSystemBound {
            override fun onConnected() {
                // The platform captures app logs per package, so no logcat tag is configured.
                val enabled = TSystemServiceBinder.enableAppLog(true)
                val listed = TSystemServiceBinder.setAppLogList(listOf(CARD_SERVICE_PACKAGE))
                Log.i(TAG, "capture: enableAppLog=$enabled setAppLogList=$listed")
            }

            override fun onDisconnected() {
                Log.w(TAG, "capture: TSystem disconnected")
            }

            override fun onCrashed() {
                Log.e(TAG, "capture: TSystem crashed")
            }
        })

        if (!bound) Log.e(TAG, "capture: TSystem bind refused — is the TSystem app installed?")
    }

    /**
     * [force] skips the "has the buffer grown" check: someone asked for this read by hand, so
     * re-running the rules on the window we already have is the point.
     */
    fun pull(context: Context, force: Boolean = false) {
        // A fresh pull abandons whatever the last ladder was still chasing and starts it over.
        retryHandler.removeCallbacksAndMessages(null)
        retryIndex = 0
        appContext = context.applicationContext
        doPull(context, force)
    }

    /**
     * Temizle: drop the shown findings and baseline the buffer here, so the next İncele reads only
     * the lines that arrive after this. The buffer itself is SUNMI's and can't be wiped, so this
     * moves where we start reading instead. The archived window is deleted too.
     */
    fun clear() {
        fedBytes = lastBufferBytes
        RuntimeInspector.clearRisks()
        File(DEST_DIR, TRIMMED_NAME).delete()
    }

    private fun doPull(context: Context, force: Boolean) {
        val destDir = File(DEST_DIR).apply { mkdirs() }
        Log.i(TAG, "getLog: binding to TSystem, destDir=${destDir.absolutePath} force=$force")

        val bound = TSystemServiceBinder.bind(context, object : IOnTSystemBound {
            override fun onConnected() {
                Log.i(TAG, "getLog: TSystem connected, calling getLog($LOG_TYPE_APP)")
                TSystemServiceBinder.getLog(
                    LOG_TYPE_APP,
                    destDir.absolutePath,
                    object : IGetLogListener {
                        override fun onSuccess(result: String, code: Int) {
                            Log.i(TAG, "getLog onSuccess: result=\"$result\" code=$code")
                            report(destDir, force)
                        }

                        override fun onError(error: Int) {
                            Log.e(TAG, "getLog onError: $error")
                            report(destDir, force)
                        }
                    },
                )
            }

            override fun onDisconnected() {
                Log.w(TAG, "getLog: TSystem disconnected")
            }

            override fun onCrashed() {
                Log.e(TAG, "getLog: TSystem crashed")
            }
        })

        if (!bound) Log.e(TAG, "getLog: TSystem bind refused — is the TSystem app installed?")
    }

    /**
     * Read what landed, then run every line through the card service tracker and its rules.
     *
     * getLog's listener calls this on a binder thread, so a throw here breaks TSystem's transaction
     * instead of failing the pull — nothing gets out.
     */
    private fun report(destDir: File, force: Boolean) = try {
        readAndReport(destDir, force)
    } catch (e: Exception) {
        Log.e(TAG, "getLog: report failed — ${e.message}", e)
    }

    private fun readAndReport(destDir: File, force: Boolean) {
        // getLog answers with the directory it wrote to — result="/sdcard/Download/runtimeinspector"
        // — so there is no reason to read the rest of Download.
        // Our own output is in here too — reading it back would replay the last window as new events.
        val files = destDir.walkTopDown().filter { it.isFile && it.name != TRIMMED_NAME }.toList()
        if (files.isEmpty()) {
            Log.w(TAG, "getLog: nothing written to ${destDir.absolutePath}")
            return
        }
        for (file in files) {
            val firstLine = try {
                file.useLines { it.firstOrNull() }
            } catch (e: Exception) {
                "<unreadable: ${e.message}>"
            }
            Log.i(TAG, "getLog file: ${file.absolutePath} ${file.length()} bytes | $firstLine")
        }
        // The platform flushes in ~128KiB blocks: 8983288 -> 9114351 -> 9245366 bytes over an
        // afternoon. Same size means the transaction we just watched is not in the file yet.
        val bufferBytes = files.firstOrNull { it.name == BUFFER_NAME }?.length() ?: 0L
        if (!force && bufferBytes > 0L && bufferBytes == lastBufferBytes) {
            Log.i(TAG, "getLog: $BUFFER_NAME still $bufferBytes bytes — nothing flushed, not read.")
            scheduleRetry()
            return
        }
        lastBufferBytes = bufferBytes
        // İncele reads only what the buffer gained since the last read or the last Temizle, so the
        // old lines still sitting in the buffer aren't re-reported. The auto pull keeps its window.
        val kept = if (force) {
            if (bufferBytes < fedBytes) fedBytes = 0L // Buffer rotated — baseline no longer valid.
            val buffer = files.firstOrNull { it.name == BUFFER_NAME }
            val delta = if (buffer != null) DumpReader.linesFrom(buffer, fedBytes) else emptyList()
            fedBytes = bufferBytes
            delta
        } else {
            DumpReader.lines(files, System.currentTimeMillis(), DumpReader.WINDOW_MILLIS).toList()
        }
        keepOnlyTrimmed(destDir, files, kept)
        RuntimeInspector.readCardServiceLines(kept.asSequence())
    }

    /**
     * The buffer hadn't grown, so the transaction isn't in the file yet. Wait the next gap and try
     * again — a read that finally sees growth just doesn't come back here, so the ladder ends on its
     * own. Only one attempt is ever pending.
     */
    private fun scheduleRetry() {
        val ctx = appContext ?: return
        if (retryIndex >= RETRY_DELAYS_MILLIS.size) {
            Log.i(TAG, "getLog: buffer never grew after ${RETRY_DELAYS_MILLIS.size} tries — waiting for the next unbind.")
            return
        }
        val delay = RETRY_DELAYS_MILLIS[retryIndex++]
        // Each attempt is a full getLog: ~14 MB copied to /sdcard.
        Log.i(TAG, "getLog: retry $retryIndex/${RETRY_DELAYS_MILLIS.size} in ${delay / 1000}s.")
        retryHandler.postDelayed({ doPull(ctx, force = false) }, delay)
    }

    /**
     * The platform writes its whole history every time, days of it. We keep the window we actually
     * read and drop the raw dump, so the folder holds that window and not two days.
     */
    private fun keepOnlyTrimmed(destDir: File, files: List<File>, kept: List<String>) {
        // Otherwise a pull that kept nothing blanks the last good window and deletes the dump that
        // could tell us why.
        if (kept.isEmpty()) {
            Log.w(TAG, "Nothing kept — raw dumps left in place.")
            return
        }
        val trimmed = File(destDir, TRIMMED_NAME)
        try {
            trimmed.writeText(kept.joinToString("\n"))
        } catch (e: Exception) {
            Log.w(TAG, "Could not write ${trimmed.absolutePath}: ${e.message}")
            return
        }
        // Only inside our own folder — /sdcard/Download holds files that are not ours.
        val dropped = files.count { it != trimmed && it.parentFile == destDir && it.delete() }
        Log.i(TAG, "Trimmed to ${trimmed.absolutePath}: ${kept.size} lines, $dropped raw dumps deleted")
    }
}
