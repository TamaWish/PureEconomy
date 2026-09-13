package io.github.tamawish.pureeconomy.economy

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.tamawish.pureeconomy.network.NetworkCurrency
import io.github.tamawish.pureeconomy.network.NetworkDatabase
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Short-TTL Caffeine cache in front of [NetworkDatabase] reads. */
class NetworkBalanceCache(
    private val network: NetworkDatabase,
    ttlMillis: Long,
    private val runAsync: (Runnable) -> Unit,
) {
    private data class Key(
        val uuid: UUID,
        val currencyId: String,
    )

    private val fresh: Cache<Key, NetworkDatabase.Balance> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(ttlMillis, TimeUnit.MILLISECONDS)
            .build()
    private val lastKnown: Cache<Key, NetworkDatabase.Balance> =
        Caffeine
            .newBuilder()
            .maximumSize(10_000)
            .build()
    private val refreshing = ConcurrentHashMap.newKeySet<Key>()

    /** Memory-only read. A miss schedules one background refresh and returns the last known value. */
    fun peek(
        uuid: UUID,
        currency: NetworkCurrency,
    ): NetworkDatabase.Balance {
        val key = Key(uuid, currency.id)
        fresh.getIfPresent(key)?.let { return it }
        refreshAsync(key, uuid, currency)
        return lastKnown.getIfPresent(key) ?: ZERO
    }

    /** Authoritative read for an already asynchronous I/O path. */
    fun load(
        uuid: UUID,
        currency: NetworkCurrency,
    ): NetworkDatabase.Balance {
        val balance = network.peekBalance(uuid, currency)
        put(uuid, currency.id, balance)
        return balance
    }

    fun put(
        uuid: UUID,
        currencyId: String,
        balance: NetworkDatabase.Balance,
    ) {
        val key = Key(uuid, currencyId)
        fresh.put(key, balance)
        lastKnown.put(key, balance)
    }

    fun update(
        uuid: UUID,
        currencyId: String,
        bank: Boolean,
        amount: java.math.BigDecimal,
    ) {
        val key = Key(uuid, currencyId)
        val previous = lastKnown.getIfPresent(key) ?: ZERO
        val updated =
            if (bank) {
                NetworkDatabase.Balance(previous.wallet, amount)
            } else {
                NetworkDatabase.Balance(amount, previous.bank)
            }
        put(uuid, currencyId, updated)
    }

    fun invalidate(
        uuid: UUID,
        currencyId: String,
    ) {
        fresh.invalidate(Key(uuid, currencyId))
    }

    fun invalidatePlayer(uuid: UUID) {
        fresh.asMap().keys.removeIf { it.uuid == uuid }
    }

    fun invalidateAll() {
        fresh.invalidateAll()
    }

    private fun refreshAsync(
        key: Key,
        uuid: UUID,
        currency: NetworkCurrency,
    ) {
        if (!refreshing.add(key)) {
            return
        }
        runAsync {
            try {
                load(uuid, currency)
            } finally {
                refreshing.remove(key)
            }
        }
    }

    companion object {
        private val ZERO = NetworkDatabase.Balance(java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO)
    }
}
