package com.tripleslate.poker

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class PokerTableTests : FunSpec() {

    override fun isolationMode(): IsolationMode {
        return IsolationMode.InstancePerLeaf
    }

    init {
        val table = PokerTableImpl()

        test("should add players to the game") {
            table.addPlayer(DefaultPlayer(0))
            table.addPlayer(DefaultPlayer(1))

            table.numPlayers shouldBe 2
        }

        test("should correctly deal hole cards") {
            val player1 = DefaultPlayer(0)
            val player2 = DefaultPlayer(1)

            table.addPlayer(player1)
            table.addPlayer(player2)

            table.dealHoleCards()

            table.holeCards.size shouldBe 2
            table.holeCardsForPlayer(player1).size shouldBe 2
            table.holeCardsForPlayer(player2).size shouldBe 2
        }

        test("should handle a player folding") {
            table.addPlayer(DefaultPlayer(0))
            table.addPlayer(DefaultPlayer(1))
            table.addPlayer(DefaultPlayer(2))

            table.dealHoleCards()

            table.fold(table.players[0])

            table.activePlayers.size shouldBe 2
            table.activePlayers.contains(table.players[0]) shouldBe false
        }

        test("should handle a player calling a bet") {
            val player0 = DefaultPlayer(0, bankroll = 100f)
            val player1 = DefaultPlayer(1, bankroll = 100f)

            table.addPlayer(player0)
            table.addPlayer(player1)

            table.dealHoleCards(amount = 2)

            table.raise(player0, 2)
            table.call(player1)

            table.pot[player1] shouldBe 2f
            player0.bankroll shouldBe 98f
            player1.bankroll shouldBe 98f
        }

        test("should handle a player raising, then another re-raising") {
            table.addPlayer(DefaultPlayer(0))
            table.addPlayer(DefaultPlayer(1))
            table.addPlayer(DefaultPlayer(2))

            table.dealHoleCards(amount = 2)

            table.raise(table.players[1], 1)
            table.raise(table.players[2], 2)

            table.pot[table.players[1]] shouldBe 1
            table.pot[table.players[2]] shouldBe 3

            table.getPotSize() shouldBe 4
        }

        test("should prevent a player from calling without sufficient bankroll") {
            val player0 = DefaultPlayer(0, bankroll = 1f)
            val player1 = DefaultPlayer(1, bankroll = 100f)

            table.addPlayer(player0)
            table.addPlayer(player1)

            table.dealHoleCards(amount = 2)

            table.raise(player1, 2)

            shouldThrow<IllegalArgumentException> {
                table.call(player0)
            }
        }

        test("should deal the flop correctly") {
            table.addPlayer(DefaultPlayer(0))
            table.addPlayer(DefaultPlayer(1))

            table.dealHoleCards()

            val flop = table.dealCommunityCards(amount = 3)

            flop.size shouldBe 3
            table.communityCards.size shouldBe 3
        }

        test("should deal the turn and river correctly") {
            table.addPlayer(DefaultPlayer(0))
            table.addPlayer(DefaultPlayer(1))

            table.dealHoleCards()
            for (player in table.players) {
                table.holeCardsForPlayer(player).size shouldBe 2
            }
            table.communityCards.size shouldBe 0

            table.dealCommunityCards(amount = 3)
            table.communityCards.size shouldBe 3

            table.dealCommunityCards(amount = 1)
            table.communityCards.size shouldBe 4

            table.dealCommunityCards(amount = 1)
            table.communityCards.size shouldBe 5
        }

        test("should determine winners based on best hand") {
            val player0 = DefaultPlayer(0)
            val player1 = DefaultPlayer(1)

            table.addPlayer(player0)
            table.addPlayer(player1)

            val fixedHoleCards = mapOf(
                player0 to listOf(Card(CardValue.ACE, CardSuit.HEARTS), Card(CardValue.KING, CardSuit.HEARTS)),
                player1 to listOf(Card(CardValue.TWO, CardSuit.DIAMONDS), Card(CardValue.THREE, CardSuit.DIAMONDS))
            )

            val communityCards = listOf(
                Card(CardValue.QUEEN, CardSuit.HEARTS),
                Card(CardValue.JACK, CardSuit.HEARTS),
                Card(CardValue.TEN, CardSuit.HEARTS),
                Card(CardValue.TWO, CardSuit.CLUBS),
                Card(CardValue.THREE, CardSuit.CLUBS),
            )

            table.startNewHandWithSeed(fixedHoleCards, communityCards)

            table.communityCards shouldContainExactly communityCards

            val winners = table.concludeRound().winners

            winners.size shouldBe 1
            winners.contains(player0) shouldBe true

            for ((player, cards) in table.holeCards) {
                fixedHoleCards[player] shouldContainExactly cards
            }
        }

        test("Full hand where everyone checks") {
            val table = PokerTableImpl()

            val player1 = DefaultPlayer(0, bankroll = 1000f)
            val player2 = DefaultPlayer(1, bankroll = 1000f)
            val player3 = DefaultPlayer(2, bankroll = 1000f)
            val player4 = DefaultPlayer(3, bankroll = 1000f)

            table.addPlayer(player1)
            table.addPlayer(player2)
            table.addPlayer(player3)
            table.addPlayer(player4)

            // Pre-flop phase
            table.resetActivePlayers()

            table.raise(player2, 10)
            table.raise(player3, 10)

            table.getPotSize() shouldBe 30

            table.call(player4)
            table.call(player1)
            table.call(player2)
            table.check(player3)

            table.getPotSize() shouldBe 80

            // Flop phase
            table.dealCommunityCards(amount = 3)
            table.check(player2)
            table.check(player3)
            table.check(player4)
            table.check(player1)

            table.getPotSize() shouldBe 80

            // Turn phase
            table.dealCommunityCards(amount = 1)
            table.check(player2)
            table.check(player3)
            table.check(player4)
            table.check(player1)

            table.getPotSize() shouldBe 80

            // River phase
            table.dealCommunityCards(amount = 1)
            table.check(player2)
            table.check(player3)
            table.check(player4)
            table.check(player1)

            table.getPotSize() shouldBe 80

            // Showdown
            val roundSummary = table.concludeRound()
            roundSummary.winners.isNotEmpty() shouldBe true

            roundSummary.totalPotSize shouldBe 80
        }

        test("Full hand with mixed strategy") {
            val player1 = DefaultPlayer(0, bankroll = 1000f)
            val player2 = DefaultPlayer(1, bankroll = 1000f)
            val player3 = DefaultPlayer(2, bankroll = 1000f)
            val player4 = DefaultPlayer(3, bankroll = 1000f)

            table.addPlayer(player1)
            table.addPlayer(player2)
            table.addPlayer(player3)
            table.addPlayer(player4)

            // Pre-flop phase
            table.resetActivePlayers()

            table.raise(player2, 10)
            table.raise(player3, 10)

            table.getPotSize() shouldBe 30

            table.call(player4)
            table.call(player1)
            table.fold(player2)
            table.check(player3)

            table.getPotSize() shouldBe 70

            // Flop phase
            table.dealCommunityCards(amount = 3)
            table.raise(player3, 10)
            table.call(player4)
            table.fold(player1)

            table.getPotSize() shouldBe 90

            // Turn phase
            table.dealCommunityCards(amount = 1)
            table.check(player3)
            table.raise(player4, 10)
            table.raise(player3, 10)
            table.call(player4)

            table.getPotSize() shouldBe 130

            // River phase
            table.dealCommunityCards(amount = 1)
            table.check(player3)
            table.raise(player4, 20)
            table.call(player3)

            val roundSummary = table.concludeRound()

            roundSummary.winners.isNotEmpty() shouldBe true
            roundSummary.totalPotSize shouldBe 170

            for (winner in roundSummary.winners) {
                val initialBankroll = 1000f
                val totalBetOverEntireHand = roundSummary.playerBets[winner] ?: 0
                val potWinnings = (roundSummary.totalPotSize.toFloat() / roundSummary.winners.size.toFloat())

                winner.bankroll shouldBe (initialBankroll - totalBetOverEntireHand + potWinnings)
            }
        }
    }
}
