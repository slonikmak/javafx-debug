package com.github.mcpjavafx.e2e;

import com.github.mcpjavafx.api.McpJavafxConfig;
import com.github.mcpjavafx.core.model.UiSnapshot;
import com.github.mcpjavafx.transport.http.HttpMcpServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ListView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpClientStreamableHttpE2eTest extends ApplicationTest {

    private static HttpMcpServer server;
    private static int port;

    private McpSyncClient client;

    static {
        // Enable headless TestFX + software rendering for CI-friendly runs.
        System.setProperty("testfx.robot", "glass");
        System.setProperty("testfx.headless", "true");
        System.setProperty("java.awt.headless", "true");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
    }

    @BeforeAll
    static void startServer() throws Exception {
        var config = McpJavafxConfig.builder()
                .bindHost(McpJavafxConfig.DEFAULT_BIND_HOST)
                .port(0)
                .allowActions(true)
                .build();
        server = new HttpMcpServer(config);
        port = server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Override
    public void start(Stage stage) {
        var label = new Label("Hello, MCP!");
        label.setId("greeting");

        var textField = new TextField();
        textField.setId("input");
        textField.setPromptText("Enter your name");

        var button = new Button("Submit");
        button.setId("submitBtn");
        button.setDefaultButton(true);
        button.setOnAction(e -> label.setText("Hello, " + textField.getText() + "!"));

        var statusLabel = new Label("Status: Idle");
        statusLabel.setId("statusLabel");

        var mouseArea = new Pane();
        mouseArea.setId("mouseArea");
        mouseArea.setPrefSize(100, 100);
        mouseArea.setStyle("-fx-background-color: lightgray;");
        mouseArea.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                statusLabel.setText("Status: DoubleClicked");
            }
        });
        mouseArea.setOnMousePressed(e -> statusLabel.setText("Status: Pressed"));
        mouseArea.setOnMouseReleased(e -> {
            if (!"Status: Dragged".equals(statusLabel.getText())) {
                statusLabel.setText("Status: Released");
            }
        });
        mouseArea.setOnMouseDragged(e -> statusLabel.setText("Status: Dragged"));

        var listView = new ListView<String>();
        listView.setId("listView");
        for (int i = 1; i <= 20; i++) {
            listView.getItems().add("Item " + i);
        }
        listView.setPrefHeight(100);

        var root = new VBox(10, label, textField, button, statusLabel, mouseArea, listView);
        root.setId("root");
        root.setPadding(new Insets(16));

        stage.setTitle("TestFX MCP Demo");
        stage.setScene(new Scene(root, 320, 600));
        stage.show();
    }

    @BeforeEach
    void createClient() {
        var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                .endpoint("/mcp")
                .build();

        client = McpClient.sync(transport)
                .clientInfo(new Implementation("mcp-javafx-debug-test", "0.0.1"))
                .build();
        client.initialize();
    }

    @AfterEach
    void closeClient() {
        if (client != null) {
            client.closeGracefully();
        }
    }

    @Test
    void streamableHttpClientCoversSnapshotQueryAndPerform() {
        var tools = client.listTools();
        assertTrue(tools.tools().stream().map(Tool::name).anyMatch("ui_get_snapshot"::equals));
        assertTrue(tools.tools().stream().map(Tool::name).anyMatch("ui_query"::equals));
        assertTrue(tools.tools().stream().map(Tool::name).anyMatch("ui_perform"::equals));

        var snapshotOutput = structuredOutput(
                client.callTool(new CallToolRequest("ui_get_snapshot", Map.of("mode", "compact"))));
        assertEquals(UiSnapshot.SCHEMA_VERSION, snapshotOutput.get("schema"));

        @SuppressWarnings("unchecked")
        var stages = (List<Map<String, Object>>) snapshotOutput.get("stages");
        assertFalse(stages.isEmpty(), "Snapshot must include at least one stage");
        var stage = stages.getFirst();

        @SuppressWarnings("unchecked")
        var scene = (Map<String, Object>) stage.get("scene");
        @SuppressWarnings("unchecked")
        var root = (Map<String, Object>) scene.get("root");
        @SuppressWarnings("unchecked")
        var rootRef = (Map<String, Object>) root.get("ref");
        assertNotNull(rootRef.get("path"));

        var inputMatch = querySingle(Map.of("css", "#input"));
        var buttonMatch = querySingle(Map.of("css", "#submitBtn"));
        var labelMatch = querySingle(Map.of("css", "#greeting"));

        structuredOutput(client.callTool(new CallToolRequest(
                "ui_perform",
                Map.of(
                        "actions", List.of(
                                Map.of(
                                        "type", "setText",
                                        "target", Map.of("ref", inputMatch.get("ref")),
                                        "text", "TestFX"),
                                Map.of(
                                        "type", "click",
                                        "target", Map.of("ref", buttonMatch.get("ref")))),
                        "awaitUiIdle", true))));

        var labelNode = structuredOutput(
                client.callTool(new CallToolRequest("ui_get_node", Map.of("ref", labelMatch.get("ref")))));
        @SuppressWarnings("unchecked")
        var text = (Map<String, Object>) labelNode.get("text");
        assertEquals("Hello, TestFX!", text.get("label"));

        // Test typeText (new logic)
        structuredOutput(client.callTool(new CallToolRequest(
                "ui_perform",
                Map.of("actions", List.of(
                        Map.of("type", "focus", "target", Map.of("ref", inputMatch.get("ref"))),
                        Map.of("type", "setText", "target", Map.of("ref", inputMatch.get("ref")), "text", ""),
                        Map.of("type", "typeText", "text", "Typed"))))));

        var inputAfterType = querySingle(Map.of("css", "#input"));
        // summary uses buildSummary which includes type and id, but for TextField it
        // includes [text=...]
        assertTrue(inputAfterType.get("summary").toString().contains("text=Typed"), "Input should contain typed text");
    }

    @Test
    void testUiScreenshot() {
        var result = structuredOutput(client.callTool(new CallToolRequest("ui_screenshot", Map.of())));
        assertEquals("image/png", result.get("contentType"));
        var base64 = (String) result.get("dataBase64");
        assertNotNull(base64);
        assertFalse(base64.isEmpty());
    }

    @Test
    void testUiQueryFilters() {
        // Query by text
        var submitMatches = (List<?>) structuredOutput(client.callTool(new CallToolRequest(
                "ui_query", Map.of("selector", Map.of("text", "Submit", "match", "equals"))))).get("matches");
        assertFalse(submitMatches.isEmpty(), "Should find at least 1 match for 'Submit'");

        // Query by predicate (visible & enabled)
        var predicateMatches = (List<?>) structuredOutput(client.callTool(new CallToolRequest(
                "ui_query",
                Map.of("selector",
                        Map.of("predicate", Map.of("visible", true, "enabled", true, "idEquals", "submitBtn"))))))
                .get("matches");
        assertEquals(1, predicateMatches.size());
    }

    @Test
    void testUiGestures() {
        var mouseArea = querySingle(Map.of("css", "#mouseArea"));
        var mouseAreaRef = mouseArea.get("ref");

        var listView = querySingle(Map.of("css", "#listView"));
        var listViewRef = listView.get("ref");

        var statusLabelRef = querySingle(Map.of("css", "#statusLabel")).get("ref");

        // 1. Double Click
        perform(Map.of("type", "doubleClick", "target", Map.of("ref", mouseAreaRef)));
        assertStatus(statusLabelRef, "Status: DoubleClicked");

        // 2. Mouse Pressed
        perform(Map.of("type", "mousePressed", "target", Map.of("ref", mouseAreaRef)));
        assertStatus(statusLabelRef, "Status: Pressed");

        // 3. Mouse Released
        perform(Map.of("type", "mouseReleased", "target", Map.of("ref", mouseAreaRef)));
        assertStatus(statusLabelRef, "Status: Released");

        // 4. Drag (simulated small drag on the area)
        perform(Map.of("type", "drag",
                "from", Map.of("ref", mouseAreaRef),
                "to", Map.of("ref", mouseAreaRef, "x", 50, "y", 50) // drag to center of itself relative? No, drag
                                                                    // target is Ref + optional offset.
        // Wait, ui_perform drag takes from: {ref, x, y} -> to: {ref, x, y}.
        // Let's drag from (10,10) to (90,90) inside the mouseArea
        ));
        // ActionExecutor drag logic resolves bounds and adds offsets.
        // It's safer to provide direct coordinates or carefully use ref.
        // Let's try simplified drag.
        // Let's try simplified drag.
        perform(Map.of("type", "drag",
                "from", Map.of("ref", mouseAreaRef, "x", 10.0, "y", 10.0),
                "to", Map.of("ref", mouseAreaRef, "x", 90.0, "y", 90.0)));
        assertStatus(statusLabelRef, "Status: Dragged");

        // 5. Scroll
        // We can't easily assert scroll position without querying node properties
        // deeply or checking visuals.
        // But we can ensure it doesn't throw.
        perform(Map.of("type", "scroll", "target", Map.of("ref", listViewRef), "deltaY", -5)); // Scroll down
        perform(Map.of("type", "scroll", "target", Map.of("ref", listViewRef), "deltaY", 5)); // Scroll up

        // 6. Press Key (ENTER to submit)
        var inputMatch = querySingle(Map.of("css", "#input"));
        perform(Map.of("type", "focus", "target", Map.of("ref", inputMatch.get("ref"))));
        perform(Map.of("type", "setText", "target", Map.of("ref", inputMatch.get("ref")), "text", "KeyboardUser"));
        perform(Map.of("type", "pressKey", "key", "ENTER"));

        var greetingLabelRef = querySingle(Map.of("css", "#greeting")).get("ref");
        assertStatus(greetingLabelRef, "Hello, KeyboardUser!");
    }

    private void perform(Map<String, Object> action) {
        structuredOutput(client.callTool(new CallToolRequest(
                "ui_perform",
                Map.of("actions", List.of(action)))));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void assertStatus(Object statusLabelRef, String expectedText) {
        long start = System.currentTimeMillis();
        String currentText = "";
        while (System.currentTimeMillis() - start < 2000) {
            var node = structuredOutput(
                    client.callTool(new CallToolRequest("ui_get_node", Map.of("ref", statusLabelRef))));
            @SuppressWarnings("unchecked")
            var textMap = (Map<String, Object>) node.get("text");
            currentText = (String) textMap.get("label");
            if (expectedText.equals(currentText)) {
                return;
            }
            WaitForAsyncUtils.waitForFxEvents();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertEquals(expectedText, currentText, "Status label did not update in time");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> structuredOutput(CallToolResult result) {
        assertNotNull(result, "CallToolResult should not be null");
        assertFalse(Boolean.TRUE.equals(result.isError()), "Expected success but got error: " + result);
        var structured = (Map<String, Object>) result.structuredContent();
        assertNotNull(structured, "structuredContent must be present");
        var output = (Map<String, Object>) structured.get("output");
        assertNotNull(output, "output payload is required");
        return output;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> querySingle(Map<String, Object> selector) {
        var result = client.callTool(new CallToolRequest("ui_query", Map.of("selector", selector)));
        var output = structuredOutput(result);
        var matches = (List<Map<String, Object>>) output.get("matches");
        assertNotNull(matches, "query should return matches");
        assertFalse(matches.isEmpty(), "query should return at least one match");
        return matches.getFirst();
    }
}
