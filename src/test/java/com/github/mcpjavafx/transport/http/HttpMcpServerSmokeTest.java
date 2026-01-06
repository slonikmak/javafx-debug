package com.github.mcpjavafx.transport.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mcpjavafx.api.McpJavafxConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class HttpMcpServerSmokeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpMcpServer server;

    private int startServer(String token) throws Exception {
        var config = McpJavafxConfig.builder()
                .bindHost("127.0.0.1")
                .port(0)
                .allowActions(false)
                .build();
        server = new HttpMcpServer(config, token);
        return server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void healthEndpointReturnsOkJson() throws Exception {
        var token = "test-token";
        var port = startServer(token);

        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/health"))
                .GET()
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("application/json"));

        JsonNode json = MAPPER.readTree(response.body());
        assertTrue(json.path("ok").asBoolean(false));
        assertEquals("mcp-javafx-ui/1.0", json.path("schema").asText());
        assertEquals("streamable-http-stateless", json.path("transport").asText());
        assertTrue(json.path("tools").isArray());
    }

    @Test
    void mcpEndpointRejectsGetWith405() throws Exception {
        var token = "test-token";
        var port = startServer(token);

        var client = HttpClient.newHttpClient();

        // Spec: GET /mcp must be 405 (no SSE), regardless of auth.
        var noAuthGet = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .GET()
                .build();
        var noAuthGetResponse = client.send(noAuthGet, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, noAuthGetResponse.statusCode());

        // With auth should still be 405.
        var authGet = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        var authGetResponse = client.send(authGet, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, authGetResponse.statusCode());
        assertTrue(authGetResponse.headers().firstValue("Allow").orElse("").contains("POST"));
    }

    @Test
    void mcpEndpointRequiresAuthorizationForPost() throws Exception {
        var token = "test-token";
        var port = startServer(token);

        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
        assertTrue(response.headers().firstValue("WWW-Authenticate").orElse("").contains("Bearer"));
    }

    @Test
    void mcpSessionHeaderIsIgnored() throws Exception {
        var token = "test-token";
        var port = startServer(token);

        var client = HttpClient.newHttpClient();

        var base = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"));

        var withoutSession = client.send(base.build(), HttpResponse.BodyHandlers.ofString());

        var withSession = client.send(HttpRequest.newBuilder(base.build().uri())
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Mcp-Session-Id", "should-be-ignored")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(withoutSession.statusCode(), withSession.statusCode());
        assertFalse(withoutSession.statusCode() == 401 || withSession.statusCode() == 401);
        assertFalse(withoutSession.statusCode() >= 500 || withSession.statusCode() >= 500);
    }

        @Test
        void toolsCallReturnsStructuredContent() throws Exception {
        var token = "test-token";
        var port = startServer(token);

        var client = HttpClient.newHttpClient();

        var initialize = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{"roots":{"listChanged":true},"sampling":{}},"clientInfo":{"name":"smoke","version":"0"}}}
            """;

        var initReq = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/mcp"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            // MCP SDK transport requires both values in Accept.
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(initialize))
            .build();

        var initResp = client.send(initReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, initResp.statusCode());
        assertTrue(initResp.headers().firstValue("content-type").orElse("").startsWith("application/json"));

        var initialized = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
        var initializedReq = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/mcp"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(initialized))
            .build();
        var initializedResp = client.send(initializedReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(202, initializedResp.statusCode());

        // No JavaFX stages in this test process; tool should return a structured error.
        var callSnapshot = """
            {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"ui.getSnapshot","arguments":{}}}
            """;
        var callReq = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/mcp"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(callSnapshot))
            .build();

        var callResp = client.send(callReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, callResp.statusCode());

        JsonNode json = MAPPER.readTree(callResp.body());
        assertEquals("2.0", json.path("jsonrpc").asText());
        assertEquals(2, json.path("id").asInt());

        var result = json.path("result");
        assertTrue(result.path("isError").asBoolean());

        // Regression check: when outputSchema is present, SDK expects structuredContent.
        assertTrue(result.has("structuredContent"), "Expected result.structuredContent to be present");

        var errorCode = result.path("structuredContent").path("error").path("code").asText();
        // In unit tests JavaFX Toolkit isn't initialized, so snapshot can fail with INTERNAL.
        assertTrue(
            errorCode.equals("MCP_UI_INTERNAL") || errorCode.equals("MCP_UI_NO_STAGES"),
            "Unexpected error code: " + errorCode);
        }

    @Test
    void initializeWithUnknownElicitationFieldsIsAccepted() throws Exception {
        var token = "test-token";
        var port = startServer(token);

        var client = HttpClient.newHttpClient();

        var initialize = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{"roots":{"listChanged":true},"sampling":{},"elicitation":{"url":"https://example.invalid","form":{"kind":"dummy"}}},"clientInfo":{"name":"smoke","version":"0"}}}
            """;

        var initReq = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/mcp"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            // MCP SDK transport requires both values in Accept.
            .header("Accept", "application/json, text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(initialize))
            .build();

        var initResp = client.send(initReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, initResp.statusCode());
    }
}
