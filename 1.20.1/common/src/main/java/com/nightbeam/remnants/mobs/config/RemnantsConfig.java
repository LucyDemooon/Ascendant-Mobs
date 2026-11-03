package com.nightbeam.remnants.mobs.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nightbeam.remnants.mobs.RemnantMobs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads and holds the files under {@code config/remnant_mobs/}. */
public final class RemnantsConfig {
    private static LevelsConfig levels = LevelsConfig.from(new JsonObject());
    private static RanksConfig ranks = RanksConfig.from(new JsonObject());
    private static TraitsConfig traits = TraitsConfig.from(new JsonObject());
    private static RewardsConfig rewards = RewardsConfig.from(new JsonObject());
    private static ClientConfig client = ClientConfig.from(new JsonObject());
    private static DifficultyConfig difficulty = DifficultyConfig.from(new JsonObject());
    private static EventsConfig events = EventsConfig.from(new JsonObject());
    private static ProgressionConfig progression = ProgressionConfig.from(new JsonObject());
    private static ElitesConfig elites = ElitesConfig.from(new JsonObject());
    private static LootConfig loot = LootConfig.from(new JsonObject());
    private static Path dir = Path.of("config", RemnantMobs.MOD_ID);

    private RemnantsConfig() {
    }

    public static LevelsConfig levels() {
        return levels;
    }

    public static RanksConfig ranks() {
        return ranks;
    }

    public static TraitsConfig traits() {
        return traits;
    }

    public static RewardsConfig rewards() {
        return rewards;
    }

    public static ClientConfig client() {
        return client;
    }

    public static DifficultyConfig difficulty() {
        return difficulty;
    }

    public static EventsConfig events() {
        return events;
    }

    public static ProgressionConfig progression() {
        return progression;
    }

    public static ElitesConfig elites() {
        return elites;
    }

    public static LootConfig loot() {
        return loot;
    }

    public static Path dir() {
        return dir;
    }

    /** Loads (or creates) every file. Safe to call again to reload. */
    public static synchronized void load(Path configRoot) {
        dir = configRoot.resolve(RemnantMobs.MOD_ID);
        Path legacy = configRoot.resolve("rpgmoblevelingsystem");
        // Decided before anything is written: files new in 3.1 start with their systems off next to an older config.
        boolean fresh = isFreshInstall(dir, legacy);
        CommentedJson levelsSpec = LevelsConfig.spec();
        if (!Files.exists(dir) && Files.isDirectory(legacy)) {
            LegacyMigration.migrate(legacy, dir, levelsSpec);
        }
        levels = LevelsConfig.from(levelsSpec.load(dir.resolve("levels.json")));
        ranks = RanksConfig.from(RanksConfig.spec().load(dir.resolve("ranks.json")));
        traits = TraitsConfig.from(TraitsConfig.spec().load(dir.resolve("traits.json")));
        rewards = RewardsConfig.from(RewardsConfig.spec().load(dir.resolve("rewards.json")));
        client = ClientConfig.from(ClientConfig.spec().load(dir.resolve("client.json")));
        Path difficultyFile = dir.resolve("difficulty.json");
        Path eventsFile = dir.resolve("events.json");
        Path progressionFile = dir.resolve("progression.json");
        Path elitesFile = dir.resolve("elites.json");
        Path lootFile = dir.resolve("loot.json");
        if (!fresh && (!Files.exists(difficultyFile) || !Files.exists(eventsFile) || !Files.exists(progressionFile)
                || !Files.exists(elitesFile) || !Files.exists(lootFile))) {
            RemnantMobs.LOG.info("Existing config found: systems new in 3.1 start switched off (see difficulty.json, events.json, "
                    + "progression.json, elites.json and loot.json)");
        }
        difficulty = DifficultyConfig.from(DifficultyConfig.spec(fresh, levels).load(difficultyFile));
        events = EventsConfig.from(EventsConfig.spec(fresh).load(eventsFile));
        progression = ProgressionConfig.from(ProgressionConfig.spec(fresh).load(progressionFile));
        elites = ElitesConfig.from(ElitesConfig.spec(fresh).load(elitesFile));
        loot = LootConfig.from(LootConfig.spec(fresh).load(lootFile));
        RemnantMobs.LOG.info("Loaded config from {}", dir);
    }

