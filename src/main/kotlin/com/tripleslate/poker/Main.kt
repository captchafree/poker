package com.tripleslate.poker

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.mordant.platform.MultiplatformSystem.exitProcess
import com.tripleslate.poker.MonteCarloEquityCalculator.ProgressListener

fun main(args: Array<String>) {

    val calculator = HandEquityCalculator()
    calculator.setHoleCards("ac as")
    calculator.setNumOpponents(1)

    println(calculator.computeCurrentEquity())

    return

    PokerCLI().main(args)
}

class PokerCLI : CliktCommand(name = "Poker Equity Calculator") {
    private val holeCards: String by option(help = "Hole cards (e.g., 'AS AC')").prompt("Enter hole cards")
    private val communityCards: String? by option(help = "Community cards (e.g., 'AD 8D KD')").prompt("Enter community cards (press enter if there are none)")
    private val opponents: Int by option(help = "Number of opponents").int().prompt("Enter number of opponents")

    override fun run() {
        val calculator = HandEquityCalculator()
        calculator.setNumOpponents(opponents)
        calculator.setHoleCards(holeCards)

        if (!communityCards.isNullOrBlank()) {
            calculator.setCommunityCards(communityCards!!)
        }

        displayState(calculator, opponents)

        // Interactive session
        while (true) {
            echo(
                "\nOptions: \n" +
                "(e) Calculate equity\n" +
                "(ac <cards>) Add community card(s)\n" +
                "(shc <cards>) Set new hole cards\n" +
                "(o <number>) Set opponents\n" +
                "(sc <cards>) Set community cards\n" +
                "(r) Reset\n" +
                "(q) Quit\n"
            )
            val input = readLine() ?: continue

            when {
                input == "e" -> {
                    try {
                        echo("\n")
                        val equity = calculateEquityWithProgress(calculator)
                        echo("\nEQUITY: ${"%.2f".format(equity * 100)}%")
                    } catch (e: Exception) {
                        echo("Error: ${e.message}")
                    }
                }
                input.startsWith("ac ") -> {
                    val newCards = input.removePrefix("ac ").trim()
                    try {
                        calculator.addCommunityCards(newCards)
                        echo("Added community cards: $newCards")
                        displayState(calculator, opponents)
                    } catch (e: Exception) {
                        echo("Error adding community card: ${e.message}")
                    }
                }
                input.startsWith("shc ") -> {
                    val newHoleCards = input.removePrefix("shc ").trim()
                    try {
                        calculator.setHoleCards(newHoleCards)
                        echo("Hole cards updated.")
                        displayState(calculator, opponents)
                    } catch (e: Exception) {
                        echo("Error setting hole cards: ${e.message}")
                    }
                }
                input.startsWith("o ") -> {
                    val numOpponents = input.removePrefix("o ").toIntOrNull()
                    if (numOpponents != null) {
                        calculator.setNumOpponents(numOpponents)
                        echo("Opponents set to: $numOpponents")
                        displayState(calculator, numOpponents)
                    } else {
                        echo("Invalid number of opponents.")
                    }
                }
                input.startsWith("sc ") -> {
                    val cards = input.removePrefix("sc ").trim()
                    try {
                        calculator.setCommunityCards(cards)
                        echo("Community cards updated.")
                        displayState(calculator, opponents)
                    } catch (e: Exception) {
                        echo("Error setting community cards: ${e.message}")
                    }
                }
                input == "r" -> {
                    calculator.reset()
                    echo("Simulator reset.")
                    displayState(calculator, opponents)
                }
                input == "q" -> {
                    echo("Exiting...")
                    exitProcess(0)
                }
                else -> echo("Invalid option.")
            }
        }
    }

    private fun displayState(calculator: HandEquityCalculator, opponents: Int) {
        echo("\nCurrent Environment:")
        echo("Hole Cards: ${calculator.currentHoleCards().joinToString(" ")}")
        echo("Community Cards: ${if (calculator.currentCommunityCards().isEmpty()) "None" else calculator.currentCommunityCards().joinToString(" ")}")
        echo("Opponents: $opponents")
    }

    private fun calculateEquityWithProgress(calculator: HandEquityCalculator): Float {
        var lastProgressPercentage = 0

        val listener = ProgressListener { progress, total ->
            val percentage = (progress.toFloat() / total.toFloat()) * 100f

            if (percentage.toInt() != lastProgressPercentage) {
                echo("\rCalculating equity... $percentage% complete", trailingNewline = false)
                lastProgressPercentage = percentage.toInt()
            }
        }

        return calculator.computeCurrentEquity(listener)
    }
}
