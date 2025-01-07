package com.tripleslate.poker

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PokerGameTests : FunSpec() {

    override fun isolationMode(): IsolationMode {
        return IsolationMode.InstancePerLeaf
    }

    init {
        val game = PokerGame()

        test("should add players to the game") {
            game.addPlayer(DefaultPlayer(0))
            game.addPlayer(DefaultPlayer(1))

            game.numPlayers shouldBe 2
        }

        test("should not allow adding players during an active hand") {
            game.addPlayer(DefaultPlayer(0))
            game.addPlayer(DefaultPlayer(1))

            game.startNewHandWithSeed(emptyMap(), emptyList())

            shouldThrow<IllegalArgumentException> {
                game.addPlayer(DefaultPlayer(2))
            }
        }

        test("should correctly deal hole cards") {
            game.addPlayer(DefaultPlayer(0))
            game.addPlayer(DefaultPlayer(1))

            game.dealHoleCards()

            game.holeCards.size shouldBe 2
            game.holeCards[0].size shouldBe 2
            game.holeCards[1].size shouldBe 2
        }

        test("should enforce a minimum of two players to deal cards") {
            game.addPlayer(DefaultPlayer(0))

            shouldThrow<IllegalArgumentException> {
                game.dealHoleCards()
            }
        }

        test("should post blinds correctly") {
            game.addPlayer(DefaultPlayer(0))
            game.addPlayer(DefaultPlayer(1))
            game.addPlayer(DefaultPlayer(2))

            game.dealHoleCards()
            game.postBlinds(1, 2)

            game.pot[game.players[1]] shouldBe 1
            game.pot[game.players[2]] shouldBe 2

            game.getPotTotal() shouldBe 3
        }

        test("should handle a player folding") {
            game.addPlayer(DefaultPlayer(0))
            game.addPlayer(DefaultPlayer(1))
            game.addPlayer(DefaultPlayer(2))

            game.dealHoleCards()

            game.fold(game.players[0])

            game.activePlayers.size shouldBe 2
            game.activePlayers.contains(game.players[0]) shouldBe false
        }

        test("should handle a player calling a bet") {
            val player0 = DefaultPlayer(0, bankroll = 100f)
            val player1 = DefaultPlayer(1, bankroll = 100f)

            game.addPlayer(player0)
            game.addPlayer(player1)

            game.dealHoleCards()
            game.postBlinds(1, 2)

            game.call(player1)

            game.pot[player1] shouldBe 2f
            player0.bankroll shouldBe 98f
            player1.bankroll shouldBe 98f
        }

        test("should prevent a player from calling without sufficient bankroll") {
            val player0 = DefaultPlayer(0, bankroll = 1f)
            val player1 = DefaultPlayer(1, bankroll = 100f)

            game.addPlayer(player0)
            game.addPlayer(player1)

            game.dealHoleCards()
            game.postBlinds(1, 2)

            shouldThrow<IllegalArgumentException> {
                game.call(player0)
            }
        }

        test("should deal the flop correctly") {
            game.addPlayer(DefaultPlayer(0))
            game.addPlayer(DefaultPlayer(1))

            game.dealHoleCards()

            val flop = game.dealFlop()

            flop.size shouldBe 3
            game.communityCards.size shouldBe 3
        }

        test("should deal the turn and river correctly") {
            game.addPlayer(DefaultPlayer(0))
            game.addPlayer(DefaultPlayer(1))

            game.dealHoleCards()
            for (player in game.players) {
                game.holeCards[player.id].size shouldBe 2
            }
            game.communityCards.size shouldBe 0

            game.dealFlop()
            game.communityCards.size shouldBe 3

            game.dealTurnOrRiver()
            game.communityCards.size shouldBe 4

            game.dealTurnOrRiver()
            game.communityCards.size shouldBe 5
        }

        test("should determine winners based on best hand") {
            val player0 = DefaultPlayer(0)
            val player1 = DefaultPlayer(1)

            game.addPlayer(player0)
            game.addPlayer(player1)

            val fixedHoleCards = mapOf(
                0 to listOf(Card(CardValue.ACE, CardSuit.HEARTS), Card(CardValue.KING, CardSuit.HEARTS)),
                1 to listOf(Card(CardValue.TWO, CardSuit.DIAMONDS), Card(CardValue.THREE, CardSuit.DIAMONDS))
            )

            val community = listOf(
                Card(CardValue.QUEEN, CardSuit.HEARTS),
                Card(CardValue.JACK, CardSuit.HEARTS),
                Card(CardValue.TEN, CardSuit.HEARTS),
                Card(CardValue.TWO, CardSuit.CLUBS),
                Card(CardValue.THREE, CardSuit.CLUBS),
            )

            game.startNewHandWithSeed(fixedHoleCards, community)

            val winners = game.getWinners()

            winners.size shouldBe 1
            winners.contains(player0.id) shouldBe true
        }

        test("Full hand where everyone checks") {
            val player1 = DefaultPlayer(0, bankroll = 1000f)
            val player2 = DefaultPlayer(1, bankroll = 1000f)
            val player3 = DefaultPlayer(2, bankroll = 1000f)
            val player4 = DefaultPlayer(3, bankroll = 1000f)

            game.addPlayer(player1)
            game.addPlayer(player2)
            game.addPlayer(player3)
            game.addPlayer(player4)

            // Pre-flop phase
            game.dealHoleCards()
            game.postBlinds(10, 20)

            game.call(player4)
            game.call(player1)
            game.call(player2)
            game.check(player3)

            // Flop phase
            game.dealFlop()
            game.check(player2)
            game.check(player3)
            game.check(player4)
            game.check(player1)

            // Turn phase
            game.dealTurnOrRiver()
            game.check(player2)
            game.check(player3)
            game.check(player4)
            game.check(player1)

            // River phase
            game.dealTurnOrRiver()
            game.check(player2)
            game.check(player3)
            game.check(player4)
            game.check(player1)

            // Showdown
            val winners = game.getWinners()
            winners.isNotEmpty() shouldBe true

            game.getPotTotal() shouldBe 80

            game.concludeRound()
        }

        test("Full hand with mixed strategy") {
            val player1 = DefaultPlayer(0, bankroll = 1000f)
            val player2 = DefaultPlayer(1, bankroll = 1000f)
            val player3 = DefaultPlayer(2, bankroll = 1000f)
            val player4 = DefaultPlayer(3, bankroll = 1000f)

            game.addPlayer(player1)
            game.addPlayer(player2)
            game.addPlayer(player3)
            game.addPlayer(player4)

            // Pre-flop phase
            game.dealHoleCards()
            game.postBlinds(10, 20)

            game.call(player4)
            game.call(player1)
            game.fold(player2)
            game.check(player3)

            game.getPotTotal() shouldBe 70

            // Flop phase
            game.dealFlop()
            game.raise(player3, 10)
            game.call(player4)
            game.fold(player1)

            game.getPotTotal() shouldBe 90

            // Turn phase
            game.dealTurnOrRiver()
            game.check(player3)
            game.raise(player4, 10)
            game.raise(player3, 10)
            game.call(player4)

            game.getPotTotal() shouldBe 130

            // River phase
            game.dealTurnOrRiver()
            game.check(player3)
            game.raise(player4, 20)
            game.call(player3)

            val potTotal = game.getPotTotal()
            potTotal shouldBe 170

            // Showdown
            val winners = game.getWinners()
            winners.isNotEmpty() shouldBe true

            val roundSummary = game.concludeRound()
            for (winner in roundSummary.winners) {
                val initialBankroll = 1000f
                val totalBetOverEntireHand = roundSummary.playerBets[winner] ?: 0
                val potWinnings = (potTotal.toFloat() / roundSummary.winners.size.toFloat())

                winner.bankroll shouldBe (initialBankroll - totalBetOverEntireHand + potWinnings)
            }
        }
    }
}
