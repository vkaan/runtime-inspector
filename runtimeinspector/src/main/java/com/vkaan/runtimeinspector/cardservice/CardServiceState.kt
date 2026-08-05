package com.vkaan.runtimeinspector.cardservice

enum class CardServiceState {
    IDLE,
    WAITING_CARD,
    CARD_READ,
    ONLINE,
    APPROVED,
    DECLINED,
    ERROR;

    val isTerminal: Boolean
        get() = this == APPROVED || this == DECLINED || this == ERROR
}
