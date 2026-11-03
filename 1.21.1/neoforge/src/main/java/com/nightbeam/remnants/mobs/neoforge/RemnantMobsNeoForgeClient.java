package com.nightbeam.remnants.mobs.neoforge;

import com.nightbeam.remnants.mobs.RemnantMobs;
import com.nightbeam.remnants.mobs.client.ClientState;
import com.nightbeam.remnants.mobs.client.LensHud;
import com.nightbeam.remnants.mobs.client.Plates;
import net.minecraft.world.entity.Mob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = RemnantMobs.MOD_ID, dist = Dist.CLIENT)
public final class RemnantMobsNeoForgeClient {
    public RemnantMobsNeoForgeClient(IEventBus modBus) {
        modBus.addListener((RegisterGuiLayersEvent event) ->
                event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, RemnantMobs.id("lens"), (graphics, deltaTracker) -> LensHud.render(graphics)));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientState.reset());
        // Last, and also when cancelled: a cancelled Pre means another mod (Epic Fight) draws this mob itself.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RenderLivingEvent.Pre.class,
                RemnantMobsNeoForgeClient::onRenderLivingPre);
    }

    private static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (event.isCanceled() && event.getEntity() instanceof Mob mob) {
            Plates.renderFallback(mob, event.getPoseStack(), event.getMultiBufferSource(), event.getPartialTick());
        } else {
            Plates.clearFallback();
        }
    }
}
