package com.tripleslate.poker

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
