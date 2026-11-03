package com.nightbeam.remnants.mobs.network;

import com.nightbeam.remnants.mobs.RemnantMobs;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import com.nightbeam.remnants.mobs.compat.RasBridge;
import com.nightbeam.remnants.mobs.content.ModContent;
import com.nightbeam.remnants.mobs.event.HuntersMoon;
import com.nightbeam.remnants.mobs.mob.MobData;
import com.nightbeam.remnants.mobs.mob.RemnantMob;
import com.nightbeam.remnants.mobs.mob.Threat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class Network {
    private static final Map<UUID, PlayerStatePayload> LAST_STATE = new HashMap<>();
    private static final Map<UUID, DifficultyPayload> LAST_DIFFICULTY = new HashMap<>();

    private Network() {
    }

    public static MobInfoPayload info(Mob mob) {
        MobData data = RemnantMob.data(mob);
        if (data == null) {
            return new MobInfoPayload(mob.getId(), -1, 0, 0, 0, 0L, 0F, 0F);
        }
        float damage = mob.getAttribute(Attributes.ATTACK_DAMAGE) != null ? (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0F;
        int flags = data.flags() | (RemnantMob.state(mob).wardUp ? MobData.WARD_UP : 0);
        return new MobInfoPayload(mob.getId(), data.level(), data.rank().ordinal(), data.traitMask(), flags, data.seed(),
                damage, (float) mob.getArmorValue());
    }

    /** Sends a mob's info to everyone who sees it. */
    public static void syncMob(Mob mob) {
        if (mob.level() instanceof ServerLevel level && level.getEntity(mob.getId()) == mob) {
            RemnantMobs.platform().sendToTracking(mob, info(mob));
        }
    }

    /** A player started seeing a mob. */
    public static void onStartTracking(ServerPlayer player, Mob mob) {
        if (RemnantMob.data(mob) != null) {
            RemnantMobs.platform().sendToPlayer(player, info(mob));
        }
    }

    public static boolean holdsLens(ServerPlayer player) {
        return player.getMainHandItem().is(ModContent.HUNTERS_LENS.get()) || player.getOffhandItem().is(ModContent.HUNTERS_LENS.get());
    }

    /** Sends the player's state if it changed. */
    public static void syncPlayer(ServerPlayer player) {
        int threat = -1;
        int min = 0;
        int max = 0;
        int structure = 0;
        if (holdsLens(player)) {
            // What monsters spawning here get: nearby players included, then each mob's personal spread.
            Threat t = Threat.at(player.serverLevel(), player.blockPosition(), true);
            int spread = RemnantsConfig.levels().spread;
            threat = t.level();
            min = Math.max(t.min(), t.level() - spread);
            max = Math.min(t.max(), t.level() + spread);
            structure = t.structure();
        }
        PlayerStatePayload payload = new PlayerStatePayload(Threat.hunterLevel(player), RasBridge.present(), HuntersMoon.active(),
                threat, min, max, structure);
        if (!payload.equals(LAST_STATE.get(player.getUUID()))) {
            LAST_STATE.put(player.getUUID(), payload);
            RemnantMobs.platform().sendToPlayer(player, payload);
        }
        syncDifficulty(player);
    }

    /** The difficulty meter (progression.json), sent only when it changed. */
    private static void syncDifficulty(ServerPlayer player) {
        DifficultyPayload payload = DifficultyPayload.HIDDEN;
        var c = RemnantsConfig.progression();
        if (c.enabled && c.meterSync) {
            Threat t = Threat.at(player.serverLevel(), player.blockPosition(), true);
            payload = new DifficultyPayload((int) Math.round(t.fraction() * 100), t.level(), t.moon() + t.event());
        }
        DifficultyPayload last = LAST_DIFFICULTY.get(player.getUUID());
        if (!payload.equals(last) && (last != null || payload != DifficultyPayload.HIDDEN)) {
            LAST_DIFFICULTY.put(player.getUUID(), payload);
            RemnantMobs.platform().sendToPlayer(player, payload);
        }
    }

    public static void forget(ServerPlayer player) {
        LAST_STATE.remove(player.getUUID());
        LAST_DIFFICULTY.remove(player.getUUID());
    }

    public static void syncPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncPlayer(player);
        }
    }

    public static void clear() {
        LAST_STATE.clear();
        LAST_DIFFICULTY.clear();
    }
}
