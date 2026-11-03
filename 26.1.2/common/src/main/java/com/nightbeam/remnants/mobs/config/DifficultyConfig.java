package com.nightbeam.remnants.mobs.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nightbeam.remnants.mobs.difficulty.Curve;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.nightbeam.remnants.mobs.config.Json.arr;
import static com.nightbeam.remnants.mobs.config.Json.obj;

/**
 * {@code config/remnant_mobs/difficulty.json}: the difficulty engine. When enabled it replaces the distance, days and
 * depth terms of levels.json with curved factors, adds play time and gear, and lets difficulty raise rank odds.
 */
public final class DifficultyConfig {
    public static final String[] FACTORS = {"days", "played", "distance", "depth", "gear"};

    public final boolean enabled;
    public final Map<String, Curve> factors = new LinkedHashMap<>();
    public final int depthStartY;
    public final double armorWeight;
    public final double toughnessWeight;
    public final double attackWeight;
    public final double healthWeight;
    public final double rankOddsMax;
    public final Curve rankOddsCurve;
    public final Map<String, Dimension> dimensions = new LinkedHashMap<>();
    public final List<String> excludeDimensions;
    public final boolean disableOverlaps;
    public final List<String> overlapModIds;

    /** Per-dimension multiplier and offset applied to base + factors, and factor overrides. */
    public record Dimension(double multiplier, double offset, Map<String, Curve> factors) {
    }

