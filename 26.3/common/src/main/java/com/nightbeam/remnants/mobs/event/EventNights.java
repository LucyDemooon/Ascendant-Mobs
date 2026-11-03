package com.nightbeam.remnants.mobs.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nightbeam.remnants.mobs.RemnantMobs;
import com.nightbeam.remnants.mobs.api.BossTier;
import com.nightbeam.remnants.mobs.compat.RasBridge;
import com.nightbeam.remnants.mobs.config.EventsConfig;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import com.nightbeam.remnants.mobs.content.ModContent;
import com.nightbeam.remnants.mobs.content.ModTags;
import com.nightbeam.remnants.mobs.content.Rank;
import com.nightbeam.remnants.mobs.content.Trait;
import com.nightbeam.remnants.mobs.difficulty.Difficulty;
import com.nightbeam.remnants.mobs.mob.MobData;
import com.nightbeam.remnants.mobs.mob.MobLeveler;
import com.nightbeam.remnants.mobs.mob.Threat;
import com.nightbeam.remnants.mobs.mob.Titles;
import com.nightbeam.remnants.mobs.mob.WorldTime;
import com.nightbeam.remnants.mobs.reward.Advancements;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Event nights: at dusk, a configured event may start (events.json). While it runs, new monsters spawn stronger and
 * rank more often, and waves of named, ranked hunters are sent after each player, ending with a mini-boss. Players
 * who see the dawn are rewarded. The Hunter's Moon stays its own system; the two never run together.
 * State is kept in {@code <world>/remnant_mobs_events.json}.
 */
public final class EventNights {
    /** Scoreboard tag on every monster an event sent. */
    public static final String TAG = RemnantMobs.MOD_ID + ".event";
    /** Scoreboard tag on an event's mini-boss. */
    public static final String BOSS_TAG = RemnantMobs.MOD_ID + ".event_boss";
    private static final int DUSK = 13000;
    private static final int DAWN = 23000;
    private static final Map<String, BossTier> TIERS = new LinkedHashMap<>();

    private static @Nullable String active;
    private static long startTime;
    private static long endTime;
    private static long rolledDay = Long.MIN_VALUE;
    private static int wavesSent;
    /** Started by an operator: runs even while events.json has events switched off. */
    private static boolean forced;
    private static final Set<UUID> participants = new HashSet<>();
    private static final Map<UUID, Hunter> hunters = new HashMap<>();
    private static @Nullable Path file;

    private record Hunter(UUID player, boolean boss) {
    }

    static {
        TIERS.put("mini", EventNights::spawnMiniBoss);
    }

    private EventNights() {
    }

    /** Adds a boss tier events can name in {@code mini_boss.tier}. Replaces a tier with the same id. */
    public static void registerTier(String id, BossTier tier) {
        TIERS.put(id.toLowerCase(Locale.ROOT), tier);
    }

    public static @Nullable String active() {
        return active;
    }

    public static EventsConfig.@Nullable Event activeEvent() {
        return active == null ? null : RemnantsConfig.events().events.get(active);
    }

    /** The running event, if it reaches {@code level}. */
    public static EventsConfig.@Nullable Event affecting(ServerLevel level) {
        EventsConfig.Event event = activeEvent();
        return event != null && Difficulty.dimensionMatches(level, event.dimensions()) ? event : null;
    }

    public static int levelBonus(ServerLevel level) {
        EventsConfig.Event event = affecting(level);
        return event == null ? 0 : event.levelBonus();
    }

    public static double rankMultiplier(ServerLevel level) {
        EventsConfig.Event event = affecting(level);
        return event == null ? 1.0 : event.rankMultiplier();
    }

    /** Shard multiplier for a monster an event sent, read from its tags so it holds after the event ends. */
    public static double shardMultiplier(Mob mob) {
        for (String tag : mob.entityTags()) {
            if (tag.startsWith(TAG + ".")) {
                EventsConfig.Event event = RemnantsConfig.events().events.get(tag.substring(TAG.length() + 1));
                return event == null ? 1.0 : event.shardMultiplier();
            }
        }
        return 1.0;
    }

    /** Extra shards an event's mini-boss drops. */
    public static int bossShards(Mob mob) {
        if (!mob.entityTags().contains(BOSS_TAG)) {
            return 0;
        }
        for (String tag : mob.entityTags()) {
            if (tag.startsWith(TAG + ".")) {
                EventsConfig.Event event = RemnantsConfig.events().events.get(tag.substring(TAG.length() + 1));
                return event == null ? 0 : event.rewards().bossShards();
            }
        }
        return 0;
    }

