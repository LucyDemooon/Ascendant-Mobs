package com.nightbeam.remnants.mobs.fabric;

import com.nightbeam.remnants.mobs.client.ClientState;
import com.nightbeam.remnants.mobs.client.LensHud;
import com.nightbeam.remnants.mobs.network.DifficultyPayload;
import com.nightbeam.remnants.mobs.network.MobInfoPayload;
import com.nightbeam.remnants.mobs.network.PlayerStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public final class RemnantMobsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(MobInfoPayload.ID, (client, handler, buf, sender) -> {
            MobInfoPayload payload = MobInfoPayload.read(buf);
            client.execute(() -> ClientState.handleMobInfo(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(PlayerStatePayload.ID, (client, handler, buf, sender) -> {
            PlayerStatePayload payload = PlayerStatePayload.read(buf);
            client.execute(() -> ClientState.handlePlayerState(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(DifficultyPayload.ID, (client, handler, buf, sender) -> {
            DifficultyPayload payload = DifficultyPayload.read(buf);
            client.execute(() -> ClientState.handleDifficulty(payload));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientState.reset());
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> LensHud.render(graphics));
    }
}
