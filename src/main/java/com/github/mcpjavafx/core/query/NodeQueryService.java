package com.github.mcpjavafx.core.query;

import com.github.mcpjavafx.core.fx.Fx;
import com.github.mcpjavafx.core.model.*;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Service for querying nodes in the scene graph.
 */
public class NodeQueryService {

    private final int fxTimeoutMs;

    public NodeQueryService(int fxTimeoutMs) {
        this.fxTimeoutMs = fxTimeoutMs;
    }

    /**
     * Result of a node query.
     */
    public record QueryMatch(
            NodeRef ref,
            String type,
            String id,
            String summary,
            LayoutInfo layout) {
    }

    /**
     * Query by CSS selector.
     *
     * @param stageIndex  stage to search in (-1 for focused)
     * @param cssSelector CSS selector
     * @param limit       maximum results
     * @return matching nodes
     */
    public List<QueryMatch> queryCss(int stageIndex, String cssSelector, int limit) {
        return Fx.exec(() -> queryCssOnFxThread(stageIndex, cssSelector, limit), fxTimeoutMs);
    }

    private List<QueryMatch> queryCssOnFxThread(int stageIndex, String cssSelector, int limit) {
        var scene = getScene(stageIndex);
        if (scene == null) {
            return List.of();
        }

        var nodes = scene.getRoot().lookupAll(cssSelector);
        return nodes.stream()
                .limit(limit)
                .map(this::toQueryMatch)
                .toList();
    }

    /**
     * Query by text content.
     *
     * @param stageIndex stage to search in (-1 for focused)
     * @param text       text to search for
     * @param matchMode  "contains", "equals", or "regex"
     * @param limit      maximum results
     * @return matching nodes
     */
    public List<QueryMatch> queryText(int stageIndex, String text, String matchMode, int limit) {
        return Fx.exec(() -> queryTextOnFxThread(stageIndex, text, matchMode, limit), fxTimeoutMs);
    }

    private List<QueryMatch> queryTextOnFxThread(int stageIndex, String text, String matchMode, int limit) {
        var scene = getScene(stageIndex);
        if (scene == null) {
            return List.of();
        }

        Predicate<String> matcher = createTextMatcher(text, matchMode);
        var results = new ArrayList<QueryMatch>();
        collectTextMatches(scene.getRoot(), matcher, results, limit);
        return results;
    }

    private Predicate<String> createTextMatcher(String text, String matchMode) {
        if (text == null || text.isEmpty()) {
            return s -> true;
        }

        return switch (matchMode != null ? matchMode.toLowerCase() : "contains") {
            case "equals" -> s -> text.equals(s != null ? s.trim() : null);
            case "regex" -> {
                var pattern = Pattern.compile(text, Pattern.CASE_INSENSITIVE);
                yield s -> s != null && pattern.matcher(s).find();
            }
            default -> s -> s != null && s.toLowerCase().contains(text.toLowerCase());
        };
    }

    private void collectTextMatches(Node node, Predicate<String> matcher, List<QueryMatch> results, int limit) {
        if (results.size() >= limit) {
            return;
        }

        var nodeText = extractText(node);
        if (nodeText != null && matcher.test(nodeText.trim())) {
            results.add(toQueryMatch(node));
        }

        if (node instanceof Parent parent) {
            for (var child : parent.getChildrenUnmodifiable()) {
                collectTextMatches(child, matcher, results, limit);
                if (results.size() >= limit) {
                    break;
                }
            }
        }
    }

    private String extractText(Node node) {
        if (node instanceof Labeled labeled) {
            return labeled.getText();
        } else if (node instanceof TextInputControl input) {
            return input.getText();
        } else if (node instanceof Text text) {
            return text.getText();
        }
        return null;
    }

    /**
     * Query by predicate.
     *
     * @param stageIndex stage to search in
     * @param predicate  query predicate
     * @param limit      maximum results
     * @return matching nodes
     */
    public List<QueryMatch> queryPredicate(int stageIndex, QueryPredicate predicate, int limit) {
        return Fx.exec(() -> queryPredicateOnFxThread(stageIndex, predicate, limit), fxTimeoutMs);
    }

