package com.tripleslate.poker

import com.tripleslate.com.tripleslate.poker.Card
import java.util.Collections
import kotlin.collections.plus

class HandEquityCalculator {

    private var numOpponents: Int = 1
    private var holeCards: List<Card> = emptyList()
    private var communityCards: List<Card> = emptyList()

    fun currentHoleCards(): List<Card> {
        return Collections.unmodifiableList(holeCards)
    }

    fun currentCommunityCards(): List<Card> {
        return Collections.unmodifiableList(communityCards)
    }

    fun setHoleCards(content: String) {
        if (content.isEmpty()) {
            return
        }

        holeCards = parseCards(content)
    }

    fun setCommunityCards(content: String) {
        if (content.isEmpty()) {
            return
        }

        communityCards = parseCards(content)
    }

    fun addCommunityCards(content: String) {
        if (content.isEmpty()) {
            return
        }

        communityCards + parseCards(content)
    }

    fun setNumOpponents(numOpponents: Int) {
        this.numOpponents = numOpponents
    }

    fun computeCurrentEquity(
        progressListener: MonteCarloEquityCalculator.ProgressListener = MonteCarloEquityCalculator.ProgressListener.Companion.NOOP,
    ): Float {
        require(holeCards.isNotEmpty() && holeCards.size <= 2) { "Must set hole cards before computing equity" }

        val monteCarloEquityCalculator = MonteCarloEquityCalculator()

        val simulationResult = monteCarloEquityCalculator.evaluate(
            numPlayers = 1 + this.numOpponents,
            holeCards = this.holeCards,
            communityCards = this.communityCards,
            progressListener = progressListener
        )

        return simulationResult
    }

    fun reset() {
        numOpponents = 1
        holeCards = emptyList()
        communityCards = emptyList()
    }

    private fun parseCards(content: String): List<Card> {
        return content.split(" ").map {
            Card.Companion.parse(it)
        }
    }
}