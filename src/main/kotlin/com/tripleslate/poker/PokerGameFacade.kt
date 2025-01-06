package com.tripleslate.poker

import com.tripleslate.com.tripleslate.poker.Card
import com.tripleslate.com.tripleslate.poker.CardSuit
import com.tripleslate.com.tripleslate.poker.CardValue
import com.tripleslate.poker.PokerGame.RoundSummary

interface PokerStrategy {

    fun executeTurn(
        game: PokerGameFacade,
        environment: PokerStrategyEnvironment
    )

    companion object
}

interface PokerStrategyEnvironment {
    val currentPlayer: IPlayer
    fun fold()
    fun check()
    fun checkOrFold()
    fun call()
    fun raise(amount: Int)
}

class PokerGameFacade(
    private val pokerGame: PokerGame,
) {

    private var roundActive: Boolean = false
    private var bettingRoundActive: Boolean = false
    private var currentPhase: Phase = Phase.PREFLOP

    private var hasReceivedPreFlopActionFromBigBlind = false

    // Track the next player to act
    private var nextToActIndex: Int = 0

    // Enum to track the current phase of the game
    enum class Phase {
        PREFLOP, FLOP, TURN, RIVER, SHOWDOWN
    }

    val underlyingGame
        get() = pokerGame

    fun isRoundActive(): Boolean = roundActive

    fun getNextToAct(): IPlayer {
        return pokerGame.players[nextToActIndex]
    }

    // Start a new hand
    fun startNewHand() {
        pokerGame.dealHoleCards()
        roundActive = true
        bettingRoundActive = true
        currentPhase = Phase.PREFLOP

        nextToActIndex = (pokerGame.dealerIndex + 3) % pokerGame.numPlayers
    }

    // Proceed with the player's action, ensuring they're not acting out of turn
    fun playerAction(player: IPlayer, action: Action, amount: Int = 0) {
        if (!roundActive) {
            throw IllegalStateException("Round is not active. Please start a new hand.")
        }

        if (isBettingRoundComplete()) {
            throw IllegalStateException("Betting round has concluded. Can't $action")
        }

        val currentPlayer = pokerGame.players[nextToActIndex]
        if (currentPlayer != player) {
            throw IllegalStateException("[$nextToActIndex] It's Player ${currentPlayer.id}'s turn, not Player ${player.id}'s.")
        }

        // Ensure the player makes a valid action
        when (action) {
            Action.FOLD -> {
                pokerGame.fold(player)
            }
            Action.CHECK -> {
                pokerGame.check(player)
            }
            Action.CALL -> {
                pokerGame.call(player)
            }
            Action.RAISE -> {
                pokerGame.raise(player, amount)
            }
        }

        if (
            currentPhase == Phase.PREFLOP &&
            pokerGame.players[(pokerGame.dealerIndex + 2) % pokerGame.numPlayers] == player
        ) {
            hasReceivedPreFlopActionFromBigBlind = true
        }

        // Move to the next player after the current action
        moveToNextPlayer()
    }

    fun isBettingRoundComplete(): Boolean {
        // Conditions for betting round completion:
        // 1. All active players have taken action, and the actions are resolved.
        // 2. No players have raised or there's a call matching the highest bet amount.
        val highestBet = pokerGame.roundBets.values.maxOrNull()

        if (currentPhase == Phase.PREFLOP && !hasReceivedPreFlopActionFromBigBlind) {
            return false
        }

        return pokerGame.roundBets.keys.containsAll(pokerGame.activePlayers) && pokerGame.activePlayers.all { player ->
            // Each active player has either folded, gone all-in, or matched the highest bet
            player.bankroll <= 0f ||
            pokerGame.roundBets[player] == highestBet
        }
    }

    // Move to the next player, ensuring the betting round continues correctly
    private fun moveToNextPlayer() {
        // Check if the betting round is over
        if (isBettingRoundComplete()) {
            bettingRoundActive = false

            // Transition to the next phase of the game
            when (currentPhase) {
                Phase.PREFLOP -> {
                    dealFlop()
                    bettingRoundActive = true // New betting round starts
                }
                Phase.FLOP -> {
                    dealTurn()
                    bettingRoundActive = true // New betting round starts
                }
                Phase.TURN -> {
                    dealRiver()
                    bettingRoundActive = true // New betting round starts
                }
                Phase.RIVER -> {
                    // After the river, move to showdown
                    currentPhase = Phase.SHOWDOWN
                    bettingRoundActive = false
                    roundActive = false
                }
                else -> {
                    bettingRoundActive = false
                    roundActive = false
                    // throw IllegalStateException("Unexpected phase: $currentPhase")
                }
            }

            nextToActIndex = pokerGame.dealerIndex
        }

        // Advance to the next active player
        do {
            nextToActIndex = (nextToActIndex + 1) % pokerGame.numPlayers
        } while (nextToActIndex !in pokerGame.activePlayers.map { it.id })
    }

    // Deal the flop and advance to the post-flop phase
    fun dealFlop(): List<Card> {
        require(currentPhase == Phase.PREFLOP) { "Flop can only be dealt after the preflop phase." }
        val flop = pokerGame.dealFlop()
        currentPhase = Phase.FLOP

        // nextToActIndex = (pokerGame.dealerIndex) % pokerGame.numPlayers
        moveToNextPlayer()

        return flop
    }

    // Deal the turn and advance to the post-turn phase
    fun dealTurn(): Card {
        require(currentPhase == Phase.FLOP) { "Turn can only be dealt after the flop." }
        val turn = pokerGame.dealTurnOrRiver()
        currentPhase = Phase.TURN

        // nextToActIndex = (pokerGame.dealerIndex) % pokerGame.numPlayers
        moveToNextPlayer()

        return turn
    }

    // Deal the river and advance to the post-river phase
    fun dealRiver(): Card {
        require(currentPhase == Phase.TURN) { "River can only be dealt after the turn." }
        val river = pokerGame.dealTurnOrRiver()
        currentPhase = Phase.RIVER

        // nextToActIndex = (pokerGame.dealerIndex) % pokerGame.numPlayers
        moveToNextPlayer()

        return river
    }

    // Determine the next phase of the game after all players have acted
    private fun nextPhase() {
        when (currentPhase) {
            Phase.PREFLOP -> dealFlop()
            Phase.FLOP -> dealTurn()
            Phase.TURN -> dealRiver()
            Phase.RIVER -> {
                // After the river, move to the showdown phase
                currentPhase = Phase.SHOWDOWN
            }
            else -> {
                throw IllegalStateException("No more phases left in the hand.")
            }
        }
    }

    // Get the total pot value
    fun getPotTotal(): Int {
        return pokerGame.getPotTotal()
    }

    // Get a list of active players
    fun getActivePlayers(): List<IPlayer> {
        return pokerGame.activePlayers
    }

    // Check if the current round is over (all players have acted)
    fun isRoundOver(): Boolean {
        return !bettingRoundActive
    }

    // Get the community cards
    fun getCommunityCards(): List<Card> {
        return pokerGame.communityCards
    }

    // Get hole cards for a player
    fun holeCardsForPlayer(player: IPlayer): List<Card> {
        return pokerGame.holeCards[player.id]
    }

    fun getWinners(): Set<IPlayer> {
        return pokerGame.getWinners().map { playerId ->
            getActivePlayers().first { it.id == playerId }
        }.toSet()
    }

    fun concludeRound(): RoundSummary {
        roundActive = false

        val roundSummary = pokerGame.concludeRound()
        pokerGame.nextDealer()
        bettingRoundActive = false
        currentPhase = Phase.PREFLOP
        hasReceivedPreFlopActionFromBigBlind = false

        return roundSummary
    }

    enum class Action {
        CALL,
        FOLD,
        RAISE,
        CHECK
    }
}


