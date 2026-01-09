package com.github.mcpjavafx.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mcpjavafx.core.model.UiNode;
import com.github.mcpjavafx.core.model.UiSnapshot;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility to trim node payloads to only the requested fields/properties.
 */
public final class NodeFieldFilter {

    private NodeFieldFilter() {
    }

    public static Map<String, Object> filterSnapshot(ObjectMapper mapper, UiSnapshot snapshot, Set<String> nodeFields) {
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(snapshot, "snapshot");

        @SuppressWarnings("unchecked")
        var snapshotMap = (Map<String, Object>) mapper.convertValue(snapshot, Map.class);
        @SuppressWarnings("unchecked")
        var stages = (List<Map<String, Object>>) snapshotMap.get("stages");
        if (stages != null) {
            for (var stage : stages) {
                @SuppressWarnings("unchecked")
                var scene = (Map<String, Object>) stage.get("scene");
                if (scene != null) {
                    @SuppressWarnings("unchecked")
                    var root = (Map<String, Object>) scene.get("root");
                    if (root != null) {
                        scene.put("root", filterNodeMap(root, nodeFields, Set.of()));
                    }
                }
            }
        }
        return snapshotMap;
    }

    public static Map<String, Object> filterNode(ObjectMapper mapper, UiNode node, Set<String> fields, Set<String> properties) {
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(node, "node");

        @SuppressWarnings("unchecked")
        var nodeMap = (Map<String, Object>) mapper.convertValue(node, Map.class);
        return filterNodeMap(nodeMap, fields, properties);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> filterNodeMap(Map<String, Object> nodeMap, Set<String> fields, Set<String> properties) {
        var includeAll = fields == null || fields.isEmpty();
        var normalizedFields = normalizeFields(fields);

        var result = new LinkedHashMap<String, Object>();
        if (!includeAll) {
            normalizedFields.add("ref");
            normalizedFields.add("type");
        }

        for (var entry : nodeMap.entrySet()) {
            var key = entry.getKey();
            if (includeAll || normalizedFields.contains(key)) {
                Object value = entry.getValue();
                if (value == null) {
                    continue;
                }
                if ("children".equals(key) && value instanceof List<?> list) {
                    value = list.stream()
                            .filter(item -> item instanceof Map)
                            .map(item -> filterNodeMap((Map<String, Object>) item, fields, properties))
                            .toList();
                } else if ("fx".equals(key) && value instanceof Map<?, ?> fxMap) {
                    value = filterFxMap((Map<String, Object>) fxMap, properties);
                    if (value == null) {
                        continue;
                    }
                }
                result.put(key, value);
            }
        }
        return result;
    }

    private static Map<String, Object> filterFxMap(Map<String, Object> fxMap, Set<String> properties) {
        var filtered = new LinkedHashMap<String, Object>();
        for (var entry : fxMap.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if ("properties".equals(entry.getKey()) && entry.getValue() instanceof Map<?, ?> props) {
                if (properties != null && !properties.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    var filteredProps = ((Map<String, Object>) props).entrySet().stream()
                            .filter(e -> properties.contains(e.getKey()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    if (!filteredProps.isEmpty()) {
                        filtered.put("properties", filteredProps);
                    }
                } else {
                    filtered.put("properties", props);
                }
            } else {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered.isEmpty() ? null : filtered;
    }

    private static Set<String> normalizeFields(Set<String> fields) {
        if (fields == null) {
            return Set.of();
        }
        var normalized = new LinkedHashSet<String>();
        for (var field : fields) {
            if (field == null) {
                continue;
            }
            var f = field.trim();
            if (f.isEmpty()) {
                continue;
            }
            normalized.add(f);
            if ("bounds".equals(f)) {
                normalized.add("layout");
            }
            if ("properties".equals(f)) {
                normalized.add("fx");
            }
        }
        return normalized;
    }
}
