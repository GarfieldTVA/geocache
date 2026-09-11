package dev.garfield.cinefxgui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;

/**
 * Visual-authoring presets used by the universal Studio inspector.
 *
 * They deliberately operate on the editor JSON model instead of runtime classes so every action stays
 * undoable, serializable and compatible with the existing CineFxBridge decoder.
 */
public final class VisualPresetLibrary {
    private VisualPresetLibrary() { }

    public static List<String> presets(EditorModel.Element element) {
        if (element == null || element.apiClass == null) return List.of();
        String type = element.apiClass;
        if (type.endsWith("UltraEventElement$PostProcess")) return List.of("Cinematic", "Glitch", "Underwater");
        if (type.endsWith("UltraEventElement$ParticleField")) return List.of("Burst", "Smoke", "Magic");
        if (type.endsWith("UltraEventElement$Fracture")) return List.of("Glass", "Rock", "Reverse");
        if (type.endsWith("UltraEventElement$SoftBody")) return List.of("Rope", "Cloth", "Tentacle");
        if (type.endsWith("UltraEventElement$SpatialAudio")) return List.of("Room", "Cave", "Arena");
        if (type.endsWith("UltraEventElement$MaterialEffect")) return List.of("Dissolve", "Burn", "Hologram");
        if (type.endsWith("UltraEventElement$WorldDeform")) return List.of("Fissure", "Pulse", "Rebuild");
        if (type.endsWith("UltraEventElement$PortalSurface")) return List.of("Portal", "Mirror", "Kaleidoscope");
        if (type.endsWith("EventElement$Atmosphere")) return List.of("Night", "Fog", "Storm");
        if (type.endsWith("EventElement$Overlay")) return List.of("Fade", "Letterbox", "Flash");
        if (type.endsWith("EventElement$Emitter")) return List.of("Sparks", "Smoke", "Burst");
        if (type.endsWith("ComplexElement$Volume")) return List.of("Fog", "Energy", "Smoke");
        if (type.endsWith("ComplexElement$Trail")) return List.of("Ribbon", "Streak", "Tube");
        if (type.endsWith("ComplexElement$Shadow")) return List.of("Soft", "Hard", "Wide");
        if (type.endsWith("AdvancedEventElement$Sky")) return List.of("Sunset", "Eclipse", "Aurora");
        if (type.endsWith("AdvancedEventElement$PlayerControl")) return List.of("Cutscene", "Freeze", "Release");
        if (type.endsWith("AdvancedEventElement$AudioLayer")) return List.of("Music", "Ambience", "Impact");
        if (type.endsWith("ComplexElement$Actor")) return List.of("Solid", "Ghost", "Emissive");
        if (type.endsWith("ComplexElement$Mesh")) return List.of("Solid", "Ghost", "Emissive");
        if (type.endsWith("ComplexElement$Decal")) return List.of("Soft", "Sharp", "Glow");
        return List.of();
    }