    /** Remaining ticks of the running event. */
    public static long remaining(MinecraftServer server) {
        return active == null ? 0 : Math.max(0, endTime - WorldTime.time(server.overworld()));
    }

    public static int wavesSent() {
        return wavesSent;
    }

    // ---------------------------------------------------------------- lifecycle

    public static void load(MinecraftServer server) {
        active = null;
        rolledDay = Long.MIN_VALUE;
        wavesSent = 0;
        forced = false;
        participants.clear();
        hunters.clear();
        file = server.getWorldPath(LevelResource.ROOT).resolve(RemnantMobs.MOD_ID + "_events.json");
        try {
            if (Files.exists(file)) {
                JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                active = o.has("active") && !o.get("active").isJsonNull() ? o.get("active").getAsString() : null;
                startTime = o.has("start") ? o.get("start").getAsLong() : 0;
                endTime = o.has("end") ? o.get("end").getAsLong() : 0;
                rolledDay = o.has("rolled_day") ? o.get("rolled_day").getAsLong() : Long.MIN_VALUE;
                wavesSent = o.has("waves_sent") ? o.get("waves_sent").getAsInt() : 0;
                forced = o.has("forced") && o.get("forced").getAsBoolean();
                if (o.get("participants") instanceof JsonArray list) {
                    for (JsonElement element : list) {
                        participants.add(UUID.fromString(element.getAsString()));
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            RemnantMobs.LOG.warn("Could not read {}: {}", file, e.toString());
        }
    }

    public static void unload() {
        save();
        active = null;
        participants.clear();
        hunters.clear();
        file = null;
    }

    private static void save() {
        if (file == null) {
            return;
        }
        JsonObject o = new JsonObject();
        if (active != null) {
            o.addProperty("active", active);
        }
        o.addProperty("start", startTime);
        o.addProperty("end", endTime);
        o.addProperty("rolled_day", rolledDay);
        o.addProperty("waves_sent", wavesSent);
        o.addProperty("forced", forced);
        JsonArray list = new JsonArray();
        participants.forEach(id -> list.add(id.toString()));
        o.add("participants", list);
        try {
            Files.writeString(file, o.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            RemnantMobs.LOG.warn("Could not write {}: {}", file, e.toString());
        }
    }

    /** Checked once a second, after the Hunter's Moon. */
    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long time = WorldTime.time(overworld);
        if (active != null) {
            EventsConfig.Event event = activeEvent();
            if (event == null || (!forced && (!RemnantsConfig.events().enabled || !event.enabled()))) {
                stop(server, false);
            } else if (time >= endTime) {
                stop(server, true);
            } else if (time < startTime) {
                // Time was set back with /time: the night it belonged to is gone.
                stop(server, false);
            } else {
                run(server, event, (time - startTime) / 20);
            }
            return;
        }
        EventsConfig config = RemnantsConfig.events();
        if (!config.enabled || !WorldTime.night(overworld)) {
            return;
        }
        long day = WorldTime.dayIndex(overworld);
        if (rolledDay == day) {
            return;
        }
        rolledDay = day;
        if (HuntersMoon.active()) {
            save();
            return;
        }
        RandomSource random = overworld.getRandom();
        for (EventsConfig.Event event : config.events.values()) {
            if (eligible(server, event, day) && random.nextDouble() < event.trigger().chance()) {
                start(server, event, false);
                return;
            }
        }
        save();
    }

    /** True when {@code event} may roll on overworld day {@code day}. */
    public static boolean eligible(MinecraftServer server, EventsConfig.Event event, long day) {
        EventsConfig.Trigger t = event.trigger();
        if (!event.enabled() || day < t.firstDay() || (day - t.firstDay()) % t.everyDays() != 0) {
            return false;
        }
        boolean moonOk = switch (t.moon()) {
            case "full" -> Math.floorMod(day, 8) == 0;
            case "new" -> Math.floorMod(day, 8) == 4;
            default -> true;
        };
        if (!moonOk) {
            return false;
        }
        if (t.minDifficulty() <= 0) {
            return true;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (Threat.at(player.level(), player.blockPosition(), true).fraction() >= t.minDifficulty()) {
                return true;
            }
        }
        return false;
    }

    /** The next overworld day {@code trigger} may roll on, today included (moon phase not counted). */
    public static long nextDay(EventsConfig.Trigger trigger, long today) {
        if (today <= trigger.firstDay()) {
            return trigger.firstDay();
        }
        long rem = (today - trigger.firstDay()) % trigger.everyDays();
        return rem == 0 ? today : today + (trigger.everyDays() - rem);
    }

    /**
     * Starts {@code event} now; it lasts until the next dawn.
     *
     * @param forced started by an operator: it runs even if events are switched off in events.json
     */
    public static void start(MinecraftServer server, EventsConfig.Event event, boolean forced) {
        ServerLevel overworld = server.overworld();
        if (active != null) {
            stop(server, false);
        }
        long time = WorldTime.time(overworld);
        long day = WorldTime.dayIndex(overworld);
        int timeOfDay = WorldTime.timeOfDay(overworld);
        active = event.id();
        startTime = time;
        endTime = day * WorldTime.DAY + DAWN + (timeOfDay >= DAWN ? WorldTime.DAY : 0);
        rolledDay = day;
        wavesSent = 0;
        EventNights.forced = forced;
        participants.clear();
        hunters.clear();
        save();
        Component title = text("event.remnant_mobs." + event.id() + ".title", pretty(event.id())).withStyle(ChatFormatting.DARK_RED);
        Component subtitle = text("event.remnant_mobs." + event.id() + ".subtitle", "Something hunts you until dawn").withStyle(ChatFormatting.GOLD);
        Component chat = text("event.remnant_mobs." + event.id() + ".chat",
                pretty(event.id()) + " falls. Until dawn, named hunters track every survivor, and something worse follows them.")
                .withStyle(ChatFormatting.RED);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!Difficulty.dimensionMatches(player.level(), event.dimensions())) {
                continue;
            }
            if (event.title()) {
                Feedback.title(player, title, subtitle, 20, 70, 30);
            }
            if (event.sound()) {
                Feedback.sound(player, ModContent.REMNANT_HORN.get(), SoundSource.AMBIENT, 1.0F, 0.8F);
            }
            if (event.chat()) {
                player.sendSystemMessage(chat);
            }
        }
        RemnantMobs.LOG.info("Event night started: {}", event.id());
    }

    /** Ends the running event. {@code dawn}: it ran its course, so survivors are rewarded. */
    public static void stop(MinecraftServer server, boolean dawn) {
        if (active == null) {
            return;
        }
        EventsConfig.Event event = activeEvent();
        String id = active;
        active = null;
        if (dawn && event != null) {
            reward(server, event);
        }
        participants.clear();
        hunters.clear();
        save();
        Component message = text("event.remnant_mobs.end", "Dawn breaks. The hunt is over.").withStyle(ChatFormatting.GRAY);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (event == null || Difficulty.dimensionMatches(player.level(), event.dimensions())) {
                Feedback.actionBar(player, message);
            }
        }
        RemnantMobs.LOG.info("Event night ended: {}{}", id, dawn ? " (dawn)" : "");
    }

