# Remnants: RPG Mobs 3.0: usage

How levels, ranks, traits and rewards work, what every config file does, and the commands. All numbers below are
the defaults; every one of them lives in a config file under `config/remnant_mobs/`.

## Levels: the threat of a place

A hostile mob gets its level once, the first time it ticks. The level is the **threat** of the spot it spawned
on:

```
threat = band minimum
       + horizontal distance from world spawn / 128            (one level per 128 blocks)
       + in-game days / 4, at most 15
       + blocks below Y 32 / 12, at most 8                     (dimensions with a sky only)
       + 5 during a Hunter's Moon
threat = at least the structure minimum                        (ancient city 40, bastion 30, ...)
threat = threat + (player level - threat) x 0.35               (players within 64 blocks)
level  = threat +/- 2, kept inside the dimension's band
```

- **Dimension bands**: Overworld 1-60, Nether 15-80, End 35-100, Eclipsed Realm (Remnant Bosses) 30-100. Other
  dimensions use the default band, 1-60. Distance is counted in Overworld blocks, so a Nether block counts as
  eight.
- **Structures**: ancient city 40, end city 50, bastion 30, mansion 30, stronghold 25, monument 25, fortress 20,
  trial chambers 20, pillager outpost 12. Any structure id works, modded ones included.
- **Players**: with RPG Attribute System the player level is the RAS level; without it, `players.source` can use
  vanilla experience levels or nothing. `players.mode` picks the average, nearest or highest player.
- `/remnants threat` prints this breakdown where you stand. Holding a Hunter's Lens shows it on screen.

### What a level does

Every level adds attribute modifiers with fixed ids, so stats never stack twice and base stats are never
changed:

| Attribute | Per level | Cap |
| --- | --- | --- |
| Max health | +6% | +600% |
| Attack damage | +3% | +300% |
| Armor | +0.15 | +15 |
| Armor toughness | +0.05 | +6 |
| Movement speed | +0.3% | +30% |
| Knockback resistance | +0.005 | +0.5 |
| Follow range | +0.5% | +50% |

A level 30 zombie has 56 health and hits for 5.7 on Normal. Arrows, tridents and fireballs of leveled mobs scale
like attack damage. Rules are `additive`, `percent` (of the base value) or `multiplicative` (compounding), and any
attribute id works, modded ones included.

Tamed mobs are never leveled, and passive mobs only with `affect_passive` (they never get ranks). Bosses
(`#remnant_mobs:bosses`: the Ender Dragon, the Wither, the Warden, the Elder Guardian, `#c:bosses` and the bosses
of Remnant Bosses) show a level on their plate but keep their own stats unless `mobs.boss_stats` is on. The
entity type tags `#remnant_mobs:never_leveled` and `#remnant_mobs:never_ranked` and the `mobs.exclude` list opt
mobs out.

## Ranks

Every leveled hostile mob rolls a rank, rarest first. Chances grow by `1 + level / 50`, are quartered for mobs
from monster spawners and doubled under a Hunter's Moon.

| Rank | Chance | From level | Health | Damage | Armor | Traits | Experience | Extra loot rolls | Shards |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Common | | | x1 | x1 | | 0 | x1 | | |
| Veteran | 6% | 1 | x1.4 | x1.1 | +2 | 1 | x2 | | 1 |
| Elite | 1.5% | 8 | x2 | x1.25 | +4 | 2 | x3 | 1 | 2-3 |
| Champion | 0.3% | 15 | x3.2 | x1.45 | +6 | 3 | x5 | 2 | 4-6 |
| Remnant | 0.03% | 30 | x5 | x1.7 | +10 | 4 | x10 | 3 | 8-12 |

**Remnants** get a generated title ("Vorgrim the Hollow") and never despawn. Players within 48 blocks hear a horn
when one appears, a boss bar follows it while it is in sight, and the whole server hears when it falls and who
slew it.

Datapacks can select ranks with the scoreboard tags `remnant_mobs.veteran`, `remnant_mobs.elite`,
`remnant_mobs.champion` and `remnant_mobs.remnant`, and traits with `remnant_mobs.trait.<trait>`:

```mcfunction
execute as @e[tag=remnant_mobs.remnant] run say I am a Remnant
```

## Traits

Ranked mobs roll as many traits as their rank allows, weighted, without repeats. Infernal and Frostbite never
roll together, nor Swift and Juggernaut. Some traits need at least Elite rank.

