package com.github.mcpjavafx.core.actions;

import com.github.mcpjavafx.core.fx.Fx;
import com.github.mcpjavafx.core.fx.NodeRefService;
import com.github.mcpjavafx.core.model.NodeRef;
import com.github.mcpjavafx.core.query.NodeQueryService;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Executes UI actions like click, type, focus, etc.
 */
public class ActionExecutor {

    private static final Logger LOG = Logger.getLogger(ActionExecutor.class.getName());

    // --- Constants to avoid magic numbers ---
    private static final int DRAG_STEPS = 10;
    private static final int DRAG_STEP_DELAY_MS = 10;
    private static final int DEFAULT_DOUBLE_CLICK_INTERVAL_MS = 200;
    private static final int DOUBLE_CLICK_WAIT_DIVISOR = 4;

    private final int fxTimeoutMs;
    private final NodeQueryService queryService;
    private final NodeRefService nodeRefService;
    private Robot robot;

    public ActionExecutor(int fxTimeoutMs, NodeQueryService queryService, NodeRefService nodeRefService) {
        if (fxTimeoutMs <= 0) {
            throw new IllegalArgumentException("fxTimeoutMs must be positive");
        }
        this.fxTimeoutMs = fxTimeoutMs;
        this.queryService = Objects.requireNonNull(queryService, "queryService");
        this.nodeRefService = Objects.requireNonNull(nodeRefService, "nodeRefService");
    }

    /**
     * Result of an action execution.
     */
    public record ActionResult(boolean ok, String type, String error) {
        public static ActionResult success(String type) {
            return new ActionResult(true, type, null);
        }

        public static ActionResult failure(String type, String error) {
            return new ActionResult(false, type, error);
        }
    }

    /**
     * Screen position for mouse actions.
     */
    private record ScreenPosition(double x, double y) {
    }

    /**
     * Result of resolving a node with its screen bounds.
     */
    private record ResolvedNode(Node node, Bounds screenBounds) {
    }

    /**
     * Initializes the Robot on FX thread if not already done.
     */
    private Robot getRobot() {
        if (robot == null) {
            robot = Fx.exec(Robot::new, fxTimeoutMs);
        }
        return robot;
    }

    // --- Common Helper Methods (DRY) ---

    /**
     * Calculates screen position based on bounds and optional offsets.
     * If x/y are provided, uses them as offsets from bounds origin.
     * Otherwise, returns the center of the bounds.
     */
    private ScreenPosition calculatePosition(Bounds screenBounds, Double x, Double y) {
        if (x != null && y != null) {
            return new ScreenPosition(screenBounds.getMinX() + x, screenBounds.getMinY() + y);
        }
        return new ScreenPosition(
                screenBounds.getMinX() + screenBounds.getWidth() / 2,
                screenBounds.getMinY() + screenBounds.getHeight() / 2);
    }

    /**
     * Resolves a node reference and gets its screen bounds.
     * Returns null if node not found or bounds unavailable.
     */
    private ResolvedNode resolveNode(NodeRef ref, String actionType) {
        if (ref == null) {
            return null;
        }

        var node = queryService.findByRef(ref);
        if (node == null) {
            return null;
        }

        bringWindowToFront(node);

        var screenBounds = getScreenBounds(node);
        if (screenBounds == null) {
            return null;
        }

        return new ResolvedNode(node, screenBounds);
    }

