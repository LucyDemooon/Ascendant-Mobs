# Remnants: RPG Mobs: the difficulty engine

Design for the 3.1 line: a world difficulty that rises with time, distance, depth and player power and drives the
existing levels and ranks; event nights; player-side progression; and loot that scales with difficulty. Branch:
`claude/difficulty-engine`. Status: **Phases 1–5 done: 1.20.1 (Fabric, Forge), 1.21.1 (Fabric, NeoForge), 26.1.2, 26.2 and 26.3 (Fabric,
NeoForge, Forge)** (see [Status](#status) at the end).

The guiding rule: **extend what 3.0 already has instead of building a parallel system.** 3.0 already turns "how
dangerous is this place" into a level (the *threat*), and level already drives rank odds. The difficulty engine
is a richer threat formula, not a second number fighting with the first.

---

## 1. What the repo looks like today

| | |
| --- | --- |
| Layout | One MultiLoader workspace per Minecraft version (`1.20.1/`, `1.21.1/`, `26.1.2/`, `26.2/`, `26.3/`), each with `common/` + two loader subprojects. No Architectury; code is copied between workspaces and adjusted per version (the `common/` trees differ by ~400 diff lines between 1.20.1 and 1.21.1). |
| Loaders | 1.20.1: Fabric + Forge 47 (ModDevGradle legacyforge). 1.21.1, 26.x: Fabric + NeoForge. **There is no Forge 26.x workspace.** (Phase 5 added a `forge/` subproject to each 26.x workspace.) |
| Java | 1.20.1 targets Java 17 (`java_version=17`, toolchain); 1.21.1 Java 21; 26.x Java 25. |
| Mod id | `remnant_mobs` (3.0). 2.x was `rpgmoblevelingsystem`; 3.0 migrates its configs and leveled mobs and refuses to load next to it. |
| Config | Own commented-JSON loader (`config/CommentedJson.java`): typed readers, comments regenerated, missing keys restored, `/remnants reload`. Files: `levels.json`, `ranks.json`, `traits.json`, `rewards.json`, `client.json` in `config/remnant_mobs/`. |
| jauml | **Not used by 3.0 at all.** 2.0 dropped it ("Gson JSON config ... replaces Jauml on 1.20.1"); no build file, `mods.toml` or `fabric.mod.json` references it. See §9. |
| Tests | JUnit tests in `1.20.1/common/src/test` (config + mob data). In-game QA over RCON: `tools/qa/run_all.py`, `server_smoke.py`. |
| Assets | Generated from code by `tools/assets/build.py` (procedural pixel art, synthesised sounds): original by construction. |

### Existing systems the engine plugs into

| System | Where | What it does |
| --- | --- | --- |
| Threat / levels | `mob/Threat.java`, `levels.json` | `band.min + distance + days + depth + moon`, structure minimum, then pulled toward nearby players' level (RAS or XP) by `players.weight`, clamped to the dimension band. Mob level = threat ± `spread`. |
| Ranks | `MobLeveler.rollRank`, `ranks.json` | Common/Veteran/Elite/Champion/Remnant. Chance × `(1 + level / 50)` × spawner × Hunter's Moon multipliers. |
| Traits | `trait/TraitEffects.java`, `traits.json` | 16 traits, rolled by rank. |
| Remnants | `RemnantBars`, `Titles` | Remnant rank: generated title, boss bar (yellow), horn, kill broadcast. |
| Hunter's Moon | `event/HuntersMoon.java`, `levels.json → hunters_moon` | On a full moon, a 35% chance: title + bell + chat at dusk, +5 levels, ×2 rank odds, ×2 shards until dawn. State in `<world>/remnant_mobs.json`. `/remnants moon [start\|stop]`. **It does not spawn anything or target players.** |
| Rewards / Valor | `reward/Rewards.java`, `rewards.json` | XP × level × rank, extra loot rolls, Remnant Shards, RAS Valor. |
| Nameplates | `client/Plate*.java`, `client.json` | Rank crest, level colour, **health bar** in rank colour with damage trail, traits. Already the "mob health bar". |
| Hunter's Lens HUD | `client/LensHud.java` | While holding the lens: threat where you stand. Already a difficulty readout, gated behind an item. |
| Commands | `/remnants` (alias `/rml`) | `threat`, `inspect`, `spawn`, `set`, `moon`, `reload`. |
| Network | `mob_info`, `player_state` payloads | Optional on the client (Forge channels accept missing; Fabric checks `canSend`). |

---

## 2. Already there vs. new

| Ask | Already in 3.0 | New work |
| --- | --- | --- |
| 1. World difficulty from days, distance, depth, player power, per dimension | days, distance, depth, RAS/XP level, per-dimension bands, structure minimums | **time played** as an alternative/extra clock, **gear score**, **curves** (linear/sqrt/log/power + cap) per factor, **per-dimension multiplier/offset/factor overrides**, difficulty → **rank odds** curve, dimension exclude list incl. dimension-type tags |
| 2. Player progression | — | Heart growth item, two healing items, always-on difficulty meter (HUD + command), meter in `player_state` |
| 2. Mob health bars | Nameplates with health bars | Optional bar for unranked mobs at range; nothing else |
| 3. Elites readable in 1 s | Rank colour on plate, Remnant boss bar + title | **Server-side** aura particles per rank, optional glow in rank colour, boss bars for Champion (configurable), event titles. All work for vanilla clients |
| 4. Event nights | Hunter's Moon (buff-only) | **Event system** (`events.json`): triggers, announcement, effects, **hunters + mini-boss that track players**, rewards, boss-tier hook. The Hunter's Moon stays as it is, listed alongside and never overlapped by a rolled event |
| 5. Loot scales with difficulty | Extra rolls per rank, shards | Extra rolls/shards scale with difficulty; optional Mob LootBags drops; loot beam |
| 6. Pack-maker controls | Commented JSON, entity tags (`bosses`, `never_leveled`, `never_ranked`), id/tag/namespace filters | `difficulty.json`, `events.json`, new entity tags, dimension-type tags, overlap detection |
| 7. Server-side first | Payloads optional | New features server-side; see §8 for the honest limit |
| 8. Backward compat | 2.x migration | Upgrade detection: new systems **off** when a 3.0 config exists |

---

## 3. The difficulty model

### 3.1 One number: difficulty is the threat

Difficulty is measured **in levels**, exactly like today's threat, so everything downstream (mob level, rank
odds, nameplate colours, rewards, RAS challenge) keeps working without translation. With the engine off
(`difficulty.json → enabled: false`) `Threat.at` runs the 3.0 formula unchanged. With it on:

```
place    = Σ factor_i                       (each factor: input → curve → cap, see 3.2)
           factors: days | played | distance | depth | gear
place    = (band.min + place) × dim.multiplier + dim.offset + event bonus
place    = max(place, structure minimum)
level    = place + (player level − place) × players.weight     (3.0 behaviour, RAS or XP)
level    = clamp(level, band.min, band.max)
```

- `days` and `distance` and `depth` default to the 3.0 linear values (`days_per_level 4, max 15`,
  `128 blocks/level`, `12 blocks/level below y 32, max 8`) so turning the engine on does not reshuffle a world;
  it **adds** `played` and `gear`.
- **Per-player inputs** (`played`, `gear`) are aggregated over players within `players.radius` with the existing
  `players.mode` (average / nearest / highest). No players nearby → 0.
- The **difficulty fraction** `f = (level − band.min) / (band.max − band.min)` (0…1) is what non-level systems
  use: rank odds, loot scaling, the meter's fill.

### 3.2 Curves

Every factor uses the same small shape, so pack makers learn it once:

```jsonc
"distance": { "enabled": true, "curve": "linear", "per": 128, "exponent": 1.0, "max": 40 }
```

`t = input / per`; levels = `linear: t`, `sqrt: √t`, `log: ln(1 + t)`, `power: t^exponent`; then `min(levels, max)`.
`max` ≤ 0 means uncapped. Inputs: days (overworld day count), played (hours of the player's own play time,
read from the vanilla `play_time` statistic, so no new save data), distance (overworld-scaled blocks from
spawn), depth (blocks below `start_y`), gear (gear score, 3.3).

### 3.3 Player power

- **Level**: the existing RAS / vanilla XP pull (`levels.json → players`). Unchanged.
- **Gear score** (new): `armor × 1 + toughness × 1.5 + max(0, attack_damage − 1) × 1 + max(0, max_health − 20) / 2`,
  read from the player's live attribute values (works for any modded gear, no item lists). Weights configurable.

### 3.4 Rank odds

The 3.0 `1 + level / level_chance_divisor` stays. With the engine on, rank chances are additionally multiplied by
`1 + (rank_odds.max_multiplier − 1) × curve(f)` (default max ×2 at full difficulty, `sqrt` curve), and by the
active event's `rank_multiplier`.

### 3.5 Per dimension

`difficulty.json → dimensions."<id>"`: `multiplier`, `offset`, and optional per-factor overrides (any key of a
factor). Defaults: Nether `multiplier 1.15`, End `1.25`. **Exclude** list: dimension ids or `#tag` of
`dimension_type` (datapack tag `data/<ns>/tags/dimension_type/…`; the mod ships
`#remnant_mobs:no_difficulty`, empty). Excluded dimensions use the 3.0 formula.

### 3.6 Entity controls (datapack tags)

| Tag | New? | Meaning |
| --- | --- | --- |
| `#remnant_mobs:never_leveled`, `never_ranked`, `bosses` | existing | unchanged |
| `#remnant_mobs:ignores_difficulty` | new | levels with the 3.0 formula even when the engine is on |
| `#remnant_mobs:event_hunters` | new | mobs an event may send after players (default: zombie, husk, skeleton, stray, spider, vindicator, pillager, witch) |
| `#remnant_mobs:event_mini_bosses` | new | mini-boss candidates (default: vindicator, wither skeleton, ravager… filtered by dimension) |

`dimension_type` tags: `#remnant_mobs:no_difficulty`, `#remnant_mobs:no_events`.

---

## 4. Event nights

### 4.1 What the Hunter's Moon does today

Buff-only full-moon night: chance roll once per full-moon night, announcement (title, sound, chat), +levels,
×rank odds, ×shards for mobs spawned while it is up, persistent across restarts, ends at dawn. No hunters, no
mini-boss, no rewards beyond more shards, nothing schedulable other than "full moon".

### 4.2 The event system

`events.json` holds a map of **event definitions**. The scheduler checks once a second; at most one event runs
at a time.

- The **Hunter's Moon** stays the 3.0 system, unchanged: its numbers stay in `levels.json → hunters_moon` (so
  existing configs keep working) and the `HuntersMoon` class keeps its state file and logic. It is **not** an
  entry of `events.json`; the event system lists it (`/remnants event list`) and a rolled event never starts while
  it is up. (An operator's `/remnants event start` can still overlap it on purpose.) Folding it into
  `events.json` later is possible but would move keys out of `levels.json`, so it waits for a major version.
- `grim_vigil` (new, original): every `every_days` nights (default 10, from day 7, chance 50%), a night where
  named elites and one mini-boss hunt each player.

```jsonc
"grim_vigil": {
  "enabled": true,
  "trigger": { "every_days": 10, "first_day": 7, "chance": 0.5, "min_difficulty": 0.0, "moon": "any" },
  "announce": { "title": true, "chat": true, "sound": true },
  "effects": { "level_bonus": 3, "rank_multiplier": 1.5, "shard_multiplier": 1.5 },
  "hunters": { "waves": 3, "per_wave": 3, "wave_seconds": 90, "first_wave_seconds": 20, "min_rank": "veteran",
               "named": true, "distance_min": 20, "distance_max": 32, "max_alive": 6 },
  "mini_boss": { "enabled": true, "per_player": false, "rank": "remnant", "level_bonus": 5, "tier": "mini" },
  "rewards": { "survive_experience": 50, "survive_valor": 40, "boss_shards": 8 },
  "dimensions": ["minecraft:overworld"]
}
```

- **Hunters** are picked from `#remnant_mobs:event_hunters`, placed on valid spawn spots 20–32 blocks from the
  player (vanilla spawn placement rules and collision, inside the world border, surface or cave depending on where
  the player is), given at least the event's minimum rank and a generated title (the existing `Titles`
  generator), and re-aimed at their player every second (monsters normally drop targets beyond follow range).
  They carry the scoreboard tags `remnant_mobs.event` and `remnant_mobs.event.<id>`, so datapacks can react.
  Players in creative or spectator are not hunted. Waves wait until someone is online to hunt.
