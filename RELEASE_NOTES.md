# Release notes

User-facing highlights for recent PureEconomy releases. For every notable change, see [CHANGELOG.md](CHANGELOG.md).

## Version 1.2.0 — 2026-09-13

**Headline:** Paper-only thin JAR, no MySQL on the scoreboard thread, and SQL that scales.

Drop `PureEconomy-1.2.0.jar` on Paper / Purpur / Folia. First boot downloads Kotlin, Hikari, Caffeine, and JDBC drivers via Paper `libraries:` (needs Maven Central once). Spigot is no longer supported. Proxy networks use `PureEconomy-Velocity-1.2.0.jar` or `PureEconomy-Bungee-1.2.0.jar` with the same shared MySQL database and currency definitions.

Existing SQL databases migrate VARCHAR amounts to DECIMAL automatically. YAML stays the default for tiny servers; use SQLite or MySQL for anything busy.

### Reliability and threading

- PlaceholderAPI balance placeholders are memory-only. A cold or expired network value returns immediately and refreshes once in the background, so scoreboard rendering never waits for MySQL.
- Successful network mutations update the cache immediately and preserve specific failure reasons such as insufficient funds and maximum balance.
- Bukkit command parsing, permissions, player lookup, messaging, and transaction events run on their proper Paper/Folia thread.
- Network account setup on join runs asynchronously, and dirty cache evictions remain queued across overlapping save passes.

### Verification

The release suite includes regression coverage for zero-JDBC placeholder misses, coalesced cache refreshes, asynchronous join setup, dirty eviction preservation, concurrent transfers, and network mutation status mapping.

## Version 1.1.0 — 2026-09-09

**Headline:** Public multi-currency API, comment-preserving config merge, and optional SQL for large servers.

### Highlights

- `PureEconomyAPI` on Bukkit ServicesManager: wallets, banks, pay, baltop, name resolve, currency metadata.
- `EconomyTransactionEvent` after successful money movement (not cancellable).
- BoostedYAML merges missing `config.yml` / lang keys on load and `/eco reload` without wiping custom keys.
- Optional `storage.type: sqlite` or `mysql` (Hikari). YAML `data.yml` stays the default; switching to empty SQL imports it once.
- Caffeine-backed hot accounts and TTL-cached `/baltop`.
- MiniMessage language files (legacy `&` still works). Chat prefix is a lime→emerald gradient on Paper and solid green on Spigot (`BukkitAudiences`). Kotlin 2 / Java 21.

### Upgrade notes

Replace the jar. Existing `config.yml` gains a `storage:` block on next load. Leave `storage.type: yaml` unless you want SQLite/MySQL. Back up `plugins/PureEconomy/` before switching storage.

### Links

