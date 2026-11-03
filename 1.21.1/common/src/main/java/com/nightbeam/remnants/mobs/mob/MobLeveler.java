package com.nightbeam.remnants.mobs.mob;

import com.nightbeam.remnants.mobs.config.LevelsConfig;
import com.nightbeam.remnants.mobs.config.RanksConfig;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import com.nightbeam.remnants.mobs.config.TraitsConfig;
import com.nightbeam.remnants.mobs.content.ModContent;
import com.nightbeam.remnants.mobs.content.ModTags;
import com.nightbeam.remnants.mobs.content.Rank;
import com.nightbeam.remnants.mobs.content.Trait;
import com.nightbeam.remnants.mobs.difficulty.Difficulty;
import com.nightbeam.remnants.mobs.event.EventNights;
import com.nightbeam.remnants.mobs.event.HuntersMoon;
import com.nightbeam.remnants.mobs.network.Network;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.Nullable;

/** Decides and applies a mob's level, rank and traits. */
public final class MobLeveler {
    private static final String LEGACY_LEVEL = "am_level_";

    private MobLeveler() {
    }

    public enum Kind { NONE, PASSIVE, HOSTILE, BOSS }

    /** The mob's first server tick: read its saved data, convert a 2.x mob, or level a new one. */
    public static void firstTick(ServerLevel level, Mob mob) {
        MobState state = RemnantMob.state(mob);
        state.checked = true;
        MobData data = RemnantMob.data(mob);
        if (data != null) {
            if (data.has(Trait.WARDED)) {
                state.wardUp = mob.getAbsorptionAmount() > 0;
            }
            return;
        }
        Integer legacy = legacyLevel(mob);
        if (legacy != null) {
            boolean ascendant = mob.getTags().contains("am_ascendant");
            mob.getTags().removeIf(tag -> tag.startsWith(LEGACY_LEVEL) || tag.startsWith("am_ascendant") || tag.equals("am_got_level"));
            Kind kind = kind(mob);
            if (kind == Kind.NONE) {
                Stats.clear(mob);
                return;
            }
            RandomSource random = mob.getRandom();
            Rank rank = ascendant && kind == Kind.HOSTILE ? Rank.ELITE : Rank.COMMON;
            Set<Trait> traits = rollTraits(random, mob.getType(), rank, RemnantsConfig.ranks().stats(rank).traits());
            set(mob, new MobData(legacy, rank, traits, random.nextLong(), flags(kind, false, false)));
            return;
        }
        if (!RemnantsConfig.levels().enabled) {
            return;
        }
        Kind kind = kind(mob);
        if (kind == Kind.NONE) {
            return;
        }
        data = roll(level, mob, kind, state.fromSpawner);
        set(mob, data);
        if (data.rank() == Rank.REMNANT) {
            announceArrival(level, mob, data);
        }
    }

    /** What kind of leveling a mob gets. */
    public static Kind kind(Mob mob) {
        EntityType<?> type = mob.getType();
        LevelsConfig c = RemnantsConfig.levels();
        if (ModTags.is(type, ModTags.NEVER_LEVELED) || MobFilter.matches(type, c.exclude)) {
            return Kind.NONE;
        }
        if (mob instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) {
            return Kind.NONE;
        }
        if (ModTags.is(type, ModTags.BOSSES)) {
            return Kind.BOSS;
        }
        if (mob instanceof Enemy) {
            return Kind.HOSTILE;
        }
        return c.affectPassive ? Kind.PASSIVE : Kind.NONE;
    }

    private static int flags(Kind kind, boolean spawner, boolean moon) {
        int flags = 0;
        if (kind == Kind.BOSS) {
            flags |= MobData.BOSS;
        }
        if (kind == Kind.PASSIVE) {
            flags |= MobData.PASSIVE;
        }
        if (spawner) {
            flags |= MobData.SPAWNER;
        }
        if (moon) {
            flags |= MobData.MOONLIT;
        }
        return flags;
    }

    /** Rolls level, rank and traits for a new mob at its position. */
    public static MobData roll(ServerLevel level, Mob mob, Kind kind, boolean spawner) {
        RandomSource random = mob.getRandom();
        LevelsConfig c = RemnantsConfig.levels();
        Threat threat = Threat.at(level, mob.blockPosition(), true, !ModTags.is(mob.getType(), ModTags.IGNORES_DIFFICULTY));
        String id = EntityType.getKey(mob.getType()).toString();
        Integer lock = c.levelLocks.get(id);
        int mobLevel = lock != null ? Math.max(0, lock)
                : Mth.clamp(threat.level() + (c.spread > 0 ? random.nextIntBetweenInclusive(-c.spread, c.spread) : 0), threat.min(), threat.max());
        boolean moon = HuntersMoon.affects(level);
        Rank rank = Rank.COMMON;
        Set<Trait> traits = EnumSet.noneOf(Trait.class);
        if (kind == Kind.HOSTILE && !ModTags.is(mob.getType(), ModTags.NEVER_RANKED)) {
            double extra = EventNights.rankMultiplier(level);
            if (threat.engine()) {
                extra *= Difficulty.rankMultiplier(RemnantsConfig.difficulty(), threat.fraction());
            }
            rank = rollRank(random, mobLevel, spawner, moon, extra);
            traits = rollTraits(random, mob.getType(), rank, RemnantsConfig.ranks().stats(rank).traits());
        }
        return new MobData(mobLevel, rank, traits, random.nextLong(), flags(kind, spawner, moon));
    }

    public static Rank rollRank(RandomSource random, int level, boolean spawner, boolean moon) {
        return rollRank(random, level, spawner, moon, 1.0);
    }

