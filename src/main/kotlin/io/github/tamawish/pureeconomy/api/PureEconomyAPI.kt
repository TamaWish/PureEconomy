package io.github.tamawish.pureeconomy.api

import java.math.BigDecimal
import java.util.UUID

/**
 * Public multi-currency economy API for SoftDepend integrators.
 *
 * Obtain via Bukkit `ServicesManager`:
 * ```
 * RegisteredServiceProvider<PureEconomyAPI> rsp =
 *     Bukkit.getServicesManager().getRegistration(PureEconomyAPI.class);
 * if (rsp != null) {
 *   PureEconomyAPI api = rsp.getProvider();
 * }
 * ```
 *
 * Vault still exposes only the configured default currency for shops. Prefer this API when you need
 * secondary currencies, banks, pay, or baltop.
 */
interface PureEconomyAPI {
    /** Returns the configured default currency id (lowercase). */
    fun defaultCurrencyId(): String

    /** Returns sorted currency ids. */
    fun currencyIds(): List<String>

    /**
     * Returns whether [currencyId] is a loaded currency.
     *
     * @param currencyId currency id; blank uses the default
     */
    fun hasCurrency(currencyId: String?): Boolean

    /**
     * Formats an amount for display using the currency's symbol or name.
     *
     * @param currencyId currency id; blank uses the default
     * @param amount raw amount
     * @return formatted text, or empty when the currency is unknown
     */
    fun format(
        currencyId: String?,
        amount: BigDecimal?,
    ): String

    /**
     * Returns the currency symbol, or empty when none / unknown.
     *
     * @param currencyId currency id; blank uses the default
     */
    fun symbol(currencyId: String?): String

    /**
     * Returns decimal places for the currency, or `-1` when unknown.
     *
     * @param currencyId currency id; blank uses the default
     */
    fun decimals(currencyId: String?): Int

    /**
     * Returns whether players may `/pay` this currency.
     *
     * @param currencyId currency id; blank uses the default
     */
    fun payable(currencyId: String?): Boolean

    /**
     * Returns a wallet balance.
     *
     * @param uuid player unique id
     * @param currencyId currency id; blank uses the default
     * @return balance, or `null` when the currency is unknown
     */
    fun getBalance(
        uuid: UUID?,
        currencyId: String?,
    ): BigDecimal?

    /**
     * Returns whether the wallet holds at least [amount].
     *
     * @param uuid player unique id
     * @param currencyId currency id; blank uses the default
     * @param amount required amount
     * @return `false` when the currency is unknown or funds are insufficient
     */
    fun has(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): Boolean

    /**
     * Sets a wallet balance.
     *
     * @param uuid player unique id
     * @param currencyId currency id; blank uses the default
     * @param amount desired balance
     * @return `false` when the currency is unknown or the amount exceeds the max
     */
    fun set(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): Boolean

    /**
     * Deposits into a wallet.
     *
     * @param uuid player unique id
     * @param currencyId currency id; blank uses the default
     * @param amount amount to add
     * @return `false` when the currency is unknown or the result exceeds the max
     */
    fun deposit(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): Boolean

    /**
     * Withdraws from a wallet.
     *
     * @param uuid player unique id
     * @param currencyId currency id; blank uses the default
     * @param amount amount to remove
     * @return `false` when the currency is unknown or funds are insufficient
     */
    fun withdraw(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): Boolean

    /**
     * Returns a personal bank balance.
     *
     * @param uuid player unique id
     * @param currencyId currency id; blank uses the default
     * @return balance, or `null` when the currency is unknown
     */
    fun getBank(
        uuid: UUID?,
        currencyId: String?,
    ): BigDecimal?

    /**
     * Returns whether the bank holds at least [amount].
     */
    fun hasBank(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): Boolean

    /** Sets a personal bank balance. */
    fun setBank(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): Boolean

    /** Deposits into a personal bank. */
    fun depositBank(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): Boolean

    /** Withdraws from a personal bank. */
    fun withdrawBank(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): Boolean

    /**
     * Moves funds between two wallets (same rules as `/pay`).
     *
     * @return `false` when the currency is not payable, unknown, or the transfer cannot complete
     */
    fun transfer(
        from: UUID?,
        to: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): Boolean

    /**
     * Deposit with a reason code. Existing [deposit] remains a Boolean wrapper.
     */
    fun depositResult(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): EconomyResult

    /**
     * Withdraw with a reason code. Existing [withdraw] remains a Boolean wrapper.
     */
    fun withdrawResult(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): EconomyResult

    /**
     * Transfer with a reason code. Existing [transfer] remains a Boolean wrapper.
     */
    fun transferResult(
        from: UUID?,
        to: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): EconomyResult

    /** Resolves an online, stored, or offline player name to a UUID. */
    fun resolve(name: String?): UUID?

    /**
     * Returns one page of richest wallets (1-based [page]).
     *
     * @return empty list when the currency is unknown
     */
    fun top(
        currencyId: String?,
        page: Int,
        pageSize: Int,
    ): List<TopEntry>

    /** Returns how many baltop pages exist for a currency (`1` when empty/unknown). */
    fun topPages(
        currencyId: String?,
        pageSize: Int,
    ): Int

    /** One public baltop row. */
    data class TopEntry(
        val uuid: UUID,
        val name: String,
        val balance: BigDecimal,
    )
}
