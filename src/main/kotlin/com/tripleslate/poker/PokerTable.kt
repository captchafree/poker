package com.tripleslate.poker

import com.tripleslate.poker.PokerTable.RoundSummary
import java.util.*

interface PokerTable {

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

    fun getPotSize(): Int

    fun startNewHandWithSeed(
        fixedHoleCards: Map<out Player, List<Card>>,
        communityCards: List<Card>,
        numCardsToDealPerPlayer: Int = 2
    )

    fun resetActivePlayers()
    fun dealHoleCards(amount: Int = 2)

    fun holeCardsForPlayer(player: Player): List<Card>

    /**
     * Deals a specified number of community cards
     *
     * @param amount The number of cards to deal. Must be >= 0.
     * @return The dealt cards
     */
    fun dealCommunityCards(amount: Int): List<Card>
    fun burnCards(amount: Int): List<Card>

    fun fold(player: Player)
    fun call(player: Player)
    fun raise(player: Player, amount: Int)
    fun check(player: Player)

    /**
     * Move the dealer position to the left by one
     */
    fun advanceDealerPosition()

    fun concludeRound(): RoundSummary

    data class RoundSummary(
        val communityCards: List<Card>,
        val holeCards: Map<Player, List<Card>>,
        val foldedPlayers: Set<Player>,
        val playerBets: Map<Player, Int>,
        val totalPotSize: Int,
        val winners: Set<Player>,
    )
}

class PokerTableImpl : PokerTable {

    override val players: MutableList<Player> = mutableListOf()
    override val numPlayers: Int
        get() = players.size

    override var activePlayers: MutableList<Player> = mutableListOf()

    private var deck: CardDeck = CardDeck.newStandardDeck().apply { shuffle() }

    override val pot: MutableMap<Player, Int> = mutableMapOf()

    private var currentBet: Int = 0
    override val roundBets: MutableMap<Player, Int> = mutableMapOf()

    override var dealerIndex: Int = 0

    override val holeCards: MutableMap<Player, MutableList<Card>> = mutableMapOf()
    override val communityCards: MutableList<Card> = mutableListOf()


    override fun addPlayers(players: Collection<Player>) {
        players.forEach(::addPlayer)
    }

    override fun addPlayer(player: Player) {
        if (player !in players) {
            players.add(player)
        }
    }

    override fun removePlayers(players: Collection<Player>) {
        players.forEach(::removePlayer)
    }

    override fun removePlayer(player: Player) {
        players.remove(player)
    }

    override fun resetActivePlayers() {
        activePlayers = players.toMutableList()
    }

    override fun startNewHandWithSeed(
        fixedHoleCards: Map<out Player, List<Card>>,
        communityCards: List<Card>,
        numCardsToDealPerPlayer: Int
    ) {
        deck = CardDeck.newStandardDeck()
        deck.shuffle()
        holeCards.clear()
        activePlayers = players.toMutableList()

        // remove provided cards so they can't appear twice
        (fixedHoleCards.values.flatten() + communityCards).forEach {
            deck.removeCardIfPresent(it)
        }

        this.communityCards.apply {
            clear()
            addAll(communityCards)
        }

        for (i in 0 until players.size) {
            holeCards.computeIfAbsent(players[i]) {
                fixedHoleCards[it]?.toMutableList() ?: mutableListOf()
            }
        }

        dealHoleCards(amount = numCardsToDealPerPlayer)

        players.forEach {
            pot[it] = 0
            roundBets[it] = 0
        }
    }

    override fun holeCardsForPlayer(player: Player): List<Card> {
        return holeCards[player] ?: emptyList()
    }

    private fun startNewBettingRound() {
        currentBet = 0
        roundBets.clear()
    }

    override fun dealHoleCards(amount: Int) {
        activePlayers = players.toMutableList()

        for (i in 0 until players.size) {
            holeCards.computeIfAbsent(players[i]) { mutableListOf() }
        }
        for (i in 0 + dealerIndex until (players.size * amount) + dealerIndex) {
            val currHoleCards = holeCards[players[i % players.size]]!!

            if (currHoleCards.size < amount) {
                currHoleCards.add(deck.dealCard())
            }
        }

        startNewBettingRound()
    }

