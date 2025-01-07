package com.tripleslate.poker

import java.io.*
import kotlin.math.ln
import kotlin.random.Random

class QLearningPokerStrategy : PokerStrategy {

    private var qTable: HashMap<StateActionPair, Double> = HashMap()
    private val learningRate = 0.8
    private val discountFactor = 0.95
    private val explorationRate = 0.10

    private var itrCount = 0

    init {
        FileInputStream(File("/Users/joshbeck/Desktop/poker/qtable")).use { fileInputStream ->
            ObjectInputStream(fileInputStream).use { objectInputStream ->
                qTable = objectInputStream.readObject() as HashMap<StateActionPair, Double>
                println(qTable.size)
            }
        }
    }

    override fun executeTurn(table: ReadOnlyPokerTable, environment: PokerStrategyEnvironment) {
        val state = extractState(table, environment)
        val actions = listOf("fold", "check", "call", "raise", "raise2", "raise4")
        val action = chooseAction(state, actions)

        // Perform the chosen action
        when (action) {
            "fold" -> environment.fold()
            "check" -> environment.checkOrFold()
            "call" -> environment.call()
            "raise" -> {
                val raiseAmount = (table.getPotTotal() * 0.15).toInt().coerceAtLeast(2)
                environment.raise(raiseAmount)
            }
            "raise2" -> {
                val raiseAmount = (table.getPotTotal() * 0.35).toInt().coerceAtLeast(4)
                environment.raise(raiseAmount)
            }
            "raise4" -> {
                val raiseAmount = (table.getPotTotal() * 0.5).toInt().coerceAtLeast(8)
                environment.raise(raiseAmount)
            }
            "raise8" -> {
                val raiseAmount = (table.getPotTotal() * 0.75).toInt().coerceAtLeast(16)
                environment.raise(raiseAmount)
            }
        }

        // Record action for later reward processing
        recordAction(environment.currentPlayer, state, action)
    }

    override fun onHandCompleted(table: ReadOnlyPokerTable, summary: PokerTable.RoundSummary) {
        for (player in table.players) {
            val change = if (player in summary.winners) {
                ((summary.playerBets[player] ?: 0) * -1f) + (summary.totalPotSize.toFloat() / summary.winners.size.toFloat())
            } else {
                (summary.playerBets[player] ?: 0) * -1f
            }

            val opponentHands = summary.holeCards.filter {
                it.key != player
            }.map {
                it.key to (summary.communityCards + it.value)
            }.toList()

            val handRanks = opponentHands.sortedWith(compareBy(HandRankUtils.handComparator()) { it.second })

            val winners = buildSet {
                var maxHand = handRanks.last()
                add(maxHand.first)

                for (handRank in handRanks.subList(0, handRanks.size - 1)) {
                    if (HandRankUtils.handComparator().compare(handRank.second, maxHand.second) == 0) {
                        add(handRank.first)
                    }
                }
            }

            val wouldHaveLost = player !in winners

            val reward = if (wouldHaveLost && player in summary.foldedPlayers) {
                5.0
            } else if (!wouldHaveLost && player in summary.foldedPlayers) {
                change.toDouble() * 2
            } else {
                change.toDouble()
            }

            updateQTable(player, reward)
        }

        if (++itrCount % 100 == 0) {
            FileOutputStream("/Users/joshbeck/Desktop/poker/qtable").use { fileOutputStream ->
                ObjectOutputStream(fileOutputStream).use { objectOutputStream ->
                    objectOutputStream.writeObject(qTable)
                }
            }
        }
    }

