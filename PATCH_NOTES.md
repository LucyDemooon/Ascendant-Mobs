# Remnants: RPG Mobs — Patch Notes

Formerly RPG Mob Leveling System. Full notes for each major release are in [patchnotes/](patchnotes/).

## 3.1.0

**Supported platforms:** Minecraft 1.20.1 (Fabric, Forge) · 1.21.1 (Fabric, NeoForge) · 26.1.2, 26.2, 26.3 (Fabric, NeoForge, Forge)

The difficulty engine. See [patchnotes/3.1.0.md](patchnotes/3.1.0.md).

* **Difficulty** grows with days, play time, distance, depth and gear, per dimension, and drives levels and rank odds. `/remnants difficulty` and an on-screen gauge.
* **Event nights**: the Grim Vigil sends waves of named, ranked hunters and a Remnant mini-boss. Add your own nights.
* **Progression**: Vital Embers grow your hearts for good; Mending Salve and Hunter's Draught heal.
* **Readable elites**: rank auras, Champion boss bars, glowing event hunters, visible without the mod too.
* **Loot that scales** with difficulty, loot beams over rare drops, and Mob LootBags bags when installed.
* **New: Forge on 26.1.2, 26.2 and 26.3.**
* **Fixes**: Volatile keeps its drops; NeoForge servers no longer crash near players without the mod; 26.1.2 nameplates stay readable at an angle.
* **Upgrading**: existing configs keep every new system off until you set `enabled: true`; fresh installs start with them on.

## 3.0.0: Blood and Valor

**Supported platforms:** Minecraft 1.20.1 (Fabric, Forge) · 1.21.1, 26.1.2, 26.2, 26.3 (Fabric, NeoForge)

A full rebuild, renamed **Remnants: RPG Mobs** (mod id `remnant_mobs`). See [patchnotes/3.0.0.md](patchnotes/3.0.0.md).

* **Levels** from the threat of a place: distance, world age, depth, dimension bands and structure minimums, pulled toward nearby players' RPG Attribute System levels. New percent-based attribute rules with fixed modifier ids.
* **Ranks**: Veteran, Elite, Champion and Remnant, with more health, damage, armor, experience, loot and Remnant Shards. Remnants get titles, boss bars, a horn and a server-wide message.
* **16 traits**, from Vampiric and Frostbite to Blinking, Packleader and Volatile.
* **Nameplates** with rank crest, level coloured against your own, health bar with damage trail, and traits.
* **Rewards**: experience by level and rank, extra loot rolls, Remnant Shards, and bonus Valor with RAS.
* **Items**: Remnant Shard, Hunter's Lens, Brand of Challenge. **Hunter's Moon** event. 10 advancements.
* `/remnants` commands, five commented JSON configs with `/remnants reload`, `RemnantMobsApi`.
* **Upgrading**: remove the 2.x jar. 2.x configs are carried over once, 2.x mobs keep their level. The Calendar item and the Neat and Jade hooks are removed.

## 2.0.7

**Supported platforms:** Minecraft 1.20.1 (Fabric, Forge) · Minecraft 1.21.1 (Fabric, NeoForge) · Minecraft 26.3 (Fabric, NeoForge)

### Minecraft 26.3
* New MultiLoader workspace (`26.3/`) for Fabric + NeoForge on Minecraft 26.3 (Java 25, NeoForge 26.3.0.16-beta, Fabric Loader 0.19.5, Fabric API 0.160.6+26.3).
* Gameplay is the existing 2.0.7 feature set: spawn leveling, attribute/XP scaling, configs, `/rml` commands, calendar item, Ascendant elites, and level display on vanilla name tags.
* **Neat plate integration is omitted** on 26.3 — Neat has no 26.3 build. Levels use the vanilla name-tag path instead. Cloth Config stays optional/unused (it was only a 1.21.1 Neat run-client companion, not a required dependency).

