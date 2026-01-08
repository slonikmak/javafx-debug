package com.github.mcpjavafx.example;

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
 * Run with: java -Dmcp.ui=true -javaagent:path/to/mcp-javafx.jar DemoApp
 */
public class DemoApp extends Application {

    @Override
    public void start(Stage stage) {
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

    public static void main(String[] args) {
        launch(args);
    }
}
