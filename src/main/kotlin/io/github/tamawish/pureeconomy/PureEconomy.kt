package io.github.tamawish.pureeconomy

import io.github.tamawish.pureeconomy.api.PureEconomyAPI
import io.github.tamawish.pureeconomy.api.PureEconomyApiService
import io.github.tamawish.pureeconomy.command.BalanceCommand
import io.github.tamawish.pureeconomy.command.BaltopCommand
import io.github.tamawish.pureeconomy.command.BankCommand
import io.github.tamawish.pureeconomy.command.CurrencyCommand
import io.github.tamawish.pureeconomy.command.EcoCommand
import io.github.tamawish.pureeconomy.command.PayCommand
import io.github.tamawish.pureeconomy.config.PluginSettings
import io.github.tamawish.pureeconomy.economy.CurrencyRegistry
import io.github.tamawish.pureeconomy.economy.EconomyService
import io.github.tamawish.pureeconomy.hook.MetricsHook
import io.github.tamawish.pureeconomy.hook.PlaceholderHook
import io.github.tamawish.pureeconomy.hook.VaultHook
import io.github.tamawish.pureeconomy.lang.Lang
import io.github.tamawish.pureeconomy.listener.PlayerConnectionListener
import io.github.tamawish.pureeconomy.network.NetworkConfig
import io.github.tamawish.pureeconomy.network.NetworkDatabase
import io.github.tamawish.pureeconomy.permission.Permissions
import io.github.tamawish.pureeconomy.persistence.AccountRepository
import io.github.tamawish.pureeconomy.persistence.LocalAccountRepository
import io.github.tamawish.pureeconomy.persistence.NetworkAccountRepository
import io.github.tamawish.pureeconomy.storage.NoOpPlayerStorage
import io.github.tamawish.pureeconomy.storage.PlayerStorage
import io.github.tamawish.pureeconomy.storage.StorageFactory
import io.github.tamawish.pureeconomy.storage.StorageSettings
import io.github.tamawish.pureeconomy.util.Schedulers
import io.github.tamawish.pureeconomy.util.UpdateChecker
import org.bukkit.Bukkit
import org.bukkit.command.CommandExecutor
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.io.FileInputStream

/** PureEconomy bootstrap plugin. Commands and listeners live in dedicated classes. */
class PureEconomy : JavaPlugin() {
    private var runtime: PluginRuntime? = null
    private var bootSettings: PluginSettings? = null
    private var autosaveTask: Any? = null
    private var networkValidationTask: Any? = null

    override fun onEnable() {
        instance = this
        val settings = PluginSettings(this)
        bootSettings = settings
        settings.reload()
        val lang = Lang(this)
        val permissions = Permissions(this)
        permissions.reload()
        val storageSettings = StorageSettings.from(settings)
        val networkEnabled = settings.getBoolean("network.enabled", false)
        if (networkEnabled && storageSettings.type != StorageSettings.Type.MYSQL) {
            logger.severe("network.enabled requires storage.type: mysql. Disabling PureEconomy.")
            server.pluginManager.disablePlugin(this)
            return
        }

        val registry = CurrencyRegistry(settings, logger)
        val storage: PlayerStorage
        val networkDatabase: NetworkDatabase?
        val repository: AccountRepository
        if (networkEnabled) {
            storage = NoOpPlayerStorage
            val networkConfig = FileInputStream(dataFolder.resolve("config.yml")).use(NetworkConfig::load)
            networkDatabase = NetworkDatabase.connect(networkConfig, logger)
            if (!networkDatabase.fingerprintMatches()) {
                logger.severe("Currency configuration does not match the proxy. Mutations will remain locked.")
            }
            repository =
                NetworkAccountRepository(networkDatabase, storageSettings.networkTtlMillis) { task ->
                    Schedulers.runAsync(this, task)
                }
        } else {
            storage = StorageFactory.create(this, storageSettings)
            networkDatabase = null
            repository =
                LocalAccountRepository(
                    storage,
                    storageSettings.maxAccounts,
                    storageSettings.expireMinutes,
                    { account ->
                        EconomyService.seedStarting(account, registry.currencies().values)
                    },
                ) { task -> Schedulers.runAsync(this, task) }
        }

        val economy =
            EconomyService(
                this,
                repository,
                registry,
                storageSettings.baltopTtlSeconds,
            )
        economy.loadCurrencies()
        if (!economy.hasCurrencies()) {
            logger.severe("No currencies configured in config.yml. Disabling PureEconomy.")
            repository.close()
            server.pluginManager.disablePlugin(this)
            return
        }

        val updateChecker = UpdateChecker(this)
        val vaultHook = VaultHook(this)
        val placeholderHook = PlaceholderHook(this)
        val api = PureEconomyApiService(economy)
        runtime =
            PluginRuntime(
                settings,
                lang,
                permissions,
                economy,
                storage,
                repository,
                api,
                vaultHook,
                placeholderHook,
                updateChecker,
                networkDatabase,
            )

        registerCommand("balance", BalanceCommand(this))
        registerCommand("bank", BankCommand(this))
        registerCommand("pay", PayCommand(this))
        registerCommand("baltop", BaltopCommand(this))
        registerCommand("eco", EcoCommand(this))
        registerCommand("currency", CurrencyCommand(this))

        Bukkit.getPluginManager().registerEvents(PlayerConnectionListener(this, updateChecker), this)
        vaultHook.tryHook()
        placeholderHook.tryHook()
        Bukkit.getServicesManager().register(PureEconomyAPI::class.java, api, this, ServicePriority.Normal)
        MetricsHook.register(this)
        startAutosave()
        startNetworkValidation()
        logger.info("PureEconomy enabled. Currencies: ${economy.currencyIds()}")
        updateChecker.checkAsync()
    }

