package io.github.tamawish.pureeconomy.velocity

import com.google.inject.Inject
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import io.github.tamawish.pureeconomy.network.NetworkActor
import io.github.tamawish.pureeconomy.network.NetworkCommands
import io.github.tamawish.pureeconomy.network.NetworkConfig
import io.github.tamawish.pureeconomy.network.NetworkDatabase
import io.github.tamawish.pureeconomy.network.NetworkPlayers
import net.kyori.adventure.text.Component
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.logging.Logger as JulLogger

@Plugin(id = "pureeconomy", name = "PureEconomy", version = "1.2.0", authors = ["Lozaine@TamaWish"])
class PureEconomyVelocity @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    @DataDirectory private val folder: Path,
) {
    private var database: NetworkDatabase? = null
    private var commands: NetworkCommands? = null

    @Subscribe
    fun onInitialize(event: ProxyInitializeEvent) {
        Files.createDirectories(folder)
        val file = folder.resolve("config.yml")
        if (Files.notExists(file)) javaClass.getResourceAsStream("/config.yml")!!.use { Files.copy(it, file) }
        val config = Files.newInputStream(file).use(NetworkConfig::load)
        val db = NetworkDatabase.connect(config, JulLogger.getLogger("PureEconomy"))
        db.publishFingerprint()
        database = db
        val directory = VelocityPlayers(server)
        val handler = NetworkCommands(config, db, directory) { db.publishFingerprint(); true }
        commands = handler
        val manager = server.commandManager
        listOf("balance", "bank", "pay", "baltop", "currency", "eco").forEach { name ->
            val aliases = when (name) { "balance" -> arrayOf("bal", "money"); "baltop" -> arrayOf("balancetop", "moneytop"); "currency" -> arrayOf("currencies"); else -> emptyArray() }
            manager.register(manager.metaBuilder(name).aliases(*aliases).plugin(this).build(), VelocityCommand(this, name))
        }
        server.scheduler.buildTask(this, Runnable { verifyConfig() }).repeat(30, TimeUnit.SECONDS).schedule()
        logger.info("PureEconomy 1.2.0 enabled with authoritative MySQL network storage")
    }

    private fun verifyConfig() { if (database?.fingerprintMatches() == false) logger.error("PureEconomy currency configuration fingerprint mismatch") }
    internal fun execute(name: String, source: CommandSource, args: List<String>) {
        server.scheduler.buildTask(this, Runnable { commands?.execute(name, VelocityActor(source), args) }).schedule()
    }
    internal fun suggest(name: String, source: CommandSource, args: List<String>) = commands?.suggestions(name, VelocityActor(source), args) ?: emptyList()

    @Subscribe fun onShutdown(event: ProxyShutdownEvent) { database?.close() }
}

private class VelocityCommand(private val plugin: PureEconomyVelocity, private val name: String) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) = plugin.execute(name, invocation.source(), invocation.arguments().toList())
    override fun suggest(invocation: SimpleCommand.Invocation) = plugin.suggest(name, invocation.source(), invocation.arguments().toList())
}
private class VelocityActor(private val source: CommandSource) : NetworkActor {
    override val uuid get() = (source as? Player)?.uniqueId
    override val name get() = (source as? Player)?.username ?: "Console"
    override fun hasPermission(permission: String) = source.hasPermission(permission)
    override fun send(message: String) = source.sendMessage(Component.text(message))
}
private class VelocityPlayers(private val server: ProxyServer) : NetworkPlayers {
    override fun online(name: String) = server.getPlayer(name).map { it.uniqueId to it.username }.orElse(null)
    override fun notify(uuid: UUID, message: String) { server.getPlayer(uuid).ifPresent { it.sendMessage(Component.text(message)) } }
    override fun names() = server.allPlayers.map { it.username }
}