| Trait | From | Effect |
| --- | --- | --- |
| Vampiric | Veteran | Heals 30% of the damage it deals |
| Frostbite | Veteran | Hits slow (Slowness II, 3 s) and freeze; it cannot be frozen |
| Infernal | Veteran | Immune to fire; hits set you on fire for 4 s |
| Venomous | Veteran | Hits poison for 4 s (Poison II from Champion) |
| Withering | Elite | Hits wither for 3 s |
| Berserk | Veteran | Below half health: +40% damage, +25% speed |
| Crushing | Veteran | Hits knock you far back and disable shields for 3 s |
| Juggernaut | Elite | Immune to knockback, +50% health, +4 armor, 10% slower |
| Thornskin | Veteran | Reflects 25% of melee damage |
| Warded | Elite | A shield of absorption worth 25% of its health that regrows 6 s after the last hit |
| Regenerating | Veteran | Regains 2% health per second (0.5% while fighting) |
| Deflecting | Veteran | Takes 60% less damage from projectiles |
| Swift | Veteran | +30% movement speed |
| Blinking | Elite | When hit, 30% chance to teleport behind its attacker (5 s cooldown) |
| Packleader | Elite | At 60% health, once, calls two copies of itself five levels lower |
| Volatile | Veteran | Explodes 1.5 s after it dies (no block damage) |

Every trait has `enabled`, `weight`, `min_rank` and `exclude` in `traits.json`, plus its own numbers.

## Nameplates

```
 ◆ 42  Elite Zombie
 ██████████████░░░░░  31
 Vampiric · Swift
```

- The rank crest and the name in the rank's colour, and the level in a colour that compares it with **your**
  level: grey 10 or more below, green below, white within 2, yellow up to 6 above, orange up to 14 above, and
  red with a skull from 15 above.
  Your level is your RAS level, or your experience level when the server uses `players.source: vanilla`.
- A health bar in the rank colour (classic red for Common mobs). Damage leaves a pale trail that drains away,
  and a Warded mob's bar gets a gold top edge while its shield is up.
- Traits appear when you are within 12 blocks or look at the mob.
- Common mobs show their plate within 12 blocks, ranked mobs within 24. Plates hide with the HUD (F1).

## Rewards

- **Experience** x (1 + level x 0.04) x the rank's multiplier. A level 30 Elite drops 6.6 times the usual.
- **Loot**: Elite and up roll their loot table again (1, 2 and 3 extra rolls). Extra loot and shards need a
  player to take part in the kill, so mob farms do not pay out more than they should.
- **Remnant Shards** drop from Veteran and up, twice as many under a Hunter's Moon. Use one to absorb it (sneak
  to absorb the whole stack): 15 Valor each with RAS, 8 experience points each without it.
- **Valor** (RAS): on top of the Valor RAS already grants for a kill (which grows with the mob's scaled health),
  the killer of a ranked mob gets `(rank bonus + level) x challenge`. Rank bonus: Veteran 10, Elite 25, Champion
  60, Remnant 150. Challenge rises to x1.5 for a mob 10 levels above you and falls to x0.25 at 15 levels below,
  so punching up pays and farming weak mobs does not. The bonus shows above the hotbar.
- **Branded** mobs (Brand of Challenge) double all of the above.

## Items

| Item | Recipe | Use |
| --- | --- | --- |
| Remnant Shard | Dropped by ranked mobs | Use to absorb for Valor (RAS) or experience; sneak to absorb the stack |
| Hunter's Lens | Spyglass in the middle, a Remnant Shard on each side | Held: the threat of the area, the level range of mobs here and how it compares with yours. Look through it at a mob: its full stat sheet (rank, level, health, damage, armor, traits, rewards) |
| Brand of Challenge | Echo Shard in the middle, eight Remnant Shards around it | Use on a leveled mob: one rank higher (up to Remnant), five levels stronger, at least one more trait, fully healed, glowing and coming for you. Its rewards are doubled |

## Hunter's Moon

On every full-moon night there is a 35% chance that a Hunter's Moon rises. A toll sounds and a title tells
everyone; until dawn, mobs in dimensions with a day cycle spawn 5 levels higher, ranks are twice as likely and
shards drop twice as often. A moon icon in the corner of the screen shows it is up. `/remnants moon` shows the
time left; operators can `/remnants moon start` and `/remnants moon stop`.

## Difficulty engine (3.1)

`difficulty.json` turns the threat into a fuller difficulty. When `enabled`:

- distance, days and depth follow **curves** (`linear`, `sqrt`, `log`, `power`, each with `per` and a cap). They
  start from your `levels.json` numbers, so switching the engine on does not move existing levels;
- **played**: hours the nearby players have played (vanilla statistic) add levels (`sqrt`, 1 level at 2 h, cap 10);
- **gear**: the nearby players' gear score (armor, toughness, attack damage, extra max health, read from their
  attributes, so modded gear counts) adds levels (1 per 6 points, cap 15);
