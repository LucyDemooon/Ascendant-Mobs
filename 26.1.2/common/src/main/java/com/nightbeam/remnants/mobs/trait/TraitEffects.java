package com.nightbeam.remnants.mobs.trait;

import com.nightbeam.remnants.mobs.compat.VanillaFrost;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import com.nightbeam.remnants.mobs.config.TraitsConfig;
import com.nightbeam.remnants.mobs.content.ModContent;
import com.nightbeam.remnants.mobs.content.Rank;
import com.nightbeam.remnants.mobs.content.Trait;
import com.nightbeam.remnants.mobs.event.Scheduler;
import com.nightbeam.remnants.mobs.mob.MobData;
import com.nightbeam.remnants.mobs.mob.MobLeveler;
import com.nightbeam.remnants.mobs.mob.MobState;
import com.nightbeam.remnants.mobs.elite.EliteVisuals;
import com.nightbeam.remnants.mobs.mob.RemnantBars;
import com.nightbeam.remnants.mobs.mob.RemnantMob;
import com.nightbeam.remnants.mobs.mob.Stats;
import com.nightbeam.remnants.mobs.network.Network;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** What traits do. Called from the damage, tick and death hooks. */
public final class TraitEffects {
    private TraitEffects() {
    }

    private static double value(Trait trait, String key) {
        return RemnantsConfig.traits().value(trait, key);
    }