    public static boolean apply(EditorModel.Element element, String preset, double localTick) {
        if (element == null || element.data == null || preset == null) return false;
        String type = element.apiClass == null ? "" : element.apiClass;
        String p = preset.toLowerCase(Locale.ROOT);
        JsonObject d = element.data;

        if (type.endsWith("UltraEventElement$PostProcess")) {
            JsonObject effects = object(d, "effects");
            effects.entrySet().clear();
            switch (p) {
                case "cinematic" -> {
                    effects.add("BLOOM", scalarTrack(localTick, 0.22));
                    effects.add("VIGNETTE", scalarTrack(localTick, 0.18));
                    effects.add("FILM_GRAIN", scalarTrack(localTick, 0.04));
                    setScalar(d, "focusDistance", localTick, 8.0);
                    setScalar(d, "focusRange", localTick, 4.0);
                }
                case "glitch" -> {
                    effects.add("GLITCH", scalarTrack(localTick, 0.72));
                    effects.add("CHROMATIC_ABERRATION", scalarTrack(localTick, 0.45));
                    effects.add("FILM_GRAIN", scalarTrack(localTick, 0.22));
                }
                case "underwater" -> {
                    effects.add("UNDERWATER_REFRACTION", scalarTrack(localTick, 0.72));
                    effects.add("VIGNETTE", scalarTrack(localTick, 0.12));
                    setColor(d, "tint", localTick, 0x553C8FD6);
                }
                default -> { return false; }
            }
            return true;
        }

        if (type.endsWith("UltraEventElement$ParticleField")) {
            switch (p) {
                case "burst" -> { setScalar(d,"spawnRate",localTick,2200); setScalar(d,"lifetimeTicks",localTick,30); setScalar(d,"speed",localTick,1.7); setScalar(d,"size",localTick,0.65); d.addProperty("trails", false); }
                case "smoke" -> { setScalar(d,"spawnRate",localTick,120); setScalar(d,"lifetimeTicks",localTick,100); setScalar(d,"speed",localTick,0.12); setScalar(d,"size",localTick,2.2); d.addProperty("trails", false); setColor(d,"color",localTick,0xAA90959C); }
                case "magic" -> { setScalar(d,"spawnRate",localTick,450); setScalar(d,"lifetimeTicks",localTick,55); setScalar(d,"speed",localTick,0.42); setScalar(d,"size",localTick,0.8); d.addProperty("trails", true); setColor(d,"color",localTick,0xFF9A6DFF); }
                default -> { return false; }
            }
            return true;
        }

        if (type.endsWith("UltraEventElement$Fracture")) {
            switch (p) {
                case "glass" -> { d.addProperty("mode","VORONOI"); d.addProperty("shardCount",96); setScalar(d,"force",localTick,1.2); setScalar(d,"gravity",localTick,0.025); setScalar(d,"drag",localTick,0.992); setScalar(d,"angularSpeed",localTick,5.5); d.addProperty("reverse",false); }
                case "rock" -> { d.addProperty("mode","RADIAL"); d.addProperty("shardCount",36); setScalar(d,"force",localTick,2.4); setScalar(d,"gravity",localTick,0.055); setScalar(d,"drag",localTick,0.982); setScalar(d,"angularSpeed",localTick,2.2); d.addProperty("reverse",false); }
                case "reverse" -> { d.addProperty("mode","PREBAKED"); d.addProperty("shardCount",64); setScalar(d,"force",localTick,1.0); d.addProperty("reverse",true); }
                default -> { return false; }
            }
            return true;
        }

        if (type.endsWith("UltraEventElement$SoftBody")) {
            switch (p) {
                case "rope" -> { d.addProperty("mode","ROPE"); setScalar(d,"gravity",localTick,0.035); setScalar(d,"wind",localTick,0.0); setScalar(d,"damping",localTick,0.985); setScalar(d,"thickness",localTick,0.05); d.addProperty("solverIterations",8); }
                case "cloth" -> { d.addProperty("mode","CLOTH"); setScalar(d,"gravity",localTick,0.028); setScalar(d,"wind",localTick,0.32); setScalar(d,"damping",localTick,0.975); setScalar(d,"thickness",localTick,0.025); d.addProperty("solverIterations",12); }
                case "tentacle" -> { d.addProperty("mode","TENTACLE"); setScalar(d,"gravity",localTick,0.008); setScalar(d,"wind",localTick,0.12); setScalar(d,"damping",localTick,0.965); setScalar(d,"thickness",localTick,0.12); d.addProperty("solverIterations",16); }
                default -> { return false; }
            }
            return true;
        }

        if (type.endsWith("UltraEventElement$SpatialAudio")) {
            switch (p) {
                case "room" -> { d.addProperty("reverb","ROOM"); setScalar(d,"reverbMix",localTick,0.28); setScalar(d,"radius",localTick,24); d.addProperty("occlusion",true); }
                case "cave" -> { d.addProperty("reverb","CAVE"); setScalar(d,"reverbMix",localTick,0.72); setScalar(d,"radius",localTick,48); d.addProperty("occlusion",true); }
                case "arena" -> { d.addProperty("reverb","ARENA"); setScalar(d,"reverbMix",localTick,0.52); setScalar(d,"radius",localTick,64); d.addProperty("occlusion",false); }
                default -> { return false; }
            }
            return true;
        }

        if (type.endsWith("UltraEventElement$MaterialEffect")) {
            switch (p) {
                case "dissolve" -> { d.addProperty("mode","DISSOLVE"); setScalar(d,"amount",localTick,0.55); setScalar(d,"edgeWidth",localTick,0.08); setScalar(d,"noiseScale",localTick,1.2); setScalar(d,"speed",localTick,1.0); setColor(d,"edgeColor",localTick,0xFFFFC45C); }
                case "burn" -> { d.addProperty("mode","BURN"); setScalar(d,"amount",localTick,0.5); setScalar(d,"edgeWidth",localTick,0.12); setScalar(d,"noiseScale",localTick,1.7); setScalar(d,"speed",localTick,1.4); setColor(d,"edgeColor",localTick,0xFFFF5E26); }
                case "hologram" -> { d.addProperty("mode","HOLOGRAM"); setScalar(d,"amount",localTick,0.75); setScalar(d,"edgeWidth",localTick,0.04); setScalar(d,"noiseScale",localTick,3.0); setScalar(d,"speed",localTick,2.0); setColor(d,"edgeColor",localTick,0xFF55D8FF); }
                default -> { return false; }
            }
            return true;
        }

        if (type.endsWith("UltraEventElement$WorldDeform")) {
            switch (p) {
                case "fissure" -> { d.addProperty("mode","FISSURE"); setScalar(d,"radius",localTick,16); setScalar(d,"amplitude",localTick,5); setScalar(d,"frequency",localTick,1.3); setScalar(d,"progress",localTick,1); }
                case "pulse" -> { d.addProperty("mode","PULSE"); setScalar(d,"radius",localTick,22); setScalar(d,"amplitude",localTick,2.5); setScalar(d,"frequency",localTick,2.2); setScalar(d,"progress",localTick,1); }
                case "rebuild" -> { d.addProperty("mode","REBUILD"); setScalar(d,"radius",localTick,18); setScalar(d,"amplitude",localTick,3); setScalar(d,"frequency",localTick,1); setScalar(d,"progress",localTick,0); }
                default -> { return false; }
            }
            return true;
        }

        if (type.endsWith("UltraEventElement$PortalSurface")) {
            switch (p) {
                case "portal" -> { d.addProperty("mode","PORTAL"); setScalar(d,"opacity",localTick,1); setScalar(d,"distortion",localTick,0.12); d.addProperty("recursionDepth",1); setColor(d,"rimColor",localTick,0xFF9A67FF); }
                case "mirror" -> { d.addProperty("mode","MIRROR"); setScalar(d,"opacity",localTick,1); setScalar(d,"distortion",localTick,0.01); d.addProperty("recursionDepth",2); setColor(d,"rimColor",localTick,0xFFFFFFFF); }
                case "kaleidoscope" -> { d.addProperty("mode","KALEIDOSCOPE"); setScalar(d,"opacity",localTick,1); setScalar(d,"distortion",localTick,0.45); d.addProperty("recursionDepth",3); setColor(d,"rimColor",localTick,0xFFFF68E6); }
                default -> { return false; }
            }
            return true;
        }

        if (type.endsWith("EventElement$Atmosphere")) {
            switch (p) {
                case "night" -> { setColor(d,"skyTint",localTick,0x88314C85); setColor(d,"fogColor",localTick,0x88233048); setScalar(d,"fogDensity",localTick,0.04); setScalar(d,"cloudOpacity",localTick,0.35); setScalar(d,"starBrightness",localTick,1.6); }
                case "fog" -> { setColor(d,"fogColor",localTick,0xFFD0D5D8); setScalar(d,"fogDensity",localTick,0.42); setScalar(d,"fogNear",localTick,2); setScalar(d,"fogFar",localTick,36); setScalar(d,"cloudOpacity",localTick,0.65); }
                case "storm" -> { setColor(d,"skyTint",localTick,0xAA5C6670); setColor(d,"fogColor",localTick,0xAA5B646C); setScalar(d,"fogDensity",localTick,0.12); setScalar(d,"cloudOpacity",localTick,1.0); setScalar(d,"windStrength",localTick,1.0); setScalar(d,"starBrightness",localTick,0.0); }
                default -> { return false; }
            }
            return true;
        }

        if (type.endsWith("EventElement$Overlay")) {
            switch (p) {
                case "fade" -> { setColor(d,"color",localTick,0xFF000000); setScalar(d,"opacity",localTick,1); setScalar(d,"letterbox",localTick,0); }
                case "letterbox" -> { setColor(d,"color",localTick,0xFF000000); setScalar(d,"opacity",localTick,0); setScalar(d,"letterbox",localTick,0.16); }
                case "flash" -> { setColor(d,"color",localTick,0xFFFFFFFF); setScalar(d,"opacity",localTick,0.9); setScalar(d,"blurHint",localTick,0.12); }
                default -> { return false; }
            }
            return true;
        }

        if (type.endsWith("EventElement$Emitter")) {
            switch (p) {
                case "sparks" -> { d.addProperty("shape","CONE"); setScalar(d,"ratePerSecond",localTick,220); setScalar(d,"spread",localTick,0.28); setScalar(d,"speed",localTick,1.2); setScalar(d,"size",localTick,0.45); setColor(d,"color",localTick,0xFFFFC65A); }
                case "smoke" -> { d.addProperty("shape","SPHERE"); setScalar(d,"ratePerSecond",localTick,55); setScalar(d,"spread",localTick,0.7); setScalar(d,"speed",localTick,0.12); setScalar(d,"size",localTick,1.8); setColor(d,"color",localTick,0xAA8B9096); }
                case "burst" -> { d.addProperty("shape","SPHERE"); setScalar(d,"ratePerSecond",localTick,1400); setScalar(d,"spread",localTick,1.0); setScalar(d,"speed",localTick,1.8); setScalar(d,"size",localTick,0.7); }
                default -> { return false; }
            }
            return true;
        }

        if (type.endsWith("ComplexElement$Volume")) {
            switch (p) {
                case "fog" -> { d.addProperty("shape","BOX"); setScalar(d,"density",localTick,0.18); setScalar(d,"noiseScale",localTick,0.8); setScalar(d,"distortion",localTick,0.08); setScalar(d,"emissive",localTick,0.0); setColor(d,"color",localTick,0x88D7E0E5); }
                case "energy" -> { d.addProperty("shape","SPHERE"); setScalar(d,"density",localTick,0.5); setScalar(d,"noiseScale",localTick,2.3); setScalar(d,"distortion",localTick,0.5); setScalar(d,"emissive",localTick,1.0); setColor(d,"color",localTick,0xCC6A8DFF); }
                case "smoke" -> { d.addProperty("shape","CYLINDER"); setScalar(d,"density",localTick,0.35); setScalar(d,"noiseScale",localTick,1.7); setScalar(d,"distortion",localTick,0.22); setScalar(d,"emissive",localTick,0.0); setColor(d,"color",localTick,0xAA6B6F74); }
                default -> { return false; }
            }
            return true;
        }

        if (type.endsWith("ComplexElement$Trail")) {
            switch (p) {
                case "ribbon" -> { d.addProperty("mode","RIBBON"); setScalar(d,"width",localTick,0.18); setScalar(d,"opacity",localTick,1); d.addProperty("maxPoints",256); d.addProperty("lifetimeTicks",40); }
                case "streak" -> { d.addProperty("mode","STREAK"); setScalar(d,"width",localTick,0.06); setScalar(d,"opacity",localTick,0.9); d.addProperty("maxPoints",128); d.addProperty("lifetimeTicks",18); }
                case "tube" -> { d.addProperty("mode","TUBE"); setScalar(d,"width",localTick,0.28); setScalar(d,"opacity",localTick,1); d.addProperty("maxPoints",192); d.addProperty("lifetimeTicks",55); }
                default -> { return false; }
            }
            return true;
        }

        if (type.endsWith("ComplexElement$Shadow")) {
            switch (p) {
                case "soft" -> { d.addProperty("mode","BLOB"); setScalar(d,"opacity",localTick,0.38); setScalar(d,"softness",localTick,0.86); setScalar(d,"radius",localTick,1.15); }
                case "hard" -> { d.addProperty("mode","PROJECTED"); setScalar(d,"opacity",localTick,0.72); setScalar(d,"softness",localTick,0.12); setScalar(d,"radius",localTick,1.0); }
                case "wide" -> { d.addProperty("mode","BLOB"); setScalar(d,"opacity",localTick,0.3); setScalar(d,"softness",localTick,0.75); setScalar(d,"radius",localTick,2.2); }
                default -> { return false; }
            }
            return true;
        }

        if (type.endsWith("AdvancedEventElement$Sky")) {
            switch (p) {
                case "sunset" -> { setColor(d,"horizonColor",localTick,0xFFFF8B56); setColor(d,"zenithColor",localTick,0xFF604F9E); setScalar(d,"sunBrightness",localTick,0.72); setScalar(d,"moonBrightness",localTick,0.25); }
                case "eclipse" -> { setColor(d,"horizonColor",localTick,0xFF473A45); setColor(d,"zenithColor",localTick,0xFF0E1220); setScalar(d,"eclipse",localTick,1); setScalar(d,"sunBrightness",localTick,0.12); }
                case "aurora" -> { setColor(d,"horizonColor",localTick,0xFF20395B); setColor(d,"zenithColor",localTick,0xFF071426); setScalar(d,"aurora",localTick,1); setScalar(d,"moonBrightness",localTick,1.2); }
                default -> { return false; }
            }
            return true;
        }

        if (type.endsWith("AdvancedEventElement$PlayerControl")) {
            switch (p) {
                case "cutscene" -> { d.addProperty("hideHud",true); d.addProperty("hideHand",true); d.addProperty("lockMovement",true); d.addProperty("lockLook",true); d.addProperty("allowJump",false); d.addProperty("allowInventory",false); setScalar(d,"movementScale",localTick,0); setScalar(d,"mouseScale",localTick,0); }
                case "freeze" -> { d.addProperty("lockMovement",true); d.addProperty("lockLook",false); setScalar(d,"movementScale",localTick,0); setScalar(d,"mouseScale",localTick,1); }
                case "release" -> { d.addProperty("hideHud",false); d.addProperty("hideHand",false); d.addProperty("lockMovement",false); d.addProperty("lockLook",false); d.addProperty("allowJump",true); d.addProperty("allowInventory",true); setScalar(d,"movementScale",localTick,1); setScalar(d,"mouseScale",localTick,1); }
                default -> { return false; }
            }
            return true;
        }

        if (type.endsWith("AdvancedEventElement$AudioLayer")) {
            switch (p) {
                case "music" -> { d.addProperty("looping",true); d.addProperty("music",true); d.addProperty("fadeInTicks",40); d.addProperty("fadeOutTicks",40); setScalar(d,"volume",localTick,0.85); setScalar(d,"pitch",localTick,1); }
                case "ambience" -> { d.addProperty("looping",true); d.addProperty("music",false); d.addProperty("fadeInTicks",80); d.addProperty("fadeOutTicks",80); setScalar(d,"volume",localTick,0.55); setScalar(d,"lowPass",localTick,0.08); }
                case "impact" -> { d.addProperty("looping",false); d.addProperty("music",false); d.addProperty("fadeInTicks",0); d.addProperty("fadeOutTicks",8); setScalar(d,"volume",localTick,1); setScalar(d,"pitch",localTick,1); }
                default -> { return false; }
            }
            return true;
        }

        if (type.endsWith("ComplexElement$Actor") || type.endsWith("ComplexElement$Mesh")) {
            switch (p) {
                case "solid" -> { setScalar(d,"opacity",localTick,1); setScalar(d,"emissive",localTick,0); setColor(d,"tint",localTick,0xFFFFFFFF); }
                case "ghost" -> { setScalar(d,"opacity",localTick,0.35); setScalar(d,"emissive",localTick,0.15); setColor(d,"tint",localTick,0xFFB7DFFF); }
                case "emissive" -> { setScalar(d,"opacity",localTick,1); setScalar(d,"emissive",localTick,1); setColor(d,"tint",localTick,0xFFFFFFFF); }
                default -> { return false; }
            }
            return true;
        }

        if (type.endsWith("ComplexElement$Decal")) {
            switch (p) {
                case "soft" -> { setScalar(d,"opacity",localTick,0.65); setScalar(d,"projectionDepth",localTick,1.8); d.addProperty("conformToSurface",true); }
                case "sharp" -> { setScalar(d,"opacity",localTick,1); setScalar(d,"projectionDepth",localTick,0.4); d.addProperty("conformToSurface",true); }
                case "glow" -> { setScalar(d,"opacity",localTick,0.9); setScalar(d,"projectionDepth",localTick,1.2); setColor(d,"tint",localTick,0xFF69D9FF); d.addProperty("conformToSurface",false); }
                default -> { return false; }
            }
            return true;
        }

        return false;
    }

