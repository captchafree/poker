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
     * player -> bets in the current round (preflop, flop, etc)
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

    fun getPotTotal(): Int

    fun startNewHandWithSeed(
        fixedHoleCards: Map<out Player, List<Card>>,
        communityCards: List<Card>,
    )

    fun dealHoleCards()
    fun postBlinds(smallBlind: Int, bigBlind: Int)

    fun holeCardsForPlayer(player: Player): List<Card>

    fun dealFlop(): List<Card>
    fun dealTurn(): Card
    fun dealRiver(): Card

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
        require(holeCards.isEmpty()) {
            "Cannot add a player in the middle of a hand"
        }

        if (player !in players) {
            players.add(player)
        }
    }

    override fun removePlayers(players: Collection<Player>) {
        players.forEach(::removePlayer)
    }

    override fun removePlayer(player: Player) {
        require(holeCards.isEmpty()) {
            "Cannot add a player in the middle of a hand"
        }

        players.remove(player)
    }

    override fun startNewHandWithSeed(
        fixedHoleCards: Map<out Player, List<Card>>,
        communityCards: List<Card>,
    ) {
        require(numPlayers > 1) {
            "The game requires at least 2 players."
        }

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

        for (i in 0 + dealerIndex until (players.size * 2) + dealerIndex) {
            val currHoleCards = holeCards.computeIfAbsent(players[i % players.size]) { mutableListOf() }

            if (currHoleCards.size < 2) {
                currHoleCards.add(deck.dealCard())
            }
        }

        players.forEach {
            pot[it] = 0
            roundBets[it] = 0
        }
    }

    override fun holeCardsForPlayer(player: Player): List<Card> {
        return holeCards[player] ?:
            error("Missing player $player")
    }

    private fun startNewBettingRound() {
        currentBet = 0
        roundBets.clear()
    }

    override fun dealHoleCards() {
        activePlayers = players.toMutableList()

        require(activePlayers.size >= 2) {
            "Cannot deal cards with less than 2 active players."
        }

        holeCards.clear()

        for (i in 0 until players.size) {
            holeCards.computeIfAbsent(players[i]) { mutableListOf() }
        }
        for (i in 0 + dealerIndex until (players.size * 2) + dealerIndex) {
            val currHoleCards = holeCards[players[i % players.size]]!!

            if (currHoleCards.size < 2) {
                currHoleCards.add(deck.dealCard())
            }
        }

        startNewBettingRound()
    }

    override fun dealFlop(): List<Card> {
        deck.dealCard() // Burn one card
        return listOf(deck.dealCard(), deck.dealCard(), deck.dealCard()).also {
            communityCards.addAll(it)
            startNewBettingRound()
        }
    }

    override fun dealTurn(): Card {
        return dealTurnOrRiver()
    }

    override fun dealRiver(): Card {
        return dealTurnOrRiver()
    }

    private fun dealTurnOrRiver(): Card {
        deck.dealCard() // Burn one card
        return deck.dealCard().also {
            communityCards.add(it)
            startNewBettingRound()
        }
    }

    override fun postBlinds(smallBlind: Int, bigBlind: Int) {
        require(smallBlind > 0 && bigBlind > 0) { "Blinds must be positive values." }
        require(bigBlind > smallBlind) { "Big blind must be greater than small blind." }

        val smallBlindPlayer = players[(dealerIndex + 1) % numPlayers]
        val bigBlindPlayer = players[(dealerIndex + 2) % numPlayers]

        pot[smallBlindPlayer] = smallBlind
        pot[bigBlindPlayer] = bigBlind

        roundBets[smallBlindPlayer] = smallBlind
        roundBets[bigBlindPlayer] = bigBlind

        currentBet = bigBlind

        smallBlindPlayer.removeAmountFromBankroll(smallBlind.toFloat())
        bigBlindPlayer.removeAmountFromBankroll(bigBlind.toFloat())
    }

    override fun fold(player: Player) {
        require(activePlayers.contains(player)) { "Player ${player.id} is not in the game." }
        activePlayers.remove(player)
    }

    override fun call(player: Player) {
        require(activePlayers.contains(player)) { "Player ${player.id} is not in the game." }
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
        require(activePlayers.contains(player)) { "Player ${player.id} is not in the game." }
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

        roundBets[player] = 0
    }

    override fun getPotTotal(): Int {
        return pot.values.sum()
    }

    override fun advanceDealerPosition() {
        dealerIndex = (dealerIndex + 1) % numPlayers
    }

    private fun computeWinners(): Set<Player> {
        require(communityCards.size == 5) {
            "Exactly 5 community cards must be shown. There are ${communityCards.size} currently shown."
        }

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

        val potTotal = getPotTotal()

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
            totalPotSize = getPotTotal(),
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
