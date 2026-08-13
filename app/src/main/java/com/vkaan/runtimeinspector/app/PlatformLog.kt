package com.vkaan.runtimeinspector.app

import android.content.Context
import android.util.Log
import com.vkaan.runtimeinspector.RuntimeInspector
import com.tokeninc.tsystemwrapper.TSystemServiceBinder
import com.tokeninc.tsystemwrapper.interfaces.IGetLogListener
import com.tokeninc.tsystemwrapper.interfaces.IOnTSystemBound
import java.io.File

/**
 * Token's platform log dump. Nothing here parses the result yet — this run exists to find out what
 * getLog actually hands back and where it puts it.
 */
internal object PlatformLog {

    private const val TAG = "RuntimeInspector"

    // 0 app, 1 system, 2 both.
    private const val LOG_TYPE_BOTH = 2

    // Token writes its own platform output here — getSysLog hardcodes /sdcard/Download/DeviceLog.txt.
    private const val DEST_DIR = "/sdcard/Download/runtimeinspector"

    private const val CARD_SERVICE_PACKAGE = "com.tokeninc.cardservice"

    fun pull(context: Context) {
        val destDir = File(DEST_DIR).apply { mkdirs() }
        Log.i(TAG, "getLog: binding to TSystem, destDir=${destDir.absolutePath}")

        val bound = TSystemServiceBinder.bind(context, object : IOnTSystemBound {
            override fun onConnected() {
                // The platform captures app logs per package, so no logcat tag filtering here.
                val enabled = TSystemServiceBinder.enableAppLog(true)
                val listed = TSystemServiceBinder.setAppLogList(listOf(CARD_SERVICE_PACKAGE))
                Log.i(TAG, "getLog: enableAppLog=$enabled setAppLogList=$listed")
                Log.i(TAG, "getLog: TSystem connected, calling getLog($LOG_TYPE_BOTH)")
                TSystemServiceBinder.getLog(
                    LOG_TYPE_BOTH,
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

    /** Read what landed, then run every line through the card service tracker and its rules. */
    private fun report(destDir: File) {
        // Also the parent, in case savePath is meant to be a file rather than a directory.
        val files = (destDir.walkTopDown() + File("/sdcard/Download").walkTopDown().maxDepth(1))
            .filter { it.isFile }
            .distinct()
            .toList()
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
        RuntimeInspector.readCardServiceLines(DumpReader.lines(files, System.currentTimeMillis()))
    }
}
