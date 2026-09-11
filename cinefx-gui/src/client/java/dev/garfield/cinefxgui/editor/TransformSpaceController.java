package dev.garfield.cinefxgui.editor;

import com.google.gson.JsonObject;
import dev.garfield.cinefx.api.Transform;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Shared transform-space state for Studio gizmos. MOVE is converted through the complete parent
 * matrix (including non-uniform scale). ROTATE is composed as matrices instead of adding Euler
 * components. WORLD scale uses a no-shear diagonal projection because CineFX transforms expose a
 * Vec3 scale rather than a general shear matrix.
 */
public final class TransformSpaceController {
    public enum Space { WORLD, LOCAL }

    private static volatile Space space = Space.WORLD;
    private static volatile EditorModel.Project project;
    private static volatile EditorModel.Element element;
    private static volatile double sceneTick;

    private TransformSpaceController() { }

    public static Space space() { return space; }
    public static void toggle() { space = space == Space.WORLD ? Space.LOCAL : Space.WORLD; }

    public static void setContext(EditorModel.Project nextProject, EditorModel.Element nextElement, double nextSceneTick) {
        project = nextProject;
        element = nextElement;
        sceneTick = Math.max(0.0, nextSceneTick);
    }

    public static HierarchyJson.Axes gizmoAxes() {
        EditorModel.Project p = project;
        EditorModel.Element e = element;
        if (space == Space.WORLD || p == null || e == null) return worldAxes();
        return HierarchyJson.worldAxes(p, e, sceneTick);
    }

    public static boolean applyAxis(SceneManipulator.Tool tool, SceneManipulator.Axis axis,
                                    JsonObject vector, Vec3d original, double amount) {
        EditorModel.Project p = project;
        EditorModel.Element e = element;
        if (p == null || e == null || vector == null || original == null || axis == null) return false;
        if (axis == SceneManipulator.Axis.CENTER) {
            if (tool != SceneManipulator.Tool.SCALE) return false;
            double factor = Math.max(0.001, 1.0 + amount);
            write(vector, new Vec3d(
                    Math.max(0.001, original.x * factor),
                    Math.max(0.001, original.y * factor),
                    Math.max(0.001, original.z * factor)));
            return true;
        }

        if (tool == SceneManipulator.Tool.MOVE) {
            Vec3d worldDelta = axisVector(axis).multiply(amount);
            if (space == Space.LOCAL) worldDelta = axis(gizmoAxes(), axis).multiply(amount);
            Vec3d localDelta = HierarchyJson.worldDirectionToParentLocal(p, e, sceneTick, worldDelta);
            write(vector, original.add(localDelta));
            return true;
        }

        if (tool == SceneManipulator.Tool.ROTATE) {
            write(vector, rotatedEuler(p, e, original, axis, amount));
            return true;
        }

        if (tool == SceneManipulator.Tool.SCALE) {
            if (space == Space.LOCAL) {
                double x = original.x, y = original.y, z = original.z;
                if (axis == SceneManipulator.Axis.X) x = Math.max(0.001, x + amount);
                if (axis == SceneManipulator.Axis.Y) y = Math.max(0.001, y + amount);
                if (axis == SceneManipulator.Axis.Z) z = Math.max(0.001, z + amount);
                write(vector, new Vec3d(x, y, z));
            } else {
                // A rotated world-axis scale generally implies shear. Project it onto the three
                // representable local scale axes instead of silently creating an impossible matrix.
                Vec3d wanted = axisVector(axis);
                HierarchyJson.Axes localAxes = HierarchyJson.worldAxes(p, e, sceneTick);
                double wx = sq(wanted.dotProduct(localAxes.x()));
                double wy = sq(wanted.dotProduct(localAxes.y()));
                double wz = sq(wanted.dotProduct(localAxes.z()));
                double total = Math.max(1.0e-9, wx + wy + wz);
                wx /= total; wy /= total; wz /= total;
                write(vector, new Vec3d(
                        Math.max(0.001, original.x + amount * wx),
                        Math.max(0.001, original.y + amount * wy),
                        Math.max(0.001, original.z + amount * wz)));
            }
            return true;
        }
        return false;
    }