    /**
     * Extract the current state of the game relevant for Q-learning.
     */
    private fun extractState(table: ReadOnlyPokerTable, environment: PokerStrategyEnvironment): GameState {
        val monteCarloEquityCalculator = MonteCarloEquityCalculator(2500)

        val equity = monteCarloEquityCalculator.evaluate(
            numPlayers = table.activePlayers.size,
            holeCards = table.holeCardsForPlayer(environment.currentPlayer),
            communityCards = table.communityCards,
        )

        val potSize = table.getPotTotal()
        val amountToCall = (table.roundBets.maxByOrNull { it.value }?.value ?: 0)
        - (table.roundBets[environment.currentPlayer] ?: 0)

        val potOdds = amountToCall.toFloat() / (potSize + amountToCall).toFloat()

        fun computeRatioMapped(value1: Double, value2: Double): Double {
            // Handle edge case where value2 is zero to avoid division by zero
            if (value2 == 0.0) {
                return 0.0
            }

            val ratio = value1 / value2

            // Use logarithmic scaling to map the ratio to the range [-100, 100]
            val mappedValue = when {
                ratio < 1 -> -25 * ln(1 / ratio) // Negative for ratios < 1
                else -> 25 * ln(ratio) // Positive for ratios >= 1
            }

            // Clamp to the range [-100, 100]
            return mappedValue.coerceIn(-100.0, 100.0)
        }

        val ratio = computeRatioMapped(equity.toDouble(), potOdds.toDouble())

        return GameState(
            ratio = ratio.toInt(), // .also { println("${equity} / $potOdds = $it") },
            gamePhase = when (table.communityCards.size) {
                0 -> 0
                in 1..3 -> 1
                4 -> 2
                5 -> 3
                else -> 4
            },
            remainingPlayers = table.activePlayers.size
//            holeCards = table.holeCardsForPlayer(environment.currentPlayer),
//            communityCards = table.communityCards,
//            equity = (equity * 100).toInt(),
//            potOdds = (potOdds * 100).toInt(),
//            potSize = when (table.getPotTotal()) {
//                in 1.. 10 -> 0
//                in 11.. 50 -> 1
//                in 50.. 150 -> 2
//                else -> 3
//            },
            // playerStack = environment.currentPlayer.bankroll.toInt(),
            //activePlayers = table.activePlayers.size,
        )
    }

    /**
     * Choose an action using an epsilon-greedy policy.
     */
    private fun chooseAction(state: GameState, actions: List<String>): String {
        return if (Random.nextDouble() < explorationRate) {
            actions.random() // Explore
        } else {
            // Exploit: Choose the action with the highest Q-value
            actions.maxByOrNull { qTable.getOrDefault(StateActionPair(state, it), 0.0) } ?: actions.random()
        }
    }

    private val actionLog: MutableMap<Player, MutableList<StateActionPair>> = mutableMapOf()

    /**
     * Record the player's action for reward processing.
     */
    private fun recordAction(player: Player, state: GameState, action: String) {
        val stateActionPair = StateActionPair(state, action)
        actionLog.computeIfAbsent(player) { mutableListOf() }.add(stateActionPair)
    }

    /**
     * Update the Q-table after receiving a reward.
     */
    fun updateQTable(player: Player, finalReward: Double) {
        val playerActions = actionLog[player] ?: return
        var nextMaxQ = 0.0 // Max Q-value for the next state

        // Iterate through actions in reverse to apply rewards backward
        for (stateActionPair in playerActions.asReversed()) {
            val currentQ = qTable.getOrDefault(stateActionPair, 0.0)
            val updatedQ = currentQ + learningRate * (finalReward + (discountFactor * nextMaxQ) - currentQ)
            qTable[stateActionPair] = updatedQ

            // Update `nextMaxQ` for the next iteration
            nextMaxQ = qTable.filterKeys { it.state == stateActionPair.state }
                .values.maxOrNull() ?: 0.0
        }

        // Clear the player's action log after updating
        actionLog.remove(player)
    }
}

/**
 * Represents the state of the game relevant for Q-learning.
 */
class GameState(
    // val holeCards: List<Card>,
    // val communityCards: List<Card>,
    // val equity: Int,
    // val potOdds: Int,
    val ratio: Int,
    val gamePhase: Int,
    val remainingPlayers: Int,
) : Serializable {
    companion object {
        const val serialVersionUID = 42L;
    }
}

/**
 * Combines a game state and action into a unique key for the Q-table.
 */
data class StateActionPair(val state: GameState, val action: String) : Serializable {
    companion object {
        const val serialVersionUID = 43L;
    }
}
