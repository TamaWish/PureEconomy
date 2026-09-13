package io.github.tamawish.pureeconomy.economy

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.tamawish.pureeconomy.persistence.AccountRepository
import io.github.tamawish.pureeconomy.storage.BalanceRow
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.TimeUnit

/** TTL-cached baltop ranking with a live-account overlay for local stores. */
class LeaderboardService(
    private val repository: AccountRepository,
    baltopTtlSeconds: Long,
) {
    private val cache: Cache<String, List<EconomyService.BalanceEntry>> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(baltopTtlSeconds, TimeUnit.SECONDS)
            .build()

    fun ranked(currency: Currency): List<EconomyService.BalanceEntry> =
        cache.get(currency.id()) {
            val byUuid = LinkedHashMap<UUID, EconomyService.BalanceEntry>()
            for (row in repository.top(currency, 0, BALTOP_CAP)) {
                if (row.balance > BigDecimal.ZERO) {
                    byUuid[row.uuid] = fromRow(currency, row)
                }
            }
            for ((uuid, account) in repository.liveAccounts()) {
                val balance: BigDecimal
                val name: String?
                synchronized(account) {
                    balance = currency.normalize(account.get(currency.id()))
                    name = account.name()
                }
                if (balance > BigDecimal.ZERO) {
                    byUuid[uuid] =
                        EconomyService.BalanceEntry(
                            uuid,
                            name ?: repository.storedName(uuid) ?: uuid.toString(),
                            balance,
                        )
                } else {
                    byUuid.remove(uuid)
                }
            }
            val list = ArrayList(byUuid.values)
            list.sortByDescending { it.balance }
            if (list.size > BALTOP_CAP) {
                ArrayList(list.subList(0, BALTOP_CAP))
            } else {
                list
            }
        }!!

    fun invalidate(currency: Currency) {
        cache.invalidate(currency.id())
    }

    fun invalidateAll() {
        cache.invalidateAll()
    }

    private fun fromRow(
        currency: Currency,
        row: BalanceRow,
    ): EconomyService.BalanceEntry = EconomyService.BalanceEntry(row.uuid, row.name, currency.normalize(row.balance))

    companion object {
        const val BALTOP_CAP = 500
    }
}
