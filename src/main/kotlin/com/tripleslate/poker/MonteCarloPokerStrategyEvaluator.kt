package com.tripleslate.poker

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
            val simulator = PokerHandSimulator(players)

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
