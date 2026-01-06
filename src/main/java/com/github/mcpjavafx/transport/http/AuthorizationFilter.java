package com.github.mcpjavafx.transport.http;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;

/**
 * Security filter that validates Bearer token authorization before MCP
 * requests.
 * 
 * <p>
 * Per the spec, authorization must be checked BEFORE passing requests to SDK
 * transport.
 * </p>
 */
public class AuthorizationFilter implements Filter {

    private final String expectedToken;
    private static final String SESSION_HEADER = "Mcp-Session-Id";

    public AuthorizationFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // No initialization needed
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        var httpRequest = (HttpServletRequest) request;
        var httpResponse = (HttpServletResponse) response;

        // Stateless Streamable HTTP profile: only POST is supported.
        // GET /mcp (SSE) MUST be rejected with 405.
        var method = httpRequest.getMethod();
        if (!"POST".equalsIgnoreCase(method)) {
            httpResponse.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            httpResponse.setHeader("Allow", "POST");
            return;
        }

        // Check Origin header for DNS rebinding protection
        var origin = httpRequest.getHeader("Origin");
        if (origin != null && !isAllowedOrigin(origin)) {
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.getWriter().write("Forbidden: Invalid origin");
            return;
        }

        // Check Authorization header
        var authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setHeader("WWW-Authenticate", "Bearer");
            httpResponse.getWriter().write("Unauthorized: Missing or invalid Authorization header");
            return;
        }

        var providedToken = authHeader.substring("Bearer ".length());
        if (!expectedToken.equals(providedToken)) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.getWriter().write("Unauthorized: Invalid token");
            return;
        }

        // Stateless Streamable HTTP profile: Mcp-Session-Id MUST be ignored.
        // We explicitly remove it before passing to the SDK transport.
        var wrappedRequest = new HttpServletRequestWrapper(httpRequest) {
            @Override
            public String getHeader(String name) {
                if (isSessionHeader(name)) {
                    return null;
                }
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if (isSessionHeader(name)) {
                    return Collections.emptyEnumeration();
                }
                return super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                var names = Collections.list(super.getHeaderNames());
                names.removeIf(AuthorizationFilter::isSessionHeader);
                return Collections.enumeration(names);
            }
        };

        // Token valid, proceed to SDK transport
        chain.doFilter(wrappedRequest, response);
    }

    @Override
    public void destroy() {
        // No cleanup needed
    }

    private boolean isAllowedOrigin(String origin) {
        // Allow localhost origins only (DNS rebinding protection)
        return origin.startsWith("http://localhost")
                || origin.startsWith("http://127.0.0.1")
                || origin.startsWith("https://localhost")
                || origin.startsWith("https://127.0.0.1");
    }

    private static boolean isSessionHeader(String headerName) {
        if (headerName == null) {
            return false;
        }
        return SESSION_HEADER.toLowerCase(Locale.ROOT).equals(headerName.toLowerCase(Locale.ROOT));
    }
}
