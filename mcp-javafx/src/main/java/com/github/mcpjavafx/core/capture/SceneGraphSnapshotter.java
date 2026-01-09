package com.github.mcpjavafx.core.capture;

import com.github.mcpjavafx.api.SnapshotOptions;
import com.github.mcpjavafx.core.fx.Fx;
import com.github.mcpjavafx.core.fx.NodeRefService;
import com.github.mcpjavafx.core.model.AccessibilityInfo;
import com.github.mcpjavafx.core.model.Bounds;
import com.github.mcpjavafx.core.model.FxProperties;
import com.github.mcpjavafx.core.model.LayoutInfo;
import com.github.mcpjavafx.core.model.NodeRef;
import com.github.mcpjavafx.core.model.ScreenBounds;
import com.github.mcpjavafx.core.model.TextInfo;
import com.github.mcpjavafx.core.model.UiNode;
import com.github.mcpjavafx.core.model.UiSnapshot;
import com.github.mcpjavafx.core.model.ValueInfo;
import com.github.mcpjavafx.core.model.VirtualizationInfo;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Captures the JavaFX scene graph as a serializable snapshot.
 */
public class SceneGraphSnapshotter {

    private final int fxTimeoutMs;
    private final NodeRefService nodeRefService = new NodeRefService();

    public SceneGraphSnapshotter(int fxTimeoutMs) {
        this.fxTimeoutMs = fxTimeoutMs;
    }

    /**
     * Stage selection mode for snapshot.
     */
    public enum StageMode {
        FOCUSED, // Currently focused stage
        PRIMARY, // First stage (index 0)
        ALL // All visible stages
    }

    /**
     * Captures a snapshot of the UI.
     *
     * @param mode       which stage(s) to capture
     * @param stageIndex specific stage index (for mode = INDEX)
     * @param options    snapshot options
     * @return UI snapshot
     */
    public UiSnapshot capture(StageMode mode, Integer stageIndex, SnapshotOptions options) {
        return Fx.exec(() -> captureOnFxThread(mode, stageIndex, options), fxTimeoutMs);
    }

    /**
     * Captures a snapshot for a single node (and optionally its direct children).
     *
     * <p>
     * This is used by get-node flows to avoid capturing a full stage tree.
     * </p>
     */
    public UiNode captureNodeDetails(Node node, boolean includeChildren, SnapshotOptions options) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(options, "options");