- The **mini-boss** arrives with the last wave, after one random hunted player (or each, with `per_player`), at
  the event's boss rank (Remnant by default), so it gets the existing boss bar, horn and kill broadcast for free.
- **Rewards**: surviving to dawn grants XP (and Valor through the existing RAS bridge if present); the mini-boss
  drops `boss_shards` extra shards. Event mobs multiply shards by `shard_multiplier`.
- **Boss-tier hook**: `mini_boss.tier` names an entry in a `BossTiers` registry (`api/BossTier`). Phase 1 ships
  `mini` (a ranked vanilla mob). Remnant Bosses or a later phase can register `major`, `raid` etc. through
  `RemnantMobsApi.registerBossTier(id, spawner)` without changing the event format.
- Commands: `/remnants event` (status), `/remnants event list`, `/remnants event start <id>` (op; runs even
  while `events.json` has events switched off, so pack makers can try a night before enabling the schedule),
  `/remnants event stop` (op). `/remnants moon …` is unchanged.
- State persists in `<world>/remnant_mobs_events.json` (separate from the Hunter's Moon file so 3.0 worlds
  load untouched).

Clip value for Shorts/Reels: red title card, horn, the named hunters' boss bar, and a server-wide line when the
mini-boss falls. All of it is vanilla packets, so it records the same on a vanilla client.

