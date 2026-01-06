package com.github.mcpjavafx.transport.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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
        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }

        var httpRequest = (HttpServletRequest) request;
        if (!"POST".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        var contentType = httpRequest.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
            chain.doFilter(request, response);
            return;
        }

        var bodyBytes = httpRequest.getInputStream().readAllBytes();
        var sanitized = sanitize(bodyBytes);
        chain.doFilter(new CachedBodyRequest(httpRequest, sanitized), response);
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

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        CachedBodyRequest(HttpServletRequest request, byte[] cachedBody) {
            super(request);
            this.cachedBody = cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            var byteStream = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return byteStream.read();
                }

                @Override
                public boolean isFinished() {
                    return byteStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // Not used for sync reads
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
