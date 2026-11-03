package com.nightbeam.remnants.mobs.mob;

import com.nightbeam.remnants.mobs.compat.RasBridge;
import com.nightbeam.remnants.mobs.config.DifficultyConfig;
import com.nightbeam.remnants.mobs.config.LevelsConfig;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import com.nightbeam.remnants.mobs.difficulty.Difficulty;
import com.nightbeam.remnants.mobs.event.EventNights;
import com.nightbeam.remnants.mobs.event.HuntersMoon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

/**
 * The threat of a place: the level mobs spawning there get, before their personal spread. Each part is kept so
 * {@code /remnants threat}, {@code /remnants difficulty} and the Hunter's Lens can explain it.
 *
 * <p>With the difficulty engine off (difficulty.json) this is the 3.0 formula. With it on, distance, days and depth
 * follow the engine's curves, play time and gear join them, and the dimension's multiplier and offset apply.
 */
public record Threat(int min, int max, double distance, double days, double depth, int moon, int structure,
                     @Nullable String structureId, int players, double playerLevel, double weight, double raw, int level,
                     boolean engine, double played, double gear, int event, double multiplier, double offset) {

    public static Threat at(ServerLevel level, BlockPos pos, boolean withPlayers) {
        return at(level, pos, withPlayers, true);
    }

    /** @param engineAllowed false for mobs tagged {@code #remnant_mobs:ignores_difficulty} */
    public static Threat at(ServerLevel level, BlockPos pos, boolean withPlayers, boolean engineAllowed) {
        LevelsConfig c = RemnantsConfig.levels();
        DifficultyConfig dc = RemnantsConfig.difficulty();
        boolean engine = engineAllowed && Difficulty.active(level);
        String dimension = level.dimension().location().toString();
        LevelsConfig.Band band = c.band(dimension);

        BlockPos spawn = level.getSharedSpawnPos();
        double scale = level.dimensionType().coordinateScale();
        double dx = pos.getX() * scale - spawn.getX();
        double dz = pos.getZ() * scale - spawn.getZ();
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD) && scale == 1.0) {
            // Dimensions without a spawn of their own measure from their origin (the End's main island).
            dx = pos.getX();
            dz = pos.getZ();
        }
        double blocks = Math.sqrt(dx * dx + dz * dz);
        double day = WorldTime.day(level);
        double distance;
        double days;
        double depth;
        if (engine) {
            distance = dc.factor(dimension, "distance").levels(blocks * band.distanceScale());
            days = dc.factor(dimension, "days").levels(day);
            depth = level.dimensionType().hasSkyLight() ? dc.factor(dimension, "depth").levels(dc.depthStartY - pos.getY()) : 0;
        } else {
            distance = blocks / c.blocksPerLevel * band.distanceScale();
            days = Math.min(day / c.daysPerLevel, c.maxDayLevels);
            depth = level.dimensionType().hasSkyLight() && pos.getY() < c.depthStartY
                    ? Math.min((c.depthStartY - pos.getY()) / c.blocksPerDepthLevel, c.maxDepthLevels) : 0;
        }
        int moon = HuntersMoon.affects(level) ? c.moonLevelBonus : 0;
        int event = EventNights.levelBonus(level);

        List<ServerPlayer> near = new ArrayList<>();
        if (withPlayers) {
            double radiusSq = c.playerRadius * c.playerRadius;
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && player.isAlive()
                        && player.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= radiusSq) {
                    near.add(player);
                }
            }
            near.sort(Comparator.comparingDouble(p -> p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)));
        }

        double played = 0;
        double gear = 0;
        double multiplier = 1.0;
        double offset = 0;
        double raw;
        if (engine) {
            played = dc.factor(dimension, "played").levels(aggregate(c.playerMode, near, Difficulty::playedHours));
            gear = dc.factor(dimension, "gear").levels(aggregate(c.playerMode, near, Difficulty::gearScore));
            DifficultyConfig.Dimension d = dc.dimension(dimension);
            multiplier = d.multiplier();
            offset = d.offset();
            raw = (band.min() + distance + days + depth + played + gear) * multiplier + offset + moon + event;
        } else {
            raw = band.min() + distance + days + depth + moon + event;
        }

        int structure = 0;
        String structureId = null;
        for (Map.Entry<String, Integer> entry : sortedStructures(c)) {
            if (entry.getValue() <= structure) {
                break;
            }
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id == null) {
                continue;
            }
            ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, id);
            if (level.structureManager().getStructureWithPieceAt(pos, holder -> holder.is(key)).isValid()) {
                structure = entry.getValue();
                structureId = entry.getKey();
            }
        }
        raw = Math.max(raw, structure);

        int players = 0;
        double playerLevel = 0;
        double weight = 0;
        if (withPlayers && !"none".equals(c.playerSource) && c.playerWeight > 0) {
            List<Integer> levels = new ArrayList<>();
            for (ServerPlayer player : near) {
                int value = hunterLevel(player);
                if (value >= 0) {
                    levels.add(value);
                }
            }
            if (!levels.isEmpty()) {
                players = levels.size();
                playerLevel = switch (c.playerMode) {
                    case "nearest" -> levels.get(0);
                    case "highest" -> levels.stream().mapToInt(Integer::intValue).max().orElse(0);
                    default -> levels.stream().mapToInt(Integer::intValue).average().orElse(0);
                };
                weight = c.playerWeight;
                raw = raw + (playerLevel - raw) * weight;
            }
        }
        int result = Mth.clamp((int) Math.floor(raw), band.min(), band.max());
        return new Threat(band.min(), band.max(), distance, days, depth, moon, structure, structureId, players, playerLevel, weight,
                raw, result, engine, played, gear, event, multiplier, offset);
    }

    /** Where this threat sits in its band, 0 to 1: what rank odds, the meter and loot scale with. */
    public double fraction() {
        return Difficulty.fraction(level, min, max);
    }

    private static double aggregate(String mode, List<ServerPlayer> players, java.util.function.ToDoubleFunction<ServerPlayer> value) {
        if (players.isEmpty()) {
            return 0;
        }
        return switch (mode) {
            case "nearest" -> value.applyAsDouble(players.get(0));
            case "highest" -> players.stream().mapToDouble(value).max().orElse(0);
            default -> players.stream().mapToDouble(value).average().orElse(0);
        };
    }

    /**
     * The level a player brings to the fight: their RPG Attribute System level, or their experience level when
     * {@code players.source} is "vanilla". -1 when player levels are not used.
     */
    public static int hunterLevel(ServerPlayer player) {
        String source = RemnantsConfig.levels().playerSource;
        if ("vanilla".equals(source)) {
            return player.experienceLevel;
        }
        if ("ras".equals(source)) {
            return RasBridge.level(player);
        }
        return -1;
    }

    private static List<Map.Entry<String, Integer>> sortedStructures(LevelsConfig c) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(c.structures.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()));
        return entries;
    }
}
