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
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testfx.framework.junit5.ApplicationTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpClientStreamableHttpE2eTest extends ApplicationTest {

    private static final String TOKEN = "test-token";

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
                .authEnabled(true)
                .build();
        server = new HttpMcpServer(config, TOKEN);
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
        button.setOnAction(e -> label.setText("Hello, " + textField.getText() + "!"));

        var root = new VBox(10, label, textField, button);
        root.setId("root");
        root.setPadding(new Insets(16));

        stage.setTitle("TestFX MCP Demo");
        stage.setScene(new Scene(root, 320, 200));
        stage.show();
    }

    @BeforeEach
    void createClient() {
        var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                .endpoint("/mcp")
                .customizeRequest(builder -> builder.header("Authorization", "Bearer " + TOKEN))
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