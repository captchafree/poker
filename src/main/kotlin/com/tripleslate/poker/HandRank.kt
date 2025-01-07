package com.tripleslate.poker

enum class HandRank(val rank: Int) {
    HIGH_CARD(1),
    ONE_PAIR(2),
    TWO_PAIR(3),
    THREE_OF_A_KIND(4),
    STRAIGHT(5),
    FLUSH(6),
    FULL_HOUSE(7),
    FOUR_OF_A_KIND(8),
    STRAIGHT_FLUSH(9),
    ROYAL_FLUSH(10),
}

object HandRankUtils {

    fun handComparator(): Comparator<List<Card>> {
        return object : Comparator<List<Card>> {
            override fun compare(
                o1: List<Card>,
                o2: List<Card>
            ): Int {
                val handRank = determineHandRank(o1)
                val otherHandRank = determineHandRank(o2)
                // Compare based on the hand rank
                val rankComparison = handRank.rank.compareTo(otherHandRank.rank)
                if (rankComparison != 0) return rankComparison

                val tieBreaker: List<Card> = getTieBreakerCards(o1)
                val otherTieBreaker: List<Card> = getTieBreakerCards(o2)

                // If hands are of the same rank, compare tie-breaker cards individually
                for (i in 0 until minOf(tieBreaker.size, otherTieBreaker.size)) {
                    val cardComparison = tieBreaker[i].value.ordinal.compareTo(otherTieBreaker[i].value.ordinal)
                    if (cardComparison != 0) return cardComparison
                }

                // If tie-breaker cards are identical, the hands are tied
                return 0
            }
        }
    }


    fun determineHandRank(cards: List<Card>): HandRank {
        // Sort the cards by value (descending)
        val sortedCards = cards.sortedByDescending { it.value.ordinal }

        // Now evaluate the hand based on the sorted cards
        return when {
            isRoyalFlush(sortedCards) -> HandRank.ROYAL_FLUSH
            isStraightFlush(sortedCards) -> HandRank.STRAIGHT_FLUSH
            isFourOfAKind(sortedCards) -> HandRank.FOUR_OF_A_KIND
            isFullHouse(sortedCards) -> HandRank.FULL_HOUSE
            isFlush(sortedCards) -> HandRank.FLUSH
            isStraight(sortedCards) -> HandRank.STRAIGHT
            isThreeOfAKind(sortedCards) -> HandRank.THREE_OF_A_KIND
            isTwoPair(sortedCards) -> HandRank.TWO_PAIR
            isOnePair(sortedCards) -> HandRank.ONE_PAIR
            else -> HandRank.HIGH_CARD
        }
    }

    private fun getTieBreakerCards(cards: List<Card>): List<Card> {
        val sortedCards = cards.sortedByDescending { it.value.ordinal }

        return when (determineHandRank(cards)) {
            HandRank.ROYAL_FLUSH, HandRank.STRAIGHT_FLUSH, HandRank.STRAIGHT -> sortedCards.take(5)
            HandRank.FLUSH -> {
                // For flush, take the best 5 cards of the same suit
                val flushCards = cards.groupBy { it.suit }
                    .maxByOrNull { it.value.size }?.value?.sortedByDescending { it.value.ordinal }
                    ?: emptyList()
                flushCards.take(5)
            }
            HandRank.FOUR_OF_A_KIND -> getFourOfAKindTieBreaker(sortedCards)
            HandRank.FULL_HOUSE -> getFullHouseTieBreaker(sortedCards)
            HandRank.THREE_OF_A_KIND -> getThreeOfAKindTieBreaker(sortedCards)
            HandRank.TWO_PAIR -> getTwoPairTieBreaker(sortedCards)
            HandRank.ONE_PAIR -> getOnePairTieBreaker(sortedCards)
            HandRank.HIGH_CARD -> sortedCards.take(5) // Just the highest cards
        }
    }

    private fun getFourOfAKindTieBreaker(cards: List<Card>): List<Card> {
        val grouped = cards.groupBy { it.value }
        val fourOfAKind = grouped.filter { it.value.size == 4 }.keys.first()
        val kicker = cards.filter { it.value != fourOfAKind }.maxByOrNull { it.value.ordinal }
        return listOf(Card(fourOfAKind, cards[0].suit)) + kicker!!
    }

    private fun getFullHouseTieBreaker(cards: List<Card>): List<Card> {
        val grouped = cards.groupBy { it.value }
        val threeOfAKind = grouped.filter { it.value.size == 3 }.keys.first()
        val pair = grouped.filter { it.value.size == 2 }.keys.first()
        return listOf(Card(threeOfAKind, cards[0].suit), Card(pair, cards[0].suit))
    }

