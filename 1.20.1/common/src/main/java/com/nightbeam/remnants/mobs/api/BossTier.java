package com.nightbeam.remnants.mobs.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import javax.annotation.Nullable;

/**
 * Spawns an event night's boss. Events name a tier in events.json ({@code mini_boss.tier}); Remnants ships
 * {@code mini} and other mods add tiers through {@link RemnantMobsApi#registerBossTier}.
 */
@FunctionalInterface
public interface BossTier {
    /**
     * @param level   where to spawn
     * @param target  the player it hunts
     * @param pos     a spawn position already checked for the hunters
     * @param eventId the event's id in events.json
     * @return the boss, already added to the world, or null if it could not spawn
     */
    @Nullable
    Mob spawn(ServerLevel level, ServerPlayer target, BlockPos pos, String eventId);
}
