package dev.maceguard;

import java.util.List;
import java.util.Map;

/** One armor set: which mace it counters, its trim, and how strongly it counters it. */
public record GuardSet(
        String id,
        String name,
        String counters,
        String base,
        String trimPattern,
        String trimMaterial,
        Map<String, Integer> enchants,
        double damageReduction,
        double effectReduction,
        double knockbackReduction,
        List<String> lore) {
}
