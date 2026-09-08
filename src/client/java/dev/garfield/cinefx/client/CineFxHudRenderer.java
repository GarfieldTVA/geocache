package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.SceneElement;
import dev.garfield.cinefx.client.api.GradeFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;

/** HUD text, countdowns and safe post-FX fallback. */
public final class CineFxHudRenderer {
    private CineFxHudRenderer() { }

    public static void render(DrawContext context, RenderTickCounter ignored) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.textRenderer == null) return;
        double absoluteTick = CineFxRuntime.absoluteGameTick(client);
        var scenes = CineFxRuntime.INSTANCE.snapshot(absoluteTick);
        if (scenes.isEmpty()) return;

        VisualClaims claims = new VisualClaims();
        GradeAccumulator grade = new GradeAccumulator();

        for (ActiveScene scene : scenes) {
            double sceneTick = scene.localTick(absoluteTick);
            if (sceneTick < 0.0) continue;
            for (SceneElement element : scene.elementsByPriority()) {
                if (!element.activeAt(sceneTick)) continue;
                double elementTick = sceneTick - element.startTick();
                if (element instanceof SceneElement.HudText text) {
                    renderText(context, client, claims, scene, text, sceneTick, elementTick);
                } else if (element instanceof SceneElement.ScreenGrade screenGrade) {
                    if (claims.screen("grade", screenGrade.conflictPolicy())) {
                        grade.add(screenGrade, elementTick);
                    }
                }
            }
        }

        if (grade.used) {
            GradeFrame frame = grade.frame();
            if (!CineFxRuntime.INSTANCE.postFxBackends().render(context, frame)) {
                renderSafeGrade(context, client, frame);
            }
        }
    }

    private static void renderText(DrawContext context, MinecraftClient client, VisualClaims claims,
                                   ActiveScene scene, SceneElement.HudText element,
                                   double sceneTick, double elementTick) {
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        int x = (int)Math.round(width * element.normalizedX()) + element.pixelOffsetX();
        int y = (int)Math.round(height * element.normalizedY()) + element.pixelOffsetY();
        if (!claims.claim("hud:" + (x / 8) + ':' + (y / 8), element.conflictPolicy())) return;

        String value = TemplateEngine.render(element.textTemplate(), sceneTick,
                scene.definition().durationTicks(), scene.options());
        StyleSpriteSource.Font font = new StyleSpriteSource.Font(element.fontId());
        Text text = Text.literal(value).styled(style -> style.withFont(font));
        int textWidth = client.textRenderer.getWidth(text);
        x = switch (element.align()) {
            case LEFT -> x;
            case CENTER -> x - textWidth / 2;
            case RIGHT -> x - textWidth;
        };

        float scale = (float)Math.max(0.001, element.scale().sample(elementTick));
        var matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        matrices.translate(-x, -y);
        int color = element.color().sample(elementTick);
        if (element.shadow()) context.drawTextWithShadow(client.textRenderer, text, x, y, color);
        else context.drawText(client.textRenderer, text, x, y, color, false);
        matrices.popMatrix();
    }

    private static void renderSafeGrade(DrawContext context, MinecraftClient client, GradeFrame grade) {
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        if ((grade.tintArgb() >>> 24) != 0) {
            context.fill(0, 0, width, height, grade.tintArgb());
        }

        if (grade.exposure() != 0.0) {
            int alpha = (int)Math.min(180.0, Math.abs(grade.exposure()) * 90.0);
            int rgb = grade.exposure() > 0.0 ? 0xFFFFFF : 0x000000;
            context.fill(0, 0, width, height, (alpha << 24) | rgb);
        }

        double vignette = Math.max(0.0, Math.min(1.0, grade.vignette()));
        if (vignette > 0.001) {
            int bands = 8;
            int maxThickness = Math.max(12, Math.min(width, height) / 7);
            for (int i = 0; i < bands; i++) {
                double fraction = (i + 1.0) / bands;
                int alpha = (int)(vignette * 80.0 * fraction * fraction);
                int color = alpha << 24;
                int edge = (int)(maxThickness * fraction);
                int next = Math.max(0, edge - Math.max(1, maxThickness / bands));
                context.fill(0, next, width, edge, color);
                context.fill(0, height - edge, width, height - next, color);
                context.fill(next, edge, edge, height - edge, color);
                context.fill(width - edge, edge, width - next, height - edge, color);
            }
        }
    }

    private static final class GradeAccumulator {
        boolean used;
        int tint;
        double saturation = 1.0;
        double exposure;
        double contrast = 1.0;
        double vignette;

        void add(SceneElement.ScreenGrade grade, double tick) {
            used = true;
            tint = over(tint, grade.tint().sample(tick));
            saturation *= grade.saturation().sample(tick);
            exposure += grade.exposure().sample(tick);
            contrast *= grade.contrast().sample(tick);
            vignette = Math.max(vignette, grade.vignette().sample(tick));
        }

        GradeFrame frame() { return new GradeFrame(tint, saturation, exposure, contrast, vignette); }

        private static int over(int under, int over) {
            double oa = ((over >>> 24) & 255) / 255.0;
            double ua = ((under >>> 24) & 255) / 255.0;
            double outA = oa + ua * (1.0 - oa);
            if (outA <= 0.0) return 0;
            int r = blend((under >>> 16) & 255, (over >>> 16) & 255, ua, oa, outA);
            int g = blend((under >>> 8) & 255, (over >>> 8) & 255, ua, oa, outA);
            int b = blend(under & 255, over & 255, ua, oa, outA);
            return ((int)Math.round(outA * 255.0) << 24) | (r << 16) | (g << 8) | b;
        }

        private static int blend(int under, int over, double ua, double oa, double outA) {
            return (int)Math.round((over * oa + under * ua * (1.0 - oa)) / outA);
        }
    }
}
