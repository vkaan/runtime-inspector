package com.vkaan.runtimeinspector.cardservice

data class CardServiceLogPattern(
    val regex: Regex,
    val state: CardServiceState,
)

internal class CardServiceLogState(
    private val patterns: List<CardServiceLogPattern>,
) {

    var state: CardServiceState = CardServiceState.IDLE
        private set

    var sinceNanos: Long = 0L
        private set

    var lastLine: String? = null
        private set

    data class Transition(
        val from: CardServiceState,
        val to: CardServiceState,
        val elapsedRealtimeNanos: Long,
        val line: String,
    )

    fun onLine(line: String, elapsedRealtimeNanos: Long): Transition? {
        val matched = patterns.firstOrNull { it.regex.containsMatchIn(line) } ?: return null
        if (matched.state == state) return null

        val transition = Transition(
            from = state,
            to = matched.state,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            line = line,
        )
        state = matched.state
        sinceNanos = elapsedRealtimeNanos
        lastLine = line
        return transition
    }
}
