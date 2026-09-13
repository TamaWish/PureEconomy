package io.github.tamawish.pureeconomy.economy

import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** In-memory wallet and bank balances for one player. */
class PlayerAccount(
    private val uuid: UUID,
) {
    @Volatile
    private var name: String? = null
    private val balances: MutableMap<String, BigDecimal> = ConcurrentHashMap()
    private val bankBalances: MutableMap<String, BigDecimal> = ConcurrentHashMap()

    @Volatile
    private var dirty: Boolean = false
    private var revision: Long = 0

    /** Returns the account owner id. */
    fun uuid(): UUID = uuid

    /** Returns the last known player name. */
    fun name(): String? = name

    /** Updates the cached player name when it changes. */
    @Synchronized
    fun setName(name: String?) {
        if (name != null && name != this.name) {
            this.name = name
            this.dirty = true
            this.revision++
        }
    }

    /** Returns whether a wallet entry exists for the currency. */
    fun has(currencyId: String): Boolean = balances.containsKey(currencyId)

    /** Returns the wallet balance for a currency. */
    fun get(currencyId: String): BigDecimal = balances.getOrDefault(currencyId, BigDecimal.ZERO)

    /** Sets the wallet balance for a currency and marks the account dirty. */
    @Synchronized
    fun set(
        currencyId: String,
        amount: BigDecimal,
    ) {
        balances[currencyId] = amount
        dirty = true
        revision++
    }

    /** Returns the live wallet map. Callers must not mutate it outside account methods. */
    fun balances(): Map<String, BigDecimal> = balances

    /** Returns the bank balance for a currency. */
    fun getBank(currencyId: String): BigDecimal = bankBalances.getOrDefault(currencyId, BigDecimal.ZERO)

    /** Sets the bank balance for a currency and marks the account dirty. */
    @Synchronized
    fun setBank(
        currencyId: String,
        amount: BigDecimal,
    ) {
        bankBalances[currencyId] = amount
        dirty = true
        revision++
    }

    /** Returns the live bank map. Callers must not mutate it outside account methods. */
    fun bankBalances(): Map<String, BigDecimal> = bankBalances

    /** Returns whether the account has unsaved changes. */
    fun dirty(): Boolean = dirty

    /** Clears the dirty flag without checking the revision. */
    @Synchronized
    fun markClean() {
        dirty = false
    }

    /** Captures an immutable copy of the account for asynchronous persistence. */
    @Synchronized
    fun snapshot(): Snapshot = Snapshot(uuid, name, HashMap(balances), HashMap(bankBalances), revision)

    /** Clears the dirty flag only when no writes happened after [savedRevision]. */
    @Synchronized
    fun markClean(savedRevision: Long) {
        if (revision == savedRevision) {
            dirty = false
        }
    }

    /**
     * Immutable copy of an account used while writing YAML off-thread.
     *
     * @param uuid player unique id
     * @param name cached name; may be `null`
     * @param balances wallet copy
     * @param bankBalances bank copy
     * @param revision revision at snapshot time
     */
    data class Snapshot(
        val uuid: UUID,
        val name: String?,
        val balances: Map<String, BigDecimal>,
        val bankBalances: Map<String, BigDecimal>,
        val revision: Long,
    )
}
