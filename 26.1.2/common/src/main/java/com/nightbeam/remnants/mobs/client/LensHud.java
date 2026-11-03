package com.nightbeam.remnants.mobs.client;

import com.nightbeam.remnants.mobs.config.RanksConfig;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import com.nightbeam.remnants.mobs.content.ModContent;
import com.nightbeam.remnants.mobs.content.Rank;
import com.nightbeam.remnants.mobs.content.Trait;
import com.nightbeam.remnants.mobs.mob.MobData;
import com.nightbeam.remnants.mobs.mob.MobState;
import com.nightbeam.remnants.mobs.network.PlayerStatePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The Hunter's Lens readouts and the Hunter's Moon indicator. */
public final class LensHud {
    private static final int PANEL_WIDTH = 168;
    private static final int BACKGROUND = 0xC0100C0A;
    private static final int GOLD = 0xFFE2B866;
    private static final int TEXT = 0xFFE6E1D3;
    private static final int MUTED = 0xFF9C958A;

    private LensHud() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        DifficultyHud.render(graphics, minecraft.font);
        Font font = minecraft.font;
        PlayerStatePayload state = ClientState.player();
        if (state.moon() && RemnantsConfig.client().moonIndicator && !player.level().dimensionType().hasFixedTime()) {
            MutableComponent moon = Component.empty().append(Glyphs.icon(Glyphs.MOON, 0xFFE0453A)).append(" ")
                    .append(Component.translatable("hud.remnant_mobs.hunters_moon").withStyle(style -> style.withColor(0xD8574A)));
            // Bottom right: the top corners belong to RPG Attribute System's level display and status effects.
            graphics.text(font, moon, graphics.guiWidth() - font.width(moon) - 6, graphics.guiHeight() - 14, 0xFFFFFFFF, true);
        }
        if (!RemnantsConfig.client().lensHud) {
            return;
        }
        boolean holding = player.getMainHandItem().is(ModContent.HUNTERS_LENS.get()) || player.getOffhandItem().is(ModContent.HUNTERS_LENS.get());
        if (!holding) {
            return;
        }
        boolean scoping = player.isUsingItem() && player.getUseItem().is(ModContent.HUNTERS_LENS.get());
        if (scoping) {
            Mob target = pick(minecraft, 96.0);
            if (target != null) {
                MobState mobState = ClientState.state(target);
                if (mobState.data != null) {
                    sheet(graphics, font, target, mobState);
                    return;
                }
            }
        }
        if (state.threat() >= 0) {
            int color = ClientState.levelColor(state.threat(), true) | 0xFF000000;
            MutableComponent line = Component.empty().append(Glyphs.icon(Glyphs.LENS, GOLD)).append(" ")
                    .append(Component.translatable("hud.remnant_mobs.threat").withStyle(style -> style.withColor(MUTED)))
                    .append(" ")
                    .append(Component.literal(String.valueOf(state.threat())).withStyle(style -> style.withColor(color).withBold(true)));
            if (state.hunterLevel() >= 0) {
                line.append(Component.literal("   ")).append(Component.translatable("hud.remnant_mobs.you", state.hunterLevel())
                        .withStyle(style -> style.withColor(MUTED)));
            }
            int x = graphics.guiWidth() / 2 - font.width(line) / 2;
            int y = graphics.guiHeight() / 2 + 24;
            graphics.text(font, line, x, y, 0xFFFFFFFF, true);
            Component range = state.structure() > 0
                    ? Component.translatable("hud.remnant_mobs.range_structure", state.rangeMin(), state.rangeMax(), state.structure())
                    : Component.translatable("hud.remnant_mobs.range", state.rangeMin(), state.rangeMax());
            graphics.text(font, range, graphics.guiWidth() / 2 - font.width(range) / 2, y + 11, MUTED, true);
        }
    }

    private static void sheet(GuiGraphicsExtractor graphics, Font font, Mob mob, MobState state) {
        MobData data = state.data;
        List<Line> lines = new ArrayList<>();
        Rank rank = data.rank();
        int rankColor = data.ranked() ? rank.color() : 0xE6E1D3;
        Component name = mob.hasCustomName() ? mob.getCustomName() : mob.getType().getDescription();
        MutableComponent title = Component.empty();
        if (data.ranked()) {
            title.append(Glyphs.crest(rank)).append(" ");
        }
        title.append(name.copy().withStyle(style -> style.withColor(rankColor).withBold(true)));
        lines.add(new Line(title, 0));
        int levelColor = ClientState.levelColor(data.level(), true) | 0xFF000000;
        MutableComponent level = Component.translatable("plate.remnant_mobs.level", data.level()).withStyle(style -> style.withColor(levelColor));
        level.append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable(rank.translationKey()).withStyle(style -> style.withColor(rankColor)));
        int own = ClientState.player().hunterLevel();
        if (own >= 0) {
            int diff = data.level() - own;
            String key = diff > 0 ? "hud.remnant_mobs.above" : diff < 0 ? "hud.remnant_mobs.below" : "hud.remnant_mobs.even";
            level.append(Component.literal("  ")).append(Component.translatable(key, Math.abs(diff)).withStyle(style -> style.withColor(MUTED)));
        }
        lines.add(new Line(level, 2));
        MutableComponent stats = Component.empty()
                .append(Glyphs.icon(Glyphs.HEART, 0xFFD8574A)).append(" " + fmt(mob.getHealth()) + " / " + fmt(mob.getMaxHealth()) + "   ")
                .append(Glyphs.icon(Glyphs.SWORD, 0xFFD9D2C3)).append(" " + fmt(state.damage) + "   ")
                .append(Glyphs.icon(Glyphs.SHIELD, 0xFF8FB3D9)).append(" " + fmt(state.armor));
        lines.add(new Line(stats.withStyle(style -> style.withColor(TEXT)), 6));
        int width = PANEL_WIDTH;
        for (Line line : lines) {
            width = Math.max(width, font.width(line.component) + 16);
        }
        width = Math.min(width, 240);
        for (Trait trait : data.traitList()) {
            MutableComponent head = Component.empty().append(Glyphs.trait(trait)).append(" ")
                    .append(Component.translatable(trait.translationKey()).withStyle(style -> style.withColor(trait.color())));
            lines.add(new Line(head, 5));
            for (FormattedCharSequence wrapped : font.split(Component.translatable(trait.descriptionKey()).withStyle(style -> style.withColor(MUTED)),
                    width - 22)) {
                lines.add(new Line(wrapped, 0, 10));
            }
        }
        if (data.ranked() && !data.flag(MobData.MINION)) {
            RanksConfig.Stats stats1 = RemnantsConfig.ranks().stats(rank);
            double brand = data.flag(MobData.BRANDED) ? RemnantsConfig.rewards().brandedMultiplier : 1.0;
            String shards = stats1.shardsMin() == stats1.shardsMax() ? String.valueOf(Math.round(stats1.shardsMin() * brand))
                    : Math.round(stats1.shardsMin() * brand) + "-" + Math.round(stats1.shardsMax() * brand);
            lines.add(new Line(Component.translatable("hud.remnant_mobs.rewards",
                    String.format(Locale.ROOT, "%.1f", stats1.experience() * brand * (1 + data.level() * RemnantsConfig.rewards().experiencePerLevel)))
                    .withStyle(style -> style.withColor(GOLD)), 6));
            if (stats1.shardsMax() > 0) {
                lines.add(new Line(Component.translatable("hud.remnant_mobs.rewards_shards", shards).withStyle(style -> style.withColor(GOLD)), 0));
            }
        }
        if (data.flag(MobData.BRANDED)) {
            lines.add(new Line(Component.empty().append(Glyphs.icon(Glyphs.BRAND, 0xFFE0563C)).append(" ")
                    .append(Component.translatable("hud.remnant_mobs.branded").withStyle(style -> style.withColor(0xE0563C))), 2));
        }

        int height = 8;
        for (Line line : lines) {
            height += line.gap + 10;
        }
        int x = graphics.guiWidth() - width - 12;
        int y = Math.max(8, graphics.guiHeight() / 2 - height / 2);
        graphics.fill(x, y, x + width, y + height, BACKGROUND);
        int accent = 0xFF000000 | (data.ranked() ? rankColor : 0x6B6257);
        graphics.fill(x, y, x + width, y + 1, accent);
        graphics.fill(x, y + height - 1, x + width, y + height, 0x80000000 | (accent & 0xFFFFFF));
        graphics.fill(x, y, x + 1, y + height, 0x80000000 | (accent & 0xFFFFFF));
        int cy = y + 5;
        for (Line line : lines) {
            cy += line.gap;
            if (line.text != null) {
                graphics.text(font, line.text, x + 7 + line.indent, cy, TEXT, true);
            } else {
                graphics.text(font, line.component, x + 7 + line.indent, cy, TEXT, true);
            }
            cy += 10;
        }
    }

    private static String fmt(double value) {
        return value >= 100 ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    /** The leveled mob under the crosshair, up to {@code range} blocks away. */
    public static @Nullable Mob pick(Minecraft minecraft, double range) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(range));
        Mob best = null;
        double bestDistance = Double.MAX_VALUE;
        for (var entity : minecraft.level.getEntities(player, new AABB(eye, end).inflate(1.0), e -> e instanceof Mob && e.isAlive())) {
            var hit = entity.getBoundingBox().inflate(0.25).clip(eye, end);
            if (hit.isPresent()) {
                double distance = eye.distanceToSqr(hit.get());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = (Mob) entity;
                }
            }
        }
        if (best != null) {
            var blockHit = minecraft.level.clip(new net.minecraft.world.level.ClipContext(eye, end,
                    net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
            if (blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS && eye.distanceToSqr(blockHit.getLocation()) < bestDistance) {
                return null;
            }
        }
        return best;
    }

    private record Line(@Nullable FormattedCharSequence text, Component component, int gap, int indent) {
        Line(Component component, int gap) {
            this(null, component, gap, 0);
        }

        Line(FormattedCharSequence text, int gap, int indent) {
            this(text, Component.empty(), gap, indent);
        }
    }
}
