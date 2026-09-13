package io.github.tamawish.pureeconomy.economy

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.tamawish.pureeconomy.api.EconomyStatus
import io.github.tamawish.pureeconomy.api.event.EconomyTransactionEvent
import io.github.tamawish.pureeconomy.network.NetworkConfig
import io.github.tamawish.pureeconomy.network.NetworkDatabase
import io.github.tamawish.pureeconomy.persistence.AccountRepository
import io.github.tamawish.pureeconomy.persistence.LocalAccountRepository
import io.github.tamawish.pureeconomy.persistence.NetworkAccountRepository
import io.github.tamawish.pureeconomy.storage.YamlStorage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EconomyInvariantTest {
    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var tempDir: File

    @Test
    fun transactionEventIsSynchronous() {
        val event =
            EconomyTransactionEvent(
                UUID.randomUUID(),
                null,
                "coins",
                BigDecimal.ONE,
                false,
                EconomyTransactionEvent.Cause.PAY,
                BigDecimal.ONE,
            )
        assertFalse(event.isAsynchronous)
    }

    @Test
    fun peekBalanceDoesNotTouchRepositoryGet() {
        val peekCalls = AtomicInteger()
        val getCalls = AtomicInteger()
        val repo =
            object : AccountRepository by UnsupportedRepository() {
                override fun peek(
                    uuid: UUID,
                    currency: Currency,
                    bank: Boolean,
                ): BigDecimal {
                    peekCalls.incrementAndGet()
                    return BigDecimal.TEN
                }

                override fun get(
                    uuid: UUID,
                    currency: Currency,
                    bank: Boolean,
                ): BigDecimal {
                    getCalls.incrementAndGet()
                    return BigDecimal.TEN
                }
            }
        val currency = coins()
        assertEquals(0, BigDecimal.TEN.compareTo(repo.peek(UUID.randomUUID(), currency, false)))
        assertEquals(1, peekCalls.get())
        assertEquals(0, getCalls.get())
    }

    @Test
    fun networkPeekNeverHitsJdbcOnCallerAndRefreshesInBackground() {
        val config = networkConfig()
        val pool =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = "jdbc:h2:mem:cachepeek;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000"
                    maximumPoolSize = 4
                },
            )
        NetworkDatabase.fromDataSource(pool, config, Logger.getAnonymousLogger()).use { database ->
            val player = UUID.randomUUID()
            val currency = coins()
            database.publishFingerprint()
            database.remember(player, "Cache")
            val queued = ConcurrentLinkedQueue<Runnable>()
            val repo = NetworkAccountRepository(database, ttlMillis = 5_000, queued::add)
            val before = database.statementsPrepared.get()
            assertEquals(BigDecimal.ZERO.setScale(2), repo.peek(player, currency, false))
            assertEquals(before, database.statementsPrepared.get())
            assertEquals(1, queued.size)
            // Repeated misses are coalesced while the refresh is in flight.
            repo.peek(player, currency, false)
            repo.peek(player, currency, true)
            assertEquals(1, queued.size)
            queued.remove().run()
            val afterFirst = database.statementsPrepared.get()
            assertTrue(afterFirst > before)
            repo.peek(player, currency, false)
            assertEquals(afterFirst, database.statementsPrepared.get())
        }
    }

    @Test
    fun networkEnsureIsQueuedInsteadOfBlockingJoinThread() {
        val config = networkConfig()
        val pool =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = "jdbc:h2:mem:asyncensure;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000"
                    maximumPoolSize = 2
                },
            )
        NetworkDatabase.fromDataSource(pool, config, Logger.getAnonymousLogger()).use { database ->
            val queued = ConcurrentLinkedQueue<Runnable>()
            val repo = NetworkAccountRepository(database, 5_000, queued::add)
            val before = database.statementsPrepared.get()
            repo.ensureAsync(UUID.randomUUID(), "Joiner")
            assertEquals(before, database.statementsPrepared.get())
            assertEquals(1, queued.size)
            queued.remove().run()
            assertTrue(database.statementsPrepared.get() > before)
        }
    }

    @Test
    fun concurrentTransfersBothDirectionsPreserveTotal() {
        val storage = YamlStorage(File(tempDir, "data.yml"), Logger.getLogger("test"))
        val currency = coins()
        val repo =
            LocalAccountRepository(
                storage,
                64,
                30,
                seedStarting = { account ->
                    EconomyService.seedStarting(account, listOf(currency))
                },
            )
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        repo.set(a, currency, BigDecimal("100"), false)
        repo.set(b, currency, BigDecimal("100"), false)
        val executor = Executors.newFixedThreadPool(2)
        executor
            .invokeAll(
                listOf(
                    Callable { repo.transfer(a, b, currency, BigDecimal("25")) },
                    Callable { repo.transfer(b, a, currency, BigDecimal("40")) },
                ),
            ).forEach { it.get() }
        executor.shutdown()
        val total = repo.get(a, currency, false).add(repo.get(b, currency, false))
        assertEquals(0, BigDecimal("200.00").compareTo(total))
        assertTrue(repo.get(a, currency, false) >= BigDecimal.ZERO)
        assertTrue(repo.get(b, currency, false) >= BigDecimal.ZERO)
    }

    @Test
    fun dirtyCheckDoesNotDrainEvictedAccounts() {
        val cache = AccountCache(1, 30) {}
        val id = UUID.randomUUID()
        val account = cache.getOrLoad(id) { PlayerAccount(it) }
        account.set("coins", BigDecimal.TEN)
        cache.invalidate(id)
        cache.cleanUp()

        assertTrue(cache.hasDirty())
        assertEquals(listOf(account), cache.collectDirty())
        assertFalse(cache.hasDirty())
    }

    @Test
    fun transferReportsInsufficientVsMax() {
        val storage = YamlStorage(File(tempDir, "pay.yml"), Logger.getLogger("test"))
        val capped =
            Currency("gems", "Gem", "Gems", "", 0, BigDecimal.ZERO, BigDecimal("50"), true)
        val repo =
            LocalAccountRepository(
                storage,
                64,
                30,
                seedStarting = { account ->
                    EconomyService.seedStarting(account, listOf(capped))
                },
            )
        val from = UUID.randomUUID()
        val to = UUID.randomUUID()
        repo.set(from, capped, BigDecimal("10"), false)
        repo.set(to, capped, BigDecimal("45"), false)
        assertEquals(EconomyStatus.INSUFFICIENT, repo.transfer(from, to, capped, BigDecimal("20")).status)
        repo.set(from, capped, BigDecimal("10"), false)
        assertEquals(EconomyStatus.MAX_BALANCE, repo.transfer(from, to, capped, BigDecimal("10")).status)
    }

    private fun coins(): Currency = Currency("coins", "Coin", "Coins", "$", 2, BigDecimal("100"), BigDecimal("-1"), true)

    private fun networkConfig(): NetworkConfig =
        NetworkConfig.load(
            ByteArrayInputStream(
                """
                storage:
                  mysql: {host: localhost, database: test, username: test, password: test}
                default-currency: coins
                currencies:
                  coins:
                    singular: Coin
                    plural: Coins
                    decimals: 2
                    starting-balance: 100.00
                    max-balance: -1
                    payable: true
                """.trimIndent().toByteArray(),
            ),
        )

    private open class UnsupportedRepository : AccountRepository {
        override fun peek(
            uuid: UUID,
            currency: Currency,
            bank: Boolean,
        ): BigDecimal = error("peek")

        override fun get(
            uuid: UUID,
            currency: Currency,
            bank: Boolean,
        ): BigDecimal = error("get")

        override fun ensure(
            uuid: UUID,
            name: String?,
        ) = error("ensure")

        override fun set(
            uuid: UUID,
            currency: Currency,
            amount: BigDecimal,
            bank: Boolean,
        ) = error("set")

        override fun add(
            uuid: UUID,
            currency: Currency,
            amount: BigDecimal,
            bank: Boolean,
        ) = error("add")

        override fun take(
            uuid: UUID,
            currency: Currency,
            amount: BigDecimal,
            bank: Boolean,
        ) = error("take")

        override fun transfer(
            from: UUID,
            to: UUID,
            currency: Currency,
            amount: BigDecimal,
        ) = error("transfer")

        override fun move(
            uuid: UUID,
            currency: Currency,
            amount: BigDecimal,
            toBank: Boolean,
        ) = error("move")

        override fun top(
            currency: Currency,
            offset: Int,
            limit: Int,
        ) = emptyList<io.github.tamawish.pureeconomy.storage.BalanceRow>()

        override fun countPositive(currencyId: String) = 0

        override fun resolveStored(name: String): UUID? = null

        override fun storedName(uuid: UUID): String? = null

        override fun savePlayer(uuid: UUID) {}

        override fun savePlayerAsync(uuid: UUID) {}

        override fun unloadPlayer(uuid: UUID) {}

        override fun saveDirtyAsync() {}

        override fun saveAll() {}

        override fun close() {}
    }
}