    /** Adds a sensible visual default to complex arrays without exposing JSON. */
    public static boolean appendListItem(JsonObject owner, String field, double localTick) {
        if (owner == null || field == null) return false;
        JsonArray array = owner.has(field) && owner.get(field).isJsonArray() ? owner.getAsJsonArray(field) : new JsonArray();
        owner.add(field, array);
        if (!array.isEmpty()) {
            array.add(array.get(array.size() - 1).deepCopy());
            return true;
        }
        JsonObject item = new JsonObject();
        switch (field) {
            case "forces" -> {
                item.addProperty("kind","DIRECTIONAL"); item.add("offset",vec(0,0,0)); item.add("direction",vec(0,1,0));
                item.add("strength",scalarTrack(localTick,1)); item.add("radius",scalarTrack(localTick,8)); item.add("falloff",scalarTrack(localTick,1)); item.addProperty("seed",0L);
            }
            case "goals" -> {
                item.addProperty("chain","default"); item.addProperty("mode","LOOK_AT"); item.addProperty("endBone",""); item.addProperty("poleBone","");
                item.add("targetOffset",vec(0,1.6,4)); item.add("targetElementKey",com.google.gson.JsonNull.INSTANCE); item.add("weight",scalarTrack(localTick,1));
                item.addProperty("iterations",8); item.addProperty("tolerance",0.01); item.add("parameters",new JsonObject());
            }
            case "lights" -> {
                item.addProperty("kind","POINT"); item.add("offset",vec(0,0,0)); item.add("direction",vec(0,-1,0)); item.add("color",colorTrack(localTick,0xFFFFFFFF));
                item.add("intensity",scalarTrack(localTick,1)); item.add("radius",scalarTrack(localTick,12)); item.add("innerConeDegrees",scalarTrack(localTick,20));
                item.add("outerConeDegrees",scalarTrack(localTick,38)); item.addProperty("castShadow",true); item.add("volumetric",scalarTrack(localTick,0));
            }
            case "animations" -> {
                item.addProperty("clipId","minecraft:idle"); item.add("weight",scalarTrack(localTick,1)); item.add("speed",scalarTrack(localTick,1));
                item.addProperty("timeOffsetTicks",0); item.addProperty("looping",true); item.addProperty("blendMode","OVERRIDE"); item.add("parameters",new JsonObject());
            }
            case "boneOverrides" -> {
                item.addProperty("bone","head"); item.add("transform",advancedTransform()); item.add("weight",scalarTrack(localTick,1)); item.addProperty("blendMode","OVERRIDE");
            }
            case "morphs" -> { item.addProperty("name","morph"); item.add("weight",scalarTrack(localTick,0)); }
            case "agents" -> { item.add("offset",vec(0,0,0)); item.addProperty("yawDegrees",0); item.addProperty("timeOffsetTicks",0); item.addProperty("variant",0); }
            case "instances" -> {
                item.addProperty("key","instance"); item.add("baseOffset",vec(0,0,0)); item.add("transform",advancedTransform()); item.add("motion",motionNone());
                item.add("tint",colorTrack(localTick,0xFFFFFFFF)); item.add("opacity",scalarTrack(localTick,1)); item.addProperty("timeOffsetTicks",0); item.addProperty("timeScale",1); item.addProperty("variant",0);
            }
            case "links" -> { item.addProperty("a",0); item.addProperty("b",1); item.addProperty("restLength",1); item.addProperty("stiffness",0.9); }
            case "cells" -> { item.addProperty("modelId","minecraft:block/stone"); item.add("offset",vec(0,0,0)); item.add("bounds",vec(16,16,16)); item.addProperty("lodLevel",0); item.addProperty("variant",0); }
            case "points" -> {
                if (owner.has("$kind") && "PathTrack".equals(owner.get("$kind").getAsString())) {
                    item.addProperty("tick",localTick); item.add("position",vec(0,0,0)); item.add("inHandle",com.google.gson.JsonNull.INSTANCE);
                    item.add("outHandle",com.google.gson.JsonNull.INSTANCE); item.addProperty("easing","SMOOTH_STEP");
                } else { item.add("offset",vec(0,0,0)); item.addProperty("inverseMass",1); item.addProperty("pinned",false); }
            }
            default -> { item.addProperty("name","item"); }
        }
        array.add(item);
        return true;
    }