    /** No 3.x config and no 2.x config: a new install, where the 3.1 systems start on. */
    static boolean isFreshInstall(Path dir, Path legacy) {
        return !Files.exists(dir.resolve("levels.json")) && !Files.isDirectory(legacy);
    }

    /** Carries customised 2.x settings ({@code config/rpgmoblevelingsystem/}) into {@code levels.json}, once. */
    static final class LegacyMigration {
        private LegacyMigration() {
        }

        static void migrate(Path legacy, Path target, CommentedJson levelsSpec) {
            try {
                JsonObject levels = levelsSpec.defaults();
                boolean changed = false;

                JsonObject dimensions = read(legacy.resolve("dimensions_settings.json"));
                if (dimensions != null && dimensions.get("dimensions") instanceof JsonArray list) {
                    JsonObject bands = levels.getAsJsonObject("dimensions");
                    for (JsonElement element : list) {
                        if (!(element instanceof JsonObject range)) {
                            continue;
                        }
                        String id = Json.str(range, "dimensionId", "");
                        if (id.isBlank() || LEGACY_DIMENSIONS.contains(id + "|" + Json.num(range, "min", 0) + "|" + Json.num(range, "max", 0))) {
                            continue;
                        }
                        JsonObject existing = bands.has(id) ? bands.getAsJsonObject(id) : null;
                        double scale = existing != null ? Json.num(existing, "distance_scale", 1.0) : 1.0;
                        bands.add(id, LevelsConfig.bandJson((int) Math.round(Json.num(range, "min", 1)),
                                (int) Math.round(Json.num(range, "max", 60)), scale));
                        changed = true;
                    }
                }

                JsonObject scale = read(legacy.resolve("scale_settings.json"));
                if (scale != null) {
                    double distance = Json.num(scale, "scaleDistance", 2500);
                    double factor = Json.num(scale, "scaleFactor", 0.2);
                    if ((Math.abs(distance - 2500) > 1e-6 || Math.abs(factor - 0.2) > 1e-6) && distance > 0 && factor > 0) {
                        levels.getAsJsonObject("distance").addProperty("blocks_per_level", Math.max(1, Math.round(distance / factor)));
                        changed = true;
                    }
                    String mode = Json.str(scale, "playerScaleMode", "none").toLowerCase(java.util.Locale.ROOT);
                    String mapped = switch (mode) {
                        case "nearest" -> "nearest";
                        case "highest_nearby" -> "highest";
                        case "average_nearby" -> "average";
                        default -> null;
                    };
                    if (mapped != null) {
                        levels.getAsJsonObject("players").addProperty("mode", mapped);
                        levels.getAsJsonObject("players").addProperty("radius", Json.num(scale, "playerScaleRadius", 64));
                        changed = true;
                    }
                }

                JsonObject attributes = read(legacy.resolve("attributes_settings.json"));
                if (attributes != null && attributes.get("attributes") instanceof JsonArray rules && !isLegacyDefaultAttributes(rules)) {
                    JsonArray converted = new JsonArray();
                    for (JsonElement element : rules) {
                        if (element instanceof JsonObject rule && !Json.str(rule, "attributeId", "").isBlank()) {
                            converted.add(LevelsConfig.rule(Json.str(rule, "attributeId", ""), Json.str(rule, "mode", "additive"),
                                    Json.num(rule, "valuePerLevel", 0), Json.num(rule, "maxValue", 0)));
                        }
                    }
                    if (!converted.isEmpty()) {
                        levels.add("attributes", converted);
                        changed = true;
                    }
                }

                JsonObject mobs = read(legacy.resolve("mobs_list_settings.json"));
                if (mobs != null) {
                    JsonObject target2 = levels.getAsJsonObject("mobs");
                    JsonArray exclude = new JsonArray();
                    for (String id : Json.strings(mobs, "banned")) {
                        if (!LEGACY_BANNED.contains(id)) {
                            exclude.add(id);
                        }
                    }
                    for (String namespace : Json.strings(mobs, "bannedNamespaces")) {
                        if (!namespace.equals("cobblemon")) {
                            exclude.add(namespace.endsWith(":") ? namespace : namespace + ":");
                        }
                    }
                    if (!exclude.isEmpty()) {
                        target2.add("exclude", exclude);
                        changed = true;
                    }
                    JsonObject locks = new JsonObject();
                    for (String key : List.of("lockedMobs", "bossLevelLocks")) {
                        Json.numbers(mobs, key).forEach((id, level) -> {
                            if (!LEGACY_LOCKS.getOrDefault(id, -1.0).equals(level)) {
                                locks.addProperty(id, (int) Math.round(level));
                            }
                        });
                    }
                    if (locks.size() > 0) {
                        target2.add("level_locks", locks);
                        changed = true;
                    }
                    Map<String, Double> structures = Json.numbers(mobs, "structureMinLevels");
                    if (!structures.isEmpty()) {
                        JsonObject current = levels.getAsJsonObject("structures");
                        structures.forEach((id, level) -> current.addProperty(id, (int) Math.round(level)));
                        changed = true;
                    }
                }

                if (!changed) {
                    RemnantMobs.LOG.info("Found 2.x config in {} with default values; starting from the 3.0 defaults", legacy);
                    return;
                }
                Files.createDirectories(target);
                Files.writeString(target.resolve("levels.json"), levelsSpec.write(levels), StandardCharsets.UTF_8);
                RemnantMobs.LOG.info("Migrated customised 2.x settings from {} to {}", legacy, target.resolve("levels.json"));
            } catch (IOException | RuntimeException e) {
                RemnantMobs.LOG.warn("Could not migrate 2.x config from {}: {}", legacy, e.toString());
            }
        }

