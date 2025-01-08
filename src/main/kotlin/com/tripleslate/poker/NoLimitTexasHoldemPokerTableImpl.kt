package com.tripleslate.poker

import com.tripleslate.poker.PokerTable.RoundSummary

interface NoLimitTexasHoldemPokerTable {

    /**
     * The players at the table
     */
    val players: List<Player>
    val numPlayers: Int

    /**
     * Players still in the hand. they haven't folded
     */
    val activePlayers: List<Player>

    val pot: Map<Player, Int>

    /**
     * player -> bets in the current round (pre-flop, flop, etc.)
     */
    val roundBets: Map<Player, Int>

    /**
     * Index of the dealer
     */
    val dealerIndex: Int

    /**
     * player -> hole cards
     */
    val holeCards: Map<Player, List<Card>>
    val communityCards: List<Card>

    fun addPlayer(player: Player)
    fun addPlayers(players: Collection<Player>)

    fun removePlayer(player: Player)
    fun removePlayers(players: Collection<Player>)

    val currentPhase: Phase

    // Enum to track the current phase of the game
    enum class Phase {
        PREFLOP, FLOP, TURN, RIVER, SHOWDOWN
    }

    fun getPotSize(): Int

    fun startNewHandWithSeed(
        fixedHoleCards: Map<out Player, List<Card>>,
        communityCards: List<Card>,
    )

    fun holeCardsForPlayer(player: Player): List<Card>

    fun dealFlop(): List<Card>
    fun dealTurn(): Card
    fun dealRiver(): Card

//    fun fold(player: Player)
//    fun call(player: Player)
//    fun raise(player: Player, amount: Int)
//    fun check(player: Player)

    /**
     * Move the dealer position to the left by one
     */
    fun advanceDealerPosition()

    fun concludeRound(): RoundSummary
}

/**
 * A decorated PokerTable that tracks who is next to act and manages the lifecycle of hands
 */
