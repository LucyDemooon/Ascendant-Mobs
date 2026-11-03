package com.nightbeam.remnants.mobs.reward;

import com.nightbeam.remnants.mobs.compat.LootBagsBridge;
import com.nightbeam.remnants.mobs.config.LootConfig;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.joml.Vector3f;

/**
 * Columns of light over rare drops of ranked monsters (loot.json). Sent as particles from the server, so every player
 * sees them; a beam ends when the item is picked up, despawns, or after beams.seconds.
 */
public final class LootBeams {
    private static final int MAX_BEAMS = 128;
    private static final Map<ItemEntity, Long> BEAMS = new IdentityHashMap<>();

    private LootBeams() {
    }

    /** Looks for the items a monster just dropped (this tick) and gives the rare ones a beam. */
    public static void watchDrops(ServerLevel level, Mob mob) {
        LootConfig c = RemnantsConfig.loot();
        if (!c.enabled || !c.beams) {
            return;
        }
        long until = level.getGameTime() + c.beamSeconds * 20L;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(1.5), e -> e.getAge() <= 1)) {
            if (BEAMS.size() < MAX_BEAMS && worthABeam(item.getItem(), c)) {
                BEAMS.put(item, until);
            }
        }
    }

    static boolean worthABeam(ItemStack stack, LootConfig c) {
        if (stack.isEmpty() || stack.getRarity().ordinal() < c.beamMinRarity.ordinal()) {
            return false;
        }
        // Mob LootBags draws its own beams over its bags.
        return !(LootBagsBridge.present() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(c.bagsModId));
    }

    /** Called every tick; draws every 5 ticks. */
    public static void tick(MinecraftServer server, int ticks) {
        if (BEAMS.isEmpty() || ticks % 5 != 0) {
            return;
        }
        LootConfig c = RemnantsConfig.loot();
        double range = c.beamDistance * c.beamDistance;
        Iterator<Map.Entry<ItemEntity, Long>> it = BEAMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ItemEntity, Long> entry = it.next();
            ItemEntity item = entry.getKey();
            if (!c.enabled || !c.beams || item.isRemoved() || !(item.level() instanceof ServerLevel level)
                    || level.getGameTime() > entry.getValue()) {
                it.remove();
                continue;
            }
            Rarity rarity = item.getItem().getRarity();
            int rgb = color(rarity);
            DustParticleOptions dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F,
                    (rgb & 0xFF) / 255F), 1.0F);
            boolean sparkle = rarity == Rarity.EPIC && ticks % 20 == 0;
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(item) > range) {
                    continue;
                }
                level.sendParticles(player, dust, true, item.getX(), item.getY() + 1.4, item.getZ(), 5, 0.02, 0.8, 0.02, 0.0);
                if (sparkle) {
                    level.sendParticles(player, ParticleTypes.END_ROD, true, item.getX(), item.getY() + 2.6, item.getZ(), 1, 0, 0.1, 0, 0.01);
                }
            }
        }
    }

    /** Vanilla's rarity colours: yellow, aqua, light purple. */
    static int color(Rarity rarity) {
        return switch (rarity) {
            case UNCOMMON -> 0xFFFF55;
            case RARE -> 0x55FFFF;
            case EPIC -> 0xFF55FF;
            default -> 0xFFFFFF;
        };
    }

    public static void clear() {
        BEAMS.clear();
    }

    public static int active() {
        return BEAMS.size();
    }
}
