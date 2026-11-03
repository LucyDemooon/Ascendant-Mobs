package com.nightbeam.remnants.mobs.network;

import com.nightbeam.remnants.mobs.RemnantMobs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client, once a second while it changes: the difficulty meter. {@code percent} is where the player stands in
 * their dimension's level band (-1 hides the meter), {@code level} the monster level there and {@code night} the levels
 * a Hunter's Moon or event night adds. A separate payload from player_state, so 3.0 clients are not affected.
 */
public record DifficultyPayload(int percent, int level, int night) implements Payload {
    public static final ResourceLocation ID = RemnantMobs.id("difficulty");
    public static final DifficultyPayload HIDDEN = new DifficultyPayload(-1, 0, 0);

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(percent + 1);
        buf.writeVarInt(level);
        buf.writeVarInt(night);
    }

    public static DifficultyPayload read(FriendlyByteBuf buf) {
        return new DifficultyPayload(buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
