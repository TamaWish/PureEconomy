package io.github.tamawish.pureeconomy.storage

import io.github.tamawish.pureeconomy.economy.PlayerAccount
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.math.BigDecimal
import java.util.UUID
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YamlStorageTest {
    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var tempDir: File

    @Test
    fun saveAndLoadRoundTrip() {
        val file = File(tempDir, "data.yml")
        val storage = YamlStorage(file, Logger.getLogger("test"))
        val uuid = UUID.randomUUID()
        val account = PlayerAccount(uuid)
        account.setName("Steve")
        account.set("coins", BigDecimal("12.50"))
        account.setBank("coins", BigDecimal("3.00"))
        storage.save(account)

        val reloaded = YamlStorage(file, Logger.getLogger("test"))
        val loaded = reloaded.load(uuid)!!
        assertEquals("Steve", loaded.name())
        assertEquals(0, BigDecimal("12.50").compareTo(loaded.get("coins")))
        assertEquals(0, BigDecimal("3.00").compareTo(loaded.getBank("coins")))
        assertEquals(uuid, reloaded.uuidByName("Steve"))
        assertEquals(1, reloaded.top("coins", 0, 10).size)
        val hydrated = reloaded.hydrate(uuid)!!
        assertEquals("Steve", hydrated.name())
        assertEquals(0, BigDecimal("12.50").compareTo(hydrated.get("coins")))
        assertEquals(0, BigDecimal("3.00").compareTo(hydrated.getBank("coins")))
        assertEquals(0, BigDecimal("3.00").compareTo(reloaded.peekBank(uuid, "coins")))
    }
}

class SqlStorageTest {
    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var tempDir: File

    @Test
    fun sqliteSaveLoadBaltopAndYamlImport() {
        val yamlFile = File(tempDir, "data.yml")
        val yaml = YamlStorage(yamlFile, Logger.getLogger("test"))
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val rich = PlayerAccount(first)
        rich.setName("Rich")
        rich.set("coins", BigDecimal("100"))
        val poor = PlayerAccount(second)
        poor.setName("Poor")
        poor.set("coins", BigDecimal("5"))
        yaml.saveAll(listOf(rich, poor))

        val dbFile = File(tempDir, "economy.db")
        val sql = SqlStorage(HikariFactory.sqlite(dbFile, "test-sqlite"), Logger.getLogger("test"), mysql = false)
        YamlToSqlMigrator.migrateIfEmpty(yamlFile, sql, Logger.getLogger("test"))
        try {
            assertEquals(2, sql.allKnownUuids().size)
            val loaded = sql.load(first)!!
            assertEquals("Rich", loaded.name())
            assertEquals(0, BigDecimal("100").compareTo(loaded.get("coins")))
            val top = sql.top("coins", 0, 10)
            assertEquals(first, top[0].uuid)
            assertTrue(top[0].balance > top[1].balance)
            assertEquals(2, sql.countPositive("coins"))

            loaded.set("coins", BigDecimal("250"))
            sql.executedSql.clear()
            sql.save(loaded)
            val sqlDump = sql.executedSql.joinToString("\n")
            assertTrue(sqlDump.contains("ON CONFLICT"))
            assertTrue(!sqlDump.contains("DELETE FROM WALLETS"))
            val again = sql.load(first)!!
            assertEquals(0, BigDecimal("250").compareTo(again.get("coins")))
            sql.executedSql.clear()
            val limited = sql.top("coins", 0, 1)
            assertEquals(1, limited.size)
            assertTrue(sql.executedSql.any { it.contains("LIMIT") && it.contains("ORDER BY") })
        } finally {
            sql.close()
        }
    }
}

class CaffeineFlushTest {
    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var tempDir: File

    @Test
    fun evictionSavesDirtyAccount() {
        val sql = SqlStorage(HikariFactory.sqlite(File(tempDir, "cache.db"), "cache-test"), Logger.getLogger("test"), mysql = false)
        try {
            val first = UUID.randomUUID()
            val cache =
                com.github.benmanes.caffeine.cache.Caffeine
                    .newBuilder()
                    .executor(Runnable::run)
                    .removalListener<UUID, PlayerAccount> { _, account, _ ->
                        if (account != null && account.dirty()) {
                            sql.save(account)
                        }
                    }.build<UUID, PlayerAccount>()
            val account = PlayerAccount(first)
            account.setName("Cached")
            account.set("coins", BigDecimal("42"))
            cache.put(first, account)
            cache.invalidate(first)
            val loaded = sql.load(first)
            assertEquals("Cached", loaded?.name())
            assertEquals(0, BigDecimal("42").compareTo(loaded!!.get("coins")))
        } finally {
            sql.close()
        }
    }
}

class SqlSchemaMigrationTest {
    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var tempDir: File

    @Test
    fun varcharAmountsMigrateToDecimal() {
        val dbFile = File(tempDir, "legacy.db")
        val bootstrap = HikariFactory.sqlite(dbFile, "legacy-boot")
        bootstrap.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE wallets (
                      uuid VARCHAR(36) NOT NULL,
                      currency VARCHAR(64) NOT NULL,
                      amount VARCHAR(64) NOT NULL,
                      PRIMARY KEY (uuid, currency)
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    CREATE TABLE banks (
                      uuid VARCHAR(36) NOT NULL,
                      currency VARCHAR(64) NOT NULL,
                      amount VARCHAR(64) NOT NULL,
                      PRIMARY KEY (uuid, currency)
                    )
                    """.trimIndent(),
                )
                statement.execute("CREATE TABLE players (uuid VARCHAR(36) PRIMARY KEY, name VARCHAR(16))")
            }
            val uuid = UUID.fromString("11111111-1111-1111-1111-111111111111")
            connection.prepareStatement("INSERT INTO players(uuid,name) VALUES(?,?)").use {
                it.setString(1, uuid.toString())
                it.setString(2, "Legacy")
                it.executeUpdate()
            }
            connection.prepareStatement("INSERT INTO wallets(uuid,currency,amount) VALUES(?,?,?)").use {
                it.setString(1, uuid.toString())
                it.setString(2, "coins")
                it.setString(3, "42.50")
                it.executeUpdate()
            }
        }
        bootstrap.close()

        val sql = SqlStorage(HikariFactory.sqlite(dbFile, "legacy-migrated"), Logger.getLogger("test"), mysql = false)
        try {
            val loaded = sql.load(UUID.fromString("11111111-1111-1111-1111-111111111111"))!!
            assertEquals("Legacy", loaded.name())
            assertEquals(0, BigDecimal("42.50").compareTo(loaded.get("coins")))
        } finally {
            sql.close()
        }
    }
}