---

## 5. Player-side progression (Phase 2, done)

All original names and art, generated by `tools/assets` (procedural pixel art, like the 3.0 items).
Configured in **`progression.json`** (new file, same migration rule as Phase 1).

| Item | Effect | Source |
| --- | --- | --- |
| **Vital Ember** | Use: +1 heart max health for good (attribute modifier `remnant_mobs:vital_growth`), up to +10 hearts; kept on death (config). Animated texture | Elite 2%, Champion 15%, Remnant 60%, event mini-boss 100% |
| **Mending Salve** | Use: heals 6 HP over 3 s; 10 s cooldown | Veteran and above, 6% |
| **Hunter's Draught** | Drink (1.2 s): heals 40% of max health, then Regeneration I for 5 s; 30 s cooldown; stacks to 4 | Elite and above, 8% |

- Drops need a player kill and `enabled: true`; every chance is multiplied by `1 + difficulty_bonus x difficulty`
  (difficulty = the mob's level across its dimension's band, 0 to 1), so harder places pay more.
- **No crafting recipes yet**: recipes are datapack files that would appear on upgraded installs too, and making
  them conditional needs a different mechanism per loader. Owner question Q9.
- `/remnants hearts [player]` shows extra hearts; `/remnants hearts <player> set <n>` (operators) sets them.
- Hearts are carried to the new player on respawn (Forge/NeoForge `PlayerEvent.Clone`, Fabric
  `ServerPlayerEvents.COPY_FROM`); after a death only with `keep_on_death`.
- **Difficulty meter**: `/remnants difficulty [player]` works for everyone, unmodded clients included. Clients with
  the mod draw a small gauge (skull, percentage, a bar from green to deep red, "+N" on event or moon nights), fed by
  a **new optional payload** `remnant_mobs:difficulty` sent once a second only when it changes. Placement
  (`meter.anchor`, `x`, `y`, `hud`) is read from each player's own `progression.json`; it sits bottom right above
  the Hunter's Moon icon by default. A separate payload from `player_state`, so 3.0 clients are unaffected.
- **Health bars**: the 3.0 nameplates already draw one in the rank colour. The planned `bar_only_distance` option
  was dropped: it needed a new key in `client.json`, which would rewrite a 3.0 file.

## 6. Elites readable in one second (Phase 2, done)

In **`elites.json`** (new file, same migration rule). All of it is vanilla particles, boss bars, names and effects,
so players without the mod see it too (verified with an unmodded client):

- **Aura**: dust in the rank's colour around Elite and above twice a second (Champion and Remnant: a turning ring
  at the feet; Remnant: soul-fire wisps above the head), sent to players within 24 blocks. `aura.min_rank`.
- **Boss bars**: Champions get a bar too (purple by default); Remnants keep yellow; an event night's mini-boss is
  red. Colours configurable. With `elites.json` off, bars are exactly as in 3.0.
