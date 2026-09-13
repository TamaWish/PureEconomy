package io.github.tamawish.pureeconomy.api

import java.math.BigDecimal

/** Outcome of a wallet or bank mutation. */
enum class EconomyStatus {
    SUCCESS,
    INSUFFICIENT,
    MAX_BALANCE,
    UNKNOWN_CURRENCY,
    LOCKED,
    INVALID_AMOUNT,
}

/**
 * Result of a pay / vault / API mutation.
 *
 * [from] is the actor's new balance (wallet unless noted). [to] is the counterpart's new balance
 * when the operation involves two accounts.
 */
data class EconomyResult(
    val status: EconomyStatus,
    val from: BigDecimal? = null,
    val to: BigDecimal? = null,
) {
    val success: Boolean get() = status == EconomyStatus.SUCCESS

    companion object {
        fun ok(
            from: BigDecimal?,
            to: BigDecimal? = null,
        ): EconomyResult = EconomyResult(EconomyStatus.SUCCESS, from, to)

        fun fail(
            status: EconomyStatus,
            from: BigDecimal? = null,
            to: BigDecimal? = null,
        ): EconomyResult = EconomyResult(status, from, to)
    }
}