        return Fx.exec(() -> {
            var stages = getSortedStages();
            var stageIndex = resolveStageIndex(stages, node);

            var depth = includeChildren ? 1 : 0;
            var effectiveOptions = SnapshotOptions.builder()
                    .depth(depth)
                    .includeBounds(options.includeBounds())
                    .includeLocalToScreen(options.includeLocalToScreen())
                    .includeProperties(options.includeProperties())
                    .includeVirtualization(options.includeVirtualization())
                    .includeAccessibility(options.includeAccessibility())
                    .includeControlInternals(options.includeControlInternals())
                    .build();

            return captureNode(node, stageIndex, effectiveOptions, 0);
        }, fxTimeoutMs);
    }

    private UiSnapshot captureOnFxThread(StageMode mode, Integer stageIndex, SnapshotOptions options) {
        var stages = nodeRefService.getSortedStages();

        if (stages.isEmpty()) {
            return new UiSnapshot(
                    UiSnapshot.SCHEMA_VERSION,
                    Instant.now().toString(),
                    captureAppInfo(),
                    null,
                    List.of());
        }

        var selectedStages = selectStages(stages, mode, stageIndex);
        var focusInfo = captureFocusInfo(stages);

        var stageInfos = new ArrayList<UiSnapshot.StageInfo>();
        for (int i = 0; i < selectedStages.size(); i++) {
            var stage = selectedStages.get(i);
            int actualIndex = stages.indexOf(stage);
            stageInfos.add(captureStage(stage, actualIndex, options));
        }

        return new UiSnapshot(
                UiSnapshot.SCHEMA_VERSION,
                Instant.now().toString(),
                captureAppInfo(),
                focusInfo,
                stageInfos);
    }

    /**
     * Returns all showing stages sorted deterministically.
     */
    private List<Stage> getSortedStages() {
        return nodeRefService.getSortedStages();
    }

    private List<Stage> selectStages(List<Stage> stages, StageMode mode, Integer stageIndex) {
        return switch (mode) {
            case FOCUSED -> stages.stream()
                    .filter(Stage::isFocused)
                    .findFirst()
                    .map(List::of)
                    .orElse(stages.isEmpty() ? List.of() : List.of(stages.getFirst()));
            case PRIMARY -> stages.isEmpty() ? List.of() : List.of(stages.getFirst());
            case ALL -> stages;
        };
    }

    private UiSnapshot.AppInfo captureAppInfo() {
        return new UiSnapshot.AppInfo(
                ProcessHandle.current().pid(),
                System.getProperty("java.version"),
                System.getProperty("javafx.runtime.version", "unknown"),
                getMainClassName(),
                List.of("mcpEnabled"));
    }

    private String getMainClassName() {
        try {
            var stackTrace = Thread.currentThread().getStackTrace();
            if (stackTrace.length > 0) {
                return stackTrace[stackTrace.length - 1].getClassName();
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private UiSnapshot.FocusInfo captureFocusInfo(List<Stage> stages) {
        for (int i = 0; i < stages.size(); i++) {
            var stage = stages.get(i);
            if (stage.isFocused() && stage.getScene() != null) {
                var focusOwner = stage.getScene().getFocusOwner();
                if (focusOwner != null) {
                    return new UiSnapshot.FocusInfo(
                            nodeRefService.forNode(focusOwner, i),
                            new UiSnapshot.FocusedWindow(i));
                }
                return new UiSnapshot.FocusInfo(null, new UiSnapshot.FocusedWindow(i));
            }
        }
        return null;
    }

    private UiSnapshot.StageInfo captureStage(Stage stage, int stageIndex, SnapshotOptions options) {
        UiSnapshot.SceneInfo sceneInfo = null;

        if (stage.getScene() != null) {
            var scene = stage.getScene();
            var root = scene.getRoot();
            var rootNode = root != null ? captureNode(root, stageIndex, options, 0) : null;

            var stylesheets = scene.getStylesheets().stream().toList();
            sceneInfo = new UiSnapshot.SceneInfo(stylesheets, rootNode);
        }

        return new UiSnapshot.StageInfo(
                stageIndex,
                stage.getTitle(),
                stage.isShowing(),
                stage.isFocused(),
                stage.getX(),
                stage.getY(),
                stage.getWidth(),
                stage.getHeight(),
                sceneInfo);
    }

    private UiNode captureNode(Node node, int stageIndex, SnapshotOptions options, int depth) {
        if (options.skeleton()) {
            return captureSkeletonNode(node, stageIndex, options, depth);
        }

        var ref = nodeRefService.forNode(node, stageIndex);

        // Children
        List<UiNode> children = List.of();
        if (depth < options.depth() && node instanceof Parent parent) {
            if (shouldRecurse(node, options)) {
                children = parent.getChildrenUnmodifiable().stream()
                        .map(child -> captureNode(child, stageIndex, options, depth + 1))
                        .toList();
            }
        }

        return new UiNode(
                ref,
                node.getClass().getSimpleName(),
                getModuleName(node),
                node.getId(),
                new ArrayList<>(node.getStyleClass()),
                capturePseudoClasses(node),
                node.isVisible(),
                node.isManaged(),
                node.isDisabled(),
                node.getOpacity(),
                options.includeBounds() ? captureLayout(node, options.includeLocalToScreen()) : null,
                captureTextInfo(node),
                captureValueInfo(node),
                options.includeAccessibility() ? captureAccessibility(node) : null,
                options.includeProperties() ? captureFxProperties(node) : null,
                options.includeVirtualization() ? captureVirtualization(node) : null,
                children);
    }

    private boolean shouldRecurse(Node node, SnapshotOptions options) {
        if (options.includeControlInternals()) {
            return true;
        }
        // Black box controls - treat as leaves unless explicitly requested
        if (node instanceof TextInputControl ||
                node instanceof Labeled ||
                node instanceof Slider ||
                node instanceof ProgressBar ||
                node instanceof ProgressIndicator ||
                node instanceof ScrollBar ||
                node instanceof Separator ||
                node instanceof ComboBox ||
                node instanceof ChoiceBox ||
                node instanceof Spinner ||
                node instanceof ColorPicker ||
                node instanceof DatePicker) {
            return false;
        }
        return true;
    }

    private UiNode captureSkeletonNode(Node node, int stageIndex, SnapshotOptions options, int depth) {
        var ref = nodeRefService.forNode(node, stageIndex);
        List<UiNode> children = List.of();
        if (depth < options.depth() && node instanceof Parent parent) {
            if (shouldRecurse(node, options)) {
                children = parent.getChildrenUnmodifiable().stream()
                        .map(child -> captureSkeletonNode(child, stageIndex, options, depth + 1))
                        .toList();
            }
        }

        return new UiNode(
                ref,
                node.getClass().getSimpleName(),
                null,
                node.getId(),
                null,
                null,
                false,
                false,
                false,
                1.0,
                null,
                captureTextInfo(node),
                captureValueInfo(node),
                null,
                null,
                null,
                children);
    }

    private int resolveStageIndex(List<Stage> stages, Node node) {
        try {
            var scene = node.getScene();
            if (scene == null) {
                return 0;
            }
            var window = scene.getWindow();
            if (window instanceof Stage stage) {
                var idx = stages.indexOf(stage);
                return idx >= 0 ? idx : 0;
            }

            // Fallback: match by scene instance.
            for (int i = 0; i < stages.size(); i++) {
                var s = stages.get(i);
                if (s.getScene() == scene) {
                    return i;
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private NodeRef createNodeRef(Node node, String path) {
        return nodeRefService.forNode(node);
    }

    private String getOrCreateUid(Node node) {
        return nodeRefService.getOrCreateUid(node);
    }

    private String buildNodePath(Node node, int stageIndex) {
        return nodeRefService.buildPath(node, stageIndex);
    }

    private int getChildIndex(Parent parent, Node child) {
        var children = parent.getChildrenUnmodifiable();
        var typeName = child.getClass().getSimpleName();
        int typeIndex = 0;

        for (var c : children) {
            if (c == child) {
                return typeIndex;
            }
            if (c.getClass().getSimpleName().equals(typeName)) {
                typeIndex++;
            }
        }
        return 0;
    }

    private String getModuleName(Node node) {
        try {
            var module = node.getClass().getModule();
            return module.isNamed() ? module.getName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> capturePseudoClasses(Node node) {
        var states = new ArrayList<String>();
        if (node.isFocused())
            states.add("focused");
        if (node.isHover())
            states.add("hover");
        if (node.isPressed())
            states.add("pressed");
        if (node.isDisabled())
            states.add("disabled");

        if (node instanceof Control control) {
            if (control instanceof ToggleButton toggle && toggle.isSelected()) {
                states.add("selected");
            }
            if (control instanceof CheckBox checkBox && checkBox.isSelected()) {
                states.add("selected");
            }
        }

        return states;
    }

    private LayoutInfo captureLayout(Node node, boolean includeLocalToScreen) {
        var boundsInParent = Bounds.from(node.getBoundsInParent());
        var boundsInScene = Bounds.from(node.localToScene(node.getBoundsInLocal()));

        ScreenBounds localToScreen = null;
        if (includeLocalToScreen) {
            try {
                var screenBounds = node.localToScreen(node.getBoundsInLocal());
                if (screenBounds != null) {
                    localToScreen = new ScreenBounds(
                            screenBounds.getMinX(),
                            screenBounds.getMinY(),
                            screenBounds.getWidth(),
                            screenBounds.getHeight());
                }
            } catch (Exception ignored) {
                // Node may not be attached to a scene/window
            }
        }

        return new LayoutInfo(boundsInParent, boundsInScene, localToScreen);
    }

    private TextInfo captureTextInfo(Node node) {
        String label = null;
        String prompt = null;

        if (node instanceof Labeled labeled) {
            label = labeled.getText();
        } else if (node instanceof Text text) {
            label = text.getText();
        } else if (node instanceof TextInputControl input) {
            label = input.getText();
            prompt = input.getPromptText();
        }

        if (label == null && prompt == null) {
            return null;
        }

        return new TextInfo(label, prompt);
    }

    private ValueInfo captureValueInfo(Node node) {
        String text = null;
        Boolean selected = null;
        Boolean checked = null;

        if (node instanceof TextInputControl input) {
            text = input.getText();
        }
        if (node instanceof ToggleButton toggle) {
            selected = toggle.isSelected();
        }
        if (node instanceof CheckBox checkBox) {
            checked = checkBox.isSelected();
        }
        if (node instanceof RadioButton radio) {
            selected = radio.isSelected();
        }

        if (text == null && selected == null && checked == null) {
            return null;
        }

        return new ValueInfo(text, selected, checked);
    }

    private AccessibilityInfo captureAccessibility(Node node) {
        var role = node.getAccessibleRole();
        var help = node.getAccessibleHelp();

        if (role == null && help == null) {
            return null;
        }

        return new AccessibilityInfo(
                role != null ? role.name() : null,
                help);
    }

    private FxProperties captureFxProperties(Node node) {
        String tooltip = null;
        Object userData = node.getUserData();

        if (node instanceof Control control) {
            var tt = control.getTooltip();
            if (tt != null) {
                tooltip = tt.getText();
            }
        }

        // Safe serialization for userData to avoid Jackson issues with arbitrary
        // objects
        Object safeUserData = null;
        if (userData != null) {
            if (userData instanceof String || userData instanceof Number || userData instanceof Boolean) {
                safeUserData = userData;
            } else {
                safeUserData = userData.toString();
            }
        }

        if (tooltip == null && safeUserData == null) {
            return null;
        }

        return new FxProperties(tooltip, safeUserData, null);
    }

    private VirtualizationInfo captureVirtualization(Node node) {
        if (node instanceof ListView<?> listView) {
            return captureListView(listView);
        } else if (node instanceof TableView<?> tableView) {
            return captureTableView(tableView);
        } else if (node instanceof TreeView<?> treeView) {
            return captureTreeView(treeView);
        }
        return null;
    }

    private VirtualizationInfo captureListView(ListView<?> listView) {
        var items = listView.getItems();
        var selectionModel = listView.getSelectionModel();

        return new VirtualizationInfo(
                "ListView",
                items != null ? items.size() : 0,
                null, // visibleRange - would need VirtualFlow access
                selectionModel.getSelectedIndices().stream().toList(),
                selectionModel.getSelectedIndex() >= 0 ? selectionModel.getSelectedIndex() : null,
                null,
                null // visibleCells - simplified for now
        );
    }

    private VirtualizationInfo captureTableView(TableView<?> tableView) {
        var items = tableView.getItems();
        var selectionModel = tableView.getSelectionModel();

        var columns = tableView.getColumns().stream()
                .map(col -> new VirtualizationInfo.ColumnInfo(col.getId(), col.getText()))
                .toList();

        return new VirtualizationInfo(
                "TableView",
                items != null ? items.size() : 0,
                null,
                selectionModel.getSelectedIndices().stream().toList(),
                selectionModel.getSelectedIndex() >= 0 ? selectionModel.getSelectedIndex() : null,
                columns,
                null);
    }

    private VirtualizationInfo captureTreeView(TreeView<?> treeView) {
        var selectionModel = treeView.getSelectionModel();
        var root = treeView.getRoot();
        int itemCount = root != null ? countTreeItems(root) : 0;

        return new VirtualizationInfo(
                "TreeView",
                itemCount,
                null,
                selectionModel.getSelectedIndices().stream().toList(),
                selectionModel.getSelectedIndex() >= 0 ? selectionModel.getSelectedIndex() : null,
                null,
                null);
    }

    private int countTreeItems(TreeItem<?> item) {
        int count = 1;
        if (item.isExpanded()) {
            for (var child : item.getChildren()) {
                count += countTreeItems(child);
            }
        }
        return count;
    }
}
