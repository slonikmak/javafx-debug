package com.github.mcpjavafx.transport.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mcpjavafx.api.McpJavafxConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/**
 * Health check endpoint servlet.
 * 
 * <p>
 * This is a non-MCP endpoint implemented manually as per spec.
 * </p>
 */
public class HealthServlet extends HttpServlet {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final McpJavafxConfig config;

    public HealthServlet(McpJavafxConfig config) {
        this.config = config;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        var tools = new java.util.ArrayList<String>();
        tools.add("ui_get_snapshot");
        tools.add("ui_query");
        tools.add("ui_get_node");
        if (config.allowActions()) {
            tools.add("ui_perform");
            tools.add("ui_screenshot");
        }

        var response = Map.of(
                "ok", true,
                "schema", "mcp-javafx-ui/1.0",
                "transport", "streamable-http-stateless",
                "tools", tools);

        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/json; charset=utf-8");
        MAPPER.writeValue(resp.getOutputStream(), response);
    }
}
