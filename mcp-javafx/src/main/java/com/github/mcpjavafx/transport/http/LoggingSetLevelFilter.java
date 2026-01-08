package com.github.mcpjavafx.transport.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Handles logging/setLevel requests for SDKs that do not implement it.
 */
public class LoggingSetLevelFilter implements Filter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void init(FilterConfig filterConfig) {
        // No initialization needed
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof CachedBodyRequestWrapper wrapped) || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        var bodyBytes = wrapped.getBody();
        if (!isLoggingSetLevel(bodyBytes)) {
            chain.doFilter(wrapped, response);
            return;
        }

        httpResponse.setStatus(HttpServletResponse.SC_OK);
        httpResponse.setContentType("application/json; charset=utf-8");
        httpResponse.getOutputStream().write(ack(bodyBytes));
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }

    private boolean isLoggingSetLevel(byte[] bodyBytes) {
        try {
            var root = MAPPER.readTree(bodyBytes);
            var methodNode = root.get("method");
            var method = methodNode != null ? methodNode.asText() : "";
            return "logging/setLevel".equals(method);
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] ack(byte[] bodyBytes) throws IOException {
        var root = MAPPER.readTree(bodyBytes);
        var idNode = root.path("id");
        Object idValue = null;
        if (idNode.isTextual()) {
            idValue = idNode.asText();
        } else if (idNode.isNumber()) {
            idValue = idNode.numberValue();
        }

        var response = new ObjectNode(MAPPER.getNodeFactory());
        response.put("jsonrpc", "2.0");
        if (idValue != null) {
            response.set("id", MAPPER.valueToTree(idValue));
        } else {
            response.set("id", MAPPER.nullNode());
        }
        response.set("result", MAPPER.valueToTree(Map.of()));
        return MAPPER.writeValueAsBytes(response);
    }
}