### Features / Compatibility
* **Neat soft integration** (1.20.1 / 1.21.1) — when [Neat](https://modrinth.com/mod/neat) is installed, mob levels appear on Neat unit-frame name plates using `nameFormat` (default `%mob_name% [Lv. %level%]`). Neat remains fully optional.
* **Legacy HUD auto-disable** — floating level digits are suppressed while Neat is present to avoid double UI.

### Bug Fixes / Polish
* **Oversized legacy level digits** — fallback overlay scale reduced to nametag-sized text when Neat is absent.
* **NeoForge / Fabric 1.21.1 missing overlays** — fixed incorrect partial-tick source (`getGameTimeDeltaTicks` → `getGameTimeDeltaPartialTick`) so level labels lerp and render correctly.

### Upgrade Notes
1. Replace previous jars with the matching 2.0.7 loader jar.
2. Optional: install Neat for polished plates; no config changes required.
3. Without Neat, legacy number HUD still works (now smaller).

## 2.0.6

**Supported platforms:** Minecraft 1.20.1 (Fabric, Forge) · Minecraft 1.21.1 (Fabric, NeoForge)

### Bug Fixes
* **Ranged / non-melee attribution** — when `DamageSource.getEntity()` is the projectile itself (instead of the shooter), level scaling now resolves the projectile owner. Melee is unchanged (still attribute-based).

### Upgrade Notes
1. Drop-in replacement for 2.0.5 on all four loader jars.

## 2.0.5

**Supported platforms:** Minecraft 1.20.1 (Fabric, Forge) · Minecraft 1.21.1 (Fabric, NeoForge)

### Bug Fixes
* **`dimensions_settings.json` rewrite loop (#20)** — config no longer rewrites on every launch; Overworld minimum level of 0 and custom dimension entries are preserved. Removed leftover Forge Jauml dimensions bootstrap that re-seeded defaults.

### Upgrade Notes
1. Drop-in replacement for 2.0.4 on all four loader jars.

## 2.0.4

**Supported platforms:** Minecraft 1.20.1 (Fabric, Forge) · Minecraft 1.21.1 (Fabric, NeoForge)

### Features
* **Multiplicative attribute scaling** — new `multiplicative` mode: `baseValue * ((1 + valuePerLevel/100)^level - 1)`, capped by `maxValue`.
* **Proportional additive scaling** — optional `referenceBase` on attribute rules scales additive modifiers by `(baseValue / referenceBase)` for size-variant mobs (e.g. slimes).

### Bug Fixes / Compatibility
* **Touhou Little Maid garbled names (#12)** — level overlay hidden for `touhou_little_maid:` entities via `hideHudFor`; name formatting uses Component composition instead of flattening translated names.
* **MOTP player search (#14)** — nearest-player lookup limited to 128 blocks (`motpSearchRadius` in `scale_settings.json`); players with MOTP level ≤ 0 are skipped.

### Upgrade Notes
1. Replace previous jars with the matching 2.0.4 loader jar.
2. `global_settings.json` auto-adds `touhou_little_maid:` to `hideHudFor` on first launch if missing.
3. MOTP users can tune `motpSearchRadius` in `scale_settings.json` (default 128).

## 2.0.3

**Supported platforms:** Minecraft 1.20.1 (Fabric, Forge) · Minecraft 1.21.1 (Fabric, NeoForge)

### Bug Fixes
* **NeoForge 1.21.1 level overlays** — restored missing HUD rendering by flushing the font buffer (`endBatch`) and honoring `useLegacyHud` like other loaders.
* **Random load crash (`IPlatformHelper`)** — platform services now load lazily with the mod classloader and fall back safely instead of crashing during parallel mod init.
* **`scale_type = MOTP`** — reinstated Memories of the Past integration (nearest player `motp_level`); falls back to distance scaling with a one-time warning when the mod is absent.
* **Movement speed not scaling** — legacy `minecraft:generic.*` attribute IDs are resolved to modern registry names; existing configs auto-migrate on launch.
* **Slime split XP/level duplication (Forge 1.20.1)** — removed duplicate legacy XP handler that stacked rewards when slimes split on death.
* **Slime split leveling** — child slimes from splits no longer inherit stale parent level tags; each split recalculates its level.
* **Baby mob overlays** — improved nameplate height for baby mobs (zombies, animals, etc.).

### Skipped / Future
* **Relative danger colors (green/yellow/red)** — deferred; existing absolute level gradient remains.
* **#15 multiplicative attribute scaling** — feature suggestion, not a bug fix.
* **#12 Touhou Little Maid name garbling** — separate mod-compat issue.

### Upgrade Notes
1. Replace previous jars with the matching 2.0.3 loader jar.
2. MOTP users: keep `scale_type` as `MOTP` (case-insensitive); ensure Memories of the Past is installed.
3. If mobs still use old speed values, delete `attributes_settings.json` or let auto-migration rewrite attribute IDs on first launch.

## 2.0.1

**Supported platforms:** Minecraft 1.20.1 (Fabric, Forge) · Minecraft 1.21.1 (Fabric, NeoForge)

### Bug Fixes
* **Ranged / special attack scaling** — projectile damage (arrows, etc.) and Warden sonic boom now scale with mob level using the same attack-damage modifier ratio as melee. Melee is unchanged (still attribute-based, not double-scaled).

### Compatibility
* Drop-in replacement for 2.0.0 on all four loader targets.

### Upgrade Notes
1. Replace previous jars with the matching 2.0.1 loader jar.
2. No config changes required.

## 2.0.0

**Supported platforms:** Minecraft 1.20.1 (Fabric, Forge) · Minecraft 1.21.1 (Fabric, NeoForge)

### New features

- **Unified leveling architecture** — mob levels are calculated once on spawn through `LevelCalculationService`, with attribute scaling, classification, and storage handled by dedicated services.
- **RPG Attribute System integration** — optional player-based scaling (`nearest`, `highest_nearby`, `average_nearby`) and combat rebalance when `rpg_attribute_system` 4.1+ is installed.
- **Public API (`RmlApi`)** — other mods can read mob levels, force recalculation, and inspect level breakdowns.
- **Debug commands** — `/rml inspect`, `/rml recalculate`, `/rml config`, `/rml distance` (op level 2).
- **Percentage attribute scaling** — per-rule `additive` or `percent` modes in `attributes_settings.json`.
- **Gson JSON config** — all settings live under `config/rpgmoblevelingsystem/` (replaces Jauml on 1.20.1).
- **Namespace bans** — exclude entire mods (e.g. `cobblemon`) via `banned_namespaces`.
- **Hostile-only scaling** — optional global or per-rule hostile filter.
- **Structure & boss level locks** — `structureMinLevels` and `bossLevelLocks` in config.
- **Custom loot by level** — optional `loot_by_level.json` for level-band drops.
- **Configurable name format** — `%mob_name% [Lv. %level%]` in `global_settings.json`.
- **Ascendant elites** — ported to common code on all loaders (buffs, boss bar, death loot).
- **Documentation** — usage guide, API reference, and community feedback resolution report in `docs/`.

### Bug fixes

- **Distance scaling** — now uses Euclidean block distance divided by `scaleDistance` (was incorrectly using squared distance).
- **Day scaling** — uses total elapsed world time (`gameTime / 24000`) instead of wrap-sensitive day time alone.
- **Movement speed corruption** — attributes applied via idempotent named modifiers instead of mutating base values; speed skipped for non-hostiles by default.
- **Locked mob HP** — locked/boss mobs no longer keep incorrect stacked HP forever.
- **Zero XP in modded dimensions** — XP scaling works on Fabric (mixin) and NeoForge/Forge (event).
- **Ender Dragon / boss scaling** — dedicated boss lock defaults and locked-level path.
- **Overlay console spam** — removed periodic debug logging; optional `overlayDebug` flag.
- **Jade / name display** — overlay no longer overwrites mob custom names with `Lv.N`.
- **Performance** — levels applied once on spawn, never every tick; early exit for banned namespaces.
- **Low-level mobs vs high RAS players** — optional `playerBalanceEnabled` scales mob power relative to nearby player combat snapshot.

### Config changes

New / updated files in `config/rpgmoblevelingsystem/`:

| File | What's new |
|------|------------|
| `scale_settings.json` | `rounding_mode`, `playerScaleMode`, `playerScaleRadius`, `playerScaleFactor`, balance ratios |
| `attributes_settings.json` | `mode` (`additive` / `percent`), `hostileOnly` per rule |
| `mobs_list_settings.json` | `banned_namespaces`, `hostileOnly`, `bossLevelLocks`, `structureMinLevels` |
| `global_settings.json` | `nameFormat`, `jadeEnabled`, `overlayDebug` |
| `loot_by_level.json` | Level-band custom drops (disabled by default) |

Legacy configs with `scaleDistance >= 100000` and near-zero progression auto-migrate on first launch.

### Breaking changes

- **Config format** — JSON only in 2.0; Jauml string configs on 1.20.1 are no longer read.
- **Attribute application** — uses Minecraft attribute modifiers instead of permanent `setBaseValue` mutations. Existing worlds keep stored level tags; use `/rml recalculate` to refresh mobs if needed.

### For mod developers

```java
import tn.nightbeam.rpgmoblevelingsystem.api.RmlApi;

int level = RmlApi.getLevel(entity);
RmlApi.recalculate(entity);
LevelDebugBreakdown breakdown = RmlApi.getDebugBreakdown(entity);
```

See `docs/API.md` for full reference.

---

## 1.5.0

- Initial MultiLoader release for 1.20.1 (Forge + Fabric) and partial 1.21.1 port.
- Distance/time/random-based mob leveling with per-dimension min/max.
- Attribute scaling, level overlay, calendar item, Ascendant elites (1.20.1 Forge).
