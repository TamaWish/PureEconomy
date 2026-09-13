package io.github.tamawish.pureeconomy.permission

import io.github.tamawish.pureeconomy.PureEconomy
import io.github.tamawish.pureeconomy.util.Schedulers
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.permissions.Permission
import org.bukkit.permissions.PermissionDefault
import java.util.Locale

/** Resolves configurable permission nodes and registers them with Bukkit. */
class Permissions(
    private val plugin: PureEconomy,
) {
    /** Logical permission slots that map to configurable Bukkit nodes. */
    enum class Node(
        val configKey: String,
        val fallbackNode: String,
        val fallbackDefault: PermissionDefault,
    ) {
        BALANCE("balance", "pureeconomy.balance", PermissionDefault.TRUE),
        BALANCE_OTHERS("balance-others", "pureeconomy.balance.others", PermissionDefault.OP),
        BANK("bank", "pureeconomy.bank", PermissionDefault.TRUE),
        BANK_TRANSFER("bank-transfer", "pureeconomy.bank.transfer", PermissionDefault.TRUE),
        BANK_WITHDRAW("bank-withdraw", "pureeconomy.bank.withdraw", PermissionDefault.TRUE),
        PAY("pay", "pureeconomy.pay", PermissionDefault.TRUE),
        BALTOP("baltop", "pureeconomy.baltop", PermissionDefault.TRUE),
        CURRENCY("currency", "pureeconomy.currency", PermissionDefault.TRUE),
        ECO("eco", "pureeconomy.eco", PermissionDefault.OP),
        ECO_GIVE("eco-give", "pureeconomy.eco.give", PermissionDefault.OP),
        ECO_TAKE("eco-take", "pureeconomy.eco.take", PermissionDefault.OP),
        ECO_SET("eco-set", "pureeconomy.eco.set", PermissionDefault.OP),
        ECO_RESET("eco-reset", "pureeconomy.eco.reset", PermissionDefault.OP),
        ECO_RELOAD("eco-reload", "pureeconomy.eco.reload", PermissionDefault.OP),
        ECO_BANK("eco-bank", "pureeconomy.eco.bank", PermissionDefault.OP),
        ECO_BANK_GIVE("eco-bank-give", "pureeconomy.eco.bank.give", PermissionDefault.OP),
        ECO_BANK_TAKE("eco-bank-take", "pureeconomy.eco.bank.take", PermissionDefault.OP),
        ECO_BANK_SET("eco-bank-set", "pureeconomy.eco.bank.set", PermissionDefault.OP),
        ECO_BANK_RESET("eco-bank-reset", "pureeconomy.eco.bank.reset", PermissionDefault.OP),
        ADMIN("admin", "pureeconomy.admin", PermissionDefault.OP),
    }

    /** Registers or updates every configured permission node with Bukkit. */
    fun reload() {
        if (plugin.settings().getSection("permissions") == null) {
            plugin.logger.info(
                "No permissions: section in config.yml — using built-in defaults. " +
                    "Copy that section from the jar's config.yml to change nodes or who has them.",
            )
        }
        val manager = Bukkit.getPluginManager()
        for (node in Node.entries) {
            val name = name(node)
            val def = defaultOf(node)
            val existing = manager.getPermission(name)
            if (existing == null) {
                manager.addPermission(Permission(name, def))
            } else if (existing.default != def) {
                existing.default = def
                manager.recalculatePermissionDefaults(existing)
            }
        }
        for (player in Bukkit.getOnlinePlayers()) {
            Schedulers.runAtEntity(plugin, player) { player.recalculatePermissions() }
        }
    }

    /** Returns the Bukkit permission node configured for a logical slot. */
    fun name(node: Node): String {
        val value =
            plugin.settings().getString("permissions.${node.configKey}.node", node.fallbackNode)
        if (value.isNullOrBlank()) {
            return node.fallbackNode
        }
        return value.trim()
    }

    /** Checks whether a sender has the configured permission for a slot. */
    fun has(
        sender: CommandSender,
        node: Node,
    ): Boolean {
        if (sender !is Player) {
            return true
        }
        if (sender.hasPermission(name(node))) {
            return true
        }
        return parentNodes(node).any { sender.hasPermission(name(it)) }
    }

    private fun parentNodes(node: Node): List<Node> =
        when (node) {
            Node.BALANCE_OTHERS -> listOf(Node.ADMIN)
            Node.ECO_BANK_GIVE, Node.ECO_BANK_TAKE, Node.ECO_BANK_SET, Node.ECO_BANK_RESET ->
                listOf(Node.ECO_BANK, Node.ECO, Node.ADMIN)
            Node.ECO_GIVE, Node.ECO_TAKE, Node.ECO_SET, Node.ECO_RESET, Node.ECO_RELOAD, Node.ECO_BANK ->
                listOf(Node.ECO, Node.ADMIN)
            Node.ECO -> listOf(Node.ADMIN)
            else -> emptyList()
        }

    private fun defaultOf(node: Node): PermissionDefault {
        val raw = plugin.settings().get("permissions.${node.configKey}.default")
        return parseDefault(raw, node.fallbackDefault)
    }

    companion object {
        /**
         * Parses a config default into a Bukkit [PermissionDefault].
         *
         * YAML `true` / `false` are booleans; Bukkit only accepts `true`, `op`, and `false`.
         * Legacy aliases `everyone` and `nobody` still resolve for existing `config.yml` files.
         *
         * @param raw config value such as `true`, `op`, or `false`
         * @param fallback value used when [raw] is blank or unrecognized
         * @return resolved permission default
         */
        @JvmStatic
        fun parseDefault(
            raw: Any?,
            fallback: PermissionDefault,
        ): PermissionDefault {
            if (raw is Boolean) {
                return if (raw) PermissionDefault.TRUE else PermissionDefault.FALSE
            }
            val value =
                raw
                    ?.toString()
                    ?.trim()
                    ?.lowercase(Locale.ROOT)
                    .orEmpty()
            if (value.isEmpty()) {
                return fallback
            }
            val bukkitName =
                when (value) {
                    "everyone", "all", "yes" -> "true"
                    "nobody", "none", "no" -> "false"
                    "operator", "operators", "ops" -> "op"
                    else -> value
                }
            return PermissionDefault.getByName(bukkitName) ?: fallback
        }
    }
}
