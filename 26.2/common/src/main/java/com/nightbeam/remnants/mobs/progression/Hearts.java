package com.nightbeam.remnants.mobs.progression;

import com.nightbeam.remnants.mobs.RemnantMobs;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Extra hearts from Vital Embers: one permanent max-health modifier with a fixed id ({@code remnant_mobs:vital_growth}),
 * saved with the player like any attribute modifier and carried over on respawn.
 */
public final class Hearts {
    /** Fixed modifier id. */
    public static final Identifier ID = RemnantMobs.id("vital_growth");

    private Hearts() {
    }

    /** Extra hearts the player has from Vital Embers. */
    public static int get(ServerPlayer player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier modifier = health == null ? null : health.getModifier(ID);
        return modifier == null ? 0 : (int) Math.round(modifier.amount() / 2.0);
    }

    /** Sets the extra hearts (0 removes the modifier), keeping current health within the new maximum. */
    public static void set(ServerPlayer player, int hearts) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) {
            return;
        }
        health.removeModifier(ID);
        if (hearts > 0) {
            health.addPermanentModifier(new AttributeModifier(ID, hearts * 2.0, AttributeModifier.Operation.ADD_VALUE));
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    /** How many hearts an Ember adds now: hearts_per_ember, but never past max_bonus_hearts. 0 when full. */
    public static int room(int current, int perEmber, int max) {
        return Math.max(0, Math.min(perEmber, max - current));
    }

    /**
     * A player was re-created (respawn or return from the End). Vanilla does not copy attribute modifiers on death, so
     * the hearts are carried over here: always when {@code alive}, after a death only with keep_on_death.
     */
    public static void copy(ServerPlayer from, ServerPlayer to, boolean alive) {
        int hearts = get(from);
        if (hearts <= 0 || (!alive && !RemnantsConfig.progression().keepHeartsOnDeath)) {
            return;
        }
        set(to, hearts);
        if (!alive) {
            to.setHealth(to.getMaxHealth());
        }
    }
}
