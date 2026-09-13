package io.github.tamawish.pureeconomy.network

import java.math.BigDecimal
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger

/** Shared wallets/banks/players schema (DECIMAL amounts, schema version 2). */
object EconomySchema {
    const val VERSION = 2

    fun ensure(
        connection: Connection,
        mysql: Boolean,
        logger: Logger,
    ) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS players (
                  uuid VARCHAR(36) PRIMARY KEY,
                  name VARCHAR(16)
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS wallets (
                  uuid VARCHAR(36) NOT NULL,
                  currency VARCHAR(64) NOT NULL,
                  amount DECIMAL(20,8) NOT NULL,
                  PRIMARY KEY (uuid, currency)
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS banks (
                  uuid VARCHAR(36) NOT NULL,
                  currency VARCHAR(64) NOT NULL,
                  amount DECIMAL(20,8) NOT NULL,
                  PRIMARY KEY (uuid, currency)
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS pureeconomy_meta (
                  meta_key VARCHAR(64) PRIMARY KEY,
                  meta_value VARCHAR(255) NOT NULL
                )
                """.trimIndent(),
            )
        }
        migrateAmountColumn(connection, "wallets", mysql, logger)
        migrateAmountColumn(connection, "banks", mysql, logger)
        createIndex(connection, "wallets_currency_amount", "wallets", mysql)
        createIndex(connection, "banks_currency_amount", "banks", mysql)
        createNameIndex(connection, mysql)
        putMeta(connection, mysql, "schema_version", VERSION.toString())
    }

    fun readAmount(
        rows: ResultSet,
        column: Int,
        logger: Logger,
        path: String,
    ): BigDecimal {
        try {
            val decimal = rows.getBigDecimal(column)
            if (decimal != null) {
                return decimal
            }
        } catch (_: SQLException) {
            // Fall through to string parse for mixed VARCHAR leftovers.
        }
        return parseAmount(rows.getString(column), logger, path)
    }

    fun parseAmount(
        raw: String?,
        logger: Logger,
        path: String,
    ): BigDecimal =
        try {
            BigDecimal(raw!!.trim())
        } catch (_: Exception) {
            logger.log(Level.SEVERE, "Invalid balance at $path: $raw — using 0")
            BigDecimal.ZERO
        }

    fun upsertSql(
        mysql: Boolean,
        table: String,
    ): String =
        if (mysql) {
            "INSERT INTO $table (uuid, currency, amount) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE amount = VALUES(amount)"
        } else {
            "INSERT INTO $table (uuid, currency, amount) VALUES (?, ?, ?) ON CONFLICT(uuid, currency) DO UPDATE SET amount = excluded.amount"
        }

    private fun migrateAmountColumn(
        connection: Connection,
        table: String,
        mysql: Boolean,
        logger: Logger,
    ) {
        if (!isVarcharAmount(connection, table, mysql)) {
            return
        }
        val migrated = "${table}_v2"
        logger.info("Migrating $table amounts from VARCHAR to DECIMAL(20,8)")
        connection.createStatement().use { statement ->
            statement.execute("DROP TABLE IF EXISTS $migrated")
            statement.execute(
                """
                CREATE TABLE $migrated (
                  uuid VARCHAR(36) NOT NULL,
                  currency VARCHAR(64) NOT NULL,
                  amount DECIMAL(20,8) NOT NULL,
                  PRIMARY KEY (uuid, currency)
                )
                """.trimIndent(),
            )
        }
        connection.prepareStatement("SELECT uuid, currency, amount FROM $table").use { statement ->
            statement.executeQuery().use { rows ->
                connection.prepareStatement("INSERT INTO $migrated (uuid, currency, amount) VALUES (?, ?, ?)").use { insert ->
                    while (rows.next()) {
                        val uuid = rows.getString(1)
                        val currency = rows.getString(2)
                        val amount = parseAmount(rows.getString(3), logger, "$table.$uuid.$currency")
                        insert.setString(1, uuid)
                        insert.setString(2, currency)
                        insert.setBigDecimal(3, amount)
                        insert.addBatch()
                    }
                    insert.executeBatch()
                }
            }
        }
        connection.createStatement().use { statement ->
            statement.execute("DROP TABLE $table")
            if (mysql) {
                statement.execute("RENAME TABLE $migrated TO $table")
            } else {
                statement.execute("ALTER TABLE $migrated RENAME TO $table")
            }
        }
    }

    private fun isVarcharAmount(
        connection: Connection,
        table: String,
        mysql: Boolean,
    ): Boolean {
        return try {
            if (mysql) {
                connection.prepareStatement("SHOW COLUMNS FROM $table LIKE 'amount'").use { statement ->
                    statement.executeQuery().use { rows ->
                        if (!rows.next()) {
                            return false
                        }
                        rows.getString("Type")?.lowercase(Locale.ROOT)?.contains("char") == true
                    }
                }
            } else {
                connection.createStatement().use { statement ->
                    statement.executeQuery("PRAGMA table_info($table)").use { rows ->
                        while (rows.next()) {
                            if (rows.getString("name").equals("amount", ignoreCase = true)) {
                                return rows.getString("type")?.lowercase(Locale.ROOT)?.contains("char") == true
                            }
                        }
                    }
                }
                false
            }
        } catch (_: SQLException) {
            false
        }
    }

    private fun createIndex(
        connection: Connection,
        name: String,
        table: String,
        mysql: Boolean,
    ) {
        try {
            connection.createStatement().use { statement ->
                if (mysql) {
                    statement.execute("CREATE INDEX $name ON $table (currency, amount DESC)")
                } else {
                    statement.execute("CREATE INDEX IF NOT EXISTS $name ON $table (currency, amount DESC)")
                }
            }
        } catch (_: SQLException) {
            // Index already exists on MySQL.
        }
    }

    private fun createNameIndex(
        connection: Connection,
        mysql: Boolean,
    ) {
        try {
            connection.createStatement().use { statement ->
                if (mysql) {
                    statement.execute("CREATE INDEX players_name ON players (name)")
                } else {
                    statement.execute("CREATE INDEX IF NOT EXISTS players_name ON players (name)")
                }
            }
        } catch (_: SQLException) {
            // Index already exists on MySQL.
        }
    }

    private fun putMeta(
        connection: Connection,
        mysql: Boolean,
        key: String,
        value: String,
    ) {
        val sql =
            if (mysql) {
                "INSERT INTO pureeconomy_meta(meta_key,meta_value) VALUES(?,?) ON DUPLICATE KEY UPDATE meta_value=VALUES(meta_value)"
            } else {
                "INSERT INTO pureeconomy_meta(meta_key,meta_value) VALUES(?,?) ON CONFLICT(meta_key) DO UPDATE SET meta_value=excluded.meta_value"
            }
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, key)
            statement.setString(2, value)
            statement.executeUpdate()
        }
    }
}
