package io.github.tamawish.pureeconomy.persistence

import io.github.tamawish.pureeconomy.api.EconomyResult
import io.github.tamawish.pureeconomy.api.EconomyStatus
import io.github.tamawish.pureeconomy.economy.AccountCache
import io.github.tamawish.pureeconomy.economy.Currency
import io.github.tamawish.pureeconomy.economy.PlayerAccount
import io.github.tamawish.pureeconomy.storage.BalanceRow
import io.github.tamawish.pureeconomy.storage.PlayerStorage
import org.bukkit.Bukkit
import java.math.BigDecimal
import java.util.UUID

/** YAML or SQL ledger with a Caffeine hot cache. */
class LocalAccountRepository(
    private val storage: PlayerStorage,
    maxAccounts: Long,
    expireMinutes: Long,
    private val seedStarting: (PlayerAccount) -> Unit,
    private val runAsync: (Runnable) -> Unit = { it.run() },
) : AccountRepository {
    private val cache =
        AccountCache(maxAccounts, expireMinutes) {
            saveDirtyAsync()
        }

    fun account(uuid: UUID): PlayerAccount =
        cache.getOrLoad(uuid) { id ->
            val loaded = storage.hydrate(id) ?: PlayerAccount(id)
            seedStarting(loaded)
            loaded
        }

    override fun peek(
        uuid: UUID,
        currency: Currency,
        bank: Boolean,
    ): BigDecimal {
        val account = cache.getIfPresent(uuid)
        if (account != null) {
            synchronized(account) {
                return currency.normalize(
                    if (bank) account.getBank(currency.id()) else account.get(currency.id()),
                )
            }
        }
        val raw = if (bank) storage.peekBank(uuid, currency.id()) else storage.peekWallet(uuid, currency.id())
        return currency.normalize(raw)
    }

    override fun get(
        uuid: UUID,
        currency: Currency,
        bank: Boolean,
    ): BigDecimal {
        val account = account(uuid)
        synchronized(account) {
            return currency.normalize(
                if (bank) account.getBank(currency.id()) else account.get(currency.id()),
            )
        }
    }

    override fun ensure(
        uuid: UUID,
        name: String?,
    ) {
        val account = account(uuid)
        synchronized(account) {
            if (name != null) {
                account.setName(name)
            }
            seedStarting(account)
        }
        storage.remember(account)
    }

    override fun set(
        uuid: UUID,
        currency: Currency,
        amount: BigDecimal,
        bank: Boolean,
    ): EconomyResult {
        var normalized = currency.normalize(amount)
        if (normalized < BigDecimal.ZERO) {
            normalized = BigDecimal.ZERO
        }
        if (currency.exceedsMax(normalized)) {
            return EconomyResult.fail(EconomyStatus.MAX_BALANCE, get(uuid, currency, bank))
        }
        val account = account(uuid)
        synchronized(account) {
            if (bank) {
                account.setBank(currency.id(), normalized)
            } else {
                account.set(currency.id(), normalized)
            }
        }
        storage.remember(account)
        return EconomyResult.ok(normalized)
    }

    override fun add(
        uuid: UUID,
        currency: Currency,
        amount: BigDecimal,
        bank: Boolean,
    ): EconomyResult {
        val account = account(uuid)
        val next: BigDecimal
        synchronized(account) {
            val current =
                currency.normalize(if (bank) account.getBank(currency.id()) else account.get(currency.id()))
            var computed = current.add(currency.normalize(amount))
            if (computed < BigDecimal.ZERO) {
                computed = BigDecimal.ZERO
            }
            if (currency.exceedsMax(computed)) {
                return EconomyResult.fail(EconomyStatus.MAX_BALANCE, current)
            }
            if (bank) {
                account.setBank(currency.id(), computed)
            } else {
                account.set(currency.id(), computed)
            }
            next = computed
        }
        storage.remember(account)
        return EconomyResult.ok(next)
    }

    override fun take(
        uuid: UUID,
        currency: Currency,
        amount: BigDecimal,
        bank: Boolean,
    ): EconomyResult {
        val normalized = currency.normalize(amount)
        if (normalized < BigDecimal.ZERO) {
            return EconomyResult.fail(EconomyStatus.INVALID_AMOUNT)
        }
        val account = account(uuid)
        val next: BigDecimal
        synchronized(account) {
            val current =
                currency.normalize(if (bank) account.getBank(currency.id()) else account.get(currency.id()))
            if (current < normalized) {
                return EconomyResult.fail(EconomyStatus.INSUFFICIENT, current)
            }
            val computed = current.subtract(normalized)
            if (bank) {
                account.setBank(currency.id(), computed)
            } else {
                account.set(currency.id(), computed)
            }
            next = computed
        }
        storage.remember(account)
        return EconomyResult.ok(next)
    }

    override fun transfer(
        from: UUID,
        to: UUID,
        currency: Currency,
        amount: BigDecimal,
    ): EconomyResult {
        val normalized = currency.normalize(amount)
        if (normalized <= BigDecimal.ZERO) {
            return EconomyResult.fail(EconomyStatus.INVALID_AMOUNT)
        }
        if (!currency.payable()) {
            return EconomyResult.fail(EconomyStatus.UNKNOWN_CURRENCY)
        }
        var fromNext = BigDecimal.ZERO
        var toNext = BigDecimal.ZERO
        var status = EconomyStatus.INSUFFICIENT
        val ok =
            withAccounts(from, to) {
                val fromAccount = account(from)
                val toAccount = account(to)
                val fromBalance = currency.normalize(fromAccount.get(currency.id()))
                if (fromBalance < normalized) {
                    fromNext = fromBalance
                    status = EconomyStatus.INSUFFICIENT
                    return@withAccounts false
                }
                val targetCurrent = currency.normalize(toAccount.get(currency.id()))
                val targetNext = targetCurrent.add(normalized)
                if (currency.exceedsMax(targetNext)) {
                    fromNext = fromBalance
                    toNext = targetCurrent
                    status = EconomyStatus.MAX_BALANCE
                    return@withAccounts false
                }
                fromNext = fromBalance.subtract(normalized)
                toNext = targetNext
                fromAccount.set(currency.id(), fromNext)
                toAccount.set(currency.id(), toNext)
                true
            }
        if (!ok) {
            return EconomyResult.fail(status, fromNext, toNext)
        }
        storage.remember(account(from))
        storage.remember(account(to))
        return EconomyResult.ok(fromNext, toNext)
    }

    override fun move(
        uuid: UUID,
        currency: Currency,
        amount: BigDecimal,
        toBank: Boolean,
    ): EconomyResult {
        val normalized = currency.normalize(amount)
        if (normalized <= BigDecimal.ZERO) {
            return EconomyResult.fail(EconomyStatus.INVALID_AMOUNT)
        }
        val account = account(uuid)
        val destination: BigDecimal
        synchronized(account) {
            val wallet = currency.normalize(account.get(currency.id()))
            val bank = currency.normalize(account.getBank(currency.id()))
            val from = if (toBank) wallet else bank
            val dest = if (toBank) bank else wallet
            val next = dest.add(normalized)
            if (from < normalized) {
                return EconomyResult.fail(EconomyStatus.INSUFFICIENT, dest)
            }
            if (currency.exceedsMax(next)) {
                return EconomyResult.fail(EconomyStatus.MAX_BALANCE, dest)
            }
            if (toBank) {
                account.set(currency.id(), wallet.subtract(normalized))
                account.setBank(currency.id(), next)
            } else {
                account.setBank(currency.id(), bank.subtract(normalized))
                account.set(currency.id(), next)
            }
            destination = next
        }
        storage.remember(account)
        return EconomyResult.ok(destination)
    }

    override fun top(
        currency: Currency,
        offset: Int,
        limit: Int,
    ): List<BalanceRow> = storage.top(currency.id(), offset, limit)

    override fun countPositive(currencyId: String): Int = storage.countPositive(currencyId)

    override fun resolveStored(name: String): UUID? = storage.uuidByName(name)

    override fun storedName(uuid: UUID): String? {
        val account = cache.getIfPresent(uuid)
        if (account != null && account.name() != null) {
            return account.name()
        }
        return storage.peekName(uuid)
    }

    override fun cachedAccount(uuid: UUID): PlayerAccount? = cache.getIfPresent(uuid)

    override fun liveAccounts(): Map<UUID, PlayerAccount> = HashMap(cache.asMap())

    override fun savePlayer(uuid: UUID) {
        val account = cache.getIfPresent(uuid) ?: return
        storage.save(account)
    }

    override fun savePlayerAsync(uuid: UUID) {
        val account = cache.getIfPresent(uuid) ?: return
        runAsync { storage.save(account) }
    }

    override fun unloadPlayer(uuid: UUID) {
        val account = cache.getIfPresent(uuid) ?: return
        runAsync {
            storage.save(account)
            val online =
                try {
                    Bukkit.getPlayer(uuid) != null
                } catch (_: Throwable) {
                    false
                }
            if (!online) {
                cache.invalidate(uuid)
            }
        }
    }

    override fun saveDirtyAsync() {
        if (!cache.tryQueueDirtySave()) {
            return
        }
        runAsync {
            try {
                val dirty = cache.collectDirty()
                if (dirty.isNotEmpty()) {
                    storage.saveAll(dirty)
                }
            } finally {
                cache.finishDirtySave()
                if (cache.hasDirty()) {
                    saveDirtyAsync()
                }
            }
        }
    }

    override fun saveAll() {
        val all = cache.allCachedAndEvicted()
        if (all.isNotEmpty()) {
            storage.saveAll(all)
        }
    }

    override fun close() {
        storage.close()
    }

    private fun withAccounts(
        first: UUID,
        second: UUID,
        action: () -> Boolean,
    ): Boolean {
        val firstAccount = account(first)
        if (first == second) {
            synchronized(firstAccount) {
                return action()
            }
        }
        val secondAccount = account(second)
        return if (first < second) {
            synchronized(firstAccount) {
                synchronized(secondAccount) {
                    action()
                }
            }
        } else {
            synchronized(secondAccount) {
                synchronized(firstAccount) {
                    action()
                }
            }
        }
    }
}
