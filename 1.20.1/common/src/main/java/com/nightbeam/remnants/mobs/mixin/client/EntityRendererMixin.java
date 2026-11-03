package com.nightbeam.remnants.mobs.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nightbeam.remnants.mobs.client.Plate;
import com.nightbeam.remnants.mobs.client.Plates;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {
    @Shadow
    @Final
    protected EntityRenderDispatcher entityRenderDispatcher;

    @Unique
    private Entity remnants$plated;

    @Shadow
    protected abstract boolean shouldShowName(T entity);

    @Inject(method = "render", at = @At("HEAD"))
    private void remnants$plate(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        remnants$plated = null;
        if (!(entity instanceof Mob mob) || Plates.drewFallback(entity)) {
            return;
        }
        Plate plate = Plate.build(mob, partialTick, entityRenderDispatcher.distanceToSqr(entity));
        if (plate == null) {
            return;
        }
        boolean replace = RemnantsConfig.client().hideVanillaName;
        if (replace) {
            remnants$plated = entity;
        }
        Plates.render(plate, pose, buffers, entityRenderDispatcher.cameraOrientation(), !replace && shouldShowName(entity));
    }

    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void remnants$hideNameTag(T entity, Component name, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (entity == remnants$plated || Plates.hidesNameTag(entity)) {
            ci.cancel();
        }
    }
}
