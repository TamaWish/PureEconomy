package io.github.tamawish.pureeconomy.persistence

import io.github.tamawish.pureeconomy.api.EconomyResult
import io.github.tamawish.pureeconomy.economy.Currency
import io.github.tamawish.pureeconomy.economy.PlayerAccount
import io.github.tamawish.pureeconomy.storage.BalanceRow
import java.math.BigDecimal
import java.util.UUID

/** Local or network ledger used by [io.github.tamawish.pureeconomy.economy.EconomyService]. */
interface AccountRepository {
    fun peek(
        uuid: UUID,
        currency: Currency,
        bank: Boolean,
    ): BigDecimal

    fun get(
        uuid: UUID,
        currency: Currency,
        bank: Boolean,
    ): BigDecimal

    fun ensure(
        uuid: UUID,
        name: String?,
    )

    /** Ensures an account without blocking the caller when the repository requires I/O. */
    fun ensureAsync(
        uuid: UUID,
        name: String?,
    ) = ensure(uuid, name)

    fun set(
        uuid: UUID,
        currency: Currency,
        amount: BigDecimal,
        bank: Boolean,
    ): EconomyResult

    fun add(
        uuid: UUID,
        currency: Currency,
        amount: BigDecimal,
        bank: Boolean,
    ): EconomyResult

    fun take(
        uuid: UUID,
        currency: Currency,
        amount: BigDecimal,
        bank: Boolean,
    ): EconomyResult

    fun transfer(
        from: UUID,
        to: UUID,
        currency: Currency,
        amount: BigDecimal,
    ): EconomyResult

    fun move(
        uuid: UUID,
        currency: Currency,
        amount: BigDecimal,
        toBank: Boolean,
    ): EconomyResult

    fun top(
        currency: Currency,
        offset: Int,
        limit: Int,
    ): List<BalanceRow>

    fun countPositive(currencyId: String): Int

    fun resolveStored(name: String): UUID?

    fun storedName(uuid: UUID): String?

    fun fingerprintOk(): Boolean = true

    fun cachedAccount(uuid: UUID): PlayerAccount? = null

    fun liveAccounts(): Map<UUID, PlayerAccount> = emptyMap()

    fun savePlayer(uuid: UUID)

    fun savePlayerAsync(uuid: UUID)

    fun unloadPlayer(uuid: UUID)

    fun saveDirtyAsync()

    fun saveAll()

    fun close()
}
