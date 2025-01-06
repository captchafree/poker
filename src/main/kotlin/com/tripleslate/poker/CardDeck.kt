package com.tripleslate.com.tripleslate.poker

interface CardDeck {

    val size: Int

    /**
     * Removes and returns the card from the top of the deck
     */
    fun dealCard(): Card

    fun addCardToTop(card: Card)
    fun addCardToBottom(card: Card)

    fun removeCardIfPresent(card: Card): Boolean

    fun shuffle()

    companion object {

        /**
         * Creates a standard deck with 52 cards.
         */
        fun newStandardDeck(): CardDeck {
            val standardDeckContents = buildList {
                for (suit in CardSuit.entries) {
                    for (value in CardValue.entries) {
                        add(
                            Card(
                                value = value,
                                suit = suit,
                            )
                        )
                    }
                }
            }

            return CardDeckImpl(standardDeckContents)
        }
    }

}

private class CardDeckImpl(
    initialCards: Collection<Card>,
): CardDeck {

    /**
     * the start of list is the "top" of the deck and the end is the "bottom"
     */
    private val contents: MutableList<Card> = initialCards.toMutableList()

    override val size: Int
        get() = contents.size

    override fun dealCard(): Card {
        return contents.removeFirst()
    }

    override fun addCardToTop(card: Card) {
        contents.add(0, card)
    }

    override fun addCardToBottom(card: Card) {
        contents.add(card)
    }

    override fun removeCardIfPresent(card: Card): Boolean {
        return contents.remove(card)
    }

    override fun shuffle() {
        contents.shuffle()
    }

    override fun toString(): String {
        return contents.toString()
    }
}


data class Card(
    val value: CardValue,
    val suit: CardSuit,
) {

    override fun toString(): String {
        val valueAsString = when (value) {
            CardValue.ACE -> "A"
            CardValue.TWO -> "2"
            CardValue.THREE -> "3"
            CardValue.FOUR -> "4"
            CardValue.FIVE -> "5"
            CardValue.SIX -> "6"
            CardValue.SEVEN -> "7"
            CardValue.EIGHT -> "8"
            CardValue.NINE -> "9"
            CardValue.TEN -> "T"
            CardValue.JACK -> "J"
            CardValue.QUEEN -> "Q"
            CardValue.KING -> "K"
        }

        val suitAsString = when (suit) {
            CardSuit.SPADES -> "♠"
            CardSuit.HEARTS -> "♥"
            CardSuit.CLUBS -> "♣"
            CardSuit.DIAMONDS -> "♦"
        }

        return "$valueAsString$suitAsString"
    }

    companion object {
        fun parse(text: String): Card {
            val valuePart = text[0].lowercase()
            val suitPart = text[1].lowercase()

            val parsedValue = when (valuePart) {
                "2" -> CardValue.TWO
                "3" -> CardValue.THREE
                "4" -> CardValue.FOUR
                "5" -> CardValue.FIVE
                "6" -> CardValue.SIX
                "7" -> CardValue.SEVEN
                "8" -> CardValue.EIGHT
                "9" -> CardValue.NINE
                "t" -> CardValue.TEN
                "j" -> CardValue.JACK
                "q" -> CardValue.QUEEN
                "k" -> CardValue.KING
                "a" -> CardValue.ACE
                else -> error("Invalid value.")
            }

            val parsedSuit = when (suitPart) {
                "s" -> CardSuit.SPADES
                "h" -> CardSuit.HEARTS
                "c" -> CardSuit.CLUBS
                "d" -> CardSuit.DIAMONDS
                else -> error("Invalid suit. Must be one of s, h, c, d")
            }

            return Card(
                parsedValue,
                parsedSuit
            )
        }
    }
}

enum class CardValue {
    TWO,
    THREE,
    FOUR,
    FIVE,
    SIX,
    SEVEN,
    EIGHT,
    NINE,
    TEN,
    JACK,
    QUEEN,
    KING,
    ACE,
}

enum class CardSuit {
    SPADES,
    HEARTS,
    CLUBS,
    DIAMONDS
}
