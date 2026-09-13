package io.github.tamawish.pureeconomy.storage

import com.zaxxer.hikari.HikariDataSource
import io.github.tamawish.pureeconomy.economy.PlayerAccount
import io.github.tamawish.pureeconomy.network.EconomySchema
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

/** Hikari-backed persistence for player wallets and banks (SQLite or MySQL). */
class SqlStorage(
    private val dataSource: HikariDataSource,
    private val logger: Logger,
    private val mysql: Boolean,
) : PlayerStorage {
    private val nameIndex: MutableMap<String, UUID> = ConcurrentHashMap()
    private val namesByUuid: MutableMap<UUID, String> = ConcurrentHashMap()
    private val walletIndex: MutableMap<UUID, MutableMap<String, BigDecimal>> = ConcurrentHashMap()
    private val bankIndex: MutableMap<UUID, MutableMap<String, BigDecimal>> = ConcurrentHashMap()

    /** Test hook: last SQL batches executed (uppercase). */
    val executedSql: MutableList<String> = Collections.synchronizedList(ArrayList())

    init {
        dataSource.connection.use { connection ->
            EconomySchema.ensure(connection, mysql, logger)
        }
    }

    override fun load(uuid: UUID): PlayerAccount? {
        try {
            dataSource.connection.use { connection ->
                var found = false
                val account = PlayerAccount(uuid)
                prepare(connection, "SELECT name FROM players WHERE uuid = ?").use { statement ->
                    statement.setString(1, uuid.toString())
                    statement.executeQuery().use { rows ->
                        if (rows.next()) {
                            found = true
                            account.setName(rows.getString("name"))
                        }
                    }
                }
                loadBalances(connection, "wallets", uuid) { id, amount ->
                    found = true
                    account.set(id, amount)
                }
                loadBalances(connection, "banks", uuid) { id, amount ->
                    found = true
                    account.setBank(id, amount)
                }
                if (!found) {
                    return null
                }
                account.markClean()
                remember(account)
                return account
            }
        } catch (exception: SQLException) {
            logger.log(Level.SEVERE, "Could not load account $uuid", exception)
            return null
        }
    }

    override fun hydrate(uuid: UUID): PlayerAccount? = load(uuid)

    private fun loadBalances(
        connection: Connection,
        table: String,
        uuid: UUID,
        consumer: (String, BigDecimal) -> Unit,
    ) {
        prepare(connection, "SELECT currency, amount FROM $table WHERE uuid = ?").use { statement ->
            statement.setString(1, uuid.toString())
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    val currency = rows.getString("currency").lowercase(Locale.ROOT)
                    consumer(
                        currency,
                        EconomySchema.readAmount(rows, 2, logger, "$table.$uuid.$currency"),
                    )
                }
            }
        }
    }

    override fun save(account: PlayerAccount) {
        saveAll(listOf(account))
    }

    override fun saveAll(accounts: Collection<PlayerAccount>) {
        if (accounts.isEmpty()) {
            return
        }
        val snapshots = accounts.associateWith { it.snapshot() }
        try {
            dataSource.connection.use { connection ->
                connection.autoCommit = false
                try {
                    for (snapshot in snapshots.values) {
                        upsertPlayer(connection, snapshot)
                        upsertBalances(connection, "wallets", snapshot.uuid, snapshot.balances)
                        upsertBalances(connection, "banks", snapshot.uuid, snapshot.bankBalances)
                        rememberSnapshot(snapshot)
                    }
                    connection.commit()
                } catch (exception: SQLException) {
                    connection.rollback()
                    throw exception
                } finally {
                    connection.autoCommit = true
                }
            }
            snapshots.forEach { (account, snapshot) -> account.markClean(snapshot.revision) }
        } catch (exception: SQLException) {
            logger.log(Level.SEVERE, "Could not save economy accounts", exception)
        }
    }

    private fun upsertPlayer(
        connection: Connection,
        snapshot: PlayerAccount.Snapshot,
    ) {
        val sql =
            if (mysql) {
                "INSERT INTO players (uuid, name) VALUES (?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name)"
            } else {
                "INSERT INTO players (uuid, name) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET name = excluded.name"
            }
        prepare(connection, sql).use { statement ->
            statement.setString(1, snapshot.uuid.toString())
            statement.setString(2, snapshot.name)
            statement.executeUpdate()
        }
    }

    private fun upsertBalances(
        connection: Connection,
        table: String,
        uuid: UUID,
        amounts: Map<String, BigDecimal>,
    ) {
        if (amounts.isEmpty()) {
            return
        }
        val sql = EconomySchema.upsertSql(mysql, table)
        prepare(connection, sql).use { statement ->
            for ((currency, amount) in amounts) {
                statement.setString(1, uuid.toString())
                statement.setString(2, currency)
                statement.setBigDecimal(3, amount)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    override fun remember(account: PlayerAccount) {
        val name = account.name()
        if (name != null) {
            nameIndex[name.lowercase(Locale.ROOT)] = account.uuid()
            namesByUuid[account.uuid()] = name
        }
        walletIndex[account.uuid()] = ConcurrentHashMap(account.balances())
        bankIndex[account.uuid()] = ConcurrentHashMap(account.bankBalances())
    }

    private fun rememberSnapshot(snapshot: PlayerAccount.Snapshot) {
        if (snapshot.name != null) {
            nameIndex[snapshot.name.lowercase(Locale.ROOT)] = snapshot.uuid
            namesByUuid[snapshot.uuid] = snapshot.name
        }
        walletIndex[snapshot.uuid] = ConcurrentHashMap(snapshot.balances)
        bankIndex[snapshot.uuid] = ConcurrentHashMap(snapshot.bankBalances)
    }

    override fun peekWallet(
        uuid: UUID,
        currencyId: String,
    ): BigDecimal {
        val wallets = walletIndex[uuid] ?: return BigDecimal.ZERO
        return wallets.getOrDefault(currencyId, BigDecimal.ZERO)
    }

    override fun peekBank(
        uuid: UUID,
        currencyId: String,
    ): BigDecimal {
        val banks = bankIndex[uuid] ?: return BigDecimal.ZERO
        return banks.getOrDefault(currencyId, BigDecimal.ZERO)
    }

    override fun peekName(uuid: UUID): String? = namesByUuid[uuid] ?: loadName(uuid)

    override fun uuidByName(name: String): UUID? {
        val cached = nameIndex[name.lowercase(Locale.ROOT)]
        if (cached != null) {
            return cached
        }
        return try {
            dataSource.connection.use { connection ->
                prepare(
                    connection,
                    "SELECT uuid FROM players WHERE LOWER(name) = LOWER(?) LIMIT 1",
                ).use { statement ->
                    statement.setString(1, name)
                    statement.executeQuery().use { rows ->
                        if (!rows.next()) {
                            return@use null
                        }
                        val uuid = UUID.fromString(rows.getString(1))
                        nameIndex[name.lowercase(Locale.ROOT)] = uuid
                        uuid
                    }
                }
            }
        } catch (exception: SQLException) {
            logger.log(Level.SEVERE, "Could not resolve player $name", exception)
            null
        }
    }

    override fun allKnownUuids(): Set<UUID> {
        val ids = LinkedHashSet<UUID>()
        try {
            dataSource.connection.use { connection ->
                prepare(connection, "SELECT uuid FROM players").use { statement ->
                    statement.executeQuery().use { rows ->
                        while (rows.next()) {
                            ids.add(UUID.fromString(rows.getString(1)))
                        }
                    }
                }
            }
        } catch (exception: SQLException) {
            logger.log(Level.SEVERE, "Could not list player ids", exception)
        }
        ids.addAll(walletIndex.keys)
        return Collections.unmodifiableSet(ids)
    }

    override fun top(
        currencyId: String,
        offset: Int,
        limit: Int,
    ): List<BalanceRow> {
        val rows = ArrayList<BalanceRow>()
        try {
            dataSource.connection.use { connection ->
                prepare(
                    connection,
                    """
                    SELECT w.uuid, p.name, w.amount
                    FROM wallets w
                    LEFT JOIN players p ON p.uuid = w.uuid
                    WHERE w.currency = ? AND w.amount > 0
                    ORDER BY w.amount DESC
                    LIMIT ? OFFSET ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, currencyId)
                    statement.setInt(2, if (limit == Int.MAX_VALUE) 500 else maxOf(1, limit))
                    statement.setInt(3, maxOf(0, offset))
                    statement.executeQuery().use { result ->
                        while (result.next()) {
                            val uuid = UUID.fromString(result.getString(1))
                            val name = result.getString(2) ?: uuid.toString()
                            val amount =
                                EconomySchema.readAmount(
                                    result,
                                    3,
                                    logger,
                                    "wallets.$uuid.$currencyId",
                                )
                            rows.add(BalanceRow(uuid, name, amount))
                        }
                    }
                }
            }
        } catch (exception: SQLException) {
            logger.log(Level.SEVERE, "Could not rank wallets for $currencyId", exception)
        }
        return rows
    }

    override fun countPositive(currencyId: String): Int {
        try {
            dataSource.connection.use { connection ->
                prepare(
                    connection,
                    "SELECT COUNT(*) FROM wallets WHERE currency = ? AND amount > 0",
                ).use { statement ->
                    statement.setString(1, currencyId)
                    statement.executeQuery().use { rows ->
                        return if (rows.next()) rows.getInt(1) else 0
                    }
                }
            }
        } catch (exception: SQLException) {
            logger.log(Level.SEVERE, "Could not count wallets for $currencyId", exception)
            return 0
        }
    }

    override fun close() {
        if (!dataSource.isClosed) {
            dataSource.close()
        }
    }

    private fun loadName(uuid: UUID): String? =
        try {
            dataSource.connection.use { connection ->
                prepare(connection, "SELECT name FROM players WHERE uuid = ?").use { statement ->
                    statement.setString(1, uuid.toString())
                    statement.executeQuery().use { rows ->
                        if (!rows.next()) {
                            return@use null
                        }
                        val name = rows.getString(1)
                        if (!name.isNullOrEmpty()) {
                            namesByUuid[uuid] = name
                            nameIndex[name.lowercase(Locale.ROOT)] = uuid
                        }
                        name
                    }
                }
            }
        } catch (exception: SQLException) {
            logger.log(Level.SEVERE, "Could not load name for $uuid", exception)
            null
        }

    private fun prepare(
        connection: Connection,
        sql: String,
    ): PreparedStatement {
        executedSql.add(sql.uppercase(Locale.ROOT))
        return connection.prepareStatement(sql)
    }
}
