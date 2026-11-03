package com.nightbeam.remnants.mobs.client;

import com.nightbeam.remnants.mobs.config.ProgressionConfig;
import com.nightbeam.remnants.mobs.config.RemnantsConfig;
import com.nightbeam.remnants.mobs.network.DifficultyPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The difficulty meter: a skull, a bar filling from green to deep red, the percentage and tonight's bonus. */
public final class DifficultyHud {
    private static final int BAR_WIDTH = 48;
    private static final int BACKGROUND = 0xA0100C0A;
    private static final int MUTED = 0xFF9C958A;

    private DifficultyHud() {
    }

    public static void render(GuiGraphicsExtractor graphics, Font font) {
        DifficultyPayload d = ClientState.difficulty();
        ProgressionConfig c = RemnantsConfig.progression();
        if (d.percent() < 0 || !c.meterHud) {
            return;
        }
        int color = color(d.percent());
        MutableComponent label = Component.empty().append(Glyphs.icon(Glyphs.SKULL, color)).append(" ")
                .append(Component.literal(d.percent() + "%").withStyle(style -> style.withColor(color)));
        if (d.night() > 0) {
            label.append(Component.literal(" +" + d.night()).withStyle(style -> style.withColor(0xD8574A)));
        }
        int width = font.width(label) + 4 + BAR_WIDTH;
        int height = 9;
        boolean right = c.meterAnchor.endsWith("right");
        boolean bottom = c.meterAnchor.startsWith("bottom");
        int x = right ? graphics.guiWidth() - c.meterX - width : c.meterX;
        int y = bottom ? graphics.guiHeight() - c.meterY - height : c.meterY;
        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 1, BACKGROUND);
        graphics.text(font, label, x, y, 0xFFFFFFFF, true);
        int barX = x + font.width(label) + 4;
        int barY = y + 3;
        graphics.fill(barX, barY, barX + BAR_WIDTH, barY + 3, 0xFF2A2420);
        graphics.fill(barX, barY, barX + Math.round(BAR_WIDTH * Math.min(100, d.percent()) / 100F), barY + 3, color);
        graphics.fill(barX, barY + 3, barX + BAR_WIDTH, barY + 4, MUTED & 0x60FFFFFF);
    }

    /** Green at the bottom of the band, through gold and orange, to deep red at the top. */
    static int color(int percent) {
        if (percent >= 75) {
            return 0xFFC8302A;
        }
        if (percent >= 50) {
            return 0xFFE8703A;
        }
        if (percent >= 25) {
            return 0xFFE2B866;
        }
        return 0xFF7FD86A;
    }
}
