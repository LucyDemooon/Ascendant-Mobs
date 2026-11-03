package com.nightbeam.remnants.mobs.item;

import com.nightbeam.remnants.mobs.config.ProgressionConfig;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/** A bitter tonic hunters carry for the worst nights: a large heal, then a little regeneration. */
public class HuntersDraughtItem extends Item {
    public HuntersDraughtItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            ProgressionConfig c = RemnantsConfig.progression();
            player.heal((float) (player.getMaxHealth() * c.draughtHealFraction));
            if (c.draughtRegenSeconds > 0) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, c.draughtRegenSeconds * 20, 0));
            }
            player.getCooldowns().addCooldown(stack, c.draughtCooldown * 20);
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 24;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.DRINK;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.remnant_mobs.hunters_draught.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
