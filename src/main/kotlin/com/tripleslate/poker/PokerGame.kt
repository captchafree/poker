package com.tripleslate.poker

class PokerGame {

    val players: MutableList<Player> = mutableListOf()

    var activePlayers: MutableList<Player> = players.toMutableList()

    private var deck: CardDeck = CardDeck.newStandardDeck().apply { shuffle() }

    val pot: MutableMap<Player, Int> = mutableMapOf()

    private var currentBet: Int = 0
    val roundBets: MutableMap<Player, Int> = mutableMapOf()

    var dealerIndex: Int = 0

    val holeCards: MutableList<MutableList<Card>> = mutableListOf()
    val communityCards: MutableList<Card> = mutableListOf()

    val numPlayers: Int
        get() = players.size

    fun addPlayer(player: Player) {
        require(holeCards.isEmpty()) {
            "Cannot add a player in the middle of a hand"
        }

        if (player !in players) {
            players.add(player)
        }
    }

    fun removePlayer(player: Player) {
        require(holeCards.isEmpty()) {
            "Cannot remove a player in the middle of a hand"
        }

        players.remove(player)
    }

    fun startNewHandWithSeed(
        fixedHoleCards: Map<Int, List<Card>>,
        communityCards: List<Card>,
    ) {
        require(numPlayers > 1) { "The game requires at least 2 players." }

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
            holeCards.add(fixedHoleCards[i]?.toMutableList() ?: mutableListOf())
        }

        for (i in 0 + dealerIndex until (players.size * 2) + dealerIndex) {
            val currHoleCards = holeCards[i % players.size]

            if (currHoleCards.size < 2) {
                currHoleCards.add(deck.dealCard())
            }
        }

        players.forEach {
            pot[it] = 0
            roundBets[it] = 0
        }
    }

    fun startNewBettingRound() {
        currentBet = 0
        roundBets.clear()
    }

    fun dealHoleCards() {
        activePlayers = players.toMutableList()

        require(activePlayers.size >= 2) { "Cannot deal cards with less than 2 active players." }

        holeCards.clear()

        for (i in 1..players.size) {
            holeCards.add(mutableListOf())
        }
        for (i in 0 + dealerIndex until (players.size * 2) + dealerIndex) {
            holeCards[i % players.size].add(deck.dealCard())
        }

        startNewBettingRound()

        // postBlinds(1, 2)
    }

    fun dealFlop(): List<Card> {
        deck.dealCard() // Burn one card
        return listOf(deck.dealCard(), deck.dealCard(), deck.dealCard()).also {
            communityCards.addAll(it)
            startNewBettingRound()
        }
    }

    fun dealTurnOrRiver(): Card {
        deck.dealCard() // Burn one card
        return deck.dealCard().also {
            communityCards.add(it)
            startNewBettingRound()
        }
    }

    fun postBlinds(smallBlind: Int, bigBlind: Int) {
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

    fun fold(player: Player) {
        require(activePlayers.contains(player)) { "Player ${player.id} is not in the game." }
        activePlayers.remove(player)
    }

    fun call(player: Player) {
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

    fun raise(player: Player, amount: Int) {
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

    fun check(player: Player) {
        require(activePlayers.contains(player)) { "Player ${player.id} is not in the game." }
        require(player.getRoundBetOrZero() == currentBet) { "Player ${player.id} cannot check without matching the current bet of $currentBet." }
        roundBets[player] = 0
    }

    fun getPotTotal(): Int {
        return pot.values.sum()
    }

    fun nextDealer() {
        dealerIndex = (dealerIndex + 1) % numPlayers
    }

    fun getWinners(): Set<Int> {
        require(communityCards.size == 5) { "Exactly 5 community cards must be shown. There are ${communityCards.size} currently shown." }
        val hands = activePlayers.associateWith {
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

    fun concludeRound(): RoundSummary {
        val winners = getWinners()
        val potTotal = getPotTotal()

        // award funds to winners
        for (winnerIdx in winners) {
            players.first { it.id == winnerIdx }.addAmountToBankroll(potTotal.toFloat() / winners.size.toFloat())
        }

        nextDealer()

        return RoundSummary(
            communityCards = communityCards.map { it },
            holeCards = holeCards.mapIndexed { idx, cards ->
                players.first {
                    it.id == idx
                } to cards
            }.toMap(),
            playerBets = players.associateWith {
                it.getTotalPotBetOrZero()
            },
            winners = winners.map { playerIdx ->
                players.first {
                    playerIdx == it.id
                }
            }.toSet()
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

    data class RoundSummary(
        val communityCards: List<Card>,
        val holeCards: Map<Player, List<Card>>,
        val playerBets: Map<Player, Int>,
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
    val game = PokerGame()

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
