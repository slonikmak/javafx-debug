package com.github.mcpjavafx.core.actions;

import com.github.mcpjavafx.core.fx.Fx;
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
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/**
 * Executes UI actions like click, type, focus, etc.
 */
public class ActionExecutor {

    private final int fxTimeoutMs;
    private final NodeQueryService queryService;
    private Robot robot;

    public ActionExecutor(int fxTimeoutMs, NodeQueryService queryService) {
        this.fxTimeoutMs = fxTimeoutMs;
        this.queryService = queryService;
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
     * Initializes the Robot on FX thread if not already done.
     */
    private Robot getRobot() {
        if (robot == null) {
            robot = Fx.exec(Robot::new, fxTimeoutMs);
        }
        return robot;
    }

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
    public ActionResult click(NodeRef ref) {
        return Fx.exec(() -> {
            var node = queryService.findByRef(ref);
            if (node == null) {
                return ActionResult.failure("click", "Node not found: " + ref);
            }

            bringWindowToFront(node);

            // Prefer deterministic UI-level activation for common controls.
            // This avoids flakiness when the window isn't focused or is covered by other windows.
            if (node instanceof ButtonBase button) {
                button.fire();
                return ActionResult.success("click");
            }

            var screenBounds = getScreenBounds(node);
            if (screenBounds == null) {
                return ActionResult.failure("click", "Cannot get screen bounds for node");
            }

            double centerX = screenBounds.getMinX() + screenBounds.getWidth() / 2;
            double centerY = screenBounds.getMinY() + screenBounds.getHeight() / 2;

            var robot = getRobot();
            robot.mouseMove(centerX, centerY);
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
     * Types text into the currently focused element.
     */
    public ActionResult typeText(String text) {
        return Fx.exec(() -> {
            var robot = getRobot();
            for (char c : text.toCharArray()) {
                robot.keyType(KeyCode.getKeyCode(String.valueOf(c).toUpperCase()));
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

            // Press modifiers
            for (var mod : modifiers != null ? modifiers : List.<String>of()) {
                var modCode = KeyCode.valueOf(mod.toUpperCase());
                robot.keyPress(modCode);
            }

            // Press and release key
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);

            // Release modifiers
            for (var mod : modifiers != null ? modifiers : List.<String>of()) {
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
            var node = queryService.findByRef(ref);
            if (node == null) {
                return ActionResult.failure("scroll", "Node not found: " + ref);
            }

            bringWindowToFront(node);

            var screenBounds = getScreenBounds(node);
            if (screenBounds == null) {
                return ActionResult.failure("scroll", "Cannot get screen bounds for node");
            }

            double centerX = screenBounds.getMinX() + screenBounds.getWidth() / 2;
            double centerY = screenBounds.getMinY() + screenBounds.getHeight() / 2;

            var robot = getRobot();
            robot.mouseMove(centerX, centerY);
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
        } catch (Exception ignored) {
            // Best-effort only; Robot actions will still be attempted.
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
            return null;
        }
    }

    private Bounds getScreenBounds(Node node) {
        try {
            return node.localToScreen(node.getBoundsInLocal());
        } catch (Exception e) {
            return null;
        }
    }

    private Stage getStage(int stageIndex) {
        var stages = Window.getWindows().stream()
                .filter(w -> w instanceof Stage stage && stage.isShowing())
                .map(w -> (Stage) w)
                .sorted(Comparator
                        .comparing((Stage s) -> s.getTitle() == null ? "" : s.getTitle())
                        .thenComparingInt(System::identityHashCode))
                .toList();

        if (stageIndex < 0) {
            return stages.stream()
                    .filter(Stage::isFocused)
                    .findFirst()
                    .orElse(stages.isEmpty() ? null : stages.getFirst());
        }

        return stageIndex < stages.size() ? stages.get(stageIndex) : null;
    }
}
