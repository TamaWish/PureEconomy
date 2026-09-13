package io.github.tamawish.pureeconomy.storage

import io.github.tamawish.pureeconomy.economy.PlayerAccount
import java.io.File
import java.util.logging.Logger

/** One-shot import of `data.yml` into SQL when the database has no player rows. */
object YamlToSqlMigrator {
    fun migrateIfEmpty(
        yamlFile: File,
        sql: SqlStorage,
        logger: Logger,
    ) {
        if (sql.allKnownUuids().isNotEmpty()) {
            return
        }
        if (!yamlFile.isFile) {
            return
        }
        val yaml = YamlStorage(yamlFile, logger)
        val uuids = yaml.allKnownUuids()
        if (uuids.isEmpty()) {
            return
        }
        val accounts = ArrayList<PlayerAccount>(uuids.size)
        for (uuid in uuids) {
            val loaded = yaml.load(uuid)
            if (loaded != null) {
                accounts.add(loaded)
            }
        }
        if (accounts.isEmpty()) {
            return
        }
        sql.saveAll(accounts)
        logger.info("Imported ${accounts.size} economy accounts from ${yamlFile.name} into SQL storage.")
    }
}
