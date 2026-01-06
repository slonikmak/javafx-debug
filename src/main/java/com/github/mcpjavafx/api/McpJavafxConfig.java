package com.github.mcpjavafx.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Configuration for MCP JavaFX Debug server.
 *
 * @param enabled          whether the debug server is enabled
 * @param transport        transport mode (HTTP_LOCAL by default)
 * @param bindHost         host to bind to (127.0.0.1 by default)
 * @param port             port to bind to (0 = auto-select)
 * @param token            authentication token (null = generate and log)
 * @param allowActions     whether to allow UI actions (click, type, etc.)
 * @param snapshotDefaults default snapshot options
 * @param fxTimeoutMs      timeout for FX thread operations
 * @param serverShutdownMs timeout for server shutdown
 */
public record McpJavafxConfig(
        boolean enabled,
        Transport transport,
        String bindHost,
        int port,
        String token,
        boolean allowActions,
        SnapshotOptions snapshotDefaults,
        int fxTimeoutMs,
        int serverShutdownMs) {
    public static final int DEFAULT_FX_TIMEOUT_MS = 5000;
    public static final int DEFAULT_SERVER_SHUTDOWN_MS = 2000;
    public static final String DEFAULT_BIND_HOST = "127.0.0.1";

    public static McpJavafxConfig defaults() {
        return new McpJavafxConfig(
                true,
                Transport.HTTP_LOCAL,
                DEFAULT_BIND_HOST,
                0,
                null,
                true,
                SnapshotOptions.DEFAULT,
                DEFAULT_FX_TIMEOUT_MS,
                DEFAULT_SERVER_SHUTDOWN_MS);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates configuration from system properties.
     */
    public static McpJavafxConfig fromSystemProperties() {
        return new McpJavafxConfig(
                Boolean.parseBoolean(System.getProperty("mcp.ui", "false")),
                parseTransport(System.getProperty("mcp.transport", "http")),
                System.getProperty("mcp.bind", DEFAULT_BIND_HOST),
                Integer.parseInt(System.getProperty("mcp.port", "0")),
                System.getProperty("mcp.token"),
                Boolean.parseBoolean(System.getProperty("mcp.allowActions", "true")),
                parseSnapshotOptions(),
                Integer.parseInt(System.getProperty("mcp.fxTimeout", String.valueOf(DEFAULT_FX_TIMEOUT_MS))),
                DEFAULT_SERVER_SHUTDOWN_MS);
    }

    private static Transport parseTransport(String value) {
        return switch (value.toLowerCase()) {
            case "http", "http_local" -> Transport.HTTP_LOCAL;
            case "stdio" -> Transport.STDIO;
            default -> Transport.HTTP_LOCAL;
        };
    }

    private static SnapshotOptions parseSnapshotOptions() {
        return SnapshotOptions.builder()
                .depth(Integer.parseInt(System.getProperty("mcp.snapshot.depth", "50")))
                .includeBounds(Boolean.parseBoolean(System.getProperty("mcp.snapshot.bounds", "true")))
                .includeLocalToScreen(Boolean.parseBoolean(System.getProperty("mcp.snapshot.localToScreen", "true")))
                .includeProperties(Boolean.parseBoolean(System.getProperty("mcp.snapshot.properties", "false")))
                .includeVirtualization(Boolean.parseBoolean(System.getProperty("mcp.snapshot.virtualization", "true")))
                .includeAccessibility(Boolean.parseBoolean(System.getProperty("mcp.snapshot.accessibility", "false")))
                .build();
    }

    /**
     * Returns token, generating one if not provided.
     */
    public String effectiveToken() {
        return token != null ? token : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static class Builder {
        private boolean enabled = true;
        private Transport transport = Transport.HTTP_LOCAL;
        private String bindHost = DEFAULT_BIND_HOST;
        private int port = 0;
        private String token = null;
        private boolean allowActions = true;
        private SnapshotOptions snapshotDefaults = SnapshotOptions.DEFAULT;
        private int fxTimeoutMs = DEFAULT_FX_TIMEOUT_MS;
        private int serverShutdownMs = DEFAULT_SERVER_SHUTDOWN_MS;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder transport(Transport transport) {
            this.transport = Objects.requireNonNull(transport);
            return this;
        }

        public Builder bindHost(String bindHost) {
            this.bindHost = Objects.requireNonNull(bindHost);
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder allowActions(boolean allowActions) {
            this.allowActions = allowActions;
            return this;
        }

        public Builder snapshotDefaults(SnapshotOptions snapshotDefaults) {
            this.snapshotDefaults = Objects.requireNonNull(snapshotDefaults);
            return this;
        }

        public Builder fxTimeoutMs(int fxTimeoutMs) {
            this.fxTimeoutMs = fxTimeoutMs;
            return this;
        }

        public Builder serverShutdownMs(int serverShutdownMs) {
            this.serverShutdownMs = serverShutdownMs;
            return this;
        }

        public McpJavafxConfig build() {
            return new McpJavafxConfig(
                    enabled, transport, bindHost, port, token,
                    allowActions, snapshotDefaults, fxTimeoutMs, serverShutdownMs);
        }
    }
}
