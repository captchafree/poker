package com.tripleslate.poker

interface PokerStrategy {

    fun executeTurn(
        game: TurnAwarePokerTable,
        environment: PokerStrategyEnvironment
    )

    companion object
}

interface PokerStrategyEnvironment {
    val currentPlayer: Player
    fun fold()
    fun check()
    fun checkOrFold()
    fun call()
    fun raise(amount: Int)
}
