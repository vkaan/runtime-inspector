package com.vkaan.runtimeinspector.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

// The only reason this Activity exists: an app that has never been launched is in Android's
// stopped state, where it receives no BOOT_COMPLETED and cannot be bound by another app. The
// button is here so getLog can be tried without a host app.
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        InspectorService.start(this)

        val label = TextView(this).apply {
            text = "Runtime inspector is running.\nFindings go to logcat under the tag RuntimeInspector."
            textSize = 18f
        }
        val button = Button(this).apply {
            text = "İncele"
            setOnClickListener { PlatformLog.pull(this@MainActivity) }
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 48, 48, 48)
                addView(label)
                addView(button)
            }
        )
    }
}
