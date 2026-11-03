package com.nightbeam.remnants.mobs.mob;

import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import com.nightbeam.remnants.mobs.content.Rank;
import com.nightbeam.remnants.mobs.elite.EliteVisuals;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;

/**
 * Boss bars for Remnant-rank mobs (and Champions, with elites.json), shown to players within 32 blocks who can see them.
 */
public final class RemnantBars {
    private static final double RANGE = 32.0;
    private static final Map<Mob, ServerBossEvent> BARS = new IdentityHashMap<>();

    private RemnantBars() {
    }

    public static void tick(ServerLevel level, Mob mob) {
        MobData data = RemnantMob.data(mob);
        boolean shown = data != null && (data.rank() == Rank.REMNANT ? RemnantsConfig.ranks().remnantBossBar
                : data.rank() == Rank.CHAMPION && EliteVisuals.championBar(mob));
        if (!shown) {
            remove(mob);
            return;
        }
        ServerBossEvent bar = BARS.get(mob);
        if (bar == null) {
            int color = data.rank().color();
            Component name = Component.empty()
                    .append(mob.getDisplayName().copy().withStyle(style -> style.withColor(color)))
                    .append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatableWithFallback("plate.remnant_mobs.level", "Lv. %s", data.level()).withStyle(ChatFormatting.GOLD));
            bar = new ServerBossEvent(mob.getUUID(), name, EliteVisuals.barColor(mob, data), BossEvent.BossBarOverlay.NOTCHED_10);
            bar.setDarkenScreen(false);
            BARS.put(mob, bar);
        }
        bar.setProgress(Math.max(0.0F, Math.min(1.0F, mob.getHealth() / mob.getMaxHealth())));
        if (mob.tickCount % 10 != 0) {
            return;
        }
        for (ServerPlayer player : new ArrayList<>(bar.getPlayers())) {
            if (player.level() != level || player.distanceToSqr(mob) > RANGE * RANGE * 1.5 || !player.isAlive()) {
                bar.removePlayer(player);
            }
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(mob) <= RANGE * RANGE && player.hasLineOfSight(mob) && !bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        }
    }

    public static void remove(Mob mob) {
        ServerBossEvent bar = BARS.remove(mob);
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    /** Drops bars of mobs that died or unloaded. Called once a second. */
    public static void cleanup() {
        Iterator<Map.Entry<Mob, ServerBossEvent>> it = BARS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Mob, ServerBossEvent> entry = it.next();
            if (entry.getKey().isRemoved() || !entry.getKey().isAlive()) {
                entry.getValue().removeAllPlayers();
                it.remove();
            }
        }
    }

    public static void clear() {
        BARS.values().forEach(ServerBossEvent::removeAllPlayers);
        BARS.clear();
    }
}
