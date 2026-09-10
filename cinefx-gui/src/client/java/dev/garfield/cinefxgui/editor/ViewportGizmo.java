package dev.garfield.cinefxgui.editor;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;

/** Screen-space interaction shell for a world-space Blender-style gizmo. */
public final class ViewportGizmo {
    public record ScreenPoint(double x, double y, double depth, boolean visible) { }
    public record Layout(ScreenPoint center, ScreenPoint x, ScreenPoint y, ScreenPoint z, double worldScale) { }

    private static final int X_COLOR = 0xFFE85B62;
    private static final int Y_COLOR = 0xFF69C46B;
    private static final int Z_COLOR = 0xFF5D91E8;
    private static final int ACTIVE_COLOR = 0xFFFFD86A;

    private ViewportGizmo() { }

    public static ScreenPoint project(Vec3d world, Vec3d camera, float yaw, float pitch,
                                      int left, int top, int right, int bottom) {
        if (world == null || camera == null) return new ScreenPoint(0, 0, -1, false);
        Vec3d rel = world.subtract(camera);
        double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch);
        Vec3d forward = new Vec3d(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp));
        Vec3d screenRight = new Vec3d(Math.cos(ry), 0, Math.sin(ry));
        Vec3d screenUp = forward.crossProduct(screenRight).normalize();
        double depth = rel.dotProduct(forward);
        if (depth <= 0.03) return new ScreenPoint(0, 0, depth, false);
        double viewportH = Math.max(1, bottom - top);
        double focal = viewportH / (2.0 * Math.tan(Math.toRadians(70.0) / 2.0));
        double cx = (left + right) * 0.5, cy = (top + bottom) * 0.5;
        double x = cx + rel.dotProduct(screenRight) / depth * focal;
        double y = cy - rel.dotProduct(screenUp) / depth * focal;
        boolean visible = x >= left - 120 && x <= right + 120 && y >= top - 120 && y <= bottom + 120;
        return new ScreenPoint(x, y, depth, visible);
    }

    public static Layout layout(Vec3d pivot, Vec3d camera, float yaw, float pitch,
                                int left, int top, int right, int bottom) {
        ScreenPoint center = project(pivot, camera, yaw, pitch, left, top, right, bottom);
        double distance = Math.max(0.1, pivot.distanceTo(camera));
        double scale = Math.max(0.35, Math.min(24.0, distance * 0.115));
        return new Layout(center,
                project(pivot.add(scale, 0, 0), camera, yaw, pitch, left, top, right, bottom),
                project(pivot.add(0, scale, 0), camera, yaw, pitch, left, top, right, bottom),
                project(pivot.add(0, 0, scale), camera, yaw, pitch, left, top, right, bottom), scale);
    }

    public static void draw(DrawContext context, Layout layout, SceneManipulator.Tool tool,
                            SceneManipulator.Axis active, double mouseX, double mouseY) {
        if (layout == null || !layout.center.visible()) return;
        int cx = round(layout.center.x), cy = round(layout.center.y);
        if (tool == SceneManipulator.Tool.ROTATE) {
            drawRing(context, cx, cy, 32, active == SceneManipulator.Axis.X ? ACTIVE_COLOR : X_COLOR, 0);
            drawRing(context, cx, cy, 38, active == SceneManipulator.Axis.Y ? ACTIVE_COLOR : Y_COLOR, 8);
            drawRing(context, cx, cy, 44, active == SceneManipulator.Axis.Z ? ACTIVE_COLOR : Z_COLOR, 16);
            context.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xE6E9EDF2);
            return;
        }
        drawAxis(context, layout.center, layout.x, active == SceneManipulator.Axis.X ? ACTIVE_COLOR : X_COLOR, tool == SceneManipulator.Tool.SCALE);
        drawAxis(context, layout.center, layout.y, active == SceneManipulator.Axis.Y ? ACTIVE_COLOR : Y_COLOR, tool == SceneManipulator.Tool.SCALE);
        drawAxis(context, layout.center, layout.z, active == SceneManipulator.Axis.Z ? ACTIVE_COLOR : Z_COLOR, tool == SceneManipulator.Tool.SCALE);
        int centerColor = active == SceneManipulator.Axis.CENTER ? ACTIVE_COLOR : 0xFFE7ECF1;
        context.fill(cx - 4, cy - 4, cx + 5, cy + 5, centerColor);
        context.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF252A31);
    }

    public static SceneManipulator.Axis hit(Layout layout, SceneManipulator.Tool tool, double mx, double my) {
        if (layout == null || !layout.center.visible()) return null;
        double cx = layout.center.x, cy = layout.center.y;
        if (tool == SceneManipulator.Tool.ROTATE) {
            double radius = Math.hypot(mx - cx, my - cy);
            if (Math.abs(radius - 32) <= 4) return SceneManipulator.Axis.X;
            if (Math.abs(radius - 38) <= 4) return SceneManipulator.Axis.Y;
            if (Math.abs(radius - 44) <= 4) return SceneManipulator.Axis.Z;
            return null;
        }
        if (Math.hypot(mx - cx, my - cy) <= 7) return SceneManipulator.Axis.CENTER;
        if (distanceToSegment(mx, my, cx, cy, layout.x.x, layout.x.y) <= 6) return SceneManipulator.Axis.X;
        if (distanceToSegment(mx, my, cx, cy, layout.y.x, layout.y.y) <= 6) return SceneManipulator.Axis.Y;
        if (distanceToSegment(mx, my, cx, cy, layout.z.x, layout.z.y) <= 6) return SceneManipulator.Axis.Z;
        return null;
    }

    public static double axisAmount(Layout layout, SceneManipulator.Axis axis, SceneManipulator.Tool tool,
                                    double startX, double startY, double mouseX, double mouseY,
                                    boolean fine, boolean snap) {
        if (layout == null || axis == null || axis == SceneManipulator.Axis.CENTER) return 0;
        ScreenPoint end = axis == SceneManipulator.Axis.X ? layout.x : axis == SceneManipulator.Axis.Y ? layout.y : layout.z;
        double vx = end.x - layout.center.x, vy = end.y - layout.center.y;
        double len = Math.max(8, Math.hypot(vx, vy));
        vx /= len; vy /= len;
        double pixels = (mouseX - startX) * vx + (mouseY - startY) * vy;
        double amount;
        if (tool == SceneManipulator.Tool.ROTATE) amount = pixels * 0.75;
        else if (tool == SceneManipulator.Tool.SCALE) amount = pixels / Math.max(45.0, len);
        else amount = pixels * layout.worldScale / len;
        if (fine) amount *= 0.2;
        if (snap) {
            double unit = tool == SceneManipulator.Tool.ROTATE ? 15.0 : tool == SceneManipulator.Tool.SCALE ? 0.1 : 0.25;
            amount = Math.round(amount / unit) * unit;
        }
        return amount;
    }

    public static Vec3d planeMove(Layout layout, Vec3d camera, float yaw, float pitch,
                                  double dx, double dy, boolean fine, boolean snap) {
        if (layout == null) return Vec3d.ZERO;
        double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch);
        Vec3d forward = new Vec3d(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp));
        Vec3d right = new Vec3d(Math.cos(ry), 0, Math.sin(ry));
        Vec3d up = forward.crossProduct(right).normalize();
        double worldPerPixel = layout.worldScale / 70.0;
        Vec3d delta = right.multiply(dx * worldPerPixel).add(up.multiply(-dy * worldPerPixel));
        if (fine) delta = delta.multiply(0.2);
        if (snap) delta = new Vec3d(snap(delta.x, .25), snap(delta.y, .25), snap(delta.z, .25));
        return delta;
    }

    public static void drawLine(DrawContext context, double x1, double y1, double x2, double y2, int color, int thickness) {
        int steps = Math.max(1, (int)Math.ceil(Math.hypot(x2 - x1, y2 - y1)));
        int r = Math.max(1, thickness) / 2;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double)steps;
            int x = round(x1 + (x2 - x1) * t), y = round(y1 + (y2 - y1) * t);
            context.fill(x - r, y - r, x + r + 1, y + r + 1, color);
        }
    }

    private static void drawAxis(DrawContext context, ScreenPoint center, ScreenPoint end, int color, boolean square) {
        if (!end.visible()) return;
        drawLine(context, center.x, center.y, end.x, end.y, 0xC9000000, 4);
        drawLine(context, center.x, center.y, end.x, end.y, color, 2);
        int ex = round(end.x), ey = round(end.y);
        if (square) context.fill(ex - 4, ey - 4, ex + 5, ey + 5, color);
        else {
            double vx = end.x - center.x, vy = end.y - center.y, len = Math.max(1, Math.hypot(vx, vy));
            vx /= len; vy /= len;
            double px = -vy, py = vx;
            drawLine(context, ex, ey, ex - vx * 9 + px * 4, ey - vy * 9 + py * 4, color, 2);
            drawLine(context, ex, ey, ex - vx * 9 - px * 4, ey - vy * 9 - py * 4, color, 2);
        }
    }

    private static void drawRing(DrawContext context, int cx, int cy, int radius, int color, int phase) {
        int segments = 64;
        double lastX = cx + Math.cos(Math.toRadians(phase)) * radius;
        double lastY = cy + Math.sin(Math.toRadians(phase)) * radius;
        for (int i = 1; i <= segments; i++) {
            double a = Math.toRadians(phase + i * 360.0 / segments);
            double x = cx + Math.cos(a) * radius, y = cy + Math.sin(a) * radius;
            drawLine(context, lastX, lastY, x, y, color, 2);
            lastX = x; lastY = y;
        }
    }

    private static double distanceToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double vx = x2 - x1, vy = y2 - y1, len2 = vx * vx + vy * vy;
        if (len2 < 1e-6) return Math.hypot(px - x1, py - y1);
        double t = Math.max(0, Math.min(1, ((px - x1) * vx + (py - y1) * vy) / len2));
        return Math.hypot(px - (x1 + vx * t), py - (y1 + vy * t));
    }

    private static double snap(double v, double step) { return Math.round(v / step) * step; }
    private static int round(double v) { return (int)Math.round(v); }
}
