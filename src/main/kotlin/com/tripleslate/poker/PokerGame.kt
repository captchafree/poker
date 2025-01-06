package com.tripleslate.poker

import com.tripleslate.com.tripleslate.poker.Card
import com.tripleslate.com.tripleslate.poker.CardDeck
import com.tripleslate.poker.PokerGame.Player

class PokerGame(val numPlayers: Int) {

    private var deck: CardDeck = CardDeck.Companion.newStandardDeck()
    val players: List<Player> = List(numPlayers) { Player(it) }
    private val pot: MutableMap<Player, Int> = mutableMapOf()
    private var currentBet: Int = 0
    var dealerIndex: Int = 0
    var activePlayers: MutableList<Player> = players.toMutableList()
    val communityCards: MutableList<Card> = mutableListOf()
    val holeCards: MutableList<MutableList<Card>> = mutableListOf()

    init {
        require(numPlayers > 1) { "The game requires at least 2 players." }
        deck.shuffle()
        players.forEach { pot[it] = 0 }
    }

    fun startNewHandWithSeed(
        fixedHoleCards: Map<Int, List<Card>>,
        communityCards: List<Card>,
    ) {
        holeCards.clear()

        // remove provided cards so they can't appear twice
        (fixedHoleCards.values.flatten() + communityCards).forEach {
            deck.removeCardIfPresent(it)
        }

        this.communityCards.apply {
            clear()
            addAll(communityCards)
        }

        for (i in 0 until players.size) {
            holeCards.add(fixedHoleCards[i]?.toMutableList() ?: mutableListOf())
        }

        for (i in 0 + dealerIndex until (players.size * 2) + dealerIndex) {
            val currHoleCards = holeCards[i % players.size]

            if (currHoleCards.size < 2) {
                currHoleCards.add(deck.dealCard())
            }
        }
    }

    fun dealHoleCards() {
        require(activePlayers.size >= 2) { "Cannot deal cards with less than 2 active players." }
        holeCards.clear()

        for (i in 1..players.size) {
            holeCards.add(mutableListOf())
        }
        for (i in 0 + dealerIndex until (players.size * 2) + dealerIndex) {
            holeCards[i % players.size].add(deck.dealCard())
        }
    }

    fun dealFlop(): List<Card> {
        deck.dealCard() // Burn one card
        return listOf(deck.dealCard(), deck.dealCard(), deck.dealCard()).also {
            communityCards.addAll(it)
        }
    }

    fun dealTurnOrRiver(): Card {
        deck.dealCard() // Burn one card
        return deck.dealCard().also {
            communityCards.add(it)
        }
    }

    fun postBlinds(smallBlind: Int, bigBlind: Int) {
        require(smallBlind > 0 && bigBlind > 0) { "Blinds must be positive values." }
        require(bigBlind > smallBlind) { "Big blind must be greater than small blind." }

        val smallBlindPlayer = players[(dealerIndex + 1) % numPlayers]
        val bigBlindPlayer = players[(dealerIndex + 2) % numPlayers]

        pot[smallBlindPlayer] = smallBlind
        pot[bigBlindPlayer] = bigBlind
        currentBet = bigBlind
    }

    fun fold(player: Player) {
        require(activePlayers.contains(player)) { "Player ${player.id} is not in the game." }
        activePlayers.remove(player)
    }

    fun call(player: Player) {
        require(activePlayers.contains(player)) { "Player ${player.id} is not in the game." }
        val amountToCall = currentBet - (pot[player] ?: 0)
        require(amountToCall > 0) { "Player ${player.id} has already matched the current bet." }
        pot[player] = (pot[player] ?: 0) + amountToCall
    }

    fun raise(player: Player, amount: Int) {
        require(activePlayers.contains(player)) { "Player ${player.id} is not in the game." }
        require(amount > 0) { "Raise amount must be positive." }

        val totalBet = currentBet + amount
        currentBet = totalBet
        pot[player] = (pot[player] ?: 0) + totalBet
    }

    fun check(player: Player) {
        require(activePlayers.contains(player)) { "Player ${player.id} is not in the game." }
        require((pot[player] ?: 0) >= currentBet) { "Player ${player.id} cannot check without matching the current bet of $currentBet." }
    }

    fun nextDealer() {
        dealerIndex = (dealerIndex + 1) % numPlayers
    }

    fun getWinners(): Set<Int> {
        require(communityCards.size == 5) { "Exactly 5 community cards must be shown. There are ${communityCards.size} currently shown." }
        val hands = players.associateWith {
            communityCards + holeCards[it.id]
        }.toList()


        val handRanks = hands.sortedWith(compareBy(HandRankUtils.handComparator()) { it.second })

        return buildSet {
            var maxHand = handRanks.last()
            add(maxHand.first.id)

            for (handRank in handRanks.subList(0, handRanks.size - 1)) {
                if (HandRankUtils.handComparator().compare(handRank.second, maxHand.second) == 0) {
                    add(handRank.first.id)
                }
            }
        }
    }

    fun reset() {
        holeCards.clear()
        pot.clear()
        communityCards.clear()
        deck = CardDeck.Companion.newStandardDeck()
    }

    fun getPotTotal(): Int {
        return pot.values.sum()
    }

    data class Player(val id: Int)
}

// Example Usage
fun main() {
    val game = PokerGame(4) // 4 players
    game.dealHoleCards() // Deal hole cards to players
    game.postBlinds(1, 2)

    println("Pot total: ${game.getPotTotal()}")

    game.call(Player(4))
    game.call(Player(1))
    game.call(Player(2))
    game.check(Player(3))

    println("Pot total: ${game.getPotTotal()}")


    val flop = game.dealFlop() // Deal the flop
    println("Flop: $flop")

    val turn = game.dealTurnOrRiver() // Deal the turn
    println("Turn: $turn")

    val river = game.dealTurnOrRiver() // Deal the river
    println("River: $river")

    println("Pot total: ${game.getPotTotal()}")
}