        private static boolean isLegacyDefaultAttributes(JsonArray rules) {
            if (rules.size() != 4) {
                return false;
            }
            for (JsonElement element : rules) {
                if (!(element instanceof JsonObject rule)) {
                    return false;
                }
                String key = Json.str(rule, "attributeId", "") + "|" + Json.num(rule, "valuePerLevel", 0) + "|" + Json.num(rule, "maxValue", 0);
                if (!LEGACY_ATTRIBUTES.contains(key)) {
                    return false;
                }
            }
            return true;
        }

        private static JsonObject read(Path file) {
            try {
                if (!Files.exists(file)) {
                    return null;
                }
                JsonElement parsed = CommentedJson.parse(Files.readString(file, StandardCharsets.UTF_8));
                return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }

        private static final Set<String> LEGACY_DIMENSIONS = Set.of(
                "minecraft:overworld|1.0|40.0", "minecraft:the_nether|20.0|80.0", "minecraft:the_end|40.0|100.0",
                "aether:the_aether|10.0|60.0", "twilightforest:twilight_forest|10.0|60.0",
                "eternal_starlight:starligh|10.0|60.0", "deeperdarker:otherside|10.0|60.0");
        private static final Set<String> LEGACY_ATTRIBUTES = Set.of(
                "minecraft:max_health|2.0|200.0", "minecraft:movement_speed|0.0015|0.15",
                "minecraft:armor|0.1|10.0", "minecraft:attack_damage|1.0|100.0");
        private static final Set<String> LEGACY_BANNED = Set.of(
                "minecraft:dolphin", "minecraft:bat", "minecraft:pufferfish", "minecraft:salmon", "minecraft:cod");
        private static final Map<String, Double> LEGACY_LOCKS = Map.of(
                "minecraft:ender_dragon", 10.0, "minecraft:wither", 10.0, "minecraft:warden", 10.0);
    }
}
