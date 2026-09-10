package dev.garfield.cinefxgui.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.garfield.cinefx.api.Easing;
import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.EditorModel;
import dev.garfield.cinefxgui.editor.PreviewController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * Keeps the dense Flashback-like timeline gestures isolated from the scene-authoring screen itself.
 * It only touches CineFX GUI editor state; CineFX remains a separate runtime dependency.
 */
@Mixin(value = CineFxStudioScreen.class, remap = false)
public abstract class CineFxStudioTimelineMixin {
    @Shadow private PreviewController preview;
    @Shadow private EditorModel.Project project;
    @Shadow private double timelineStartTick;
    @Shadow private double pixelsPerTick;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$stepPlayhead(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (input.key() != GLFW.GLFW_KEY_LEFT && input.key() != GLFW.GLFW_KEY_RIGHT) return;
        double amount = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 10.0 : 1.0;
        if (input.key() == GLFW.GLFW_KEY_LEFT) amount = -amount;
        preview.setTick(MinecraftClient.getInstance(), preview.currentTick() + amount);
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$snapPlayhead(Click click, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !cinefxGui$isShiftDown()) return;
        Object dragMode = cinefxGui$readField(this, "dragMode");
        if (dragMode == null || !"PLAYHEAD".equals(dragMode.toString())) return;

        double tick = timelineStartTick + (click.x() - 205.0) / Math.max(0.0001, pixelsPerTick);
        tick = Math.max(0.0, Math.min(project.durationTicks, tick));
        double nearest = cinefxGui$nearestKeyframe(tick);
        preview.setTick(MinecraftClient.getInstance(), Double.isFinite(nearest) ? nearest : tick);
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$keyframeContextAction(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
        Object rawHits = cinefxGui$readField(this, "keyHits");
        if (!(rawHits instanceof List<?> hits)) return;

        for (Object hit : List.copyOf(hits)) {
            if (!cinefxGui$hitContains(hit, click.x(), click.y())) continue;
            Object keyRaw = cinefxGui$readField(hit, "key");
            Object parentRaw = cinefxGui$readField(hit, "parent");
            if (!(keyRaw instanceof JsonObject key)) return;

            cinefxGui$checkpoint();
            if (cinefxGui$isShiftDown()) {
                if (parentRaw instanceof JsonArray parent && parent.size() > 1) {
                    for (int i = 0; i < parent.size(); i++) {
                        if (parent.get(i) == key) {
                            parent.remove(i);
                            break;
                        }
                    }
                } else {
                    return;
                }
            } else {
                Easing[] curves = Easing.values();
                String current = key.has("easing") && key.get("easing").isJsonPrimitive()
                        ? key.get("easing").getAsString() : Easing.LINEAR.name();
                int index = 0;
                for (int i = 0; i < curves.length; i++) if (curves[i].name().equalsIgnoreCase(current)) index = i;
                int direction = cinefxGui$isCtrlDown() ? -1 : 1;
                key.addProperty("easing", curves[Math.floorMod(index + direction, curves.length)].name());
            }
            project.dirty = true;
            preview.markDirty();
            cir.setReturnValue(true);
            return;
        }
    }

    @Unique
    private void cinefxGui$checkpoint() {
        Object history = cinefxGui$readField(this, "history");
        if (history instanceof EditorModel.History editorHistory) editorHistory.checkpoint(project);
    }

    @Unique
    private double cinefxGui$nearestKeyframe(double target) {
        double best = Double.NaN;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (EditorModel.Element element : project.elements) {
            double start = element.startTick();
            double startDistance = Math.abs(start - target);
            if (startDistance < bestDistance) { bestDistance = startDistance; best = start; }
            double end = element.endTick();
            double endDistance = Math.abs(end - target);
            if (endDistance < bestDistance) { bestDistance = endDistance; best = end; }
            double[] holder = {best, bestDistance};
            cinefxGui$findTicks(element.data, start, target, holder);
            best = holder[0]; bestDistance = holder[1];
        }
        return best;
    }

    @Unique
    private static void cinefxGui$findTicks(JsonElement value, double elementStart, double target, double[] holder) {
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("tick") && object.get("tick").isJsonPrimitive()) {
                try {
                    double absolute = elementStart + object.get("tick").getAsDouble();
                    double distance = Math.abs(absolute - target);
                    if (distance < holder[1]) { holder[0] = absolute; holder[1] = distance; }
                } catch (RuntimeException ignored) { }
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) cinefxGui$findTicks(entry.getValue(), elementStart, target, holder);
        } else if (value.isJsonArray()) {
            for (JsonElement child : value.getAsJsonArray()) cinefxGui$findTicks(child, elementStart, target, holder);
        }
    }

    @Unique
    private static boolean cinefxGui$hitContains(Object hit, double x, double y) {
        try {
            double x1 = ((Number)cinefxGui$readField(hit, "x1")).doubleValue();
            double y1 = ((Number)cinefxGui$readField(hit, "y1")).doubleValue();
            double x2 = ((Number)cinefxGui$readField(hit, "x2")).doubleValue();
            double y2 = ((Number)cinefxGui$readField(hit, "y2")).doubleValue();
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Unique
    private static Object cinefxGui$readField(Object target, String name) {
        if (target == null) return null;
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    @Unique
    private static boolean cinefxGui$isShiftDown() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return false;
        long window = client.getWindow().getHandle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    @Unique
    private static boolean cinefxGui$isCtrlDown() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return false;
        long window = client.getWindow().getHandle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }
}
