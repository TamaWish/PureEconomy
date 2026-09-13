package io.github.tamawish.pureeconomy.network

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkDatabaseTest {
    @Test
    fun `concurrent withdrawals cannot overspend`() {
        val config = config()
        pool().use { pool ->
            NetworkDatabase.fromDataSource(pool, config, Logger.getAnonymousLogger()).use { database ->
                database.publishFingerprint()
                val player = UUID.randomUUID()
                database.remember(player, "Alice")
                val currency = config.currencies.getValue("coins")
                val executor = Executors.newFixedThreadPool(2)
                val results =
                    executor
                        .invokeAll(
                            listOf(
                                Callable { database.add(player, currency, BigDecimal("-60"), false) },
                                Callable { database.add(player, currency, BigDecimal("-60"), false) },
                            ),
                        ).map { it.get() }
                executor.shutdown()
                assertEquals(1, results.count { it.success })
                assertEquals(0, BigDecimal("40.00").compareTo(database.balance(player, currency).wallet))
            }
        }
    }

    @Test
    fun `fingerprintMatches is memory only after refresh`() {
        val config = config()
        pool("fingerprint").use { pool ->
            NetworkDatabase.fromDataSource(pool, config, Logger.getAnonymousLogger()).use { database ->
                database.publishFingerprint()
                assertTrue(database.fingerprintMatches())
                val before = database.statementsPrepared.get()
                repeat(20) { assertTrue(database.fingerprintMatches()) }
                assertEquals(before, database.statementsPrepared.get())
                database.expectFingerprint("other")
                assertFalse(database.fingerprintMatches())
                assertEquals(before, database.statementsPrepared.get())
            }
        }
    }

    @Test
    fun `peekBalance does not insert missing rows`() {
        val config = config()
        pool("peek").use { pool ->
            NetworkDatabase.fromDataSource(pool, config, Logger.getAnonymousLogger()).use { database ->
                val player = UUID.randomUUID()
                val currency = config.currencies.getValue("coins")
                val peeked = database.peekBalance(player, currency)
                assertEquals(0, BigDecimal.ZERO.compareTo(peeked.wallet))
                assertEquals(0, database.countPositive(currency))
            }
        }
    }

    @Test
    fun `top uses limit not a full in-memory dump of zeros`() {
        val config = config()
        pool("top").use { pool ->
            NetworkDatabase.fromDataSource(pool, config, Logger.getAnonymousLogger()).use { database ->
                val currency = config.currencies.getValue("coins")
                repeat(8) { index ->
                    val id = UUID.randomUUID()
                    database.remember(id, "P$index")
                    database.set(id, currency, BigDecimal((index + 1) * 10), false)
                }
                val (page, pages) = database.top(currency, 1, 3)
                assertEquals(3, page.size)
                assertTrue(pages >= 2)
                assertTrue(page[0].amount >= page[1].amount)
            }
        }
    }

    @Test
    fun `transfer distinguishes insufficient funds from recipient maximum`() {
        val config = configWithMaximum("50")
        pool("transfer-status").use { pool ->
            NetworkDatabase.fromDataSource(pool, config, Logger.getAnonymousLogger()).use { database ->
                val currency = config.currencies.getValue("coins")
                val from = UUID.randomUUID()
                val to = UUID.randomUUID()
                database.set(from, currency, BigDecimal("10"), false)
                database.set(to, currency, BigDecimal("45"), false)
                assertEquals(
                    NetworkDatabase.MutationStatus.INSUFFICIENT,
                    database.transfer(from, to, currency, BigDecimal("20")).status,
                )
                assertEquals(
                    NetworkDatabase.MutationStatus.MAX_BALANCE,
                    database.transfer(from, to, currency, BigDecimal("10")).status,
                )
            }
        }
    }

    private fun pool(name: String = "network"): HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:$name;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000"
                maximumPoolSize = 4
            },
        )

    private fun config(): NetworkConfig =
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
                    symbol: '$'
                    decimals: 2
                    starting-balance: 100.00
                    max-balance: -1
                    payable: true
                """.trimIndent().toByteArray(),
            ),
        )

    private fun configWithMaximum(maximum: String): NetworkConfig =
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
                    starting-balance: 0
                    max-balance: $maximum
                    payable: true
                """.trimIndent().toByteArray(),
            ),
        )
}
