package com.github.mcpjavafx.example;

import com.github.mcpjavafx.api.McpJavafxDebug;
import com.github.mcpjavafx.api.McpJavafxHandle;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Demo JavaFX application with MCP debug enabled.
 * 
 * Run with: java -Dmcp.ui=true DemoApp
 */
public class DemoApp extends Application {

    private McpJavafxHandle mcpHandle;

    @Override
    public void start(Stage stage) {
        // Start MCP server via system properties or defaults
        mcpHandle = McpJavafxDebug.startFromSystemProperties();

        // Build simple UI
        var label = new Label("Hello, MCP!");
        label.setId("greeting");

        var textField = new TextField();
        textField.setId("input");
        textField.setPromptText("Enter your name");

        var button = new Button("Submit");
        button.setId("submitBtn");
        button.setOnAction(e -> {
            label.setText("Hello, " + textField.getText() + "!");
        });

        var root = new VBox(10, label, textField, button);
        root.setPadding(new Insets(20));
        root.setId("root");

        var scene = new Scene(root, 300, 200);
        stage.setTitle("MCP Demo");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (mcpHandle != null) {
            mcpHandle.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
