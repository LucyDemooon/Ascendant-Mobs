package com.nightbeam.remnants.mobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightbeam.remnants.mobs.config.ElitesConfig;
import com.nightbeam.remnants.mobs.config.ProgressionConfig;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import com.nightbeam.remnants.mobs.content.Rank;
import com.nightbeam.remnants.mobs.progression.Hearts;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.BossEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgressionTest {
    @Test
    void progressionDefaults() {
        ProgressionConfig c = ProgressionConfig.from(ProgressionConfig.spec(true).defaults());
        assertTrue(c.enabled);
        assertEquals(10, c.maxBonusHearts);
        assertEquals(1, c.heartsPerEmber);
        assertTrue(c.keepHeartsOnDeath);
        assertEquals(0.0, c.emberChance.get(Rank.VETERAN));
        assertEquals(0.15, c.emberChance.get(Rank.CHAMPION));
        assertEquals(1.0, c.emberEventBoss);
        assertEquals("bottom_right", c.meterAnchor);
        assertFalse(ProgressionConfig.from(ProgressionConfig.spec(false).defaults()).enabled);
        // The HUD switch is a per-player preference and stays on either way.
        assertTrue(ProgressionConfig.from(ProgressionConfig.spec(false).defaults()).meterHud);
    }

    @Test
    void dropChancesGrowWithDifficulty() {
        ProgressionConfig c = ProgressionConfig.from(ProgressionConfig.spec(true).defaults());
        assertEquals(0.15, c.chance(0.15, 0), 1e-9);
        assertEquals(0.3, c.chance(0.15, 1), 1e-9);
        assertEquals(0.225, c.chance(0.15, 0.5), 1e-9);
        assertEquals(1.0, c.chance(0.6, 1), 1e-9);
        assertEquals(0.3, c.chance(0.15, 7), 1e-9);
    }

    @Test
    void heartsStopAtTheCap() {
        assertEquals(1, Hearts.room(0, 1, 10));
        assertEquals(1, Hearts.room(9, 3, 10));
        assertEquals(0, Hearts.room(10, 1, 10));
        assertEquals(0, Hearts.room(12, 1, 10));
        assertEquals(3, Hearts.room(2, 3, 10));
    }

    @Test
    void elitesDefaults() {
        ElitesConfig c = ElitesConfig.from(ElitesConfig.spec(true).defaults());
        assertTrue(c.enabled);
        assertEquals(Rank.ELITE, c.auraMinRank);
        assertEquals(BossEvent.BossBarColor.PURPLE, c.championColor);
        assertEquals(BossEvent.BossBarColor.YELLOW, c.remnantColor);
        assertFalse(c.championTitles);
        assertFalse(ElitesConfig.from(ElitesConfig.spec(false).defaults()).enabled);
        var json = ElitesConfig.spec(true).defaults();
        json.getAsJsonObject("boss_bars").addProperty("remnant_color", "not_a_colour");
        json.getAsJsonObject("aura").addProperty("min_rank", "common");
        ElitesConfig bad = ElitesConfig.from(json);
        assertEquals(BossEvent.BossBarColor.YELLOW, bad.remnantColor);
        assertEquals(Rank.ELITE, bad.auraMinRank);
    }

    @Test
    void migrationRuleCoversTheNewFiles(@TempDir Path root) throws IOException {
        RemnantsConfig.load(root);
        assertTrue(RemnantsConfig.progression().enabled);
        assertTrue(RemnantsConfig.elites().enabled);

        Path upgraded = root.resolve("upgraded");
        Path dir = Files.createDirectories(upgraded.resolve("remnant_mobs"));
        Files.writeString(dir.resolve("levels.json"), "{}", StandardCharsets.UTF_8);
        RemnantsConfig.load(upgraded);
        assertFalse(RemnantsConfig.progression().enabled);
        assertFalse(RemnantsConfig.elites().enabled);
    }
}
