package com.vkaan.runtimeinspector.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.vkaan.runtimeinspector.RuntimeInspector
import com.vkaan.runtimeinspector.rules.Risk

/** A card's identity: rule + subject. Shared with the View so it can ask if a card is open. */
internal fun riskKey(ruleId: String, subject: String?) = "$ruleId:${subject.orEmpty()}"

/** Everything the screen draws, already sorted and split — the View does no deciding. */
data class UiState(
    val opened: Risk?,          // the finding a notification was tapped on, if it is still in memory
    val openRuleId: String?,    // set even when [opened] is null, so the View can say it is gone
    val rest: List<Risk>,       // newest first, [opened] removed
    val expanded: Set<String>,
    val helpFor: Risk?,
    val confirmingClear: Boolean,
)

/**
 * The screen's state and the actions on it. Survives rotation, and holds no View reference, so the
 * list-shaping and the button logic can be read (and tested) without an Activity. The findings
 * themselves live in the Model ([RuntimeInspector]); this reads them each [state] call.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private var openRuleId: String? = null
    private var openSubject: String? = null
    private val expanded = mutableSetOf<String>()
    private var helpFor: Risk? = null
    private var confirmingClear = false

    /** Arrived from a notification: remember which card, and start it open. */
    fun openFromNotification(ruleId: String?, subject: String?) {
        openRuleId = ruleId
        openSubject = subject
        ruleId?.let { expanded += riskKey(it, subject) }
    }

    fun incele() = PlatformLog.pull(getApplication(), force = true)

    fun startClear() { confirmingClear = true }
    fun cancelClear() { confirmingClear = false }
    fun confirmClear() { PlatformLog.clear(); confirmingClear = false }

    fun toggle(key: String) {
        if (key in expanded) expanded -= key else expanded += key
    }

    fun openHelp(risk: Risk) { helpFor = risk }
    fun closeHelp() { helpFor = null }

    /** Back unwinds the help view first; true means it handled the press. */
    fun back(): Boolean {
        if (helpFor == null) return false
        helpFor = null
        return true
    }

    fun state(): UiState {
        val risks = RuntimeInspector.risks()
        val opened = risks.firstOrNull { it.ruleId == openRuleId && it.subject == openSubject }
        return UiState(
            opened = opened,
            openRuleId = openRuleId,
            rest = risks.filter { it != opened }.sortedByDescending { it.lastTimestampMillis },
            expanded = expanded.toSet(),
            helpFor = helpFor,
            confirmingClear = confirmingClear,
        )
    }
}
