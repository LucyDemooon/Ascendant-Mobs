package com.nightbeam.remnants.mobs.elite;

import com.nightbeam.remnants.mobs.config.ElitesConfig;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import com.nightbeam.remnants.mobs.content.Rank;
import com.nightbeam.remnants.mobs.event.EventNights;
import com.nightbeam.remnants.mobs.mob.MobData;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;

/**
 * Elites you can read in one second (elites.json): a rank-coloured aura and boss bar colours. Everything is sent as
 * vanilla particles and boss bars, so players without the mod see it too.
 */
public final class EliteVisuals {
    private EliteVisuals() {
    }

    /** True when Champions get a boss bar too. */
    public static boolean championBar(Mob mob) {
        ElitesConfig c = RemnantsConfig.elites();
        return c.enabled && c.championBar;
    }

    /** The bar colour: yellow as in 3.0 unless elites.json is on. */
    public static BossEvent.BossBarColor barColor(Mob mob, MobData data) {
        ElitesConfig c = RemnantsConfig.elites();
        if (!c.enabled) {
            return BossEvent.BossBarColor.YELLOW;
        }
        if (mob.entityTags().contains(EventNights.BOSS_TAG)) {
            return c.eventBossColor;
        }
        return data.rank() == Rank.CHAMPION ? c.championColor : c.remnantColor;
    }

    /** Every tick for leveled mobs; draws the aura twice a second. */
    public static void tick(ServerLevel level, Mob mob, MobData data) {
        ElitesConfig c = RemnantsConfig.elites();
        if (!c.enabled || !c.aura || !data.rank().atLeast(c.auraMinRank) || data.rank() == Rank.COMMON || !mob.isAlive()
                || (mob.tickCount + mob.getId()) % 10 != 0 || mob.isInvisible()) {
            return;
        }
        int rgb = data.rank().color();
        DustParticleOptions dust = new DustParticleOptions(rgb, data.rank().atLeast(Rank.CHAMPION) ? 1.3F : 1.0F);
        double range = c.auraDistance * c.auraDistance;
        double x = mob.getX();
        double y = mob.getY();
        double z = mob.getZ();
        double radius = Math.max(0.5, mob.getBbWidth() * 0.75);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(mob) > range) {
                continue;
            }
            level.sendParticles(player, dust, false, false, x, y + mob.getBbHeight() * 0.6, z, 3, mob.getBbWidth() * 0.4,
                    mob.getBbHeight() * 0.3, mob.getBbWidth() * 0.4, 0.0);
            if (data.rank().atLeast(Rank.CHAMPION)) {
                double turn = (mob.tickCount % 40) / 40.0 * Math.PI * 2;
                for (int i = 0; i < 8; i++) {
                    double a = turn + i * Math.PI / 4;
                    level.sendParticles(player, dust, false, false, x + Math.cos(a) * radius, y + 0.1, z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
                }
            }
            if (data.rank() == Rank.REMNANT) {
                level.sendParticles(player, ParticleTypes.SOUL_FIRE_FLAME, false, false, x, y + mob.getBbHeight() + 0.2, z, 2, 0.15, 0.05, 0.15, 0.01);
            }
        }
    }
}
