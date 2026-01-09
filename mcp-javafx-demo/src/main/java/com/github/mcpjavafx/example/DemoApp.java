package com.github.mcpjavafx.example;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * Demo JavaFX application with MCP debug enabled.
 * Includes a map-like canvas for testing zoom, pan, points and lines.
 * 
 * Run with: java -Dmcp.ui=true -javaagent:path/to/mcp-javafx.jar DemoApp
 */
public class DemoApp extends Application {

    private enum Mode {
        PAN, POINT, LINE
    }

    private Mode currentMode = Mode.PAN;
    private final List<Point2D> points = new ArrayList<>();
    private final List<Point2D> polyline = new ArrayList<>();

    // Transform state
    private double translateX = 0;
    private double translateY = 0;
    private double scale = 1.0;

    // Drag state
    private double dragStartX;
    private double dragStartY;
    private double dragStartTranslateX;
    private double dragStartTranslateY;

    private Canvas canvas;

    @Override
    public void start(Stage stage) {
        // Build original simple UI
        var label = new Label("Hello, MCP!");
        label.setId("greeting");

        var textField = new TextField();
        textField.setId("input");
        textField.setPromptText("Enter your name");

        var button = new Button("Submit");
        button.setId("submitBtn");
        Runnable submitAction = () -> label.setText("Hello, " + textField.getText() + "!");
        button.setOnAction(e -> submitAction.run());
        textField.setOnAction(e -> submitAction.run());

        var simpleUi = new VBox(10, label, textField, button);
        simpleUi.setPadding(new Insets(10));
        simpleUi.setId("simpleUi");

        // Build map canvas section
        var mapSection = createMapSection();
        mapSection.setId("mapSection");
        VBox.setVgrow(mapSection, Priority.ALWAYS);

        var root = new VBox(10, simpleUi, new Separator(), mapSection);
        root.setPadding(new Insets(10));
        root.setId("root");

        var scene = new Scene(root, 600, 500);
        stage.setTitle("MCP Demo with Map Canvas");
        stage.setScene(scene);
        stage.show();
    }