    public static void setScalar(JsonObject root, String field, double tick, double value) {
        JsonObject track = root.has(field) && root.get(field).isJsonObject() ? root.getAsJsonObject(field) : scalarTrack(0, value);
        if (!track.has("$kind")) track.addProperty("$kind","ScalarTrack");
        JsonArray keys = track.has("keys") && track.get("keys").isJsonArray() ? track.getAsJsonArray("keys") : new JsonArray();
        track.add("keys", keys);
        JsonObject key = nearestOrNew(keys, tick);
        key.addProperty("value", value);
        root.add(field, track);
    }

    public static void setColor(JsonObject root, String field, double tick, int argb) {
        JsonObject track = root.has(field) && root.get(field).isJsonObject() ? root.getAsJsonObject(field) : colorTrack(0, argb);
        if (!track.has("$kind")) track.addProperty("$kind","ColorTrack");
        JsonArray keys = track.has("keys") && track.get("keys").isJsonArray() ? track.getAsJsonArray("keys") : new JsonArray();
        track.add("keys", keys);
        JsonObject key = nearestOrNew(keys, tick);
        key.addProperty("value", String.format(Locale.ROOT,"#%08X",argb));
        root.add(field, track);
    }

    private static JsonObject nearestOrNew(JsonArray keys, double tick) {
        JsonObject best = null; double distance = Double.POSITIVE_INFINITY;
        for (JsonElement raw : keys) if (raw.isJsonObject()) {
            JsonObject key = raw.getAsJsonObject();
            double kt = number(key.get("tick"),0); double d = Math.abs(kt - tick);
            if (d < 0.001) return key;
            if (d < distance) { distance = d; best = key; }
        }
        JsonObject key = new JsonObject(); key.addProperty("tick",tick); key.addProperty("easing","SMOOTH_STEP"); keys.add(key); return key;
    }