    override fun burnCards(amount: Int): List<Card> {
        return List(amount) {
            deck.dealCard()
        }
    }

    override fun dealCommunityCards(amount: Int): List<Card> {
        return List(amount) {
            deck.dealCard()
        }.also { dealtCards ->
            communityCards.addAll(dealtCards)
            startNewBettingRound()
        }
    }

    override fun fold(player: Player) {
        activePlayers.remove(player)
    }

    override fun call(player: Player) {
        val amountToCall = currentBet - player.getRoundBetOrZero()
        require(amountToCall > 0) { "Player ${player.id} has already matched the current bet." }

        require(player.bankroll >= amountToCall) {
            "Insufficient funds! $amountToCall > ${player.bankroll}"
        }

        player.removeAmountFromBankroll(amountToCall.toFloat())

        roundBets[player] = player.getRoundBetOrZero() + amountToCall
        pot[player] = player.getTotalPotBetOrZero() + amountToCall
    }

    override fun raise(player: Player, amount: Int) {
        require(amount > 0) { "Raise amount must be positive." }

        val newBet = currentBet + amount
        val raiseAmount = newBet - player.getRoundBetOrZero()
        require(raiseAmount > 0) { "Raise amount must exceed the player's current bet in this round." }

        require(player.bankroll >= raiseAmount) {
            "Insufficient funds! $raiseAmount > ${player.bankroll}"
        }

        player.removeAmountFromBankroll(raiseAmount.toFloat())

        currentBet = newBet
        roundBets[player] = player.getRoundBetOrZero() + raiseAmount
        pot[player] = player.getTotalPotBetOrZero() + raiseAmount
    }

    private fun Player.getRoundBetOrZero(): Int {
        return roundBets[this] ?: 0
    }

    private fun Player.getTotalPotBetOrZero(): Int {
        return pot[this] ?: 0
    }

    override fun check(player: Player) {
        require(activePlayers.contains(player)) {
            "Player ${player.id} is not in the game."
        }
        require(player.getRoundBetOrZero() == currentBet) {
            "Player ${player.id} cannot check without matching the current bet of $currentBet."
        }
    }

    override fun getPotSize(): Int {
        return pot.values.sum()
    }

    override fun advanceDealerPosition() {
        dealerIndex = (dealerIndex + 1) % numPlayers
    }

    private fun computeWinners(): Set<Player> {
        val hands = activePlayers.associateWith {
            communityCards + holeCardsForPlayer(it)
        }.toList()

        val handRanks = hands.sortedWith(compareBy(HandRankUtils.handComparator()) { it.second })

        return buildSet {
            var maxHand = handRanks.last()
            add(maxHand.first)

            for (handRank in handRanks.subList(0, handRanks.size - 1)) {
                if (HandRankUtils.handComparator().compare(handRank.second, maxHand.second) == 0) {
                    add(handRank.first)
                }
            }
        }
    }

    override fun concludeRound(): RoundSummary {
        val winners = if (activePlayers.size == 1) {
            setOf(activePlayers.single())
        } else {
            computeWinners()
        }

        val potTotal = getPotSize()

        // award funds to winners
        for (winner in winners) {
            winner.addAmountToBankroll(potTotal.toFloat() / winners.size.toFloat())
        }

        advanceDealerPosition()

        return RoundSummary(
            communityCards = communityCards.map { it },
            holeCards = Collections.unmodifiableMap(holeCards.map { it.key to it.value }.toMap()),
            playerBets = players.associateWith {
                it.getTotalPotBetOrZero()
            },
            totalPotSize = getPotSize(),
            foldedPlayers = (players - activePlayers).toSet(),
            winners = winners.toSet()
        ).also {
            this.reset()
        }
    }

    private fun reset() {
        currentBet = 0
        holeCards.clear()
        pot.clear()
        roundBets.clear()
        communityCards.clear()
    }
}