- [Changelog 1.1.0](CHANGELOG.md#110---2026-09-09)
- [README](README.md)

## Version 1.0.2 — 2026-09-08

**Headline:** Spigot servers can enable PureEconomy again.

### Fixed

- Enable crash on Spigot caused by missing Adventure (`Component`) classes — Adventure is now shaded and messages go through `BukkitAudiences`.
- Paper-only `getPluginMeta()` usage replaced with Spigot-compatible APIs.
- Folia-safe schedulers (no `BukkitScheduler` fallback on Paper/Folia) and region-safe `/pay` recipient messages.

### Upgrade notes

Replace the jar and restart (or `/reload` if you use it). No config changes required.

### Links

- [Changelog 1.0.2](CHANGELOG.md#102---2026-09-08)
- [README](README.md)
- [GitHub Release v1.0.2](https://github.com/TamaWish/PureEconomy/releases/tag/v1.0.2)
- Download: [PureEconomy-1.0.2.jar](https://github.com/TamaWish/PureEconomy/releases/download/v1.0.2/PureEconomy-1.0.2.jar)

## Version 1.0.1 — 2026-09-05

**Headline:** Admins can manage bank balances from `/eco`, tune permissions in config, and get notified when a newer release is out.

This update is aimed at operators: bank tools catch up with wallet admin commands, permissions are configurable without a rebuild, and an optional GitHub checker surfaces updates in console and to admins on join. Players see bank amounts on `/balance` once language strings are updated.

### Highlights

- Optional GitHub release checker (on by default) logs newer versions to console and shows `pureeconomy.admin` players a clickable release link on join.
- `/eco bank give|take|set|reset` adjust personal bank balances, with matching `pureeconomy.eco.bank.*` permissions.
- `permissions:` in `config.yml` renames permission nodes and sets `everyone` / `op` / `nobody` defaults; `/eco reload` applies changes.
- `/balance` can show the matching bank amount alongside the wallet (language strings must include `{bank}`).
- Tab completion for `/currency`.
- Clearer error when `/bank transfer` or `/bank withdraw` fails after pre-checks.

### Improvements

- Invalid currency IDs (not matching `[a-z0-9_]`) are skipped at load with a warning instead of being accepted.
- `/eco` uses the `account-missing` language key when a player account cannot be resolved.
- `/eco reset` stays wallet-only; use `/eco bank reset` to clear a bank balance.
- `EconomyService` exposes `addBank`, `takeBank`, and `resetBank` for admin tools and other plugins.

### Upgrade notes

Replace the jar and restart. `/eco reload` does not load new Java from a replaced jar.

Existing `config.yml` and `lang/en.yml` are not overwritten.

- To use configurable permissions, paste the `permissions:` section from the jar default or the release asset `config.yml`.
- To show bank amounts on `/balance`, update `balance-self`, `balance-other`, and `balance-all-line` in `lang/en.yml` to include `{bank}`, or delete that file and restart so the default is recopied.
- Built-in `pureeconomy.*` defaults remain in effect if you leave `permissions:` out.
- Disable the update checker with `update-checker.enabled: false` if you prefer.

Requirements are unchanged: Minecraft 1.21.4+, Java 21+. Vault and PlaceholderAPI remain optional.

### Links

- [Changelog 1.0.1](CHANGELOG.md#101---2026-09-05)
- [README](README.md)
- [GitHub Release v1.0.1](https://github.com/TamaWish/PureEconomy/releases/tag/v1.0.1)
- Download: [PureEconomy-1.0.1.jar](https://github.com/TamaWish/PureEconomy/releases/download/v1.0.1/PureEconomy-1.0.1.jar)

## Version 1.0.0 — 2026-09-02

**Headline:** First public release — multi-currency wallets, optional banks, payments, and admin tools for Bukkit, Spigot, Paper, and Folia.

PureEconomy focuses on balances and transfers only: no kits, homes, shops, or GUIs. Configure currencies, pay players, move money between wallet and bank, and run leaderboards.

### Highlights

- Multi-currency wallets with optional personal bank balances.
- Player commands: balance checks, `/pay`, bank transfer/withdraw, and paginated `/baltop`.
- Admin `/eco` give, take, set, reset, and reload for wallets.
- Configurable currency names, symbols, precision, starting balances, limits, and transfer settings.
- Optional Vault hook for the configured default currency.
- Optional PlaceholderAPI placeholders for every configured currency.
- Folia-compatible scheduling and thread-safe balance operations.
- Atomic YAML persistence with automatic saves.
- Optional anonymous bStats metrics.

### Upgrade notes

Nothing prior to upgrade from. Drop `PureEconomy-1.0.0.jar` in `plugins/`, restart, then edit `config.yml` and `lang/en.yml` as needed.

Requirements: Minecraft 1.21.4+, Java 21+. Vault and PlaceholderAPI are optional.

### Links

- [Changelog 1.0.0](CHANGELOG.md#100---2026-09-02)
- [README](README.md)
- [GitHub Release v1.0.0](https://github.com/TamaWish/PureEconomy/releases/tag/v1.0.0)
- Download: [PureEconomy-1.0.0.jar](https://github.com/TamaWish/PureEconomy/releases/download/v1.0.0/PureEconomy-1.0.0.jar)
