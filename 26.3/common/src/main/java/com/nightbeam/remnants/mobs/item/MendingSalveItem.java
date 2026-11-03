package com.nightbeam.remnants.mobs.item;

import com.nightbeam.remnants.mobs.config.ProgressionConfig;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import com.nightbeam.remnants.mobs.event.Scheduler;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** A quick salve: smear it on and heal a little over a few seconds, even mid-fight. */
public class MendingSalveItem extends Item {
    public MendingSalveItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (player.getHealth() >= player.getMaxHealth()) {
            return InteractionResult.FAIL;
        }
        ProgressionConfig c = RemnantsConfig.progression();
        int ticks = c.salveSeconds * 20;
        float perTick = (float) (c.salveHeal / ticks);
        Scheduler.repeat(ticks, left -> {
            if (!serverPlayer.isAlive() || serverPlayer.hasDisconnected()) {
                return false;
            }
            serverPlayer.heal(perTick);
            if (left % 10 == 0) {
                serverPlayer.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, serverPlayer.getX(), serverPlayer.getY(0.5),
                        serverPlayer.getZ(), 2, 0.3, 0.4, 0.3, 0.0);
            }
            return true;
        });
        // The cooldown goes on before the stack shrinks: the last salve would otherwise leave an empty stack.
        player.getCooldowns().addCooldown(stack, c.salveCooldown * 20);
        if (!player.hasInfiniteMaterials()) {
            stack.shrink(1);
        }
        server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_PLACE, SoundSource.PLAYERS, 0.8F, 1.2F);
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.remnant_mobs.mending_salve.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
