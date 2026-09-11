package dev.garfield.cinefx.api;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Transaction-like unit used by authoring tools and the remote publication protocol. Everything is
 * validated before any runtime registry is mutated, so a malformed bundle cannot leave the program
 * registry on a newer revision than its assets.
 */
public record EventPublication(EventProgramSpec program, List<AssetBundle> bundles) {
    public EventPublication {
        if (program == null) throw new IllegalArgumentException("program is required");
        program.compile();
        bundles = bundles == null ? List.of() : List.copyOf(bundles);
        Set<Identifier> ids = new HashSet<>();
        for (AssetBundle bundle : bundles) {
            if (bundle == null) throw new IllegalArgumentException("asset bundle is null");
            if (!ids.add(bundle.id())) throw new IllegalArgumentException("duplicate asset bundle id: " + bundle.id());
        }
    }

    /** Publishes the already-validated immutable snapshot into the hot-reload registries. */
    public synchronized void publish() {
        // Force compilation again immediately before publication to make this method safe when called
        // from external integrations that may have constructed a custom EventProgramSpec instance.
        program.compile();
        ArrayList<AssetBundle> checked = new ArrayList<>(bundles.size());
        Set<Identifier> ids = new HashSet<>();
        for (AssetBundle bundle : bundles) {
            if (bundle == null || !ids.add(bundle.id())) throw new IllegalArgumentException("invalid/duplicate asset bundle");
            checked.add(bundle);
        }
        EventProgramSpec.Registry.replace(program);
        for (AssetBundle bundle : checked) AssetBundle.Registry.replace(bundle);
    }
}
