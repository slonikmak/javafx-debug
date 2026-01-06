package com.github.mcpjavafx.transport.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;

/**
 * Sanitizes MCP initialize payloads to avoid SDK incompatibilities.
 */
public class RequestSanitizingFilter implements Filter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void init(FilterConfig filterConfig) {
        // No initialization needed
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof CachedBodyRequestWrapper wrapped)) {
            chain.doFilter(request, response);
            return;
        }

        var bodyBytes = wrapped.getBody();
        var sanitized = sanitize(bodyBytes);
        if (sanitized != bodyBytes) {
            wrapped.setBody(sanitized);
        }
        chain.doFilter(wrapped, response);
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }

    private byte[] sanitize(byte[] bodyBytes) {
        try {
            var root = MAPPER.readTree(bodyBytes);
            if (!(root instanceof ObjectNode rootObj)) {
                return bodyBytes;
            }
            var methodNode = rootObj.get("method");
            var method = methodNode != null ? methodNode.asText() : "";
            if ("initialize".equals(method)) {
                var paramsNode = rootObj.path("params");
                if (paramsNode instanceof ObjectNode paramsObj) {
                    var capsNode = paramsObj.path("capabilities");
                    if (capsNode instanceof ObjectNode capsObj) {
                        var elicitationNode = capsObj.path("elicitation");
                        if (elicitationNode instanceof ObjectNode elicitationObj) {
                            elicitationObj.remove("form");
                            elicitationObj.remove("url");
                        }
                    }
                }
            }

            if ("tools/call".equals(method)) {
                var paramsNode = rootObj.path("params");
                if (paramsNode instanceof ObjectNode paramsObj) {
                    var nameNode = paramsObj.path("name");
                    if (nameNode.isTextual()) {
                        var rewritten = mapLegacyToolName(nameNode.asText());
                        if (!rewritten.equals(nameNode.asText())) {
                            paramsObj.put("name", rewritten);
                        }
                    }
                }
            }

            return MAPPER.writeValueAsBytes(rootObj);
        } catch (Exception e) {
            return bodyBytes;
        }
    }

    private String mapLegacyToolName(String name) {
        return switch (name) {
            case "ui.getSnapshot" -> "ui_get_snapshot";
            case "ui.query" -> "ui_query";
            case "ui.getNode" -> "ui_get_node";
            case "ui.perform" -> "ui_perform";
            case "ui.screenshot" -> "ui_screenshot";
            default -> name;
        };
    }
}
