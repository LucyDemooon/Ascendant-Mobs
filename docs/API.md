# Remnants: RPG Mobs 3.0: API

For other mods, datapacks and modpacks. Everything here is the same on every supported Minecraft version unless
noted.

## RemnantMobsApi

`com.nightbeam.remnants.mobs.api.RemnantMobsApi`. Reads work on both sides (a client knows the mobs it can see);
changes run on the server thread.

```java
int getLevel(Entity entity);                         // -1 when Remnants has not leveled it
String getRank(Entity entity);                       // "common", "veteran", "elite", "champion", "remnant"; "" when not leveled
Set<String> getTraits(Entity entity);                // trait keys such as "vampiric"
boolean isBranded(Entity entity);                    // marked with a Brand of Challenge
void setLevel(Mob mob, int level);                   // keeps rank and traits, re-applies stats (server)
int getThreat(ServerLevel level, BlockPos pos);      // the level mobs would spawn at there, players included (server)
boolean isHuntersMoon();                             // (server)
double getDifficulty(ServerLevel level, BlockPos pos); // 0-1, where the threat sits in the dimension's band (server, 3.1)
String getActiveEvent();                             // id of the running event night, "" when none (server, 3.1)
void registerBossTier(String id, BossTier tier);     // a boss tier events can name in mini_boss.tier (3.1)
```

Compile against the jar for your version, or call it through reflection to keep Remnants optional.

## Keys

- Ranks: `common`, `veteran`, `elite`, `champion`, `remnant`.
- Traits: `vampiric`, `frostbite`, `infernal`, `venomous`, `withering`, `berserk`, `crushing`, `juggernaut`,
  `thornskin`, `warded`, `regenerating`, `deflecting`, `swift`, `blinking`, `packleader`, `volatile`.

## Entity type tags

| Tag | Meaning |
| --- | --- |
| `#remnant_mobs:bosses` | Shown with a level but never ranked; stats only scale with `mobs.boss_stats`. Includes `#c:bosses` |
| `#remnant_mobs:never_leveled` | Never leveled |
| `#remnant_mobs:never_ranked` | Leveled but always Common (vexes, silverfish and endermites by default) |
| `#remnant_mobs:ignores_difficulty` | (3.1) Keeps the `levels.json` formula when the difficulty engine is on |
| `#remnant_mobs:event_hunters` | (3.1) Monsters event nights send after players |
| `#remnant_mobs:event_mini_bosses` | (3.1) Mini-boss candidates for event nights |

Dimension type tag `#remnant_mobs:no_difficulty` (3.1): dimensions of these types keep the `levels.json` formula.

## Scoreboard tags

Leveled mobs carry these entity tags (`/tag`, `@e[tag=...]`):

| Tag | |
| --- | --- |
| `remnant_mobs.<rank>` | `remnant_mobs.veteran` ... `remnant_mobs.remnant` (none for Common) |
| `remnant_mobs.trait.<trait>` | One per trait |
| `remnant_mobs.event`, `remnant_mobs.event.<id>` | (3.1) Sent by an event night |
| `remnant_mobs.event_boss` | (3.1) An event night's mini-boss |
| `remnant_mobs:data\|<level>\|<rank>\|<traits>\|<seed>\|<flags>` | The saved data. Read it through the API rather than parsing it |

Removing these tags by hand does not remove the stats; use `/remnants set <targets> clear`.

## Attribute modifiers

Fixed ids, so any mod can find, read or remove them:

| Id | Source |
| --- | --- |
| `remnant_mobs:level` | The level (one per scaled attribute) |
| `remnant_mobs:rank` | The rank's health, damage and armor |
| `remnant_mobs:trait/swift`, `remnant_mobs:trait/juggernaut`, `remnant_mobs:trait/warded` | Trait stats |
| `remnant_mobs:trait/berserk` | Berserk while below half health (not saved) |
| `remnant_mobs:vital_growth` | (3.1) On players: extra max health from Vital Embers (2 per heart) |

On 1.20.1 modifiers have UUIDs: the name-based (version 3) UUID of the id above, `UUID.nameUUIDFromBytes("remnant_mobs:level".getBytes(UTF_8))`. Warded has no modifier on 1.20.1, which has no max absorption attribute.

## RPG Attribute System

Remnants reads RAS through reflection, with no compile dependency in either direction:

- player level: `tn.nightbeam.ras.api.RasApi.getLevel(Player)`;
- Valor: `tn.nightbeam.ras.procedures.LevelingService.addXp(Entity, double)`, falling back to the
  `/ras xp <amount> <player>` command.

## Network

Two play payloads, both optional on the client: `remnant_mobs:mob_info` (a mob's level, rank, traits, flags,
damage and armor, sent when a player starts tracking it or it changes) and `remnant_mobs:player_state` (your
level, whether a Hunter's Moon is up and the Hunter's Lens readout). 3.1 adds `remnant_mobs:difficulty` (the
difficulty meter: percent of the band, level, tonight's bonus), also optional.
