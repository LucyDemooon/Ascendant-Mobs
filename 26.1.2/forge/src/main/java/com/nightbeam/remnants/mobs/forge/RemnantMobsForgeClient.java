package com.nightbeam.remnants.mobs.forge;

import com.nightbeam.remnants.mobs.RemnantMobs;
import com.nightbeam.remnants.mobs.client.ClientState;
import com.nightbeam.remnants.mobs.client.LensHud;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;

/** Client setup, called from {@link RemnantMobsForge} on the client only. */
final class RemnantMobsForgeClient {
    private RemnantMobsForgeClient() {
    }

    static void init() {
        // Forge draws the HUD through layers, so the lens readouts and the difficulty meter go above the boss bars.
        AddGuiOverlayLayersEvent.BUS.addListener(event -> event.getLayeredDraw().addAbove(ForgeLayeredDraw.PRE_SLEEP_STACK,
                RemnantMobs.id("lens"), ForgeLayeredDraw.BOSS_OVERLAY, (graphics, deltaTracker) -> LensHud.render(graphics)));
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> ClientState.reset());
    }
}
