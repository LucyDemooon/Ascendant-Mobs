package com.nightbeam.remnants.mobs.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nightbeam.remnants.mobs.content.Rank;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.nightbeam.remnants.mobs.config.Json.arr;
import static com.nightbeam.remnants.mobs.config.Json.obj;

/**
 * {@code config/remnant_mobs/events.json}: event nights. Each entry under {@code events} is one kind of night; copy
 * one under a new id to add another. The Hunter's Moon keeps its settings in levels.json.
 */
public final class EventsConfig {
    public final boolean enabled;
    public final Map<String, Event> events = new LinkedHashMap<>();

    public record Trigger(int everyDays, int firstDay, double chance, double minDifficulty, String moon) {
    }

    public record Hunters(int waves, int perWave, int waveSeconds, int firstWaveSeconds, Rank minRank, boolean named,
                          int distanceMin, int distanceMax, int maxAlive) {
    }

    public record MiniBoss(boolean enabled, boolean perPlayer, Rank rank, int levelBonus, String tier) {
    }

    public record Rewards(int surviveExperience, double surviveValor, int bossShards) {
    }

    public record Event(String id, boolean enabled, Trigger trigger, boolean title, boolean chat, boolean sound,
                        int levelBonus, double rankMultiplier, double shardMultiplier, Hunters hunters, MiniBoss miniBoss,
                        Rewards rewards, List<String> dimensions) {
    }

    private EventsConfig(JsonObject o) {
        enabled = Json.bool(o, "enabled", false);
        for (Map.Entry<String, JsonElement> entry : Json.object(o, "events").entrySet()) {
            if (entry.getValue().isJsonObject()) {
                events.put(entry.getKey(), event(entry.getKey(), entry.getValue().getAsJsonObject()));
            }
        }
    }

    private static Event event(String id, JsonObject e) {
        Trigger trigger = new Trigger(
                Math.max(1, Json.integer(e, "trigger.every_days", 10)),
                Math.max(0, Json.integer(e, "trigger.first_day", 7)),
                clamp01(Json.num(e, "trigger.chance", 0.5)),
                clamp01(Json.num(e, "trigger.min_difficulty", 0)),
                Json.str(e, "trigger.moon", "any").toLowerCase(Locale.ROOT));
        int distanceMin = Math.max(8, Json.integer(e, "hunters.distance_min", 20));
        Hunters hunters = new Hunters(
                Math.max(0, Math.min(20, Json.integer(e, "hunters.waves", 3))),
                Math.max(0, Math.min(16, Json.integer(e, "hunters.per_wave", 3))),
                Math.max(5, Json.integer(e, "hunters.wave_seconds", 90)),
                Math.max(0, Json.integer(e, "hunters.first_wave_seconds", 20)),
                rank(Json.str(e, "hunters.min_rank", "veteran"), Rank.VETERAN),
                Json.bool(e, "hunters.named", true),
                distanceMin,
                Math.max(distanceMin, Json.integer(e, "hunters.distance_max", 32)),
                Math.max(1, Json.integer(e, "hunters.max_alive", 6)));
        MiniBoss boss = new MiniBoss(
                Json.bool(e, "mini_boss.enabled", true),
                Json.bool(e, "mini_boss.per_player", false),
                rank(Json.str(e, "mini_boss.rank", "remnant"), Rank.REMNANT),
                Math.max(0, Json.integer(e, "mini_boss.level_bonus", 5)),
                Json.str(e, "mini_boss.tier", "mini"));
        Rewards rewards = new Rewards(
                Math.max(0, Json.integer(e, "rewards.survive_experience", 50)),
                Math.max(0.0, Json.num(e, "rewards.survive_valor", 40)),
                Math.max(0, Json.integer(e, "rewards.boss_shards", 8)));
        return new Event(id, Json.bool(e, "enabled", true), trigger,
                Json.bool(e, "announce.title", true), Json.bool(e, "announce.chat", true), Json.bool(e, "announce.sound", true),
                Math.max(0, Json.integer(e, "effects.level_bonus", 3)),
                Math.max(0.0, Json.num(e, "effects.rank_multiplier", 1.5)),
                Math.max(0.0, Json.num(e, "effects.shard_multiplier", 1.5)),
                hunters, boss, rewards, Json.strings(e, "dimensions"));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static Rank rank(String key, Rank fallback) {
        Rank rank = Rank.byKey(key);
        return rank != null ? rank : fallback;
    }

    public static EventsConfig from(JsonObject json) {
        return new EventsConfig(json);
    }

    public static JsonObject grimVigil() {
        return obj(
                "enabled", true,
                "trigger", obj("every_days", 10, "first_day", 7, "chance", 0.5, "min_difficulty", 0.0, "moon", "any"),
                "announce", obj("title", true, "chat", true, "sound", true),
                "effects", obj("level_bonus", 3, "rank_multiplier", 1.5, "shard_multiplier", 1.5),
                "hunters", obj("waves", 3, "per_wave", 3, "wave_seconds", 90, "first_wave_seconds", 20, "min_rank", "veteran",
                        "named", true, "distance_min", 20, "distance_max", 32, "max_alive", 6),
                "mini_boss", obj("enabled", true, "per_player", false, "rank", "remnant", "level_bonus", 5, "tier", "mini"),
                "rewards", obj("survive_experience", 50, "survive_valor", 40, "boss_shards", 8),
                "dimensions", arr("minecraft:overworld"));
    }

    /** @param fresh true on a fresh install (events start on); false when an older config was found */
    public static CommentedJson spec(boolean fresh) {
        JsonObject d = obj(
                "enabled", fresh,
                "events", obj("grim_vigil", grimVigil()));
        return new CommentedJson(d)
                .header("Remnants: RPG Mobs 3.1: event nights.\n"
                        + "New in 3.1. When this file is first created next to an existing config, events start switched off;\n"
                        + "fresh installs start with them on. The Hunter's Moon is configured in levels.json, as before.\n"
                        + "Edit and run /remnants reload, or restart. Missing keys are restored with their defaults.")
                .comment("enabled", "Master switch for the events below (not the Hunter's Moon). One event runs at a time and never\n"
                        + "during a Hunter's Moon. /remnants event start <id> starts one now.")
                .comment("events", "One entry per kind of night; copy one under a new id to add another.\n"
                        + "trigger: checked at dusk. Rolls chance every every_days nights from first_day, when at least one\n"
                        + "  player stands at min_difficulty (0-1, bottom to top of their dimension's band) or more.\n"
                        + "  moon: \"any\", \"full\" or \"new\".\n"
                        + "announce: title card, chat line and horn at dusk.\n"
                        + "effects: while it runs, new mobs get level_bonus levels and rank_multiplier x rank odds;\n"
                        + "  event monsters drop shard_multiplier x shards.\n"
                        + "hunters: waves of named, ranked monsters (#remnant_mobs:event_hunters) sent after each player,\n"
                        + "  per_wave per player every wave_seconds, spawning distance_min-distance_max blocks away, at most\n"
                        + "  max_alive per player.\n"
                        + "mini_boss: joins the last wave (#remnant_mobs:event_mini_bosses) with this rank, after one random player\n"
                        + "  (or every player with per_player). tier picks the boss\n"
                        + "  tier (\"mini\"; other mods can add tiers).\n"
                        + "rewards: players who see the dawn get survive_experience XP (and survive_valor Valor with RPG\n"
                        + "  Attribute System); the mini-boss drops boss_shards extra Remnant Shards.\n"
                        + "dimensions: dimension ids or #tags of dimension types where the event runs.");
    }
}
