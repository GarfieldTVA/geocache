package dev.garfield.cinefx.client;

import dev.garfield.cinefx.api.SceneDefinition;
import dev.garfield.cinefx.api.SceneElement;
import dev.garfield.cinefx.api.SceneOptions;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ActiveScene {
    private final long instanceId;
    private SceneDefinition definition;
    private final SceneOptions options;
    private final long startGameTime;
    private List<SceneElement> allElementsByPriority;
    private TemporalIndex temporalIndex;
    private Map<String, BlockState> sampledBlocks;

    ActiveScene(long instanceId, SceneDefinition definition, SceneOptions options, long startGameTime,
                MinecraftClient client) {
        this.instanceId = instanceId;
        this.options = options;
        this.startGameTime = startGameTime;
        installDefinition(definition, client);
    }

    /**
     * Replaces only the immutable definition backing this running instance. Timeline position,
     * instance id and SceneOptions stay intact, so editor hot-reload does not restart audio/camera
     * cues or jump the playhead back to zero.
     */
    void refreshDefinition(SceneDefinition replacement, MinecraftClient client) {
        if (replacement == null || !definition.id().equals(replacement.id())) return;
        installDefinition(replacement, client);
    }

    private void installDefinition(SceneDefinition next, MinecraftClient client) {
        if (next == null) throw new IllegalArgumentException("definition is required");
        this.definition = next;
        ArrayList<SceneElement> sorted = new ArrayList<>(next.elements());
        sorted.sort(Comparator.comparingInt(SceneElement::priority).reversed());
        this.allElementsByPriority = List.copyOf(sorted);
        this.temporalIndex = new TemporalIndex(allElementsByPriority, next.durationTicks());
        this.sampledBlocks = captureSampledBlocks(client, next);
    }

    private Map<String, BlockState> captureSampledBlocks(MinecraftClient client, SceneDefinition source) {
        if (client.world == null) return Map.of();
        HashMap<String, BlockState> result = new HashMap<>();
        BlockPos anchorBlock = BlockPos.ofFloored(options.anchor());
        for (SceneElement element : source.elements()) {
            if (element instanceof SceneElement.Block block && block.samplesWorld()) {
                result.put(block.key(), client.world.getBlockState(anchorBlock.add(block.sampleOffset())));
            }
        }
        return Map.copyOf(result);
    }

    public long instanceId() { return instanceId; }
    public SceneDefinition definition() { return definition; }
    public SceneOptions options() { return options; }
    public long startGameTime() { return startGameTime; }

    /**
     * Returns only the elements that can possibly be active in the current temporal bucket.
     * Existing render bridges can keep their normal activeAt() guard, but a ten-minute scene
     * with thousands of short cues no longer scans its entire definition every frame.
     */
    public List<SceneElement> elementsByPriority() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return allElementsByPriority;
        return temporalIndex.candidates(localTick(CineFxRuntime.absoluteGameTick(client)));
    }

    public List<SceneElement> activeElementsAt(double sceneTick) {
        if (sceneTick < 0.0) return List.of();
        List<SceneElement> candidates = temporalIndex.candidates(sceneTick);
        if (candidates.isEmpty()) return candidates;
        ArrayList<SceneElement> active = new ArrayList<>(candidates.size());
        for (SceneElement element : candidates) if (element.activeAt(sceneTick)) active.add(element);
        return active;
    }

    public List<SceneElement> allElementsByPriority() { return allElementsByPriority; }
    public BlockState sampledBlock(String elementKey) { return sampledBlocks.get(elementKey); }

    public double localTick(double absoluteGameTick) {
        double local = absoluteGameTick - startGameTime;
        if (!definition.looping()) return local;
        double duration = definition.durationTicks();
        double modulo = local % duration;
        return modulo < 0.0 ? modulo + duration : modulo;
    }

    public boolean completed(double absoluteGameTick) {
        return !definition.looping() && absoluteGameTick - startGameTime > definition.durationTicks();
    }

    /** Compact fixed-bucket interval index. Bucket count is capped for very long scenes. */
    private static final class TemporalIndex {
        private final double bucketSize;
        private final List<List<SceneElement>> buckets;

        TemporalIndex(List<SceneElement> sortedElements, double durationTicks) {
            double safeDuration = Math.max(1.0, durationTicks);
            this.bucketSize = Math.max(10.0, safeDuration / 2048.0);
            int bucketCount = Math.max(1, (int)Math.ceil(safeDuration / bucketSize) + 1);
            ArrayList<ArrayList<SceneElement>> mutable = new ArrayList<>(bucketCount);
            for (int i = 0; i < bucketCount; i++) mutable.add(new ArrayList<>());

            for (SceneElement element : sortedElements) {
                double start = Math.max(0.0, element.startTick());
                double rawEnd = Double.isFinite(element.endTick()) ? element.endTick() : safeDuration;
                double end = Math.max(start, Math.min(safeDuration, rawEnd));
                if (start > safeDuration) continue;
                int from = clamp((int)Math.floor(start / bucketSize), bucketCount);
                int to = clamp((int)Math.floor(end / bucketSize), bucketCount);
                for (int bucket = from; bucket <= to; bucket++) mutable.get(bucket).add(element);
            }

            ArrayList<List<SceneElement>> frozen = new ArrayList<>(bucketCount);
            for (ArrayList<SceneElement> bucket : mutable) frozen.add(List.copyOf(bucket));
            this.buckets = List.copyOf(frozen);
        }

        List<SceneElement> candidates(double sceneTick) {
            if (sceneTick < 0.0 || buckets.isEmpty()) return List.of();
            int index = clamp((int)Math.floor(sceneTick / bucketSize), buckets.size());
            return buckets.get(index);
        }

        private static int clamp(int value, int size) {
            return Math.max(0, Math.min(size - 1, value));
        }
    }
}
