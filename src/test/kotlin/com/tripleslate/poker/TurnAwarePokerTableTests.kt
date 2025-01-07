package com.tripleslate.poker

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TurnAwarePokerTableTest : FunSpec() {

    init {
        test("should set nextToActIndex correctly at the start of a new hand") {
            val players = listOf(
                DefaultPlayer(id = 1, bankroll = 100.0f),
                DefaultPlayer(id = 2, bankroll = 100.0f),
                DefaultPlayer(id = 3, bankroll = 100.0f),
                DefaultPlayer(id = 4, bankroll = 100.0f)
            )
            val pokerTable = PokerTableImpl()
            pokerTable.addPlayers(players)
            val table = TurnAwarePokerTable(pokerTable)

            table.startNewHand()

            table.getNextToAct().id shouldBe 4 // Assuming dealerIndex starts at 0.
        }

        test("should update nextToActIndex correctly after a player action") {
            val players = listOf(
                DefaultPlayer(id = 1, bankroll = 100.0f),
                DefaultPlayer(id = 2, bankroll = 100.0f),
                DefaultPlayer(id = 3, bankroll = 100.0f),
                DefaultPlayer(id = 4, bankroll = 100.0f)
            )
            val pokerTable = PokerTableImpl()
            pokerTable.addPlayers(players)
            val table = TurnAwarePokerTable(pokerTable)

            table.startNewHand()

            val initialDefaultPlayer = table.getNextToAct()
            initialDefaultPlayer.id shouldBe 4

            table.playerAction(initialDefaultPlayer, TurnAwarePokerTable.Action.CALL)

            table.getNextToAct().id shouldBe 1
        }

        test("should wrap around nextToActIndex after reaching the last player") {
            val players = listOf(
                DefaultPlayer(id = 1, bankroll = 100.0f),
                DefaultPlayer(id = 2, bankroll = 100.0f),
                DefaultPlayer(id = 3, bankroll = 100.0f),
                DefaultPlayer(id = 4, bankroll = 100.0f)
            )
            val pokerTable = PokerTableImpl()
            pokerTable.addPlayers(players)
            val table = TurnAwarePokerTable(pokerTable)

            table.startNewHand()

            val player4 = table.getNextToAct()
            table.playerAction(player4, TurnAwarePokerTable.Action.CALL)

            val player1 = table.getNextToAct()
            table.playerAction(player1, TurnAwarePokerTable.Action.CALL)

            val player2 = table.getNextToAct()
            table.playerAction(player2, TurnAwarePokerTable.Action.CALL)

            val player3 = table.getNextToAct()
            player3.id shouldBe 3
        }

        test("should reset nextToActIndex correctly after a new phase starts") {
            val players = listOf(
                DefaultPlayer(id = 1, bankroll = 100.0f),
                DefaultPlayer(id = 2, bankroll = 100.0f),
                DefaultPlayer(id = 3, bankroll = 100.0f),
                DefaultPlayer(id = 4, bankroll = 100.0f)
            )
            val pokerTable = PokerTableImpl()
            pokerTable.addPlayers(players)
            val table = TurnAwarePokerTable(pokerTable)

            table.startNewHand()

            val player4 = table.getNextToAct()
            table.playerAction(player4, TurnAwarePokerTable.Action.CALL)

            val player1 = table.getNextToAct()
            table.playerAction(player1, TurnAwarePokerTable.Action.CALL)

            val player2 = table.getNextToAct()
            table.playerAction(player2, TurnAwarePokerTable.Action.CALL)

            val player3 = table.getNextToAct()
            table.playerAction(player3, TurnAwarePokerTable.Action.CHECK)

            table.dealFlop()

            table.getNextToAct().id shouldBe 2 // Assuming first to act is next to the dealer.
        }

        test("should skip folded players when determining nextToActIndex") {
            val players = listOf(
                DefaultPlayer(id = 1, bankroll = 100.0f),
                DefaultPlayer(id = 2, bankroll = 100.0f),
                DefaultPlayer(id = 3, bankroll = 100.0f),
                DefaultPlayer(id = 4, bankroll = 100.0f)
            )
            val pokerTable = PokerTableImpl()
            pokerTable.addPlayers(players)
            val table = TurnAwarePokerTable(pokerTable)

            table.startNewHand()

            val player4 = table.getNextToAct()
            table.playerAction(player4, TurnAwarePokerTable.Action.CALL)

            val player1 = table.getNextToAct()
            table.playerAction(player1, TurnAwarePokerTable.Action.CALL)

            val player2 = table.getNextToAct()
            table.playerAction(player2, TurnAwarePokerTable.Action.FOLD)

            val player3 = table.getNextToAct()
            table.playerAction(player3, TurnAwarePokerTable.Action.CHECK)

            table.dealFlop()

            table.getNextToAct() shouldBe player3
            table.playerAction(player3, TurnAwarePokerTable.Action.CHECK)

            table.getNextToAct() shouldBe player4
        }
    }
}