- each dimension can multiply and offset the result and override any factor;
- the higher a place sits in its dimension's level band, the likelier ranks get (up to x2 at the top);
- `exclude_dimensions` (ids or `#` dimension type tags, `#remnant_mobs:no_difficulty` by default) and the entity
  tag `#remnant_mobs:ignores_difficulty` keep the 3.0 formula.

`/remnants difficulty` shows where you stand as a percentage of the band, factor by factor.

## Event nights (3.1)

`events.json` holds event nights. The built-in **Grim Vigil** rolls at dusk every 10 nights from day 7 (50%):
a title, a horn and a chat line, then until dawn new monsters spawn 3 levels higher with x1.5 rank odds, and
three waves of named, ranked hunters (`#remnant_mobs:event_hunters`) are sent after every player in survival.
The last wave brings a Remnant-rank mini-boss (`#remnant_mobs:event_mini_bosses`) with its boss bar. Players who
see the dawn get experience (and Valor with RAS) and the *See the Dawn* advancement; the mini-boss drops extra
shards. Event monsters carry the scoreboard tags `remnant_mobs.event`, `remnant_mobs.event.<id>` and, for the
mini-boss, `remnant_mobs.event_boss`.

Copy the `grim_vigil` entry under a new id to add your own night (frequency, moon phase, minimum difficulty,
announcement, effects, waves, boss rank and tier, rewards, dimensions). Only one event runs at a time and never
during a Hunter's Moon.

## Player progression (3.1)

`progression.json`. Ranked monsters killed by a player may drop:

