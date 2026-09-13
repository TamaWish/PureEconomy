package io.github.tamawish.pureeconomy.storage

import io.github.tamawish.pureeconomy.PureEconomy
import io.github.tamawish.pureeconomy.economy.PlayerAccount
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Level
import java.util.logging.Logger

/** YAML-backed persistence for player wallets and banks (Bukkit YamlConfiguration). */
class YamlStorage(
    private val file: File,
    private val logger: Logger,
) : PlayerStorage {
    constructor(plugin: PureEconomy) : this(File(plugin.dataFolder, "data.yml"), plugin.logger)

    private val yaml = YamlConfiguration()
    private val nameIndex: MutableMap<String, UUID> = ConcurrentHashMap()
    private val namesByUuid: MutableMap<UUID, String> = ConcurrentHashMap()
    private val walletIndex: MutableMap<UUID, MutableMap<String, BigDecimal>> = ConcurrentHashMap()
    private val bankIndex: MutableMap<UUID, MutableMap<String, BigDecimal>> = ConcurrentHashMap()
    private val known: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val ioLock = ReentrantLock()

    init {
        loadIndex()
    }

    @Synchronized
    private fun loadIndex() {
        if (!file.exists()) {
            return
        }
        try {
            yaml.load(file)
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Could not load data.yml", e)
            return
        }
        val root = yaml.getConfigurationSection("players") ?: return
        for (key in root.getKeys(false)) {
            try {
                val uuid = UUID.fromString(key)
                known.add(uuid)
                val name = root.getString("$key.name")
                if (name != null) {
                    nameIndex[name.lowercase(Locale.ROOT)] = uuid
                    namesByUuid[uuid] = name
                }
                val balances = root.getConfigurationSection("$key.balances")
                if (balances != null) {
                    val wallets: MutableMap<String, BigDecimal> = ConcurrentHashMap()
                    for (id in balances.getKeys(false)) {
                        wallets[id.lowercase(Locale.ROOT)] =
                            parseBalance("players.$key.balances.$id", balances.getString(id, "0"))
                    }
                    walletIndex[uuid] = wallets
                }
                val banks = root.getConfigurationSection("$key.bank-balances")
                if (banks != null) {
                    val stored: MutableMap<String, BigDecimal> = ConcurrentHashMap()
                    for (id in banks.getKeys(false)) {
                        stored[id.lowercase(Locale.ROOT)] =
                            parseBalance("players.$key.bank-balances.$id", banks.getString(id, "0"))
                    }
                    bankIndex[uuid] = stored
                }
            } catch (_: IllegalArgumentException) {
                // Skip corrupt UUID keys.
            }
        }
    }

    @Synchronized
    override fun load(uuid: UUID): PlayerAccount? {
        val section = yaml.getConfigurationSection("players.$uuid") ?: return null
        val account = PlayerAccount(uuid)
        account.setName(section.getString("name"))
        val balances = section.getConfigurationSection("balances")
        if (balances != null) {
            for (id in balances.getKeys(false)) {
                val path = "players.$uuid.balances.$id"
                account.set(id.lowercase(Locale.ROOT), parseBalance(path, balances.getString(id, "0")))
            }
        }
        val bankBalances = section.getConfigurationSection("bank-balances")
        if (bankBalances != null) {
            for (id in bankBalances.getKeys(false)) {
                val path = "players.$uuid.bank-balances.$id"
                account.setBank(
                    id.lowercase(Locale.ROOT),
                    parseBalance(path, bankBalances.getString(id, "0")),
                )
            }
        }
        account.markClean()
        remember(account)
        return account
    }

    override fun hydrate(uuid: UUID): PlayerAccount? {
        if (!known.contains(uuid)) {
            return null
        }
        val account = PlayerAccount(uuid)
        namesByUuid[uuid]?.let { account.setName(it) }
        walletIndex[uuid]?.forEach { (id, amount) -> account.set(id, amount) }
        bankIndex[uuid]?.forEach { (id, amount) -> account.setBank(id, amount) }
        account.markClean()
        return account
    }

    override fun remember(account: PlayerAccount) {
        known.add(account.uuid())
        val name = account.name()
        if (name != null) {
            nameIndex[name.lowercase(Locale.ROOT)] = account.uuid()
            namesByUuid[account.uuid()] = name
        }
        walletIndex[account.uuid()] = ConcurrentHashMap(account.balances())
        bankIndex[account.uuid()] = ConcurrentHashMap(account.bankBalances())
    }

    override fun save(account: PlayerAccount) {
        saveAll(Collections.singleton(account))
    }

    override fun saveAll(accounts: Collection<PlayerAccount>) {
        val snapshots = HashMap<PlayerAccount, PlayerAccount.Snapshot>()
        for (account in accounts) {
            snapshots[account] = account.snapshot()
        }

        val saved: Boolean
        ioLock.lock()
        try {
            synchronized(this) {
                for (snapshot in snapshots.values) {
                    applySnapshot(snapshot)
                }
                saved = writeYaml()
            }
        } finally {
            ioLock.unlock()
        }
        if (saved) {
            snapshots.forEach { (account, snapshot) -> account.markClean(snapshot.revision) }
        }
    }

    private fun applySnapshot(snapshot: PlayerAccount.Snapshot) {
        val path = "players.${snapshot.uuid}"
        if (snapshot.name != null) {
            yaml.set("$path.name", snapshot.name)
            nameIndex[snapshot.name.lowercase(Locale.ROOT)] = snapshot.uuid
            namesByUuid[snapshot.uuid] = snapshot.name
        }
        val wallets: MutableMap<String, BigDecimal> = ConcurrentHashMap(snapshot.balances)
        walletIndex[snapshot.uuid] = wallets
        bankIndex[snapshot.uuid] = ConcurrentHashMap(snapshot.bankBalances)
        for ((key, value) in snapshot.balances) {
            yaml.set("$path.balances.$key", value.toPlainString())
        }
        for ((key, value) in snapshot.bankBalances) {
            yaml.set("$path.bank-balances.$key", value.toPlainString())
        }
        known.add(snapshot.uuid)
    }

    private fun writeYaml(): Boolean {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val temp = File(file.parentFile, file.name + ".tmp")
        return try {
            yaml.save(temp)
            try {
                Files.move(
                    temp.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (e: IOException) {
            logger.log(Level.SEVERE, "Could not save data.yml", e)
            if (temp.exists() && !temp.delete()) {
                logger.warning("Could not delete temporary data file: ${temp.name}")
            }
            false
        }
    }

    private fun parseBalance(
        path: String,
        raw: String?,
    ): BigDecimal =
        try {
            BigDecimal(raw!!.trim())
        } catch (_: NumberFormatException) {
            logger.warning("Invalid balance at $path: $raw — using 0")
            BigDecimal.ZERO
        }

    override fun uuidByName(name: String): UUID? = nameIndex[name.lowercase(Locale.ROOT)]

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

    override fun peekName(uuid: UUID): String? = namesByUuid[uuid]

    override fun allKnownUuids(): Set<UUID> = Collections.unmodifiableSet(known)

    override fun top(
        currencyId: String,
        offset: Int,
        limit: Int,
    ): List<BalanceRow> = StorageIndexes.top(walletIndex, namesByUuid, currencyId, offset, limit)

    override fun countPositive(currencyId: String): Int = StorageIndexes.countPositive(walletIndex, currencyId)
}