    private fun getThreeOfAKindTieBreaker(cards: List<Card>): List<Card> {
        val grouped = cards.groupBy { it.value }
        val threeOfAKind = grouped.filter { it.value.size == 3 }.keys.first()
        val kickers = cards.filter { it.value != threeOfAKind }.sortedByDescending { it.value.ordinal }.take(2)
        return listOf(Card(threeOfAKind, cards[0].suit)) + kickers
    }

    private fun getTwoPairTieBreaker(cards: List<Card>): List<Card> {
        val grouped = cards.groupBy { it.value }
        val pairs = grouped.filter { it.value.size == 2 }.keys.sortedByDescending { it.ordinal }
        val kicker = cards.filter { it.value !in pairs }.maxByOrNull { it.value.ordinal }
        return listOf(Card(pairs[0], cards[0].suit), Card(pairs[1], cards[0].suit)) + kicker!!
    }

    private fun getOnePairTieBreaker(cards: List<Card>): List<Card> {
        val grouped = cards.groupBy { it.value }
        val pair = grouped.filter { it.value.size == 2 }.keys.first()
        val kickers = cards.filter { it.value != pair }.sortedByDescending { it.value.ordinal }.take(3)
        return listOf(Card(pair, cards[0].suit)) + kickers
    }


    private fun isRoyalFlush(cards: List<Card>): Boolean {
        // Get the top 5 cards for straight flush
        val bestCards = getBestStraightFlushCards(cards)
        return isStraightFlush(bestCards) && bestCards[0].value == CardValue.ACE
    }

    private fun isStraightFlush(cards: List<Card>): Boolean {
        // Get the top 5 cards for a straight flush
        val bestCards = getBestStraightFlushCards(cards)
        return isStraight(bestCards) && isFlush(bestCards)
    }

    private fun getBestStraightFlushCards(cards: List<Card>): List<Card> {
        // Get only the flush cards, sorted by value
        val flushCards = cards.groupBy { it.suit }
            .maxByOrNull { it.value.size }?.value?.sortedByDescending { it.value.ordinal }
            ?: emptyList()

        return flushCards.take(5) // Take only the best 5 flush cards
    }

    private fun isFourOfAKind(cards: List<Card>): Boolean {
        // Get the best 5 cards for Four of a Kind
        val sortedCards = cards.sortedByDescending { it.value.ordinal }
        val grouped = sortedCards.groupBy { it.value }
        return grouped.any { it.value.size >= 4 }
    }

    private fun isFullHouse(cards: List<Card>): Boolean {
        // Get the best 5 cards for a Full House
        val sortedCards = cards.sortedByDescending { it.value.ordinal }
        val grouped = sortedCards.groupBy { it.value }
        val counts = grouped.map { it.value.size }.sorted()
        return counts == listOf(3, 2) // Check if there's a triplet and a pair
    }

    private fun isFlush(cards: List<Card>): Boolean {
        // Get the flush cards and select the best 5
        val flushCards = cards.groupBy { it.suit }
            .maxByOrNull { it.value.size }?.value?.sortedByDescending { it.value.ordinal }
            ?: emptyList()
        return flushCards.size >= 5
    }

    private fun isStraight(cards: List<Card>): Boolean {
        val sortedCards = cards.sortedByDescending { it.value.ordinal }
        val values = sortedCards.map { it.value.ordinal }

        // Check for a straight (consecutive sequence)
        return values.zipWithNext().all { it.first - it.second == 1 }
    }

    private fun isThreeOfAKind(cards: List<Card>): Boolean {
        // Get the best 5 cards for Three of a Kind
        val sortedCards = cards.sortedByDescending { it.value.ordinal }
        val grouped = sortedCards.groupBy { it.value }
        return grouped.any { it.value.size >= 3 }
    }

    private fun isTwoPair(cards: List<Card>): Boolean {
        // Get the best 5 cards for Two Pair
        val sortedCards = cards.sortedByDescending { it.value.ordinal }
        val grouped = sortedCards.groupBy { it.value }
        val pairValues = grouped.filter { it.value.size == 2 }.keys.sortedByDescending { it.ordinal }

        return pairValues.size >= 2
    }

    private fun isOnePair(cards: List<Card>): Boolean {
        // Get the best 5 cards for One Pair
        val sortedCards = cards.sortedByDescending { it.value.ordinal }
        val grouped = sortedCards.groupBy { it.value }
        return grouped.any { it.value.size == 2 }
    }

}
