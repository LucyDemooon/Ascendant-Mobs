package com.nightbeam.remnants.mobs.config;

import com.google.gson.JsonObject;
import com.nightbeam.remnants.mobs.content.Rank;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.world.item.Rarity;

import static com.nightbeam.remnants.mobs.config.Json.arr;
import static com.nightbeam.remnants.mobs.config.Json.obj;

/**
 * {@code config/remnant_mobs/loot.json}: loot that grows with difficulty, bonus Mob LootBags bags from ranked
 * monsters (when that mod is installed) and loot beams over rare drops.
 */
public final class LootConfig {
    /** Mob LootBags tiers, lowest first. */
    public static final List<String> BAG_TIERS = List.of("common", "uncommon", "rare", "epic", "legendary", "mythic");
    /** The key for an event night's mini-boss in the per-rank bag maps. */
    public static final String EVENT_BOSS = "event_boss";

    public final boolean enabled;
    public final double extraRollsBonus;
    public final double shardsBonus;
    public final double commonExtraRoll;
    public final boolean bagsEnabled;
    public final String bagsModId;
    public final Map<String, Double> bagChance = new LinkedHashMap<>();
    public final Map<String, Integer> bagTier = new LinkedHashMap<>();
    public final double bagTierUpAt;
    public final List<String> bagItems;
    public final boolean beams;
    public final Rarity beamMinRarity;
    public final int beamSeconds;
    public final double beamDistance;

    private LootConfig(JsonObject o) {
        enabled = Json.bool(o, "enabled", false);
        extraRollsBonus = Math.max(0.0, Json.num(o, "scaling.extra_rolls_bonus", 1.0));
        shardsBonus = Math.max(0.0, Json.num(o, "scaling.shards_bonus", 0.5));
        commonExtraRoll = Math.max(0.0, Math.min(1.0, Json.num(o, "scaling.common_extra_roll", 0.15)));
        bagsEnabled = Json.bool(o, "loot_bags.enabled", true);
        bagsModId = Json.str(o, "loot_bags.mod_id", "mob_loot_bags");
        for (String key : bagKeys()) {
            bagChance.put(key, Math.max(0.0, Math.min(1.0, Json.num(o, "loot_bags.chance." + key, DEFAULT_CHANCE.get(key)))));
            int tier = BAG_TIERS.indexOf(Json.str(o, "loot_bags.tier." + key, DEFAULT_TIER.get(key)).toLowerCase(Locale.ROOT));
            bagTier.put(key, tier >= 0 ? tier : BAG_TIERS.indexOf(DEFAULT_TIER.get(key)));
        }
        bagTierUpAt = Json.num(o, "loot_bags.tier_up_at_difficulty", 0.66);
        bagItems = Json.strings(o, "loot_bags.items");
        beams = Json.bool(o, "beams.enabled", true);
        beamMinRarity = rarity(Json.str(o, "beams.min_rarity", "rare"));
        beamSeconds = Math.max(1, Math.min(300, Json.integer(o, "beams.seconds", 30)));
        beamDistance = Math.max(8.0, Math.min(128.0, Json.num(o, "beams.distance", 48)));
    }

    private static Rarity rarity(String name) {
        try {
            return Rarity.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Rarity.RARE;
        }
    }

    /** Rank keys from Veteran up, then the event mini-boss. */
    public static List<String> bagKeys() {
        return List.of(Rank.VETERAN.key(), Rank.ELITE.key(), Rank.CHAMPION.key(), Rank.REMNANT.key(), EVENT_BOSS);
    }

    /** Extra loot rolls grow with difficulty: {@code base x (1 + extra_rolls_bonus x difficulty)}. */
    public double rolls(double base, double fraction) {
        return base * (1.0 + extraRollsBonus * clamp01(fraction));
    }

    public double shards(double base, double fraction) {
        return base * (1.0 + shardsBonus * clamp01(fraction));
    }

    /** The bag tier for a rank key: one tier up from tier_up_at_difficulty, never past Mythic. */
    public int tier(String key, double fraction) {
        int tier = bagTier.getOrDefault(key, 0);
        if (bagTierUpAt <= 1.0 && fraction >= bagTierUpAt) {
            tier++;
        }
        return Math.min(BAG_TIERS.size() - 1, tier);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static LootConfig from(JsonObject json) {
        return new LootConfig(json);
    }

    private static final Map<String, Double> DEFAULT_CHANCE = Map.of(
            "veteran", 0.0, "elite", 0.1, "champion", 0.3, "remnant", 1.0, EVENT_BOSS, 1.0);
    private static final Map<String, String> DEFAULT_TIER = Map.of(
            "veteran", "common", "elite", "uncommon", "champion", "rare", "remnant", "epic", EVENT_BOSS, "legendary");

    /** @param fresh true on a fresh install (on); false when an older config was found (off: loot as in 3.0) */
    public static CommentedJson spec(boolean fresh) {
        JsonObject chance = new JsonObject();
        JsonObject tier = new JsonObject();
        for (String key : bagKeys()) {
            chance.addProperty(key, DEFAULT_CHANCE.get(key));
            tier.addProperty(key, DEFAULT_TIER.get(key));
        }
        JsonObject d = obj(
                "enabled", fresh,
                "scaling", obj("extra_rolls_bonus", 1.0, "shards_bonus", 0.5, "common_extra_roll", 0.15),
                "loot_bags", obj("enabled", true, "mod_id", "mob_loot_bags", "chance", chance, "tier", tier,
                        "tier_up_at_difficulty", 0.66,
                        "items", arr("mob_loot_bags:commonlootbag", "mob_loot_bags:uncommonlootbag", "mob_loot_bags:rarelootbag",
                                "mob_loot_bags:epiclootbag", "mob_loot_bags:legendarylootbag", "mob_loot_bags:mythiclootbag")),
                "beams", obj("enabled", true, "min_rarity", "rare", "seconds", 30, "distance", 48));
        return new CommentedJson(d)
                .header("Remnants: RPG Mobs 3.1: loot.\n"
                        + "New in 3.1. When this file is first created next to an existing config, enabled starts false so\n"
                        + "drops are exactly as in 3.0; fresh installs start with it on.\n"
                        + "Edit and run /remnants reload, or restart. Missing keys are restored with their defaults.")
                .comment("enabled", "Master switch for everything below.")
                .comment("scaling", "Difficulty is where a monster's level sits in its dimension's band, 0 to 1.\n"
                        + "extra_rolls_bonus: ranked monsters roll their loot table loot_rolls (ranks.json) x (1 + this x\n"
                        + "difficulty) extra times; fractions are a chance of one more roll. shards_bonus: the same for Remnant\n"
                        + "Shards. common_extra_roll: chance, times difficulty, that an unranked monster rolls its loot once more.")
                .comment("loot_bags", "With Mob LootBags installed (mod_id): ranked monsters killed by a player may drop a bag on top of\n"
                        + "that mod's own drops. chance and tier per rank (common, uncommon, rare, epic, legendary, mythic);\n"
                        + "event_boss is an event night's mini-boss. From tier_up_at_difficulty (0-1, above 1 never) the bag is\n"
                        + "one tier better. items: the bag item ids, lowest tier first, used if Mob LootBags' own bag code\n"
                        + "cannot be reached.")
                .comment("beams", "A column of light over items of min_rarity (uncommon, rare or epic) and above that a ranked\n"
                        + "monster drops, for seconds or until picked up, seen within distance blocks. Sent as particles, so\n"
                        + "players without the mod see it too. Mob LootBags bags keep that mod's own beams.");
    }
}
