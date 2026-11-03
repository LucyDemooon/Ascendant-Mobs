package com.nightbeam.remnants.mobs.neoforge;

import com.nightbeam.remnants.mobs.RemnantMobs;
import com.nightbeam.remnants.mobs.RemnantsEvents;
import com.nightbeam.remnants.mobs.content.ModContent;
import com.nightbeam.remnants.mobs.network.DifficultyPayload;
import com.nightbeam.remnants.mobs.network.MobInfoPayload;
import com.nightbeam.remnants.mobs.network.PlayerStatePayload;
import com.nightbeam.remnants.mobs.platform.Platform;
import com.nightbeam.remnants.mobs.platform.Registrar;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(RemnantMobs.MOD_ID)
public final class RemnantMobsNeoForge {
    private final Map<ResourceKey<?>, DeferredRegister<?>> registers = new HashMap<>();

    public RemnantMobsNeoForge(IEventBus modBus) {
        RemnantMobs.init(new NeoForgePlatform(), new NeoForgeRegistrar());
        registers.values().forEach(deferred -> deferred.register(modBus));

        modBus.addListener(this::registerPayloads);
        modBus.addListener(this::buildTabs);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDeathEvent event) -> {
            if (!event.isCanceled()) {
                RemnantsEvents.onLivingDeath(event.getEntity(), event.getSource());
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> RemnantsEvents.onServerStarted(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> RemnantsEvents.onServerStopping(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> RemnantsEvents.onServerTick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                RemnantsEvents.onPlayerJoin(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone event) -> {
            if (event.getOriginal() instanceof ServerPlayer from && event.getEntity() instanceof ServerPlayer to) {
                RemnantsEvents.onPlayerClone(from, to, !event.isWasDeath());
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                RemnantsEvents.onPlayerLeave(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> RemnantsEvents.registerCommands(event.getDispatcher(), event.getBuildContext()));
    }

    @SuppressWarnings("unchecked")
    private <R> DeferredRegister<R> register(ResourceKey<? extends Registry<R>> registry) {
        return (DeferredRegister<R>) registers.computeIfAbsent(registry, key -> DeferredRegister.create(registry, RemnantMobs.MOD_ID));
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("3").optional()
                .playToClient(MobInfoPayload.TYPE, MobInfoPayload.CODEC)
                .playToClient(PlayerStatePayload.TYPE, PlayerStatePayload.CODEC)
                .playToClient(DifficultyPayload.TYPE, DifficultyPayload.CODEC);
    }

    private void buildTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModContent.REMNANT_SHARD.get());
            event.accept(ModContent.VITAL_EMBER.get());
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModContent.HUNTERS_LENS.get());
        } else if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModContent.BRAND_OF_CHALLENGE.get());
        } else if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(ModContent.MENDING_SALVE.get());
            event.accept(ModContent.HUNTERS_DRAUGHT.get());
        }
    }

    private final class NeoForgeRegistrar implements Registrar {
        @Override
        public <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String path, Function<Identifier, T> factory) {
            return RemnantMobsNeoForge.this.register(registry).register(path, factory);
        }
    }

    private static final class NeoForgePlatform implements Platform {
        @Override
        public Path configDir() {
            return FMLPaths.CONFIGDIR.get();
        }

        @Override
        public boolean isModLoaded(String modId) {
            return ModList.get().isLoaded(modId);
        }

        @Override
        public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
            if (player.connection.hasChannel(payload.type())) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }

        @Override
        public void sendToTracking(Entity entity, CustomPacketPayload payload) {
            // PacketDistributor.sendToPlayersTrackingEntity throws for clients without the channel (unmodded
            // clients), which crashed the server. Send to the players in tracking range that have it instead; players
            // who start tracking later get the mob's info from Network.onStartTracking.
            if (!(entity.level() instanceof ServerLevel level)) {
                return;
            }
            double range = entity.getType().clientTrackingRange() * 16.0;
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(entity) <= range * range) {
                    sendToPlayer(player, payload);
                }
            }
        }

        @Override
        public boolean isFakePlayer(ServerPlayer player) {
            return player instanceof FakePlayer;
        }

        @Override
        public boolean isClient() {
            return FMLEnvironment.getDist() == Dist.CLIENT;
        }
    }
}