// Example Usage
fun main() {
    val pokerGame = PokerGame()

    val player3 = Player(3, 6f)
    // 4 players
    pokerGame.addPlayer(Player(0))
    pokerGame.addPlayer(Player(1))
    pokerGame.addPlayer(Player(2))
    pokerGame.addPlayer(player3)

    val pokerFacade = PokerGameFacade(pokerGame)

    pokerFacade.startNewHand() // Start a new hand

    println("Pot total before actions: ${pokerFacade.getPotTotal()}")


    pokerFacade.playerAction(pokerGame.players[3], PokerGameFacade.Action.RAISE, amount = 2)
    println("Pot total 3: ${pokerFacade.getPotTotal()}")
    pokerFacade.playerAction(pokerGame.players[0], PokerGameFacade.Action.CALL)
    println("Pot total 0: ${pokerFacade.getPotTotal()}")
    pokerFacade.playerAction(pokerGame.players[1], PokerGameFacade.Action.FOLD)
    println("Pot total 1: ${pokerFacade.getPotTotal()}")
    pokerFacade.playerAction(pokerGame.players[2], PokerGameFacade.Action.CALL)
    println("Pot total 2: ${pokerFacade.getPotTotal()}")

    println("Pot total after actions: ${pokerFacade.getPotTotal()}")

    val flop = pokerFacade.dealFlop()
    println("Flop: $flop")

    pokerFacade.playerAction(pokerGame.players[2], PokerGameFacade.Action.CHECK)
    pokerFacade.playerAction(pokerGame.players[3], PokerGameFacade.Action.CHECK)
    pokerFacade.playerAction(pokerGame.players[0], PokerGameFacade.Action.CHECK)

    val turn = pokerFacade.dealTurn()
    println("Turn: $turn")

    pokerFacade.playerAction(pokerGame.players[2], PokerGameFacade.Action.RAISE, 2)
    pokerFacade.playerAction(pokerGame.players[3], PokerGameFacade.Action.CALL)
    pokerFacade.playerAction(pokerGame.players[0], PokerGameFacade.Action.FOLD)

    val river = pokerFacade.dealRiver()
    println("River: $river")

    println("Pot total after river: ${pokerFacade.getPotTotal()}")

    pokerFacade.getActivePlayers().forEach {
        println("[${it.id}] ${pokerFacade.holeCardsForPlayer(it)}")
    }

    println(pokerGame.concludeRound())

    val communityCards = listOf(
        // Card(CardValue.ACE, CardSuit.SPADES),
        Card(CardValue.KING, CardSuit.SPADES),
        Card(CardValue.TWO, CardSuit.HEARTS),
        Card(CardValue.JACK, CardSuit.SPADES),
        Card(CardValue.TEN, CardSuit.SPADES),
    )

    val hand1 = listOf(
        Card(CardValue.TEN, CardSuit.SPADES),
        Card(CardValue.NINE, CardSuit.SPADES),
    ) + communityCards

    val hand2 = listOf(
        Card(CardValue.ACE, CardSuit.SPADES),
        Card(CardValue.THREE, CardSuit.SPADES),
    ) + communityCards

    println(hand1)
    println(hand2)

    println(HandRankUtils.determineHandRank(hand1))
    println(HandRankUtils.determineHandRank(hand2))
    println(HandRankUtils.handComparator().compare(hand1, hand2))
}