    private VBox createMapSection() {
        var titleLabel = new Label("Map Canvas (Zoom: scroll, Pan: drag, Points: Ctrl+click, Lines: Shift+click)");
        titleLabel.setId("mapTitle");

        // Mode toggle buttons
        var toggleGroup = new ToggleGroup();

        var panBtn = new ToggleButton("Pan");
        panBtn.setId("panModeBtn");
        panBtn.setToggleGroup(toggleGroup);
        panBtn.setSelected(true);
        panBtn.setOnAction(e -> {
            if (panBtn.isSelected()) {
                currentMode = Mode.PAN;
            }
        });

        var pointBtn = new ToggleButton("Add Point");
        pointBtn.setId("pointModeBtn");
        pointBtn.setToggleGroup(toggleGroup);
        pointBtn.setOnAction(e -> {
            if (pointBtn.isSelected()) {
                currentMode = Mode.POINT;
            }
        });

        var lineBtn = new ToggleButton("Add Line");
        lineBtn.setId("lineModeBtn");
        lineBtn.setToggleGroup(toggleGroup);
        lineBtn.setOnAction(e -> {
            if (lineBtn.isSelected()) {
                currentMode = Mode.LINE;
            }
        });

        // Auto-switch to Pan when no toggle selected
        toggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                panBtn.setSelected(true);
                currentMode = Mode.PAN;
            }
        });

        var resetBtn = new Button("Reset View");
        resetBtn.setId("resetViewBtn");
        resetBtn.setOnAction(e -> resetView());

        var clearBtn = new Button("Clear All");
        clearBtn.setId("clearAllBtn");
        clearBtn.setOnAction(e -> clearAll());

        var zoomLabel = new Label("Zoom: 100%");
        zoomLabel.setId("zoomLabel");

        var toolbar = new HBox(10, panBtn, pointBtn, lineBtn, resetBtn, clearBtn, zoomLabel);
        toolbar.setPadding(new Insets(5, 0, 5, 0));
        toolbar.setId("mapToolbar");

        // Canvas in a resizable container
        canvas = new Canvas(580, 350);
        canvas.setId("mapCanvas");

        var canvasContainer = new Pane(canvas);
        canvasContainer.setId("canvasContainer");
        canvasContainer.setStyle("-fx-border-color: gray; -fx-border-width: 1;");
        VBox.setVgrow(canvasContainer, Priority.ALWAYS);

        // Make canvas resize with container
        canvasContainer.widthProperty().addListener((obs, oldVal, newVal) -> {
            canvas.setWidth(newVal.doubleValue() - 2);
            redrawCanvas();
        });
        canvasContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            canvas.setHeight(newVal.doubleValue() - 2);
            redrawCanvas();
        });

        // Mouse handlers
        canvas.setOnMousePressed(e -> {
            if (currentMode == Mode.PAN) {
                canvas.setCursor(Cursor.CLOSED_HAND);
                dragStartX = e.getX();
                dragStartY = e.getY();
                dragStartTranslateX = translateX;
                dragStartTranslateY = translateY;
            } else if (currentMode == Mode.POINT) {
                addPoint(e.getX(), e.getY());
            } else if (currentMode == Mode.LINE) {
                handleLineClick(e.getX(), e.getY());
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (currentMode == Mode.PAN) {
                translateX = dragStartTranslateX + (e.getX() - dragStartX);
                translateY = dragStartTranslateY + (e.getY() - dragStartY);
                redrawCanvas();
            }
        });

        canvas.setOnMouseReleased(e -> {
            if (currentMode == Mode.PAN) {
                canvas.setCursor(Cursor.OPEN_HAND);
            }
        });

        canvas.setOnMouseEntered(e -> {
            if (currentMode == Mode.PAN) {
                canvas.setCursor(Cursor.OPEN_HAND);
            } else {
                canvas.setCursor(Cursor.CROSSHAIR);
            }
        });

        canvas.setOnMouseExited(e -> canvas.setCursor(Cursor.DEFAULT));

        // Zoom with scroll
        canvas.setOnScroll(e -> {
            double zoomFactor = e.getDeltaY() > 0 ? 1.1 : 0.9;
            double mouseX = e.getX();
            double mouseY = e.getY();

            // Zoom towards mouse position
            translateX = mouseX - zoomFactor * (mouseX - translateX);
            translateY = mouseY - zoomFactor * (mouseY - translateY);
            scale *= zoomFactor;

            // Clamp scale
            scale = Math.max(0.1, Math.min(10.0, scale));

            zoomLabel.setText(String.format("Zoom: %.0f%%", scale * 100));
            redrawCanvas();
        });

        // Mode toggle updates cursor
        panBtn.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                canvas.setCursor(Cursor.OPEN_HAND);
            } else {
                canvas.setCursor(Cursor.CROSSHAIR);
            }
        });

        redrawCanvas();

        var section = new VBox(5, titleLabel, toolbar, canvasContainer);
        return section;
    }

    private void addPoint(double screenX, double screenY) {
        Point2D worldPoint = screenToWorld(screenX, screenY);
        points.add(worldPoint);
        redrawCanvas();
    }

    private void handleLineClick(double screenX, double screenY) {
        Point2D worldPoint = screenToWorld(screenX, screenY);
        polyline.add(worldPoint);
        redrawCanvas();
    }

    private Point2D screenToWorld(double screenX, double screenY) {
        return new Point2D(
                (screenX - translateX) / scale,
                (screenY - translateY) / scale);
    }

    private Point2D worldToScreen(Point2D world) {
        return new Point2D(
                world.getX() * scale + translateX,
                world.getY() * scale + translateY);
    }

    private void resetView() {
        translateX = 0;
        translateY = 0;
        scale = 1.0;
        redrawCanvas();
    }

    private void clearAll() {
        points.clear();
        polyline.clear();
        redrawCanvas();
    }

    private void redrawCanvas() {
        var gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // Clear
        gc.setFill(Color.web("#f5f5f5"));
        gc.fillRect(0, 0, w, h);

        // Draw grid
        drawGrid(gc, w, h);

        // Draw polyline
        if (polyline.size() >= 2) {
            gc.setStroke(Color.BLUE);
            gc.setLineWidth(2);
            for (int i = 0; i < polyline.size() - 1; i++) {
                Point2D start = worldToScreen(polyline.get(i));
                Point2D end = worldToScreen(polyline.get(i + 1));
                gc.strokeLine(start.getX(), start.getY(), end.getX(), end.getY());
            }
        }

        // Draw polyline vertices
        gc.setFill(Color.BLUE);
        for (Point2D vertex : polyline) {
            Point2D screen = worldToScreen(vertex);
            gc.fillOval(screen.getX() - 4, screen.getY() - 4, 8, 8);
        }

        // Draw points
        gc.setFill(Color.RED);
        for (Point2D point : points) {
            Point2D screen = worldToScreen(point);
            gc.fillOval(screen.getX() - 5, screen.getY() - 5, 10, 10);
        }

        // Origin marker
        Point2D origin = worldToScreen(new Point2D(0, 0));
        if (origin.getX() >= -10 && origin.getX() <= w + 10 &&
                origin.getY() >= -10 && origin.getY() <= h + 10) {
            gc.setStroke(Color.DARKGRAY);
            gc.setLineWidth(2);
            gc.strokeLine(origin.getX() - 10, origin.getY(), origin.getX() + 10, origin.getY());
            gc.strokeLine(origin.getX(), origin.getY() - 10, origin.getX(), origin.getY() + 10);
        }
    }

    private void drawGrid(GraphicsContext gc, double w, double h) {
        gc.setStroke(Color.LIGHTGRAY);
        gc.setLineWidth(1);

        double gridSize = 50 * scale;
        if (gridSize < 20)
            gridSize = 20;
        if (gridSize > 100)
            gridSize = 100;

        double startX = translateX % gridSize;
        double startY = translateY % gridSize;

        for (double x = startX; x < w; x += gridSize) {
            gc.strokeLine(x, 0, x, h);
        }
        for (double y = startY; y < h; y += gridSize) {
            gc.strokeLine(0, y, w, y);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