    private DifficultyConfig(JsonObject o) {
        enabled = Json.bool(o, "enabled", false);
        for (String key : FACTORS) {
            factors.put(key, Curve.read(Json.object(o, "factors." + key), Curve.OFF));
        }
        depthStartY = Json.integer(o, "factors.depth.start_y", 32);
        armorWeight = Math.max(0.0, Json.num(o, "gear_score.armor", 1.0));
        toughnessWeight = Math.max(0.0, Json.num(o, "gear_score.toughness", 1.5));
        attackWeight = Math.max(0.0, Json.num(o, "gear_score.attack_damage", 1.0));
        healthWeight = Math.max(0.0, Json.num(o, "gear_score.extra_health", 0.5));
        rankOddsMax = Math.max(1.0, Json.num(o, "rank_odds.max_multiplier", 2.0));
        rankOddsCurve = Curve.read(obj("enabled", true, "curve", Json.str(o, "rank_odds.curve", "sqrt"), "per", 1, "max", 1), Curve.OFF);
        for (Map.Entry<String, JsonElement> entry : Json.object(o, "dimensions").entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject d = entry.getValue().getAsJsonObject();
            Map<String, Curve> overrides = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> f : Json.object(d, "factors").entrySet()) {
                Curve global = factors.get(f.getKey());
                if (global != null && f.getValue().isJsonObject()) {
                    overrides.put(f.getKey(), Curve.read(f.getValue().getAsJsonObject(), global));
                }
            }
            dimensions.put(entry.getKey(), new Dimension(Math.max(0.0, Json.num(d, "multiplier", 1.0)), Json.num(d, "offset", 0), overrides));
        }
        excludeDimensions = Json.strings(o, "exclude_dimensions");
        disableOverlaps = Json.bool(o, "compat.disable_overlaps", false);
        overlapModIds = Json.strings(o, "compat.overlap_mod_ids");
    }

    /** The factor curve for a dimension: its override if it has one. */
    public Curve factor(String dimension, String key) {
        Dimension d = dimensions.get(dimension);
        Curve override = d != null ? d.factors().get(key) : null;
        return override != null ? override : factors.get(key);
    }

    public Dimension dimension(String id) {
        return dimensions.getOrDefault(id, DEFAULT_DIMENSION);
    }

    private static final Dimension DEFAULT_DIMENSION = new Dimension(1.0, 0, Map.of());

    public static DifficultyConfig from(JsonObject json) {
        return new DifficultyConfig(json);
    }

    /**
     * @param fresh  true on a fresh install (the new systems start on); false when an older config was found
     * @param levels the loaded levels.json: days, distance and depth start from its numbers, so turning the engine on
     *               keeps a world's levels where they were and only adds the new factors
     */
    public static CommentedJson spec(boolean fresh, LevelsConfig levels) {
        JsonObject depth = new Curve(true, Curve.Shape.LINEAR, levels.blocksPerDepthLevel, 1, levels.maxDepthLevels).json();
        depth.addProperty("start_y", levels.depthStartY);
        JsonObject d = obj(
                "enabled", fresh,
                "factors", obj(
                        "days", new Curve(true, Curve.Shape.LINEAR, levels.daysPerLevel, 1, levels.maxDayLevels).json(),
                        "played", new Curve(true, Curve.Shape.SQRT, 2, 1, 10).json(),
                        "distance", new Curve(true, Curve.Shape.LINEAR, levels.blocksPerLevel, 1, 0).json(),
                        "depth", depth,
                        "gear", new Curve(true, Curve.Shape.LINEAR, 6, 1, 15).json()),
                "gear_score", obj("armor", 1.0, "toughness", 1.5, "attack_damage", 1.0, "extra_health", 0.5),
                "rank_odds", obj("max_multiplier", 2.0, "curve", "sqrt"),
                "dimensions", obj(
                        "minecraft:the_nether", obj("multiplier", 1.15, "offset", 0, "factors", obj()),
                        "minecraft:the_end", obj("multiplier", 1.25, "offset", 0, "factors", obj())),
                "exclude_dimensions", arr("#remnant_mobs:no_difficulty"),
                "compat", obj("disable_overlaps", fresh, "overlap_mod_ids", arr("champions", "infernalmobs")));
        return new CommentedJson(d)
                .header("Remnants: RPG Mobs 3.1: the difficulty engine.\n"
                        + "New in 3.1. When this file is first created next to an existing config, the engine starts switched\n"
                        + "off so worlds and modpacks behave exactly as before; fresh installs start with it on.\n"
                        + "Edit and run /remnants reload, or restart. Missing keys are restored with their defaults.")
                .comment("enabled", "Master switch. When false, levels follow levels.json exactly as in 3.0.\n"
                        + "When true, the distance, time and depth settings of levels.json are replaced by the factors below,\n"
                        + "and play time and gear join them. Bands, structures, player levels and spread stay in levels.json.")
                .comment("factors", "Each factor turns an input into levels: t = input / per, then curve \"linear\" (t), \"sqrt\",\n"
                        + "\"log\" (ln(1+t)) or \"power\" (t^exponent), capped at max (0 = no cap).\n"
                        + "days: in-game days. played: hours the nearby player(s) have played (vanilla statistic).\n"
                        + "distance: blocks from world spawn (Nether blocks count x8). depth: blocks below start_y.\n"
                        + "gear: the gear score of nearby player(s), see gear_score. Per-player inputs use players.mode\n"
                        + "and players.radius from levels.json; with nobody near they count 0.")
                .comment("gear_score", "Gear score = armor x armor + toughness x toughness + (attack damage - 1) x attack_damage\n"
                        + "+ (max health - 20) x extra_health, read from the player's attributes (works with any modded gear).")
                .comment("rank_odds", "Rank chances are multiplied by up to max_multiplier as difficulty rises from the bottom to\n"
                        + "the top of the dimension's band, following curve (linear, sqrt, log or power).")
                .comment("dimensions", "Per dimension: (band min + factors) x multiplier + offset. factors overrides any key of a\n"
                        + "factor there, e.g. \"factors\": {\"distance\": {\"per\": 64}}.")
                .comment("exclude_dimensions", "Dimension ids, or #tags of dimension types, that keep the levels.json formula.")
                .comment("compat", "When one of overlap_mod_ids (mods that already give monsters elite affixes) is installed and\n"
                        + "disable_overlaps is true, Remnants rolls no traits so the two do not stack. Levels, ranks and events stay.");
    }
}
