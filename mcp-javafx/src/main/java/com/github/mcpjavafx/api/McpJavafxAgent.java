package com.github.mcpjavafx.api;

import java.lang.instrument.Instrumentation;

/**
 * Java Agent entry point for MCP JavaFX Debug.
 * Allows attaching the debugger without code changes.
 */
public class McpJavafxAgent {
    
    public static void premain(String agentArgs, Instrumentation inst) {
        start();
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        start();
    }

    private static void start() {
        // Start in a separate thread to avoid blocking application startup
        Thread t = new Thread(() -> {
            try {
                // Wait a bit for JavaFX to initialize if needed, 
                // though McpJavafxDebug handles lazy initialization internally
                McpJavafxDebug.startFromSystemProperties();
            } catch (Exception e) {
                System.err.println("[MCP] Failed to start agent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        t.setDaemon(true);
        t.setName("McpAgent-Startup");
        t.start();
    }
}
