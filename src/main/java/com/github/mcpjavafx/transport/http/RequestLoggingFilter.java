package com.github.mcpjavafx.transport.http;

import com.github.mcpjavafx.api.McpJavafxConfig;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Logs MCP HTTP requests for debugging.
 */
public class RequestLoggingFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(RequestLoggingFilter.class.getName());
    private static final int MAX_BODY_LOG_BYTES = 64 * 1024;
    private final McpJavafxConfig config;

    public RequestLoggingFilter(McpJavafxConfig config) {
        this.config = config;
    }

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

        logRequest(wrapped);
        chain.doFilter(wrapped, response);
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }

    private void logRequest(CachedBodyRequestWrapper request) {
        var method = request.getMethod();
        var uri = request.getRequestURI();
        var contentType = request.getContentType();
        var contentLength = request.getContentLengthLong();

        var message = new StringBuilder(512)
                .append("MCP request: ")
                .append(method).append(" ").append(uri)
                .append(" contentType=").append(contentType)
                .append(" contentLength=").append(contentLength);

        if (config.logRequests()) {
            var bodyBytes = request.getBody();
            var truncated = bodyBytes.length > MAX_BODY_LOG_BYTES;
            var toLog = truncated ? MAX_BODY_LOG_BYTES : bodyBytes.length;
            var body = new String(bodyBytes, 0, toLog, StandardCharsets.UTF_8);

            message.append("\n").append(body);
            if (truncated) {
                message.append("\n...(truncated ").append(bodyBytes.length - toLog).append(" bytes)");
            }
        }

        LOG.info(message.toString());
    }
}
