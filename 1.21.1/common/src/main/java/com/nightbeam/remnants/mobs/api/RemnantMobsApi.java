package com.nightbeam.remnants.mobs.api;

import com.nightbeam.remnants.mobs.content.Rank;
import com.nightbeam.remnants.mobs.content.Trait;
import com.nightbeam.remnants.mobs.event.EventNights;
import com.nightbeam.remnants.mobs.event.HuntersMoon;
import com.nightbeam.remnants.mobs.mob.MobData;
import com.nightbeam.remnants.mobs.mob.MobLeveler;
import com.nightbeam.remnants.mobs.mob.RemnantMob;
import com.nightbeam.remnants.mobs.mob.Threat;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * The stable entry point for other mods. Reads work on both sides (the client knows the mobs it can see); changes
 * must run on the server thread. Rank and trait keys are the lower-case names used in commands and config files.
 */
public final class RemnantMobsApi {
    private RemnantMobsApi() {
    }

    /** The mob's level, or -1 if Remnants has not leveled it. */
    public static int getLevel(Entity entity) {
        MobData data = data(entity);
        return data == null ? -1 : data.level();
    }

    /** {@code common}, {@code veteran}, {@code elite}, {@code champion} or {@code remnant}; empty if not leveled. */
    public static String getRank(Entity entity) {
        MobData data = data(entity);
        return data == null ? "" : data.rank().key();
    }

    /** Trait keys such as {@code vampiric}, in a stable order. */
    public static Set<String> getTraits(Entity entity) {
        Set<String> keys = new LinkedHashSet<>();
        MobData data = data(entity);
        if (data != null) {
            for (Trait trait : data.traitList()) {
                keys.add(trait.key());
            }
        }
        return keys;
    }

    /** True if the mob was marked with a Brand of Challenge. */
    public static boolean isBranded(Entity entity) {
        MobData data = data(entity);
        return data != null && data.flag(MobData.BRANDED);
    }

    /** Sets a mob's level, keeping its rank and traits, and re-applies its stats. Server only. */
    public static void setLevel(Mob mob, int level) {
        MobData data = RemnantMob.data(mob);
        if (data == null) {
            data = new MobData(Math.max(0, level), Rank.COMMON, java.util.EnumSet.noneOf(Trait.class), mob.getRandom().nextLong(), 0);
        }
        MobLeveler.set(mob, data.withLevel(level));
    }

    /** The level mobs would spawn at here, before their personal spread (players nearby included). Server only. */
    public static int getThreat(ServerLevel level, BlockPos pos) {
        return Threat.at(level, pos, true).level();
    }

    /** True while a Hunter's Moon is up. Server only. */
    public static boolean isHuntersMoon() {
        return HuntersMoon.active();
    }

    /**
     * Where the threat at {@code pos} sits in its dimension's level band: 0 at the bottom, 1 at the top. This is the
     * difficulty that rank odds and the difficulty meter follow. Server only.
     */
    public static double getDifficulty(ServerLevel level, BlockPos pos) {
        return Threat.at(level, pos, true).fraction();
    }

    /** The id of the running event night (events.json), or "" when none is running. Server only. */
    public static String getActiveEvent() {
        String id = EventNights.active();
        return id == null ? "" : id;
    }

    /** Adds a boss tier that events can name in {@code mini_boss.tier}. Call during mod setup. */
    public static void registerBossTier(String id, BossTier tier) {
        EventNights.registerTier(id, tier);
    }

    private static MobData data(Entity entity) {
        return entity instanceof Mob mob ? (mob.level().isClientSide() ? RemnantMob.state(mob).data : RemnantMob.data(mob)) : null;
    }
}
