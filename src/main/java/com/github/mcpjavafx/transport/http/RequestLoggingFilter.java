package com.github.mcpjavafx.transport.http;

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
import java.util.logging.Logger;

/**
 * Logs MCP HTTP requests for debugging.
 */
public class RequestLoggingFilter implements Filter {

    private static final Logger LOG = Logger.getLogger(RequestLoggingFilter.class.getName());
    private static final int MAX_BODY_LOG_BYTES = 64 * 1024;

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
        var wrapped = new CachedBodyRequest(httpRequest);
        logRequest(wrapped);
        chain.doFilter(wrapped, response);
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }

    private void logRequest(CachedBodyRequest request) {
        var method = request.getMethod();
        var uri = request.getRequestURI();
        var contentType = request.getContentType();
        var contentLength = request.getContentLengthLong();

        var bodyBytes = request.getCachedBody();
        var truncated = bodyBytes.length > MAX_BODY_LOG_BYTES;
        var toLog = truncated ? MAX_BODY_LOG_BYTES : bodyBytes.length;
        var body = new String(bodyBytes, 0, toLog, StandardCharsets.UTF_8);

        var message = new StringBuilder(512)
                .append("MCP request: ")
                .append(method).append(" ").append(uri)
                .append(" contentType=").append(contentType)
                .append(" contentLength=").append(contentLength)
                .append("\n")
                .append(body);
        if (truncated) {
            message.append("\n...(truncated ").append(bodyBytes.length - toLog).append(" bytes)");
        }

        LOG.info(message.toString());
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            var input = request.getInputStream();
            this.cachedBody = input.readAllBytes();
        }

        byte[] getCachedBody() {
            return cachedBody;
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