- **Vital Ember** (Elite 2%, Champion 15%, Remnant 60%, an event night's mini-boss always): use it for one more
  heart of max health, for good, up to ten extra hearts. They stay after death (`hearts.keep_on_death`).
  `/remnants hearts` shows yours.
- **Mending Salve** (Veteran and above, 6%): heals 3 hearts over 3 seconds.
- **Hunter's Draught** (Elite and above, 8%): drink to heal 40% of your health, then Regeneration for 5 seconds.

Every chance grows with difficulty (up to double at the top of the dimension's band, `drops.difficulty_bonus`).
With the mod on your client, a small **difficulty meter** sits bottom right: a skull, the percentage and a bar from
green to deep red, with "+N" when an event night or Hunter's Moon adds levels. Move it with `meter.anchor`, `x`
and `y` in your own `progression.json`, or hide it with `meter.hud`.

## Loot (3.1)

`loot.json`. The harder the place (a monster's level across its dimension's band), the better the loot:

- ranked monsters roll their loot table up to twice as many extra times and drop up to 50% more Remnant Shards;
  unranked monsters get up to a 15% chance of one more roll;
- with **Mob LootBags** installed, ranked monsters killed by a player may also drop a bag on top of that mod's own
  drops: Elite 10% Uncommon, Champion 30% Rare, Remnant always Epic, an event night's mini-boss always Legendary,
  one tier better from two thirds of the way up the band. The bags come from Mob LootBags itself, with its themes,
  announcements and beams;
- rare and epic items a ranked monster drops get a **loot beam** in their rarity colour for 30 seconds, visible
  to everyone.

## Readable elites (3.1)

`elites.json`, visible to every player, with or without the mod: an aura in the rank's colour around Elites and
above (a turning ring for Champions and Remnants), boss bars for Champions as well as Remnants (purple, yellow, and
red for an event night's mini-boss), monsters sent by an event night glow until dawn, and optional titles for
Champions.

## RPG Attribute System

RAS is optional and found at runtime; nothing needs configuring. With it:

- nearby players' RAS levels pull mob levels toward their own (`players.weight`);
- nameplate and lens colours compare a mob's level with your RAS level;
- ranked kills pay bonus Valor and Remnant Shards absorb into Valor;
- RAS's own kill Valor follows the mob's scaled health, so stronger mobs pay more on their own.

Without RAS, levels follow distance, days and depth only, and shards give experience.

## Commands

`/remnants` (alias `/rml`). Operator commands need permission level 2.

| Command | Who | |
| --- | --- | --- |
| `/remnants threat` | everyone | Threat breakdown where you stand |
| `/remnants inspect` | everyone | Level, rank, traits and stats of the mob you look at |
| `/remnants inspect <target>` | operators | The same for any entity |
| `/remnants spawn <entity> <level> [rank] [traits...]` | operators | Spawn a mob with a set level, rank and traits |
| `/remnants set <targets> level <level>` | operators | Change the level, keeping rank and traits |
| `/remnants set <targets> rank <rank>` | operators | Change the rank, keeping level and traits |
| `/remnants set <targets> traits <traits...>` | operators | Set the traits (`none` clears them) |
| `/remnants set <targets> reroll` | operators | Roll level, rank and traits again |
| `/remnants set <targets> clear` | operators | Remove everything Remnants added |
| `/remnants moon [start\|stop]` | everyone / operators | Hunter's Moon status, start, stop |
| `/remnants difficulty [player]` | everyone / operators | Difficulty where you (or a player) stand, factor by factor |
| `/remnants hearts [player]` | everyone / operators | Extra hearts from Vital Embers |
| `/remnants hearts <player> set <n>` | operators | Set them |
| `/remnants event` | everyone | The running event night and its waves |
| `/remnants event list` | everyone | Event nights, their schedule and the next day they may roll |
| `/remnants event start <id>` / `stop` | operators | Start an event night now (until dawn) or end it |
| `/remnants reload` | operators | Reload the config files |

## Configuration

Ten commented JSON files in `config/remnant_mobs/`. Missing keys come back with their defaults, comments explain
every key, and `/remnants reload` applies changes without a restart (new spawns use them; existing mobs keep their
rolls until `/remnants set ... reroll`).

| File | Contents |
| --- | --- |
| `levels.json` | Master switch, dimension bands, distance/time/depth, spread, structures, player scaling (`ras`, `vanilla` or `none`), attribute rules, projectile scaling, excluded mobs, level locks, boss stats, Hunter's Moon |
| `ranks.json` | Rank chances, minimum levels, stat multipliers, trait counts, rewards; Remnant titles, boss bar, announcement and horn |
| `traits.json` | Per trait: enabled, weight, minimum rank, excluded mobs and its numbers |
| `rewards.json` | Experience per level, extra loot, shards, Valor per rank and level, challenge curve, shard value, Brand multiplier |
| `difficulty.json` | (3.1) Difficulty engine: factors and curves, gear score weights, rank odds, per-dimension multipliers and overrides, excluded dimensions, overlap with other elite-mob mods |
| `events.json` | (3.1) Event nights: trigger, announcement, effects, hunter waves, mini-boss, rewards, dimensions |
| `progression.json` | (3.1) Vital Embers and the heart cap, healing items, their drop chances, the difficulty meter |
| `elites.json` | (3.1) Rank aura, Champion and event boss bars and colours, Champion titles, event glow |
| `loot.json` | (3.1) Loot and shard scaling with difficulty, Mob LootBags bags per rank, loot beams |
| `client.json` | Nameplates (distances, look-at-only, health numbers, scale, colours, name tag replacement, F1), lens HUD, moon icon |

Mob lists (`mobs.exclude`, trait `exclude`) take entity ids (`minecraft:creeper`), entity type tags
(`#minecraft:skeletons`) and whole namespaces (`cobblemon:`).

### Upgrading to 3.1

Nothing changes until you opt in. When `difficulty.json`, `events.json`, `progression.json`, `elites.json` and
`loot.json` are created next to an existing 3.0 (or 2.x) config, every new system starts **off**, so worlds and modpacks behave exactly as
before; set `enabled: true` in either and run `/remnants reload`. Fresh installs start with all of them on. The five 3.0
files are not changed and mob data in worlds keeps its format.

### Coming from 2.x

On first start, settings from `config/rpgmoblevelingsystem/` are carried over once: dimension levels, distance
factor, player scaling mode, attribute rules, banned mobs and namespaces, level locks and structure minimums. The
old folder is left untouched. Mobs leveled by 2.x keep their level and have the old modifier replaced; 2.x
Ascendant elites become Elites, everything else Common. The Calendar item and the Neat and Jade hooks are gone;
levels show on Remnants' own plates.
