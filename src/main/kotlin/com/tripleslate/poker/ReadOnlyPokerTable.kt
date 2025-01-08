package com.tripleslate.poker

class ReadOnlyPokerTable(
    private val delegate: PokerTable
) : PokerTable by delegate {

    override fun addPlayer(player: Player) {
        throw UnsupportedOperationException("This is a read only view of the poker table!!")
    }

    override fun addPlayers(players: Collection<Player>) {
        throw UnsupportedOperationException("This is a read only view of the poker table!!")
    }

    override fun removePlayer(player: Player) {
        throw UnsupportedOperationException("This is a read only view of the poker table!!")
    }

    override fun removePlayers(players: Collection<Player>) {
        throw UnsupportedOperationException("This is a read only view of the poker table!!")
    }

    override fun startNewHandWithSeed(
        fixedHoleCards: Map<out Player, List<Card>>,
        communityCards: List<Card>,
        numCardsToDealPerPlayer: Int
    ) {
        throw UnsupportedOperationException("This is a read only view of the poker table!!")
    }

    override fun dealHoleCards(amount: Int) {
        throw UnsupportedOperationException("This is a read only view of the poker table!!")
    }

    override fun dealCommunityCards(amount: Int): List<Card> {
        throw UnsupportedOperationException("This is a read only view of the poker table!!")
    }

    override fun fold(player: Player) {
        throw UnsupportedOperationException("This is a read only view of the poker table!!")
    }

    override fun call(player: Player) {
        throw UnsupportedOperationException("This is a read only view of the poker table!!")
    }

    override fun raise(player: Player, amount: Int) {
        throw UnsupportedOperationException("This is a read only view of the poker table!!")
    }

    override fun check(player: Player) {
        throw UnsupportedOperationException("This is a read only view of the poker table!!")
    }

    override fun advanceDealerPosition() {
        throw UnsupportedOperationException("This is a read only view of the poker table!!")
    }

    override fun concludeRound(): PokerTable.RoundSummary {
        throw UnsupportedOperationException("This is a read only view of the poker table!!")
    }
}
