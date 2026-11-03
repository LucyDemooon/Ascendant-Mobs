package com.nightbeam.remnants.mobs.config;

import com.google.gson.JsonObject;
import com.nightbeam.remnants.mobs.content.Rank;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import static com.nightbeam.remnants.mobs.config.Json.obj;

/**
 * {@code config/remnant_mobs/progression.json}: the player's side. Vital Embers (more hearts), healing items, where
 * they drop, and the difficulty meter. The meter's position is read from each player's own copy of this file.
 */
public final class ProgressionConfig {
    public final boolean enabled;
    public final int maxBonusHearts;
    public final int heartsPerEmber;
    public final boolean keepHeartsOnDeath;
    public final Map<Rank, Double> emberChance = new EnumMap<>(Rank.class);
    public final double emberEventBoss;
    public final double salveChance;
    public final double draughtChance;
    public final double difficultyBonus;
    public final double salveHeal;
    public final int salveSeconds;
    public final int salveCooldown;
    public final double draughtHealFraction;
    public final int draughtRegenSeconds;
    public final int draughtCooldown;
    public final boolean meterSync;
    public final boolean meterHud;
    public final String meterAnchor;
    public final int meterX;
    public final int meterY;

    private ProgressionConfig(JsonObject o) {
        enabled = Json.bool(o, "enabled", false);
        maxBonusHearts = Math.max(0, Math.min(100, Json.integer(o, "hearts.max_bonus_hearts", 10)));
        heartsPerEmber = Math.max(1, Math.min(10, Json.integer(o, "hearts.hearts_per_ember", 1)));
        keepHeartsOnDeath = Json.bool(o, "hearts.keep_on_death", true);
        for (Rank rank : Rank.values()) {
            emberChance.put(rank, clamp01(Json.num(o, "drops.vital_ember." + rank.key(), DEFAULT_EMBER.getOrDefault(rank, 0.0))));
        }
        emberEventBoss = clamp01(Json.num(o, "drops.vital_ember.event_boss", 1.0));
        salveChance = clamp01(Json.num(o, "drops.mending_salve", 0.06));
        draughtChance = clamp01(Json.num(o, "drops.hunters_draught", 0.08));
        difficultyBonus = Math.max(0.0, Json.num(o, "drops.difficulty_bonus", 1.0));
        salveHeal = Math.max(0.0, Json.num(o, "healing.mending_salve.heal", 6));
        salveSeconds = Math.max(1, Json.integer(o, "healing.mending_salve.seconds", 3));
        salveCooldown = Math.max(0, Json.integer(o, "healing.mending_salve.cooldown_seconds", 10));
        draughtHealFraction = clamp01(Json.num(o, "healing.hunters_draught.heal_fraction", 0.4));
        draughtRegenSeconds = Math.max(0, Json.integer(o, "healing.hunters_draught.regeneration_seconds", 5));
        draughtCooldown = Math.max(0, Json.integer(o, "healing.hunters_draught.cooldown_seconds", 30));
        meterSync = Json.bool(o, "meter.send_to_players", true);
        meterHud = Json.bool(o, "meter.hud", true);
        meterAnchor = Json.str(o, "meter.anchor", "bottom_right").toLowerCase(Locale.ROOT);
        meterX = Json.integer(o, "meter.x", 6);
        meterY = Json.integer(o, "meter.y", 26);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** A drop chance raised by difficulty: {@code base x (1 + difficulty_bonus x fraction)}, at most 1. */
    public double chance(double base, double fraction) {
        return Math.min(1.0, base * (1.0 + difficultyBonus * Math.max(0, Math.min(1, fraction))));
    }

    public static ProgressionConfig from(JsonObject json) {
        return new ProgressionConfig(json);
    }

    private static final Map<Rank, Double> DEFAULT_EMBER = Map.of(
            Rank.COMMON, 0.0, Rank.VETERAN, 0.0, Rank.ELITE, 0.02, Rank.CHAMPION, 0.15, Rank.REMNANT, 0.6);

    /** @param fresh true on a fresh install (drops and the meter start on); false when an older config was found */
    public static CommentedJson spec(boolean fresh) {
        JsonObject ember = new JsonObject();
        for (Rank rank : Rank.values()) {
            ember.addProperty(rank.key(), DEFAULT_EMBER.get(rank));
        }
        ember.addProperty("event_boss", 1.0);
        JsonObject d = obj(
                "enabled", fresh,
                "hearts", obj("max_bonus_hearts", 10, "hearts_per_ember", 1, "keep_on_death", true),
                "drops", obj("vital_ember", ember, "mending_salve", 0.06, "hunters_draught", 0.08, "difficulty_bonus", 1.0),
                "healing", obj(
                        "mending_salve", obj("heal", 6, "seconds", 3, "cooldown_seconds", 10),
                        "hunters_draught", obj("heal_fraction", 0.4, "regeneration_seconds", 5, "cooldown_seconds", 30)),
                "meter", obj("send_to_players", true, "hud", true, "anchor", "bottom_right", "x", 6, "y", 26));
        return new CommentedJson(d)
                .header("Remnants: RPG Mobs 3.1: player progression.\n"
                        + "New in 3.1. When this file is first created next to an existing config, enabled starts false so\n"
                        + "worlds and modpacks behave exactly as before; fresh installs start with it on.\n"
                        + "Edit and run /remnants reload, or restart. Missing keys are restored with their defaults.")
                .comment("enabled", "Master switch: when false, ranked monsters drop none of the items below and the difficulty meter\n"
                        + "is not sent. Items players already have keep working.")
                .comment("hearts", "Vital Embers: each one eaten adds hearts_per_ember hearts of max health, up to max_bonus_hearts.\n"
                        + "keep_on_death: the extra hearts survive death. /remnants hearts shows or sets them.")
                .comment("drops", "Chances (0-1) per kill by a player. vital_ember: per rank, and for an event night's mini-boss.\n"
                        + "mending_salve: Veteran and above. hunters_draught: Elite and above. Each chance is multiplied by\n"
                        + "(1 + difficulty_bonus x difficulty), difficulty going from 0 to 1 across the dimension's level band.")
                .comment("healing", "mending_salve: heals heal health over seconds. hunters_draught: drink to heal heal_fraction of\n"
                        + "your max health, then Regeneration I for regeneration_seconds. Cooldowns in seconds.")
                .comment("meter", "The difficulty meter. send_to_players (server): send each player their difficulty once a second\n"
                        + "while enabled. hud (your client): draw it. anchor: top_left, top_right, bottom_left or bottom_right;\n"
                        + "x and y: distance in GUI pixels from that corner. /remnants difficulty works for everyone either way.");
    }
}
