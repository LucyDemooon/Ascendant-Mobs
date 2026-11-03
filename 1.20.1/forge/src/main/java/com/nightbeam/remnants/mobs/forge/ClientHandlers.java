package com.nightbeam.remnants.mobs.forge;

import com.nightbeam.remnants.mobs.client.ClientState;
import com.nightbeam.remnants.mobs.network.DifficultyPayload;
import com.nightbeam.remnants.mobs.network.MobInfoPayload;
import com.nightbeam.remnants.mobs.network.PlayerStatePayload;

/** Keeps client classes out of the common entry point: only loaded when a payload reaches a client. */
final class ClientHandlers {
    private ClientHandlers() {
    }

    static void mobInfo(MobInfoPayload payload) {
        ClientState.handleMobInfo(payload);
    }

    static void playerState(PlayerStatePayload payload) {
        ClientState.handlePlayerState(payload);
    }

    static void difficulty(DifficultyPayload payload) {
        ClientState.handleDifficulty(payload);
    }
}