    private List<QueryMatch> queryPredicateOnFxThread(int stageIndex, QueryPredicate predicate, int limit) {
        var scene = getScene(stageIndex);
        if (scene == null) {
            return List.of();
        }

        var results = new ArrayList<QueryMatch>();
        collectPredicateMatches(scene.getRoot(), predicate, results, limit);
        return results;
    }

    private void collectPredicateMatches(Node node, QueryPredicate pred, List<QueryMatch> results, int limit) {
        if (results.size() >= limit) {
            return;
        }

        if (matchesPredicate(node, pred)) {
            results.add(toQueryMatch(node));
        }

        if (node instanceof Parent parent) {
            for (var child : parent.getChildrenUnmodifiable()) {
                collectPredicateMatches(child, pred, results, limit);
                if (results.size() >= limit) {
                    break;
                }
            }
        }
    }

    private boolean matchesPredicate(Node node, QueryPredicate pred) {
        if (pred.typeIs() != null && !pred.typeIs().isEmpty()) {
            var typeName = node.getClass().getSimpleName();
            if (!pred.typeIs().contains(typeName)) {
                return false;
            }
        }

        if (pred.idEquals() != null) {
            if (!pred.idEquals().equals(node.getId())) {
                return false;
            }
        }

        if (pred.styleClassHas() != null) {
            if (!node.getStyleClass().contains(pred.styleClassHas())) {
                return false;
            }
        }

        if (pred.visible() != null) {
            if (node.isVisible() != pred.visible()) {
                return false;
            }
        }

        if (pred.enabled() != null) {
            if (node.isDisabled() == pred.enabled()) {
                return false;
            }
        }

        if (pred.textContains() != null) {
            var nodeText = extractText(node);
            if (nodeText == null || !nodeText.toLowerCase().contains(pred.textContains().toLowerCase())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Find a node by its reference.
     *
     * @param ref node reference
     * @return the node or null if not found
     */
    public Node findByRef(NodeRef ref) {
        return Fx.exec(() -> findByRefOnFxThread(ref), fxTimeoutMs);
    }

    private Node findByRefOnFxThread(NodeRef ref) {
        // First try by UID
        if (ref.uid() != null) {
            var node = findByUid(ref.uid());
            if (node != null) {
                return node;
            }
        }

        // Fall back to path
        if (ref.path() != null) {
            return findByPath(ref.path());
        }

        return null;
    }

    private Node findByUid(String uid) {
        for (var window : Window.getWindows()) {
            if (window instanceof Stage stage && stage.getScene() != null) {
                var node = findByUidInTree(stage.getScene().getRoot(), uid);
                if (node != null) {
                    return node;
                }
            }
        }
        return null;
    }

    private Node findByUidInTree(Node node, String uid) {
        if (uid.equals(node.getProperties().get(NodeRef.UID_PROPERTY_KEY))) {
            return node;
        }

        if (node instanceof Parent parent) {
            for (var child : parent.getChildrenUnmodifiable()) {
                var found = findByUidInTree(child, uid);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private Node findByPath(String path) {
        // Parse path: /stages[0]/scene/root/VBox[0]/Button[1]
        var parts = path.split("/");
        if (parts.length < 4) {
            return null;
        }

        // Extract stage index
        var stagesPart = parts[1]; // stages[0]
        if (!stagesPart.startsWith("stages[")) {
            return null;
        }
        var stageIndex = Integer.parseInt(
                stagesPart.substring("stages[".length(), stagesPart.length() - 1));

        var stages = getSortedStages();
        if (stageIndex >= stages.size()) {
            return null;
        }

        var stage = stages.get(stageIndex);
        if (stage.getScene() == null || stage.getScene().getRoot() == null) {
            return null;
        }

        // Navigate from root
        Node current = stage.getScene().getRoot();

        // Start from index 4 (after /stages[n]/scene/root)
        for (int i = 4; i < parts.length && current != null; i++) {
            current = navigateToChild(current, parts[i]);
        }

        return current;
    }

    private Node navigateToChild(Node parent, String pathPart) {
        if (!(parent instanceof Parent p)) {
            return null;
        }

        // Parse pathPart: Type[index]
        var bracketIdx = pathPart.indexOf('[');
        if (bracketIdx < 0) {
            return null;
        }

        var typeName = pathPart.substring(0, bracketIdx);
        var index = Integer.parseInt(
                pathPart.substring(bracketIdx + 1, pathPart.length() - 1));

        int typeCount = 0;
        for (var child : p.getChildrenUnmodifiable()) {
            if (child.getClass().getSimpleName().equals(typeName)) {
                if (typeCount == index) {
                    return child;
                }
                typeCount++;
            }
        }

        return null;
    }

    private Scene getScene(int stageIndex) {
        var stages = getSortedStages();

        if (stageIndex < 0) {
            // Find focused stage
            return stages.stream()
                    .filter(Stage::isFocused)
                    .findFirst()
                    .or(() -> stages.isEmpty() ? Optional.empty() : Optional.of(stages.getFirst()))
                    .map(Stage::getScene)
                    .orElse(null);
        }

        if (stageIndex >= stages.size()) {
            return null;
        }

        return stages.get(stageIndex).getScene();
    }

    private List<Stage> getSortedStages() {
        return Window.getWindows().stream()
                .filter(w -> w instanceof Stage stage && stage.isShowing())
                .map(w -> (Stage) w)
                .sorted(Comparator
                        .comparing((Stage s) -> s.getTitle() == null ? "" : s.getTitle())
                        .thenComparingInt(System::identityHashCode))
                .toList();
    }

    private QueryMatch toQueryMatch(Node node) {
        var stages = getSortedStages();
        var path = buildPath(node, stages);
        var uid = (String) node.getProperties().get(NodeRef.UID_PROPERTY_KEY);

        return new QueryMatch(
                new NodeRef(path, uid),
                node.getClass().getSimpleName(),
                node.getId(),
                buildSummary(node),
                captureLayout(node));
    }

    private String buildPath(Node node, List<Stage> stages) {
        if (node.getScene() == null)
            return null;

        var window = node.getScene().getWindow();
        if (!(window instanceof Stage stage))
            return null;

        int stageIndex = stages.indexOf(stage);
        if (stageIndex < 0)
            return null;

        var parts = new ArrayList<String>();
        var current = node;

        while (current != null) {
            var parent = current.getParent();
            if (parent != null) {
                int index = getChildIndex(parent, current);
                parts.addFirst(current.getClass().getSimpleName() + "[" + index + "]");
            } else {
                parts.addFirst("root");
            }
            current = parent;
        }

        parts.addFirst("scene");
        parts.addFirst("stages[" + stageIndex + "]");

        return "/" + String.join("/", parts);
    }

    private int getChildIndex(Parent parent, Node child) {
        var typeName = child.getClass().getSimpleName();
        int typeIndex = 0;

        for (var c : parent.getChildrenUnmodifiable()) {
            if (c == child) {
                return typeIndex;
            }
            if (c.getClass().getSimpleName().equals(typeName)) {
                typeIndex++;
            }
        }
        return 0;
    }

    private String buildSummary(Node node) {
        var sb = new StringBuilder(node.getClass().getSimpleName());
        var hasDetails = false;

        if (node instanceof Labeled labeled && labeled.getText() != null && !labeled.getText().isEmpty()) {
            sb.append("[text=").append(truncate(labeled.getText(), 20));
            hasDetails = true;
        } else if (node instanceof TextInputControl input && input.getText() != null) {
            sb.append("[text=").append(truncate(input.getText(), 20));
            hasDetails = true;
        }

        if (node.getId() != null && !node.getId().isEmpty()) {
            if (hasDetails) {
                sb.append(", ");
            } else {
                sb.append("[");
            }
            sb.append("id=").append(node.getId());
            hasDetails = true;
        }

        if (hasDetails) {
            sb.append("]");
        }

        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        if (s.length() <= maxLen)
            return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    private LayoutInfo captureLayout(Node node) {
        try {
            var boundsInScene = Bounds.from(node.localToScene(node.getBoundsInLocal()));
            return new LayoutInfo(null, boundsInScene, null);
        } catch (Exception e) {
            return null;
        }
    }
}
