package io.github.tamawish.pureeconomy.economy

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Immutable definition of a configured currency. */
class Currency(
    id: String,
    private val singular: String,
    private val plural: String,
    symbol: String?,
    decimals: Int,
    starting: BigDecimal,
    private val max: BigDecimal,
    private val payable: Boolean,
) {
    private val id: String = id.lowercase(Locale.ROOT)
    private val symbol: String = symbol ?: ""
    private val decimals: Int = maxOf(0, decimals)
    private val starting: BigDecimal = starting.setScale(this.decimals, RoundingMode.HALF_UP)
    private val formatPattern: String
    private val decimalFormats: ThreadLocal<DecimalFormat>

    init {
        val pattern = StringBuilder("#,##0")
        if (this.decimals > 0) {
            pattern.append('.')
            pattern.append("0".repeat(this.decimals))
        }
        formatPattern = pattern.toString()
        decimalFormats =
            ThreadLocal.withInitial {
                val format =
                    DecimalFormat(formatPattern, DecimalFormatSymbols.getInstance(Locale.US))
                format.roundingMode = RoundingMode.HALF_UP
                format
            }
    }

    /** Returns the currency id. */
    fun id(): String = id

    /** Returns the singular display name. */
    fun singular(): String = singular

    /** Returns the plural display name. */
    fun plural(): String = plural

    /** Returns the optional symbol prefix. */
    fun symbol(): String = symbol

    /** Returns the configured decimal precision. */
    fun decimals(): Int = decimals

    /** Returns the starting wallet balance for new accounts. */
    fun starting(): BigDecimal = starting

    /** Returns the configured maximum balance. */
    fun max(): BigDecimal = max

    /** Returns whether a finite maximum balance is configured. */
    fun hasMax(): Boolean = max >= BigDecimal.ZERO

    /** Returns whether players may transfer this currency with `/pay`. */
    fun payable(): Boolean = payable

    /** Rounds an amount to this currency's scale. */
    fun normalize(amount: BigDecimal): BigDecimal = amount.setScale(decimals, RoundingMode.HALF_UP)

    /** Returns whether [amount] is above the configured maximum. */
    fun exceedsMax(amount: BigDecimal): Boolean = hasMax() && amount > max

    /** Chooses singular or plural based on the absolute amount. */
    fun displayName(amount: BigDecimal): String = if (amount.compareTo(BigDecimal.ONE) == 0) singular else plural

    /** Formats an amount with the currency symbol or display name. */
    fun format(amount: BigDecimal): String {
        val normalized = normalize(amount)
        val formatted = decimalFormats.get().format(normalized)
        return if (symbol.isEmpty()) {
            "$formatted ${displayName(normalized)}"
        } else {
            symbol + formatted
        }
    }

    /** Returns the plural name used in most player-facing messages. */
    fun name(): String = plural
}
