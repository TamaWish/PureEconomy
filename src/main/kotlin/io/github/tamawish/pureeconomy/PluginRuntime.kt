package io.github.tamawish.pureeconomy

import io.github.tamawish.pureeconomy.api.PureEconomyAPI
import io.github.tamawish.pureeconomy.config.PluginSettings
import io.github.tamawish.pureeconomy.economy.EconomyService
import io.github.tamawish.pureeconomy.hook.PlaceholderHook
import io.github.tamawish.pureeconomy.hook.VaultHook
import io.github.tamawish.pureeconomy.lang.Lang
import io.github.tamawish.pureeconomy.network.NetworkDatabase
import io.github.tamawish.pureeconomy.permission.Permissions
import io.github.tamawish.pureeconomy.persistence.AccountRepository
import io.github.tamawish.pureeconomy.storage.PlayerStorage
import io.github.tamawish.pureeconomy.util.UpdateChecker

/** Started-state created in `onEnable` and discarded as a unit in `onDisable`. */
class PluginRuntime(
    val settings: PluginSettings,
    val lang: Lang,
    val permissions: Permissions,
    val economy: EconomyService,
    val storage: PlayerStorage,
    val repository: AccountRepository,
    val api: PureEconomyAPI,
    val vaultHook: VaultHook,
    val placeholderHook: PlaceholderHook,
    val updateChecker: UpdateChecker,
    val networkDatabase: NetworkDatabase?,
)
