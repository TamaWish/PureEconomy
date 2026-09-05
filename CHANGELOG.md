# Changelog

All notable changes to PureEconomy are documented in this file.

## 1.0.1

### Added
- Optional asynchronous GitHub release checker, enabled by default. It logs newer releases to
  console and gives `pureeconomy.admin` players a clickable release link when they join.
- `/eco bank give|take|set|reset` for admin bank adjustments, with matching `pureeconomy.eco.bank.*` permissions.
- Tab completion for `/currency`.
- Error message when `/bank transfer` or `/bank withdraw` fails after pre-checks.
- `/balance` now also shows the matching bank amount.
- `permissions:` section in `config.yml` to rename nodes and set `everyone` / `op` / `nobody` defaults, with comments for each command. `/eco reload` applies changes. Existing `config.yml` files are not overwritten; paste that section in by hand, or built-in `pureeconomy.*` defaults stay in effect.

Replace the old jar and restart the server. `/eco reload` does not load new Java from a replaced jar.

`plugins/PureEconomy/lang/en.yml` is copied once on first run and is never overwritten later, so your edits stay intact. Existing keys keep their old text; the plugin does not merge new wording into keys that already exist. After upgrading from 1.0.0, `/balance` still shows wallet only until the three balance lines include `{bank}`.

Pick one:

1. Edit these lines in `lang/en.yml`, then `/eco reload`:

```yaml
balance-self: "{prefix}&7Your {currency}: &f{amount} &8(&7bank &f{bank}&8)"
balance-other: "{prefix}&f{player}&7's {currency}: &f{amount} &8(&7bank &f{bank}&8)"
balance-all-line: "&8- &f{currency}&7: &f{amount} &8(&7bank &f{bank}&8)"
```

2. Delete only `lang/en.yml` (not the whole folder unless you want to), restart, and let the plugin recopy the default file from the jar.

### Changed
- Currency IDs that do not match `[a-z0-9_]` are skipped at load with a warning.
- `/eco reset` remains wallet-only. Use `/eco bank reset` to clear a bank balance.
- `/eco` now uses the `account-missing` language key when a player account cannot be resolved.

### API
- `addBank`, `takeBank`, and `resetBank` on `EconomyService` for admin and integrator bank changes.

## 1.0.0

Initial release: multi-currency wallet and personal bank, `/pay`, `/baltop`, Vault (default currency only), and PlaceholderAPI.
