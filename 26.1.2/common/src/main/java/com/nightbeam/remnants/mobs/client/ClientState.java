package com.nightbeam.remnants.mobs.client;

import com.nightbeam.remnants.mobs.content.Rank;
import com.nightbeam.remnants.mobs.mob.MobData;
import com.nightbeam.remnants.mobs.mob.MobState;
import com.nightbeam.remnants.mobs.mob.RemnantMob;
import com.nightbeam.remnants.mobs.network.DifficultyPayload;
import com.nightbeam.remnants.mobs.network.MobInfoPayload;
import com.nightbeam.remnants.mobs.network.PlayerStatePayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** What the client knows: synced mob info (kept on each mob) and the player's own state. */
public final class ClientState {
    private static final Map<Integer, MobInfoPayload> PENDING = new HashMap<>();
    private static PlayerStatePayload player = new PlayerStatePayload(-1, false, false, -1, 0, 0, 0);
    private static DifficultyPayload difficulty = DifficultyPayload.HIDDEN;

    public static DifficultyPayload difficulty() {
        return difficulty;
    }

    public static void handleDifficulty(DifficultyPayload payload) {
        difficulty = payload;
    }

    private ClientState() {
    }

    public static PlayerStatePayload player() {
        return player;
    }

    public static void handlePlayerState(PlayerStatePayload payload) {
        player = payload;
    }

    public static void handleMobInfo(MobInfoPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.level != null ? minecraft.level.getEntity(payload.entityId()) : null;
        if (entity instanceof Mob mob) {
            apply(mob, payload);
        } else if (PENDING.size() < 4096) {
            PENDING.put(payload.entityId(), payload);
        }
    }

    /** The mob's data, picking up info that arrived before the mob did. */
    public static MobState state(Mob mob) {
        MobState state = RemnantMob.state(mob);
        if (state.data == null && !PENDING.isEmpty()) {
            MobInfoPayload pending = PENDING.remove(mob.getId());
            if (pending != null) {
                apply(mob, pending);
            }
        }
        return state;
    }

    private static void apply(Mob mob, MobInfoPayload payload) {
        MobState state = RemnantMob.state(mob);
        if (payload.level() < 0) {
            state.data = null;
            return;
        }
        state.data = new MobData(payload.level(), Rank.byTier(payload.rank()), MobData.traitsFromMask(payload.traits()), payload.seed(),
                payload.flags() & ~MobData.WARD_UP);
        state.wardUp = (payload.flags() & MobData.WARD_UP) != 0;
        state.damage = payload.damage();
        state.armor = payload.armor();
    }

    public static void reset() {
        PENDING.clear();
        player = new PlayerStatePayload(-1, false, false, -1, 0, 0, 0);
        difficulty = DifficultyPayload.HIDDEN;
    }

    /** Colour of a mob's level compared with the player's own, or neutral when levels are not compared. */
    public static int levelColor(int mobLevel, boolean relative) {
        int own = player.hunterLevel();
        if (!relative || own < 0) {
            return 0xFFE8E2D0;
        }
        int diff = mobLevel - own;
        if (diff <= -10) {
            return 0xFF9A9A9A;
        }
        if (diff <= -3) {
            return 0xFF7FD86A;
        }
        if (diff <= 2) {
            return 0xFFF2F2F2;
        }
        if (diff <= 6) {
            return 0xFFF2D24B;
        }
        if (diff <= 14) {
            return 0xFFF2903A;
        }
        return 0xFFE8403A;
    }

    public static boolean deadly(int mobLevel, boolean relative) {
        return relative && player.hunterLevel() >= 0 && mobLevel - player.hunterLevel() >= 15;
    }
}
