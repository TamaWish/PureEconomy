# PureEconomy

Lightweight multi-currency economy for **Bukkit / Spigot / Paper / Folia**.

Focused on balances and payments only — no kits, homes, chat, shops, interest, or GUI.

Pair with [AuraUtils](https://github.com/TamaWish/AuraUtils) for a lightweight server stack:
utilities and economy as separate plugins, each doing one job well.

## Lightweight stack (with AuraUtils)

PureEconomy handles **economy** — balances, pay, bank, baltop, multi-currency, and Vault.

[AuraUtils](https://github.com/TamaWish/AuraUtils) handles **utilities** — homes, warps, TPA,
back, RTP, fly, god, and more. The two plugins are optional companions; neither requires the other.

Together they cover common server needs while keeping installs small and configs easy to reason
about. Use only what your server needs.

| Need | Plugin |
|------|--------|
| Economy, pay, baltop, Vault | **PureEconomy** |
| Homes, warps, TPA, RTP, fly, god | **[AuraUtils](https://github.com/TamaWish/AuraUtils)** |

## Build

```bash
mvn -q -DskipTests package
```

Jar: `target/PureEconomy-1.0.1.jar`

- Java 21+ (Java 25 is required by Paper/Folia 26.1+)
- Server 1.21.4+ (Paper/Folia recommended)
- Vault optional. If present, the **default currency** is registered as the Vault economy so shops/jobs work.
- PlaceholderAPI optional. If present, every configured currency is available through PureEconomy placeholders.

Folia: `folia-supported: true`. Autosave uses `GlobalRegionScheduler` on Paper/Folia and Bukkit scheduler on Spigot.

## Metrics

PureEconomy uses [bStats](https://bstats.org/) to collect anonymous usage statistics (server
software, player count, Java version, and a few plugin-specific flags such as whether Vault or
PlaceholderAPI is present). No player names, balances, or economy data are sent.

Server owners can disable metrics for all bStats-enabled plugins by editing
`plugins/bStats/config.yml` and setting `enabled: false`. See the
[bStats documentation](https://bstats.org/getting-started) for details.

## Update checker

PureEconomy checks [GitHub releases](https://github.com/TamaWish/PureEconomy/releases)
asynchronously when it starts and after `/eco reload`. If a newer release exists, it logs the
version and release URL to console. Players with `pureeconomy.admin` also receive a clickable
release link when they join. Set `update-checker.enabled: false` in `config.yml` to disable it.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/balance [player] [currency]` | `pureeconomy.balance` / `.others` | Wallet and bank balance(s) |
| `/bank [balance] [currency]` | `pureeconomy.bank` | Bank balance(s) |
| `/bank transfer <amount> [currency]` | `pureeconomy.bank.transfer` | Move wallet funds to the bank (`deposit` and `tf` also work) |
| `/bank withdraw <amount> [currency]` | `pureeconomy.bank.withdraw` | Move bank funds to the wallet |
| `/pay <player> <amount> [currency]` | `pureeconomy.pay` | Transfer |
| `/baltop [currency] [page]` | `pureeconomy.baltop` | Leaderboard |
| `/currency` | `pureeconomy.currency` | List currencies |
| `/eco give \| take \| set <player> <amount> [currency]` | `pureeconomy.eco.*` | Admin wallet |
| `/eco reset <player> [currency]` | `pureeconomy.eco.reset` | Wallet starting balance (bank unchanged) |
| `/eco bank give \| take \| set <player> <amount> [currency]` | `pureeconomy.eco.bank.*` | Admin bank |
| `/eco bank reset <player> [currency]` | `pureeconomy.eco.bank.reset` | Bank to zero |
| `/eco reload` | `pureeconomy.eco.reload` | Reload config + lang |

## Config

`plugins/PureEconomy/config.yml`

The `permissions:` section lists every command node with comments. Change `node`
to rename a permission, or set `default` to `everyone`, `op`, or `nobody`. Then
`/eco reload`. LuckPerms (and similar) still override `default` when they grant
or deny the node. Console always bypasses these checks.

This file is copied once on first run and is not overwritten by a new jar.
Existing servers must paste the `permissions:` block from the jar config, or
the plugin keeps the built-in `pureeconomy.*` nodes and defaults.

Fresh installations contain only the default `coins` currency. Extra currencies
are optional and can be added under `currencies:` when a server needs them.

Each currency has:

- `singular` / `plural` / `symbol`
- `decimals` — number of decimal places (`0` for whole items such as gems)
- `starting-balance` — given once when that currency is first added to an account
- `max-balance` (`-1` = none)
- `payable` — whether players may transfer it with `/pay`

`default-currency` must match one configured currency ID. It is used by Vault
and by commands where the optional currency argument is omitted.

### Adding an optional currency

Currency IDs may contain lowercase letters, numbers, and underscores. The ID is
not restricted to `gems`; examples include `tokens`, `credits`, or
`event_points`.

To add gems while keeping coins as the default, add this next to `coins` under
the existing `currencies:` section:

```yaml
currencies:
  coins:
    singular: Coin
    plural: Coins
    symbol: "$"
    decimals: 2
    starting-balance: 100.00
    max-balance: -1
    payable: true

  gems:
    singular: Gem
    plural: Gems
    symbol: "◆"
    decimals: 0
    starting-balance: 0
    max-balance: -1
    payable: true
```

Keep `default-currency: coins` unless gems should replace coins for Vault and
commands that omit a currency. Run `/eco reload` after editing the file, or
restart the server. Existing accounts receive the new currency's
`starting-balance` when they are next loaded.

To stop using an optional currency, remove its block and reload or restart.
Stored balances are preserved in `data.yml`, but the disabled currency is
hidden from commands and placeholders. Adding the same ID again restores those
balances. Back up `data.yml` and remove its balance entries manually only if
the currency data must be permanently deleted.

### Giving players a second income

PureEconomy stores and transfers balances; it does not generate income from
jobs, playtime, mobs, or other activities.

An administrator or console can award any configured currency explicitly:

```text
/eco give <player> <amount> <currency>
/eco give Steve 5 gems
```

For automated rewards, the plugin handling the activity must integrate with
PureEconomy's API and name the configured currency. For example:

```java
import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.economy.Currency;

import java.math.BigDecimal;
import java.util.UUID;

Currency gems = PureEconomy.get().economy().currency("gems");
if (gems != null) {
    UUID playerId = player.getUniqueId();
    boolean credited = PureEconomy.get().economy()
            .add(playerId, gems, BigDecimal.valueOf(5));
    // credited is false if the configured maximum balance would be exceeded.
}
```

Vault exposes only one currency per economy provider. Shops, jobs, and reward
plugins that deposit through Vault therefore affect only `default-currency`.
They must use the PureEconomy API (or run `/eco give`) to award an optional
currency.

## Placeholders

Vault's `%vault_eco_*%` placeholders represent the default currency because the Vault API supports one currency per economy provider.

With PlaceholderAPI installed, use:

- `%pureeconomy_balance_<currency>%` — raw balance
- `%pureeconomy_balance_<currency>_formatted%` — balance with the configured symbol and formatting

For example, after configuring `gems`,
`%pureeconomy_balance_gems%` and
`%pureeconomy_balance_gems_formatted%` expose it. The same placeholders with
`coins` expose coins.

## Language

`plugins/PureEconomy/lang/en.yml`

Copy it to `lang/xx.yml`, edit it, and set `language: xx` in `config.yml`. The
plugin ships English only.

The file is copied once on first run and is not overwritten when you drop in a
new jar, so custom wording is kept. New or changed default text for keys that
already exist must be copied in by hand, or delete `lang/en.yml` and restart to
restore the shipped file.

`&` color codes. `{prefix}`, `{player}`, `{amount}`, `{bank}`, `{currency}` placeholders.

## Storage

`plugins/PureEconomy/data.yml` — UUID keyed wallet and bank balances. Cached in memory, flushed on quit, disable, and autosave. Bank balances start at zero.

## API (same jar)

```java
import io.github.tamawish.pureeconomy.PureEconomy;

var eco = PureEconomy.get().economy();
eco.get(uuid, currency);
eco.add(uuid, currency, amount);
eco.take(uuid, currency, amount);
eco.set(uuid, currency, amount);
eco.transfer(from, to, currency, amount);
eco.reset(uuid, currency);          // wallet only
eco.getBank(uuid, currency);
eco.addBank(uuid, currency, amount);
eco.takeBank(uuid, currency, amount);
eco.setBank(uuid, currency, amount);
eco.resetBank(uuid, currency);
eco.transferToBank(uuid, currency, amount);
eco.withdrawFromBank(uuid, currency, amount);
eco.currency("currency_id");
```
