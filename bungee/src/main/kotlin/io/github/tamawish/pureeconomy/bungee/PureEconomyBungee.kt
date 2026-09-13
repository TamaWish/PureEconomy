package io.github.tamawish.pureeconomy.bungee

import io.github.tamawish.pureeconomy.network.NetworkActor
import io.github.tamawish.pureeconomy.network.NetworkCommands
import io.github.tamawish.pureeconomy.network.NetworkConfig
import io.github.tamawish.pureeconomy.network.NetworkDatabase
import io.github.tamawish.pureeconomy.network.NetworkPlayers
import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.api.plugin.Command
import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.api.plugin.TabExecutor
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.TimeUnit

class PureEconomyBungee : Plugin() {
    private var database: NetworkDatabase? = null
    private var commands: NetworkCommands? = null
    override fun onEnable() {
        if (!dataFolder.exists()) dataFolder.mkdirs()
        val file = dataFolder.toPath().resolve("config.yml")
        if (Files.notExists(file)) getResourceAsStream("config.yml").use { Files.copy(it, file) }
        val config = Files.newInputStream(file).use(NetworkConfig::load)
        val db = NetworkDatabase.connect(config, logger)
        db.publishFingerprint(); database = db
        val handler = NetworkCommands(config, db, BungeePlayers(proxy)) { db.publishFingerprint(); true }
        commands = handler
        listOf("balance", "bank", "pay", "baltop", "currency", "eco").forEach { name ->
            val aliases = when (name) { "balance" -> arrayOf("bal", "money"); "baltop" -> arrayOf("balancetop", "moneytop"); "currency" -> arrayOf("currencies"); else -> emptyArray() }
            proxy.pluginManager.registerCommand(this, BungeeCommand(this, name, aliases))
        }
        proxy.scheduler.schedule(this, { if (!db.fingerprintMatches()) logger.severe("PureEconomy currency configuration fingerprint mismatch") }, 30, 30, TimeUnit.SECONDS)
        logger.info("PureEconomy 1.2.0 enabled with authoritative MySQL network storage")
    }
    internal fun execute(name: String, source: CommandSender, args: List<String>) { proxy.scheduler.runAsync(this) { commands?.execute(name, BungeeActor(source), args) } }
    internal fun suggest(name: String, source: CommandSender, args: List<String>) = commands?.suggestions(name, BungeeActor(source), args) ?: emptyList()
    override fun onDisable() { database?.close() }
}
private class BungeeCommand(private val plugin: PureEconomyBungee, private val command: String, aliases: Array<String>) : Command(command, null, *aliases), TabExecutor {
    override fun execute(sender: CommandSender, args: Array<out String>) = plugin.execute(command, sender, args.toList())
    override fun onTabComplete(sender: CommandSender, args: Array<out String>) = plugin.suggest(command, sender, args.toList())
}
private class BungeeActor(private val source: CommandSender) : NetworkActor {
    override val uuid get() = (source as? ProxiedPlayer)?.uniqueId
    override val name get() = source.name
    override fun hasPermission(permission: String) = source.hasPermission(permission)
    override fun send(message: String) = source.sendMessage(TextComponent(message))
}
private class BungeePlayers(private val proxy: ProxyServer) : NetworkPlayers {
    override fun online(name: String) = proxy.getPlayer(name)?.let { it.uniqueId to it.name }
    override fun notify(uuid: UUID, message: String) { proxy.getPlayer(uuid)?.sendMessage(TextComponent(message)) }
    override fun names() = proxy.players.map { it.name }
}