    public static boolean applyPlane(SceneManipulator.Tool tool, JsonObject vector, Vec3d original, Vec3d worldDelta) {
        EditorModel.Project p = project;
        EditorModel.Element e = element;
        if (tool != SceneManipulator.Tool.MOVE || p == null || e == null || vector == null || original == null || worldDelta == null) return false;
        Vec3d localDelta = HierarchyJson.worldDirectionToParentLocal(p, e, sceneTick, worldDelta);
        write(vector, original.add(localDelta));
        return true;
    }

    private static Vec3d rotatedEuler(EditorModel.Project p, EditorModel.Element e, Vec3d editable,
                                      SceneManipulator.Axis axis, double amountDegrees) {
        Transform motion = HierarchyJson.localMotion(p, e, sceneTick);
        Vec3d motionEuler = motion == null ? Vec3d.ZERO : motion.rotationDegrees();
        Vec3d effective = editable.add(motionEuler);
        Mat3 local = Mat3.eulerXYZ(effective);
        Mat3 desired;
        if (space == Space.LOCAL) {
            desired = local.mul(Mat3.axis(axisVector(axis), Math.toRadians(amountDegrees)));
        } else {
            Mat3 parent = rotationOnly(HierarchyJson.parentWorldMatrix(p, e, sceneTick));
            Mat3 worldDelta = Mat3.axis(axisVector(axis), Math.toRadians(amountDegrees));
            desired = parent.transpose().mul(worldDelta).mul(parent).mul(local);
        }
        Vec3d result = desired.eulerXYZDegrees();
        return new Vec3d(
                nearestAngle(result.x - motionEuler.x, editable.x),
                nearestAngle(result.y - motionEuler.y, editable.y),
                nearestAngle(result.z - motionEuler.z, editable.z));
    }

    private static Mat3 rotationOnly(Matrix4f matrix) {
        if (matrix == null) return Mat3.identity();
        Vec3d x = safeNormalize(new Vec3d(matrix.m00(), matrix.m01(), matrix.m02()), new Vec3d(1, 0, 0));
        Vec3d rawY = new Vec3d(matrix.m10(), matrix.m11(), matrix.m12());
        Vec3d y = rawY.subtract(x.multiply(rawY.dotProduct(x)));
        y = safeNormalize(y, new Vec3d(0, 1, 0));
        Vec3d z = safeNormalize(x.crossProduct(y), new Vec3d(0, 0, 1));
        Vec3d rawZ = new Vec3d(matrix.m20(), matrix.m21(), matrix.m22());
        if (z.dotProduct(rawZ) < 0) z = z.multiply(-1);
        y = safeNormalize(z.crossProduct(x), y);
        return Mat3.fromColumns(x, y, z);
    }

    private static HierarchyJson.Axes worldAxes() {
        return new HierarchyJson.Axes(new Vec3d(1, 0, 0), new Vec3d(0, 1, 0), new Vec3d(0, 0, 1));
    }

    private static Vec3d axis(HierarchyJson.Axes axes, SceneManipulator.Axis axis) {
        return switch (axis) {
            case X -> axes.x();
            case Y -> axes.y();
            case Z -> axes.z();
            case CENTER -> Vec3d.ZERO;
        };
    }

    private static Vec3d axisVector(SceneManipulator.Axis axis) {
        return switch (axis) {
            case X -> new Vec3d(1, 0, 0);
            case Y -> new Vec3d(0, 1, 0);
            case Z -> new Vec3d(0, 0, 1);
            case CENTER -> Vec3d.ZERO;
        };
    }

