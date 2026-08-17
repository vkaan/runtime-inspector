package com.vkaan.runtimeinspector.app

import android.content.Context
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

    fun pull(context: Context) {
        val destDir = File(DEST_DIR).apply { mkdirs() }
        Log.i(TAG, "getLog: binding to TSystem, destDir=${destDir.absolutePath}")

        val bound = TSystemServiceBinder.bind(context, object : IOnTSystemBound {
            override fun onConnected() {
                Log.i(TAG, "getLog: TSystem connected, calling getLog($LOG_TYPE_APP)")
                TSystemServiceBinder.getLog(
                    LOG_TYPE_APP,
                    destDir.absolutePath,
                    object : IGetLogListener {
                        override fun onSuccess(result: String, code: Int) {
                            Log.i(TAG, "getLog onSuccess: result=\"$result\" code=$code")
                            report(destDir)
                        }

                        override fun onError(error: Int) {
                            Log.e(TAG, "getLog onError: $error")
                            report(destDir)
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
    private fun report(destDir: File) = try {
        readAndReport(destDir)
    } catch (e: Exception) {
        Log.e(TAG, "getLog: report failed — ${e.message}", e)
    }

    private fun readAndReport(destDir: File) {
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
        if (bufferBytes > 0L && bufferBytes == lastBufferBytes) {
            Log.i(TAG, "getLog: $BUFFER_NAME still $bufferBytes bytes — nothing flushed, not read.")
            return
        }
        lastBufferBytes = bufferBytes
        val kept = DumpReader.lines(files, System.currentTimeMillis()).toList()
        keepOnlyTrimmed(destDir, files, kept)
        RuntimeInspector.readCardServiceLines(kept.asSequence())
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
