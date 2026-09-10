package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.CineFxBridge;
import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.PreviewController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Compact live error panel so invalid advanced drafts can be repaired without leaving the viewport. */
@Mixin(value = CineFxStudioScreen.class, remap = false)
public abstract class CineFxStudioDiagnosticsMixin {
    @Shadow private PreviewController preview;

    @Inject(method = "render", at = @At("TAIL"))
    private void cinefxGui$renderDiagnostics(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        CineFxBridge.BuildResult result = preview == null ? null : preview.lastBuild();
        if (result == null || result.errors().isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer text = client.textRenderer;
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        int viewportLeft = 252;
        int viewportRight = screenW - 364;
        int viewportBottom = Math.max(32 + 170, screenH - 244);
        if (viewportRight - viewportLeft < 180) return;

        List<CineFxBridge.BuildError> errors = result.errors();
        int shown = Math.min(6, errors.size());
        int panelW = Math.min(360, Math.max(200, viewportRight - viewportLeft - 18));
        int panelH = 22 + shown * 23 + (errors.size() > shown ? 15 : 0);
        int x = viewportRight - panelW - 8;
        int y = Math.max(56, viewportBottom - panelH - 24);
        context.fill(x, y, x + panelW, y + panelH, 0xE91A1515);
        context.fill(x, y, x + 3, y + panelH, 0xFFE46C63);
        context.drawTextWithShadow(text, "SCENE DIAGNOSTICS · " + errors.size(), x + 9, y + 7, 0xFFFFA39A);

        int yy = y + 22;
        for (int i = 0; i < shown; i++) {
            CineFxBridge.BuildError error = errors.get(i);
            String key = error.key() == null || error.key().isBlank() ? "$project" : error.key();
            String line1 = trim(text, key, panelW - 19);
            String line2 = trim(text, error.message(), panelW - 27);
            context.drawTextWithShadow(text, line1, x + 9, yy, 0xFFE7C3BF);
            context.drawTextWithShadow(text, line2, x + 17, yy + 10, 0xFFBCA5A2);
            yy += 23;
        }
        if (errors.size() > shown) {
            context.drawTextWithShadow(text, "+ " + (errors.size() - shown) + " more · Validate for first error", x + 9, yy, 0xFF9A8583);
        }
    }

    private static String trim(TextRenderer text, String value, int width) {
        return text.trimToWidth(value == null ? "" : value, Math.max(16, width));
    }
}
