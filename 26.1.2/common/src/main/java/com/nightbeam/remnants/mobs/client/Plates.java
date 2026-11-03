package com.nightbeam.remnants.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nightbeam.remnants.mobs.content.Trait;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/** Draws nameplates for Minecraft 26.2 (render-state extraction, then submission; bars as custom geometry). */
public final class Plates {
    private static final int BORDER = 0xD0120E0B;
    private static final int EMPTY = 0xA0281A16;
    private static final int TRAIL = 0xE0E8D8A8;
    private static final int WARD = 0xFFF2D46B;

    private Plates() {
    }

    /** Extraction: decide the plate for this frame and hide the vanilla name tag it replaces. */
    public static void extract(Entity entity, EntityRenderState state, float partialTicks) {
        PlateHolder holder = (PlateHolder) state;
        if (!(entity instanceof Mob mob)) {
            holder.remnants$setPlate(null);
            return;
        }
        Plate plate = Plate.build(mob, partialTicks, state.distanceToCameraSq);
        holder.remnants$setPlate(plate);
        if (plate != null && com.nightbeam.remnants.mobs.config.RemnantsConfig.client().hideVanillaName) {
            state.nameTag = null;
        }
    }

    public static void submit(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        Plate plate = ((PlateHolder) state).remnants$plate();
        if (plate == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int light = LightCoordsUtil.FULL_BRIGHT;
        int alpha = Math.round(plate.alpha * 255);
        pose.pushPose();
        double lift = state.nameTag != null ? 0.3 : 0.0;
        pose.translate(plate.attachment.x, plate.attachment.y + 0.5 + lift, plate.attachment.z);
        pose.mulPose(camera.orientation);
        float scale = 0.025F * plate.scale;
        pose.scale(scale, -scale, scale);
        // How far in front of its outline text goes, in plate pixels: about eight steps of the depth buffer, which
        // grow with the square of the distance (4 cm at 64 blocks). See outlined().
        float ahead = (float) (state.distanceToCameraSq * 4.0E-4 / plate.scale);

        int titleWidth = font.width(plate.title);
        outlined(collector, pose, -titleWidth / 2.0F, -13, plate.title.getVisualOrderText(), light, ARGB.color(alpha, 0xFFFFFF),
                ARGB.color(Math.round(alpha * 0.85F), 0x000000), ahead);

        float half = Plate.BAR_WIDTH / 2.0F;
        float x0 = -half;
        float x1 = half;
        float y0 = -2;
        float y1 = 3;
        int border = fade(BORDER, plate.alpha);
        List<Rect> rects = new ArrayList<>();
        rects.add(new Rect(x0 - 1, y0, x1 + 1, y0 + 1, plate.ward ? fade(WARD, plate.alpha) : border));
        rects.add(new Rect(x0 - 1, y1, x1 + 1, y1 + 1, border));
        rects.add(new Rect(x0 - 1, y0 + 1, x0, y1, border));
        rects.add(new Rect(x1, y0 + 1, x1 + 1, y1, border));
        float fill = x0 + Plate.BAR_WIDTH * plate.health;
        float trail = x0 + Plate.BAR_WIDTH * Math.max(plate.health, plate.trail);
        if (fill > x0) {
            rects.add(new Rect(x0, y0 + 1, fill, y0 + 2, fade(ARGB.srgbLerp(0.35F, plate.barColor, 0xFFFFFFFF), plate.alpha)));
            rects.add(new Rect(x0, y0 + 2, fill, y1, fade(plate.barColor, plate.alpha)));
        }
        if (trail > fill) {
            rects.add(new Rect(fill, y0 + 1, trail, y1, fade(TRAIL, plate.alpha)));
        }
        if (x1 > trail) {
            rects.add(new Rect(trail, y0 + 1, x1, y1, fade(EMPTY, plate.alpha)));
        }

        collector.submitCustomGeometry(pose, RenderTypes.textBackground(), (p, buffer) -> {
            for (Rect rect : rects) {
                buffer.addVertex(p, rect.x0(), rect.y1(), -0.01F).setColor(rect.color()).setLight(light);
                buffer.addVertex(p, rect.x1(), rect.y1(), -0.01F).setColor(rect.color()).setLight(light);
                buffer.addVertex(p, rect.x1(), rect.y0(), -0.01F).setColor(rect.color()).setLight(light);
                buffer.addVertex(p, rect.x0(), rect.y0(), -0.01F).setColor(rect.color()).setLight(light);
            }
        });

        if (plate.showHealth) {
            pose.pushPose();
            pose.translate(x1 + 3, y0 - 0.5F, 0);
            pose.scale(0.62F, 0.62F, 0.62F);
            outlined(collector, pose, 0, 0, Component.literal(plate.healthText).getVisualOrderText(), light, ARGB.color(alpha, 0xE6E1D3),
                    ARGB.color(Math.round(alpha * 0.8F), 0x000000), ahead / 0.62F);
            pose.popPose();
        }

        if (!plate.traits.isEmpty()) {
            MutableComponent line = Component.empty();
            boolean first = true;
            for (Trait trait : plate.traits) {
                if (!first) {
                    line.append(Component.literal(plate.traitNames ? "  " : " "));
                }
                first = false;
                line.append(Glyphs.trait(trait));
                if (plate.traitNames) {
                    line.append(" ").append(Component.translatable(trait.translationKey()).withStyle(style -> style.withColor(trait.color())));
                }
            }
            pose.pushPose();
            pose.translate(0, y1 + 3, 0);
            pose.scale(0.75F, 0.75F, 0.75F);
            int width = font.width(line);
            outlined(collector, pose, -width / 2.0F, 0, line.getVisualOrderText(), light, ARGB.color(alpha, 0xFFFFFF),
                    ARGB.color(Math.round(alpha * 0.8F), 0x000000), ahead / 0.75F);
            pose.popPose();
        }
        pose.popPose();
    }

    /**
     * Text with an outline. 26.1.2 draws the outline at the depth of the text, so seen at an angle the outline can win
     * over the text itself, worst of all over bold glyphs, whose second stroke sits a little in front. So the outline
     * is drawn on its own first (fully transparent text is discarded), then the text, {@code ahead} in front of it.
     */
    private static void outlined(SubmitNodeCollector collector, PoseStack pose, float x, float y, FormattedCharSequence text, int light,
            int color, int outline, float ahead) {
        collector.submitText(pose, x, y, text, false, Font.DisplayMode.NORMAL, light, ARGB.color(0, color), 0, outline);
        pose.pushPose();
        pose.translate(0, 0, ahead);
        collector.submitText(pose, x, y, text, false, Font.DisplayMode.POLYGON_OFFSET, light, color, 0, 0);
        pose.popPose();
    }

    /** A coloured rectangle of the health bar, in plate pixels. */
    private record Rect(float x0, float y0, float x1, float y1, int color) {
    }

    private static int fade(int color, float alpha) {
        return ARGB.color(Math.round(ARGB.alpha(color) * alpha), color);
    }
}
