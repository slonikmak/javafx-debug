package com.github.mcpjavafx.api;

import com.github.mcpjavafx.util.McpLogger;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.lang.instrument.Instrumentation;

/**
 * Java Agent entry point for MCP JavaFX Debug.
 * Allows attaching the debugger without code changes.
 * 
 * <p>
 * The agent automatically registers listeners to properly stop the MCP server when:
 * <ul>
 *   <li>All JavaFX windows/stages are closed</li>
 *   <li>The JVM is shutting down (SIGTERM, SIGINT, etc.)</li>
 * </ul>
 * </p>
 */
public class McpJavafxAgent {
    
    private static volatile boolean shutdownHookRegistered = false;
    private static volatile boolean javafxListenerRegistered = false;
    
    public static void premain(String agentArgs, Instrumentation inst) {
        start();
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        start();
    }

    private static void start() {
        registerShutdownHook();
        
        // Start in a separate thread to avoid blocking application startup
        Thread t = new Thread(() -> {
            try {
                // Wait a bit for JavaFX to initialize if needed, 
                // though McpJavafxDebug handles lazy initialization internally
                McpJavafxDebug.startFromSystemProperties();
                
                // After MCP server starts, register JavaFX exit listener
                registerJavaFxExitListener();
            } catch (Exception e) {
                System.err.println("[MCP] Failed to start agent: " + e.getMessage());
                e.printStackTrace();
            }
        });
        t.setDaemon(true);
        t.setName("McpAgent-Startup");
        t.start();
    }
    
    private static synchronized void registerJavaFxExitListener() {
        if (javafxListenerRegistered) {
            return;
        }
        
        // Run on JavaFX Application Thread
        Platform.runLater(() -> {
            // Listen for window list changes
            Window.getWindows().addListener((javafx.collections.ListChangeListener<Window>) change -> {
                while (change.next()) {
                    if (change.wasRemoved()) {
                        // Check if all windows are closed
                        if (Window.getWindows().isEmpty()) {
                            McpLogger.info("All JavaFX windows closed, stopping MCP agent...");
                            stopAgentAndExit();
                        }
                    }
                }
            });
            
            // Also register close handlers on existing stages
            for (Window window : Window.getWindows()) {
                if (window instanceof Stage stage) {
                    stage.setOnHidden(event -> {
                        // Use runLater to check after the window is fully removed
                        Platform.runLater(() -> {
                            if (Window.getWindows().isEmpty()) {
                                McpLogger.info("Last JavaFX stage closed, stopping MCP agent...");
                                stopAgentAndExit();
                            }
                        });
                    });
                }
            }
        });
        
        javafxListenerRegistered = true;
    }
    
    private static void stopAgentAndExit() {
        try {
            var instance = McpJavafxDebug.getInstance();
            if (instance != null && instance.isRunning()) {
                instance.close();
                McpLogger.info("MCP agent stopped successfully");
            }
        } catch (Exception e) {
            System.err.println("[MCP] Error during agent shutdown: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Exit the JVM since the application has closed all windows
        Platform.exit();
        System.exit(0);
    }
    
    private static synchronized void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        
        Thread shutdownHook = new Thread(() -> {
            McpLogger.info("JVM shutdown detected, stopping MCP agent...");
            try {
                var instance = McpJavafxDebug.getInstance();
                if (instance != null && instance.isRunning()) {
                    instance.close();
                    McpLogger.info("MCP agent stopped successfully");
                }
            } catch (Exception e) {
                System.err.println("[MCP] Error during agent shutdown: " + e.getMessage());
                e.printStackTrace();
            }
        }, "McpAgent-Shutdown");
        
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        shutdownHookRegistered = true;
    }
}
