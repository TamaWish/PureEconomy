package io.github.tamawish.pureeconomy.hook;

import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.economy.Currency;
import io.github.tamawish.pureeconomy.economy.EconomyService;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class VaultEconomy extends AbstractEconomy {

    private final PureEconomy plugin;

    public VaultEconomy(PureEconomy plugin) {
        this.plugin = plugin;
    }

    private EconomyService eco() {
        return plugin.economy();
    }

    private Currency cur() {
        return eco().defaultCurrency();
    }

    private UUID id(String name) {
        return eco().resolve(name);
    }

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled() && cur() != null;
    }

    @Override
    public String getName() {
        return "PureEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return cur() == null ? 2 : cur().decimals();
    }

    @Override
    public String format(double amount) {
        if (cur() == null) {
            return String.valueOf(amount);
        }
        return cur().format(BigDecimal.valueOf(amount));
    }

    @Override
    public String currencyNamePlural() {
        return cur() == null ? "Coins" : cur().plural();
    }

    @Override
    public String currencyNameSingular() {
        return cur() == null ? "Coin" : cur().singular();
    }

    @Override
    public boolean hasAccount(String playerName) {
        return id(playerName) != null;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return player != null;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        UUID uuid = id(playerName);
        if (uuid == null || cur() == null) {
            return 0;
        }
        return eco().get(uuid, cur()).doubleValue();
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (player == null || cur() == null) {
            return 0;
        }
        eco().ensureAccount(player.getUniqueId(), player.getName());
        return eco().get(player.getUniqueId(), cur()).doubleValue();
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        UUID uuid = id(playerName);
        if (uuid == null || cur() == null) {
            return false;
        }
        return eco().has(uuid, cur(), BigDecimal.valueOf(amount));
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        if (player == null || cur() == null) {
            return false;
        }
        return eco().has(player.getUniqueId(), cur(), BigDecimal.valueOf(amount));
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        UUID uuid = id(playerName);
        if (uuid == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Unknown player");
        }
        return withdraw(uuid, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (player == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Unknown player");
        }
        return withdraw(player.getUniqueId(), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    private EconomyResponse withdraw(UUID uuid, double amount) {
        if (cur() == null) {
            return fail("No default currency");
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            return fail("Amount must be finite and positive");
        }
        BigDecimal bd = cur().normalize(BigDecimal.valueOf(amount));
        if (bd.compareTo(BigDecimal.ZERO) <= 0) {
            return fail("Amount is below the minimum currency precision");
        }
        if (!eco().take(uuid, cur(), bd)) {
            return new EconomyResponse(0, eco().get(uuid, cur()).doubleValue(), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }
        return new EconomyResponse(bd.doubleValue(), eco().get(uuid, cur()).doubleValue(), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        UUID uuid = id(playerName);
        if (uuid == null) {
            return fail("Unknown player");
        }
        return deposit(uuid, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (player == null) {
            return fail("Unknown player");
        }
        return deposit(player.getUniqueId(), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    private EconomyResponse deposit(UUID uuid, double amount) {
        if (cur() == null) {
            return fail("No default currency");
        }
        if (!Double.isFinite(amount) || amount <= 0) {
            return fail("Amount must be finite and positive");
        }
        BigDecimal bd = cur().normalize(BigDecimal.valueOf(amount));
        if (bd.compareTo(BigDecimal.ZERO) <= 0) {
            return fail("Amount is below the minimum currency precision");
        }
        if (!eco().add(uuid, cur(), bd)) {
            return fail("Max balance");
        }
        return new EconomyResponse(bd.doubleValue(), eco().get(uuid, cur()).doubleValue(), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return noBank();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return noBank();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return noBank();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return noBank();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return noBank();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return noBank();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return noBank();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return noBank();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return noBank();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return noBank();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return noBank();
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        UUID uuid = id(playerName);
        if (uuid == null) {
            OfflinePlayer off = Bukkit.getOfflinePlayer(playerName);
            eco().ensureAccount(off.getUniqueId(), playerName);
            return true;
        }
        eco().ensureAccount(uuid, playerName);
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (player == null) {
            return false;
        }
        eco().ensureAccount(player.getUniqueId(), player.getName());
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    private static EconomyResponse noBank() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No banks");
    }

    private static EconomyResponse fail(String reason) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, reason);
    }
}
