package com.vkaan.runtimeinspector.cardservice

data class CardServiceLogPattern(
    val regex: Regex,
    val api: CardServiceApi,
    val state: CardServiceState? = null,
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

    data class Match(
        val apis: List<CardServiceApi>,
        val from: CardServiceState,
        val to: CardServiceState,
        val elapsedRealtimeNanos: Long,
        val line: String,
    )

    fun onLine(line: String, elapsedRealtimeNanos: Long): Match? {
        val matched = patterns.filter { it.regex.containsMatchIn(line) }
        if (matched.isEmpty()) return null

        val from = state
        val to = matched.firstNotNullOfOrNull { it.state } ?: from
        if (to != from) {
            state = to
            sinceNanos = elapsedRealtimeNanos
        }
        lastLine = line

        return Match(
            apis = matched.map { it.api }.distinct(),
            from = from,
            to = to,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            line = line,
        )
    }
}
