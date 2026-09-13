package io.github.tamawish.pureeconomy.api

import io.github.tamawish.pureeconomy.economy.Currency
import io.github.tamawish.pureeconomy.economy.EconomyService
import java.math.BigDecimal
import java.util.UUID

/** [PureEconomyAPI] backed by [EconomyService]. */
class PureEconomyApiService(
    private val economy: EconomyService,
) : PureEconomyAPI {
    override fun defaultCurrencyId(): String = economy.defaultId()

    override fun currencyIds(): List<String> = economy.currencyIds()

    override fun hasCurrency(currencyId: String?): Boolean = resolveCurrency(currencyId) != null

    override fun format(
        currencyId: String?,
        amount: BigDecimal?,
    ): String {
        val currency = resolveCurrency(currencyId)
        if (currency == null || amount == null) {
            return ""
        }
        return currency.format(amount)
    }

    override fun symbol(currencyId: String?): String = resolveCurrency(currencyId)?.symbol() ?: ""

    override fun decimals(currencyId: String?): Int = resolveCurrency(currencyId)?.decimals() ?: -1

    override fun payable(currencyId: String?): Boolean = resolveCurrency(currencyId)?.payable() ?: false

    override fun getBalance(
        uuid: UUID?,
        currencyId: String?,
    ): BigDecimal? {
        val currency = resolveCurrency(currencyId)
        if (currency == null || uuid == null) {
            return null
        }
        return economy.get(uuid, currency)
    }

    override fun has(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): Boolean {
        val currency = resolveCurrency(currencyId)
        if (currency == null || uuid == null || amount == null) {
            return false
        }
        return economy.has(uuid, currency, amount)
    }

    override fun set(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): Boolean {
        val currency = resolveCurrency(currencyId)
        if (currency == null || uuid == null || amount == null) {
            return false
        }
        return economy.set(uuid, currency, amount)
    }

    override fun deposit(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): Boolean {
        val currency = resolveCurrency(currencyId)
        if (currency == null || uuid == null || amount == null) {
            return false
        }
        return economy.add(uuid, currency, amount)
    }

    override fun withdraw(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): Boolean {
        val currency = resolveCurrency(currencyId)
        if (currency == null || uuid == null || amount == null) {
            return false
        }
        return economy.take(uuid, currency, amount)
    }

    override fun getBank(
        uuid: UUID?,
        currencyId: String?,
    ): BigDecimal? {
        val currency = resolveCurrency(currencyId)
        if (currency == null || uuid == null) {
            return null
        }
        return economy.getBank(uuid, currency)
    }

    override fun hasBank(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): Boolean {
        val currency = resolveCurrency(currencyId)
        if (currency == null || uuid == null || amount == null) {
            return false
        }
        return economy.getBank(uuid, currency) >= currency.normalize(amount)
    }

    override fun setBank(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): Boolean {
        val currency = resolveCurrency(currencyId)
        if (currency == null || uuid == null || amount == null) {
            return false
        }
        return economy.setBank(uuid, currency, amount)
    }

    override fun depositBank(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): Boolean {
        val currency = resolveCurrency(currencyId)
        if (currency == null || uuid == null || amount == null) {
            return false
        }
        return economy.addBank(uuid, currency, amount)
    }

    override fun withdrawBank(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): Boolean {
        val currency = resolveCurrency(currencyId)
        if (currency == null || uuid == null || amount == null) {
            return false
        }
        return economy.takeBank(uuid, currency, amount)
    }

    override fun transfer(
        from: UUID?,
        to: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): Boolean = transferResult(from, to, currencyId, amount).success

    override fun depositResult(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): EconomyResult {
        val currency = resolveCurrency(currencyId)
        if (currency == null || uuid == null || amount == null) {
            return EconomyResult.fail(EconomyStatus.UNKNOWN_CURRENCY)
        }
        return economy.addResult(uuid, currency, amount, bank = false)
    }

    override fun withdrawResult(
        uuid: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): EconomyResult {
        val currency = resolveCurrency(currencyId)
        if (currency == null || uuid == null || amount == null) {
            return EconomyResult.fail(EconomyStatus.UNKNOWN_CURRENCY)
        }
        return economy.takeResult(uuid, currency, amount, bank = false)
    }

    override fun transferResult(
        from: UUID?,
        to: UUID?,
        currencyId: String?,
        amount: BigDecimal?,
    ): EconomyResult {
        val currency = resolveCurrency(currencyId)
        if (currency == null || from == null || to == null || amount == null) {
            return EconomyResult.fail(EconomyStatus.UNKNOWN_CURRENCY)
        }
        return economy.transferResult(from, to, currency, amount)
    }

    override fun resolve(name: String?): UUID? {
        if (name.isNullOrBlank()) {
            return null
        }
        return economy.resolve(name)
    }

    override fun top(
        currencyId: String?,
        page: Int,
        pageSize: Int,
    ): List<PureEconomyAPI.TopEntry> {
        val currency = resolveCurrency(currencyId) ?: return emptyList()
        val size = if (pageSize < 1) 10 else pageSize
        return economy.top(currency, page, size).map {
            PureEconomyAPI.TopEntry(it.uuid, it.name, it.balance)
        }
    }

    override fun topPages(
        currencyId: String?,
        pageSize: Int,
    ): Int {
        val currency = resolveCurrency(currencyId) ?: return 1
        val size = if (pageSize < 1) 10 else pageSize
        return economy.topPages(currency, size)
    }

    private fun resolveCurrency(currencyId: String?): Currency? = economy.currency(currencyId)
}
