package com.tripleslate.poker

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

class PokerSimulator(
    private val players: Map<out Player, PokerStrategy>
) {

    fun runSimulation(): PokerGame.RoundSummary {
        val pokerGame = PokerGame()
        val pokerFacade = PokerGameFacade(pokerGame)

        for ((player, _) in players) {
            pokerGame.addPlayer(player)
        }

        pokerFacade.startNewHand()

        while (pokerFacade.isRoundActive()) {
            val nextToAct = pokerFacade.getNextToAct()

            val env = object : PokerStrategyEnvironment {

                override val currentPlayer: Player
                    get() = nextToAct

                override fun fold() {
                    pokerFacade.playerAction(nextToAct, PokerGameFacade.Action.FOLD)
                }

                override fun check() {
                    pokerFacade.playerAction(nextToAct, PokerGameFacade.Action.CHECK)
                }

                override fun checkOrFold() {
                    try {
                        pokerFacade.playerAction(nextToAct, PokerGameFacade.Action.CHECK)
                    } catch (e: Exception) {
                        // println(e.message)
                        fold()
                    }
                }

                override fun call() {
                    try {
                        pokerFacade.playerAction(nextToAct, PokerGameFacade.Action.CALL)
                    } catch (e: Exception) {
                        // println(e.message)
                        checkOrFold()
                    }
                }

                override fun raise(amount: Int) {
                    pokerFacade.playerAction(nextToAct, PokerGameFacade.Action.RAISE, amount)
                }
            }

            val strategy = players[nextToAct]!!

            strategy.executeTurn(
                pokerFacade,
                env,
            )
        }

        return pokerFacade.concludeRound()
    }

}

object AlwaysCall : PokerStrategy {

    override fun executeTurn(
        game: PokerGameFacade,
        environment: PokerStrategyEnvironment
    ) {
        environment.call()
    }
}

object AlwaysCheckOrFold : PokerStrategy {

    override fun executeTurn(
        game: PokerGameFacade,
        environment: PokerStrategyEnvironment
    ) {
        environment.checkOrFold()
    }
}

object AlwaysFold : PokerStrategy {

    override fun executeTurn(
        game: PokerGameFacade,
        environment: PokerStrategyEnvironment
    ) {
        environment.fold()
    }
}

object AlwaysRaise : PokerStrategy {

    override fun executeTurn(
        game: PokerGameFacade,
        environment: PokerStrategyEnvironment
    ) {
        environment.raise(amount = 2)
    }
}

object RandomAction : PokerStrategy {

    override fun executeTurn(
        game: PokerGameFacade,
        environment: PokerStrategyEnvironment
    ) {
        when (Random.nextInt(4)) {
            0 -> environment.fold()
            1 -> environment.checkOrFold()
            2 -> environment.call()
            3 -> environment.raise(amount = 2)
        }
    }
}

object GoodPotOdds : PokerStrategy {

    override fun executeTurn(
        game: PokerGameFacade,
        environment: PokerStrategyEnvironment
    ) {
        val monteCarloEquityCalculator = MonteCarloEquityCalculator(100)

        val equity = monteCarloEquityCalculator.evaluate(
            numPlayers = game.getActivePlayers().size,
            holeCards = game.holeCardsForPlayer(environment.currentPlayer),
            communityCards = game.getCommunityCards(),
        )

        val potSize = game.getPotTotal()
        val amountToCall = (game.underlyingGame.roundBets.maxByOrNull { it.value }?.value ?: 0)
                            - (game.underlyingGame.roundBets[environment.currentPlayer] ?: 0)

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
                    if ((game.underlyingGame.roundBets[environment.currentPlayer] ?: 0) >= (environment.currentPlayer.bankroll * 0.05)) {
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
                        if ((game.underlyingGame.roundBets[environment.currentPlayer] ?: 0) >= (environment.currentPlayer.bankroll * 0.05)) {
                            environment.call()
                            return
                        }

                        val randomPart = Random.nextInt(1, 4)

                        val raiseAmount = ceil(Random.nextInt(1, 2) * equity * potSize).toInt()
                        println("[$equity] [$potSize] [$randomPart] = $raiseAmount")

                        for (e in game.underlyingGame.pot) {
                            println("${e.key}: ${e.value}")
                        }

                        environment.raise(amount = max(1, 2))
                    }
                }
            }
        } else {
            environment.checkOrFold()
        }
    }
}


class MonteCarloPokerStrategyEvaluator(
    val players: Map<out Player, PokerStrategy>,
    val numSimulations: Int = 1_000
) {

    interface StrategyResults {
        val wins: Int
        val losses: Int
        val draws: Int

        val chipDifference: Float
    }

    data class StrategyResultsImpl(
        override var wins: Int,
        override var losses: Int,
        override var draws: Int,
        override var chipDifference: Float,
    ) : StrategyResults

    fun evaluate(): Map<Player, StrategyResults> {
        val results = players.keys.associateWith {
            StrategyResultsImpl(players.size - 1, 0, 0, 0f)
        }

        val originalChipAmounts = players.keys.associateWith {
            it.bankroll
        }

        for (i in 1..numSimulations) {
            println("Running simulation $i / $numSimulations")
            val simulator = PokerSimulator(players)

            val simulationResults = simulator.runSimulation()

            for ((player, _) in players) {
                if (player in simulationResults.winners) {
                    if (simulationResults.winners.size == 1) {
                        results[player]!!.wins += 1
                    } else {
                        results[player]!!.draws += 1
                    }
                } else {
                    results[player]!!.losses += 1
                }
            }
        }

        for (player in players.keys) {
            results[player]!!.chipDifference = (player.bankroll - originalChipAmounts[player]!!)
        }

        return results
    }
}

fun PokerStrategy.Companion.alwaysFold() = AlwaysFold
fun PokerStrategy.Companion.alwaysRaise() = AlwaysRaise
fun PokerStrategy.Companion.alwaysCall() = AlwaysCall
fun PokerStrategy.Companion.alwaysCheckOrFold() = AlwaysCheckOrFold
fun PokerStrategy.Companion.randomAction() = RandomAction
fun PokerStrategy.Companion.useGoodPotOdds() = GoodPotOdds

fun main() {
    val player1 = DefaultPlayer(0, bankroll = 1_000_000f)
    val player2 = DefaultPlayer(1, bankroll = 1_000_000f)
    val player3 = DefaultPlayer(2, bankroll = 1_000_000f)

    val playerStrategies = mapOf(
        player1 to PokerStrategy.useGoodPotOdds(),
        player2 to PokerStrategy.useGoodPotOdds(),
        player3 to PokerStrategy.useGoodPotOdds(),
    )

    val evaluator = MonteCarloPokerStrategyEvaluator(
        playerStrategies,
        numSimulations = 1000
    )

    println("Running simulation...")

    val evaluationResults = evaluator.evaluate()

    for ((player, result) in evaluationResults) {
        val percentChange = ((player.bankroll - 1_000_000f) / 1_000_000) * 100f
        println("[${player.id}] [$${player.bankroll}] ${result.wins}-${result.losses}-${result.draws} (${result.chipDifference}) ($percentChange%)")
    }
}
