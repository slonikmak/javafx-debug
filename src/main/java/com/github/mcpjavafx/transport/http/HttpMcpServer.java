package com.github.mcpjavafx.transport.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mcpjavafx.api.McpJavafxConfig;
import com.github.mcpjavafx.mcp.McpToolRegistry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP-based MCP server using JDK HttpServer.
 */
public class HttpMcpServer {

    private static final Logger LOG = Logger.getLogger(HttpMcpServer.class.getName());
    private static final int MAX_BODY_SIZE = 5 * 1024 * 1024; // 5MB

    private final McpJavafxConfig config;
    private final String token;
    private final McpToolRegistry registry;
    private final ObjectMapper mapper;
    private HttpServer server;
    private int actualPort;

    public HttpMcpServer(McpJavafxConfig config, String token) {
        this.config = config;
        this.token = token;
        this.registry = new McpToolRegistry(config);
        this.mapper = new ObjectMapper();
    }

    /**
     * Starts the HTTP server.
     *
     * @return the actual port the server is listening on
     */
    public int start() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(config.bindHost(), config.port()),
                0);

        server.createContext("/health", new HealthHandler());
        server.createContext("/mcp", new McpHandler());
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.start();
        actualPort = server.getAddress().getPort();

        LOG.info("MCP JavaFX Debug HTTP server started on http://" +
                config.bindHost() + ":" + actualPort);

        return actualPort;
    }

    /**
     * Stops the HTTP server.
     */
    public void stop() {
        if (server != null) {
            server.stop(config.serverShutdownMs() / 1000);
            LOG.info("MCP JavaFX Debug HTTP server stopped");
        }
    }

    /**
     * Returns the actual port.
     */
    public int getPort() {
        return actualPort;
    }

    /**
     * Health endpoint handler.
     */
    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                var response = Map.of(
                        "ok", true,
                        "schema", "mcp-javafx-ui/1.0",
                        "tools", registry.getToolNames());
                sendJson(exchange, 200, mapper.writeValueAsString(response));
            } catch (Exception e) {
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }

    /**
     * MCP tool execution handler.
     */
    private class McpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            // Check authorization
            if (!checkAuth(exchange)) {
                sendResponse(exchange, 401, "Unauthorized");
                return;
            }

            try {
                // Read body
                var body = readBody(exchange);
                if (body == null) {
                    sendResponse(exchange, 413, "Request Entity Too Large");
                    return;
                }

                // Parse request
                var requestNode = mapper.readTree(body);
                var tool = requestNode.path("tool").asText();
                var input = requestNode.path("input");

                if (tool.isEmpty()) {
                    sendJson(exchange, 400,
                            "{\"error\":{\"code\":\"INVALID_REQUEST\",\"message\":\"Missing tool\"}}");
                    return;
                }

                // Execute tool
                var result = registry.execute(tool, input);
                sendJson(exchange, 200, result);

            } catch (Exception e) {
                LOG.log(Level.WARNING, "Error processing MCP request", e);
                sendJson(exchange, 500,
                        "{\"error\":{\"code\":\"MCP_UI_INTERNAL\",\"message\":\"" +
                                escapeJson(e.getMessage()) + "\"}}");
            }
        }
    }

    private boolean checkAuth(HttpExchange exchange) {
        var authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        var providedToken = authHeader.substring("Bearer ".length());
        return token.equals(providedToken);
    }

    private String readBody(HttpExchange exchange) throws IOException {
        var contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLength != null) {
            var length = Long.parseLong(contentLength);
            if (length > MAX_BODY_SIZE) {
                return null;
            }
        }

        try (var is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void sendResponse(HttpExchange exchange, int code, String message) throws IOException {
        var bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String escapeJson(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
