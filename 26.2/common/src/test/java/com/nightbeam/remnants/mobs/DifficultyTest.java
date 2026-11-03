package com.nightbeam.remnants.mobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightbeam.remnants.mobs.config.CommentedJson;
import com.nightbeam.remnants.mobs.config.DifficultyConfig;
import com.nightbeam.remnants.mobs.config.EventsConfig;
import com.nightbeam.remnants.mobs.config.LevelsConfig;
import com.nightbeam.remnants.mobs.config.RanksConfig;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import com.nightbeam.remnants.mobs.content.Rank;
import com.nightbeam.remnants.mobs.difficulty.Curve;
import com.nightbeam.remnants.mobs.difficulty.Difficulty;
import com.nightbeam.remnants.mobs.event.EventNights;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DifficultyTest {
    @Test
    void curves() {
        assertEquals(2.0, new Curve(true, Curve.Shape.LINEAR, 128, 1, 0).levels(256), 1e-9);
        assertEquals(15.0, new Curve(true, Curve.Shape.LINEAR, 4, 1, 15).levels(400), 1e-9);
        assertEquals(3.0, new Curve(true, Curve.Shape.SQRT, 2, 1, 10).levels(18), 1e-9);
        assertEquals(Math.log(2), new Curve(true, Curve.Shape.LOG, 10, 1, 0).levels(10), 1e-9);
        assertEquals(8.0, new Curve(true, Curve.Shape.POWER, 1, 3, 0).levels(2), 1e-9);
        assertEquals(0.0, new Curve(true, Curve.Shape.LINEAR, 12, 1, 8).levels(-30), 1e-9);
        assertEquals(0.0, new Curve(false, Curve.Shape.LINEAR, 1, 1, 0).levels(50), 1e-9);
    }

    @Test
    void curveReadsOverridesOverItsFallback() {
        Curve global = new Curve(true, Curve.Shape.LINEAR, 128, 1, 0);
        Curve override = Curve.read(com.nightbeam.remnants.mobs.config.Json.obj("per", 64), global);
        assertEquals(Curve.Shape.LINEAR, override.shape());
        assertEquals(64, override.per());
        assertEquals(Curve.Shape.SQRT, Curve.read(com.nightbeam.remnants.mobs.config.Json.obj("curve", "SQRT"), global).shape());
    }

    @Test
    void engineDefaultsStartFromLevelsJson() {
        LevelsConfig levels = LevelsConfig.from(LevelsConfig.spec().defaults());
        DifficultyConfig d = DifficultyConfig.from(DifficultyConfig.spec(true, levels).defaults());
        assertTrue(d.enabled);
        // Days, distance and depth reproduce the 3.0 formula: turning the engine on only adds played time and gear.
        assertEquals(levels.daysPerLevel, d.factors.get("days").per());
        assertEquals(levels.maxDayLevels, d.factors.get("days").max());
        assertEquals(levels.blocksPerLevel, d.factors.get("distance").per());
        assertEquals(levels.blocksPerDepthLevel, d.factors.get("depth").per());
        assertEquals(levels.depthStartY, d.depthStartY);
        assertEquals(1.15, d.dimension("minecraft:the_nether").multiplier(), 1e-9);
        assertEquals(1.0, d.dimension("minecraft:overworld").multiplier(), 1e-9);
        assertTrue(d.excludeDimensions.contains("#remnant_mobs:no_difficulty"));
        assertFalse(DifficultyConfig.from(DifficultyConfig.spec(false, levels).defaults()).enabled);
    }

    @Test
    void dimensionFactorOverrides() {
        LevelsConfig levels = LevelsConfig.from(LevelsConfig.spec().defaults());
        var json = DifficultyConfig.spec(true, levels).defaults();
        json.getAsJsonObject("dimensions").getAsJsonObject("minecraft:the_nether").getAsJsonObject("factors")
                .add("distance", com.nightbeam.remnants.mobs.config.Json.obj("per", 32));
        DifficultyConfig d = DifficultyConfig.from(json);
        assertEquals(32, d.factor("minecraft:the_nether", "distance").per());
        assertEquals(128, d.factor("minecraft:overworld", "distance").per());
        assertTrue(d.factor("minecraft:the_nether", "distance").enabled());
    }

    @Test
    void gearScoreAndRankOdds() {
        LevelsConfig levels = LevelsConfig.from(LevelsConfig.spec().defaults());
        DifficultyConfig d = DifficultyConfig.from(DifficultyConfig.spec(true, levels).defaults());
        // Full diamond and a diamond sword: 20 armor, 8 toughness, 7 attack damage, 20 health.
        assertEquals(20 + 12 + 6, Difficulty.gearScore(d, 20, 8, 7, 20), 1e-9);
        assertEquals(0, Difficulty.gearScore(d, 0, 0, 1, 20), 1e-9);
        assertEquals(10, Difficulty.gearScore(d, 0, 0, 1, 40), 1e-9);
        assertEquals(1.0, Difficulty.rankMultiplier(d, 0), 1e-9);
        assertEquals(2.0, Difficulty.rankMultiplier(d, 1), 1e-9);
        assertEquals(1.5, Difficulty.rankMultiplier(d, 0.25), 1e-9);
        assertEquals(0.5, Difficulty.fraction(30.5, 1, 60), 1e-9);
        assertEquals(1.0, Difficulty.fraction(90, 1, 60), 1e-9);
        assertEquals(0.0, Difficulty.fraction(5, 5, 5), 1e-9);
    }

    @Test
    void eventDefaultsAndSchedule() {
        EventsConfig events = EventsConfig.from(EventsConfig.spec(true).defaults());
        assertTrue(events.enabled);
        EventsConfig.Event vigil = events.events.get("grim_vigil");
        assertEquals(10, vigil.trigger().everyDays());
        assertEquals(Rank.VETERAN, vigil.hunters().minRank());
        assertEquals(Rank.REMNANT, vigil.miniBoss().rank());
        assertEquals("mini", vigil.miniBoss().tier());
        assertTrue(vigil.dimensions().contains("minecraft:overworld"));
        assertEquals(7, EventNights.nextDay(vigil.trigger(), 0));
        assertEquals(7, EventNights.nextDay(vigil.trigger(), 7));
        assertEquals(17, EventNights.nextDay(vigil.trigger(), 8));
        assertEquals(27, EventNights.nextDay(vigil.trigger(), 27));
        assertEquals("The Grim Vigil", EventNights.pretty("grim_vigil"));
        assertFalse(EventsConfig.from(EventsConfig.spec(false).defaults()).enabled);
    }

    @Test
    void freshInstallTurnsNewSystemsOn(@TempDir Path root) {
        RemnantsConfig.load(root);
        assertTrue(RemnantsConfig.difficulty().enabled);
        assertTrue(RemnantsConfig.events().enabled);
        assertTrue(RemnantsConfig.difficulty().disableOverlaps);
        // Second start: the files are the user's now and keep their values.
        RemnantsConfig.load(root);
        assertTrue(RemnantsConfig.difficulty().enabled);
        assertTrue(RemnantsConfig.events().enabled);
    }

    @Test
    void existing30ConfigKeepsNewSystemsOffAndFilesUntouched(@TempDir Path root) throws IOException {
        Path dir = Files.createDirectories(root.resolve("remnant_mobs"));
        CommentedJson levels = LevelsConfig.spec();
        String levelsText = levels.write(levels.defaults()).replace("\"spread\": 2", "\"spread\": 3");
        Files.writeString(dir.resolve("levels.json"), levelsText, StandardCharsets.UTF_8);
        CommentedJson ranks = RanksConfig.spec();
        String ranksText = ranks.write(ranks.defaults());
        Files.writeString(dir.resolve("ranks.json"), ranksText, StandardCharsets.UTF_8);
        RemnantsConfig.load(root);
        assertFalse(RemnantsConfig.difficulty().enabled);
        assertFalse(RemnantsConfig.events().enabled);
        assertFalse(RemnantsConfig.difficulty().disableOverlaps);
        assertEquals(3, RemnantsConfig.levels().spread);
        assertEquals(levelsText, Files.readString(dir.resolve("levels.json"), StandardCharsets.UTF_8));
        assertEquals(ranksText, Files.readString(dir.resolve("ranks.json"), StandardCharsets.UTF_8));
        assertTrue(Files.readString(dir.resolve("difficulty.json"), StandardCharsets.UTF_8).contains("\"enabled\": false"));
        // Opting in survives later starts.
        Path difficulty = dir.resolve("difficulty.json");
        Files.writeString(difficulty, Files.readString(difficulty, StandardCharsets.UTF_8)
                .replaceFirst("\"enabled\": false", "\"enabled\": true"), StandardCharsets.UTF_8);
        RemnantsConfig.load(root);
        assertTrue(RemnantsConfig.difficulty().enabled);
        assertFalse(RemnantsConfig.events().enabled);
    }

    @Test
    void upgradeFrom2xKeepsNewSystemsOff(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("rpgmoblevelingsystem"));
        RemnantsConfig.load(root);
        assertFalse(RemnantsConfig.difficulty().enabled);
        assertFalse(RemnantsConfig.events().enabled);
    }
}
