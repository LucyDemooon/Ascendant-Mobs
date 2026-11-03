package com.nightbeam.remnants.mobs.content;

import com.nightbeam.remnants.mobs.item.BrandOfChallengeItem;
import com.nightbeam.remnants.mobs.item.HuntersDraughtItem;
import com.nightbeam.remnants.mobs.item.HuntersLensItem;
import com.nightbeam.remnants.mobs.item.MendingSalveItem;
import com.nightbeam.remnants.mobs.item.RemnantShardItem;
import com.nightbeam.remnants.mobs.item.VitalEmberItem;
import com.nightbeam.remnants.mobs.platform.Registrar;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Everything the mod registers. */
public final class ModContent {
    public static Supplier<Item> REMNANT_SHARD;
    public static Supplier<Item> HUNTERS_LENS;
    public static Supplier<Item> BRAND_OF_CHALLENGE;
    public static Supplier<Item> VITAL_EMBER;
    public static Supplier<Item> MENDING_SALVE;
    public static Supplier<Item> HUNTERS_DRAUGHT;

    public static Supplier<SoundEvent> SHARD_ABSORB;
    public static Supplier<SoundEvent> BRAND_APPLY;
    public static Supplier<SoundEvent> REMNANT_HORN;
    public static Supplier<SoundEvent> REMNANT_SLAIN;
    public static Supplier<SoundEvent> MOON_RISE;
    public static Supplier<SoundEvent> BLINK;
    public static Supplier<SoundEvent> WARD_BREAK;
    public static Supplier<SoundEvent> WARD_RESTORE;
    public static Supplier<SoundEvent> PACK_CALL;
    public static Supplier<SoundEvent> VOLATILE_FUSE;
    public static Supplier<SoundEvent> RANK_SLAIN;

    private ModContent() {
    }

    public static void register(Registrar r) {
        REMNANT_SHARD = r.register(Registries.ITEM, "remnant_shard", id -> new RemnantShardItem(item(id).rarity(Rarity.UNCOMMON)));
        HUNTERS_LENS = r.register(Registries.ITEM, "hunters_lens", id -> new HuntersLensItem(item(id).stacksTo(1).rarity(Rarity.UNCOMMON)));
        BRAND_OF_CHALLENGE = r.register(Registries.ITEM, "brand_of_challenge",
                id -> new BrandOfChallengeItem(item(id).stacksTo(16).rarity(Rarity.RARE)));
        VITAL_EMBER = r.register(Registries.ITEM, "vital_ember", id -> new VitalEmberItem(item(id).stacksTo(16).rarity(Rarity.RARE)));
        MENDING_SALVE = r.register(Registries.ITEM, "mending_salve", id -> new MendingSalveItem(item(id).stacksTo(16)));
        HUNTERS_DRAUGHT = r.register(Registries.ITEM, "hunters_draught",
                id -> new HuntersDraughtItem(item(id).stacksTo(4).rarity(Rarity.UNCOMMON)));

        SHARD_ABSORB = sound(r, "item.shard.absorb");
        BRAND_APPLY = sound(r, "item.brand.apply");
        REMNANT_HORN = sound(r, "mob.remnant.horn");
        REMNANT_SLAIN = sound(r, "mob.remnant.slain");
        MOON_RISE = sound(r, "event.hunters_moon");
        BLINK = sound(r, "trait.blink");
        WARD_BREAK = sound(r, "trait.ward_break");
        WARD_RESTORE = sound(r, "trait.ward_restore");
        PACK_CALL = sound(r, "trait.pack_call");
        VOLATILE_FUSE = sound(r, "trait.volatile_fuse");
        RANK_SLAIN = sound(r, "mob.rank_slain");
    }

    private static Item.Properties item(Identifier id) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id));
    }

    private static Supplier<SoundEvent> sound(Registrar r, String path) {
        return r.register(Registries.SOUND_EVENT, path, SoundEvent::createVariableRangeEvent);
    }
}
