package com.nightbeam.remnants.mobs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.nightbeam.remnants.mobs.RemnantMobs;
import com.nightbeam.remnants.mobs.compat.RasBridge;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import com.nightbeam.remnants.mobs.content.Rank;
import com.nightbeam.remnants.mobs.content.Trait;
import com.nightbeam.remnants.mobs.config.EventsConfig;
import com.nightbeam.remnants.mobs.difficulty.Difficulty;
import com.nightbeam.remnants.mobs.event.EventNights;
import com.nightbeam.remnants.mobs.event.HuntersMoon;
import com.nightbeam.remnants.mobs.mob.MobData;
import com.nightbeam.remnants.mobs.mob.MobLeveler;
import com.nightbeam.remnants.mobs.mob.RemnantMob;
import com.nightbeam.remnants.mobs.mob.Threat;
import com.nightbeam.remnants.mobs.mob.Titles;
import com.nightbeam.remnants.mobs.network.Network;
import com.nightbeam.remnants.mobs.progression.Hearts;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** {@code /remnants} (alias {@code /rml}). */
public final class RemnantsCommand {
    private static final SimpleCommandExceptionType NO_TARGET =
            new SimpleCommandExceptionType(Component.translatable("commands.remnant_mobs.inspect.none"));
    private static final DynamicCommandExceptionType NOT_A_MOB =
            new DynamicCommandExceptionType(name -> Component.translatable("commands.remnant_mobs.not_a_mob", name));
    private static final DynamicCommandExceptionType UNKNOWN_RANK =
            new DynamicCommandExceptionType(key -> Component.translatable("commands.remnant_mobs.unknown_rank", key));
    private static final DynamicCommandExceptionType UNKNOWN_TRAIT =
            new DynamicCommandExceptionType(key -> Component.translatable("commands.remnant_mobs.unknown_trait", key));
    private static final DynamicCommandExceptionType UNKNOWN_EVENT =
            new DynamicCommandExceptionType(id -> Component.translatableWithFallback("commands.remnant_mobs.event.unknown", "Unknown event: %s", id));
    private static final SuggestionProvider<CommandSourceStack> EVENTS = (c, b) ->
            SharedSuggestionProvider.suggest(RemnantsConfig.events().events.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> RANKS = (c, b) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Rank.values()).map(Rank::key), b);
    private static final SuggestionProvider<CommandSourceStack> TRAITS = (c, b) -> {
        String remaining = b.getRemaining();
        int space = remaining.lastIndexOf(' ');
        var sub = b.createOffset(b.getStart() + space + 1);
        List<String> options = new ArrayList<>(Arrays.stream(Trait.values()).map(Trait::key).toList());
        options.add("none");
        return SharedSuggestionProvider.suggest(options, sub);
    };

    private RemnantsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        java.util.function.Predicate<CommandSourceStack> op = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("remnants")
                .then(Commands.literal("threat").executes(RemnantsCommand::threat))
                .then(Commands.literal("inspect")
                        .executes(c -> inspect(c, lookedAt(c.getSource())))
                        .then(Commands.argument("target", EntityArgument.entity()).requires(op)
                                .executes(c -> inspect(c, mob(EntityArgument.getEntity(c, "target"))))))
                .then(Commands.literal("spawn").requires(op)
                        .then(Commands.argument("entity", ResourceArgument.resource(context, Registries.ENTITY_TYPE))
                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 10000))
                                        .executes(c -> spawn(c, null, ""))
                                        .then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                                .executes(c -> spawn(c, rank(StringArgumentType.getString(c, "rank")), ""))
                                                .then(Commands.argument("traits", StringArgumentType.greedyString()).suggests(TRAITS)
                                                        .executes(c -> spawn(c, rank(StringArgumentType.getString(c, "rank")),
                                                                StringArgumentType.getString(c, "traits"))))))))
                .then(Commands.literal("set").requires(op)
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.literal("level").then(Commands.argument("level", IntegerArgumentType.integer(0, 10000))
                                        .executes(c -> set(c, data -> data.withLevel(IntegerArgumentType.getInteger(c, "level"))))))
                                .then(Commands.literal("rank").then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
                                        .executes(c -> {
                                            Rank rank = rank(StringArgumentType.getString(c, "rank"));
                                            return set(c, data -> data.withRank(rank));
                                        })))
                                .then(Commands.literal("traits").then(Commands.argument("traits", StringArgumentType.greedyString()).suggests(TRAITS)
                                        .executes(c -> {
                                            Set<Trait> traits = traits(StringArgumentType.getString(c, "traits"));
                                            return set(c, data -> data.withTraits(traits));
                                        })))
                                .then(Commands.literal("reroll").executes(RemnantsCommand::reroll))
                                .then(Commands.literal("clear").executes(RemnantsCommand::clear))))
                .then(Commands.literal("moon")
                        .executes(RemnantsCommand::moonStatus)
                        .then(Commands.literal("start").requires(op).executes(c -> {
                            HuntersMoon.start(c.getSource().getServer());
                            c.getSource().sendSuccess(() -> Component.translatable("commands.remnant_mobs.moon.started"), true);
                            return 1;
                        }))
                        .then(Commands.literal("stop").requires(op).executes(c -> {
                            HuntersMoon.stop(c.getSource().getServer(), true);
                            c.getSource().sendSuccess(() -> Component.translatable("commands.remnant_mobs.moon.stopped"), true);
                            return 1;
                        })))
                .then(Commands.literal("difficulty")
                        .executes(c -> difficulty(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player()).requires(op)
                                .executes(c -> difficulty(c, EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("hearts")
                        .executes(c -> hearts(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player()).requires(op)
                                .executes(c -> hearts(c, EntityArgument.getPlayer(c, "player")))
                                .then(Commands.literal("set").then(Commands.argument("hearts", IntegerArgumentType.integer(0, 100))
                                        .executes(c -> {
                                            ServerPlayer player = EntityArgument.getPlayer(c, "player");
                                            Hearts.set(player, IntegerArgumentType.getInteger(c, "hearts"));
                                            return hearts(c, player);
                                        })))))
                .then(Commands.literal("event")
                        .executes(RemnantsCommand::eventStatus)
                        .then(Commands.literal("list").executes(RemnantsCommand::eventList))
                        .then(Commands.literal("start").requires(op)
                                .then(Commands.argument("id", StringArgumentType.word()).suggests(EVENTS)
                                        .executes(c -> eventStart(c, StringArgumentType.getString(c, "id")))))
                        .then(Commands.literal("stop").requires(op).executes(c -> {
                            boolean running = EventNights.active() != null;
                            EventNights.stop(c.getSource().getServer(), false);
                            c.getSource().sendSuccess(() -> running
                                    ? Component.translatableWithFallback("commands.remnant_mobs.event.stopped", "The event night is over")
                                    : Component.translatableWithFallback("commands.remnant_mobs.event.none", "No event night is running"), true);
                            return running ? 1 : 0;
                        })))
                .then(Commands.literal("reload").requires(op).executes(RemnantsCommand::reload));
        dispatcher.register(root);
        dispatcher.register(Commands.literal("rml").redirect(dispatcher.getRoot().getChild("remnants")));
    }

    private static int threat(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        Threat t = Threat.at(level, pos, true);
        int spread = RemnantsConfig.levels().spread;
        source.sendSuccess(() -> Component.translatable("commands.remnant_mobs.threat.header",
                Component.literal(String.valueOf(t.level())).withStyle(ChatFormatting.GOLD),
                Component.literal(level.dimension().identifier().toString()).withStyle(ChatFormatting.GRAY), t.min(), t.max()), false);
        MutableComponent parts = Component.translatable("commands.remnant_mobs.threat.parts", t.min(),
                one(t.distance()), one(t.days()), one(t.depth()), t.moon()).withStyle(ChatFormatting.GRAY);
        source.sendSuccess(() -> parts, false);
        if (t.structureId() != null) {
            source.sendSuccess(() -> Component.translatable("commands.remnant_mobs.threat.structure", t.structureId(), t.structure())
                    .withStyle(ChatFormatting.GRAY), false);
        }
        if (t.players() > 0) {
            source.sendSuccess(() -> Component.translatable("commands.remnant_mobs.threat.players", t.players(), one(t.playerLevel()),
                    Math.round(t.weight() * 100)).withStyle(ChatFormatting.GRAY), false);
        } else if (!RasBridge.present() && "ras".equals(RemnantsConfig.levels().playerSource)) {
            source.sendSuccess(() -> Component.translatable("commands.remnant_mobs.threat.no_ras").withStyle(ChatFormatting.DARK_GRAY), false);
        }
        source.sendSuccess(() -> Component.translatable("commands.remnant_mobs.threat.range",
                Math.max(t.min(), t.level() - spread), Math.min(t.max(), t.level() + spread)), false);
        return t.level();
    }

    /** The difficulty meter: where a player stands, as a share of their dimension's band, and why. */
    private static int difficulty(CommandContext<CommandSourceStack> c, ServerPlayer player) {
        CommandSourceStack source = c.getSource();
        ServerLevel level = player.level();
        Threat t = Threat.at(level, player.blockPosition(), true, true);
        int percent = (int) Math.round(t.fraction() * 100);
        ChatFormatting color = percent >= 75 ? ChatFormatting.DARK_RED : percent >= 50 ? ChatFormatting.RED
                : percent >= 25 ? ChatFormatting.GOLD : ChatFormatting.GREEN;
        source.sendSuccess(() -> Component.translatableWithFallback("commands.remnant_mobs.difficulty.header",
                "Difficulty for %s: %s  (level %s of %s-%s, %s)", player.getDisplayName(),
                Component.literal(percent + "%").withStyle(color), t.level(), t.min(), t.max(),
                level.dimension().identifier().toString()).withStyle(ChatFormatting.GOLD), false);
        if (t.engine()) {
            source.sendSuccess(() -> Component.translatableWithFallback("commands.remnant_mobs.difficulty.parts",
                    "  base %s + distance %s + days %s + depth %s + played %s + gear %s", t.min(), one(t.distance()), one(t.days()),
                    one(t.depth()), one(t.played()), one(t.gear())).withStyle(ChatFormatting.GRAY), false);
            if (t.multiplier() != 1.0 || t.offset() != 0) {
                source.sendSuccess(() -> Component.translatableWithFallback("commands.remnant_mobs.difficulty.dimension",
                        "  x %s %s this dimension", one(t.multiplier()), (t.offset() >= 0 ? "+ " : "- ") + one(Math.abs(t.offset())))
                        .withStyle(ChatFormatting.GRAY), false);
            }
            double odds = Difficulty.rankMultiplier(RemnantsConfig.difficulty(), t.fraction()) * EventNights.rankMultiplier(level);
            source.sendSuccess(() -> Component.translatableWithFallback("commands.remnant_mobs.difficulty.ranks",
                    "  rank odds x%s", one(odds)).withStyle(ChatFormatting.GRAY), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.remnant_mobs.threat.parts", t.min(),
                    one(t.distance()), one(t.days()), one(t.depth()), t.moon()).withStyle(ChatFormatting.GRAY), false);
            source.sendSuccess(() -> Component.translatableWithFallback("commands.remnant_mobs.difficulty.off",
                    "  (the difficulty engine is off here: see difficulty.json)").withStyle(ChatFormatting.DARK_GRAY), false);
        }
        if (t.moon() > 0 || t.event() > 0) {
            source.sendSuccess(() -> Component.translatableWithFallback("commands.remnant_mobs.difficulty.night",
                    "  tonight: +%s levels", t.moon() + t.event()).withStyle(ChatFormatting.RED), false);
        }
        if (t.structureId() != null) {
            source.sendSuccess(() -> Component.translatable("commands.remnant_mobs.threat.structure", t.structureId(), t.structure())
                    .withStyle(ChatFormatting.GRAY), false);
        }
        if (t.players() > 0) {
            source.sendSuccess(() -> Component.translatable("commands.remnant_mobs.threat.players", t.players(), one(t.playerLevel()),
                    Math.round(t.weight() * 100)).withStyle(ChatFormatting.GRAY), false);
        }
        return percent;
    }

    private static int hearts(CommandContext<CommandSourceStack> c, ServerPlayer player) {
        int hearts = Hearts.get(player);
        int max = RemnantsConfig.progression().maxBonusHearts;
        c.getSource().sendSuccess(() -> Component.translatableWithFallback("commands.remnant_mobs.hearts",
                "%s has %s of %s extra hearts from Vital Embers", player.getDisplayName(), hearts, max), false);
        return hearts;
    }

    private static int eventStatus(CommandContext<CommandSourceStack> c) {
        var server = c.getSource().getServer();
        String id = EventNights.active();
        if (id != null) {
            EventsConfig.Event event = EventNights.activeEvent();
            long seconds = EventNights.remaining(server) / 20;
            int waves = event != null ? event.hunters().waves() : 0;
            c.getSource().sendSuccess(() -> Component.translatableWithFallback("commands.remnant_mobs.event.active",
                    "%s is running: wave %s/%s, %s min %s s until dawn",
                    eventName(id), EventNights.wavesSent(), waves, seconds / 60, seconds % 60).withStyle(ChatFormatting.RED), false);
            return 1;
        }
        boolean enabled = RemnantsConfig.events().enabled;
        c.getSource().sendSuccess(() -> enabled
                ? Component.translatableWithFallback("commands.remnant_mobs.event.none", "No event night is running")
                : Component.translatableWithFallback("commands.remnant_mobs.event.disabled", "Event nights are turned off in events.json"), false);
        return 0;
    }

    private static int eventList(CommandContext<CommandSourceStack> c) {
        var server = c.getSource().getServer();
        long day = com.nightbeam.remnants.mobs.mob.WorldTime.dayIndex(server.overworld());
        c.getSource().sendSuccess(() -> Component.translatableWithFallback("commands.remnant_mobs.event.list_moon",
                " ◆ Hunter's Moon (levels.json): %s, %s%% on full moons",
                RemnantsConfig.levels().moonEnabled ? "on" : "off", Math.round(RemnantsConfig.levels().moonChance * 100)), false);
        for (EventsConfig.Event event : RemnantsConfig.events().events.values()) {
            EventsConfig.Trigger t = event.trigger();
            long next = EventNights.nextDay(t, day);
            c.getSource().sendSuccess(() -> Component.translatableWithFallback("commands.remnant_mobs.event.list_entry",
                    " ◆ %s (%s): %s, %s%% every %s nights from day %s; next chance on day %s",
                    eventName(event.id()), event.id(), event.enabled() && RemnantsConfig.events().enabled ? "on" : "off",
                    Math.round(t.chance() * 100), t.everyDays(), t.firstDay(), next), false);
        }
        return RemnantsConfig.events().events.size();
    }

    private static int eventStart(CommandContext<CommandSourceStack> c, String id) throws CommandSyntaxException {
        EventsConfig.Event event = RemnantsConfig.events().events.get(id);
        if (event == null) {
            throw UNKNOWN_EVENT.create(id);
        }
        EventNights.start(c.getSource().getServer(), event, true);
        c.getSource().sendSuccess(() -> Component.translatableWithFallback("commands.remnant_mobs.event.started",
                "%s begins and lasts until dawn", eventName(id)), true);
        return 1;
    }

    private static Component eventName(String id) {
        return Component.translatableWithFallback("event.remnant_mobs." + id + ".title", EventNights.pretty(id));
    }

    private static String one(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static Mob lookedAt(CommandSourceStack source) throws CommandSyntaxException {
        Entity entity = source.getEntityOrException();
        Vec3 eye = entity.getEyePosition();
        Vec3 end = eye.add(entity.getLookAngle().scale(32.0));
        Mob best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Mob mob : source.getLevel().getEntitiesOfClass(Mob.class, new AABB(eye, end).inflate(1.0), m -> m.isAlive() && m != entity)) {
            var hit = mob.getBoundingBox().inflate(0.3).clip(eye, end);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < bestDistance) {
                bestDistance = eye.distanceToSqr(hit.get());
                best = mob;
            }
        }
        if (best == null) {
            throw NO_TARGET.create();
        }
        return best;
    }

    private static Mob mob(Entity entity) throws CommandSyntaxException {
        if (entity instanceof Mob mob) {
            return mob;
        }
        throw NOT_A_MOB.create(entity.getDisplayName());
    }

    private static int inspect(CommandContext<CommandSourceStack> c, Mob mob) {
        CommandSourceStack source = c.getSource();
        MobData data = RemnantMob.data(mob);
        if (data == null) {
            source.sendSuccess(() -> Component.translatable("commands.remnant_mobs.inspect.unleveled", mob.getDisplayName()), false);
            return 0;
        }
        Component rank = Component.translatable(data.rank().translationKey()).withStyle(style -> style.withColor(data.rank().color()));
        source.sendSuccess(() -> Component.translatable("commands.remnant_mobs.inspect.header", mob.getDisplayName(), data.level(), rank)
                .withStyle(ChatFormatting.GOLD), false);
        double damage = mob.getAttribute(Attributes.ATTACK_DAMAGE) != null ? mob.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0;
        var speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        long speedBonus = speed == null || speed.getBaseValue() <= 0 ? 0 : Math.round((speed.getValue() / speed.getBaseValue() - 1.0) * 100);
        source.sendSuccess(() -> Component.translatable("commands.remnant_mobs.inspect.stats",
                one(mob.getHealth()), one(mob.getMaxHealth()), one(damage), one(mob.getArmorValue()),
                (speedBonus >= 0 ? "+" : "") + speedBonus + "%"), false);
        if (!data.traits().isEmpty()) {
            for (Trait trait : data.traitList()) {
                source.sendSuccess(() -> Component.literal(" ◆ ").withStyle(style -> style.withColor(trait.color()))
                        .append(Component.translatable(trait.translationKey()).withStyle(style -> style.withColor(trait.color())))
                        .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.translatable(trait.descriptionKey()).withStyle(ChatFormatting.GRAY)), false);
            }
        }
        List<String> flags = new ArrayList<>();
        if (data.flag(MobData.BRANDED)) {
            flags.add("branded");
        }
        if (data.flag(MobData.MOONLIT)) {
            flags.add("moonlit");
        }
        if (data.flag(MobData.SPAWNER)) {
            flags.add("spawner");
        }
        if (data.flag(MobData.MINION)) {
            flags.add("minion");
        }
        if (data.flag(MobData.BOSS)) {
            flags.add("boss");
        }
        if (!flags.isEmpty()) {
            MutableComponent line = Component.empty();
            for (int i = 0; i < flags.size(); i++) {
                if (i > 0) {
                    line.append(Component.literal(" · "));
                }
                line.append(Component.translatable("commands.remnant_mobs.inspect.flag." + flags.get(i)));
            }
            source.sendSuccess(() -> line.withStyle(ChatFormatting.DARK_AQUA), false);
        }
        return data.level();
    }

    private static Rank rank(String key) throws CommandSyntaxException {
        Rank rank = Rank.byKey(key);
        if (rank == null) {
            throw UNKNOWN_RANK.create(key);
        }
        return rank;
    }

    private static Set<Trait> traits(String text) throws CommandSyntaxException {
        EnumSet<Trait> traits = EnumSet.noneOf(Trait.class);
        for (String key : text.trim().split("[\\s,]+")) {
            if (key.isEmpty() || key.equalsIgnoreCase("none")) {
                continue;
            }
            Trait trait = Trait.byKey(key);
            if (trait == null) {
                throw UNKNOWN_TRAIT.create(key);
            }
            if (traits.stream().noneMatch(trait::conflictsWith)) {
                traits.add(trait);
            }
        }
        return traits;
    }

    private static int spawn(CommandContext<CommandSourceStack> c, @Nullable Rank rank, String traitText) throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        EntityType<?> type = ResourceArgument.getSummonableEntityType(c, "entity").value();
        int level = IntegerArgumentType.getInteger(c, "level");
        Set<Trait> traits = traits(traitText);
        ServerLevel world = source.getLevel();
        Entity created = type.create(world, EntitySpawnReason.COMMAND);
        if (!(created instanceof Mob mob)) {
            throw NOT_A_MOB.create(type.getDescription());
        }
        Vec3 pos = source.getPosition();
        mob.snapTo(pos.x, pos.y, pos.z, source.getRotation().y, 0.0F);
        mob.finalizeSpawn(world, world.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.COMMAND, null);
        MobLeveler.Kind kind = MobLeveler.kind(mob);
        Rank finalRank = rank != null ? rank : Rank.COMMON;
        if (rank == null && kind == MobLeveler.Kind.HOSTILE) {
            finalRank = MobLeveler.rollRank(mob.getRandom(), level, false, false);
        }
        if (traitText.isBlank() && finalRank != Rank.COMMON) {
            traits = MobLeveler.rollTraits(mob.getRandom(), type, finalRank, RemnantsConfig.ranks().stats(finalRank).traits());
        }
        int flags = kind == MobLeveler.Kind.BOSS ? MobData.BOSS : kind == MobLeveler.Kind.PASSIVE ? MobData.PASSIVE : 0;
        MobData data = new MobData(level, finalRank, traits, mob.getRandom().nextLong(), flags);
        MobLeveler.set(mob, data);
        mob.setHealth(mob.getMaxHealth());
        world.addFreshEntityWithPassengers(mob);
        Component rankName = Component.translatable(finalRank.translationKey()).withStyle(style -> style.withColor(data.rank().color()));
        source.sendSuccess(() -> Component.translatable("commands.remnant_mobs.spawn", mob.getDisplayName(), level, rankName), true);
        return 1;
    }

    private interface Change {
        MobData apply(MobData data);
    }

    private static int set(CommandContext<CommandSourceStack> c, Change change) throws CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(c, "targets");
        int changed = 0;
        for (Entity entity : targets) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            MobData data = RemnantMob.data(mob);
            if (data == null) {
                data = new MobData(1, Rank.COMMON, EnumSet.noneOf(Trait.class), mob.getRandom().nextLong(), 0);
            }
            MobData updated = change.apply(data);
            if (updated.rank() == Rank.REMNANT && data.rank() != Rank.REMNANT && !mob.hasCustomName()) {
                mob.setCustomName(Component.literal(Titles.of(updated.seed())));
            }
            MobLeveler.set(mob, updated);
            changed++;
        }
        int count = changed;
        c.getSource().sendSuccess(() -> Component.translatable("commands.remnant_mobs.set", count), true);
        return count;
    }

    private static int reroll(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int changed = 0;
        for (Entity entity : EntityArgument.getEntities(c, "targets")) {
            if (entity instanceof Mob mob && entity.level() instanceof ServerLevel level) {
                MobLeveler.Kind kind = MobLeveler.kind(mob);
                if (kind == MobLeveler.Kind.NONE) {
                    continue;
                }
                MobLeveler.set(mob, MobLeveler.roll(level, mob, kind, false));
                changed++;
            }
        }
        int count = changed;
        c.getSource().sendSuccess(() -> Component.translatable("commands.remnant_mobs.set", count), true);
        return count;
    }

    private static int clear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        int changed = 0;
        for (Entity entity : EntityArgument.getEntities(c, "targets")) {
            if (entity instanceof Mob mob) {
                MobLeveler.reset(mob);
                changed++;
            }
        }
        int count = changed;
        c.getSource().sendSuccess(() -> Component.translatable("commands.remnant_mobs.cleared", count), true);
        return count;
    }

    private static int moonStatus(CommandContext<CommandSourceStack> c) {
        var server = c.getSource().getServer();
        if (HuntersMoon.active()) {
            long seconds = HuntersMoon.remaining(server) / 20;
            c.getSource().sendSuccess(() -> Component.translatable("commands.remnant_mobs.moon.active", seconds / 60, seconds % 60)
                    .withStyle(ChatFormatting.RED), false);
        } else {
            c.getSource().sendSuccess(() -> Component.translatable(RemnantsConfig.levels().moonEnabled
                    ? "commands.remnant_mobs.moon.inactive" : "commands.remnant_mobs.moon.disabled"), false);
        }
        return HuntersMoon.active() ? 1 : 0;
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        RemnantsConfig.load(RemnantMobs.platform().configDir());
        Network.clear();
        Network.syncPlayers(c.getSource().getServer());
        c.getSource().sendSuccess(() -> Component.translatable("commands.remnant_mobs.reload"), true);
        return 1;
    }
}
