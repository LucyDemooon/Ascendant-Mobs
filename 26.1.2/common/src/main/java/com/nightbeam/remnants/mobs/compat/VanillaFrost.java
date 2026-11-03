package com.nightbeam.remnants.mobs.compat;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

/**
 * Minecraft 26.4 adds a Freezing effect. The Frostbite trait uses it when the game has it and keeps its 3.0 slowness
 * and freeze ticks otherwise, so no frost mechanic of our own runs next to vanilla's.
 */
public final class VanillaFrost {
    /** The effect's id; to be confirmed against the 26.4 release (snapshots may name it differently). */
    public static final Identifier FREEZING = Identifier.withDefaultNamespace("freezing");

    private VanillaFrost() {
    }

    /** The vanilla Freezing effect, or empty before 26.4. */
    public static Optional<Holder<MobEffect>> freezing() {
        return BuiltInRegistries.MOB_EFFECT.get(FREEZING).map(reference -> reference);
    }
}