    private static JsonObject scalarTrack(double tick, double value) {
        JsonObject track = new JsonObject(); track.addProperty("$kind","ScalarTrack"); JsonArray keys = new JsonArray();
        JsonObject key = new JsonObject(); key.addProperty("tick",tick); key.addProperty("value",value); key.addProperty("easing","SMOOTH_STEP"); keys.add(key); track.add("keys",keys); return track;
    }
    private static JsonObject colorTrack(double tick, int argb) {
        JsonObject track = new JsonObject(); track.addProperty("$kind","ColorTrack"); JsonArray keys = new JsonArray();
        JsonObject key = new JsonObject(); key.addProperty("tick",tick); key.addProperty("value",String.format(Locale.ROOT,"#%08X",argb)); key.addProperty("easing","SMOOTH_STEP"); keys.add(key); track.add("keys",keys); return track;
    }
    private static JsonObject object(JsonObject root, String field) {
        if (root.has(field) && root.get(field).isJsonObject()) return root.getAsJsonObject(field);
        JsonObject value = new JsonObject(); root.add(field,value); return value;
    }
    private static JsonObject vec(double x,double y,double z) { JsonObject o=new JsonObject(); o.addProperty("x",x);o.addProperty("y",y);o.addProperty("z",z);return o; }
    private static JsonObject advancedTransform() {
        JsonObject o=new JsonObject(); o.addProperty("$kind","AdvancedTransformTrack");
        o.add("translation",vecTrack(0,0,0)); o.add("rotationDegrees",vecTrack(0,0,0)); o.add("scale",vecTrack(1,1,1)); return o;
    }
    private static JsonObject vecTrack(double x,double y,double z) {
        JsonObject t=new JsonObject(); t.addProperty("$kind","Vec3Track"); JsonArray k=new JsonArray(); JsonObject key=new JsonObject();
        key.addProperty("tick",0); key.add("value",vec(x,y,z)); key.addProperty("easing","SMOOTH_STEP"); k.add(key); t.add("keys",k); return t;
    }
    private static JsonObject motionNone() { JsonObject o=new JsonObject();o.addProperty("preset","NONE");o.add("parameters",new JsonObject());return o; }
    private static double number(JsonElement value,double fallback){try{return value==null?fallback:value.getAsDouble();}catch(RuntimeException e){return fallback;}}
}
