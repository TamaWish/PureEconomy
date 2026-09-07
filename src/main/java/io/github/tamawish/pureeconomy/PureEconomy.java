package io.github.tamawish.pureeconomy;

import io.github.tamawish.pureeconomy.command.BalanceCommand;
import io.github.tamawish.pureeconomy.command.BaltopCommand;
import io.github.tamawish.pureeconomy.command.BankCommand;
import io.github.tamawish.pureeconomy.command.CurrencyCommand;
import io.github.tamawish.pureeconomy.command.EcoCommand;
import io.github.tamawish.pureeconomy.command.PayCommand;
import io.github.tamawish.pureeconomy.economy.EconomyService;
import io.github.tamawish.pureeconomy.hook.MetricsHook;
import io.github.tamawish.pureeconomy.hook.PlaceholderHook;
import io.github.tamawish.pureeconomy.hook.VaultHook;
import io.github.tamawish.pureeconomy.lang.Lang;
import io.github.tamawish.pureeconomy.listener.PlayerConnectionListener;
import io.github.tamawish.pureeconomy.permission.Permissions;
import io.github.tamawish.pureeconomy.storage.YamlStorage;
import io.github.tamawish.pureeconomy.util.Schedulers;
import io.github.tamawish.pureeconomy.util.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** PureEconomy bootstrap plugin. Commands and listeners live in dedicated classes. */
public final class PureEconomy extends JavaPlugin {

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

    lang = new Lang(this);
    permissions = new Permissions(this);
    permissions.reload();
    storage = new YamlStorage(this);
    economy = new EconomyService(this, storage);
    economy.loadCurrencies();
    if (!economy.hasCurrencies()) {
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

    updateChecker = new UpdateChecker(this);
    Bukkit.getPluginManager()
        .registerEvents(new PlayerConnectionListener(this, updateChecker), this);

    vaultHook = new VaultHook(this);
    vaultHook.tryHook();
    placeholderHook = new PlaceholderHook(this);
    placeholderHook.tryHook();

    MetricsHook.register(this);

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

  /** Reloads config, language, permissions, currencies, hooks, and autosave. */
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
    autosaveTask = Schedulers.runGlobalTimer(this, () -> economy.saveDirtyAsync(), ticks, ticks);
  }

  /** Returns the enabled plugin singleton. */
  public static PureEconomy get() {
    return instance;
  }

  /** Returns the loaded economy service. */
  public EconomyService economy() {
    return economy;
  }

  /** Returns the language manager. */
  public Lang lang() {
    return lang;
  }

  /** Returns the permission resolver. */
  public Permissions permissions() {
    return permissions;
  }

  /** Returns the live Bukkit configuration for this plugin. */
  public FileConfiguration cfg() {
    return getConfig();
  }
}
