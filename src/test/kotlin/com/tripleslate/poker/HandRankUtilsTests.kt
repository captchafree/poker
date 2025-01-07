package com.tripleslate.poker

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HandRankUtilsTests : FunSpec() {

    private fun parseHand(vararg cards: String): List<Card> = cards.map { Card.parse(it) }

    init {
        context("Hand rank determination") {
            test("Detect Royal Flush") {
                val hand = parseHand("AS", "KS", "QS", "JS", "TS")
                HandRankUtils.determineHandRank(hand) shouldBe HandRank.ROYAL_FLUSH
            }

            test("Detect Straight Flush") {
                val hand = parseHand("9H", "8H", "7H", "6H", "5H")
                HandRankUtils.determineHandRank(hand) shouldBe HandRank.STRAIGHT_FLUSH
            }

            test("Detect Four of a Kind") {
                val hand = parseHand("9C", "9D", "9H", "9S", "5H")
                HandRankUtils.determineHandRank(hand) shouldBe HandRank.FOUR_OF_A_KIND
            }

            test("Detect Full House") {
                val hand = parseHand("8D", "8C", "8S", "KH", "KC")
                HandRankUtils.determineHandRank(hand) shouldBe HandRank.FULL_HOUSE
            }

            test("Detect Flush") {
                val hand = parseHand("AD", "JD", "9D", "7D", "5D")
                HandRankUtils.determineHandRank(hand) shouldBe HandRank.FLUSH
            }

            test("Detect Straight") {
                val hand = parseHand("5C", "4H", "3S", "2D", "AH")
                HandRankUtils.determineHandRank(hand) shouldBe HandRank.STRAIGHT

                val hand2 = parseHand("TC", "JH", "QS", "KD", "AH")
                HandRankUtils.determineHandRank(hand2) shouldBe HandRank.STRAIGHT
            }

            test("Detect Three of a Kind") {
                val hand = parseHand("7H", "7C", "7D", "KH", "9S")
                HandRankUtils.determineHandRank(hand) shouldBe HandRank.THREE_OF_A_KIND
            }

            test("Detect Two Pair") {
                val hand = parseHand("4S", "4D", "2H", "2C", "9D")
                HandRankUtils.determineHandRank(hand) shouldBe HandRank.TWO_PAIR
            }

            test("Detect One Pair") {
                val hand = parseHand("5H", "5C", "8S", "TD", "2D")
                HandRankUtils.determineHandRank(hand) shouldBe HandRank.ONE_PAIR
            }

            test("Detect High Card") {
                val hand = parseHand("AS", "KD", "7C", "5H", "3D")
                HandRankUtils.determineHandRank(hand) shouldBe HandRank.HIGH_CARD
            }
        }

        context("Hand comparison") {
            test("Compare hands with different ranks") {
                val royalFlush = parseHand("AS", "KS", "QS", "JS", "TS")
                val fullHouse = parseHand("7C", "7H", "7S", "KH", "KC")
                HandRankUtils.handComparator().compare(royalFlush, fullHouse) shouldBe 1
            }

            test("Compare hands with the same rank but different tie-breakers") {
                val straightFlush1 = parseHand("9H", "8H", "7H", "6H", "5H")
                val straightFlush2 = parseHand("8S", "7S", "6S", "5S", "4S")
                HandRankUtils.handComparator().compare(straightFlush1, straightFlush2) shouldBe 1
            }

            test("Compare identical hands") {
                val hand1 = parseHand("KH", "KC", "KS", "2D", "2C")
                val hand2 = parseHand("KH", "KC", "KS", "2D", "2C")
                HandRankUtils.handComparator().compare(hand1, hand2) shouldBe 0
            }
        }
    }
}
