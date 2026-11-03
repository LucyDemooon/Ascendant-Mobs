package com.nightbeam.remnants.mobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nightbeam.remnants.mobs.config.LootConfig;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import com.nightbeam.remnants.mobs.reward.Rewards;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LootTest {
    private static LootConfig defaults() {
        return LootConfig.from(LootConfig.spec(true).defaults());
    }

    @Test
    void defaultsMatchTheDesign() {
        LootConfig c = defaults();
        assertTrue(c.enabled);
        assertEquals("mob_loot_bags", c.bagsModId);
        assertEquals(0.3, c.bagChance.get("champion"));
        assertEquals(1.0, c.bagChance.get(LootConfig.EVENT_BOSS));
        assertEquals(2, c.bagTier.get("champion"));
        assertEquals(6, c.bagItems.size());
        assertEquals("mob_loot_bags:mythiclootbag", c.bagItems.get(5));
        assertEquals(Rarity.RARE, c.beamMinRarity);
        assertFalse(LootConfig.from(LootConfig.spec(false).defaults()).enabled);
    }

    @Test
    void lootGrowsWithDifficulty() {
        LootConfig c = defaults();
        assertEquals(2.0, c.rolls(2, 0), 1e-9);
        assertEquals(4.0, c.rolls(2, 1), 1e-9);
        assertEquals(3.0, c.rolls(2, 0.5), 1e-9);
        assertEquals(4.0, c.rolls(2, 3), 1e-9);
        assertEquals(10.0, c.shards(8, 0.5), 1e-9);
        assertEquals(12.0, c.shards(8, 1), 1e-9);
    }

    @Test
    void bagsGoOneTierUpAtHighDifficulty() {
        LootConfig c = defaults();
        assertEquals(2, c.tier("champion", 0.5));
        assertEquals(3, c.tier("champion", 0.66));
        assertEquals(4, c.tier("remnant", 0.9));
        assertEquals(5, c.tier(LootConfig.EVENT_BOSS, 1.0));
        var json = LootConfig.spec(true).defaults();
        json.getAsJsonObject("loot_bags").getAsJsonObject("tier").addProperty("event_boss", "mythic");
        json.getAsJsonObject("loot_bags").getAsJsonObject("tier").addProperty("elite", "not_a_tier");
        LootConfig custom = LootConfig.from(json);
        assertEquals(5, custom.tier(LootConfig.EVENT_BOSS, 1.0));
        assertEquals(1, custom.bagTier.get("elite"));
    }

    @Test
    void fractionalRollsBecomeChances() {
        RandomSource random = RandomSource.create(42);
        int total = 0;
        for (int i = 0; i < 10000; i++) {
            int rolls = Rewards.chanceRound(2.4, random);
            assertTrue(rolls == 2 || rolls == 3);
            total += rolls;
        }
        assertEquals(2.4, total / 10000.0, 0.03);
        assertEquals(0, Rewards.chanceRound(-1, random));
        assertEquals(3, Rewards.chanceRound(3.0, random));
    }

    @Test
    void migrationRuleCoversLootJson(@TempDir Path root) throws IOException {
        RemnantsConfig.load(root);
        assertTrue(RemnantsConfig.loot().enabled);
        Path upgraded = root.resolve("upgraded");
        Path dir = Files.createDirectories(upgraded.resolve("remnant_mobs"));
        Files.writeString(dir.resolve("levels.json"), "{}", StandardCharsets.UTF_8);
        RemnantsConfig.load(upgraded);
        assertFalse(RemnantsConfig.loot().enabled);
    }
}
