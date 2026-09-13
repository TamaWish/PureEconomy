package io.github.tamawish.pureeconomy.storage

import java.math.BigDecimal
import java.util.UUID

/** Shared ranking helpers over in-memory wallet/name indexes. */
internal object StorageIndexes {
    fun top(
        walletIndex: Map<UUID, Map<String, BigDecimal>>,
        namesByUuid: Map<UUID, String>,
        currencyId: String,
        offset: Int,
        limit: Int,
    ): List<BalanceRow> {
        val ranked = ArrayList<BalanceRow>()
        for ((uuid, wallets) in walletIndex) {
            val balance = wallets[currencyId] ?: continue
            if (balance <= BigDecimal.ZERO) {
                continue
            }
            ranked.add(BalanceRow(uuid, namesByUuid[uuid] ?: uuid.toString(), balance))
        }
        ranked.sortByDescending { it.balance }
        if (offset >= ranked.size) {
            return emptyList()
        }
        val to = minOf(ranked.size, offset + limit)
        return ranked.subList(maxOf(0, offset), to)
    }

    fun countPositive(
        walletIndex: Map<UUID, Map<String, BigDecimal>>,
        currencyId: String,
    ): Int {
        var count = 0
        for (wallets in walletIndex.values) {
            val balance = wallets[currencyId] ?: continue
            if (balance > BigDecimal.ZERO) {
                count++
            }
        }
        return count
    }
}
