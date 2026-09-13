package io.github.tamawish.pureeconomy.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.File

/** Builds Hikari pools for SQLite (single writer) and MySQL. */
object HikariFactory {
    fun sqlite(
        file: File,
        poolName: String,
    ): HikariDataSource {
        file.parentFile?.mkdirs()
        val config = HikariConfig()
        config.poolName = poolName
        config.jdbcUrl = "jdbc:sqlite:${file.absolutePath}"
        config.maximumPoolSize = 1
        config.minimumIdle = 1
        // Fail fast so a rare sync caller cannot stall a tick for 30s behind the writer.
        config.connectionTimeout = 8_000
        config.connectionInitSql =
            "PRAGMA journal_mode=WAL; PRAGMA busy_timeout=5000; PRAGMA foreign_keys=ON; PRAGMA synchronous=NORMAL;"
        return HikariDataSource(config)
    }

    fun mysql(
        settings: StorageSettings,
        poolName: String,
    ): HikariDataSource {
        val config = HikariConfig()
        config.poolName = poolName
        config.jdbcUrl =
            "jdbc:mysql://${settings.mysqlHost}:${settings.mysqlPort}/${settings.mysqlDatabase}" +
            "?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true"
        config.username = settings.mysqlUsername
        config.password = settings.mysqlPassword
        config.maximumPoolSize = settings.poolSize
        config.minimumIdle = minOf(2, settings.poolSize)
        config.connectionTimeout = 30_000
        config.maxLifetime = 1_800_000
        config.keepaliveTime = 300_000
        config.leakDetectionThreshold = 60_000
        return HikariDataSource(config)
    }
}
