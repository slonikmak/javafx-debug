package com.github.mcpjavafx.example;

/**
 * Launcher class to work around JavaFX module system requirements.
 * Use this class to run the demo from IDE without module configuration.
 */
public class DemoAppLauncher {

    public static void main(String[] args) {
        // Force enable MCP for development/testing
        System.setProperty("mcp.ui", "true");
        System.setProperty("mcp.allowActions", "true");
        System.setProperty("mcp.auth", "false");
        System.setProperty("mcp.port", "55667");

        DemoApp.main(args);
    }
}
