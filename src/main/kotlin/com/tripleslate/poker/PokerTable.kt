package com.tripleslate.poker

import com.tripleslate.poker.PokerTableImpl.RoundSummary
import java.util.*

interface PokerTable {

    val players: List<Player>
    val numPlayers: Int

    val activePlayers: List<Player>

    val pot: Map<Player, Int>

    val roundBets: Map<Player, Int>

    val dealerIndex: Int
    val holeCardsMap: Map<Player, List<Card>>

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

    fun holeCardsForPlayer(player: Player): List<Card>

    fun dealFlop(): List<Card>
    fun dealTurnOrRiver(): Card
    fun postBlinds(smallBlind: Int, bigBlind: Int)

    fun fold(player: Player)
    fun call(player: Player)
    fun raise(player: Player, amount: Int)
    fun check(player: Player)

    fun advanceDealerPosition()

    fun concludeRound(): RoundSummary
}

class PokerTableImpl : PokerTable {

    override val players: MutableList<Player> = mutableListOf()
    override val numPlayers: Int
        get() = players.size

    override var activePlayers: MutableList<Player> = players.toMutableList()

    private var deck: CardDeck = CardDeck.newStandardDeck().apply { shuffle() }

    override val pot: MutableMap<Player, Int> = mutableMapOf()

    private var currentBet: Int = 0
    override val roundBets: MutableMap<Player, Int> = mutableMapOf()

    override var dealerIndex: Int = 0

    override val holeCardsMap: MutableMap<Player, MutableList<Card>> = mutableMapOf()
    override val communityCards: MutableList<Card> = mutableListOf()

    override fun addPlayers(players: Collection<Player>) {
        players.forEach(::addPlayer)
    }

    override fun addPlayer(player: Player) {
        require(holeCardsMap.isEmpty()) {
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
        require(holeCardsMap.isEmpty()) {
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
        holeCardsMap.clear()
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
            holeCardsMap.computeIfAbsent(players[i]) {
                fixedHoleCards[it]?.toMutableList() ?: mutableListOf()
            }
        }

        for (i in 0 + dealerIndex until (players.size * 2) + dealerIndex) {
            val currHoleCards = holeCardsMap.computeIfAbsent(players[i % players.size]) { mutableListOf() }

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
        return holeCardsMap[player] ?:
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

        holeCardsMap.clear()

        for (i in 0 until players.size) {
            holeCardsMap.computeIfAbsent(players[i]) { mutableListOf() }
        }
        for (i in 0 + dealerIndex until (players.size * 2) + dealerIndex) {
            val currHoleCards = holeCardsMap[players[i % players.size]]!!

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

    override fun dealTurnOrRiver(): Card {
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
        val winners = computeWinners()
        val potTotal = getPotTotal()

        // award funds to winners
        for (winner in winners) {
            winner.addAmountToBankroll(potTotal.toFloat() / winners.size.toFloat())
        }

        advanceDealerPosition()

        return RoundSummary(
            communityCards = communityCards.map { it },
            holeCards = Collections.unmodifiableMap(holeCardsMap.map { it.key to it.value }.toMap()),
            playerBets = players.associateWith {
                it.getTotalPotBetOrZero()
            },
            totalPotSize = getPotTotal(),
            winners = winners.toSet()
        ).also {
            this.reset()
        }
    }

    private fun reset() {
        currentBet = 0
        holeCardsMap.clear()
        pot.clear()
        roundBets.clear()
        communityCards.clear()
    }

    data class RoundSummary(
        val communityCards: List<Card>,
        val holeCards: Map<Player, List<Card>>,
        val playerBets: Map<Player, Int>,
        val totalPotSize: Int,
        val winners: Set<Player>,
    )
}

interface Player {
    val id: Int
    val bankroll: Float

    fun addAmountToBankroll(amount: Float)
    fun removeAmountFromBankroll(amount: Float)
}

data class DefaultPlayer(
    override val id: Int,
    override var bankroll: Float = 1000f
): Player {

    override fun addAmountToBankroll(amount: Float) {
        bankroll += amount
    }

    override fun removeAmountFromBankroll(amount: Float) {
        require(amount >= amount) {
            "Player has insufficient funds!"
        }

        bankroll -= amount
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DefaultPlayer) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id
    }
}


// Example Usage
fun main() {
    val game = PokerTableImpl()

    // 4 players
    game.addPlayer(DefaultPlayer(0))
    game.addPlayer(DefaultPlayer(1))
    game.addPlayer(DefaultPlayer(2))
    game.addPlayer(DefaultPlayer(3))

    game.dealHoleCards() // Deal hole cards to players
    game.postBlinds(1, 2)

    println("Pot total: ${game.getPotTotal()}")

    game.call(DefaultPlayer(4))
    game.call(DefaultPlayer(1))
    game.call(DefaultPlayer(2))
    game.check(DefaultPlayer(3))

    println("Pot total: ${game.getPotTotal()}")


    val flop = game.dealFlop() // Deal the flop
    println("Flop: $flop")

    val turn = game.dealTurnOrRiver() // Deal the turn
    println("Turn: $turn")

    val river = game.dealTurnOrRiver() // Deal the river
    println("River: $river")

    println("Pot total: ${game.getPotTotal()}")
}
