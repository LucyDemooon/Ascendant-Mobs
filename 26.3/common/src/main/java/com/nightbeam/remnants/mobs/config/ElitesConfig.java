package com.nightbeam.remnants.mobs.config;

import com.google.gson.JsonObject;
import com.nightbeam.remnants.mobs.content.Rank;
import java.util.Locale;
import net.minecraft.world.BossEvent;

import static com.nightbeam.remnants.mobs.config.Json.obj;

/**
 * {@code config/remnant_mobs/elites.json}: making ranked monsters readable at a glance, for every player including
 * those without the mod (all of it is particles, boss bars, names and the glow effect).
 */
public final class ElitesConfig {
    public final boolean enabled;
    public final boolean aura;
    public final Rank auraMinRank;
    public final double auraDistance;
    public final boolean championBar;
    public final BossEvent.BossBarColor remnantColor;
    public final BossEvent.BossBarColor championColor;
    public final BossEvent.BossBarColor eventBossColor;
    public final boolean championTitles;
    public final boolean eventGlow;

    private ElitesConfig(JsonObject o) {
        enabled = Json.bool(o, "enabled", false);
        aura = Json.bool(o, "aura.enabled", true);
        Rank min = Rank.byKey(Json.str(o, "aura.min_rank", "elite"));
        auraMinRank = min != null && min != Rank.COMMON ? min : Rank.ELITE;
        auraDistance = Math.max(4.0, Math.min(64.0, Json.num(o, "aura.distance", 24)));
        championBar = Json.bool(o, "boss_bars.champion", true);
        remnantColor = color(Json.str(o, "boss_bars.remnant_color", "yellow"), BossEvent.BossBarColor.YELLOW);
        championColor = color(Json.str(o, "boss_bars.champion_color", "purple"), BossEvent.BossBarColor.PURPLE);
        eventBossColor = color(Json.str(o, "boss_bars.event_boss_color", "red"), BossEvent.BossBarColor.RED);
        championTitles = Json.bool(o, "champion_titles", false);
        eventGlow = Json.bool(o, "event_glow", true);
    }

    private static BossEvent.BossBarColor color(String name, BossEvent.BossBarColor fallback) {
        try {
            return BossEvent.BossBarColor.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public static ElitesConfig from(JsonObject json) {
        return new ElitesConfig(json);
    }

    /** @param fresh true on a fresh install (on); false when an older config was found (off: 3.0 looks unchanged) */
    public static CommentedJson spec(boolean fresh) {
        JsonObject d = obj(
                "enabled", fresh,
                "aura", obj("enabled", true, "min_rank", "elite", "distance", 24),
                "boss_bars", obj("champion", true, "remnant_color", "yellow", "champion_color", "purple", "event_boss_color", "red"),
                "champion_titles", false,
                "event_glow", true);
        return new CommentedJson(d)
                .header("Remnants: RPG Mobs 3.1: elites you can read in one second.\n"
                        + "New in 3.1. When this file is first created next to an existing config, enabled starts false so\n"
                        + "ranked monsters look exactly as in 3.0; fresh installs start with it on. Everything here also shows\n"
                        + "for players without the mod. Edit and run /remnants reload, or restart.")
                .comment("enabled", "Master switch. When false, Remnant boss bars stay yellow and nothing below applies.")
                .comment("aura", "Particles in the rank's colour around ranked monsters of min_rank and above (veteran, elite,\n"
                        + "champion, remnant), seen by players within distance blocks. Champions and Remnants get a ring.")
                .comment("boss_bars", "champion: Champions get a boss bar like Remnants. Colours: pink, blue, red, green, yellow,\n"
                        + "purple or white. event_boss_color is for an event night's mini-boss.")
                .comment("champion_titles", "Champions get a generated title too, not only Remnants.")
                .comment("event_glow", "Monsters sent by an event night glow (seen through walls) until dawn.");
    }
}
