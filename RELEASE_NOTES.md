# Release notes

User-facing highlights for recent PureEconomy releases. For every notable change, see [CHANGELOG.md](CHANGELOG.md).

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
