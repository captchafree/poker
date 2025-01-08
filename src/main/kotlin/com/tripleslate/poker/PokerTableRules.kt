package com.tripleslate.poker

/**
 * Currently unused...
 */

data class PokerTableRules(
    val blinds: Blinds
) {
    companion object {
        fun default(): PokerTableRules {
            return PokerTableRules(
                Blinds(1, 2)
            )
        }
    }
}

data class Blinds(
    val smallBlindAmount: Int,
    val bigBlindAmount: Int,
)
