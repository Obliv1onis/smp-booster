# SMP Booster

SMP Booster is a configurable gameplay-restriction and lightweight anti-cheat plugin for Minecraft Java Edition 26.3 running on Paper 26.3.

## Build and Installation

Requirements: Java 25 and Paper 26.3.

```bash
./gradlew build
```

Copy `build/libs/SMP Booster-1.0.jar` into the Paper server's `plugins` directory, then start the server. After the first start, the configuration is available at `plugins/smp-booster/config.yml`. Restart the server after manually editing the file.

## Gameplay Restrictions

- `disabled-from-level: 5` blocks level V and above, reducing affected items to level IV.
- `disabled: true` completely disables an enchantment or potion effect. Enchantments are removed from items by default.
- `remove-item: true`, used together with `disabled: true`, deletes the entire item stack carrying that enchantment. The default Curse of Vanishing example enables this option.
- Strong Vanilla potions, such as Strength II, are reduced to the corresponding level I potion. Custom potion effects are reduced as well.
- The ender pearl and chorus fruit options disable only their teleportation behavior; they do not delete the items.
- Explosion multipliers use `1.0` for Vanilla damage, `0.5` for half damage, and `0` for no damage.

The plugin continuously scans online players' items. It also checks items when container chunks load, items are picked up or dispensed, anvil and smithing results are produced, and inventories are changed. Items in unloaded chunks or offline player data are processed the next time they are loaded.

## Anti-Cheat

Checks include attack reach, KillAura, CrystalAura, regular flight, and level Elytra flight without a firework boost. Detailed evidence is written to `plugins/smp-booster/anticheat.log`.

- `/acwhitelist add <player>` adds a player to the anti-cheat whitelist.
- `/acwhitelist remove <player>` removes a player from the whitelist.
- `/acwhitelist list` displays the whitelist.
- `/silentac on` logs detections and alerts online operators without banning the player.
- `/silentac off` bans players after they reach the configured confirmation threshold.
- `/silentac status` displays the current mode.

These commands are restricted to operators. The ban message and duration can be changed under `anti-cheat.ban`. Supported duration formats include `30m`, `12h`, `7d`, `2w`, and `permanent`.

When silent anti-cheat confirms a detection, every online operator receives a bold chat alert and a notification sound.

## Infinite Villager Trades

- `/infvillager` toggles infinite villager trades globally.
- `/infvillager on|off|toggle|status` explicitly changes or displays the current state.

This command is restricted to operators. While enabled, trades do not gain uses, and sold-out trades belonging to loaded villagers are restored immediately. Villagers in unloaded chunks are restored when their chunks are loaded. The state is stored in `features.infinite-villager-trades`.

## Paper Vanilla Exploit Fixes

- `/paperfix` toggles the supported Paper fixes.
- `/paperfix on|off|toggle|status` explicitly changes or displays the current state.

`on` uses Paper's safer behavior, while `off` permits the corresponding Vanilla exploits. The command currently controls attribute swapping (including spears), tripwire hook duplication, piston duplication, unsafe end-portal teleportation, headless pistons, permanent-block breaking exploits, and invulnerable end crystals.

Both Paper's live configuration and its YAML files are updated. A `.smp-booster.bak` backup is created before the first change.

> [!WARNING]
> Disabling these fixes can allow item duplication and irreversible damage to bedrock or End Portal blocks. Paper classifies several of these settings as unsupported. Older tripwire duplicator designs that depend on specific block-update ordering are not guaranteed to work even when the fixes are disabled.

## Languages

The language setting applies globally and is restricted to operators:

- `/language zh` — Simplified Chinese
- `/language en` — English
- `/language ja` — Japanese
- `/language de` — German
- `/language es` — Spanish

The selected language is saved under `messages.language` and is applied immediately to restriction notices, administrative commands, and anti-cheat alerts.
