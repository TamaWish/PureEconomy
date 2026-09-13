package io.github.tamawish.pureeconomy.persistence

import io.github.tamawish.pureeconomy.api.EconomyResult
import io.github.tamawish.pureeconomy.api.EconomyStatus
import io.github.tamawish.pureeconomy.economy.Currency
import io.github.tamawish.pureeconomy.economy.NetworkBalanceCache
import io.github.tamawish.pureeconomy.network.NetworkCurrency
import io.github.tamawish.pureeconomy.network.NetworkDatabase
import io.github.tamawish.pureeconomy.storage.BalanceRow
import java.math.BigDecimal
import java.util.UUID

/** Network MySQL ledger with a short-TTL read cache. */
class NetworkAccountRepository(
    private val network: NetworkDatabase,
    ttlMillis: Long,
    private val runAsync: (Runnable) -> Unit,
) : AccountRepository {
    private val cache = NetworkBalanceCache(network, ttlMillis, runAsync)

    override fun peek(
        uuid: UUID,
        currency: Currency,
        bank: Boolean,
    ): BigDecimal {
        val value = cache.peek(uuid, currency.network())
        return currency.normalize(if (bank) value.bank else value.wallet)
    }

    override fun get(
        uuid: UUID,
        currency: Currency,
        bank: Boolean,
    ): BigDecimal {
        val value = cache.load(uuid, currency.network())
        return currency.normalize(if (bank) value.bank else value.wallet)
    }

    override fun ensure(
        uuid: UUID,
        name: String?,
    ) {
        if (name != null) {
            network.remember(uuid, name)
            cache.invalidatePlayer(uuid)
        }
    }

    override fun ensureAsync(
        uuid: UUID,
        name: String?,
    ) {
        if (name == null) {
            return
        }
        runAsync { ensure(uuid, name) }
    }

    override fun set(
        uuid: UUID,
        currency: Currency,
        amount: BigDecimal,
        bank: Boolean,
    ): EconomyResult {
        if (!network.fingerprintMatches()) {
            return EconomyResult.fail(EconomyStatus.LOCKED)
        }
        val result = network.set(uuid, currency.network(), amount, bank)
        if (result.success) {
            cache.update(uuid, currency.id(), bank, result.newBalance)
        } else {
            cache.invalidate(uuid, currency.id())
        }
        return if (result.success) {
            EconomyResult.ok(result.newBalance)
        } else {
            EconomyResult.fail(result.status.economyStatus(), result.newBalance)
        }
    }

    override fun add(
        uuid: UUID,
        currency: Currency,
        amount: BigDecimal,
        bank: Boolean,
    ): EconomyResult {
        if (!network.fingerprintMatches()) {
            return EconomyResult.fail(EconomyStatus.LOCKED)
        }
        val result = network.add(uuid, currency.network(), currency.normalize(amount), bank)
        if (result.success) {
            cache.update(uuid, currency.id(), bank, result.newBalance)
        } else {
            cache.invalidate(uuid, currency.id())
        }
        return if (result.success) {
            EconomyResult.ok(result.newBalance)
        } else {
            EconomyResult.fail(result.status.economyStatus(), result.newBalance)
        }
    }

    override fun take(
        uuid: UUID,
        currency: Currency,
        amount: BigDecimal,
        bank: Boolean,
    ): EconomyResult {
        if (!network.fingerprintMatches()) {
            return EconomyResult.fail(EconomyStatus.LOCKED)
        }
        val result = network.add(uuid, currency.network(), -currency.normalize(amount), bank)
        if (result.success) {
            cache.update(uuid, currency.id(), bank, result.newBalance)
        } else {
            cache.invalidate(uuid, currency.id())
        }
        return if (result.success) {
            EconomyResult.ok(result.newBalance)
        } else {
            EconomyResult.fail(result.status.economyStatus(), result.newBalance)
        }
    }

    override fun transfer(
        from: UUID,
        to: UUID,
        currency: Currency,
        amount: BigDecimal,
    ): EconomyResult {
        if (!network.fingerprintMatches()) {
            return EconomyResult.fail(EconomyStatus.LOCKED)
        }
        val result = network.transfer(from, to, currency.network(), amount)
        if (result.success) {
            cache.update(from, currency.id(), bank = false, result.newBalance)
            result.otherBalance?.let { cache.update(to, currency.id(), bank = false, it) }
        } else {
            cache.invalidate(from, currency.id())
            cache.invalidate(to, currency.id())
        }
        return if (result.success) {
            EconomyResult.ok(result.newBalance, result.otherBalance)
        } else {
            EconomyResult.fail(result.status.economyStatus(), result.newBalance, result.otherBalance)
        }
    }

    override fun move(
        uuid: UUID,
        currency: Currency,
        amount: BigDecimal,
        toBank: Boolean,
    ): EconomyResult {
        if (!network.fingerprintMatches()) {
            return EconomyResult.fail(EconomyStatus.LOCKED)
        }
        val result = network.move(uuid, currency.network(), amount, toBank)
        if (result.success) {
            // A move changes both sides. Refresh asynchronously; last-known values remain available
            // to PlaceholderAPI while the authoritative pair is fetched.
            cache.invalidate(uuid, currency.id())
            cache.peek(uuid, currency.network())
        } else {
            cache.invalidate(uuid, currency.id())
        }
        return if (result.success) {
            EconomyResult.ok(result.newBalance)
        } else {
            EconomyResult.fail(result.status.economyStatus(), result.newBalance)
        }
    }

    override fun top(
        currency: Currency,
        offset: Int,
        limit: Int,
    ): List<BalanceRow> {
        val pageSize = maxOf(1, limit)
        val page = offset / pageSize + 1
        val (entries, _) = network.top(currency.network(), page, pageSize)
        return entries.map { BalanceRow(it.uuid, it.name, currency.normalize(it.amount)) }
    }

    override fun countPositive(currencyId: String): Int {
        val currency =
            NetworkCurrency(currencyId, currencyId, currencyId, "", 2, BigDecimal.ZERO, null, true)
        return network.countPositive(currency)
    }

    override fun resolveStored(name: String): UUID? = network.resolve(name)

    override fun storedName(uuid: UUID): String? = network.name(uuid)

    override fun fingerprintOk(): Boolean = network.fingerprintMatches()

    override fun savePlayer(uuid: UUID) {}

    override fun savePlayerAsync(uuid: UUID) {}

    override fun unloadPlayer(uuid: UUID) {}

    override fun saveDirtyAsync() {}

    override fun saveAll() {}

    override fun close() {
        network.close()
    }

    private fun Currency.network(): NetworkCurrency =
        NetworkCurrency(
            id(),
            singular(),
            plural(),
            symbol(),
            decimals(),
            starting(),
            max().takeUnless { it.signum() < 0 },
            payable(),
        )

    private fun NetworkDatabase.MutationStatus.economyStatus(): EconomyStatus =
        when (this) {
            NetworkDatabase.MutationStatus.SUCCESS -> EconomyStatus.SUCCESS
            NetworkDatabase.MutationStatus.INSUFFICIENT -> EconomyStatus.INSUFFICIENT
            NetworkDatabase.MutationStatus.MAX_BALANCE -> EconomyStatus.MAX_BALANCE
            NetworkDatabase.MutationStatus.INVALID_AMOUNT,
            NetworkDatabase.MutationStatus.SELF_TRANSFER,
            -> EconomyStatus.INVALID_AMOUNT
            NetworkDatabase.MutationStatus.NOT_PAYABLE -> EconomyStatus.UNKNOWN_CURRENCY
        }
}
