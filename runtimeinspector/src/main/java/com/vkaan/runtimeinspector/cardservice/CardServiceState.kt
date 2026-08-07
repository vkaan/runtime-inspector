package com.vkaan.runtimeinspector.cardservice

enum class CardServiceState {
    IDLE,
    READ_CARD,
    CONTINUE_EMV,
    FULL_EMV,
    COMPLETED;

    val requiresCompletion: Boolean
        get() = this == CONTINUE_EMV || this == FULL_EMV
}
