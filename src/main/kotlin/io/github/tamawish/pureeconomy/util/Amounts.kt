package io.github.tamawish.pureeconomy.util

import java.math.BigDecimal

/** Parses non-negative decimal amounts from command input. */
object Amounts {
    /**
     * Parses a non-negative decimal amount, accepting optional thousands commas.
     *
     * @param raw player-supplied text; may be `null`
     * @return parsed amount, or `null` when invalid or negative
     */
    @JvmStatic
    fun parse(raw: String?): BigDecimal? {
        if (raw == null) {
            return null
        }
        val cleaned = raw.trim().replace(",", "")
        if (cleaned.isEmpty()) {
            return null
        }
        return try {
            val value = BigDecimal(cleaned)
            if (value < BigDecimal.ZERO) {
                null
            } else {
                value
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