- **Titles**: `champion_titles` gives Champions a generated title too (off by default).
- **Event glow**: monsters an event night sends glow until dawn (vanilla Glowing effect, white outline). The
  team-colour variant was dropped: scoreboard teams would collide with packs' own teams.

## 7. Loot that scales (Phase 3, done)

In **`loot.json`** (new file, same migration rule: off next to an existing config, so drops stay exactly as in
3.0). Difficulty is the monster's level across its dimension's band, 0 to 1.

- **Scaling**: ranked monsters roll their loot table `loot_rolls x (1 + extra_rolls_bonus x difficulty)` extra
  times (default up to double; a fraction is the chance of one more roll) and drop
  `shards x (1 + shards_bonus x difficulty)` Remnant Shards (up to +50%). Unranked monsters get
  `common_extra_roll x difficulty` chance (up to 15%) of one more roll.
- **Mob LootBags** (soft dependency): detected by mod id `mob_loot_bags`. Ranked monsters killed by a player may drop
  a bag **on top of** that mod's own drops (which already scale with the monster's max health, so leveled mobs
  already pay better there): Elite 10% Uncommon, Champion 30% Rare, Remnant 100% Epic, event mini-boss 100%
  Legendary; from difficulty 0.66 one tier better. Bags are made and dropped through Mob LootBags' own
  `DropEngine.createBag`/`spawnBag` (reflection, no compile dependency either way), so they get its dimension themes,
  bag statistics, drop sound and particles, announcements and client loot beams. If that code is not there (Mob
  LootBags 1.x), the bag is dropped by item id (`loot_bags.items`) instead. The ids were read from the Mob LootBags
  repository (`BagType`: `commonlootbag` … `mythiclootbag`, identical on 1.20.1, 1.21.1 and 26.x and in 1.x).
- **Loot beams**: a column of dust in the item's rarity colour (vanilla's: yellow, aqua, light purple; default
  `min_rarity: rare`) over what a ranked monster drops, for 30 s or until picked up, within 48 blocks. Sent from the
  server, so every player sees it; Mob LootBags bags are skipped because that mod draws its own beam. Cost: one
  particle packet per beam per nearby player every 5 ticks, at most 128 beams.
- **Bug fix found while testing (3.0)**: the Volatile trait's death blast destroyed the monster's own drops
  (explosions kill item entities), so Volatile Remnants dropped nothing. Item entities in the blast's reach are now
  invulnerable for the instant of the explosion; the blast still hurts players and pushes items. This fix applies
  regardless of `loot.json` (see Decisions).

## 8. Server-side first: what testing showed

- Everything Phase 1 adds (difficulty, events, commands, hunters, titles, boss bars) is server-side, adds **no
  registry entries and no payloads**, and uses vanilla packets. New messages use `translatableWithFallback`, so
  unmodded clients read English instead of raw keys; the generated advancements and the Remnant boss bar level
  now carry fallbacks too.
- Smoke tests with an **unmodded client** (a mineflayer bot, see Status) on dev servers with the mod installed:
  - **Forge 1.20.1** and **Fabric 1.20.1**: the client joins and plays through a full event night.
  - **NeoForge 1.21.1**: in 3.0 the **server crashed** as soon as a leveled mob near an unmodded client got its
    data (`sendToPlayersTrackingEntity` throws for clients without the channel). Fixed on this branch; the client
    then plays through a full event night with no server exception. **The same call is in the 26.1.2, 26.2 and 26.3
    NeoForge workspaces and needs the same fix** (Phase 4).
