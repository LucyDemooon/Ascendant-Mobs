package com.nightbeam.remnants.mobs;

import com.mojang.brigadier.CommandDispatcher;
import com.nightbeam.remnants.mobs.command.RemnantsCommand;
import com.nightbeam.remnants.mobs.difficulty.Difficulty;
import com.nightbeam.remnants.mobs.event.EventNights;
import com.nightbeam.remnants.mobs.event.HuntersMoon;
import com.nightbeam.remnants.mobs.event.Scheduler;
import com.nightbeam.remnants.mobs.mob.RemnantBars;
import com.nightbeam.remnants.mobs.network.Network;
import com.nightbeam.remnants.mobs.progression.Hearts;
import com.nightbeam.remnants.mobs.reward.LootBeams;
import com.nightbeam.remnants.mobs.reward.Rewards;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** Loader-independent event handlers; each loader forwards its events here. */
public final class RemnantsEvents {
    private static int ticks;

    private RemnantsEvents() {
    }

    public static void onServerStarted(MinecraftServer server) {
        HuntersMoon.load(server);
        EventNights.load(server);
        if (Difficulty.traitsSuppressed()) {
            RemnantMobs.LOG.info("A mod listed in difficulty.json compat.overlap_mod_ids is installed: Remnants rolls no traits");
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        HuntersMoon.unload();
        EventNights.unload();
        RemnantBars.clear();
        LootBeams.clear();
        Scheduler.clear();
        Network.clear();
    }

    public static void onServerTick(MinecraftServer server) {
        Scheduler.tick();
        LootBeams.tick(server, ticks);
        if (++ticks % 20 == 0) {
            HuntersMoon.tick(server);
            EventNights.tick(server);
            RemnantBars.cleanup();
            Network.syncPlayers(server);
        }
    }

    public static void onPlayerJoin(ServerPlayer player) {
        Network.forget(player);
        Network.syncPlayer(player);
    }

    /** A player was re-created on respawn or on leaving the End; {@code alive} is false after a death. */
    public static void onPlayerClone(ServerPlayer from, ServerPlayer to, boolean alive) {
        Hearts.copy(from, to, alive);
    }

    public static void onPlayerLeave(ServerPlayer player) {
        Network.forget(player);
    }

    public static void onLivingDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof Mob mob && mob.level() instanceof ServerLevel level) {
            Rewards.onDeath(level, mob, source);
        }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        RemnantsCommand.register(dispatcher, context);
    }
}