    /** @param extra further multiplier on every rank chance: difficulty and event nights */
    public static Rank rollRank(RandomSource random, int level, boolean spawner, boolean moon, double extra) {
        RanksConfig ranks = RemnantsConfig.ranks();
        if (!ranks.enabled) {
            return Rank.COMMON;
        }
        double multiplier = (1.0 + level / ranks.levelDivisor) * extra;
        if (spawner) {
            multiplier *= ranks.spawnerMultiplier;
        }
        if (moon) {
            multiplier *= RemnantsConfig.levels().moonRankMultiplier;
        }
        for (Rank rank : new Rank[] {Rank.REMNANT, Rank.CHAMPION, Rank.ELITE, Rank.VETERAN}) {
            RanksConfig.Stats stats = ranks.stats(rank);
            if (level >= stats.minLevel() && random.nextDouble() < stats.chance() * multiplier) {
                return rank;
            }
        }
        return Rank.COMMON;
    }

    /** Picks {@code count} traits by weight: no repeats, no conflicting pairs, respecting min_rank and exclusions. */
    public static Set<Trait> rollTraits(RandomSource random, EntityType<?> type, Rank rank, int count) {
        return addTraits(random, type, rank, EnumSet.noneOf(Trait.class), count);
    }

    public static Set<Trait> addTraits(RandomSource random, EntityType<?> type, Rank rank, Set<Trait> existing, int count) {
        EnumSet<Trait> result = existing.isEmpty() ? EnumSet.noneOf(Trait.class) : EnumSet.copyOf(existing);
        if (rank == Rank.COMMON || Difficulty.traitsSuppressed()) {
            return result;
        }
        TraitsConfig config = RemnantsConfig.traits();
        for (int i = 0; i < count; i++) {
            List<Trait> candidates = new ArrayList<>();
            int total = 0;
            for (Trait trait : Trait.values()) {
                TraitsConfig.Settings settings = config.settings(trait);
                if (!settings.enabled() || settings.weight() <= 0 || result.contains(trait) || !rank.atLeast(settings.minRank())
                        || MobFilter.matches(type, settings.exclude()) || result.stream().anyMatch(trait::conflictsWith)) {
                    continue;
                }
                candidates.add(trait);
                total += settings.weight();
            }
            if (candidates.isEmpty()) {
                break;
            }
            int pick = random.nextInt(total);
            for (Trait trait : candidates) {
                pick -= config.settings(trait).weight();
                if (pick < 0) {
                    result.add(trait);
                    break;
                }
            }
        }
        return result;
    }

    /** Stores {@code data}, applies its stats and tells watching players. */
    public static void set(Mob mob, MobData data) {
        MobState state = RemnantMob.state(mob);
        state.data = data;
        state.decoded = true;
        state.checked = true;
        store(mob, data);
        Stats.apply(mob, data);
        if (data.rank() == Rank.REMNANT) {
            mob.setPersistenceRequired();
            if (RemnantsConfig.ranks().remnantTitles && !mob.hasCustomName()) {
                mob.setCustomName(Component.literal(Titles.of(data.seed())));
            }
        } else if (data.rank() == Rank.CHAMPION && RemnantsConfig.elites().enabled && RemnantsConfig.elites().championTitles
                && !mob.hasCustomName()) {
            mob.setCustomName(Component.literal(Titles.of(data.seed())));
        }
        Network.syncMob(mob);
    }

    /** Updates the saved tag only (flags such as Packleader's call). */
    public static void update(Mob mob, MobData data) {
        RemnantMob.state(mob).data = data;
        store(mob, data);
    }

    private static void store(Mob mob, MobData data) {
        mob.getTags().removeIf(tag -> tag.startsWith(MobData.TAG_PREFIX) || tag.startsWith(TAG_RANK) || tag.startsWith(TAG_TRAIT));
        mob.addTag(data.encode());
        if (data.ranked()) {
            mob.addTag(data.rank().entityTag());
        }
        for (Trait trait : data.traits()) {
            mob.addTag(TAG_TRAIT + trait.key());
        }
    }

    private static final String TAG_RANK = com.nightbeam.remnants.mobs.RemnantMobs.MOD_ID + ".";
    private static final String TAG_TRAIT = com.nightbeam.remnants.mobs.RemnantMobs.MOD_ID + ".trait.";

    /** Removes everything: the mob becomes a plain vanilla mob again. */
    public static void reset(Mob mob) {
        mob.getTags().removeIf(tag -> tag.startsWith(MobData.TAG_PREFIX) || tag.startsWith(TAG_RANK));
        Stats.clear(mob);
        if (mob.getHealth() > mob.getMaxHealth()) {
            mob.setHealth(mob.getMaxHealth());
        }
        MobState state = RemnantMob.state(mob);
        state.data = null;
        RemnantBars.remove(mob);
        Network.syncMob(mob);
    }

    private static @Nullable Integer legacyLevel(Mob mob) {
        for (String tag : mob.getTags()) {
            if (tag.startsWith(LEGACY_LEVEL)) {
                try {
                    return Math.max(0, (int) Math.floor(Double.parseDouble(tag.substring(LEGACY_LEVEL.length()))));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static void announceArrival(ServerLevel level, Mob mob, MobData data) {
        double radius = RemnantsConfig.ranks().remnantHornRadius;
        if (radius <= 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(mob) <= radius * radius) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), ModContent.REMNANT_HORN.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
                player.displayClientMessage(Component.translatable("message.remnant_mobs.remnant_near",
                        Component.literal(Titles.of(data.seed())).withStyle(style -> style.withColor(Rank.REMNANT.color()))), true);
            }
        }
    }
}
