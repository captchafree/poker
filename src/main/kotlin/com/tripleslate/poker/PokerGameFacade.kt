package com.tripleslate.poker

import com.tripleslate.com.tripleslate.poker.Card
import com.tripleslate.com.tripleslate.poker.CardSuit
import com.tripleslate.com.tripleslate.poker.CardValue
import com.tripleslate.poker.PokerGame.Player

class PokerSimulator(
    private val strategies: Map<Int, PokerStrategy>
) {

    fun runSimulation() {
        val pokerGame = PokerGame(4)
        val pokerFacade = PokerGameFacade(pokerGame)

        pokerFacade.startNewHand()

        val currentPlayer = Player(pokerFacade.getActivePlayers().first().id + 1)

        val env = object : PokerStrategyEnvironment {
            override fun fold() {
                pokerGame.fold(currentPlayer)
            }

            override fun check() {
                pokerGame.check(currentPlayer)
            }

            override fun call() {
                pokerGame.call(currentPlayer)
            }

            override fun raise(amount: Int) {
                pokerGame.raise(currentPlayer, amount)
            }
        }

        pokerFacade.resetGame()
    }

}

interface PokerStrategy {

    fun executeTurn(
        game: PokerGame,
        environment: PokerStrategyEnvironment
    )
}

interface PokerStrategyEnvironment {
    fun fold()
    fun check()
    fun call()
    fun raise(amount: Int)
}

class PokerGameFacade(private val pokerGame: PokerGame) {

    private var roundActive: Boolean = false
    private var bettingRoundActive: Boolean = false
    private var currentPhase: Phase = Phase.PREFLOP

    private val playersWhoHaveActedThisRound = mutableMapOf<Int, Int>()
    private val foldedPlayers = mutableListOf<Int>()

    // Track the next player to act
    private var nextToActIndex: Int = 0

    init {
        require(pokerGame.numPlayers > 1) { "The game requires at least 2 players." }
    }

    // Enum to track the current phase of the game
    enum class Phase {
        PREFLOP, FLOP, TURN, RIVER, SHOWDOWN
    }

    // Start a new hand
    fun startNewHand() {
        pokerGame.dealHoleCards()
        pokerGame.postBlinds(1, 2)
        playersWhoHaveActedThisRound[(pokerGame.dealerIndex + 1) % pokerGame.numPlayers] = 1
        playersWhoHaveActedThisRound[(pokerGame.dealerIndex + 2) % pokerGame.numPlayers] = 2
        roundActive = true
        bettingRoundActive = true
        currentPhase = Phase.PREFLOP

        nextToActIndex = (pokerGame.dealerIndex + 3) % pokerGame.numPlayers
    }

    // Proceed with the player's action, ensuring they're not acting out of turn
    fun playerAction(player: Player, action: Action, amount: Int = 0) {
        if (!roundActive) {
            throw IllegalStateException("Round is not active. Please start a new hand.")
        }

        if (!bettingRoundActive) {
            throw IllegalStateException("Betting round has concluded.")
        }

        val currentPlayer = pokerGame.players[nextToActIndex]
        if (currentPlayer != player) {
            throw IllegalStateException("[$nextToActIndex] It's Player ${currentPlayer.id}'s turn, not Player ${player.id}'s.")
        }

        // Ensure the player makes a valid action
        when (action) {
            Action.FOLD -> {
                pokerGame.fold(player)
                foldedPlayers.add(nextToActIndex)
            }
            Action.CALL -> {
                pokerGame.call(player)
                playersWhoHaveActedThisRound[nextToActIndex] = playersWhoHaveActedThisRound.maxBy { it.value }.value
            }
            Action.RAISE -> {
                pokerGame.raise(player, amount)
                playersWhoHaveActedThisRound[nextToActIndex] = playersWhoHaveActedThisRound.maxByOrNull { it.value }?.value?.plus(amount) ?: amount
            }
            Action.CHECK -> {
                pokerGame.check(player)
                playersWhoHaveActedThisRound[nextToActIndex] = playersWhoHaveActedThisRound.maxByOrNull { it.value }?.value ?: 0
            }
        }

        // Move to the next player after the current action
        moveToNextPlayer()
    }

    // Move to the next player, ensuring the betting round continues correctly
    private fun moveToNextPlayer() {
        do {
            nextToActIndex = (nextToActIndex + 1) % pokerGame.numPlayers
        } while (nextToActIndex in foldedPlayers)

//        // End the betting round once the dealer has acted
//        if (nextToActIndex == pokerGame.dealerIndex) {
//            bettingRoundActive = false
//            nextPhase()
//        }
    }


    // Deal the flop and advance to the post-flop phase
    fun dealFlop(): List<Card> {
        require(currentPhase == Phase.PREFLOP) { "Flop can only be dealt after the preflop phase." }
        val flop = pokerGame.dealFlop()
        currentPhase = Phase.FLOP

        nextToActIndex = (pokerGame.dealerIndex) % pokerGame.numPlayers
        moveToNextPlayer()

        return flop
    }

    // Deal the turn and advance to the post-turn phase
    fun dealTurn(): Card {
        require(currentPhase == Phase.FLOP) { "Turn can only be dealt after the flop." }
        val turn = pokerGame.dealTurnOrRiver()
        currentPhase = Phase.TURN

        nextToActIndex = (pokerGame.dealerIndex) % pokerGame.numPlayers
        moveToNextPlayer()

        return turn
    }

    // Deal the river and advance to the post-river phase
    fun dealRiver(): Card {
        require(currentPhase == Phase.TURN) { "River can only be dealt after the turn." }
        val river = pokerGame.dealTurnOrRiver()
        currentPhase = Phase.RIVER

        nextToActIndex = (pokerGame.dealerIndex) % pokerGame.numPlayers
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
    fun getActivePlayers(): List<Player> {
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
    fun holeCardsForPlayer(player: Player): List<Card> {
        return pokerGame.holeCards[player.id]
    }

    fun getWinner(): Player {
        println(getCommunityCards())
        for (player in pokerGame.players) {
            println(holeCardsForPlayer(player))
        }

        val hands = pokerGame.players.associateWith {
            getCommunityCards() + holeCardsForPlayer(it)
        }.toList()


        val winner = hands.sortedWith(compareBy(HandRankUtils.handComparator()) { it.second })

        return winner.last().first
    }

    // Reset the game for a new hand
    fun resetGame() {
        pokerGame.reset()
        pokerGame.nextDealer()
        roundActive = false
        bettingRoundActive = false
        currentPhase = Phase.PREFLOP
        playersWhoHaveActedThisRound.clear()
        foldedPlayers.clear()
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
    val pokerGame = PokerGame(4)
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

    println(pokerFacade.getWinner())


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
