package io.github.tamawish.pureeconomy.network

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.math.BigDecimal
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

class NetworkDatabase private constructor(
    private val source: HikariDataSource,
    private val config: NetworkConfig,
    private val logger: Logger,
) : AutoCloseable {
    @Volatile
    private var expectedFingerprint: String = config.fingerprint

    @Volatile
    private var cachedFingerprint: String? = null

    /** Test hook: counts JDBC statements prepared by this engine. */
    val statementsPrepared: AtomicInteger = AtomicInteger()

    data class Balance(val wallet: BigDecimal, val bank: BigDecimal)

    data class TopEntry(val uuid: UUID, val name: String, val amount: BigDecimal)

    enum class MutationStatus {
        SUCCESS,
        INVALID_AMOUNT,
        INSUFFICIENT,
        MAX_BALANCE,
        NOT_PAYABLE,
        SELF_TRANSFER,
    }

    data class MutationResult(
        val status: MutationStatus,
        val newBalance: BigDecimal = BigDecimal.ZERO,
        val otherBalance: BigDecimal? = null,
    ) {
        val success: Boolean get() = status == MutationStatus.SUCCESS

        companion object {
            fun success(
                newBalance: BigDecimal,
                otherBalance: BigDecimal? = null,
            ) = MutationResult(MutationStatus.SUCCESS, newBalance, otherBalance)

            fun failure(
                status: MutationStatus,
                newBalance: BigDecimal = BigDecimal.ZERO,
                otherBalance: BigDecimal? = null,
            ) = MutationResult(status, newBalance, otherBalance)
        }
    }

    init {
        source.connection.use { connection ->
            EconomySchema.ensure(connection, mysql = true, logger)
        }
        refreshFingerprint()
    }

    fun publishFingerprint(): Long =
        transaction { connection ->
            val old = meta(connection, "config_fingerprint")
            val revision =
                (meta(connection, "config_revision")?.toLongOrNull() ?: 0L) +
                    if (old == config.fingerprint) 0 else 1
            putMeta(connection, "schema_version", EconomySchema.VERSION.toString())
            putMeta(connection, "config_fingerprint", config.fingerprint)
            putMeta(connection, "config_revision", revision.toString())
            cachedFingerprint = config.fingerprint
            revision
        }

    /** Memory-only compare against the last fingerprint loaded from MySQL. */
    fun fingerprintMatches(): Boolean = cachedFingerprint == expectedFingerprint

    fun expectFingerprint(fingerprint: String) {
        expectedFingerprint = fingerprint
    }

    /** Reloads `config_fingerprint` from MySQL into memory. */
    fun refreshFingerprint(): Boolean {
        source.connection.use { connection ->
            cachedFingerprint = meta(connection, "config_fingerprint")
        }
        return fingerprintMatches()
    }

    fun remember(
        uuid: UUID,
        name: String,
    ) = transaction { connection ->
        connection
            .prepareStatement(
                "INSERT INTO players(uuid,name) VALUES(?,?) ON DUPLICATE KEY UPDATE name=VALUES(name)",
            ).use {
                count(it)
                it.setString(1, uuid.toString())
                it.setString(2, name)
                timeout(it)
                it.executeUpdate()
            }
        config.currencies.values.forEach { currency -> ensureRows(connection, uuid, currency) }
    }

    fun resolve(name: String): UUID? =
        source.connection.use { connection ->
            connection.prepareStatement("SELECT uuid FROM players WHERE LOWER(name)=LOWER(?) LIMIT 1").use {
                count(it)
                it.setString(1, name)
                timeout(it)
                it.executeQuery().use { rows ->
                    if (rows.next()) UUID.fromString(rows.getString(1)) else null
                }
            }
        }

    fun name(uuid: UUID): String =
        source.connection.use { connection ->
            connection.prepareStatement("SELECT name FROM players WHERE uuid=?").use {
                count(it)
                it.setString(1, uuid.toString())
                timeout(it)
                it.executeQuery().use { rows ->
                    if (rows.next()) rows.getString(1) ?: uuid.toString() else uuid.toString()
                }
            }
        }

    /** SELECT-only balance. Missing rows are zero; this never inserts. */
    fun peekBalance(
        uuid: UUID,
        currency: NetworkCurrency,
    ): Balance =
        source.connection.use { connection ->
            Balance(
                read(connection, "wallets", uuid, currency.id, false),
                read(connection, "banks", uuid, currency.id, false),
            )
        }

    fun balance(
        uuid: UUID,
        currency: NetworkCurrency,
    ): Balance =
        transaction { connection ->
            ensureRows(connection, uuid, currency)
            Balance(
                read(connection, "wallets", uuid, currency.id, false),
                read(connection, "banks", uuid, currency.id, false),
            )
        }

    fun set(
        uuid: UUID,
        currency: NetworkCurrency,
        amount: BigDecimal,
        bank: Boolean,
    ): MutationResult =
        transaction { connection ->
            val normalized = currency.normalize(amount).max(BigDecimal.ZERO)
            if (currency.maximum != null && normalized > currency.maximum) {
                return@transaction MutationResult.failure(MutationStatus.MAX_BALANCE)
            }
            ensureRows(connection, uuid, currency)
            write(connection, table(bank), uuid, currency.id, normalized)
            MutationResult.success(normalized)
        }

    fun add(
        uuid: UUID,
        currency: NetworkCurrency,
        delta: BigDecimal,
        bank: Boolean,
    ): MutationResult =
        transaction { connection ->
            ensureRows(connection, uuid, currency)
            val current = read(connection, table(bank), uuid, currency.id, true)
            val next = currency.normalize(current + delta)
            if (next < BigDecimal.ZERO) {
                return@transaction MutationResult.failure(MutationStatus.INSUFFICIENT, current)
            }
            if (currency.maximum?.let { next > it } == true) {
                return@transaction MutationResult.failure(MutationStatus.MAX_BALANCE, current)
            }
            write(connection, table(bank), uuid, currency.id, next)
            MutationResult.success(next)
        }

    fun move(
        uuid: UUID,
        currency: NetworkCurrency,
        amount: BigDecimal,
        toBank: Boolean,
    ): MutationResult =
        transaction { connection ->
            val value = currency.normalize(amount)
            if (value <= BigDecimal.ZERO) {
                return@transaction MutationResult.failure(MutationStatus.INVALID_AMOUNT)
            }
            ensureRows(connection, uuid, currency)
            val wallet = read(connection, "wallets", uuid, currency.id, true)
            val bank = read(connection, "banks", uuid, currency.id, true)
            val from = if (toBank) wallet else bank
            val destination = if (toBank) bank else wallet
            val next = destination + value
            if (from < value) {
                return@transaction MutationResult.failure(MutationStatus.INSUFFICIENT, destination)
            }
            if (currency.maximum?.let { next > it } == true) {
                return@transaction MutationResult.failure(MutationStatus.MAX_BALANCE, destination)
            }
            write(connection, "wallets", uuid, currency.id, if (toBank) wallet - value else next)
            write(connection, "banks", uuid, currency.id, if (toBank) next else bank - value)
            MutationResult.success(next)
        }

    fun transfer(
        from: UUID,
        to: UUID,
        currency: NetworkCurrency,
        amount: BigDecimal,
    ): MutationResult =
        transaction { connection ->
            val value = currency.normalize(amount)
            if (from == to) {
                return@transaction MutationResult.failure(MutationStatus.SELF_TRANSFER)
            }
            if (value <= BigDecimal.ZERO) {
                return@transaction MutationResult.failure(MutationStatus.INVALID_AMOUNT)
            }
            if (!currency.payable) {
                return@transaction MutationResult.failure(MutationStatus.NOT_PAYABLE)
            }
            val ordered = listOf(from, to).sortedBy(UUID::toString)
            ordered.forEach { ensureRows(connection, it, currency) }
            val balances = ordered.associateWith { read(connection, "wallets", it, currency.id, true) }
            val fromValue = balances.getValue(from)
            val toValue = balances.getValue(to) + value
            if (fromValue < value) {
                return@transaction MutationResult.failure(MutationStatus.INSUFFICIENT, fromValue, toValue - value)
            }
            if (currency.maximum?.let { toValue > it } == true) {
                return@transaction MutationResult.failure(MutationStatus.MAX_BALANCE, fromValue, toValue - value)
            }
            val fromNext = fromValue - value
            write(connection, "wallets", from, currency.id, fromNext)
            write(connection, "wallets", to, currency.id, toValue)
            MutationResult.success(fromNext, toValue)
        }

    fun countPositive(currency: NetworkCurrency): Int =
        source.connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM wallets WHERE currency=? AND amount>0").use {
                count(it)
                it.setString(1, currency.id)
                timeout(it)
                it.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else 0 }
            }
        }

    fun top(
        currency: NetworkCurrency,
        page: Int,
        pageSize: Int,
    ): Pair<List<TopEntry>, Int> =
        source.connection.use { connection ->
            val size = maxOf(1, pageSize)
            val offset = (page - 1).coerceAtLeast(0) * size
            var total = 0
            connection.prepareStatement("SELECT COUNT(*) FROM wallets WHERE currency=? AND amount>0").use {
                count(it)
                it.setString(1, currency.id)
                timeout(it)
                it.executeQuery().use { rows ->
                    if (rows.next()) {
                        total = rows.getInt(1)
                    }
                }
            }
            val pages = maxOf(1, (total + size - 1) / size)
            val entries = mutableListOf<TopEntry>()
            connection
                .prepareStatement(
                    """
                    SELECT w.uuid, p.name, w.amount
                    FROM wallets w
                    LEFT JOIN players p ON p.uuid = w.uuid
                    WHERE w.currency=? AND w.amount>0
                    ORDER BY w.amount DESC
                    LIMIT ? OFFSET ?
                    """.trimIndent(),
                ).use {
                    count(it)
                    it.setString(1, currency.id)
                    it.setInt(2, size)
                    it.setInt(3, offset)
                    timeout(it)
                    it.executeQuery().use { rows ->
                        while (rows.next()) {
                            val uuid = UUID.fromString(rows.getString(1))
                            val name = rows.getString(2) ?: uuid.toString()
                            val amount = EconomySchema.readAmount(rows, 3, logger, "wallets.$uuid")
                            entries += TopEntry(uuid, name, amount)
                        }
                    }
                }
            entries to pages
        }

    private fun ensureRows(
        connection: Connection,
        uuid: UUID,
        currency: NetworkCurrency,
    ) {
        connection.prepareStatement("INSERT IGNORE INTO players(uuid,name) VALUES(?,NULL)").use {
            count(it)
            it.setString(1, uuid.toString())
            timeout(it)
            it.executeUpdate()
        }
        ensure(connection, "wallets", uuid, currency.id, currency.starting)
        ensure(connection, "banks", uuid, currency.id, BigDecimal.ZERO)
    }

    private fun ensure(
        connection: Connection,
        table: String,
        uuid: UUID,
        currency: String,
        initial: BigDecimal,
    ) {
        connection.prepareStatement("INSERT IGNORE INTO $table(uuid,currency,amount) VALUES(?,?,?)").use {
            count(it)
            it.setString(1, uuid.toString())
            it.setString(2, currency)
            it.setBigDecimal(3, initial)
            timeout(it)
            it.executeUpdate()
        }
    }

    private fun read(
        connection: Connection,
        table: String,
        uuid: UUID,
        currency: String,
        lock: Boolean,
    ): BigDecimal {
        val sql = "SELECT amount FROM $table WHERE uuid=? AND currency=?${if (lock) " FOR UPDATE" else ""}"
        return connection.prepareStatement(sql).use {
            count(it)
            it.setString(1, uuid.toString())
            it.setString(2, currency)
            timeout(it)
            it.executeQuery().use { rows ->
                if (rows.next()) {
                    EconomySchema.readAmount(rows, 1, logger, "$table.$uuid.$currency")
                } else {
                    BigDecimal.ZERO
                }
            }
        }
    }

    private fun write(
        connection: Connection,
        table: String,
        uuid: UUID,
        currency: String,
        amount: BigDecimal,
    ) {
        connection.prepareStatement(EconomySchema.upsertSql(mysql = true, table)).use {
            count(it)
            it.setString(1, uuid.toString())
            it.setString(2, currency)
            it.setBigDecimal(3, amount)
            timeout(it)
            it.executeUpdate()
        }
    }

    private fun table(bank: Boolean) = if (bank) "banks" else "wallets"

    private fun timeout(statement: java.sql.Statement) {
        statement.queryTimeout = config.mysql.queryTimeoutSeconds
    }

    private fun count(statement: java.sql.Statement) {
        statementsPrepared.incrementAndGet()
    }

    private fun meta(
        connection: Connection,
        key: String,
    ): String? =
        connection.prepareStatement("SELECT meta_value FROM pureeconomy_meta WHERE meta_key=?").use {
            count(it)
            it.setString(1, key)
            timeout(it)
            it.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }

    private fun putMeta(
        connection: Connection,
        key: String,
        value: String,
    ) {
        connection
            .prepareStatement(
                "INSERT INTO pureeconomy_meta(meta_key,meta_value) VALUES(?,?) ON DUPLICATE KEY UPDATE meta_value=VALUES(meta_value)",
            ).use {
                count(it)
                it.setString(1, key)
                it.setString(2, value)
                timeout(it)
                it.executeUpdate()
            }
    }

    private fun <T> transaction(action: (Connection) -> T): T {
        var attempt = 0
        while (true) {
            try {
                return source.connection.use { connection ->
                    connection.autoCommit = false
                    try {
                        val result = action(connection)
                        connection.commit()
                        result
                    } catch (failure: Throwable) {
                        connection.rollback()
                        throw failure
                    }
                }
            } catch (failure: SQLException) {
                if (++attempt > config.mysql.deadlockRetries || failure.sqlState !in setOf("40001", "41000")) {
                    throw failure
                }
                logger.warning(
                    "Retrying economy transaction after SQL conflict ($attempt/${config.mysql.deadlockRetries})",
                )
            }
        }
    }

    override fun close() = source.close()

    companion object {
        fun connect(
            config: NetworkConfig,
            logger: Logger,
        ): NetworkDatabase {
            Class.forName("com.mysql.cj.jdbc.Driver")
            val hikari =
                HikariConfig().apply {
                    jdbcUrl =
                        "jdbc:mysql://${config.mysql.host}:${config.mysql.port}/${config.mysql.database}" +
                        "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                    username = config.mysql.username
                    password = config.mysql.password
                    maximumPoolSize = config.mysql.poolSize
                    minimumIdle = minOf(2, config.mysql.poolSize)
                    connectionTimeout = config.mysql.connectionTimeoutMs
                    maxLifetime = 1_800_000
                    keepaliveTime = 300_000
                    leakDetectionThreshold = 60_000
                    poolName = "pureeconomy-network"
                }
            return NetworkDatabase(HikariDataSource(hikari), config, logger)
        }

        /** Creates an engine around an existing pool, primarily for integration tests. */
        fun fromDataSource(
            source: HikariDataSource,
            config: NetworkConfig,
            logger: Logger,
        ): NetworkDatabase = NetworkDatabase(source, config, logger)
    }
}
