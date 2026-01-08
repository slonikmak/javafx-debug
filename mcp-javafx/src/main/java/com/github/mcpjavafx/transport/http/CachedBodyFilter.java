package com.github.mcpjavafx.transport.http;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/**
 * Filter that wraps HttpServletRequest with CachedBodyRequestWrapper.
 */
public class CachedBodyFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest && "POST".equalsIgnoreCase(httpRequest.getMethod())) {
            var wrapped = new CachedBodyRequestWrapper(httpRequest);
            chain.doFilter(wrapped, response);
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
    }
}
