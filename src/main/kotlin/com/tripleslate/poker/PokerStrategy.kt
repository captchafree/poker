package com.tripleslate.poker

import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

interface PokerStrategy {

    fun executeTurn(
        table: ReadOnlyPokerTable,
        environment: PokerStrategyEnvironment
    )

    fun onHandCompleted(table: ReadOnlyPokerTable, summary: PokerTable.RoundSummary) {}

    companion object {
        fun alwaysFold() = AlwaysFold
        fun alwaysRaise() = AlwaysRaise
        fun alwaysCall() = AlwaysCall
        fun alwaysCheckOrFold() = AlwaysCheckOrFold
        fun randomAction() = RandomAction
        fun useGoodPotOdds() = GoodPotOdds
    }
}

interface PokerStrategyEnvironment {

    val currentPlayer: Player

    fun fold()

    fun check()
    fun checkOrFold()

    fun call()

    fun raise(amount: Int)
}


object AlwaysCall : PokerStrategy {

    override fun executeTurn(table: ReadOnlyPokerTable, environment: PokerStrategyEnvironment) {
        environment.call()
    }
}

object AlwaysCheckOrFold : PokerStrategy {

    override fun executeTurn(table: ReadOnlyPokerTable, environment: PokerStrategyEnvironment) {
        environment.checkOrFold()
    }
}

object AlwaysFold : PokerStrategy {

    override fun executeTurn(table: ReadOnlyPokerTable, environment: PokerStrategyEnvironment) {
        environment.fold()
    }
}

object AlwaysRaise : PokerStrategy {

    override fun executeTurn(table: ReadOnlyPokerTable, environment: PokerStrategyEnvironment) {
        environment.raise(amount = 2)
    }
}

object RandomAction : PokerStrategy {

    override fun executeTurn(table: ReadOnlyPokerTable, environment: PokerStrategyEnvironment) {
        when (Random.nextInt(4)) {
            0 -> environment.fold()
            1 -> environment.checkOrFold()
            2 -> environment.call()
            3 -> environment.raise(amount = 2)
        }
    }
}

object GoodPotOdds : PokerStrategy {

    override fun executeTurn(table: ReadOnlyPokerTable, environment: PokerStrategyEnvironment) {
        val monteCarloEquityCalculator = MonteCarloEquityCalculator(100)

        val equity = monteCarloEquityCalculator.evaluate(
            numPlayers = table.activePlayers.size,
            holeCards = table.holeCardsForPlayer(environment.currentPlayer),
            communityCards = table.communityCards,
        )

        val potSize = table.getPotTotal()
        val amountToCall = (table.roundBets.maxByOrNull { it.value }?.value ?: 0)
        - (table.roundBets[environment.currentPlayer] ?: 0)

        val potOdds = amountToCall.toFloat() / (potSize + amountToCall).toFloat()

//        println("Hole cards ${game.holeCardsForPlayer(environment.currentPlayer)}")
//        println("Community cards ${game.getCommunityCards()}")
//        println("Remaining players ${game.getActivePlayers()}")
//        println("Equity: ${equity}")
//        println("Pot Odds: ${potOdds}")
//        Thread.sleep(1000)

        if (equity > potOdds) {
            if (amountToCall == 0) {
                if (Random.nextBoolean()) {
                    if ((table.roundBets[environment.currentPlayer] ?: 0) >= (environment.currentPlayer.bankroll * 0.05)) {
                        environment.call()
                        return
                    }

                    environment.raise(amount = max(1, ceil(Random.nextInt(1, 2) * equity * potSize).toInt()))
                } else {
                    environment.call()
                }
            } else {
                when (Random.nextInt(3)) {
                    0 -> environment.call()
                    1 -> environment.raise(amount = 2)
                    2 -> {
                        if ((table.roundBets[environment.currentPlayer] ?: 0) >= (environment.currentPlayer.bankroll * 0.05)) {
                            environment.call()
                            return
                        }

                        val randomPart = Random.nextInt(1, 4)

                        val raiseAmount = ceil(Random.nextInt(1, 2) * equity * potSize).toInt()
                        // println("[$equity] [$potSize] [$randomPart] = $raiseAmount")

//                        for (e in game.underlyingGame.pot) {
//                            println("${e.key}: ${e.value}")
//                        }

                        environment.raise(amount = max(1, 2))
                    }
                }
            }
        } else {
            environment.checkOrFold()
        }
    }
}
