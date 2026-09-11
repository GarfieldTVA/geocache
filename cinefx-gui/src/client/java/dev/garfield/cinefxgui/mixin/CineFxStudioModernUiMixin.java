package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.EditorModel;
import dev.garfield.cinefxgui.editor.PreviewController;
import dev.garfield.cinefxgui.editor.SceneManipulator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

/**
 * Modern visual shell for Studio. It deliberately keeps the existing editor model, hit testing and
 * authoring logic intact while replacing the old Minecraft-menu look with a compact 3D-editor UI.
 */
@Mixin(value = CineFxStudioScreen.class, remap = false, priority = 500)
public abstract class CineFxStudioModernUiMixin {
    @Unique private static final int TOP = 32;
    @Unique private static final int LEFT = 252;
    @Unique private static final int RIGHT = 364;
    @Unique private static final int TIMELINE = 244;

    @Unique private static final int BG_TOP = 0xFA0B0E13;
    @Unique private static final int BG_PANEL = 0xF712161D;
    @Unique private static final int BG_PANEL_2 = 0xF7171C25;
    @Unique private static final int BG_ELEVATED = 0xFF202733;
    @Unique private static final int BG_HOVER = 0xFF2A3441;
    @Unique private static final int BORDER = 0xFF2A323D;
    @Unique private static final int BORDER_SOFT = 0xAA323C49;
    @Unique private static final int TEXT = 0xFFF1F5F8;
    @Unique private static final int TEXT_DIM = 0xFF94A2AF;
    @Unique private static final int TEXT_MUTED = 0xFF677582;
    @Unique private static final int ACCENT = 0xFF4DA3FF;
    @Unique private static final int ACCENT_DARK = 0xFF214E78;
    @Unique private static final int GOOD = 0xFF64D99A;
    @Unique private static final int WARN = 0xFFFFC66B;

