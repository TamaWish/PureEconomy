package io.github.tamawish.pureeconomy;

import io.github.tamawish.pureeconomy.command.BalanceCommand;
import io.github.tamawish.pureeconomy.command.BankCommand;
import io.github.tamawish.pureeconomy.command.BaltopCommand;
import io.github.tamawish.pureeconomy.command.CurrencyCommand;
import io.github.tamawish.pureeconomy.command.EcoCommand;
import io.github.tamawish.pureeconomy.command.PayCommand;
import io.github.tamawish.pureeconomy.economy.EconomyService;
import io.github.tamawish.pureeconomy.hook.MetricsHook;
import io.github.tamawish.pureeconomy.hook.PlaceholderHook;
import io.github.tamawish.pureeconomy.hook.VaultHook;
import io.github.tamawish.pureeconomy.lang.Lang;
import io.github.tamawish.pureeconomy.permission.Permissions;
import io.github.tamawish.pureeconomy.storage.YamlStorage;
import io.github.tamawish.pureeconomy.util.Schedulers;
import io.github.tamawish.pureeconomy.util.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PureEconomy extends JavaPlugin implements Listener {

    private static PureEconomy instance;
    private EconomyService economy;
    private YamlStorage storage;
    private Lang lang;
    private Permissions permissions;
    private VaultHook vaultHook;
    private PlaceholderHook placeholderHook;
    private UpdateChecker updateChecker;
    private Object autosaveTask;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.lang = new Lang(this);
        this.permissions = new Permissions(this);
        this.permissions.reload();
        this.storage = new YamlStorage(this);
        this.economy = new EconomyService(this, storage);
        this.economy.loadCurrencies();
        if (!this.economy.hasCurrencies()) {
            getLogger().severe("No currencies configured in config.yml. Disabling PureEconomy.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getCommand("balance").setExecutor(new BalanceCommand(this));
        getCommand("bank").setExecutor(new BankCommand(this));
        getCommand("pay").setExecutor(new PayCommand(this));
        getCommand("baltop").setExecutor(new BaltopCommand(this));
        getCommand("eco").setExecutor(new EcoCommand(this));
        getCommand("currency").setExecutor(new CurrencyCommand(this));

        Bukkit.getPluginManager().registerEvents(this, this);

        this.vaultHook = new VaultHook(this);
        this.vaultHook.tryHook();
        this.placeholderHook = new PlaceholderHook(this);
        this.placeholderHook.tryHook();

        MetricsHook.register(this);
        this.updateChecker = new UpdateChecker(this);

        startAutosave();
        getLogger().info("PureEconomy enabled. Currencies: " + economy.currencyIds());
        updateChecker.checkAsync();
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            Schedulers.cancel(autosaveTask);
        }
        if (economy != null) {
            economy.saveAll();
        }
        if (vaultHook != null) {
            vaultHook.unhook();
        }
        if (placeholderHook != null) {
            placeholderHook.unhook();
        }
    }

    public void reloadAll() {
        reloadConfig();
        lang.reload();
        permissions.reload();
        economy.loadCurrencies();
        if (vaultHook != null) {
            vaultHook.tryHook();
        }
        if (placeholderHook != null) {
            placeholderHook.tryHook();
        }
        startAutosave();
        if (updateChecker == null) {
            updateChecker = new UpdateChecker(this);
        }
        updateChecker.checkAsync();
    }

    private void startAutosave() {
        if (autosaveTask != null) {
            Schedulers.cancel(autosaveTask);
            autosaveTask = null;
        }
        long seconds = getConfig().getLong("autosave-seconds", 60);
        if (seconds <= 0) {
            return;
        }
        long ticks = seconds * 20L;
        autosaveTask = Schedulers.runGlobalTimer(this, () -> economy.saveDirty(), ticks, ticks);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (getConfig().getBoolean("create-on-join", true)) {
            economy.ensureAccount(player.getUniqueId(), player.getName());
        }
        if (updateChecker != null) {
            updateChecker.notifyPlayerIfNeeded(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        economy.savePlayer(event.getPlayer().getUniqueId());
    }

    public static PureEconomy get() {
        return instance;
    }

    public EconomyService economy() {
        return economy;
    }

    public Lang lang() {
        return lang;
    }

    public Permissions permissions() {
        return permissions;
    }

    public FileConfiguration cfg() {
        return getConfig();
    }
}
