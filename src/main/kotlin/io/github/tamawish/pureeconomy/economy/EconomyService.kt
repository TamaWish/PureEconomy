package io.github.tamawish.pureeconomy.economy

import io.github.tamawish.pureeconomy.PureEconomy
import io.github.tamawish.pureeconomy.api.EconomyResult
import io.github.tamawish.pureeconomy.api.event.EconomyTransactionEvent
import io.github.tamawish.pureeconomy.persistence.AccountRepository
import io.github.tamawish.pureeconomy.util.Schedulers
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.math.BigDecimal
import java.util.UUID

/** Currency registry, account repository, baltop, and Bukkit events. */
class EconomyService(
    private val plugin: PureEconomy,
    private val repository: AccountRepository,
    private val currencies: CurrencyRegistry,
    baltopTtlSeconds: Long,
    internal var dispatchEvent: (Runnable) -> Unit = { task -> Schedulers.runGlobal(plugin, task) },
) {
    private val leaderboard = LeaderboardService(repository, baltopTtlSeconds)

    /** Reloads currency definitions from `config.yml`. */
    fun loadCurrencies() {
        currencies.load()
        leaderboard.invalidateAll()
    }

    fun hasCurrencies(): Boolean = currencies.hasCurrencies()

    fun currency(id: String?): Currency? = currencies.currency(id)

    fun defaultCurrency(): Currency? = currencies.defaultCurrency()

    fun defaultId(): String = currencies.defaultId

    fun currencyIds(): List<String> = currencies.currencyIds()

    fun currencies(): Map<String, Currency> = currencies.currencies()

    fun account(uuid: UUID): PlayerAccount {
        val local = repository as? io.github.tamawish.pureeconomy.persistence.LocalAccountRepository
        if (local != null) {
            return local.account(uuid)
        }
        return repository.cachedAccount(uuid) ?: PlayerAccount(uuid)
    }

    fun cachedAccount(uuid: UUID): PlayerAccount? = repository.cachedAccount(uuid)

    /**
     * Reads a balance from the live cache, or the last hot-index value when the account is cold.
     * Never hits disk or JDBC — safe for PlaceholderAPI scoreboards.
     */
    fun peekBalance(
        uuid: UUID,
        currency: Currency,
        bank: Boolean,
    ): BigDecimal = repository.peek(uuid, currency, bank)

    fun ensureAccount(
        uuid: UUID,
        name: String?,
    ) {
        repository.ensure(uuid, name)
    }

    fun ensureAccountAsync(
        uuid: UUID,
        name: String?,
    ) {
        repository.ensureAsync(uuid, name)
    }

    fun get(
        uuid: UUID,
        currency: Currency,
    ): BigDecimal = repository.get(uuid, currency, bank = false)

    fun has(
        uuid: UUID,
        currency: Currency,
        amount: BigDecimal,
    ): Boolean = get(uuid, currency) >= currency.normalize(amount)

    fun set(
        uuid: UUID,
        currency: Currency,
        raw: BigDecimal,
    ): Boolean = setResult(uuid, currency, raw, bank = false).success

    fun add(
        uuid: UUID,
        currency: Currency,
        raw: BigDecimal,
    ): Boolean = addResult(uuid, currency, raw, bank = false).success

    fun take(
        uuid: UUID,
        currency: Currency,
        raw: BigDecimal,
    ): Boolean = takeResult(uuid, currency, raw, bank = false).success

    fun getBank(
        uuid: UUID,
        currency: Currency,
    ): BigDecimal = repository.get(uuid, currency, bank = true)

    fun setBank(
        uuid: UUID,
        currency: Currency,
        raw: BigDecimal,
    ): Boolean = setResult(uuid, currency, raw, bank = true).success

    fun addBank(
        uuid: UUID,
        currency: Currency,
        raw: BigDecimal,
    ): Boolean = addResult(uuid, currency, raw, bank = true).success

    fun takeBank(
        uuid: UUID,
        currency: Currency,
        raw: BigDecimal,
    ): Boolean = takeResult(uuid, currency, raw, bank = true).success

    fun resetBank(
        uuid: UUID,
        currency: Currency,
    ) {
        setBank(uuid, currency, BigDecimal.ZERO)
    }

    fun transferToBank(
        uuid: UUID,
        currency: Currency,
        raw: BigDecimal,
    ): Boolean = moveResult(uuid, currency, raw, toBank = true).success

    fun withdrawFromBank(
        uuid: UUID,
        currency: Currency,
        raw: BigDecimal,
    ): Boolean = moveResult(uuid, currency, raw, toBank = false).success

    fun transfer(
        from: UUID,
        to: UUID,
        currency: Currency,
        raw: BigDecimal,
    ): Boolean = transferResult(from, to, currency, raw).success

    fun setResult(
        uuid: UUID,
        currency: Currency,
        raw: BigDecimal,
        bank: Boolean,
    ): EconomyResult {
        val result = repository.set(uuid, currency, raw, bank)
        if (result.success) {
            if (!bank) {
                leaderboard.invalidate(currency)
            }
            fire(
                uuid,
                null,
                currency,
                currency.normalize(raw),
                bank,
                EconomyTransactionEvent.Cause.SET,
                result.from ?: currency.normalize(raw),
            )
        }
        return result
    }

    fun addResult(
        uuid: UUID,
        currency: Currency,
        raw: BigDecimal,
        bank: Boolean,
    ): EconomyResult {
        val result = repository.add(uuid, currency, raw, bank)
        if (result.success) {
            if (!bank) {
                leaderboard.invalidate(currency)
            }
            fire(
                uuid,
                null,
                currency,
                currency.normalize(raw),
                bank,
                EconomyTransactionEvent.Cause.DEPOSIT,
                result.from ?: get(uuid, currency),
            )
        }
        return result
    }

    fun takeResult(
        uuid: UUID,
        currency: Currency,
        raw: BigDecimal,
        bank: Boolean,
    ): EconomyResult {
        val amount = currency.normalize(raw)
        val result = repository.take(uuid, currency, amount, bank)
        if (result.success) {
            if (!bank) {
                leaderboard.invalidate(currency)
            }
            fire(
                uuid,
                null,
                currency,
                amount,
                bank,
                EconomyTransactionEvent.Cause.WITHDRAW,
                result.from ?: amount,
            )
        }
        return result
    }

    fun transferResult(
        from: UUID,
        to: UUID,
        currency: Currency,
        raw: BigDecimal,
    ): EconomyResult {
        val amount = currency.normalize(raw)
        val result = repository.transfer(from, to, currency, amount)
        if (result.success) {
            leaderboard.invalidate(currency)
            fire(
                from,
                to,
                currency,
                amount,
                bank = false,
                EconomyTransactionEvent.Cause.PAY,
                result.from ?: amount,
            )
        }
        return result
    }

    fun moveResult(
        uuid: UUID,
        currency: Currency,
        raw: BigDecimal,
        toBank: Boolean,
    ): EconomyResult {
        val amount = currency.normalize(raw)
        val result = repository.move(uuid, currency, amount, toBank)
        if (result.success) {
            leaderboard.invalidate(currency)
            fire(
                uuid,
                null,
                currency,
                amount,
                toBank,
                EconomyTransactionEvent.Cause.BANK_MOVE,
                result.from ?: amount,
            )
        }
        return result
    }

    fun reset(
        uuid: UUID,
        currency: Currency,
    ) {
        set(uuid, currency, currency.starting())
    }

    fun top(
        currency: Currency,
        page: Int,
        pageSize: Int,
    ): List<BalanceEntry> = topPage(currency, page, pageSize).entries

    fun topPage(
        currency: Currency,
        page: Int,
        pageSize: Int,
    ): TopPage {
        val list = leaderboard.ranked(currency)
        val pages = maxOf(1, Math.ceil(list.size / pageSize.toDouble()).toInt())
        val from = maxOf(0, (page - 1) * pageSize)
        if (from >= list.size) {
            return TopPage(emptyList(), pages)
        }
        val to = minOf(list.size, from + pageSize)
        return TopPage(list.subList(from, to), pages)
    }

    fun topPages(
        currency: Currency,
        pageSize: Int,
    ): Int {
        val count = leaderboard.ranked(currency).size
        return maxOf(1, Math.ceil(count / pageSize.toDouble()).toInt())
    }

    fun savePlayer(uuid: UUID) = repository.savePlayer(uuid)

    fun savePlayerAsync(uuid: UUID) = repository.savePlayerAsync(uuid)

    fun unloadPlayer(uuid: UUID) = repository.unloadPlayer(uuid)

    fun saveDirtyAsync() = repository.saveDirtyAsync()

    fun saveAll() = repository.saveAll()

    fun resolve(
        name: String,
        lookupOffline: Boolean = true,
    ): UUID? {
        val online = Bukkit.getPlayerExact(name)
        if (online != null) {
            ensureAccount(online.uniqueId, online.name)
            return online.uniqueId
        }
        val fromStore = repository.resolveStored(name)
        if (fromStore != null) {
            return fromStore
        }
        if (!lookupOffline) {
            return null
        }
        return try {
            val offline: OfflinePlayer = Bukkit.getOfflinePlayer(name)
            if (offline.hasPlayedBefore() || offline.isOnline) {
                ensureAccount(offline.uniqueId, offline.name ?: name)
                offline.uniqueId
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun nameOf(uuid: UUID): String {
        val stored = repository.storedName(uuid)
        if (stored != null) {
            return stored
        }
        return try {
            Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()
        } catch (_: Throwable) {
            uuid.toString()
        }
    }

    fun fingerprintOk(): Boolean = repository.fingerprintOk()

    private fun fire(
        uuid: UUID,
        other: UUID?,
        currency: Currency,
        amount: BigDecimal,
        bank: Boolean,
        cause: EconomyTransactionEvent.Cause,
        newBalance: BigDecimal,
    ) {
        try {
            if (!plugin.isEnabled) {
                return
            }
            dispatchEvent {
                if (!plugin.isEnabled) {
                    return@dispatchEvent
                }
                Bukkit.getPluginManager().callEvent(
                    EconomyTransactionEvent(uuid, other, currency.id(), amount, bank, cause, newBalance),
                )
            }
        } catch (_: Throwable) {
            // Unit tests and early lifecycle have no plugin manager.
        }
    }

    companion object {
        fun seedStarting(
            account: PlayerAccount,
            currencies: Collection<Currency>,
        ) {
            for (currency in currencies) {
                if (!account.has(currency.id())) {
                    account.set(currency.id(), currency.starting())
                }
            }
        }
    }

    data class BalanceEntry(
        val uuid: UUID,
        val name: String,
        val balance: BigDecimal,
    )

    data class TopPage(
        val entries: List<BalanceEntry>,
        val pages: Int,
    )
}