    private static Vec3d safeNormalize(Vec3d value, Vec3d fallback) {
        return value == null || value.lengthSquared() < 1.0e-12 ? fallback : value.normalize();
    }

    private static double nearestAngle(double value, double reference) {
        while (value - reference > 180.0) value -= 360.0;
        while (value - reference < -180.0) value += 360.0;
        return value;
    }

    private static double sq(double value) { return value * value; }

    private static void write(JsonObject object, Vec3d value) {
        object.addProperty("x", finite(value.x));
        object.addProperty("y", finite(value.y));
        object.addProperty("z", finite(value.z));
    }

    private static double finite(double value) { return Double.isFinite(value) ? value : 0.0; }

    /** Tiny row-major rotation matrix helper matching Matrix4f.rotateX().rotateY().rotateZ(). */
    private record Mat3(double m00, double m01, double m02,
                        double m10, double m11, double m12,
                        double m20, double m21, double m22) {
        static Mat3 identity() { return new Mat3(1,0,0, 0,1,0, 0,0,1); }

        static Mat3 fromColumns(Vec3d x, Vec3d y, Vec3d z) {
            return new Mat3(x.x, y.x, z.x, x.y, y.y, z.y, x.z, y.z, z.z);
        }

        static Mat3 eulerXYZ(Vec3d degrees) {
            double x = Math.toRadians(degrees.x), y = Math.toRadians(degrees.y), z = Math.toRadians(degrees.z);
            double cx = Math.cos(x), sx = Math.sin(x), cy = Math.cos(y), sy = Math.sin(y), cz = Math.cos(z), sz = Math.sin(z);
            return new Mat3(
                    cy * cz, -cy * sz, sy,
                    cx * sz + sx * sy * cz, cx * cz - sx * sy * sz, -sx * cy,
                    sx * sz - cx * sy * cz, sx * cz + cx * sy * sz, cx * cy);
        }

        static Mat3 axis(Vec3d axis, double radians) {
            Vec3d n = safeNormalize(axis, new Vec3d(1, 0, 0));
            double x = n.x, y = n.y, z = n.z, c = Math.cos(radians), s = Math.sin(radians), t = 1.0 - c;
            return new Mat3(
                    t*x*x + c, t*x*y - s*z, t*x*z + s*y,
                    t*x*y + s*z, t*y*y + c, t*y*z - s*x,
                    t*x*z - s*y, t*y*z + s*x, t*z*z + c);
        }

        Mat3 mul(Mat3 b) {
            return new Mat3(
                    m00*b.m00 + m01*b.m10 + m02*b.m20, m00*b.m01 + m01*b.m11 + m02*b.m21, m00*b.m02 + m01*b.m12 + m02*b.m22,
                    m10*b.m00 + m11*b.m10 + m12*b.m20, m10*b.m01 + m11*b.m11 + m12*b.m21, m10*b.m02 + m11*b.m12 + m12*b.m22,
                    m20*b.m00 + m21*b.m10 + m22*b.m20, m20*b.m01 + m21*b.m11 + m22*b.m21, m20*b.m02 + m21*b.m12 + m22*b.m22);
        }

        Mat3 transpose() { return new Mat3(m00,m10,m20, m01,m11,m21, m02,m12,m22); }

        Vec3d eulerXYZDegrees() {
            double sy = clamp(m02, -1.0, 1.0);
            double y = Math.asin(sy);
            double cy = Math.cos(y);
            double x, z;
            if (Math.abs(cy) > 1.0e-7) {
                x = Math.atan2(-m12, m22);
                z = Math.atan2(-m01, m00);
            } else {
                // Gimbal lock: keep Z at zero and fold the remaining rotation into X.
                z = 0.0;
                x = sy >= 0.0 ? Math.atan2(m10, m11) : Math.atan2(-m10, m11);
            }
            return new Vec3d(Math.toDegrees(x), Math.toDegrees(y), Math.toDegrees(z));
        }

        private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    }
}
