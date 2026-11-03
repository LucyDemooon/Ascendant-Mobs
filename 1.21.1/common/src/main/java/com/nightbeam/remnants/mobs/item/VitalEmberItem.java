package com.nightbeam.remnants.mobs.item;

import com.nightbeam.remnants.mobs.config.ProgressionConfig;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import com.nightbeam.remnants.mobs.progression.Hearts;
import com.nightbeam.remnants.mobs.reward.Advancements;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** An ember from a strong monster's heart. Use it to grow your own: more max health, for good. */
public class VitalEmberItem extends Item {
    public VitalEmberItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        ProgressionConfig c = RemnantsConfig.progression();
        int current = Hearts.get(serverPlayer);
        int added = Hearts.room(current, c.heartsPerEmber, c.maxBonusHearts);
        if (added <= 0) {
            serverPlayer.displayClientMessage(Component.translatableWithFallback("message.remnant_mobs.ember.full",
                    "Your heart can hold no more embers (%s extra hearts)", current).withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        Hearts.set(serverPlayer, current + added);
        serverPlayer.heal(added * 2.0F);
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 0.6F);
        server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 0.7F);
        server.sendParticles(ParticleTypes.HEART, player.getX(), player.getY(1.0) + 0.3, player.getZ(), 5, 0.4, 0.3, 0.4, 0.0);
        server.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY(0.6), player.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
        serverPlayer.displayClientMessage(Component.translatableWithFallback("message.remnant_mobs.ember.used",
                "Your heart burns brighter: %s extra hearts", current + added).withStyle(ChatFormatting.GOLD), true);
        player.getCooldowns().addCooldown(this, 10);
        Advancements.award(serverPlayer, Advancements.KINDLED);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.remnant_mobs.vital_ember.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
