package com.tripleslate.poker

/**
 * A decorated PokerTable that tracks who is next to act and manages the lifecycle of hands
 */
class TurnAwarePokerTable(
    private val pokerTable: PokerTable,
) : PokerTable by pokerTable {

    private var isRoundActive: Boolean = false
    private var currentPhase: Phase = Phase.PREFLOP

    private var hasReceivedPreFlopActionFromBigBlind = false

    // Track the next player to act
    private var nextToActIndex: Int = 0

    // Enum to track the current phase of the game
    private enum class Phase {
        PREFLOP, FLOP, TURN, RIVER, SHOWDOWN
    }

    fun isRoundActive(): Boolean = isRoundActive

    fun getNextToAct(): Player {
        return pokerTable.players[nextToActIndex]
    }

    fun startNewHand() {
        pokerTable.startNewHandWithSeed(
            emptyMap(),
            emptyList()
        )

        pokerTable.postBlinds(1, 2)

        isRoundActive = true
        currentPhase = Phase.PREFLOP

        nextToActIndex = (pokerTable.dealerIndex + 3) % pokerTable.numPlayers
    }

    override fun startNewHandWithSeed(
        fixedHoleCards: Map<out Player, List<Card>>,
        communityCards: List<Card>
    ) {
        pokerTable.startNewHandWithSeed(
            fixedHoleCards,
            communityCards
        )

        pokerTable.postBlinds(1, 2)

        isRoundActive = true
        currentPhase = Phase.PREFLOP

        nextToActIndex = (pokerTable.dealerIndex + 3) % pokerTable.numPlayers
    }

    // Proceed with the player's action, ensuring they're not acting out of turn
    fun playerAction(player: Player, action: Action, amount: Int = 0) {
        if (!isRoundActive) {
            throw IllegalStateException("Round is not active. Please start a new hand.")
        }

        if (isBettingRoundComplete()) {
            throw IllegalStateException("Betting round has concluded. Can't $action")
        }

        val currentPlayer = pokerTable.players[nextToActIndex]
        if (currentPlayer != player) {
            throw IllegalStateException("[$nextToActIndex] It's Player ${currentPlayer.id}'s turn, not Player ${player.id}'s.")
        }

        // Ensure the player makes a valid action
        when (action) {
            Action.FOLD -> {
                pokerTable.fold(player)
            }
            Action.CHECK -> {
                pokerTable.check(player)
            }
            Action.CALL -> {
                pokerTable.call(player)
            }
            Action.RAISE -> {
                pokerTable.raise(player, amount)
            }
        }

        if (
            currentPhase == Phase.PREFLOP &&
            pokerTable.players[(pokerTable.dealerIndex + 2) % pokerTable.numPlayers] == player
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
        val highestBet = pokerTable.roundBets.values.maxOrNull()

        if (currentPhase == Phase.PREFLOP && !hasReceivedPreFlopActionFromBigBlind) {
            return false
        }

        return pokerTable.roundBets.keys.containsAll(pokerTable.activePlayers) && pokerTable.activePlayers.all { player ->
            // Each active player has either folded, gone all-in, or matched the highest bet
            player.bankroll <= 0f ||
            pokerTable.roundBets[player] == highestBet
        }
    }

    // Move to the next player, ensuring the betting round continues correctly
    private fun moveToNextPlayer() {
        // handle only a single player remaining
        if (activePlayers.size == 1) {
            currentPhase = Phase.RIVER
            isRoundActive = false
            return
        }

        // Check if the betting round is over
        if (isBettingRoundComplete()) {
            // Transition to the next phase of the game
            when (currentPhase) {
                Phase.PREFLOP -> {
                    dealFlop()
                }
                Phase.FLOP -> {
                    dealTurn()
                }
                Phase.TURN -> {
                    dealRiver()
                }
                Phase.RIVER -> {
                    // After the river, move to showdown
                    currentPhase = Phase.SHOWDOWN
                    isRoundActive = false
                }
                else -> {
                    isRoundActive = false
                }
            }

            nextToActIndex = pokerTable.dealerIndex
        }

        // Advance to the next active player
        do {
            nextToActIndex = (nextToActIndex + 1) % pokerTable.numPlayers
        } while (players[nextToActIndex] !in pokerTable.activePlayers)
    }

    // Deal the flop and advance to the post-flop phase
    override fun dealFlop(): List<Card> {
        require(currentPhase == Phase.PREFLOP) { "Flop can only be dealt after the preflop phase." }
        val flop = pokerTable.dealFlop()
        currentPhase = Phase.FLOP

        nextToActIndex = (pokerTable.dealerIndex) % pokerTable.activePlayers.size
        moveToNextPlayer()

        return flop
    }

    // Deal the turn and advance to the post-turn phase
    override fun dealTurn(): Card {
        require(currentPhase == Phase.FLOP) { "Turn can only be dealt after the flop." }
        val turn = pokerTable.dealTurn()
        currentPhase = Phase.TURN

        nextToActIndex = (pokerTable.dealerIndex) % pokerTable.activePlayers.size
        moveToNextPlayer()

        return turn
    }

    // Deal the river and advance to the post-river phase
    override fun dealRiver(): Card {
        require(currentPhase == Phase.TURN) { "River can only be dealt after the turn." }
        val river = pokerTable.dealRiver()
        currentPhase = Phase.RIVER

        nextToActIndex = (pokerTable.dealerIndex) % pokerTable.activePlayers.size
        moveToNextPlayer()

        return river
    }

    override fun concludeRound(): PokerTable.RoundSummary {
        isRoundActive = false

        val roundSummary = pokerTable.concludeRound()
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
