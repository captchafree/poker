package com.tripleslate.poker

import java.io.File

fun main(args: Array<String>) {
    val player1 = DefaultPlayer(0, bankroll = 1_000_000f)
    val player2 = DefaultPlayer(1, bankroll = 1_000_000f)
    val player3 = DefaultPlayer(2, bankroll = 1_000_000f)

    val playerStrategies = mapOf(
        player1 to PokerStrategy.useGoodPotOdds(),
        player2 to PokerStrategy.useGoodPotOdds(),
        // player3 to PokerStrategy.useGoodPotOdds(),
        player3 to QLearningPokerStrategy(
            qtableFile = File("/Users/joshbeck/Desktop/poker/qtable")
        ),
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
