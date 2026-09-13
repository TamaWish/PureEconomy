package io.github.tamawish.pureeconomy.storage

import io.github.tamawish.pureeconomy.economy.PlayerAccount
import java.math.BigDecimal
import java.util.UUID

/** Persistence stub used when network MySQL owns the ledger. */
object NoOpPlayerStorage : PlayerStorage {
    override fun load(uuid: UUID): PlayerAccount? = null

    override fun hydrate(uuid: UUID): PlayerAccount? = null

    override fun save(account: PlayerAccount) {}

    override fun saveAll(accounts: Collection<PlayerAccount>) {}

    override fun remember(account: PlayerAccount) {}

    override fun peekWallet(
        uuid: UUID,
        currencyId: String,
    ): BigDecimal = BigDecimal.ZERO

    override fun peekBank(
        uuid: UUID,
        currencyId: String,
    ): BigDecimal = BigDecimal.ZERO

    override fun peekName(uuid: UUID): String? = null

    override fun uuidByName(name: String): UUID? = null

    override fun allKnownUuids(): Set<UUID> = emptySet()

    override fun top(
        currencyId: String,
        offset: Int,
        limit: Int,
    ): List<BalanceRow> = emptyList()

    override fun countPositive(currencyId: String): Int = 0
}