    /** Before damage lands: scales leveled mobs' indirect damage, Deflecting, and Crushing's shield break. */
    public static float beforeHurt(ServerLevel level, LivingEntity victim, DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (attacker instanceof Mob mob && mob != victim) {
            MobData data = RemnantMob.data(mob);
            if (data != null) {
                boolean indirect = direct != attacker || mob.getAttribute(Attributes.ATTACK_DAMAGE) == null;
                if (indirect && RemnantsConfig.levels().scaleProjectiles) {
                    amount *= (float) Stats.damageFactor(mob, data);
                }
                if (data.has(Trait.CRUSHING) && direct == attacker && victim.isBlocking()) {
                    ItemStack shield = victim.getItemBlockingWith();
                    BlocksAttacks blocks = shield != null ? shield.get(DataComponents.BLOCKS_ATTACKS) : null;
                    if (blocks != null) {
                        blocks.disable(level, victim, (float) value(Trait.CRUSHING, "shield_seconds"), shield);
                    } else {
                        victim.stopUsingItem();
                    }
                }
            }
        }
        if (victim instanceof Mob mob) {
            MobData data = RemnantMob.data(mob);
            if (data != null && data.has(Trait.DEFLECTING) && source.is(DamageTypeTags.IS_PROJECTILE)) {
                amount *= (float) Math.max(0.0, 1.0 - value(Trait.DEFLECTING, "reduction"));
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 0.6F, 1.4F);
            }
        }
        return amount;
    }

    /** After damage landed. {@code dealt} is the health and absorption the victim actually lost. */
    public static void afterHurt(ServerLevel level, LivingEntity victim, DamageSource source, float dealt, boolean hadShield) {
        Entity attacker = source.getEntity();
        if (attacker instanceof Mob mob && mob != victim && mob.isAlive()) {
            MobData data = RemnantMob.data(mob);
            if (data != null && !data.traits().isEmpty()) {
                onAttack(level, mob, data, victim, source, dealt);
            }
        }
        if (victim instanceof Mob mob) {
            MobData data = RemnantMob.data(mob);
            if (data != null) {
                onDefend(level, mob, data, source, dealt, hadShield);
            }
        }
    }

    private static void onAttack(ServerLevel level, Mob mob, MobData data, LivingEntity victim, DamageSource source, float dealt) {
        if (source.is(DamageTypeTags.IS_EXPLOSION) && !mob.isAlive()) {
            return;
        }
        if (data.has(Trait.VAMPIRIC) && dealt > 0) {
            mob.heal((float) (dealt * value(Trait.VAMPIRIC, "heal_fraction")));
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, mob.getX(), mob.getY(0.6), mob.getZ(), 3, 0.25, 0.25, 0.25, 0.0);
        }
        if (!victim.isAlive()) {
            return;
        }
        if (data.has(Trait.FROSTBITE)) {
            int seconds = ticks(value(Trait.FROSTBITE, "slowness_seconds"));
            int amplifier = Math.max(0, (int) value(Trait.FROSTBITE, "slowness_level") - 1);
            var freezing = VanillaFrost.freezing();
            if (freezing.isPresent()) {
                // Minecraft 26.4 and later: the game's own Freezing effect instead of a parallel frost mechanic.
                victim.addEffect(new MobEffectInstance(freezing.get(), seconds, amplifier), mob);
            } else {
                victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, seconds, amplifier), mob);
                if (victim.canFreeze()) {
                    int freeze = (int) value(Trait.FROSTBITE, "freeze_ticks");
                    victim.setTicksFrozen(Math.min(victim.getTicksRequiredToFreeze() + 60, victim.getTicksFrozen() + freeze));
                }
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY(0.5), victim.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
        }
        if (data.has(Trait.INFERNAL)) {
            victim.igniteForSeconds((float) value(Trait.INFERNAL, "fire_seconds"));
        }
        if (data.has(Trait.VENOMOUS)) {
            victim.addEffect(new MobEffectInstance(MobEffects.POISON, ticks(value(Trait.VENOMOUS, "poison_seconds")),
                    data.rank().atLeast(Rank.CHAMPION) ? 1 : 0), mob);
        }
        if (data.has(Trait.WITHERING)) {
            victim.addEffect(new MobEffectInstance(MobEffects.WITHER, ticks(value(Trait.WITHERING, "wither_seconds")), 0), mob);
        }
        if (data.has(Trait.CRUSHING) && source.getDirectEntity() == mob) {
            double strength = value(Trait.CRUSHING, "knockback");
            victim.knockback(strength, mob.getX() - victim.getX(), mob.getZ() - victim.getZ());
            victim.needsSync = true;
            victim.hurtMarked = true;
        }
    }

    private static void onDefend(ServerLevel level, Mob mob, MobData data, DamageSource source, float dealt, boolean hadShield) {
        MobState state = RemnantMob.state(mob);
        state.lastHurt = level.getGameTime();
        Entity attacker = source.getEntity();
        if (data.has(Trait.WARDED) && hadShield && mob.getAbsorptionAmount() <= 0 && state.wardUp) {
            state.wardUp = false;
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), ModContent.WARD_BREAK.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.WAX_OFF, mob.getX(), mob.getY(0.5), mob.getZ(), 16, 0.4, 0.5, 0.4, 0.4);
            Network.syncMob(mob);
        }
        if (!mob.isAlive()) {
            return;
        }
        if (data.has(Trait.THORNSKIN) && attacker instanceof LivingEntity living && source.getDirectEntity() == attacker
                && !source.is(net.minecraft.world.damagesource.DamageTypes.THORNS) && dealt > 0) {
            living.hurtServer(level, level.damageSources().thorns(mob), (float) (dealt * value(Trait.THORNSKIN, "reflect")));
        }
        if (data.has(Trait.BLINKING) && attacker instanceof LivingEntity living && level.getGameTime() >= state.blinkReady
                && mob.getRandom().nextDouble() < value(Trait.BLINKING, "chance") && mob.distanceTo(living) <= value(Trait.BLINKING, "range") * 2) {
            blink(level, mob, living, state);
        }
        if (data.has(Trait.PACKLEADER) && !data.flag(MobData.PACK_CALLED)
                && mob.getHealth() <= mob.getMaxHealth() * value(Trait.PACKLEADER, "health_threshold")) {
            MobLeveler.update(mob, data.withFlag(MobData.PACK_CALLED, true));
            callPack(level, mob, data, attacker instanceof LivingEntity living ? living : null);
        }
    }

    private static void blink(ServerLevel level, Mob mob, LivingEntity target, MobState state) {
        Vec3 look = target.getLookAngle();
        Vec3 behind = target.position().subtract(look.x * 2.0, 0, look.z * 2.0);
        double fromX = mob.getX();
        double fromY = mob.getY();
        double fromZ = mob.getZ();
        if (mob.randomTeleport(behind.x, target.getY(), behind.z, true)) {
            state.blinkReady = level.getGameTime() + ticks(value(Trait.BLINKING, "cooldown_seconds"));
            level.playSound(null, fromX, fromY, fromZ, ModContent.BLINK.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), ModContent.BLINK.get(), SoundSource.HOSTILE, 1.0F, 1.2F);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, fromX, fromY + mob.getBbHeight() / 2, fromZ, 24, 0.3, 0.6, 0.3, 0.05);
            mob.lookAt(target, 180.0F, 180.0F);
        }
    }

    private static void callPack(ServerLevel level, Mob leader, MobData data, LivingEntity target) {
        int count = (int) value(Trait.PACKLEADER, "minions");
        int levels = (int) value(Trait.PACKLEADER, "minion_levels");
        level.playSound(null, leader.getX(), leader.getY(), leader.getZ(), ModContent.PACK_CALL.get(), SoundSource.HOSTILE, 1.5F, 1.0F);
        for (int i = 0; i < count; i++) {
            Entity created = leader.getType().create(level, EntitySpawnReason.REINFORCEMENT);
            if (!(created instanceof Mob minion)) {
                return;
            }
            double angle = (Math.PI * 2 * i) / Math.max(1, count) + leader.getRandom().nextDouble();
            double x = leader.getX() + Math.cos(angle) * 2.0;
            double z = leader.getZ() + Math.sin(angle) * 2.0;
            BlockPos pos = BlockPos.containing(x, leader.getY(), z);
            if (!level.noCollision(minion.getType().getSpawnAABB(x, leader.getY(), z))) {
                x = leader.getX();
                z = leader.getZ();
                pos = leader.blockPosition();
            }
            minion.snapTo(x, leader.getY(), z, leader.getYRot(), 0.0F);
            minion.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.REINFORCEMENT, null);
            MobLeveler.set(minion, new MobData(Math.max(1, data.level() - levels), Rank.COMMON, EnumSet.noneOf(Trait.class),
                    leader.getRandom().nextLong(), MobData.MINION));
            if (target != null) {
                minion.setTarget(target);
            }
            level.addFreshEntityWithPassengers(minion);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, x, leader.getY() + 0.5, z, 10, 0.3, 0.5, 0.3, 0.02);
        }
    }

    /** Every tick for leveled mobs with traits or a Remnant bar. */
    public static void tick(ServerLevel level, Mob mob, MobData data) {
        if (data.rank() == Rank.REMNANT || (data.rank() == Rank.CHAMPION && EliteVisuals.championBar(mob))) {
            RemnantBars.tick(level, mob);
        }
        EliteVisuals.tick(level, mob, data);
        if (data.traits().isEmpty() || !mob.isAlive()) {
            return;
        }
        MobState state = RemnantMob.state(mob);
        long time = level.getGameTime();
        int phase = mob.getId() % 20;
        if (data.has(Trait.BERSERK) && time % 5 == phase % 5) {
            boolean low = mob.getHealth() < mob.getMaxHealth() * value(Trait.BERSERK, "below_health");
            if (low != state.berserk) {
                state.berserk = low;
                Stats.berserk(mob, low);
                if (low) {
                    level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.8F, 1.3F);
                    level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getY(1.0) + 0.3, mob.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
                }
            }
            if (state.berserk && time % 10 == 0) {
                level.sendParticles(ParticleTypes.CRIMSON_SPORE, mob.getX(), mob.getY(0.6), mob.getZ(), 4, 0.3, 0.4, 0.3, 0.0);
            }
        }
        if (time % 20 != phase) {
            return;
        }
        boolean inCombat = time - state.lastHurt < ticks(value(Trait.REGENERATING, "combat_seconds"));
        if (data.has(Trait.REGENERATING) && mob.getHealth() < mob.getMaxHealth()) {
            double fraction = inCombat ? value(Trait.REGENERATING, "in_combat") : value(Trait.REGENERATING, "per_second");
            if (fraction > 0) {
                mob.heal((float) (mob.getMaxHealth() * fraction));
                if (!inCombat) {
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY(0.7), mob.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
                }
            }
        }
        if (data.has(Trait.WARDED) && !state.wardUp && time - state.lastHurt >= ticks(value(Trait.WARDED, "regrow_seconds"))) {
            float shield = (float) (value(Trait.WARDED, "shield_fraction") * mob.getMaxHealth());
            if (shield > 0) {
                mob.setAbsorptionAmount(shield);
                state.wardUp = true;
                level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), ModContent.WARD_RESTORE.get(), SoundSource.HOSTILE, 0.8F, 1.0F);
                level.sendParticles(ParticleTypes.WAX_ON, mob.getX(), mob.getY(0.5), mob.getZ(), 12, 0.4, 0.5, 0.4, 0.2);
                Network.syncMob(mob);
            }
        }
        if (data.has(Trait.FROSTBITE)) {
            mob.setTicksFrozen(0);
        }
    }

    /** Volatile: a delayed blast where the mob died. */
    public static void onDeath(ServerLevel level, Mob mob, MobData data) {
        if (!data.has(Trait.VOLATILE)) {
            return;
        }
        float power = (float) value(Trait.VOLATILE, "power");
        int fuse = Math.max(1, ticks(value(Trait.VOLATILE, "fuse_seconds")));
        double x = mob.getX();
        double y = mob.getY() + 0.3;
        double z = mob.getZ();
        level.playSound(null, x, y, z, ModContent.VOLATILE_FUSE.get(), SoundSource.HOSTILE, 1.2F, 1.0F);
        Scheduler.repeat(fuse, left -> {
            if (left % 4 == 0) {
                level.sendParticles(ParticleTypes.FLAME, x, y + 0.3, z, 6, 0.25, 0.3, 0.25, 0.02);
                level.sendParticles(ParticleTypes.SMOKE, x, y + 0.5, z, 4, 0.2, 0.2, 0.2, 0.01);
            }
            if (left == 0) {
                explodeSparingDrops(level, x, y, z, power);
            }
            return true;
        });
    }

    /**
     * The Volatile blast hurts players and pushes items away, but must not destroy the loot the mob just dropped:
     * explosions kill item entities, so the ones in reach are invulnerable for the instant of the blast.
     */
    private static void explodeSparingDrops(ServerLevel level, double x, double y, double z, float power) {
        double reach = power * 2.0 + 1.0;
        java.util.List<net.minecraft.world.entity.item.ItemEntity> items = level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(x - reach, y - reach, z - reach,
                        x + reach, y + reach, z + reach), item -> !item.isInvulnerable());
        items.forEach(item -> item.setInvulnerable(true));
        try {
            level.explode(null, x, y, z, power, Level.ExplosionInteraction.NONE);
        } finally {
            items.forEach(item -> item.setInvulnerable(false));
        }
    }

    private static int ticks(double seconds) {
        return Mth.floor(seconds * 20.0);
    }
}