    /**
     * Sleeps for the specified duration, properly handling interruption.
     */
    private void sleepSafely(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(Level.FINE, "Sleep interrupted", e);
        }
    }

    // --- Action Methods ---

    /**
     * Focuses a node.
     */
    public ActionResult focus(NodeRef ref) {
        return Fx.exec(() -> {
            var node = queryService.findByRef(ref);
            if (node == null) {
                return ActionResult.failure("focus", "Node not found: " + ref);
            }
            node.requestFocus();
            return ActionResult.success("focus");
        }, fxTimeoutMs);
    }

    /**
     * Clicks on a node (center).
     */
    public ActionResult click(NodeRef ref, Double x, Double y) {
        return Fx.exec(() -> {
            var node = queryService.findByRef(ref);
            if (node == null) {
                return ActionResult.failure("click", "Node not found: " + ref);
            }

            bringWindowToFront(node);

            // Prefer deterministic UI-level activation for common controls.
            // This avoids flakiness when the window isn't focused or is covered.
            if (node instanceof ButtonBase button && x == null && y == null) {
                button.fire();
                return ActionResult.success("click");
            }

            var screenBounds = getScreenBounds(node);
            if (screenBounds == null) {
                return ActionResult.failure("click", "Cannot get screen bounds for node");
            }

            var pos = calculatePosition(screenBounds, x, y);

            var robot = getRobot();
            robot.mouseMove(pos.x(), pos.y());
            robot.mouseClick(MouseButton.PRIMARY);

            return ActionResult.success("click");
        }, fxTimeoutMs);
    }

    /**
     * Clicks at specific screen coordinates.
     */
    public ActionResult clickAt(double x, double y) {
        return Fx.exec(() -> {
            var robot = getRobot();
            robot.mouseMove(x, y);
            robot.mouseClick(MouseButton.PRIMARY);
            return ActionResult.success("click");
        }, fxTimeoutMs);
    }

    /**
     * Double-clicks on a node (center).
     */
    public ActionResult doubleClick(NodeRef ref, Double x, Double y) {
        return Fx.exec(() -> {
            var resolved = resolveNode(ref, "doubleClick");
            if (resolved == null) {
                return ActionResult.failure("doubleClick", "Node not found or cannot get screen bounds: " + ref);
            }

            var pos = calculatePosition(resolved.screenBounds(), x, y);
            var robot = getRobot();
            robot.mouseMove(pos.x(), pos.y());

            int doubleClickInterval = getDoubleClickInterval();

            // First click
            robot.mousePress(MouseButton.PRIMARY);
            robot.mouseRelease(MouseButton.PRIMARY);

            // Wait a small fraction of the threshold
            sleepSafely(Math.max(10, doubleClickInterval / DOUBLE_CLICK_WAIT_DIVISOR));

            // Second click
            robot.mousePress(MouseButton.PRIMARY);
            robot.mouseRelease(MouseButton.PRIMARY);

            return ActionResult.success("doubleClick");
        }, fxTimeoutMs);
    }

    /**
     * Presses mouse button on a node (without releasing).
     */
    public ActionResult mousePressed(NodeRef ref, String button, Double x, Double y) {
        return Fx.exec(() -> {
            var resolved = resolveNode(ref, "mousePressed");
            if (resolved == null) {
                return ActionResult.failure("mousePressed", "Node not found or cannot get screen bounds: " + ref);
            }

            var pos = calculatePosition(resolved.screenBounds(), x, y);

            var robot = getRobot();
            robot.mouseMove(pos.x(), pos.y());
            robot.mousePress(parseMouseButton(button));

            return ActionResult.success("mousePressed");
        }, fxTimeoutMs);
    }

    /**
     * Releases mouse button on a node.
     */
    public ActionResult mouseReleased(NodeRef ref, String button, Double x, Double y) {
        return Fx.exec(() -> {
            if (ref != null) {
                var resolved = resolveNode(ref, "mouseReleased");
                if (resolved != null) {
                    var pos = calculatePosition(resolved.screenBounds(), x, y);
                    getRobot().mouseMove(pos.x(), pos.y());
                }
            } else if (x != null && y != null) {
                getRobot().mouseMove(x, y);
            }

            getRobot().mouseRelease(parseMouseButton(button));
            return ActionResult.success("mouseReleased");
        }, fxTimeoutMs);
    }

    /**
     * Performs a drag operation from source to target.
     */
    public ActionResult drag(NodeRef fromRef, Double fromX, Double fromY,
            NodeRef toRef, Double toX, Double toY, String button) {
        return Fx.exec(() -> {
            var robot = getRobot();
            var mouseButton = parseMouseButton(button);

            // Determine start position
            ScreenPosition start = resolveStartPosition(fromRef, fromX, fromY);
            if (start == null) {
                return ActionResult.failure("drag", "No valid source specified");
            }

            // Determine end position
            ScreenPosition end = resolveEndPosition(toRef, toX, toY);
            if (end == null) {
                return ActionResult.failure("drag", "No valid target specified");
            }

            // Execute drag sequence
            robot.mouseMove(start.x(), start.y());
            robot.mousePress(mouseButton);

            // Move in steps to trigger proper drag events
            for (int i = 1; i <= DRAG_STEPS; i++) {
                double stepX = start.x() + (end.x() - start.x()) * i / DRAG_STEPS;
                double stepY = start.y() + (end.y() - start.y()) * i / DRAG_STEPS;
                robot.mouseMove(stepX, stepY);
                sleepSafely(DRAG_STEP_DELAY_MS);
            }

            robot.mouseRelease(mouseButton);

            return ActionResult.success("drag");
        }, fxTimeoutMs);
    }

    private ScreenPosition resolveStartPosition(NodeRef fromRef, Double fromX, Double fromY) {
        if (fromRef != null) {
            var resolved = resolveNode(fromRef, "drag");
            if (resolved == null) {
                return null;
            }
            return calculatePosition(resolved.screenBounds(), fromX, fromY);
        } else if (fromX != null && fromY != null) {
            return new ScreenPosition(fromX, fromY);
        }
        return null;
    }

    private ScreenPosition resolveEndPosition(NodeRef toRef, Double toX, Double toY) {
        if (toRef != null) {
            var resolved = resolveNode(toRef, "drag");
            if (resolved == null) {
                return null;
            }
            return calculatePosition(resolved.screenBounds(), toX, toY);
        } else if (toX != null && toY != null) {
            return new ScreenPosition(toX, toY);
        }
        return null;
    }

    private MouseButton parseMouseButton(String button) {
        if (button == null || button.isBlank()) {
            return MouseButton.PRIMARY;
        }
        return switch (button.toUpperCase()) {
            case "SECONDARY", "RIGHT" -> MouseButton.SECONDARY;
            case "MIDDLE" -> MouseButton.MIDDLE;
            default -> MouseButton.PRIMARY;
        };
    }

    private int getDoubleClickInterval() {
        try {
            Object interval = java.awt.Toolkit.getDefaultToolkit()
                    .getDesktopProperty("awt.multiClickInterval");
            if (interval instanceof Integer i) {
                return i;
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not get double-click interval from system", e);
        }
        return DEFAULT_DOUBLE_CLICK_INTERVAL_MS;
    }

    /**
     * Types text into the currently focused element.
     */
    public ActionResult typeText(String text) {
        return Fx.exec(() -> {
            var stage = getStage(-1);
            if (stage != null && stage.getScene() != null) {
                var focusOwner = stage.getScene().getFocusOwner();
                if (focusOwner instanceof TextInputControl input) {
                    input.insertText(input.getCaretPosition(), text);
                    return ActionResult.success("typeText");
                }
            }

            var robot = getRobot();
            for (char c : text.toCharArray()) {
                String s = String.valueOf(c);
                if (" ".equals(s)) {
                    robot.keyType(KeyCode.SPACE);
                    continue;
                }
                var keyCode = KeyCode.getKeyCode(s.toUpperCase());
                if (keyCode != null) {
                    robot.keyType(keyCode);
                }
            }
            return ActionResult.success("typeText");
        }, fxTimeoutMs);
    }

    /**
     * Sets text on a TextInputControl.
     */
    public ActionResult setText(NodeRef ref, String text) {
        return Fx.exec(() -> {
            var node = queryService.findByRef(ref);
            if (node == null) {
                return ActionResult.failure("setText", "Node not found: " + ref);
            }

            if (!(node instanceof TextInputControl input)) {
                return ActionResult.failure("setText",
                        "Node is not a TextInputControl: " + node.getClass().getSimpleName());
            }

            input.setText(text);
            input.positionCaret(text.length());
            return ActionResult.success("setText");
        }, fxTimeoutMs);
    }

    /**
     * Presses a key with optional modifiers.
     */
    public ActionResult pressKey(String key, List<String> modifiers) {
        return Fx.exec(() -> {
            var robot = getRobot();
            var keyCode = KeyCode.valueOf(key.toUpperCase());
            var effectiveModifiers = modifiers != null ? modifiers : List.<String>of();

            // Press modifiers
            for (var mod : effectiveModifiers) {
                var modCode = KeyCode.valueOf(mod.toUpperCase());
                robot.keyPress(modCode);
            }

            // Press and release key
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);

            // Release modifiers
            for (var mod : effectiveModifiers) {
                var modCode = KeyCode.valueOf(mod.toUpperCase());
                robot.keyRelease(modCode);
            }

            return ActionResult.success("pressKey");
        }, fxTimeoutMs);
    }

    /**
     * Scrolls on a node.
     */
    public ActionResult scroll(NodeRef ref, int deltaY) {
        return Fx.exec(() -> {
            var resolved = resolveNode(ref, "scroll");
            if (resolved == null) {
                return ActionResult.failure("scroll", "Node not found or cannot get screen bounds: " + ref);
            }

            var pos = calculatePosition(resolved.screenBounds(), null, null);

            var robot = getRobot();
            robot.mouseMove(pos.x(), pos.y());
            robot.mouseWheel(deltaY);

            return ActionResult.success("scroll");
        }, fxTimeoutMs);
    }

    private void bringWindowToFront(Node node) {
        try {
            if (node.getScene() == null) {
                return;
            }

            var window = node.getScene().getWindow();
            if (window == null) {
                return;
            }

            if (window instanceof Stage stage) {
                stage.toFront();
                stage.requestFocus();
            } else {
                window.requestFocus();
            }
        } catch (Exception e) {
            // Best-effort only; Robot actions will still be attempted.
            LOG.log(Level.FINE, "Could not bring window to front", e);
        }
    }

    /**
     * Takes a screenshot of a stage.
     *
     * @param stageIndex stage index (-1 for focused)
     * @return base64 encoded PNG
     */
    public String screenshot(int stageIndex) {
        return Fx.exec(() -> {
            var stage = getStage(stageIndex);
            if (stage == null || stage.getScene() == null) {
                return null;
            }

            var scene = stage.getScene();
            var root = scene.getRoot();

            var params = new SnapshotParameters();
            var image = root.snapshot(params, null);

            return encodeImageToBase64(image);
        }, fxTimeoutMs);
    }

    private String encodeImageToBase64(WritableImage image) {
        try {
            var bufferedImage = SwingFXUtils.fromFXImage(image, null);
            var baos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to encode image to base64", e);
            return null;
        }
    }

    private Bounds getScreenBounds(Node node) {
        try {
            return node.localToScreen(node.getBoundsInLocal());
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not get screen bounds for node", e);
            return null;
        }
    }

    private Stage getStage(int stageIndex) {
        var stages = nodeRefService.getSortedStages();

        if (stageIndex < 0) {
            return stages.stream()
                    .filter(Stage::isFocused)
                    .findFirst()
                    .orElse(stages.isEmpty() ? null : stages.get(0));
        }

        return stageIndex < stages.size() ? stages.get(stageIndex) : null;
    }
}
