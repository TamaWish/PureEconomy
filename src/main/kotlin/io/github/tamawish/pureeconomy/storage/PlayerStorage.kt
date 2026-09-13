package io.github.tamawish.pureeconomy.storage

import io.github.tamawish.pureeconomy.economy.PlayerAccount
import java.math.BigDecimal
import java.util.UUID

/** Persistence for player wallets and banks. Implementations must keep peek methods synchronous. */
interface PlayerStorage {
    /** Loads one account from the backing store, or `null` when the player has no row. */
    fun load(uuid: UUID): PlayerAccount?

    /**
     * Builds an account from the hot index (YAML) or a single-UUID SQL load. YAML hydrate is
     * RAM-only; SQL hydrate performs JDBC and must not run on a tick thread.
     */
    fun hydrate(uuid: UUID): PlayerAccount?

    /** Persists a single account. */
    fun save(account: PlayerAccount)

    /** Persists many accounts. */
    fun saveAll(accounts: Collection<PlayerAccount>)

    /** Copies live wallet/bank/name into the in-memory indexes without I/O. */
    fun remember(account: PlayerAccount)

    /** Returns a wallet balance from the in-memory index without constructing an account. */
    fun peekWallet(
        uuid: UUID,
        currencyId: String,
    ): BigDecimal

    /** Returns a bank balance from the in-memory index without constructing an account. */
    fun peekBank(
        uuid: UUID,
        currencyId: String,
    ): BigDecimal

    /** Returns the last stored name without constructing an account. */
    fun peekName(uuid: UUID): String?

    /** Looks up a UUID from the lowercase name index. */
    fun uuidByName(name: String): UUID?

    /** Returns every UUID that has appeared in storage or memory. */
    fun allKnownUuids(): Set<UUID>

    /**
     * Returns a page of positive wallets for [currencyId], richest first.
     *
     * @param offset number of rows to skip
     * @param limit maximum rows to return; `Int.MAX_VALUE` means the full ranking
     */
    fun top(
        currencyId: String,
        offset: Int,
        limit: Int,
    ): List<BalanceRow>

    /** Returns how many wallets have a positive balance for [currencyId]. */
    fun countPositive(currencyId: String): Int

    /** Releases pools or file handles. YAML is a no-op. */
    fun close() {}
}

/** One baltop row from storage (name may be the UUID string). */
data class BalanceRow(
    val uuid: UUID,
    val name: String,
    val balance: BigDecimal,
)
