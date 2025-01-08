package com.tripleslate.poker

class MonteCarloEquityCalculator(
    val numSimulations: Int = 100_000
) {

    fun evaluate(
        numPlayers: Int,
        holeCards: List<Card>,
        communityCards: List<Card>,
        progressListener: ProgressListener = ProgressListener.NOOP,
    ): Float {
        if (numPlayers == 1) {
            return 1f
        }

        var numWins = 0
        var numTies = 0
        var numLosses = 0

        for (i in 1..numSimulations) {
            val pokerTable = PokerTableImpl()

            val players = (0 until numPlayers).map {
                DefaultPlayer(it)
            }

            pokerTable.addPlayers(players)

            pokerTable.startNewHandWithSeed(
                fixedHoleCards = mapOf(players.first() to holeCards),
                communityCards = communityCards,
                numCardsToDealPerPlayer = 2
            )

            while (pokerTable.communityCards.size < 5) {
                if (pokerTable.communityCards.isEmpty()) {
                    pokerTable.dealCommunityCards(amount = 3)
                } else if (pokerTable.communityCards.size == 3){
                    pokerTable.dealCommunityCards(amount = 1)
                } else {
                    pokerTable.dealCommunityCards(amount = 1)
                }
            }

            val roundSummary = pokerTable.concludeRound()

            val winners = roundSummary.winners

            if (winners.contains(players.first())) {
                if (winners.size == 1) {
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
