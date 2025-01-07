package com.tripleslate.poker

class PokerHandSimulator(
    private val players: Map<out Player, PokerStrategy>
) {

    fun runSimulation(): PokerTable.RoundSummary {
        val pokerTable = PokerTableImpl()
        val readOnlyPokerTable = ReadOnlyPokerTable(pokerTable)
        val turnBasedPokerTable = TurnAwarePokerTable(pokerTable)

        for ((player, _) in players) {
            pokerTable.addPlayer(player)
        }

        turnBasedPokerTable.startNewHand()

        while (turnBasedPokerTable.isRoundActive()) {
            val nextToAct = turnBasedPokerTable.getNextToAct()

            val env = object : PokerStrategyEnvironment {

                override val currentPlayer: Player
                    get() = nextToAct

                override fun fold() {
                    turnBasedPokerTable.playerAction(nextToAct, TurnAwarePokerTable.Action.FOLD)
                }

                override fun check() {
                    turnBasedPokerTable.playerAction(nextToAct, TurnAwarePokerTable.Action.CHECK)
                }

                override fun checkOrFold() {
                    try {
                        turnBasedPokerTable.playerAction(nextToAct, TurnAwarePokerTable.Action.CHECK)
                    } catch (e: Exception) {
                        fold()
                    }
                }

                override fun call() {
                    try {
                        turnBasedPokerTable.playerAction(nextToAct, TurnAwarePokerTable.Action.CALL)
                    } catch (e: Exception) {
                        checkOrFold()
                    }
                }

                override fun raise(amount: Int) {
                    turnBasedPokerTable.playerAction(nextToAct, TurnAwarePokerTable.Action.RAISE, amount)
                }
            }

            val strategy = players[nextToAct]!!

            strategy.executeTurn(readOnlyPokerTable, env)
        }

        return turnBasedPokerTable.concludeRound().also { roundSummary ->
            players.forEach {
                it.value.onHandCompleted(readOnlyPokerTable, roundSummary)
            }
        }
    }

}