    private static void reward(MinecraftServer server, EventsConfig.Event event) {
        EventsConfig.Rewards r = event.rewards();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!participants.contains(player.getUUID()) || !player.isAlive() || !hunted(player, event)) {
                continue;
            }
            if (r.surviveExperience() > 0) {
                player.giveExperiencePoints(r.surviveExperience());
            }
            if (r.surviveValor() > 0 && RasBridge.present()) {
                RasBridge.addValor(player, r.surviveValor());
            }
            Advancements.award(player, Advancements.SEE_THE_DAWN);
            player.sendSystemMessage(text("event.remnant_mobs.survived", "You saw the dawn. +%s experience", r.surviveExperience())
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    // ---------------------------------------------------------------- the hunt

    private static boolean hunted(ServerPlayer player, EventsConfig.Event event) {
        return !player.isSpectator() && !player.isCreative() && Difficulty.dimensionMatches(player.level(), event.dimensions());
    }

    private static void run(MinecraftServer server, EventsConfig.Event event, long seconds) {
        List<ServerPlayer> targets = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (hunted(player, event) && player.isAlive()) {
                targets.add(player);
                participants.add(player.getUUID());
            }
        }
        steer(server);
        EventsConfig.Hunters h = event.hunters();
        // Waves wait for someone to hunt, so players who arrive later in the night still get the whole hunt.
        if (targets.isEmpty() || wavesSent >= h.waves() || seconds < h.firstWaveSeconds() + (long) wavesSent * h.waveSeconds()) {
            return;
        }
        wavesSent++;
        boolean last = wavesSent == h.waves();
        for (ServerPlayer player : targets) {
            int alive = aliveFor(player.getUUID());
            int count = Math.min(h.perWave(), Math.max(0, h.maxAlive() - alive));
            for (int i = 0; i < count; i++) {
                Mob mob = spawnHunter(player, event, false);
                if (mob == null) {
                    break;
                }
            }
            Feedback.actionBar(player, text("event.remnant_mobs.wave", "The hunters close in (%s/%s)", wavesSent, h.waves())
                    .withStyle(ChatFormatting.RED));
        }
        if (last && event.miniBoss().enabled() && !targets.isEmpty()) {
            List<ServerPlayer> bossTargets = event.miniBoss().perPlayer() ? targets
                    : List.of(targets.get(server.overworld().getRandom().nextInt(targets.size())));
            for (ServerPlayer player : bossTargets) {
                spawnBoss(player, event);
            }
        }
        save();
    }

