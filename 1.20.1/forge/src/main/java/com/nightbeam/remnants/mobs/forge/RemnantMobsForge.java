package com.nightbeam.remnants.mobs.forge;

import com.nightbeam.remnants.mobs.RemnantMobs;
import com.nightbeam.remnants.mobs.RemnantsEvents;
import com.nightbeam.remnants.mobs.content.ModContent;
import com.nightbeam.remnants.mobs.network.DifficultyPayload;
import com.nightbeam.remnants.mobs.network.MobInfoPayload;
import com.nightbeam.remnants.mobs.network.Payload;
import com.nightbeam.remnants.mobs.network.PlayerStatePayload;
import com.nightbeam.remnants.mobs.platform.Platform;
import com.nightbeam.remnants.mobs.platform.Registrar;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;

/** The Forge 47 entry point. */
@Mod(RemnantMobs.MOD_ID)
public final class RemnantMobsForge {
    private static final String PROTOCOL = "3";
    private static final Map<ResourceLocation, SimpleChannel> CHANNELS = new HashMap<>();

    private final Map<ResourceKey<?>, DeferredRegister<?>> registers = new HashMap<>();

    public RemnantMobsForge() {
        // Fabric and NeoForge refuse to load next to 2.x; Forge 47's mods.toml cannot say "incompatible".
        if (ModList.get().isLoaded("rpgmoblevelingsystem")) {
            throw new IllegalStateException("Remnants: RPG Mobs replaces RPG Mob Leveling System. Remove the rpgmoblevelingsystem jar from the mods folder.");
        }
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        RemnantMobs.init(new ForgePlatform(), new ForgeRegistrar());
        registers.values().forEach(deferred -> deferred.register(modBus));
        registerPayloads();

        modBus.addListener(this::buildTabs);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDeathEvent event) -> {
            if (!event.isCanceled()) {
                RemnantsEvents.onLivingDeath(event.getEntity(), event.getSource());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> RemnantsEvents.onServerStarted(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> RemnantsEvents.onServerStopping(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                RemnantsEvents.onServerTick(event.getServer());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                RemnantsEvents.onPlayerJoin(player);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.Clone event) -> {
            if (event.getOriginal() instanceof ServerPlayer from && event.getEntity() instanceof ServerPlayer to) {
                RemnantsEvents.onPlayerClone(from, to, !event.isWasDeath());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                RemnantsEvents.onPlayerLeave(player);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                RemnantsEvents.registerCommands(event.getDispatcher(), event.getBuildContext()));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            RemnantMobsForgeClient.init(modBus);
        }
    }

    @SuppressWarnings("unchecked")
    private <R> DeferredRegister<R> register(ResourceKey<? extends Registry<R>> registry) {
        return (DeferredRegister<R>) registers.computeIfAbsent(registry, key -> DeferredRegister.create(registry, RemnantMobs.MOD_ID));
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

    /** One channel per payload, named like the 26.x payload types. Clients without the mod may still join. */
    private static void registerPayloads() {
        channel(MobInfoPayload.ID).messageBuilder(MobInfoPayload.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MobInfoPayload::write)
                .decoder(MobInfoPayload::read)
                .consumerMainThread((payload, context) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandlers.mobInfo(payload)))
                .add();
        channel(PlayerStatePayload.ID).messageBuilder(PlayerStatePayload.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlayerStatePayload::write)
                .decoder(PlayerStatePayload::read)
                .consumerMainThread((payload, context) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandlers.playerState(payload)))
                .add();
        channel(DifficultyPayload.ID).messageBuilder(DifficultyPayload.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DifficultyPayload::write)
                .decoder(DifficultyPayload::read)
                .consumerMainThread((payload, context) ->
                        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandlers.difficulty(payload)))
                .add();
    }

    private static SimpleChannel channel(ResourceLocation id) {
        SimpleChannel channel = NetworkRegistry.newSimpleChannel(id, () -> PROTOCOL,
                NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));
        CHANNELS.put(id, channel);
        return channel;
    }

    private final class ForgeRegistrar implements Registrar {
        @Override
        public <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String path, Function<ResourceLocation, T> factory) {
            ResourceLocation id = RemnantMobs.id(path);
            return RemnantMobsForge.this.register(registry).register(path, () -> factory.apply(id));
        }
    }

    private static final class ForgePlatform implements Platform {
        @Override
        public Path configDir() {
            return FMLPaths.CONFIGDIR.get();
        }

        @Override
        public boolean isModLoaded(String modId) {
            return ModList.get().isLoaded(modId);
        }

        @Override
        public void sendToPlayer(ServerPlayer player, Payload payload) {
            SimpleChannel channel = CHANNELS.get(payload.id());
            if (channel.isRemotePresent(player.connection.connection)) {
                channel.send(PacketDistributor.PLAYER.with(() -> player), payload);
            }
        }

        @Override
        public void sendToTracking(Entity entity, Payload payload) {
            SimpleChannel channel = CHANNELS.get(payload.id());
            channel.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), payload);
        }

        @Override
        public boolean isFakePlayer(ServerPlayer player) {
            return player instanceof FakePlayer;
        }

        @Override
        public boolean isClient() {
            return FMLEnvironment.dist == Dist.CLIENT;
        }
    }
}
