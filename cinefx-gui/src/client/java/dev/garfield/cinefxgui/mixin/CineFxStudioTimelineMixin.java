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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dense Flashback-like timeline gestures kept separate from the scene-authoring screen itself.
 * It only touches CineFX GUI editor state; CineFX remains a separate runtime dependency.
 */
@Mixin(value = CineFxStudioScreen.class, remap = false)
public abstract class CineFxStudioTimelineMixin {
    @Shadow private PreviewController preview;
    @Shadow private EditorModel.Project project;
    @Shadow private double timelineStartTick;
    @Shadow private double pixelsPerTick;
    @Shadow private EditorModel.History history;

    @Unique private EditorModel.Project cinefxGui$selectionProject;
    @Unique private IdentityHashMap<JsonObject, JsonArray> cinefxGui$selectedKeys;
    @Unique private IdentityHashMap<JsonObject, Double> cinefxGui$dragStartTicks;
    @Unique private boolean cinefxGui$boxSelecting;
    @Unique private boolean cinefxGui$boxAdditive;
    @Unique private double cinefxGui$boxStartX;
    @Unique private double cinefxGui$boxStartY;
    @Unique private double cinefxGui$boxCurrentX;
    @Unique private double cinefxGui$boxCurrentY;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$timelineKeys(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        cinefxGui$ensureSelectionProject();
        boolean ctrl = (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;

        if (ctrl && input.key() == GLFW.GLFW_KEY_A) {
            Object selectedId = cinefxGui$readField(this, "selectedId");
            EditorModel.Element selected = selectedId instanceof String id ? project.find(id) : null;
            if (selected != null) {
                cinefxGui$selected().clear();
                cinefxGui$collectTimed(selected.data, null, cinefxGui$selected());
                cir.setReturnValue(true);
                return;
            }
        }

        if (input.key() == GLFW.GLFW_KEY_ESCAPE && (!cinefxGui$selected().isEmpty() || cinefxGui$boxSelecting)) {
            cinefxGui$selected().clear();
            cinefxGui$boxSelecting = false;
            cir.setReturnValue(true);
            return;
        }

        if (input.key() == GLFW.GLFW_KEY_DELETE && !cinefxGui$selected().isEmpty()) {
            history.checkpoint(project);
            boolean changed = false;
            for (Map.Entry<JsonObject, JsonArray> entry : List.copyOf(cinefxGui$selected().entrySet())) {
                JsonArray parent = entry.getValue();
                if (parent == null || parent.size() <= 1) continue;
                for (int i = 0; i < parent.size(); i++) {
                    if (parent.get(i) == entry.getKey()) {
                        parent.remove(i);
                        changed = true;
                        break;
                    }
                }
            }
            if (changed) {
                project.dirty = true;
                preview.markDirty();
            }
            cinefxGui$selected().clear();
            cir.setReturnValue(true);
            return;
        }

        if (input.key() == GLFW.GLFW_KEY_LEFT || input.key() == GLFW.GLFW_KEY_RIGHT) {
            double amount = shift ? 10.0 : 1.0;
            if (input.key() == GLFW.GLFW_KEY_LEFT) amount = -amount;
            preview.setTick(MinecraftClient.getInstance(), preview.currentTick() + amount);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$timelineDrag(Click click, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        cinefxGui$ensureSelectionProject();
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;

        if (cinefxGui$boxSelecting) {
            cinefxGui$boxCurrentX = click.x();
            cinefxGui$boxCurrentY = click.y();
            cir.setReturnValue(true);
            return;
        }

        Object dragMode = cinefxGui$readField(this, "dragMode");
        if (dragMode != null && "KEYFRAME".equals(dragMode.toString()) && cinefxGui$selected().size() > 1
                && cinefxGui$dragStartTicks != null && !cinefxGui$dragStartTicks.isEmpty()) {
            Object startRaw = cinefxGui$readField(this, "dragMouseStartTick");
            if (startRaw instanceof Number startNumber) {
                double tick = timelineStartTick + (click.x() - 205.0) / Math.max(0.0001, pixelsPerTick);
                double delta = Math.round((tick - startNumber.doubleValue()) * 4.0) / 4.0;
                for (Map.Entry<JsonObject, Double> entry : cinefxGui$dragStartTicks.entrySet()) {
                    if (!cinefxGui$selected().containsKey(entry.getKey())) continue;
                    entry.getKey().addProperty("tick", Math.max(0.0, entry.getValue() + delta));
                }
                project.dirty = true;
                preview.markDirty();
                cir.setReturnValue(true);
                return;
            }
        }

        if (!cinefxGui$isShiftDown()) return;
        if (dragMode == null || !"PLAYHEAD".equals(dragMode.toString())) return;
        double tick = timelineStartTick + (click.x() - 205.0) / Math.max(0.0001, pixelsPerTick);
        tick = Math.max(0.0, Math.min(project.durationTicks, tick));
        double nearest = cinefxGui$nearestKeyframe(tick);
        preview.setTick(MinecraftClient.getInstance(), Double.isFinite(nearest) ? nearest : tick);
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$keyframeActions(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        cinefxGui$ensureSelectionProject();

        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && cinefxGui$isAltDown()
                && click.y() >= cinefxGui$timelineTop() && click.x() >= 205.0) {
            cinefxGui$boxSelecting = true;
            cinefxGui$boxAdditive = cinefxGui$isCtrlDown();
            cinefxGui$boxStartX = cinefxGui$boxCurrentX = click.x();
            cinefxGui$boxStartY = cinefxGui$boxCurrentY = click.y();
            if (!cinefxGui$boxAdditive) cinefxGui$selected().clear();
            cir.setReturnValue(true);
            return;
        }

        Object rawHits = cinefxGui$readField(this, "keyHits");
        if (!(rawHits instanceof List<?> hits)) return;

        for (Object hit : List.copyOf(hits)) {
            if (!cinefxGui$hitContains(hit, click.x(), click.y())) continue;
            Object keyRaw = cinefxGui$readField(hit, "key");
            Object parentRaw = cinefxGui$readField(hit, "parent");
            if (!(keyRaw instanceof JsonObject key)) return;
            JsonArray parent = parentRaw instanceof JsonArray array ? array : null;

            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (cinefxGui$isCtrlDown()) {
                    if (cinefxGui$selected().containsKey(key)) cinefxGui$selected().remove(key);
                    else cinefxGui$selected().put(key, parent);
                    cir.setReturnValue(true);
                    return;
                }
                if (!cinefxGui$selected().containsKey(key)) {
                    cinefxGui$selected().clear();
                    cinefxGui$selected().put(key, parent);
                }
                cinefxGui$dragStartTicks = new IdentityHashMap<>();
                for (JsonObject selected : cinefxGui$selected().keySet()) {
                    try { cinefxGui$dragStartTicks.put(selected, selected.get("tick").getAsDouble()); }
                    catch (RuntimeException ignored) { }
                }
                return; // let the normal screen start the drag/checkpoint
            }

            if (click.button() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
            history.checkpoint(project);
            if (cinefxGui$isShiftDown()) {
                if (parent != null && parent.size() > 1) {
                    for (int i = 0; i < parent.size(); i++) {
                        if (parent.get(i) == key) { parent.remove(i); break; }
                    }
                    cinefxGui$selected().remove(key);
                } else return;
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

        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && click.y() >= cinefxGui$timelineTop()) {
            cinefxGui$selected().clear();
        }
    }

    @Inject(method = "drawTimeline", at = @At("TAIL"))
    private void cinefxGui$drawKeySelection(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        cinefxGui$ensureSelectionProject();
        Object rawHits = cinefxGui$readField(this, "keyHits");
        if (rawHits instanceof List<?> hits && !cinefxGui$selected().isEmpty()) {
            for (Object hit : hits) {
                Object keyRaw = cinefxGui$readField(hit, "key");
                if (!(keyRaw instanceof JsonObject key) || !cinefxGui$selected().containsKey(key)) continue;
                try {
                    int x1 = ((Number)cinefxGui$readField(hit, "x1")).intValue();
                    int y1 = ((Number)cinefxGui$readField(hit, "y1")).intValue();
                    int x2 = ((Number)cinefxGui$readField(hit, "x2")).intValue();
                    int y2 = ((Number)cinefxGui$readField(hit, "y2")).intValue();
                    int color = 0xFFFFE08A;
                    context.fill(x1, y1, x2, y1 + 1, color);
                    context.fill(x1, y2 - 1, x2, y2, color);
                    context.fill(x1, y1, x1 + 1, y2, color);
                    context.fill(x2 - 1, y1, x2, y2, color);
                } catch (RuntimeException ignored) { }
            }
        }

        if (cinefxGui$boxSelecting) {
            int x1 = (int)Math.floor(Math.min(cinefxGui$boxStartX, cinefxGui$boxCurrentX));
            int y1 = (int)Math.floor(Math.min(cinefxGui$boxStartY, cinefxGui$boxCurrentY));
            int x2 = (int)Math.ceil(Math.max(cinefxGui$boxStartX, cinefxGui$boxCurrentX));
            int y2 = (int)Math.ceil(Math.max(cinefxGui$boxStartY, cinefxGui$boxCurrentY));
            x1 = Math.max(205, x1);
            y1 = Math.max(cinefxGui$timelineTop(), y1);
            context.fill(x1, y1, x2, y2, 0x223FA8E8);
            context.fill(x1, y1, x2, y1 + 1, 0xFF72C5F2);
            context.fill(x1, y2 - 1, x2, y2, 0xFF72C5F2);
            context.fill(x1, y1, x1 + 1, y2, 0xFF72C5F2);
            context.fill(x2 - 1, y1, x2, y2, 0xFF72C5F2);
        }
    }

    @Inject(method = "mouseReleased", at = @At("TAIL"), cancellable = true)
    private void cinefxGui$endMultiDrag(Click click, CallbackInfoReturnable<Boolean> cir) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (cinefxGui$boxSelecting) {
            double sx1 = Math.min(cinefxGui$boxStartX, cinefxGui$boxCurrentX);
            double sy1 = Math.min(cinefxGui$boxStartY, cinefxGui$boxCurrentY);
            double sx2 = Math.max(cinefxGui$boxStartX, cinefxGui$boxCurrentX);
            double sy2 = Math.max(cinefxGui$boxStartY, cinefxGui$boxCurrentY);
            Object rawHits = cinefxGui$readField(this, "keyHits");
            if (rawHits instanceof List<?> hits) {
                for (Object hit : hits) {
                    try {
                        double hx1 = ((Number)cinefxGui$readField(hit, "x1")).doubleValue();
                        double hy1 = ((Number)cinefxGui$readField(hit, "y1")).doubleValue();
                        double hx2 = ((Number)cinefxGui$readField(hit, "x2")).doubleValue();
                        double hy2 = ((Number)cinefxGui$readField(hit, "y2")).doubleValue();
                        if (hx2 < sx1 || hx1 > sx2 || hy2 < sy1 || hy1 > sy2) continue;
                        Object keyRaw = cinefxGui$readField(hit, "key");
                        Object parentRaw = cinefxGui$readField(hit, "parent");
                        if (keyRaw instanceof JsonObject key) cinefxGui$selected().put(key, parentRaw instanceof JsonArray a ? a : null);
                    } catch (RuntimeException ignored) { }
                }
            }
            cinefxGui$boxSelecting = false;
            cinefxGui$dragStartTicks = null;
            cir.setReturnValue(true);
            return;
        }
        cinefxGui$dragStartTicks = null;
    }

    @Unique
    private IdentityHashMap<JsonObject, JsonArray> cinefxGui$selected() {
        if (cinefxGui$selectedKeys == null) cinefxGui$selectedKeys = new IdentityHashMap<>();
        return cinefxGui$selectedKeys;
    }

    @Unique
    private void cinefxGui$ensureSelectionProject() {
        if (cinefxGui$selectionProject == project) return;
        cinefxGui$selectionProject = project;
        cinefxGui$selected().clear();
        cinefxGui$dragStartTicks = null;
        cinefxGui$boxSelecting = false;
    }

    @Unique
    private int cinefxGui$timelineTop() {
        MinecraftClient client = MinecraftClient.getInstance();
        int height = client == null ? 0 : client.getWindow().getScaledHeight();
        return Math.max(32 + 170, height - 244);
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
    private static void cinefxGui$collectTimed(JsonElement value, JsonArray parent, IdentityHashMap<JsonObject, JsonArray> out) {
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (parent != null && object.has("tick") && object.get("tick").isJsonPrimitive()) out.put(object, parent);
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) cinefxGui$collectTimed(entry.getValue(), null, out);
        } else if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray();
            for (JsonElement child : array) {
                if (child.isJsonObject() && child.getAsJsonObject().has("tick")) out.put(child.getAsJsonObject(), array);
                else cinefxGui$collectTimed(child, array, out);
            }
        }
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

    @Unique
    private static boolean cinefxGui$isAltDown() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return false;
        long window = client.getWindow().getHandle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }
}