    override fun onDisable() {
        if (autosaveTask != null) {
            Schedulers.cancel(autosaveTask)
        }
        if (networkValidationTask != null) {
            Schedulers.cancel(networkValidationTask)
        }
        val started = runtime
        if (started != null) {
            Bukkit.getServicesManager().unregister(PureEconomyAPI::class.java, started.api)
            started.economy.saveAll()
            started.repository.close()
            started.vaultHook.unhook()
            started.placeholderHook.unhook()
            runtime = null
        }
        bootSettings = null
        instance = null
    }

    /** Reloads config, language, permissions, currencies, hooks, and autosave. */
    fun reloadAll() {
        val started = runtime ?: return
        started.settings.reload()
        started.lang.reload()
        started.permissions.reload()
        started.economy.loadCurrencies()
        refreshNetworkFingerprint()
        if (!started.economy.hasCurrencies()) {
            logger.severe("No currencies configured after reload. Fix config.yml.")
        }
        started.vaultHook.tryHook()
        started.placeholderHook.tryHook()
        startAutosave()
        startNetworkValidation()
        started.updateChecker.checkAsync()
    }

    private fun registerCommand(
        name: String,
        executor: CommandExecutor,
    ) {
        val command = getCommand(name)
        if (command == null) {
            logger.severe("Missing command '/$name' in plugin.yml — cannot register.")
            return
        }
        // Bukkit command parsing, permissions, player lookup, and messaging must remain on the
        // command sender's server thread. Repositories schedule their explicit background work.
        command.setExecutor(executor)
        if (executor is TabCompleter) {
            command.tabCompleter = executor
        }
    }

    private fun startAutosave() {
        if (autosaveTask != null) {
            Schedulers.cancel(autosaveTask)
            autosaveTask = null
        }
        val started = runtime ?: return
        val seconds = started.settings.getLong("autosave-seconds", 60)
        if (seconds <= 0) {
            return
        }
        val ticks = seconds * 20L
        autosaveTask = Schedulers.runGlobalTimer(this, { started.economy.saveDirtyAsync() }, ticks, ticks)
    }

    private fun refreshNetworkFingerprint() {
        val database = runtime?.networkDatabase ?: return
        try {
            val current = FileInputStream(dataFolder.resolve("config.yml")).use(NetworkConfig::load)
            database.expectFingerprint(current.fingerprint)
            database.refreshFingerprint()
            if (!database.fingerprintMatches()) {
                logger.severe("Currency configuration does not match the proxy. Economy mutations are locked.")
            }
        } catch (exception: Exception) {
            logger.log(java.util.logging.Level.SEVERE, "Could not validate network currency configuration", exception)
        }
    }

    private fun startNetworkValidation() {
        networkValidationTask?.let(Schedulers::cancel)
        networkValidationTask = null
        if (runtime?.networkDatabase != null) {
            networkValidationTask =
                Schedulers.runGlobalTimer(this, { refreshNetworkFingerprint() }, 600L, 600L)
        }
    }

    fun economy(): EconomyService = runtime!!.economy

    fun api(): PureEconomyAPI? = runtime?.api

    fun lang(): Lang = runtime!!.lang

    fun permissions(): Permissions = runtime!!.permissions

    fun settings(): PluginSettings = runtime?.settings ?: bootSettings!!

    fun brandEco(): String = BRAND_PAPER

    fun resolvedPrefix(): String {
        val configured = runtime?.lang?.raw("prefix")
        if (configured.isNullOrEmpty() || configured == "prefix" || isShippedDefaultPrefix(configured)) {
            return brandEco() + PREFIX_SEPARATOR
        }
        return configured
    }

    companion object {
        const val BRAND_PAPER: String = "<gradient:#C8FF7A:#3DDC84><bold>Eco</bold></gradient>"
        const val PREFIX_SEPARATOR: String = " <dark_gray>»</dark_gray> "
        const val DEFAULT_PREFIX_PAPER: String = "$BRAND_PAPER$PREFIX_SEPARATOR"

        private const val LEGACY_BRACKET_PREFIX = "<dark_gray>[<green>Eco</green>]</dark_gray> <reset>"
        private const val LEGACY_AMPERSAND_PREFIX = "&8[&aEco&8] &r"
        private const val DEFAULT_PREFIX_BUKKIT = "<green><bold>Eco</bold>$PREFIX_SEPARATOR"

        @JvmStatic
        private var instance: PureEconomy? = null

        @JvmStatic
        fun get(): PureEconomy = instance!!

        private fun isShippedDefaultPrefix(value: String): Boolean =
            value == DEFAULT_PREFIX_PAPER ||
                value == DEFAULT_PREFIX_BUKKIT ||
                value == LEGACY_BRACKET_PREFIX ||
                value == LEGACY_AMPERSAND_PREFIX
    }
}
