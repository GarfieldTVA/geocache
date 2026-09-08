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
    private final SceneDefinition definition;
    private final SceneOptions options;
    private final long startGameTime;
    private final List<SceneElement> elementsByPriority;
    private final Map<String, BlockState> sampledBlocks;

    ActiveScene(long instanceId, SceneDefinition definition, SceneOptions options, long startGameTime,
                MinecraftClient client) {
        this.instanceId = instanceId;
        this.definition = definition;
        this.options = options;
        this.startGameTime = startGameTime;
        ArrayList<SceneElement> sorted = new ArrayList<>(definition.elements());
        sorted.sort(Comparator.comparingInt(SceneElement::priority).reversed());
        this.elementsByPriority = List.copyOf(sorted);
        this.sampledBlocks = captureSampledBlocks(client);
    }

    private Map<String, BlockState> captureSampledBlocks(MinecraftClient client) {
        if (client.world == null) return Map.of();
        HashMap<String, BlockState> result = new HashMap<>();
        BlockPos anchorBlock = BlockPos.ofFloored(options.anchor());
        for (SceneElement element : definition.elements()) {
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
    public List<SceneElement> elementsByPriority() { return elementsByPriority; }
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
}
