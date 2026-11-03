package com.nightbeam.remnants.mobs.difficulty;

import com.nightbeam.remnants.mobs.RemnantMobs;
import com.nightbeam.remnants.mobs.config.DifficultyConfig;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** The parts of the difficulty engine that read the world: which dimensions it runs in, play time and gear. */
public final class Difficulty {
    private static final double TICKS_PER_HOUR = 72000.0;

    private Difficulty() {
    }

    /** True when the engine is on and {@code level} is not excluded. */
    public static boolean active(ServerLevel level) {
        DifficultyConfig c = RemnantsConfig.difficulty();
        return c.enabled && !dimensionMatches(level, c.excludeDimensions);
    }

    /** Matches a dimension against ids and {@code #tags} of dimension types. */
    public static boolean dimensionMatches(ServerLevel level, List<String> entries) {
        if (entries.isEmpty()) {
            return false;
        }
        String id = level.dimension().location().toString();
        for (String entry : entries) {
            if (entry.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(entry.substring(1));
                if (tag != null && level.dimensionTypeRegistration().is(TagKey.create(Registries.DIMENSION_TYPE, tag))) {
                    return true;
                }
            } else if (entry.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Hours played, from the vanilla statistic (kept in the player's stats file, so nothing new is saved). */
    public static double playedHours(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / TICKS_PER_HOUR;
    }

    /** What the player brings to a fight, from live attribute values, so modded gear counts too. */
    public static double gearScore(ServerPlayer player) {
        DifficultyConfig c = RemnantsConfig.difficulty();
        double attack = player.getAttribute(Attributes.ATTACK_DAMAGE) != null ? player.getAttributeValue(Attributes.ATTACK_DAMAGE) : 1.0;
        return gearScore(c, player.getArmorValue(), player.getAttributeValue(Attributes.ARMOR_TOUGHNESS), attack, player.getMaxHealth());
    }

    public static double gearScore(DifficultyConfig c, double armor, double toughness, double attack, double maxHealth) {
        return armor * c.armorWeight + toughness * c.toughnessWeight + Math.max(0, attack - 1.0) * c.attackWeight
                + Math.max(0, maxHealth - 20.0) * c.healthWeight;
    }

    /** Where {@code level} sits in a band: 0 at its minimum, 1 at its maximum. */
    public static double fraction(double level, int min, int max) {
        return max <= min ? 0 : Math.max(0, Math.min(1, (level - min) / (max - min)));
    }

    /** The rank odds multiplier for a difficulty fraction. */
    public static double rankMultiplier(DifficultyConfig c, double fraction) {
        return 1.0 + (c.rankOddsMax - 1.0) * Math.min(1.0, c.rankOddsCurve.levels(fraction));
    }

    /** True when an elite-affix mod listed in compat.overlap_mod_ids is loaded and overlaps should be avoided. */
    public static boolean traitsSuppressed() {
        DifficultyConfig c = RemnantsConfig.difficulty();
        if (!c.disableOverlaps || RemnantMobs.platform() == null) {
            return false;
        }
        for (String id : c.overlapModIds) {
            if (RemnantMobs.platform().isModLoaded(id)) {
                return true;
            }
        }
        return false;
    }
}