    private static int aliveFor(UUID player) {
        int count = 0;
        for (Hunter hunter : hunters.values()) {
            if (hunter.player.equals(player)) {
                count++;
            }
        }
        return count;
    }

    /** Keeps hunters on their player: monsters drop targets beyond their follow range, hunters do not. */
    private static void steer(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Hunter>> it = hunters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Hunter> entry = it.next();
            Entity entity = find(server, entry.getKey());
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getValue().player);
            if (entity == null || !entity.isAlive()) {
                it.remove();
                continue;
            }
            if (!(entity instanceof Mob mob) || player == null || !player.isAlive() || player.level() != mob.level()
                    || player.isSpectator() || player.isCreative()) {
                continue;
            }
            if (mob.getTarget() != player) {
                mob.setTarget(player);
            }
            if (mob.distanceToSqr(player) > 12 * 12) {
                mob.getNavigation().moveTo(player, 1.1);
            }
        }
    }

    private static @Nullable Entity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static @Nullable Mob spawnHunter(ServerPlayer player, EventsConfig.Event event, boolean boss) {
        ServerLevel level = player.level();
        EntityType<?> type = pick(level.getRandom(), boss ? ModTags.EVENT_MINI_BOSSES : ModTags.EVENT_HUNTERS);
        if (type == null) {
            return null;
        }
        BlockPos pos = findSpot(level, player, type, event.hunters());
        if (pos == null) {
            return null;
        }
        Rank minRank = boss ? event.miniBoss().rank() : event.hunters().minRank();
        int bonus = boss ? event.miniBoss().levelBonus() : 0;
        return spawn(level, player, type, pos, minRank, bonus, event.hunters().named() || boss, boss, event.id());
    }

    /** Creates a ranked, leveled hunter at {@code pos} and sends it after {@code player}. */
    public static @Nullable Mob spawn(ServerLevel level, ServerPlayer player, EntityType<?> type, BlockPos pos, Rank minRank,
                                      int levelBonus, boolean named, boolean boss, String eventId) {
        Entity created = type.create(level, EntitySpawnReason.EVENT);
        if (!(created instanceof Mob mob)) {
            if (created != null) {
                created.discard();
            }
            return null;
        }
        RandomSource random = level.getRandom();
        mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360F, 0F);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        Threat threat = Threat.at(level, pos, true);
        int mobLevel = Mth.clamp(threat.level() + levelBonus, threat.min(), threat.max());
        Rank rank = Rank.COMMON;
        Set<Trait> traits = java.util.EnumSet.noneOf(Trait.class);
        MobLeveler.Kind kind = MobLeveler.kind(mob);
        if (kind == MobLeveler.Kind.HOSTILE && !ModTags.is(type, ModTags.NEVER_RANKED)) {
            Rank rolled = MobLeveler.rollRank(random, mobLevel, false, false);
            rank = rolled.atLeast(minRank) ? rolled : minRank;
            traits = MobLeveler.rollTraits(random, type, rank, RemnantsConfig.ranks().stats(rank).traits());
        }
        long seed = random.nextLong();
        if (named) {
            mob.setCustomName(Component.literal(Titles.of(seed)));
        }
        int flags = kind == MobLeveler.Kind.BOSS ? MobData.BOSS : kind == MobLeveler.Kind.PASSIVE ? MobData.PASSIVE : 0;
        MobLeveler.set(mob, new MobData(Math.max(0, mobLevel), rank, traits, seed, flags));
        mob.addTag(TAG);
        mob.addTag(TAG + "." + eventId);
        if (boss) {
            mob.addTag(BOSS_TAG);
            mob.setPersistenceRequired();
        }
        mob.setHealth(mob.getMaxHealth());
        if (!level.addFreshEntity(mob)) {
            return null;
        }
        mob.setTarget(player);
        if (RemnantsConfig.elites().enabled && RemnantsConfig.elites().eventGlow) {
            int ticks = (int) Math.min(Integer.MAX_VALUE, Math.max(20, remaining(level.getServer())));
            mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, ticks, 0,
                    false, false));
        }
        hunters.put(mob.getUUID(), new Hunter(player.getUUID(), boss));
        return mob;
    }

    private static void spawnBoss(ServerPlayer player, EventsConfig.Event event) {
        BossTier tier = TIERS.getOrDefault(event.miniBoss().tier().toLowerCase(Locale.ROOT), TIERS.get("mini"));
        ServerLevel level = player.level();
        BlockPos pos = findSpot(level, player, EntityTypes.VINDICATOR, event.hunters());
        if (pos == null) {
            return;
        }
        Mob boss;
        try {
            boss = tier.spawn(level, player, pos, event.id());
        } catch (RuntimeException e) {
            RemnantMobs.LOG.warn("Boss tier {} failed to spawn", event.miniBoss().tier(), e);
            return;
        }
        if (boss == null) {
            return;
        }
        hunters.putIfAbsent(boss.getUUID(), new Hunter(player.getUUID(), true));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModContent.REMNANT_HORN.get(), SoundSource.HOSTILE, 1.0F, 0.7F);
        player.sendOverlayMessage(text("event.remnant_mobs.boss", "%s has found your trail", boss.getDisplayName())
                .withStyle(ChatFormatting.DARK_RED));
    }

    /** The built-in "mini" tier: a monster from #remnant_mobs:event_mini_bosses at the event's boss rank. */
    private static @Nullable Mob spawnMiniBoss(ServerLevel level, ServerPlayer target, BlockPos pos, String eventId) {
        EventsConfig.Event event = RemnantsConfig.events().events.get(eventId);
        EntityType<?> type = pick(level.getRandom(), ModTags.EVENT_MINI_BOSSES);
        if (event == null || type == null) {
            return null;
        }
        BlockPos spot = findSpot(level, target, type, event.hunters());
        return spawn(level, target, type, spot != null ? spot : pos, event.miniBoss().rank(), event.miniBoss().levelBonus(),
                true, true, eventId);
    }

    private static @Nullable EntityType<?> pick(RandomSource random, TagKey<EntityType<?>> tag) {
        List<EntityType<?>> types = new ArrayList<>();
        for (Holder<EntityType<?>> holder : BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(tag)) {
            types.add(holder.value());
        }
        return types.isEmpty() ? null : types.get(random.nextInt(types.size()));
    }

    /** A spot distance_min to distance_max blocks from the player where {@code type} can stand. */
    static @Nullable BlockPos findSpot(ServerLevel level, ServerPlayer player, EntityType<?> type, EventsConfig.Hunters h) {
        RandomSource random = level.getRandom();
        BlockPos origin = player.blockPosition();
        boolean surface = level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling()
                && origin.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ()) - 6;
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = h.distanceMin() + random.nextDouble() * (h.distanceMax() - h.distanceMin());
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!level.hasChunkAt(new BlockPos(x, origin.getY(), z))) {
                continue;
            }
            if (surface) {
                BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (fits(level, pos, type)) {
                    return pos;
                }
                continue;
            }
            for (int dy = 6; dy >= -6; dy--) {
                BlockPos pos = new BlockPos(x, origin.getY() + dy, z);
                if (fits(level, pos, type)) {
                    return pos;
                }
            }
        }
        return null;
    }

    private static boolean fits(ServerLevel level, BlockPos pos, EntityType<?> type) {
        return level.getWorldBorder().isWithinBounds(pos)
                && SpawnPlacements.isSpawnPositionOk(type, level, pos)
                && level.noCollision(type.getDimensions().makeBoundingBox(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
    }

    // ---------------------------------------------------------------- text

    /** Translated text that still reads well on clients without the mod's language file. */
    static net.minecraft.network.chat.MutableComponent text(String key, String fallback, Object... args) {
        return Component.translatableWithFallback(key, fallback, args);
    }

    /** "grim_vigil" -> "The Grim Vigil" for events without a language entry. */
    public static String pretty(String id) {
        StringBuilder out = new StringBuilder("The");
        for (String part : id.split("_")) {
            if (!part.isEmpty()) {
                out.append(' ').append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return out.toString();
    }
}