class NoLimitTexasHoldemPokerTableImpl(
    private val pokerTable: PokerTable,
    private val rules: PokerTableRules = PokerTableRules.default()
) : NoLimitTexasHoldemPokerTable {

    private var isRoundActive: Boolean = false
    override var currentPhase: NoLimitTexasHoldemPokerTable.Phase = NoLimitTexasHoldemPokerTable.Phase.PREFLOP

    private var hasReceivedPreFlopActionFromBigBlind = false

    // Track the next player to act
    private var nextToActIndex: Int = 0

    override val players: List<Player>
        get() = pokerTable.players
    override val numPlayers: Int
        get() = pokerTable.numPlayers
    override val activePlayers: List<Player>
        get() = pokerTable.activePlayers
    override val pot: Map<Player, Int>
        get() = pokerTable.pot
    override val roundBets: Map<Player, Int>
        get() = pokerTable.roundBets
    override val dealerIndex: Int
        get() = pokerTable.dealerIndex
    override val holeCards: Map<Player, List<Card>>
        get() = pokerTable.holeCards
    override val communityCards: List<Card>
        get() = pokerTable.communityCards

    override fun addPlayers(players: Collection<Player>) {
        players.forEach(::addPlayer)
    }

    override fun removePlayers(players: Collection<Player>) {
        players.forEach(::removePlayer)
    }

    override fun getPotSize(): Int {
        return pokerTable.getPotSize()
    }

    override fun holeCardsForPlayer(player: Player): List<Card> {
        return pokerTable.holeCardsForPlayer(player)
    }

    override fun advanceDealerPosition() {
        pokerTable.advanceDealerPosition()
    }

    override fun addPlayer(player: Player) {
        require(!isRoundActive) {
            "Cannot add a player in the middle of a hand"
        }

        pokerTable.addPlayer(player)
    }

    override fun removePlayer(player: Player) {
        require(!isRoundActive) {
            "Cannot remove a player in the middle of a hand"
        }

        pokerTable.removePlayer(player)
    }

    fun isRoundActive(): Boolean = isRoundActive

    fun getNextToAct(): Player {
        return pokerTable.players[nextToActIndex]
    }

    fun startNewHand() {
        require(numPlayers > 1) {
            "The game requires at least 2 players."
        }

        this.startNewHandWithSeed(
            fixedHoleCards = emptyMap(),
            communityCards = emptyList(),
        )
    }

    override fun startNewHandWithSeed(
        fixedHoleCards: Map<out Player, List<Card>>,
        communityCards: List<Card>
    ) {
        require(numPlayers > 1) {
            "The game requires at least 2 players."
        }

        pokerTable.startNewHandWithSeed(
            fixedHoleCards,
            communityCards,
            numCardsToDealPerPlayer = 2,
        )

        isRoundActive = true
        currentPhase = NoLimitTexasHoldemPokerTable.Phase.PREFLOP

        nextToActIndex = (pokerTable.dealerIndex + 1) % pokerTable.numPlayers

        this.postBlinds()
    }

    private fun postBlinds() {
        val smallBlind = rules.blinds.smallBlindAmount
        val bigBlind = rules.blinds.bigBlindAmount

        val smallBlindPlayer = players[(dealerIndex + 1) % numPlayers]
        val bigBlindPlayer = players[(dealerIndex + 2) % numPlayers]

        playerAction(smallBlindPlayer, Action.RAISE, smallBlind)
        playerAction(bigBlindPlayer, Action.RAISE, bigBlind - smallBlind)

        hasReceivedPreFlopActionFromBigBlind = false
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
            currentPhase == NoLimitTexasHoldemPokerTable.Phase.PREFLOP &&
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

        if (currentPhase == NoLimitTexasHoldemPokerTable.Phase.PREFLOP && !hasReceivedPreFlopActionFromBigBlind) {
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
            currentPhase = NoLimitTexasHoldemPokerTable.Phase.RIVER
            isRoundActive = false
            return
        }

        // Check if the betting round is over
        if (isBettingRoundComplete()) {
            // Transition to the next phase of the game
            when (currentPhase) {
                NoLimitTexasHoldemPokerTable.Phase.PREFLOP -> {
                    dealFlop()
                }
                NoLimitTexasHoldemPokerTable.Phase.FLOP -> {
                    dealTurn()
                }
                NoLimitTexasHoldemPokerTable.Phase.TURN -> {
                    dealRiver()
                }
                NoLimitTexasHoldemPokerTable.Phase.RIVER -> {
                    // After the river, move to showdown
                    currentPhase = NoLimitTexasHoldemPokerTable.Phase.SHOWDOWN
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
        require(currentPhase == NoLimitTexasHoldemPokerTable.Phase.PREFLOP) { "Flop can only be dealt after the preflop phase. Currently $currentPhase." }
        pokerTable.burnCards(amount = 1)
        val flop = pokerTable.dealCommunityCards(amount = 3)

        currentPhase = NoLimitTexasHoldemPokerTable.Phase.FLOP

        return flop
    }

    // Deal the turn and advance to the post-turn phase
    override fun dealTurn(): Card {
        require(currentPhase == NoLimitTexasHoldemPokerTable.Phase.FLOP) { "Turn can only be dealt after the flop. Currently $currentPhase." }
        pokerTable.burnCards(amount = 1)
        val turn = pokerTable.dealCommunityCards(amount = 1).single()
        currentPhase = NoLimitTexasHoldemPokerTable.Phase.TURN

        return turn
    }

    // Deal the river and advance to the post-river phase
    override fun dealRiver(): Card {
        require(currentPhase == NoLimitTexasHoldemPokerTable.Phase.TURN) { "River can only be dealt after the turn. Currently $currentPhase." }
        pokerTable.burnCards(amount = 1)
        val river = pokerTable.dealCommunityCards(amount = 1).single()
        currentPhase = NoLimitTexasHoldemPokerTable.Phase.RIVER

        return river
    }

    override fun concludeRound(): RoundSummary {
        isRoundActive = false

        val roundSummary = pokerTable.concludeRound()
        currentPhase = NoLimitTexasHoldemPokerTable.Phase.PREFLOP
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
