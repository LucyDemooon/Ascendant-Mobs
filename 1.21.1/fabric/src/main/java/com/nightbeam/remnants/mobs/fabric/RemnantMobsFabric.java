package com.nightbeam.remnants.mobs.fabric;

import com.nightbeam.remnants.mobs.RemnantMobs;
import com.nightbeam.remnants.mobs.RemnantsEvents;
import com.nightbeam.remnants.mobs.content.ModContent;
import com.nightbeam.remnants.mobs.network.DifficultyPayload;
import com.nightbeam.remnants.mobs.network.MobInfoPayload;
import com.nightbeam.remnants.mobs.network.PlayerStatePayload;
import com.nightbeam.remnants.mobs.platform.Platform;
import com.nightbeam.remnants.mobs.platform.Registrar;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Items;

public final class RemnantMobsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        RemnantMobs.init(new FabricPlatform(), new FabricRegistrar());

        PayloadTypeRegistry.playS2C().register(MobInfoPayload.TYPE, MobInfoPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlayerStatePayload.TYPE, PlayerStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DifficultyPayload.TYPE, DifficultyPayload.CODEC);

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS)
                .register(entries -> entries.addAfter(Items.ECHO_SHARD, ModContent.REMNANT_SHARD.get(), ModContent.VITAL_EMBER.get()));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FOOD_AND_DRINKS)
                .register(entries -> {
                    entries.accept(ModContent.MENDING_SALVE.get());
                    entries.accept(ModContent.HUNTERS_DRAUGHT.get());
                });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
                .register(entries -> entries.addAfter(Items.SPYGLASS, ModContent.HUNTERS_LENS.get()));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT)
                .register(entries -> entries.accept(ModContent.BRAND_OF_CHALLENGE.get()));

        ServerLivingEntityEvents.AFTER_DEATH.register(RemnantsEvents::onLivingDeath);
        ServerLifecycleEvents.SERVER_STARTED.register(RemnantsEvents::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(RemnantsEvents::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(RemnantsEvents::onServerTick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> RemnantsEvents.onPlayerJoin(handler.getPlayer()));
        ServerPlayerEvents.COPY_FROM.register(RemnantsEvents::onPlayerClone);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RemnantsEvents.onPlayerLeave(handler.getPlayer()));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> RemnantsEvents.registerCommands(dispatcher, context));
    }

    private static final class FabricRegistrar implements Registrar {
        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String path, Function<ResourceLocation, T> factory) {
            ResourceLocation id = RemnantMobs.id(path);
            Registry<R> target = (Registry<R>) BuiltInRegistries.REGISTRY.getOrThrow((ResourceKey) registry);
            T value = factory.apply(id);
            Registry.register(target, id, value);
            return () -> value;
        }
    }

    private static final class FabricPlatform implements Platform {
        @Override
        public Path configDir() {
            return FabricLoader.getInstance().getConfigDir();
        }

        @Override
        public boolean isModLoaded(String modId) {
            return FabricLoader.getInstance().isModLoaded(modId);
        }

        @Override
        public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
            if (ServerPlayNetworking.canSend(player, payload.type())) {
                ServerPlayNetworking.send(player, payload);
            }
        }

        @Override
        public void sendToTracking(Entity entity, CustomPacketPayload payload) {
            for (ServerPlayer player : PlayerLookup.tracking(entity)) {
                sendToPlayer(player, payload);
            }
        }

        @Override
        public boolean isFakePlayer(ServerPlayer player) {
            return player instanceof FakePlayer;
        }

        @Override
        public boolean isClient() {
            return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
        }
    }
}