    @Shadow private EditorModel.Project project;
    @Shadow private PreviewController preview;
    @Shadow private String selectedId;
    @Shadow private SceneManipulator.Tool tool;
    @Shadow private double cameraSpeed;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Ldev/garfield/cinefxgui/editor/CineFxStudioScreen;drawToolbar(Lnet/minecraft/client/gui/DrawContext;II)V", shift = At.Shift.BEFORE))
    private void cinefxGui$modernFrame(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();
        int timelineTop = Math.max(TOP + 170, h - TIMELINE);
        int inspectorX = w - RIGHT;

        context.fill(0, 0, w, TOP, BG_TOP);
        context.fill(0, TOP, LEFT, timelineTop, BG_PANEL);
        context.fill(inspectorX, TOP, w, timelineTop, BG_PANEL);
        context.fill(0, timelineTop, w, h, BG_PANEL_2);

        context.fill(LEFT - 1, TOP, LEFT, timelineTop, BORDER);
        context.fill(inspectorX, TOP, inspectorX + 1, timelineTop, BORDER);
        context.fill(0, timelineTop, w, timelineTop + 1, BORDER);
        context.fill(0, TOP - 1, w, TOP, 0xFF202833);
        context.fill(0, TOP - 2, 58, TOP, ACCENT);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void cinefxGui$modernFinalChrome(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;
        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();
        int timelineTop = Math.max(TOP + 170, h - TIMELINE);
        int inspectorX = w - RIGHT;

        // Crisp dock separators rendered last, above vanilla widgets.
        context.fill(LEFT - 1, TOP, LEFT, timelineTop, 0xCC394452);
        context.fill(inspectorX, TOP, inspectorX + 1, timelineTop, 0xCC394452);
        context.fill(0, timelineTop, w, timelineTop + 1, 0xCC394452);

        // Small unsaved/runtime state in the top-right corner.
        String state = project.dirty ? "UNSAVED" : "SAVED";
        int stateColor = project.dirty ? WARN : GOOD;
        int sx = w - 82;
        cinefxGui$pill(context, sx, 7, 70, 18, project.dirty ? 0xFF3A3020 : 0xFF1E342A, 0xFF3F4A55);
        context.drawTextWithShadow(client.textRenderer, state, sx + 10, 13, stateColor);
    }

    @Inject(method = "button", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$modernButton(DrawContext c, int mx, int my, int x, int y, int w, String text, Runnable action,
                                        CallbackInfoReturnable<Integer> cir) {
        boolean hover = cinefxGui$hovered(mx, my, x, y, x + w, y + 22);
        boolean primary = text.equals("Play") || text.equals("Pause") || text.equals("Save") || text.equals("Validate");
        int fill = primary ? (hover ? 0xFF2D6CA1 : ACCENT_DARK) : (hover ? BG_HOVER : BG_ELEVATED);
        cinefxGui$softRect(c, x, y, x + w, y + 22, fill, hover || primary ? 0xFF435466 : BORDER_SOFT);
        int color = primary ? 0xFFFFFFFF : TEXT;
        c.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, cinefxGui$short(text, w - 10), x + 6, y + 7, color);
        ((CineFxStudioAccessMixin)(Object)this).cinefxGui$addHit(x, y, x + w, y + 22, action);
        cir.setReturnValue(x + w);
    }

    @Inject(method = "smallButton", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$modernSmallButton(DrawContext c, int mx, int my, int x, int y, int w, String text, Runnable action,
                                             CallbackInfoReturnable<Integer> cir) {
        boolean hover = cinefxGui$hovered(mx, my, x, y, x + w, y + 18);
        boolean destructive = text.equalsIgnoreCase("Delete");
        int fill = destructive ? (hover ? 0xFF603139 : 0xFF3A2429) : (hover ? BG_HOVER : 0xFF1B222C);
        cinefxGui$softRect(c, x, y, x + w, y + 18, fill, hover ? 0xFF4A5968 : BORDER_SOFT);
        int color = destructive ? 0xFFFFA2A2 : 0xFFDCE5EB;
        c.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, cinefxGui$short(text, w - 8), x + 5, y + 5, color);
        ((CineFxStudioAccessMixin)(Object)this).cinefxGui$addHit(x, y, x + w, y + 18, action);
        cir.setReturnValue(x + w);
    }

    @Inject(method = "leftAction", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$modernLeftAction(DrawContext c, int mx, int my, int y, int bottom, String text, Runnable action,
                                            CallbackInfoReturnable<Integer> cir) {
        if (y >= TOP + 70 && y < bottom - 18) {
            boolean hover = cinefxGui$hovered(mx, my, 7, y - 2, LEFT - 7, y + 18);
            cinefxGui$softRect(c, 7, y - 2, LEFT - 7, y + 18, hover ? BG_HOVER : 0xFF171E27, hover ? 0xFF405062 : BORDER_SOFT);
            c.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, text, 14, y + 4, hover ? TEXT : 0xFFD4DEE5);
            ((CineFxStudioAccessMixin)(Object)this).cinefxGui$addHit(7, y - 2, LEFT - 7, y + 18, action);
        }
        cir.setReturnValue(y + 22);
    }

    @Inject(method = "toolButton", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$modernToolButton(DrawContext c, int mx, int my, int x, String label, SceneManipulator.Tool candidate,
                                            CallbackInfoReturnable<Integer> cir) {
        boolean active = tool == candidate;
        boolean hover = cinefxGui$hovered(mx, my, x, 5, x + 29, 27);
        int fill = active ? ACCENT_DARK : hover ? BG_HOVER : BG_ELEVATED;
        cinefxGui$softRect(c, x, 5, x + 29, 27, fill, active ? ACCENT : hover ? 0xFF445261 : BORDER_SOFT);
        int color = active ? 0xFFFFFFFF : 0xFFD9E3E9;
        c.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, label, x + 11, 12, color);
        ((CineFxStudioAccessMixin)(Object)this).cinefxGui$addHit(x, 5, x + 29, 27, () -> {
            tool = candidate;
            ((CineFxStudioAccessMixin)(Object)this).cinefxGui$toast(candidate.name().toLowerCase(Locale.ROOT));
        });
        cir.setReturnValue(x + 29);
    }

    @Inject(method = "drawLeftPanel", at = @At("TAIL"))
    private void cinefxGui$modernLeftHeader(DrawContext c, int mx, int my, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;
        // Redraw only the fixed chrome. The original tab hitboxes remain active underneath.
        c.fill(0, TOP, LEFT, TOP + 50, BG_PANEL);
        c.drawTextWithShadow(client.textRenderer, "SCENE", 10, TOP + 9, TEXT_DIM);
        String count = project.elements.size() + " objects";
        c.drawTextWithShadow(client.textRenderer, count, LEFT - client.textRenderer.getWidth(count) - 10, TOP + 9, TEXT_MUTED);

        int y = TOP + 23;
        int gap = 4;
        int inner = LEFT - 16;
        int tabW = (inner - gap * 3) / 4;
        String[] labels = {"OUTLINE", "ADD", "BLOCKS", "API"};
        for (int i = 0; i < labels.length; i++) {
            int x = 8 + i * (tabW + gap);
            boolean hover = cinefxGui$hovered(mx, my, x, y, x + tabW, y + 19);
            cinefxGui$softRect(c, x, y, x + tabW, y + 19, hover ? BG_HOVER : 0xFF181F28, hover ? 0xFF465565 : BORDER_SOFT);
            int tx = x + Math.max(4, (tabW - client.textRenderer.getWidth(labels[i])) / 2);
            c.drawTextWithShadow(client.textRenderer, labels[i], tx, y + 6, hover ? TEXT : TEXT_DIM);
        }
    }

    @Inject(method = "drawInspector", at = @At("TAIL"))
    private void cinefxGui$modernInspectorHeader(DrawContext c, int mx, int my, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;
        int w = client.getWindow().getScaledWidth();
        int x = w - RIGHT;
        c.fill(x, TOP, w, TOP + 24, BG_PANEL);
        c.fill(x, TOP + 23, w, TOP + 24, BORDER_SOFT);
        c.drawTextWithShadow(client.textRenderer, "INSPECTOR", x + 10, TOP + 9, TEXT_DIM);
        EditorModel.Element selected = selectedId == null ? null : project.find(selectedId);
        if (selected != null) {
            String name = cinefxGui$short(selected.key(), RIGHT - 118);
            c.drawTextWithShadow(client.textRenderer, name, w - client.textRenderer.getWidth(name) - 10, TOP + 9, TEXT);
        } else {
            c.drawTextWithShadow(client.textRenderer, "Scene", w - 42, TOP + 9, TEXT_MUTED);
        }
    }

    @Inject(method = "drawTimeline", at = @At("TAIL"))
    private void cinefxGui$modernTimelineHeader(DrawContext c, int mx, int my, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;
        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();
        int top = Math.max(TOP + 170, h - TIMELINE);
        c.fill(0, top, w, top + 28, 0xFF10151C);
        c.fill(0, top + 27, w, top + 28, BORDER);
        c.drawTextWithShadow(client.textRenderer, "TIMELINE", 10, top + 10, TEXT_DIM);

        String time = String.format(Locale.ROOT, "%05.2fs", preview.currentTick() / 20.0);
        int tx = 78;
        cinefxGui$pill(c, tx, top + 5, 54, 18, 0xFF1C2631, 0xFF334252);
        c.drawTextWithShadow(client.textRenderer, time, tx + 7, top + 11, 0xFFEAF2F8);

        String playback = preview.playing() ? "PLAYING" : "PAUSED";
        int pw = client.textRenderer.getWidth(playback) + 18;
        int px = w - pw - 12;
        cinefxGui$pill(c, px, top + 5, pw, 18, preview.playing() ? 0xFF173528 : 0xFF2D2920, 0xFF3B4652);
        c.drawTextWithShadow(client.textRenderer, playback, px + 9, top + 11, preview.playing() ? GOOD : WARN);
    }

    @Inject(method = "drawViewport", at = @At("TAIL"))
    private void cinefxGui$modernViewportHud(DrawContext c, int mx, int my, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;
        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();
        int right = w - RIGHT;
        int bottom = Math.max(TOP + 170, h - TIMELINE);
        if (right <= LEFT + 120) return;

        // Cover the old verbose viewport hint and replace it with compact chips.
        c.fill(LEFT + 4, TOP + 4, right - 4, TOP + 31, 0xB80D1117);
        c.fill(LEFT + 4, TOP + 30, right - 4, TOP + 31, 0x99404A56);

        int x = LEFT + 10;
        String camera = preview.sceneCameraPreview() ? "SCENE CAM" : "EDITOR CAM";
        int cameraW = client.textRenderer.getWidth(camera) + 18;
        cinefxGui$pill(c, x, TOP + 8, cameraW, 18, preview.sceneCameraPreview() ? 0xFF342846 : 0xFF182C3E, 0xFF42505F);
        c.drawTextWithShadow(client.textRenderer, camera, x + 9, TOP + 14, preview.sceneCameraPreview() ? 0xFFD8B6FF : 0xFFAED8FF);
        x += cameraW + 6;

        String transform = switch (tool) {
            case MOVE -> "MOVE [G]";
            case ROTATE -> "ROTATE [R]";
            case SCALE -> "SCALE [S]";
        };
        int transformW = client.textRenderer.getWidth(transform) + 18;
        cinefxGui$pill(c, x, TOP + 8, transformW, 18, 0xFF1B222B, tool == SceneManipulator.Tool.MOVE ? ACCENT_DARK : 0xFF3C4652);
        c.drawTextWithShadow(client.textRenderer, transform, x + 9, TOP + 14, tool == SceneManipulator.Tool.MOVE ? 0xFFBFE0FF : TEXT_DIM);

        String speed = String.format(Locale.ROOT, "SPEED %.2f", cameraSpeed);
        int speedW = client.textRenderer.getWidth(speed) + 16;
        cinefxGui$pill(c, right - speedW - 10, TOP + 8, speedW, 18, 0xFF161D25, 0xFF394552);
        c.drawTextWithShadow(client.textRenderer, speed, right - speedW - 2, TOP + 14, TEXT_DIM);

        // Tiny contextual help at the bottom of the viewport, without covering the scene.
        String help = "RMB look/fly   WASD move   Q/E vertical   LMB select   F focus";
        int hw = client.textRenderer.getWidth(help) + 18;
        int hx = LEFT + Math.max(10, (right - LEFT - hw) / 2);
        if (hx + hw < right - 8) {
            cinefxGui$pill(c, hx, bottom - 25, hw, 18, 0xC8151A21, 0x88414C58);
            c.drawTextWithShadow(client.textRenderer, help, hx + 9, bottom - 19, 0xFF9AA8B4);
        }
    }

    @Unique
    private static void cinefxGui$softRect(DrawContext c, int x1, int y1, int x2, int y2, int fill, int border) {
        if (x2 <= x1 || y2 <= y1) return;
        c.fill(x1 + 1, y1, x2 - 1, y2, fill);
        c.fill(x1, y1 + 1, x2, y2 - 1, fill);
        c.fill(x1 + 1, y1, x2 - 1, y1 + 1, border);
        c.fill(x1 + 1, y2 - 1, x2 - 1, y2, border);
        c.fill(x1, y1 + 1, x1 + 1, y2 - 1, border);
        c.fill(x2 - 1, y1 + 1, x2, y2 - 1, border);
    }

    @Unique
    private static void cinefxGui$pill(DrawContext c, int x, int y, int w, int h, int fill, int border) {
        cinefxGui$softRect(c, x, y, x + w, y + h, fill, border);
    }

    @Unique
    private static boolean cinefxGui$hovered(double mx, double my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
    }

    @Unique
    private static String cinefxGui$short(String text, int width) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null || client.textRenderer == null ? (text == null ? "" : text)
                : client.textRenderer.trimToWidth(text == null ? "" : text, Math.max(4, width));
    }
}
