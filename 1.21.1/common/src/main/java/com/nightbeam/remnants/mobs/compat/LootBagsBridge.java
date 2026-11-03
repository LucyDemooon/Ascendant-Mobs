package com.nightbeam.remnants.mobs.compat;

import com.nightbeam.remnants.mobs.RemnantMobs;
import com.nightbeam.remnants.mobs.config.LootConfig;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Mob LootBags, reached by reflection so neither mod needs the other. Bags are made and dropped through its own
 * {@code DropEngine.createBag} and {@code DropEngine.spawnBag}, so they get its themes, statistics, drop sounds,
 * announcements and loot beams. If that code cannot be found, the bag item ids from loot.json are dropped instead.
 */
public final class LootBagsBridge {
    private static final String DROP_ENGINE = "tn.naizo.moblootbags.common.drop.DropEngine";
    private static final String BAG_TYPE = "tn.naizo.moblootbags.common.content.BagType";
    private static boolean resolved;
    private static @Nullable String resolvedFor;
    private static @Nullable Class<?> bagType;
    private static @Nullable MethodHandle createBag;
    private static @Nullable MethodHandle spawnBag;

    private LootBagsBridge() {
    }

    /** True when Mob LootBags (loot.json mod_id) is installed. */
    public static boolean present() {
        LootConfig c = RemnantsConfig.loot();
        return RemnantMobs.platform() != null && RemnantMobs.platform().isModLoaded(c.bagsModId);
    }

    private static synchronized void resolve() {
        String modId = RemnantsConfig.loot().bagsModId;
        if (resolved && modId.equals(resolvedFor)) {
            return;
        }
        resolved = true;
        resolvedFor = modId;
        bagType = null;
        createBag = null;
        spawnBag = null;
        if (!present()) {
            return;
        }
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> engine = Class.forName(DROP_ENGINE);
            Class<?> type = Class.forName(BAG_TYPE);
            createBag = lookup.findStatic(engine, "createBag", MethodType.methodType(ItemStack.class, ServerLevel.class, type));
            spawnBag = lookup.findStatic(engine, "spawnBag",
                    MethodType.methodType(void.class, ServerLevel.class, LivingEntity.class, ItemStack.class, ServerPlayer.class));
            bagType = type;
            RemnantMobs.LOG.info("Mob LootBags found: ranked monsters can drop loot bags (loot.json)");
        } catch (ReflectiveOperationException | LinkageError e) {
            RemnantMobs.LOG.info("Mob LootBags is installed but its bag code could not be reached ({}); dropping bag items by id", e.toString());
        }
    }

    /**
     * Drops a bag of {@code tier} (0 Common to 5 Mythic) from {@code mob}, found by {@code killer}.
     *
     * @return true if a bag was dropped
     */
    public static boolean drop(ServerLevel level, Mob mob, int tier, @Nullable ServerPlayer killer) {
        if (!present()) {
            return false;
        }
        resolve();
        String key = LootConfig.BAG_TIERS.get(Math.max(0, Math.min(LootConfig.BAG_TIERS.size() - 1, tier)));
        if (createBag != null && spawnBag != null && bagType != null) {
            try {
                Object type = enumConstant(bagType, key.toUpperCase(Locale.ROOT));
                ItemStack bag = (ItemStack) createBag.invoke(level, type);
                spawnBag.invoke(level, (LivingEntity) mob, bag, killer);
                return true;
            } catch (Throwable t) {
                RemnantMobs.LOG.debug("Mob LootBags bag code failed, dropping the item by id", t);
            }
        }
        ItemStack bag = byId(tier);
        if (bag.isEmpty()) {
            return false;
        }
        mob.spawnAtLocation(bag);
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    private static ItemStack byId(int tier) {
        var items = RemnantsConfig.loot().bagItems;
        if (tier < 0 || tier >= items.size()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(items.get(tier));
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}
