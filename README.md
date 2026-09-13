# PureEconomy

Lightweight multi-currency economy for Paper, Purpur, Folia, Velocity, BungeeCord, and Waterfall.

![PureEconomy](https://files.catbox.moe/3v73ga.png)

[![GitHub Release](https://img.shields.io/github/v/release/TamaWish/PureEconomy?sort=semver&display_name=release&style=plastic&logo=github&label=Release)](https://github.com/TamaWish/PureEconomy/releases)
[![License: MIT](https://img.shields.io/github/license/TamaWish/PureEconomy?style=plastic&logo=github&label=License&color=red)](LICENSE)
[![bStats Servers](https://img.shields.io/bstats/servers/33797?style=plastic&label=bStats%20servers&color=f16436)](https://bstats.org/plugin/bukkit/PureEconomy/33797)
[![Java](https://img.shields.io/badge/Java-21%2B-orange?style=plastic&logo=openjdk&logoColor=white)](https://www.java.com)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4%2B-brightgreen?style=plastic&logo=minecraft&logoColor=white)](https://www.minecraft.net)
[![Platforms](https://img.shields.io/badge/Platforms-Paper%20%7C%20Purpur%20%7C%20Folia-blue?style=plastic)](https://github.com/TamaWish/PureEconomy)

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Usage](#usage)
- [Commands](#commands)
- [Configuration](#configuration)
- [Placeholders](#placeholders)
- [Language](#language)
- [Storage](#storage)
- [API](#api)
- [Metrics](#metrics)
- [Update checker](#update-checker)
- [Development](#development)
- [Contributing](#contributing)
- [License](#license)

## Features

- Multi-currency wallets with optional personal bank balances
- Player payments (`/pay`), balance checks, and `/baltop` leaderboards
- Admin `/eco` tools for wallet and bank (give, take, set, reset, reload)
- Configurable permission nodes and defaults in `config.yml`
- Optional [Vault](https://www.spigotmc.org/resources/vault.34315/) hook (default currency only)
- Optional [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) placeholders for every configured currency
- Folia-aware scheduling (`folia-supported: true`) and async I/O via Paper schedulers
- YAML persistence with autosave; BoostedYAML comment-preserving merge for `config.yml` / `lang/`

Focused on economy only — no kits, homes, chat, shops, interest, or GUIs.

Pair with [AuraUtils](https://github.com/TamaWish/AuraUtils) for a lightweight server stack: PureEconomy for economy, AuraUtils for utilities. Neither plugin requires the other.

| Need | Plugin |
|------|--------|
| Economy, pay, bank, baltop, Vault | **PureEconomy** |
| Homes, warps, TPA, RTP, fly, god | **[AuraUtils](https://github.com/TamaWish/AuraUtils)** |

## Requirements

| Requirement | Notes |
|-------------|--------|
| Minecraft | 1.21.4+ (`api-version` `1.21`) |
| Java | 21+ (bytecode release 21). Kotlin stdlib, JDBC drivers, Hikari, and Caffeine are loaded by Paper `libraries:`. |
| Server | Paper, Purpur, or Folia (Spigot/CraftBukkit are not supported). Velocity, BungeeCord, or Waterfall for proxy networks. |
| Vault | Optional |
| PlaceholderAPI | Optional |
| Gradle | Wrapper included (`./gradlew`) when building from source |

## Installation

### From a release

1. Download the latest `PureEconomy-*.jar` from [GitHub Releases](https://github.com/TamaWish/PureEconomy/releases).
2. Place the jar in your server’s `plugins/` folder. First Paper boot downloads Maven dependencies into `libraries/` (needs outbound access to Maven Central, or pre-fill that folder).
3. Start (or restart) the server.
4. Edit `plugins/PureEconomy/config.yml` and `plugins/PureEconomy/lang/en.yml` as needed, then run `/eco reload` or restart.

### Build from source

```bash
./gradlew test build
```

Copy `build/libs/PureEconomy-1.2.0.jar` into `plugins/`.

The build also produces `velocity/build/libs/PureEconomy-Velocity-1.2.0.jar` and
`bungee/build/libs/PureEconomy-Bungee-1.2.0.jar`.

### Proxy networks

1. Install the Velocity or Bungee artifact on the proxy and `PureEconomy-1.2.0.jar` on every backend.
2. Use one shared MySQL database and copy the proxy's currencies/default currency to each backend.
3. Set `storage.type: mysql` and `network.enabled: true` on every backend.
4. Configure secure Velocity modern forwarding or Bungee IP forwarding so proxy and backend UUIDs match.
5. Start the proxy first. It publishes the canonical currency fingerprint; backends reject mutations when their definitions differ.

Proxy commands run asynchronously and include `/balance`, `/bank`, `/pay`, `/baltop`, `/currency`, and `/eco`. Vault and PlaceholderAPI remain backend integrations.

> [!NOTE]
> `config.yml` and `lang/*.yml` are merged with jar defaults on load and `/eco reload` via BoostedYAML: missing keys (and their comments) are inserted, custom values and extra disk-only keys are kept. Player data (`data.yml` or SQL) is never overwritten by the jar template.

## Usage

After install, players can:

```text
/balance
/pay Steve 10
/bank transfer 50
/baltop
/currency
```

Admins:

```text
/eco give Steve 100
/eco bank set Steve 0
/eco reload
```

Fresh installs ship only the `coins` currency (`default-currency: coins`). Extra currencies are optional under `currencies:` in `config.yml`.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/balance [player] [currency]` | `pureeconomy.balance` / `.others` | Wallet and bank balance(s). Aliases: `/bal`, `/money` |
| `/bank [balance] [currency]` | `pureeconomy.bank` | Bank balance(s) |
| `/bank transfer <amount> [currency]` | `pureeconomy.bank.transfer` | Wallet → bank (`deposit` and `tf` also work) |
| `/bank withdraw <amount> [currency]` | `pureeconomy.bank.withdraw` | Bank → wallet |
| `/pay <player> <amount> [currency]` | `pureeconomy.pay` | Pay another player |
| `/baltop [currency] [page]` | `pureeconomy.baltop` | Richest players. Aliases: `/balancetop`, `/moneytop` |
| `/currency` | `pureeconomy.currency` | List currencies. Alias: `/currencies` |
| `/eco give\|take\|set <player> <amount> [currency]` | `pureeconomy.eco.*` | Admin wallet |
| `/eco reset <player> [currency]` | `pureeconomy.eco.reset` | Wallet to starting balance (bank unchanged) |
| `/eco bank give\|take\|set <player> <amount> [currency]` | `pureeconomy.eco.bank.*` | Admin bank |
| `/eco bank reset <player> [currency]` | `pureeconomy.eco.bank.reset` | Bank to zero |
| `/eco reload` | `pureeconomy.eco.reload` | Reload config and language |

Grant `pureeconomy.admin` for all admin economy permissions at once. Console bypasses permission checks.

## Configuration

File: `plugins/PureEconomy/config.yml`

### Top-level options

| Key | Required | Default | Description |
|-----|----------|---------|-------------|
| `language` | no | `en` | Loads `lang/<code>.yml` |
| `autosave-seconds` | no | `60` | Disk flush interval; `0` = save only on quit/disable |
| `create-on-join` | no | `true` | Create accounts with starting balances on join |
| `network.enabled` | no | `false` | Use authoritative shared MySQL transactions on a proxy network |
| `update-checker.enabled` | no | `true` | Check GitHub for newer releases |
| `default-currency` | yes | `coins` | Vault + omitted currency argument |
| `pay-minimum` | no | `0.01` | Minimum `/pay` amount |
| `permissions.*` | no | see file | Rename nodes; set `true` / `op` / `false` defaults |
| `currencies.<id>.*` | yes | `coins` | Currency definitions |

### Currency fields

| Field | Description |
|-------|-------------|
| `singular` / `plural` / `symbol` | Display names and symbol |
| `decimals` | Decimal places (`0` for whole units such as gems) |
| `starting-balance` | Given once when the currency is first added to an account |
| `max-balance` | Cap; `-1` = none |
| `payable` | Whether players may `/pay` this currency |

Currency IDs must match `[a-z0-9_]` (lowercase letters, numbers, underscores).

### Adding an optional currency

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

Keep `default-currency: coins` unless gems should replace coins for Vault and commands that omit a currency. Run `/eco reload` or restart.

Removing a currency block hides it from commands and placeholders; balances remain in `data.yml` until you delete those entries manually (back up first).

### Giving players a second income

PureEconomy stores and transfers balances; it does not generate income from jobs, playtime, or mobs.

```text
/eco give <player> <amount> <currency>
/eco give Steve 5 gems
```

Vault exposes only one currency. Shops and jobs that deposit through Vault affect only `default-currency`. Optional currencies need the PureEconomy API or `/eco give`.

## Placeholders

With PlaceholderAPI:

| Placeholder | Description |
|-------------|-------------|
| `%pureeconomy_balance_<currency>%` | Raw wallet balance |
| `%pureeconomy_balance_<currency>_formatted%` | Balance with symbol and formatting |

Example: `%pureeconomy_balance_gems%`, `%pureeconomy_balance_coins_formatted%`, `%pureeconomy_bank_coins%`, `%pureeconomy_bank_coins_formatted%`.

Vault’s `%vault_eco_*%` placeholders use the default currency only.

## Language

`plugins/PureEconomy/lang/en.yml` ships with the plugin. Copy it to `lang/xx.yml`, edit messages, and set `language: xx` in `config.yml`. English is the only bundled language.

Shipped strings use MiniMessage tags (`<gray>`, `<white>`, …). Legacy `&` color codes still work. Keep placeholders such as `{prefix}`, `{brand}`, `{player}`, `{amount}`, `{bank}`, and `{currency}` unchanged.

Chat uses Paper Adventure. The shipped `{prefix}` / `{brand}` is a lime→emerald **gradient**. A custom `prefix` in your lang file is left as written.

## Storage

Default is YAML: `plugins/PureEconomy/data.yml` — fine for small servers. YAML rewrites the whole file on save and will not stay lag-free at tens of thousands of accounts.

For any busy standalone server, set `storage.type` to `sqlite` (local `economy.db`) or `mysql`. SQL stores amounts as `DECIMAL`, uses UPSERT, and ranks `/baltop` with `ORDER BY … LIMIT`. The first start with an empty database imports `data.yml` once and leaves the YAML file on disk as backup.

Hot accounts live in a Caffeine cache (`storage.cache`). PlaceholderAPI `peekBalance` is strictly memory-only and never waits for JDBC. In network mode, a cold or expired entry returns its last-known value (zero until first load) and queues one deduplicated background refresh. Successful mutations update cached balances immediately. Backends skip the local SQL ledger; MySQL is authoritative, fresh reads use `storage.cache.network-ttl-millis`, and the currency fingerprint is kept in memory (refreshed on the 30s timer).

For proxy networks, also set `network.enabled: true`; every mutation uses a row-locked transaction and reports an exact result such as insufficient funds or maximum balance. Bukkit command parsing, permission checks, player lookup, and messaging remain on the correct Paper/Folia thread. Network join bookkeeping, cache refreshes, and persistence work are scheduled explicitly in the background.

## API

Prefer the ServicesManager API (works for SoftDepend plugins without a hard jar dependency on internals):

```java
import io.github.tamawish.pureeconomy.api.PureEconomyAPI;
import java.math.BigDecimal;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

RegisteredServiceProvider<PureEconomyAPI> rsp =
    Bukkit.getServicesManager().getRegistration(PureEconomyAPI.class);
if (rsp != null) {
    PureEconomyAPI api = rsp.getProvider();
    UUID playerId = player.getUniqueId();
    api.deposit(playerId, "gems", BigDecimal.valueOf(5));
    api.transfer(fromId, toId, "coins", BigDecimal.TEN);
}
```

`PureEconomyAPI` also covers banks (`getBank` / `depositBank` / `withdrawBank`), `resolve(name)`, `top` / `topPages`, currency metadata (`symbol`, `decimals`, `payable`), and reason-coded `depositResult` / `withdrawResult` / `transferResult` (`EconomyResult`). Existing Boolean methods are unchanged. Listen to `EconomyTransactionEvent` (post-success, not cancellable; listeners run on the global region / main thread) for wallet, bank, and pay changes.

Vault = default currency for shops. `PureEconomyAPI` = multi-currency for integrators.

Same-jar access to the full service is still available:

```java
import io.github.tamawish.pureeconomy.PureEconomy;
import io.github.tamawish.pureeconomy.economy.Currency;

import java.math.BigDecimal;
import java.util.UUID;

var eco = PureEconomy.get().economy();
Currency gems = eco.currency("gems");
if (gems != null) {
    UUID playerId = player.getUniqueId();
    boolean credited = eco.add(playerId, gems, BigDecimal.valueOf(5));
    // false if max-balance would be exceeded
}
```

### EconomyService methods

| Method | Description |
|--------|-------------|
| `get` / `add` / `take` / `set` / `reset` | Wallet |
| `getBank` / `addBank` / `takeBank` / `setBank` / `resetBank` | Bank |
| `transferToBank` / `withdrawFromBank` | Move between wallet and bank |
| `transfer` | Player-to-player wallet transfer |
| `currency(id)` / `defaultCurrency()` | Resolve currencies |

`reset` restores the wallet to `starting-balance` and leaves the bank unchanged.

## Metrics

PureEconomy uses [bStats](https://bstats.org/plugin/bukkit/PureEconomy/33797) (plugin id `33797`) for anonymous usage stats: server software, player count, Java version, and whether Vault, PlaceholderAPI, or multi-currency is in use. No player names, balances, or economy data are sent.

Opt out for all bStats plugins in `plugins/bStats/config.yml` with `enabled: false`. See the [bStats docs](https://bstats.org/getting-started).

## Update checker

When enabled, PureEconomy checks [GitHub releases](https://github.com/TamaWish/PureEconomy/releases) asynchronously on start and after `/eco reload`. Newer versions are logged to console; players with `pureeconomy.admin` get a clickable release link on join. Disable with `update-checker.enabled: false`.

## Development

```bash
./gradlew test
```

```bash
./gradlew spotlessCheck
```

```bash
./gradlew -q build -x test
```

Spotless (ktlint) runs as part of the Gradle build. Tests use JUnit 5. Sources are Kotlin 2 on a Java 21 toolchain; async I/O uses `Schedulers.runAsync`. Config/lang merge uses BoostedYAML with `keepAll`.

### Smoke matrix

| Platform | Expect |
|----------|--------|
| Paper / Purpur | Thin plugin JAR + Paper `libraries:`; native Adventure; lime→emerald `{brand}` |
| Folia | Custom region/entity schedulers (`folia-supported: true`); economy events on the global region |
| Vault shops | Default currency only; withdraw/deposit use a single mutation result |
| SoftDepend plugins | `PureEconomyAPI` on ServicesManager for multi-currency |

See [CHANGELOG.md](CHANGELOG.md) for release notes.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for how to ask questions, report bugs, set up a build, and open a pull request.

## License

[MIT](LICENSE) — Copyright (c) 2026 TamaWish
