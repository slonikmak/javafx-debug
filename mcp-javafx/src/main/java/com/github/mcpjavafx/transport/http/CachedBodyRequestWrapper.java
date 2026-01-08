package com.github.mcpjavafx.transport.http;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Request wrapper that keeps a copy of the body for multiple reads.
 */
public class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

    private byte[] cachedBody;

    public CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = request.getInputStream().readAllBytes();
    }

    public CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
        super(request);
        this.cachedBody = body;
    }

    public byte[] getBody() {
        return cachedBody;
    }

    public void setBody(byte[] newBody) {
        this.cachedBody = newBody;
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
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
