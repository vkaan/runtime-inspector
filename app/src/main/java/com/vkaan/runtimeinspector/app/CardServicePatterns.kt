package com.vkaan.runtimeinspector.app

import com.vkaan.runtimeinspector.cardservice.CardServiceApi
import com.vkaan.runtimeinspector.cardservice.CardServiceLogPattern
import com.vkaan.runtimeinspector.cardservice.CardServiceState

/**
 * The card service lines we know how to read. They live here now that the inspector app pulls the
 * log itself — the app under test no longer configures anything.
 */
internal val CARD_SERVICE_PATTERNS = listOf(
    CardServiceLogPattern(
        Regex("""getCard called with config:.*"emvProcessType"\s*:\s*1\b"""),
        CardServiceApi.GET_CARD,
        CardServiceState.READ_CARD,
    ),
    CardServiceLogPattern(
        Regex("""getCard called with config:.*"emvProcessType"\s*:\s*2\b"""),
        CardServiceApi.GET_CARD,
        CardServiceState.CONTINUE_EMV,
    ),
    CardServiceLogPattern(
        Regex("""getCard called with config:.*"emvProcessType"\s*:\s*3\b"""),
        CardServiceApi.GET_CARD,
        CardServiceState.FULL_EMV,
    ),
    CardServiceLogPattern(
        Regex("getOnlinePIN"),
        CardServiceApi.GET_ONLINE_PIN,
    ),
    CardServiceLogPattern(
        Regex("completeEmv", RegexOption.IGNORE_CASE),
        CardServiceApi.COMPLETE_EMV,
        CardServiceState.COMPLETED,
    ),
    CardServiceLogPattern(
        Regex("takeOutICC", RegexOption.IGNORE_CASE),
        CardServiceApi.TAKE_OUT_ICC,
    ),
    CardServiceLogPattern(
        Regex("setEMVConfiguration"),
        CardServiceApi.SET_EMV_CONFIG,
    ),
    CardServiceLogPattern(
        Regex("setEMVCLConfiguration"),
        CardServiceApi.SET_EMV_CL_CONFIG,
    ),
    CardServiceLogPattern(
        Regex("A client is bound"),
        CardServiceApi.BIND,
    ),
)