- What unmodded clients still cannot get: the nameplates, the Hunter's Lens HUD and textures/names of the three
  3.0 items (Remnant Shard, Hunter's Lens, Brand of Challenge). Item handling for unmodded clients was not tested
  (the bot never held one); on NeoForge, registered items normally require the mod on the client, so the
  **metadata stays "both sides"** (`environment: "*"`, `side = "BOTH"`), and the client part is "recommended",
  not "required, or you cannot join". A fully vanilla-joinable "server edition" without registered items is open
  question Q3.
- With **Mob LootBags** installed, unmodded clients cannot join a NeoForge server at all: that mod's network channels
  are required (its README: "needed on both the server and the client"). Remnants' own parts stay optional.

## 9. jauml bundling

**Finding: 3.0 does not depend on jauml.** Modpacks updating from 2.x→3.x no longer need it for this mod.
Recommendation: do **not** add it back. If the studio wants a shared library for the other ~13 mods:

| Loader | Jar-in-jar | Risk for the other mods |
| --- | --- | --- |
| Fabric | `include(modImplementation(...))` | Loader picks the highest nested version across all mods; safe if jauml stays backward compatible. |
| Forge 1.20.1 (JarJar) | `jarJar(...)` with a range `[x,)` | Forge picks one version satisfying every declared range; a mod pinning `[x]` exactly breaks resolution. Use open ranges in every mod. |
| NeoForge | `jarJar(...)` same model | Same; plus the standalone jauml jar in a pack must have the same mod id/version scheme or both load. |

Rule if bundling later: open version ranges everywhere, never change the mod id, keep publishing the standalone jar.

## 10. Coexistence with other elite-mob mods

`difficulty.json → compat.overlap_mod_ids`: mod ids of affix/elite mods that overlap our traits. When one is
loaded and `compat.disable_overlaps` is true, **trait rolls are turned off** (ranks, levels, events stay — they
do not overlap) and a log line says why. Default ids: the two most widespread elite-affix mods (see Q6). On
upgraded installs `disable_overlaps` defaults to `false` (packs keep current behaviour).

## 11. Config schema

New files only — **no new keys are injected into the five 3.0 files**, so their text stays byte-identical:

- `config/remnant_mobs/difficulty.json`: `enabled`, `factors` (days, played, distance, depth, gear),
  `gear_score`, `rank_odds`, `dimensions`, `exclude_dimensions`, `compat`.
- `config/remnant_mobs/events.json`: `enabled`, `events.<id>` as in §4.2.
- `config/remnant_mobs/progression.json` (Phase 2): `enabled`, `hearts`, `drops`, `healing`, `meter`.
- `config/remnant_mobs/elites.json` (Phase 2): `enabled`, `aura`, `boss_bars`, `champion_titles`, `event_glow`.
- `config/remnant_mobs/loot.json` (Phase 3): `enabled`, `scaling`, `loot_bags`, `beams`.

### Migration defaults (requirement 8)

On load, for each **new** file that does not exist yet:

| Situation | Detected by | New systems |
| --- | --- | --- |
| Fresh install | no `config/remnant_mobs/` *and* no `config/rpgmoblevelingsystem/` | **on** |
| Upgrade from 3.0 | `config/remnant_mobs/levels.json` exists | **off** (`enabled: false`, `compat.disable_overlaps: false`) |
| Upgrade from 2.x | `config/rpgmoblevelingsystem/` exists | **off** |

Once written, the file is the user's; the rule never runs again. The decision is logged ("Existing config found:
systems new in 3.1 start switched off…") and each file's header explains the rule. Worlds: no change to the mob data tag format, no
change to `remnant_mobs.json`; event state goes to a new file.

### Migration note (for the changelog)

> **Upgrading from 3.0 or 2.x?** Nothing changes until you opt in. Remnants finds your existing config and
> creates `difficulty.json`, `events.json`, `progression.json`, `elites.json` and `loot.json` with every new
> system switched off. Set `enabled: true` in each to try them; `/remnants reload` applies it. Fresh installs start with them on. The Hunter's Moon, levels, ranks,
> traits and rewards behave exactly as before either way.

## 12. Minecraft 26.4 overlap

26.4 (Q4 2026) adds a Freezing effect and a frost zombie.

- No new frost mechanics anywhere in this engine.
- **Done (Phase 4):** on 26.1.2, 26.2 and 26.3 the **Frostbite** trait looks up `minecraft:freezing` in the effect
  registry (`compat/VanillaFrost`). If the game has it, Frostbite applies it (for `slowness_seconds` at
  `slowness_level`) instead of Slowness + freeze ticks; if not, 3.0 behaviour, verified on 26.3 (Slowness II for 3 s
  and 100 freeze ticks). The id is a single constant **to confirm against 26.4** (snapshots may name it otherwise).
- **Not done:** the frost zombie's id is not known yet, so it is not in `#remnant_mobs:event_hunters`. When 26.4 is
  out, add it to `tools/assets/data.py` as `{"id": "minecraft:<id>", "required": false}` so the same data still
  loads on 26.1–26.3.

## 13. Phases

| Phase | Scope | Targets |
| --- | --- | --- |
| **1** | Difficulty engine (factors, curves, dimensions, rank odds), `difficulty.json` + `events.json` with migration defaults, `/remnants difficulty`, `/remnants event …`, event system with `grim_vigil` (Hunter's Moon unchanged, listed alongside), new tags, unit tests | 1.20.1 (Forge first, Fabric shares `common/`), then 1.21.1 (NeoForge, Fabric) |
| **2** | Vital Ember, healing items, difficulty payload + HUD gauge, elite readability (aura, bars, titles), overlap detection polish | same |
| **3** | Loot scaling, Mob LootBags soft dep, loot beam | same |
| **4** | Port to 26.1.2, 26.2, 26.3 (Fabric, NeoForge); 26.4 freezing hook (done) | 26.x |
| **5** | Forge 26.x: a `forge/` subproject in each 26.x workspace (ForgeGradle 7, Forge 64–66) (done) | 26.x |
| later | KubeJS hooks, more boss tiers | — |

## 14. Open questions for the owner

1. **Mod id / project.** 3.0 already changed the mod id from `rpgmoblevelingsystem` to `remnant_mobs` and the
   `fabric.mod.json` homepage still points to `rpg-mob-leveling-system` on Modrinth. Has 3.0 been published to the
   `remnants-rpg-mobs` projects? The 143 packs on 2.x will see a new mod id on update no matter what this branch
   does; 3.0's 2.x config/mob migration covers worlds, but pack scripts referencing `rpgmoblevelingsystem` will not.
2. **Clock for difficulty.** Default: world days (as 3.0) **plus** each player's own play time at a smaller
   weight. Should difficulty ever go **down** (on death, with an item)? Not in the design today.
3. **Server edition.** Unmodded clients already join Forge/Fabric 1.20.1 and (after this branch's fix) NeoForge
   1.21.1. Do you also want a jar without registered items so nothing on the server can trip them up (§8)?
4. **Event cadence.** `grim_vigil` every 10 nights, 50% chance, from day 7: too much or too little for packs?
   Should it refuse to start while a player is in a bed / AFK?
5. **Names.** Proposed original names: *Grim Vigil* (event), *Vital Ember*, *Mending Salve*, *Hunter's Draught*.
   OK, or a different naming family?
6. **Overlap detection.** The rules say not to name other mods anywhere, but detecting them needs their mod ids.
   The design keeps the two ids only as default values of `compat.overlap_mod_ids` (a config value, never shown in
   docs or descriptions). Accept, or ship the list empty and let pack makers fill it?
7. ~~Mob LootBags item ids~~: resolved in Phase 3 by reading that repository (§7).
9. **Recipes for the new items.** Drops only for now (§5). Should the Salve and Draught be craftable, and should
   Vital Embers stay drop-only (they are the progression gate)?
8. **Java for 1.20.1.** The workspace targets Java 17 (the task brief said 21). Keep 17 (wider compatibility)?

## Status

_As of 2026-10-29, branch `claude/difficulty-engine`. **3.1.0 is published** on Modrinth and CurseForge (13 files,
the `Publish` workflow run on this branch, jars in `releases/`). Nothing has been tagged or merged into `main`._

### Done

| | 1.20.1 (Forge, Fabric) | 1.21.1 (NeoForge, Fabric) |
| --- | --- | --- |
| Difficulty engine: curved factors (days, played, distance, depth, gear), per-dimension multiplier/offset/overrides, exclusions by id or `dimension_type` tag, `#remnant_mobs:ignores_difficulty`, rank odds from difficulty | ✅ | ✅ |
| `difficulty.json` and `events.json` with the migration rule (off next to a 3.0 or 2.x config, on for fresh installs); the five 3.0 files untouched | ✅ | ✅ |
| Event nights: scheduler, Grim Vigil, waves of named ranked hunters, mini-boss through the `BossTier` hook, dawn rewards, *See the Dawn* advancement, state in `remnant_mobs_events.json`; Hunter's Moon unchanged | ✅ | ✅ |
| `/remnants difficulty [player]`, `/remnants event [list\|start <id>\|stop]` | ✅ | ✅ |
| Trait suppression next to listed overlapping mods (`compat`) | ✅ | ✅ |
| API: `getDifficulty`, `getActiveEvent`, `registerBossTier`; tags; English fallbacks | ✅ | ✅ |
| Fix: NeoForge server crash with unmodded clients online | n/a | ✅ |
| Docs: this file, `USAGE.md`, `API.md`, `patchnotes/3.1.0.md` (draft with the migration note) | ✅ | ✅ |

### Tested

- `./gradlew build` passes in `1.20.1/` (common, Fabric, Forge) and `1.21.1/` (common, Fabric, NeoForge). It was
  built with JDK 21; Gradle provisioned the Java 17 toolchain for 1.20.1.
- Unit tests: 19 per workspace, all passing (10 existing + 9 new in `DifficultyTest`: curves, overrides, defaults
  mirroring `levels.json`, gear score, rank odds, event schedule, fresh install on, 3.0 upgrade off with files
  byte-identical, 2.x upgrade off). The first run caught a real bug (rank-odds curve read as disabled).
- Dev dedicated servers, driven over RCON (`tools/qa/rcon.py`), with a mineflayer bot as an unmodded player:
  - **Forge 1.20.1**: commands; fresh-install configs on; **upgrade**: with the 3.0 files kept and the new files
    deleted, the restart logs "Existing config found…", creates both new files with `enabled: false`, leaves the
    five 3.0 files byte-identical (md5) and threat unchanged; full Grim Vigil (3 waves, named Veteran+ hunters,
    Remnant mini-boss with boss bar, cap on hunters alive); dawn reward (+50 XP, advancement, message); event state
    survives a restart; operator start works with events off.
  - **NeoForge 1.21.1**: found and fixed the unmodded-client crash, then a full Grim Vigil with 0 exceptions.
  - **Fabric 1.20.1**: full Grim Vigil, boss bar reads "Lv. 14" on the unmodded client, 0 exceptions.
  - Not run: Fabric 1.21.1 in game (it builds), the QA client with nameplates (no change to client code), the
    existing `server_smoke.py` suite, RAS present.
- Smoke tests found and fixed two event bugs: an operator start ended at once while events were off, and waves
  were used up while nobody was online.

### Phase 2 (2026-10-29)

| | 1.20.1 (Forge, Fabric) | 1.21.1 (NeoForge, Fabric) |
| --- | --- | --- |
| `progression.json` and `elites.json` with the migration rule | ✅ | ✅ |
| Vital Ember, Mending Salve, Hunter's Draught: items, original textures, models, lang, creative tabs | ✅ | ✅ |
| Difficulty-scaled drops from ranked kills and the event mini-boss | ✅ | ✅ |
| Hearts kept on respawn; `/remnants hearts`; *Kindled* advancement | ✅ | ✅ |
| `remnant_mobs:difficulty` payload and HUD gauge | ✅ | ✅ |
| Rank aura, Champion boss bars, bar colours, Champion titles option, event glow | ✅ | ✅ |

Tested:
- `./gradlew build` passes in both workspaces; **24 unit tests** each, all passing (5 new in `ProgressionTest`:
  config defaults, drop-chance curve, heart cap, elites defaults and bad values, migration rule for both new files).
- **Forge 1.20.1** dev server with the unmodded bot: 3 Embers took max health 20 → 26 with a message and the
  *Kindled* advancement (shown with its English fallback); `/remnants hearts … set 10` then `kill`: the player
  respawned at 40/40; set 0 back to 20. A Champion killed by the player dropped an Ember, a Salve, a Draught and
  shards (chances raised to 1.0 for the test); its boss bar was purple and read "Lv. 30". Aura particles reached
  the client (≈20/s for one Remnant + one Elite) and stopped right after `elites.json` was switched off and reloaded.
- **NeoForge 1.21.1**: the same Ember, respawn (24 kept), drop, purple bar and particle checks, 0 server
  exceptions; Mending Salve healed ≈6 over 3 s; Hunter's Draught healed 40% and applied Regeneration.
- Not tested in game: the HUD gauge itself (needs a modded client; the payload is only sent to clients that have the
  channel, so the unmodded bot never got it) and Fabric 1.21.1 (builds; shares all common code).

### Phase 3 (2026-10-29)

| | 1.20.1 (Forge, Fabric) | 1.21.1 (NeoForge, Fabric) |
| --- | --- | --- |
| `loot.json` with the migration rule | ✅ | ✅ |
| Extra loot rolls and shards scale with difficulty; extra roll chance for unranked monsters | ✅ | ✅ |
| Mob LootBags bags from ranked kills (its own bag code, item-id fallback), tier up at high difficulty | ✅ | ✅ |
| Server-side loot beams over rare drops | ✅ | ✅ |
| Fix: Volatile no longer destroys the drops of the monster that exploded | ✅ | ✅ |

Tested:
- `./gradlew build` passes in both workspaces; **29 unit tests** each, all passing (5 new in `LootTest`: defaults,
  scaling maths, bag tier-up and bad values, fractional rolls as chances over 10,000 draws, migration rule).
- **Fabric 1.20.1** + Mob LootBags 2.0.0, unmodded bot as the player: "Mob LootBags found" in the log; an Elite at
  level 58 (difficulty 0.97) dropped a **Rare** bag (Uncommon + 1 tier); a Remnant dropped an **Epic** bag
  through Mob LootBags' own code. Volatile Remnant on a closed platform: 5 drops before the blast, **0 after** on
  the 3.0 code, **5 after** with the fix (Vampiric control: drops kept either way). Loot beam: 51 extra dust
  packets reached the bot in 12 s over a Vital Ember, then stopped when the item was gone.
- **Fabric 1.20.1** + Mob LootBags **1.11.2** (no `DropEngine`): the bridge logs that it falls back, and the Epic
  bag still drops by item id.
- **NeoForge 1.21.1** + Mob LootBags 2.0.0 with the **real QA client** (Xvfb, software rendering): a level-40
  Remnant (difficulty 0.66) dropped a **Legendary** bag, a Vital Ember and shards despite being Volatile. Screenshot:
  our aqua beam over the Ember next to Mob LootBags' gold beam over its bag, and the **difficulty gauge** (Phase 2)
  at "0%" near spawn and "86% +3" (deep red) 6,000 blocks out during a Grim Vigil, plus the extra heart row.
  0 server exceptions, no client exceptions besides the missing sound device.
- Not tested in game: Forge 1.20.1 and Fabric 1.21.1 with Mob LootBags (same common code and bridge).

### Phase 4 (2026-10-29)

| | 26.1.2 | 26.2 | 26.3 |
| --- | --- | --- | --- |
| Phases 1–3 (all five new config files, engine, events, progression, elites, loot, commands, API) on Fabric and NeoForge | ✅ | ✅ | ✅ |
| NeoForge unmodded-client crash fix, Volatile drops fix (own commit, for a 3.0.1 hotfix) | ✅ | ✅ | ✅ |
| Frostbite uses vanilla Freezing when present (26.4), 3.0 behaviour before | ✅ | ✅ | ✅ |

How: a three-way merge of this branch's 1.21.1 changes into each 26.x workspace (5 conflicts each, resolved by
hand), then the 26.x API changes (`Identifier`, jspecify, `entityTags()`, `level()`, `snapTo`, `EntitySpawnReason`,
`spawnAtLocation(level, stack)`, `DustParticleOptions(rgb, scale)`, two-flag `sendParticles`, `getTagOrEmpty`,
`GuiGraphicsExtractor`, `InteractionResult`, Consumer tooltips, `ItemUseAnimation`, stack cooldowns,
`sendOverlayMessage`; Fabric `CreativeModeTabEvents`/`clientboundPlay`; NeoForge client payload handlers). The
versions differ from each other only where Minecraft does: `EntityTypes` exists from 26.2, and 26.3 renamed
`setInvulnerable` to `setPermanentlyInvulnerable`.

Tested:
- `./gradlew build` passes in `26.1.2/`, `26.2/` and `26.3/` (common, Fabric, NeoForge); **29 unit tests** each, all
  passing. **Building 26.x needs Gradle to run on Java 25** (Loom 1.18 refuses Java 21); a Temurin 25.0.5 was used.
- **NeoForge 26.3 + Mob LootBags 2.0.0 with the real QA client** (Xvfb; the 26.3 client needs EGL, so the container
  needed `libegl1`): a level-40 Volatile Remnant dropped a **Legendary** bag (bridge, tier up), a Vital Ember and
  shards, all surviving the blast; Frostbite on the player: Slowness II 60 ticks + 100 freeze ticks (no Freezing
  effect in 26.3); a real right-click on a Vital Ember: 2 → 3 extra hearts, max health 26; Grim Vigil: 3/3 waves,
  7 event monsters, glowing hunters, the mini-boss, and the gauge reading "14% +3" on screen. 0 server exceptions.
- Dev servers, console checks (event list/start/stop, ranked spawns, threat), 0 exceptions each:
  **NeoForge 26.2 as an upgrade** (3.0 `levels.json` present: "Existing config found", all five new files
  `enabled: false`, `levels.json` byte-identical), **NeoForge 26.1.2**, **Fabric 26.1.2, 26.2 and 26.3** (fresh: all on).
- Not tested in game: the 26.4 Freezing branch (no 26.4 yet), a real client on 26.1.2/26.2 or on Fabric 26.x.

### Phase 5 (2026-10-29)

| | 26.1.2 | 26.2 | 26.3 |
| --- | --- | --- | --- |
| `forge/` subproject (ForgeGradle 7.0.40): Forge 64.1.5 / 65.1.5 / 66.1.0, jar `remnant_mobs-forge-<mc>-<version>.jar` | ✅ | ✅ | ✅ |
| Everything from Phases 1–4 (the common code is shared unchanged; the three Forge entry points are identical) | ✅ | ✅ | ✅ |

No new workspace was needed: Forge sits next to Fabric and NeoForge in each 26.x workspace. What is Forge-specific:
- **Events**: EventBus 7 (one static bus per event). **Bug found and fixed:** the death hook was first a monitor
  listener, but EventBus 7 never calls monitors on a bus that has no ordinary listener, which is the case for deaths
  unless another mod listens too. Volatile blasts, kill rewards and Remnant announcements silently did nothing. It is
  now a lowest-priority listener, which still skips deaths another mod cancelled (same as NeoForge's `LOWEST`).
- **Network**: the payload channel is `optional()`, so clients without the mod still join; payloads only go to
  clients that have the channel.
- **HUD gauge**: Forge draws the HUD through its own layers, so the HUD mixin (already optional, `require = 0`) never
  runs there; the gauge is a Forge overlay layer instead.
- **Fake players**: Forge 64–66 has no `FakePlayer` class; any `ServerPlayer` subclass counts as one.
- **Mixins**: the jar lists them in its manifest (`MixinConfigs`); dev runs pass `--mixin.config`.
- **Build**: ForgeGradle 7 needs Gradle 9.3+, so the **26.1.2 workspace moved from Gradle 9.2.0 to 9.5.0** (what
  26.2 and the Forge MDKs use). `tools/qa/devrun.sh stop-server` also recognises Forge 26 dev servers.

Tested:
- `./gradlew build` passes in `26.1.2/`, `26.2/` and `26.3/` with all three loaders; **29 unit tests** each, all
  passing.
- `tools/qa/server_smoke.py` on dev servers: **Forge 26.1.2, 26.2 and 26.3: 0 failures** (the first 26.2 run had 3,
  which is how the death-hook bug was found; 0 after the fix). **Fabric and NeoForge 26.1.2** again after the
  Gradle bump: 0 failures. Forge fresh installs create all 10 config files; event list/start/status/stop works;
  vanilla-summoned mobs get levels.
- `tools/qa/client_checks.py` with the **real QA client on Forge 26.1.2 and 26.2: 0 failures** (rank nameplates,
  damage trail, Hunter's Lens, Brand, shards, Remnant and Champion boss bars, Hunter's Moon), with the difficulty
  gauge on screen ("8% +5" under the Hunter's Moon).
- **Forge 26.3 with the real QA client**: a Vital Ember took extra hearts 2 → 3 (max health 26) and granted
  *Kindled*; Frostbite applied Slowness II and 96 freeze ticks; during a Grim Vigil the Champion boss bar, the
  nameplates and the gauge ("22% +8") were on screen, with no mixin errors. That session ran before the death-hook
  fix, so the Volatile blast on Forge is covered by the smoke test only (the drop-sparing code is common code, tested
  in game on Fabric 1.20.1 and NeoForge 26.3).
- Not tested on Forge 26.x: Mob LootBags (it has no Forge 26.x build), RPG Attribute System, clients without the
  mod (the mineflayer bots do not support 26.x), and the 3.0 upgrade path (same common code as the NeoForge 26.2
  upgrade test).
- Found and fixed (a 3.0 bug, not caused by this branch): on **26.1.2 (every loader)** nameplate text seen at an
  angle broke up into dark blocks, worst on the bold level number, also on the rank crest. 26.1.2 draws a text
  outline at the depth of the text and its depth buffer cannot tell them apart from a few blocks away (26.2 moved to
  a reverse depth buffer, so 26.2 and 26.3 are clean). Plates now draw each outline on its own first, then the text
  just in front of it, by about eight depth steps at that distance. Checked with the real QA client from both
  diagonals and head-on at 6–28 blocks (plates at scale 2 to keep the text resolvable), at the screen edge and in the
  Hunter's Moon scene where it showed first: all clean, `client_checks.py` 0 failures. 1.21.1 under the same sweep
  was already clean (its bold glyphs do not sit in front of their outline), so nothing changes there.

### Next

1. **26.4**: when it ships, confirm the Freezing effect id, add the frost zombie to `event_hunters`, and add a 26.4
   workspace (`tools/port_setup.py`). The script copies `common/`, `fabric/` and `neoforge/` only: copy `26.3/forge/`
   and its lines in `settings.gradle`, `build.gradle` and `gradle.properties` by hand.
2. Run the full `tools/qa/run_all.py` suite on every target with a real client, once with RAS.
3. After the 3.1.0 release: merge this branch into `main` (the released code and jars are only here so far), and
   update the store pages: the description in `marketing/` still describes 3.0 and lists Fabric and NeoForge only
   for 26.x, and the workflow uploads files, not page text.

Release gate for 3.1.0 (2026-10-29): `./gradlew clean build` in all five workspaces (29 tests each), each jar's
mappings checked against its 3.0.0 counterpart, then a fresh dev server and `server_smoke.py` for all 13 shipped
version/loader pairs: 0 failures.

### Decisions for the owner

The questions in §14, most urgent first: **Q1** (mod id / which project 3.x ships to; the 143 packs are on 2.x),
**Q6** (overlap mod ids in config vs. no detection), **Q4** (Grim Vigil cadence), **Q5** (names), **Q9**
(recipes for the new items), **Q3** (server edition), **Q2** (should difficulty ever go down), **Q8** (Java 17 for
1.20.1). Plus two fixes that change 3.0 behaviour on every install, not only with the new systems on: the
**NeoForge crash with unmodded clients** and **Volatile destroying its own drops**. Both look like 3.0.1 hotfix
material for every version, independent of 3.1, and so is the 26.1.2 nameplate fix (its own commit).

From Phase 5: whether the Forge 26.x jars ship with 3.1 or wait until Mob LootBags and RPG Attribute System are
checked on Forge 26.x (both are optional; Remnants runs without them).
