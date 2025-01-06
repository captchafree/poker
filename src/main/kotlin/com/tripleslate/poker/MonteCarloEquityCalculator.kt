package com.tripleslate.poker

import com.tripleslate.com.tripleslate.poker.Card

class MonteCarloEquityCalculator(
    val numSimulations: Int = 100_000
) {

    fun evaluate(
        numPlayers: Int,
        holeCards: List<Card>,
        communityCards: List<Card>,
        progressListener: ProgressListener = ProgressListener.NOOP,
    ): Float {
        var numWins = 0
        var numTies = 0
        var numLosses = 0

        for (i in 1..numSimulations) {
            val pokerGame = PokerGame()

            for (i in 0 until numPlayers) {
                pokerGame.addPlayer(Player(i))
            }

            pokerGame.startNewHandWithSeed(
                fixedHoleCards = mapOf(0 to holeCards),
                communityCards = communityCards,
            )

            while (pokerGame.communityCards.size < 5) {
                if (pokerGame.communityCards.isEmpty()) {
                    pokerGame.dealFlop()
                } else {
                    pokerGame.dealTurnOrRiver()
                }
            }

            val winnerIndexes = pokerGame.getWinners()

            if (winnerIndexes.contains(0)) {
                if (winnerIndexes.size == 1) {
                    numWins++
                } else {
                    numTies++
                }
            } else {
                numLosses++
            }

            progressListener.onProgressUpdate(i, numSimulations)
        }

        return (numWins.toFloat() + numTies.toFloat() * (1f / numPlayers.toFloat())) / (numWins + numLosses + numTies).toFloat()
    }

    fun interface ProgressListener {
        fun onProgressUpdate(progress: Int, total: Int)

        companion object {
            val NOOP = ProgressListener { _, _ -> }
        }
    }
}
