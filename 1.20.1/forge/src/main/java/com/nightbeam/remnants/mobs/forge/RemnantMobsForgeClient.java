package com.nightbeam.remnants.mobs.forge;

import com.nightbeam.remnants.mobs.client.ClientState;
import com.nightbeam.remnants.mobs.client.LensHud;
import com.nightbeam.remnants.mobs.client.Plates;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/** Client setup. Forge 47 has no client-only {@code @Mod} entry point, so {@link RemnantMobsForge} calls {@link #init}. */
public final class RemnantMobsForgeClient {
    private RemnantMobsForgeClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerAbove(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(), "lens",
                (gui, graphics, partialTick, width, height) -> LensHud.render(graphics)));
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientState.reset());
        // Last, and also when cancelled: a cancelled Pre means another mod (Epic Fight) draws this mob itself.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Pre.class,
                RemnantMobsForgeClient::onRenderLivingPre);
    }

    private static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (event.isCanceled() && event.getEntity() instanceof Mob mob) {
            Plates.renderFallback(mob, event.getPoseStack(), event.getMultiBufferSource(), event.getPartialTick());
        } else {
            Plates.clearFallback();
        }
    }
}
